@file:OptIn(ExperimentalMaterial3Api::class)

package com.lion.filebeam.ui

import android.content.ContentValues
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ActivityNotFoundException
import android.widget.Toast
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.lion.filebeam.Discovery
import com.lion.filebeam.TransferClient
import com.lion.filebeam.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SenderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val isTv = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    val scope = rememberCoroutineScope()
    val devices = remember { mutableStateListOf<Discovery.Beacon>() }
    var picked by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var progress by remember { mutableStateOf<TransferClient.Progress?>(null) }
    var sending by remember { mutableStateOf(false) }
    var externalBusy by remember { mutableStateOf(false) }
    var autoSendPending by remember { mutableStateOf(false) }
    var externalMessage by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var doneMsg by remember { mutableStateOf<String?>(null) }
    var manualIp by remember { mutableStateOf(SettingsStore.savedTransferIp(context)) }
    var manualPin by remember { mutableStateOf(SettingsStore.savedTransferPin(context)) }
    var selected by remember {
        val savedIp = SettingsStore.savedTransferIp(context)
        val savedPin = SettingsStore.savedTransferPin(context)
        mutableStateOf(
            if (savedIp.isNotBlank() && savedPin.length == 6) {
                Discovery.Beacon("Kayıtlı cihaz", savedIp, Discovery.TCP_PORT, savedPin)
            } else null
        )
    }
    val listenJob = remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        listenJob.value = scope.launch {
            Discovery.listen(context) { b ->
                val pairedPin = SettingsStore.transferPinFor(context, b.ip)
                val discovered = if (pairedPin != null) b.copy(pin = pairedPin) else b
                val idx = devices.indexOfFirst { it.ip == b.ip }
                if (idx >= 0) devices[idx] = discovered else devices.add(discovered)
                if (selected?.ip == b.ip) selected = discovered.copy(pin = selected?.pin?.takeIf { it.length == 6 } ?: discovered.pin)
                if (selected == null && pairedPin != null) selected = discovered
            }
        }
        onDispose { listenJob.value?.cancel() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            val cutoff = System.currentTimeMillis() - 9000
            devices.removeAll { it.seen < cutoff }
            if (selected?.let { it.seen < cutoff && it.pin.isBlank() } == true) selected = null
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        if (it.isNotEmpty()) {
            picked = it
            externalMessage = null
            autoSendPending = true
        }
    }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { c ->
            Regex("filebeam://([0-9.]+):([0-9]+)#([0-9]+)").find(c)?.let { m ->
                SettingsStore.saveTransferPair(context, m.groupValues[1], m.groupValues[3])
                manualIp = m.groupValues[1]
                manualPin = m.groupValues[3]
                selected = Discovery.Beacon("QR ile bağlanıldı", m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3])
                error = null
                doneMsg = null
            } ?: run { error = "QR anlaşılamadı" }
        }
    }
    fun prepareForSfttv() {
        if (picked.isEmpty() || externalBusy || sending) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            externalMessage = "SFTTV aktarım hazırlığı Android 10 ve üzeri cihazlarda destekleniyor. Diğer uygulamalarla paylaş seçeneğini kullanabilirsin."
            return
        }
        externalBusy = true
        externalMessage = null
        scope.launch {
            try {
                val savedNames = withContext(Dispatchers.IO) {
                    picked.mapIndexed { index, uri ->
                        val originalName = context.contentResolver.query(
                            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        } ?: "dosya_${index + 1}"
                        val safeName = originalName
                            .substringAfterLast('/')
                            .map { character ->
                                if (character.isLetterOrDigit() || character == '.' || character == '-' || character == '_') {
                                    character
                                } else {
                                    '_'
                                }
                            }
                            .joinToString("")
                            .take(180)
                            .ifBlank { "file_" + (index + 1) }
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, System.currentTimeMillis().toString() + "_" + (index + 1) + "_" + safeName)
                            put(MediaStore.MediaColumns.MIME_TYPE, context.contentResolver.getType(uri) ?: "application/octet-stream")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FileBeam")
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                        val target = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            ?: error("Dosya İndirilenler/FileBeam klasörüne oluşturulamadı.")
                        try {
                            val input = context.contentResolver.openInputStream(uri)
                                ?: error("Seçilen dosya okunamadı: $originalName")
                            input.use { source ->
                                val output = context.contentResolver.openOutputStream(target, "w")
                                    ?: error("Hedef dosya yazılamadı: $originalName")
                                output.use { destination -> source.copyTo(destination, 128 * 1024) }
                            }
                            context.contentResolver.update(target, ContentValues().apply {
                                put(MediaStore.MediaColumns.IS_PENDING, 0)
                            }, null, null)
                            originalName
                        } catch (e: Exception) {
                            context.contentResolver.delete(target, null, null)
                            throw e
                        }
                    }
                }
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.yablio.sendfilestotv")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    externalMessage = "${savedNames.size} dosya İndirilenler/FileBeam klasörüne hazırlandı. SFTTV'de GÖNDER bölümünü açıp bu klasörden dosyaları seç; TV'de ise AL ekranı açık olsun."
                } else {
                    externalMessage = "Dosyalar İndirilenler/FileBeam klasörüne hazırlandı. Send Files to TV telefonda kurulu değil; önce Yablio'nun resmî uygulamasını yükle."
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.yablio.sendfilestotv")))
                    } catch (_: Exception) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.yablio.sendfilestotv")))
                    }
                }
            } catch (e: Exception) {
                externalMessage = "SFTTV hazırlığı başarısız: ${e.message ?: "Dosyalar kopyalanamadı"}"
            } finally {
                externalBusy = false
            }
        }
    }

    fun shareWithOtherApps() {
        if (picked.isEmpty()) return
        try {
            val shareIntent = if (picked.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = context.contentResolver.getType(picked.first()) ?: "*/*"
                    putExtra(Intent.EXTRA_STREAM, picked.first())
                    clipData = ClipData.newUri(context.contentResolver, "FileBeam", picked.first())
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(picked))
                    val clip = ClipData.newUri(context.contentResolver, "FileBeam", picked.first())
                    picked.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                    clipData = clip
                }
            }.apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "Dosyaları hangi uygulamayla göndermek istiyorsun?")
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Bu dosyaları paylaşabilecek bir uygulama bulunamadı.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Paylaşım başlatılamadı.", Toast.LENGTH_LONG).show()
        }
    }

    fun startSend() {
        val dev = selected ?: return
        if (picked.isEmpty()) return
        sending = true
        error = null
        doneMsg = null
        scope.launch {
            try {
                TransferClient.send(context, dev.ip, dev.port, dev.pin.ifBlank { manualPin }, picked) { p ->
                    scope.launch { progress = p }
                }
                doneMsg = picked.size.toString() + " dosya başarıyla gönderildi"
                picked = emptyList()
            } catch (e: Exception) {
                error = e.message ?: "Gönderilemedi"
            }
            sending = false
            progress = null
        }
    }

    LaunchedEffect(autoSendPending, picked, selected, sending) {
        if (autoSendPending && picked.isNotEmpty() && selected?.pin?.length == 6 && !sending) {
            autoSendPending = false
            startSend()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dosya gönder", fontWeight = FontWeight.Bold)
                        Text("Yakındaki cihaza aktar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = {
                    if (!isTv) {
                        IconButton(onClick = {
                            scanLauncher.launch(
                                ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    .setPrompt("TV ekranındaki QR kodu okut")
                                    .setBeepEnabled(false).setOrientationLocked(true)
                            )
                        }) { Icon(Icons.Filled.QrCodeScanner, "QR ile bağlan") }
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = if (isTv) 40.dp else 16.dp,
                end = if (isTv) 40.dp else 16.dp,
                top = 12.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Nereye gönderelim?", fontSize = if (isTv) 27.sp else 21.sp, fontWeight = FontWeight.Bold)
                    Text("Aynı Wi-Fi'daki FileBeam cihazları otomatik bulunur. Eşleştirilmiş TV'ye dosya seçtiğinde gönderim kendiliğinden başlar.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = if (isTv) 17.sp else 13.sp)
                }
            }
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Tv, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(9.dp))
                        Text("Cihazlar", fontWeight = FontWeight.SemiBold, fontSize = if (isTv) 20.sp else 16.sp)
                        Spacer(Modifier.weight(1f))
                        Text(if (devices.isEmpty()) "Aranıyor…" else "${devices.size} bulundu", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (devices.isEmpty()) {
                        Text("TV'de FileBeam uygulamasını açıp Al ekranına geç.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = if (isTv) 17.sp else 13.sp)
                    } else {
                        devices.forEach { d ->
                            val active = selected?.ip == d.ip
                            Surface(
                                onClick = { selected = d; error = null; doneMsg = null },
                                shape = RoundedCornerShape(14.dp),
                                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            ) {
                                Row(
                                    Modifier.padding(if (isTv) 18.dp else 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.Tv, null, tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(d.name, fontWeight = FontWeight.SemiBold, fontSize = if (isTv) 18.sp else 14.sp)
                                        Text(d.ip + ":" + d.port, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (active) Icon(Icons.Filled.CheckCircle, "Seçildi", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Bağlantı bilgileri", fontWeight = FontWeight.SemiBold, fontSize = if (isTv) 20.sp else 16.sp)
                            Text(
                                if (selected?.pin?.length == 6) "Cihaz eşleştirildi · bilgiler bu telefonda kayıtlı" else "TV IP adresi ve PIN'i bir kez kaydet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (selected?.pin?.length == 6) {
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text("KAYITLI", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        label = { Text("TV IP adresi") },
                        placeholder = { Text("192.168.1.25") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = if (isTv) 18.sp else 15.sp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualPin,
                        onValueChange = { manualPin = it.filter(Char::isDigit).take(6) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        label = { Text("6 haneli PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = if (isTv) 18.sp else 15.sp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (manualIp.isNotBlank() && manualPin.length == 6) {
                                val cleanIp = manualIp.trim()
                                SettingsStore.saveTransferPair(context, cleanIp, manualPin)
                                manualIp = cleanIp
                                selected = Discovery.Beacon("Kayıtlı cihaz", cleanIp, Discovery.TCP_PORT, manualPin)
                                error = null
                                doneMsg = null
                            }
                        },
                        enabled = manualIp.isNotBlank() && manualPin.length == 6,
                        modifier = Modifier.fillMaxWidth().height(if (isTv) 58.dp else 46.dp),
                    ) { Text(if (selected?.ip == manualIp.trim() && selected?.pin == manualPin) "Bağlantıyı güncelle" else "Cihazı kaydet ve bağlan") }
                    if (SettingsStore.savedTransferIp(context).isNotBlank()) {
                        TextButton(
                            onClick = {
                                SettingsStore.clearTransferPair(context)
                                manualIp = ""
                                manualPin = ""
                                selected = null
                                autoSendPending = false
                            },
                            modifier = Modifier.align(Alignment.End),
                        ) { Text("Kayıtlı cihazı unut") }
                    }
                }
            }
            item {
                SectionCard {
                    Text("Gönderilecek dosyalar", fontWeight = FontWeight.SemiBold, fontSize = if (isTv) 20.sp else 16.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(if (picked.isEmpty()) "Henüz dosya seçilmedi." else "${picked.size} dosya seçildi · ${if (selected?.pin?.length == 6) "eşleşmiş cihaza otomatik gönderim hazır" else "cihaz eşleşmesi bekleniyor"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = if (isTv) 17.sp else 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        enabled = !sending && !externalBusy,
                        modifier = Modifier.fillMaxWidth().height(if (isTv) 62.dp else 50.dp),
                    ) { Text(if (picked.isEmpty()) "Dosya seç · otomatik gönder" else "Dosyaları değiştir") }
                    Spacer(Modifier.height(9.dp))
                    Button(
                        onClick = { startSend() },
                        enabled = !sending && selected != null && picked.isNotEmpty() && (selected?.pin?.length == 6 || manualPin.length == 6),
                        modifier = Modifier.fillMaxWidth().height(if (isTv) 68.dp else 52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Send, null)
                        Spacer(Modifier.width(9.dp))
                        Text(if (sending) "Gönderiliyor…" else "Gönderimi başlat", fontWeight = FontWeight.Bold, fontSize = if (isTv) 19.sp else 15.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { prepareForSfttv() },
                        enabled = picked.isNotEmpty() && !sending && !externalBusy && !isTv,
                        modifier = Modifier.fillMaxWidth().height(if (isTv) 58.dp else 50.dp),
                    ) {
                        if (externalBusy) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Tv, null)
                        Spacer(Modifier.width(9.dp))
                        Text(if (externalBusy) "SFTTV için hazırlanıyor…" else "SFTTV için hazırla ve aç", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Seçtiğin dosyalar İndirilenler/FileBeam klasörüne kopyalanır ve SFTTV açılır. SFTTV'de GÖNDER, televizyonda AL seçeneğini kullan.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { shareWithOtherApps() },
                        enabled = picked.isNotEmpty() && !sending && !externalBusy,
                        modifier = Modifier.fillMaxWidth().height(if (isTv) 56.dp else 46.dp),
                    ) {
                        Icon(Icons.Filled.Share, null)
                        Spacer(Modifier.width(9.dp))
                        Text("Diğer uygulamalarla paylaş", fontWeight = FontWeight.SemiBold)
                    }
                    if (externalMessage != null) {
                        Text(externalMessage!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (progress != null) {
                item {
                    SectionCard {
                        val p = progress!!
                        Text(p.fileName, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        LinearProgressIndicator(
                            progress = { if (p.total > 0) (p.bytes.toFloat() / p.total).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        )
                        Text(formatSize(p.bytes) + " / " + (if (p.total >= 0) formatSize(p.total) else "Bilinmiyor") + " · " + formatSpeed(p.speedBps), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (doneMsg != null) item { StatusMessage(doneMsg!!, false) }
            if (error != null) item { StatusMessage(error!!, true) }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun StatusMessage(message: String, isError: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            (if (isError) "⚠  " else "✓  ") + message,
            modifier = Modifier.padding(14.dp),
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            fontSize = 13.sp,
        )
    }
}
