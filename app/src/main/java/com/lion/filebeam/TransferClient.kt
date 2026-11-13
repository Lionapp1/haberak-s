package com.lion.filebeam

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

/** Telefon / gönderici taraf: dosyaları ham TCP ile uçtan uca yollar. */
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
        val resolver = context.contentResolver
        for (uri in uris) {
            val name = queryName(resolver, uri) ?: "dosya"
            val pfd = resolver.openFileDescriptor(uri, "r") ?: throw IOException("Dosya açılamadı")
            val size = pfd.statSize
            pfd.close()

            val conn = Socket()
            try {
                conn.tcpNoDelay = true
                conn.connect(InetSocketAddress(ip, port), 8000)
                conn.soTimeout = 20_000
                val out = BufferedOutputStream(conn.getOutputStream(), 256 * 1024)
                val reader = BufferedReader(InputStreamReader(conn.getInputStream(), Charsets.UTF_8))

                out.write(("BEAM1|" + pin + "|" + name + "|" + size + "\n").toByteArray(Charsets.UTF_8))
                out.flush()
                when (reader.readLine()) {
                    "OK" -> {}
                    "PIN" -> throw IOException("PIN hatalı — TV ekranındaki PIN ile tekrar dene")
                    else -> throw IOException("TV bağlantıyı reddetti")
                }

                resolver.openInputStream(uri)?.use { ins ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    var last = 0L
                    var lastTime = System.currentTimeMillis()
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        total += n
                        val now = System.currentTimeMillis()
                        if (now - lastTime > 300) {
                            onProgress(Progress(name, total, size, (total - last) * 1000 / (now - lastTime)))
                            last = total
                            lastTime = now
                        }
                    }
                    out.flush()
                }
                val done = reader.readLine()
                if (done != "DONE") throw IOException("Aktarım onaylanmadı")
            } finally {
                runCatching { conn.close() }
            }
        }
    }

    private fun queryName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
}
