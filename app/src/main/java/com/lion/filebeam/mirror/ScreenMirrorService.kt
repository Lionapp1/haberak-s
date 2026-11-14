package com.lion.filebeam.mirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class ScreenMirrorService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var encoder: MediaCodec? = null
    @Volatile private var running = false
    private var socket: Socket? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMirror()
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") (intent?.getParcelableExtra(EXTRA_DATA) as? Intent)
        val host = intent?.getStringExtra(EXTRA_HOST).orEmpty()
        val pin = intent?.getStringExtra(EXTRA_PIN).orEmpty()
        if (data == null || host.isBlank() || pin.length != 6) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        thread(name = "FileBeam-mirror-encoder") {
            try {
                startMirror(resultCode, data, host, pin)
            } catch (_: Exception) {
                stopMirror()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startMirror(resultCode: Int, data: Intent, host: String, pin: String) {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val scale = minOf(1f, 1280f / maxOf(metrics.widthPixels, metrics.heightPixels))
        val width = (metrics.widthPixels * scale).toInt().coerceAtLeast(2) / 2 * 2
        val height = (metrics.heightPixels * scale).toInt().coerceAtLeast(2) / 2 * 2

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 3_500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            if (Build.VERSION.SDK_INT >= 29) setInteger(MediaFormat.KEY_LATENCY, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder = codec
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = codec.createInputSurface()
        codec.start()
        virtualDisplay = projection?.createVirtualDisplay(
            "FileBeam Screen Mirror", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, inputSurface, null, null
        )
        running = true

        val client = Socket()
        socket = client
        client.tcpNoDelay = true
        client.connect(java.net.InetSocketAddress(host, MIRROR_PORT), 8000)
        val out = DataOutputStream(BufferedOutputStream(client.getOutputStream(), 256 * 1024))
        out.writeUTF(pin)
        out.flush()

        val info = MediaCodec.BufferInfo()
        var sentFormat = false
        while (running) {
            when (val index = codec.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val outputFormat = codec.outputFormat
                    val sps = outputFormat.getByteBuffer("csd-0")?.copyBytes() ?: ByteArray(0)
                    val pps = outputFormat.getByteBuffer("csd-1")?.copyBytes() ?: ByteArray(0)
                    out.writeInt(MAGIC)
                    out.writeInt(width); out.writeInt(height)
                    out.writeInt(sps.size); out.write(sps)
                    out.writeInt(pps.size); out.write(pps)
                    out.flush()
                    sentFormat = true
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                else -> if (index >= 0) {
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer != null && info.size > 0 && sentFormat &&
                        info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val bytes = ByteArray(info.size)
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.get(bytes)
                        out.writeInt(bytes.size)
                        out.writeInt(info.flags)
                        out.writeLong(info.presentationTimeUs)
                        out.write(bytes)
                        out.flush()
                    }
                    codec.releaseOutputBuffer(index, false)
                }
            }
        }
    }

    private fun ByteBuffer.copyBytes(): ByteArray {
        val copy = duplicate()
        val bytes = ByteArray(copy.remaining())
        copy.get(bytes)
        return bytes
    }

    private fun stopMirror() {
        running = false
        runCatching { socket?.close() }
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        encoder = null
        runCatching { projection?.stop() }
        projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopMirror()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val channelId = "filebeam_mirror"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "Ekran yansıtma", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId) else Notification.Builder(this)
        return builder.setContentTitle("FileBeam ekran yansıtma")
            .setContentText("Ekranın TV'ye aktarılıyor")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val MIRROR_PORT = 47814
        const val ACTION_STOP = "com.lion.filebeam.mirror.STOP"
        const val EXTRA_RESULT = "result_code"
        const val EXTRA_DATA = "projection_data"
        const val EXTRA_HOST = "host"
        const val EXTRA_PIN = "pin"
        private const val NOTIFICATION_ID = 47814
        private const val MAGIC = 0x46424D31
    }
}
