package com.lion.filebeam

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

object FileSaver {
    fun create(context: Context, name: String): Pair<Uri, OutputStream> {
        val safeName = name.replace('\\', '/').substringAfterLast('/')
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
            .replace(Regex("[<>:\"|?*]"), "_")
            .trim().trim('.').take(240)
            .ifBlank { "filebeam-file" }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime(safeName))
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Kayıt yeri oluşturulamadı")
            try {
                val out = resolver.openOutputStream(uri) ?: throw IOException("Dosya açılamadı")
                uri to out
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            if (!dir.exists() && !dir.mkdirs()) throw IOException("Kayıt klasörü oluşturulamadı")
            val file = File(dir, safeName)
            Uri.fromFile(file) to FileOutputStream(file)
        }
    }

    fun finish(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    fun delete(context: Context, uri: Uri) {
        if (uri.scheme == "content") {
            context.contentResolver.delete(uri, null, null)
        } else {
            uri.path?.let { File(it).delete() }
        }
    }

    private fun mime(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }
}
