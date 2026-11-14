package com.lion.filebeam.mirror

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class ScreenMirrorReceiver(
    private val surface: Surface,
    private val pin: String,
    private val onStatus: (String) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private var server: ServerSocket? = null
    private var client: Socket? = null
    private var decoder: MediaCodec? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread(name = "FileBeam-mirror-receiver") {
            try {
                server = ServerSocket(ScreenMirrorService.MIRROR_PORT)
                onStatus("Telefon bağlantısı bekleniyor")
                client = server?.accept()
                val socket = client ?: return@thread
                socket.tcpNoDelay = true
                val input = DataInputStream(BufferedInputStream(socket.getInputStream(), 256 * 1024))
                if (input.readUTF() != pin) throw IllegalStateException("PIN hatalı")
                if (input.readInt() != MAGIC) throw IllegalStateException("Yayın biçimi tanınmadı")
                val width = input.readInt()
                val height = input.readInt()
                require(width in 2..1920 && height in 2..1920)
                val sps = ByteArray(input.readInt().also { require(it in 1..65536) })
                input.readFully(sps)
                val pps = ByteArray(input.readInt().also { require(it in 1..65536) })
                input.readFully(pps)
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                    setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(sps))
                    setByteBuffer("csd-1", java.nio.ByteBuffer.wrap(pps))
                }
                val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                decoder = codec
                codec.configure(format, surface, null, 0)
                codec.start()
                onStatus("Yansıtma başladı · ${width}×${height}")
                val info = MediaCodec.BufferInfo()
                while (running.get()) {
                    val size = input.readInt()
                    if (size < 0) break
                    require(size in 1..2_000_000) { "Video paketi geçersiz" }
                    val flags = input.readInt()
                    val pts = input.readLong()
                    val bytes = ByteArray(size)
                    input.readFully(bytes)
                    var inputIndex = codec.dequeueInputBuffer(10_000)
                    while (inputIndex < 0 && running.get()) inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val target = codec.getInputBuffer(inputIndex) ?: throw IllegalStateException("Video tamponu açılamadı")
                        target.clear()
                        target.put(bytes)
                        codec.queueInputBuffer(inputIndex, 0, bytes.size, pts, flags)
                    }
                    var outputIndex = codec.dequeueOutputBuffer(info, 0)
                    while (outputIndex >= 0) {
                        codec.releaseOutputBuffer(outputIndex, true)
                        outputIndex = codec.dequeueOutputBuffer(info, 0)
                    }
                }
            } catch (e: Exception) {
                if (running.get()) onStatus(e.message ?: "Yansıtma bağlantısı kesildi")
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { client?.close() }
        runCatching { server?.close() }
        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        decoder = null
    }

    companion object { private const val MAGIC = 0x46424D31 }
}
