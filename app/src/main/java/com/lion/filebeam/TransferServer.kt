package com.lion.filebeam

import android.content.Context
import android.util.Base64
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest

/** TV / alıcı taraf: dosyaları uzunluk kontrollü veya EOF ile TCP üzerinden alır. */
class TransferServer(
    private val context: Context,
    private val pin: String,
    private val onFileStart: (name: String, size: Long) -> Unit,
    private val onProgress: (name: String, bytes: Long, total: Long, speedBps: Long) -> Unit,
    private val onFileDone: (name: String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var serverSocket: ServerSocket? = null
    private val activeClients = java.util.concurrent.ConcurrentHashMap.newKeySet<Socket>()
    @Volatile private var running = false

    fun start(): Int {
        check(!running) { "Sunucu zaten çalışıyor" }
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(Discovery.TCP_PORT))
        serverSocket = ss
        running = true
        Thread({
            while (running) {
                try {
                    val client = ss.accept()
                    activeClients.add(client)
                    Thread({
                        try {
                            handle(client)
                        } finally {
                            activeClients.remove(client)
                        }
                    }, "FileBeam-client").start()
                } catch (e: Exception) {
                    if (running) mainHandler.post { onError(e.message ?: "Sunucu hatası") }
                }
            }
        }, "FileBeam-server").start()
        return ss.localPort
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        activeClients.forEach { client -> runCatching { client.close() } }
        activeClients.clear()
        serverSocket = null
    }

    private fun handle(socket: Socket) {
        var savedUri: android.net.Uri? = null
        var output: java.io.OutputStream? = null
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 120_000
            val input = socket.getInputStream()
            val header = readHeader(input) ?: throw java.io.IOException("Bağlantı başlığı alınamadı")
            val parts = header.split("|")
            val response = socket.getOutputStream()

            if (parts.size != 4 || parts[0] != "BEAM2") {
                response.write("NO\n".toByteArray(Charsets.US_ASCII))
                response.flush()
                return
            }
            if (!MessageDigest.isEqual(parts[1].toByteArray(Charsets.UTF_8), pin.toByteArray(Charsets.UTF_8))) {
                response.write("PIN\n".toByteArray(Charsets.US_ASCII))
                response.flush()
                return
            }

            val expectedSize = parts[2].toLongOrNull()
                ?: throw java.io.IOException("Dosya boyutu geçersiz")
            if (expectedSize < -1L) throw java.io.IOException("Dosya boyutu geçersiz")

            val decodedName = try {
                String(Base64.decode(parts[3], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
            } catch (_: Exception) {
                throw java.io.IOException("Dosya adı çözümlenemedi")
            }
            val name = sanitizeFileName(decodedName)
            if (name.isBlank()) throw java.io.IOException("Dosya adı geçersiz")

            val (uri, fileOut) = FileSaver.create(context, name)
            savedUri = uri
            output = fileOut

            response.write("OK\n".toByteArray(Charsets.US_ASCII))
            response.flush()
            mainHandler.post { onFileStart(name, expectedSize) }

            val buffer = ByteArray(64 * 1024)
            var received = 0L
            var lastBytes = 0L
            var lastTime = System.currentTimeMillis()

            fileOut.use { out ->
                while (expectedSize < 0L || received < expectedSize) {
                    val wanted = if (expectedSize < 0L) buffer.size else minOf(
                        buffer.size.toLong(), expectedSize - received
                    ).toInt()
                    val count = input.read(buffer, 0, wanted)
                    if (count < 0) {
                        if (expectedSize >= 0L && received != expectedSize) {
                            throw java.io.IOException("Aktarım eksik: $received / $expectedSize bayt")
                        }
                        break
                    }
                    if (count == 0) continue
                    out.write(buffer, 0, count)
                    received += count
                    val now = System.currentTimeMillis()
                    if (now - lastTime >= 300) {
                        val speed = (received - lastBytes) * 1000 / (now - lastTime)
                        mainHandler.post { onProgress(name, received, expectedSize, speed) }
                        lastBytes = received
                        lastTime = now
                    }
                }
                out.flush()
            }
            output = null
            FileSaver.finish(context, uri)
            savedUri = null
            response.write("DONE\n".toByteArray(Charsets.US_ASCII))
            response.flush()
            mainHandler.post {
                onProgress(name, received, expectedSize, 0L)
                onFileDone(name)
            }
        } catch (e: Exception) {
            runCatching { output?.close() }
            savedUri?.let { FileSaver.delete(context, it) }
            mainHandler.post { onError(e.message ?: "Aktarım hatası") }
        } finally {
            runCatching { socket.close() }
        }
    }

    /**
     * Read exactly through LF without a buffered reader. A BufferedReader can read
     * ahead into the binary payload and silently consume the first file bytes.
     */
    private fun readHeader(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (bytes.size() <= MAX_HEADER_BYTES) {
            val value = input.read()
            if (value < 0) {
                if (bytes.size() == 0) return null
                throw java.io.IOException("Eksik aktarım başlığı")
            }
            if (value == 10) return bytes.toString(Charsets.UTF_8.name()).trimEnd('\r')
            bytes.write(value)
        }
        throw java.io.IOException("Aktarım başlığı çok uzun")
    }

    private fun sanitizeFileName(value: String): String {
        val leaf = value.replace('\\', '/').substringAfterLast('/')
        return leaf.replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
            .replace(Regex("[<>:\"|?*]"), "_")
            .trim().trim('.').take(240)
    }

    companion object {
        private const val MAX_HEADER_BYTES = 16 * 1024
    }
}
