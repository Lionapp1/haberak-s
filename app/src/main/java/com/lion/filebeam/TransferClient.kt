package com.lion.filebeam

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

/** Telefon / gönderici taraf: dosyaları TCP üzerinden güvenilir biçimde yollar. */
object TransferClient {
    data class Progress(val fileName: String, val bytes: Long, val total: Long, val speedBps: Long)

    suspend fun send(
        context: Context,
        ip: String,
        port: Int,
        pin: String,
        uris: List<Uri>,
        onProgress: (Progress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        require(port in 1..65535) { "Bağlantı portu geçersiz" }
        require(pin.length == 6 && pin.all(Char::isDigit)) { "PIN 6 rakam olmalı" }
        val resolver = context.contentResolver

        for (uri in uris) {
            val name = queryName(resolver, uri) ?: "dosya"
            val size = querySize(resolver, uri)
            val encodedName = Base64.encodeToString(
                name.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            val conn = Socket()
            try {
                conn.tcpNoDelay = true
                conn.connect(InetSocketAddress(ip, port), 10_000)
                conn.soTimeout = 120_000

                val out = BufferedOutputStream(conn.getOutputStream(), 256 * 1024)
                val reader = BufferedReader(InputStreamReader(conn.getInputStream(), Charsets.US_ASCII))
                out.write("BEAM2|$pin|$size|$encodedName\n".toByteArray(Charsets.US_ASCII))
                out.flush()

                when (reader.readLine()) {
                    "OK" -> Unit
                    "PIN" -> throw IOException("PIN hatalı — TV ekranındaki PIN ile tekrar dene")
                    else -> throw IOException("TV bağlantıyı reddetti")
                }

                val source = resolver.openInputStream(uri) ?: throw IOException("Dosya açılamadı")
                source.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var sent = 0L
                    var lastBytes = 0L
                    var lastTime = System.currentTimeMillis()

                    while (size < 0L || sent < size) {
                        val wanted = if (size < 0L) buffer.size else minOf(
                            buffer.size.toLong(), size - sent
                        ).toInt()
                        val count = input.read(buffer, 0, wanted)
                        if (count < 0) {
                            if (size >= 0L && sent != size) {
                                throw IOException("Kaynak dosya beklenenden kısa: $sent / $size bayt")
                            }
                            break
                        }
                        if (count == 0) continue
                        out.write(buffer, 0, count)
                        sent += count
                        val now = System.currentTimeMillis()
                        if (now - lastTime >= 300) {
                            onProgress(Progress(name, sent, size, (sent - lastBytes) * 1000 / (now - lastTime)))
                            lastBytes = sent
                            lastTime = now
                        }
                    }
                    out.flush()
                }

                // EOF terminates unknown-length transfers; for known sizes it also
                // prevents the receiver from waiting for any additional payload.
                conn.shutdownOutput()
                if (reader.readLine() != "DONE") throw IOException("TV dosyanın tamamlandığını onaylamadı")
                onProgress(Progress(name, if (size >= 0L) size else 0L, size, 0L))
            } finally {
                runCatching { conn.close() }
            }
        }
    }

    private fun queryName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    /** Content providers are allowed to report an unknown length; -1 means stream to EOF. */
    private fun querySize(resolver: ContentResolver, uri: Uri): Long =
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index).takeIf { it >= 0L } ?: -1L
            else -1L
        } ?: -1L
}
