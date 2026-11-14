package com.lion.filebeam.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lion.filebeam.TransferHistory
import com.lion.filebeam.TransferHistoryEntry
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val entries = remember { mutableStateListOf<TransferHistoryEntry>().apply { addAll(TransferHistory.list(context)) } }
    val selected = remember { mutableStateListOf<String>() }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)

    fun key(entry: TransferHistoryEntry) = "${entry.uri}#${entry.timestamp}"
    fun removeEntries(target: List<TransferHistoryEntry>) {
        TransferHistory.deleteAll(context, target)
        val keys = target.map { key(it) }.toSet()
        entries.removeAll { key(it) in keys }
        selected.clear()
        message = "${target.size} kayıt silindi."
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Aktarım geçmişi", fontWeight = FontWeight.Bold)
                        Text("${entries.size} dosya · Alınan ve gönderilen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri") } },
                actions = {
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = {
                            if (selected.size == entries.size) selected.clear()
                            else { selected.clear(); selected.addAll(entries.map(::key)) }
                        }) { Text(if (selected.size == entries.size) "Seçimi kaldır" else "Tümünü seç") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (entries.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${selected.size} seçildi", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { confirmDelete = true }, enabled = selected.isNotEmpty()) {
                        Icon(Icons.Filled.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Seçilenleri sil")
                    }
                    TextButton(onClick = { selected.clear(); selected.addAll(entries.map(::key)); confirmDelete = true }) {
                        Text("Tümünü sil", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            message?.let {
                Text(it, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Henüz aktarım yok", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Bu cihazdan gönderdiğin ve bu cihaza aldığın dosyalar burada listelenir.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(entries, key = { key(it) }) { entry ->
                        HistoryRow(
                            entry = entry,
                            checked = key(entry) in selected,
                            onChecked = { checked ->
                                if (checked) { if (key(entry) !in selected) selected.add(key(entry)) }
                                else selected.remove(key(entry))
                            },
                            onMessage = { message = it },
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val target = if (selected.isEmpty()) entries.toList() else entries.filter { key(it) in selected }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Aktarım kayıtlarını sil?") },
            text = { Text("${target.size} kayıt silinecek. Alınan dosyalar cihazdan da silinir; gönderdiğin orijinal dosyalar korunur.") },
            confirmButton = {
                TextButton(onClick = { removeEntries(target); confirmDelete = false }) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun HistoryRow(
    entry: TransferHistoryEntry,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val date = remember(entry.timestamp) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.timestamp)) }
    val isApk = entry.name.endsWith(".apk", ignoreCase = true)

    fun openFile() {
        val storedUri = Uri.parse(entry.uri)
        // Convert legacy file:// URIs into grantable content:// URIs for Android's installer.
        val uri = if (storedUri.scheme == "file") {
            val file = storedUri.path?.let { java.io.File(it) }
            if (file != null && file.exists()) {
                try {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                } catch (error: Exception) {
                    onMessage("APK dosyasına güvenli erişim sağlanamadı: ${error.message ?: "dosya yolu hatalı"}")
                    return
                }
            } else {
                onMessage("APK dosyası artık bu konumda bulunamıyor.")
                return
            }
        } else storedUri
        if (isApk) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                val settingsUri = Uri.parse("package:${context.packageName}")
                val settingsIntents = listOf(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, settingsUri),
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settingsUri),
                    Intent(Settings.ACTION_SECURITY_SETTINGS),
                    Intent(Settings.ACTION_SETTINGS),
                )
                var opened = false
                settingsIntents.forEach { intent ->
                    if (!opened) try {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        opened = true
                    } catch (_: ActivityNotFoundException) {
                    } catch (_: SecurityException) {
                    }
                }
                onMessage("Önce Ayarlar'dan FileBeam için bilinmeyen uygulama yükleme iznini aç, sonra APK'ya tekrar dokun.")
                return
            }
            val installerActions = listOf(Intent.ACTION_VIEW, Intent.ACTION_INSTALL_PACKAGE)
            var launched = false
            var lastError: Exception? = null
            for (action in installerActions) {
                if (launched) break
                try {
                    val installIntent = Intent(action).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        clipData = ClipData.newUri(context.contentResolver, entry.name, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(installIntent)
                    launched = true
                } catch (error: ActivityNotFoundException) {
                    lastError = error
                } catch (error: SecurityException) {
                    lastError = error
                }
            }
            if (!launched) {
                onMessage("TV'de APK yükleyicisi açılamadı. Dosya mevcut; TV'nin Dosya Yöneticisi'nden APK'yı açmayı dene. ${lastError?.message.orEmpty()}")
            }
        } else {
            val mime = context.contentResolver.getType(uri) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                clipData = ClipData.newUri(context.contentResolver, entry.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { context.startActivity(intent) }
            catch (_: Exception) { onMessage("Bu dosyayı açacak uyumlu uygulama bulunamadı.") }
        }
    }

    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().clickable { openFile() }.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = onChecked)
            Icon(
                if (isApk) Icons.Filled.Android else Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable { openFile() }) {
                Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text("${if (entry.direction == "sent") "Gönderildi" else "Alındı"} · $date · ${formatSize(entry.size)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isApk) Text("APK · Kurmak için dokun", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (isApk) {
                IconButton(onClick = { openFile() }) { Icon(Icons.Filled.InstallMobile, contentDescription = "APK kur") }
            }
        }
    }
}
