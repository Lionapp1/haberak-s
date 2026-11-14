package com.lion.filebeam.mirror

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
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
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var audioQueue: ArrayBlockingQueue<ByteArray>? = null
    @Volatile private var audioPlaybackRunning = false

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
            when (input.readInt()) {
                PACKET_AUDIO_CONFIG -> {
                    val rate = input.readInt()
                    val channels = input.readInt()
                    val encoding = input.readInt()
                    val channelOut = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
                    val min = AudioTrack.getMinBufferSize(rate, channelOut, encoding)
                    if (min > 0) {
                        releaseAudio()
                        val track = AudioTrack.Builder()
                            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(channelOut)
                                .setEncoding(encoding).build())
                            .setBufferSizeInBytes(maxOf(min * 2, 16384))
                            .setTransferMode(AudioTrack.MODE_STREAM).build()
                        if (track.state == AudioTrack.STATE_INITIALIZED) {
                            audioTrack = track
                            val queue = ArrayBlockingQueue<ByteArray>(24)
                            audioQueue = queue
                            audioPlaybackRunning = true
                            track.play()
                            thread(name = "FileBeam-tv-audio") {
                                while (audioPlaybackRunning && running.get()) {
                                    val chunk = try { queue.poll(250, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { null }
                                        ?: continue
                                    var offset = 0
                                    while (offset < chunk.size && audioPlaybackRunning) {
                                        val written = runCatching {
                                            track.write(chunk, offset, chunk.size - offset, AudioTrack.WRITE_BLOCKING)
                                        }.getOrDefault(-1)
                                        if (written <= 0) break
                                        offset += written
                                    }
                                }
                            }
                        } else track.release()
                    }
                }
                PACKET_AUDIO -> {
                    val size = input.readInt()
                    require(size in 1..262144) { "Ses paketi boyutu geçersiz" }
                    val pcm = ByteArray(size)
                    input.readFully(pcm)
                    val queue = audioQueue
                    if (queue != null && !queue.offer(pcm)) {
                        queue.poll()
                        queue.offer(pcm)
                    }
                }
                PACKET_VIDEO -> {
                    val size = input.readInt()
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
                else -> throw IllegalStateException("Bilinmeyen aktarım paketi")
            }
        }
    }

    private fun releaseAudio() {
        audioPlaybackRunning = false
        audioQueue?.clear()
        audioQueue = null
        val track = audioTrack
        audioTrack = null
        if (track != null) {
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }

    private fun releaseDecoder() {
        releaseAudio()
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

    companion object { private const val MAGIC = 0x46424D32; private const val PACKET_VIDEO = 1; private const val PACKET_AUDIO_CONFIG = 2; private const val PACKET_AUDIO = 3 }
}
