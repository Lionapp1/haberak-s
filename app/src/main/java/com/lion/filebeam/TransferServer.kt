package com.lion.filebeam

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/** TV / alıcı taraf: gelen dosyaları ham TCP üzerinden en yüksek hızda kabul eder. */
class TransferServer(
    private val context: Context,
    private val pin: String,
    private val onFileStart: (name: String, size: Long) -> Unit,
    private val onProgress: (name: String, bytes: Long, total: Long, speedBps: Long) -> Unit,
    private val onFileDone: (name: String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false

    fun start(): Int {
        running = true
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(Discovery.TCP_PORT))
        serverSocket = ss
        Thread {
            while (running) {
                try {
                    val client = ss.accept()
                    Thread { handle(client) }.start()
                } catch (e: Exception) {
                    if (running) onError(e.message ?: "Sunucu hatası")
                }
            }
        }.start()
        return ss.localPort
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
    }

    private fun handle(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 20_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val header = reader.readLine() ?: return
            val parts = header.split("|")
            val out = socket.getOutputStream()
            if (parts.size < 4 || parts[0] != "BEAM1") {
                out.write("NO\n".toByteArray()); out.flush(); return
            }
            if (parts[1] != pin) {
                out.write("PIN\n".toByteArray()); out.flush(); return
            }
            val name = parts[2]
            val size = parts[3].toLong()
            out.write("OK\n".toByteArray()); out.flush()

            onFileStart(name, size)
            val (savedUri, fileOut) = FileSaver.create(context, name)

            val input = socket.getInputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            var last = 0L
            var lastTime = System.currentTimeMillis()
            while (total < size) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), size - total).toInt())
                if (n < 0) break
                fileOut.write(buf, 0, n)
                total += n
                val now = System.currentTimeMillis()
                if (now - lastTime > 300) {
                    onProgress(name, total, size, (total - last) * 1000 / (now - lastTime))
                    last = total
                    lastTime = now
                }
            }
            fileOut.flush()
            fileOut.close()
            out.write("DONE\n".toByteArray()); out.flush()
            FileSaver.finish(context, savedUri)
            onFileDone(name)
        } catch (e: Exception) {
            onError(e.message ?: "Aktarım hatası")
        } finally {
            runCatching { socket.close() }
        }
    }
}
