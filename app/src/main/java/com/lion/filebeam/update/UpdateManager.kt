package com.lion.filebeam.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ReleaseInfo(
    val version: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val apkName: String,
    val checksumUrl: String?,
)

object UpdateManager {
    private const val RELEASE_API = "https://api.github.com/repos/Lionapp1/haberak-s/releases/latest"
    private const val CONNECT_TIMEOUT = 15000
    private const val READ_TIMEOUT = 30000

    fun currentVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"

    suspend fun check(context: Context): ReleaseInfo? = withContext(Dispatchers.IO) {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "FileBeam-Android")
            setRequestProperty("Cache-Control", "no-cache, no-store")
            setRequestProperty("Pragma", "no-cache")
        }
        try {
            if (connection.responseCode !in 200..299) {
                if (connection.responseCode == 404) return@withContext null
                throw IllegalStateException("GitHub yanıtı: HTTP ${connection.responseCode}")
            }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@withContext null
            val tag = release.optString("tag_name").removePrefix("v").trim()
            if (tag.isBlank() || !isNewer(tag, currentVersion(context))) return@withContext null
            val assets = release.optJSONArray("assets") ?: return@withContext null
            var apk: JSONObject? = null
            var checksum: JSONObject? = null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true) &&
                    (apk == null || name == "app-release.apk" || name == "FileBeam-release.apk")) apk = asset
                if (name.endsWith(".apk.sha256", ignoreCase = true) || name.endsWith(".sha256", ignoreCase = true)) checksum = asset
            }
            val selected = apk ?: throw IllegalStateException("Bu sürümde indirilebilir APK bulunamadı.")
            val checksumAsset = checksum ?: throw IllegalStateException("Release içinde APK SHA-256 doğrulama dosyası bulunamadı.")
            ReleaseInfo(
                version = tag,
                title = release.optString("name").ifBlank { "FileBeam $tag" },
                notes = release.optString("body").ifBlank { "Bu sürüm için henüz sürüm notu eklenmemiş." },
                pageUrl = release.optString("html_url"),
                apkUrl = selected.optString("browser_download_url"),
                apkName = selected.optString("name").ifBlank { "FileBeam-update.apk" },
                checksumUrl = checksumAsset.optString("browser_download_url").takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("SHA-256 doğrulama bağlantısı eksik."),
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadAndInstall(
        context: Context,
        release: ReleaseInfo,
        onProgress: (Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(target, "FileBeam-${release.version}.apk")
        download(release.apkUrl, apkFile, onProgress)
        release.checksumUrl?.let { checksumUrl ->
            val expected = readText(checksumUrl).trim().split(Regex("\\s+"), limit = 2).firstOrNull().orEmpty().lowercase()
            if (!expected.matches(Regex("[a-f0-9]{64}"))) {
                apkFile.delete()
                throw IllegalStateException("Release SHA-256 dosyası hatalı.")
            }
            val actual = sha256(apkFile)
            if (!actual.equals(expected, ignoreCase = true)) {
                apkFile.delete()
                throw IllegalStateException("İndirilen dosyanın SHA-256 doğrulaması başarısız.")
            }
        }
        withContext(Dispatchers.Main) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                val packageUri = Uri.parse("package:${context.packageName}")
                val permissionIntents = listOf(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri),
                    // Android TV firmwares may not implement the dedicated unknown-sources screen.
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
                    Intent(Settings.ACTION_SECURITY_SETTINGS),
                    Intent(Settings.ACTION_SETTINGS),
                )
                var opened = false
                for (permissionIntent in permissionIntents) {
                    try {
                        permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(permissionIntent)
                        opened = true
                        break
                    } catch (_: android.content.ActivityNotFoundException) {
                        // Try the next settings screen supported by this device.
                    } catch (_: SecurityException) {
                        // Some TV launchers restrict a settings destination; try a broader one.
                    }
                }
                throw InstallPermissionRequiredException(
                    if (opened) {
                        "Bu TV'de özel izin sayfası bulunamadıysa Ayarlar > Uygulamalar > Özel uygulama erişimi > Bilinmeyen uygulamaları yükle bölümünden FileBeam'e izin ver, sonra tekrar İndir ve kur'a dokun."
                    } else {
                        "Bu cihaz izin ayarlarını uygulama içinden açamıyor. TV Ayarları > Uygulamalar > Özel uygulama erişimi bölümünden FileBeam için bilinmeyen uygulama yükleme iznini elle aç."
                    }
                )
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(installIntent)
            } catch (_: android.content.ActivityNotFoundException) {
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(viewIntent)
                } catch (_: android.content.ActivityNotFoundException) {
                    throw IllegalStateException("Bu TV'de APK kurulumunu açacak sistem yükleyicisi bulunamadı. İndirilen APK dosyasını Dosya Yöneticisi'nden açarak kurmayı dene.")
                }
            }
        }
    }

    private fun download(url: String, destination: File, onProgress: (Int) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "FileBeam-Android")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("APK indirilemedi: HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var received = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        if (total > 0) onProgress(((received * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
            if (!destination.exists() || destination.length() == 0L) throw IllegalStateException("İndirilen APK boş.")
        } catch (error: Exception) {
            destination.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun readText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            setRequestProperty("User-Agent", "FileBeam-Android")
        }
        return try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("SHA-256 dosyası indirilemedi: HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isNewer(remote: String, local: String): Boolean {
        fun parts(value: String) = value.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }
}

class InstallPermissionRequiredException(message: String) : Exception(message)
