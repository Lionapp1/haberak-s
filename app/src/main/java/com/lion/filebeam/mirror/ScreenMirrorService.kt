package com.lion.filebeam.mirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
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
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class ScreenMirrorService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var encoder: MediaCodec? = null
    private var projectionCallback: MediaProjection.Callback? = null
    @Volatile private var running = false
    @Volatile private var socket: Socket? = null

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
        val profile = intent?.getStringExtra(EXTRA_PROFILE) ?: "balanced"
        if (data == null || host.isBlank() || pin.length != 6) {
            publishStatus("Yansıtma başlatılamadı: TV adresi veya PIN eksik")
            stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification("TV'ye bağlanılıyor…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification("TV'ye bağlanılıyor…"))
        }
        running = true
        thread(name = "FileBeam-mirror-encoder") {
            try {
                startMirror(resultCode, data, host, pin, profile)
            } catch (e: Exception) {
                publishStatus("Yansıtma hatası: ${e.message ?: e.javaClass.simpleName}")
                stopMirror()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startMirror(resultCode: Int, data: Intent, host: String, pin: String, profile: String) {
        val client = Socket()
        socket = client
        client.tcpNoDelay = true
        client.keepAlive = true
        client.connect(InetSocketAddress(host, MIRROR_PORT), 15000)
        client.soTimeout = 0
        val out = DataOutputStream(BufferedOutputStream(client.getOutputStream(), 256 * 1024))
        out.writeUTF(pin)
        out.flush()
        publishStatus("TV bağlantısı kuruldu · görüntü hazırlanıyor")

        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                publishStatus("Ekran paylaşımı Android tarafından durduruldu")
                stopMirror()
                stopSelf()
            }
        }
        projection?.registerCallback(projectionCallback!!, android.os.Handler(mainLooper))

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        // 720p-first keeps the stream compatible with older Android TV decoders.
        val maxDimension = if (profile == "economy") 960f else 1280f
        val scale = minOf(1f, maxDimension / maxOf(metrics.widthPixels, metrics.heightPixels))
        val width = ((metrics.widthPixels * scale).toInt().coerceAtLeast(2) / 2) * 2
        val height = ((metrics.heightPixels * scale).toInt().coerceAtLeast(2) / 2) * 2
        val fps = if (profile == "economy") 24 else 30
        val bitrate = if (profile == "economy") 1_200_000 else 2_800_000

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            // Baseline AVC is widely supported by older Android TV hardware decoders.
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            if (Build.VERSION.SDK_INT >= 29) {
                setInteger(MediaFormat.KEY_LATENCY, 1)
                setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
            }
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder = codec
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = codec.createInputSurface()
        codec.start()
        virtualDisplay = projection?.createVirtualDisplay(
            "FileBeam Screen Mirror", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, inputSurface, null, null
        ) ?: error("Sanal ekran oluşturulamadı")

        publishStatus("Görüntü TV'ye aktarılıyor · ${width}×${height}")
        val info = MediaCodec.BufferInfo()
        var sentFormat = false
        while (running) {
            when (val index = codec.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val outputFormat = codec.outputFormat
                    val sps = outputFormat.getByteBuffer("csd-0")?.copyBytes() ?: ByteArray(0)
                    val pps = outputFormat.getByteBuffer("csd-1")?.copyBytes() ?: ByteArray(0)
                    check(sps.isNotEmpty() && pps.isNotEmpty()) { "H.264 kodlayıcı SPS/PPS bilgisi üretmedi" }
                    out.writeInt(MAGIC)
                    out.writeInt(width)
                    out.writeInt(height)
                    out.writeInt(sps.size)
                    out.write(sps)
                    out.writeInt(pps.size)
                    out.write(pps)
                    out.flush()
                    sentFormat = true
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                else -> if (index >= 0) {
                    try {
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
                    } finally {
                        codec.releaseOutputBuffer(index, false)
                    }
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

    private fun publishStatus(message: String) {
        val update = Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_STATUS, message)
        sendBroadcast(update)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(message))
    }

    private fun stopMirror() {
        running = false
        runCatching { socket?.close() }
        socket = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        encoder = null
        projectionCallback?.let { callback -> runCatching { projection?.unregisterCallback(callback) } }
        runCatching { projection?.stop() }
        projectionCallback = null
        projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopMirror()
        super.onDestroy()
    }

    private fun notification(message: String): Notification {
        val channelId = "filebeam_mirror"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "Ekran yansıtma", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId) else Notification.Builder(this)
        return builder.setContentTitle("FileBeam ekran yansıtma")
            .setContentText(message.take(100))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(running)
            .build()
    }

    companion object {
        const val MIRROR_PORT = 47814
        const val ACTION_STOP = "com.lion.filebeam.mirror.STOP"
        const val ACTION_STATUS = "com.lion.filebeam.mirror.STATUS"
        const val EXTRA_STATUS = "status_message"
        const val EXTRA_RESULT = "result_code"
        const val EXTRA_DATA = "projection_data"
        const val EXTRA_HOST = "host"
        const val EXTRA_PIN = "pin"
        const val EXTRA_PROFILE = "profile"
        private const val NOTIFICATION_ID = 47814
        private const val MAGIC = 0x46424D31
    }
}
