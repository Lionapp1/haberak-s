@file:OptIn(ExperimentalMaterial3Api::class)

package com.lion.filebeam.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.lion.filebeam.Discovery
import com.lion.filebeam.TransferClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun SenderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val devices = remember { mutableStateListOf<Discovery.Beacon>() }
    var selected by remember { mutableStateOf<Discovery.Beacon?>(null) }
    var picked by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var progress by remember { mutableStateOf<TransferClient.Progress?>(null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var doneMsg by remember { mutableStateOf<String?>(null) }
    var manualIp by remember { mutableStateOf("") }
    var manualPin by remember { mutableStateOf("") }
    var sendJob by remember { mutableStateOf<Job?>(null) }

    val listenJob = remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) {
        listenJob.value = scope.launch {
            Discovery.listen { b ->
                val idx = devices.indexOfFirst { it.ip == b.ip }
                if (idx >= 0) devices[idx] = b else devices.add(b)
            }
        }
        onDispose { listenJob.value?.cancel() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        if (it.isNotEmpty()) picked = it
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { c ->
            Regex("filebeam://([0-9.]+):([0-9]+)#([0-9]+)").find(c)?.let { m ->
                selected = Discovery.Beacon("QR ile bağlanıldı", m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3])
                error = null
                doneMsg = null
            } ?: run { error = "QR anlaşılamadı" }
        }
    }

    fun startSend() {
        val dev = selected ?: return
        if (picked.isEmpty()) return
        sending = true
        error = null
        doneMsg = null
        sendJob = scope.launch {
            try {
                TransferClient.send(context, dev.ip, dev.port, dev.pin, picked) { p -> progress = p }
                doneMsg = picked.size.toString() + " dosya gönderildi 🎉"
                picked = emptyList()
            } catch (e: Exception) {
                error = e.message ?: "Gönderilemedi"
            }
            sending = false
            progress = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gönder", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = {
                    IconButton(onClick = {
                        scanLauncher.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("TV ekranındaki QR kodu okut")
                                .setBeepEnabled(false)
                                .setOrientationLocked(true)
                        )
                    }) { Icon(Icons.Filled.QrCodeScanner, "QR ile bağlan") }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Ağdaki cihazlar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))

            if (devices.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("TV'de FileBeam > Al ekranını aç", fontSize = 14.sp)
                        Text("Cihazlar burada otomatik listelenecek…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(0.4f)) {
                    items(devices) { d ->
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                selected = d; error = null; doneMsg = null
                            },
                            colors = if (selected?.ip == d.ip)
                                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            else CardDefaults.cardColors(),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("📺", fontSize = 20.sp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(d.name, fontWeight = FontWeight.SemiBold)
                                    Text(d.ip + ":" + d.port, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = manualIp,
                onValueChange = { manualIp = it },
                label = { Text("IP (manuel: 192.168.1.x)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = manualPin,
                onValueChange = { manualPin = it.take(6) },
                label = { Text("TV'deki PIN") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (manualIp.isNotBlank() && manualPin.length == 6) {
                        selected = Discovery.Beacon("Manuel bağlantı", manualIp.trim(), 47813, manualPin)
                        error = null; doneMsg = null
                    }
                },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Bağlan") }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = !sending,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (picked.isEmpty()) "Dosya Seç" else picked.size.toString() + " dosya seçildi — değiştir") }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { startSend() },
                enabled = !sending && selected != null && picked.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.Send, null)
                Spacer(Modifier.width(8.dp))
                Text(if (selected == null) "Önce cihaz seç" else "GÖNDER", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            progress?.let { p ->
                Spacer(Modifier.height(16.dp))
                Text(p.fileName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    progress = { if (p.total > 0) p.bytes.toFloat() / p.total else 0f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Text(
                    formatSize(p.bytes) + " / " + formatSize(p.total) + "  •  " + formatSpeed(p.speedBps),
                    fontSize = 13.sp,
                )
            }

            doneMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text("⚠ " + it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
    }
}
