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
                )
            }.sortedByDescending { it.timestamp }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun delete(context: Context, entry: TransferHistoryEntry) {
        runCatching {
            val uri = Uri.parse(entry.uri)
            if (uri.scheme == "content") context.contentResolver.delete(uri, null, null)
            else uri.path?.let { java.io.File(it).delete() }
        }
        val remaining = list(context).filterNot { it.uri == entry.uri && it.timestamp == entry.timestamp }
        val array = JSONArray()
        remaining.forEach { item ->
            array.put(JSONObject().apply {
                put("name", item.name)
                put("uri", item.uri)
                put("size", item.size)
                put("timestamp", item.timestamp)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    @Synchronized
    fun record(context: Context, name: String, uri: Uri, size: Long) {
        val items = list(context) + TransferHistoryEntry(name, uri.toString(), size, System.currentTimeMillis())
        val array = JSONArray()
        items.forEach { entry ->
            array.put(JSONObject().apply {
                put("name", entry.name)
                put("uri", entry.uri)
                put("size", entry.size)
                put("timestamp", entry.timestamp)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, array.toString()).apply()
        showNotification(context, name)
    }

    private fun showNotification(context: Context, name: String) {
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
            .setContentTitle("FileBeam · Dosya alındı")
            .setContentText(name)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification) }
    }
}
