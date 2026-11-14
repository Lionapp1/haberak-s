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
    @Volatile private var server: ServerSocket? = null
    @Volatile private var client: Socket? = null
    @Volatile private var decoder: MediaCodec? = null

    fun start() {
        if (!surface.isValid || !running.compareAndSet(false, true)) return
        thread(name = "FileBeam-mirror-receiver") {
            try {
                val listener = ServerSocket()
                server = listener
                listener.reuseAddress = true
                listener.bind(java.net.InetSocketAddress(ScreenMirrorService.MIRROR_PORT), 8)
                onStatus("TV hazır · telefon bağlantısı bekleniyor")
                while (running.get()) {
                    val accepted = listener.accept()
                    client = accepted
                    accepted.tcpNoDelay = true
                    accepted.keepAlive = true
                    try {
                        receiveStream(accepted)
                    } catch (e: Exception) {
                        if (running.get()) onStatus("Bağlantı kesildi: ${e.message ?: "yeniden bağlanmayı bekliyor"}")
                    } finally {
                        runCatching { accepted.close() }
                        client = null
                        releaseDecoder()
                    }
                }
            } catch (e: Exception) {
                if (running.get()) onStatus("TV alıcısı açılamadı: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                stop()
            }
        }
    }

    private fun receiveStream(socket: Socket) {
        val input = DataInputStream(BufferedInputStream(socket.getInputStream(), 256 * 1024))
        if (input.readUTF() != pin) throw IllegalStateException("PIN hatalı")
        if (input.readInt() != MAGIC) throw IllegalStateException("H.264 yayın başlığı tanınmadı")
        val width = input.readInt()
        val height = input.readInt()
        require(width in 2..1920 && height in 2..1920) { "Görüntü boyutu desteklenmiyor" }
        val spsSize = input.readInt()
        require(spsSize in 1..65536) { "SPS bilgisi geçersiz" }
        val sps = ByteArray(spsSize).also(input::readFully)
        val ppsSize = input.readInt()
        require(ppsSize in 1..65536) { "PPS bilgisi geçersiz" }
        val pps = ByteArray(ppsSize).also(input::readFully)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(sps))
            setByteBuffer("csd-1", java.nio.ByteBuffer.wrap(pps))
        }
        val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        decoder = codec
        codec.configure(format, surface, null, 0)
        codec.start()
        // Keep the source aspect ratio; never stretch a portrait phone frame across a 16:9 TV.
        codec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT)
        onStatus("Görüntü alınıyor · ${width}×${height}")

        val info = MediaCodec.BufferInfo()
        while (running.get() && !socket.isClosed) {
            val size = try { input.readInt() } catch (_: java.io.EOFException) { break }
            if (size < 0) break
            require(size in 1..2_000_000) { "Video paketi boyutu geçersiz" }
            val flags = input.readInt()
            val pts = input.readLong()
            val bytes = ByteArray(size)
            input.readFully(bytes)
            var inputIndex = codec.dequeueInputBuffer(10_000)
            while (inputIndex < 0 && running.get()) inputIndex = codec.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
                val target = codec.getInputBuffer(inputIndex) ?: error("TV video tamponu açılamadı")
                target.clear()
                require(bytes.size <= target.remaining()) { "Video karesi çözücü tamponundan büyük" }
                target.put(bytes)
                codec.queueInputBuffer(inputIndex, 0, bytes.size, pts, flags)
            }
            var outputIndex = codec.dequeueOutputBuffer(info, 0)
            while (outputIndex >= 0) {
                codec.releaseOutputBuffer(outputIndex, true)
                outputIndex = codec.dequeueOutputBuffer(info, 0)
            }
        }
    }

    private fun releaseDecoder() {
        val current = decoder
        decoder = null
        if (current != null) {
            runCatching { current.stop() }
            runCatching { current.release() }
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        runCatching { client?.close() }
        runCatching { server?.close() }
        releaseDecoder()
    }

    companion object { private const val MAGIC = 0x46424D31 }
}
