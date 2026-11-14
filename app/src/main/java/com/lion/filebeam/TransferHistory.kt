package com.lion.filebeam

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

data class TransferHistoryEntry(
    val name: String,
    val uri: String,
    val size: Long,
    val timestamp: Long,
    val direction: String = "received",
)

object TransferHistory {
    private const val PREFS = "filebeam_transfer_history"
    private const val KEY_ITEMS = "items"
    private const val CHANNEL_ID = "filebeam_transfers"

    @Synchronized
    fun list(context: Context): List<TransferHistoryEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                TransferHistoryEntry(
                    name = item.optString("name"),
                    uri = item.optString("uri"),
                    size = item.optLong("size"),
                    timestamp = item.optLong("timestamp"),
                    direction = item.optString("direction", "received"),
                )
            }.sortedByDescending { it.timestamp }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun delete(context: Context, entry: TransferHistoryEntry) {
        // Deleting a sent-history row must never delete the user's original source file.
        if (entry.direction != "sent") deleteStoredFile(context, entry.uri)
        save(context, list(context).filterNot { it.uri == entry.uri && it.timestamp == entry.timestamp })
    }

    @Synchronized
    fun deleteAll(context: Context, entries: List<TransferHistoryEntry>) {
        entries.filter { it.direction != "sent" }.forEach { deleteStoredFile(context, it.uri) }
        val keys = entries.map { it.uri to it.timestamp }.toSet()
        save(context, list(context).filterNot { (it.uri to it.timestamp) in keys })
    }

    private fun deleteStoredFile(context: Context, rawUri: String) {
        runCatching {
            val uri = Uri.parse(rawUri)
            if (uri.scheme == "content") context.contentResolver.delete(uri, null, null)
            else uri.path?.let { java.io.File(it).delete() }
        }
    }

    private fun save(context: Context, entries: List<TransferHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { item ->
            array.put(JSONObject().apply {
                put("name", item.name)
                put("uri", item.uri)
                put("size", item.size)
                put("timestamp", item.timestamp)
                put("direction", item.direction)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    @Synchronized
    fun record(context: Context, name: String, uri: Uri, size: Long) {
        save(context, list(context) + TransferHistoryEntry(name, uri.toString(), size, System.currentTimeMillis(), "received"))
        showNotification(context, name, "FileBeam · Dosya alındı")
    }

    @Synchronized
    fun recordSent(context: Context, name: String, uri: Uri, size: Long) {
        save(context, list(context) + TransferHistoryEntry(name, uri.toString(), size, System.currentTimeMillis(), "sent"))
        showNotification(context, name, "FileBeam · Gönderim tamamlandı")
    }

    fun notifySent(context: Context, name: String) {
        showNotification(context, name, "FileBeam · Gönderim tamamlandı")
    }

    private fun showNotification(context: Context, name: String, title: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Dosya aktarımları", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            android.app.Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(name)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification) }
    }
}
