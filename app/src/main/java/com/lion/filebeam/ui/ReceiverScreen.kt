@file:OptIn(ExperimentalMaterial3Api::class)

package com.lion.filebeam.ui

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.lion.filebeam.Discovery
import com.lion.filebeam.TransferClient
import com.lion.filebeam.TransferServer

@Composable
fun ReceiverScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val isTv = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    val pin = remember { (100000..999999).random().toString() }
    val ip = remember { Discovery.localIp() }
    var port by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val log = remember { mutableStateListOf<String>() }
    var incoming by remember { mutableStateOf<TransferClient.Progress?>(null) }
    var serverRef by remember { mutableStateOf<TransferServer?>(null) }
    val activity = context as? Activity

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            serverRef?.stop()
        }
    }
    LaunchedEffect(Unit) {
        val server = TransferServer(
            context, pin,
            onFileStart = { name, size ->
                incoming = TransferClient.Progress(name, 0, size, 0)
                log.add(0, "↓  " + name)
            },
            onProgress = { _, bytes, total, speed ->
                incoming = TransferClient.Progress("Aktarım sürüyor", bytes, total, speed)
            },
            onFileDone = { name ->
                incoming = null
                log.add(0, "✓  " + name + " alındı")
            },
            onError = { e -> error = e },
        )
        port = server.start()
        serverRef = server
        Discovery.broadcastLoop(port)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dosya al", fontWeight = FontWeight.Bold)
                        Text("Bu cihazı aktarım için hazırla", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                horizontal = if (isTv) 40.dp else 16.dp,
                vertical = 14.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Wifi, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(6.dp))
                            Text("BAĞLANTI BEKLENİYOR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Cihazın hazır", fontSize = if (isTv) 32.sp else 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Telefonundan QR kodu okut veya aşağıdaki PIN'i kullan.",
                        fontSize = if (isTv) 18.sp else 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(if (isTv) 24.dp else 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (ip.isNotBlank() && port > 0) {
                            QrImage(
                                content = "filebeam://" + ip + ":" + port + "#" + pin,
                                modifier = Modifier
                                    .size(if (isTv) 310.dp else 190.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White)
                                    .padding(7.dp),
                            )
                        } else {
                            Box(Modifier.size(if (isTv) 310.dp else 190.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Bağlanmak için QR kodu okut", fontWeight = FontWeight.SemiBold, fontSize = if (isTv) 19.sp else 14.sp)
                        Text(ip + ":" + port, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(if (isTv) 24.dp else 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("6 HANELİ GÜVENLİ PIN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(pin, fontSize = if (isTv) 58.sp else 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = if (isTv) 10.sp else 7.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Telefonunda manuel bağlantı için kullan", fontSize = if (isTv) 16.sp else 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                }
            }
            if (incoming != null) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Download, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Dosya alınıyor", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(incoming!!.fileName, maxLines = 2, fontSize = 14.sp)
                            LinearProgressIndicator(
                                progress = { if (incoming!!.total > 0) (incoming!!.bytes.toFloat() / incoming!!.total).coerceIn(0f, 1f) else 0f },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                            )
                            Text(
                                formatSize(incoming!!.bytes) + " / " + (if (incoming!!.total >= 0) formatSize(incoming!!.total) else "Bilinmiyor") + " · " + formatSpeed(incoming!!.speedBps),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            if (error != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text("⚠  " + error, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            if (log.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Son aktarımlar", fontWeight = FontWeight.Bold, fontSize = if (isTv) 20.sp else 16.sp)
                    }
                }
                items(log) { line ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text(line, modifier = Modifier.padding(13.dp), fontSize = if (isTv) 17.sp else 13.sp)
                    }
                }
            }
            item {
                Text(
                    "Dosyalar doğrudan yerel ağ üzerinden aktarılır; buluta yüklenmez.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = if (isTv) 15.sp else 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
fun QrImage(content: String, modifier: Modifier = Modifier) {
    val bmp = remember(content) {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 600, 600, hints)
        val w = bits.width
        val h = bits.height
        val px = IntArray(w * h) { i ->
            if (bits.get(i % w, i / w)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
    Image(bitmap = bmp.asImageBitmap(), contentDescription = "Bağlantı QR kodu", modifier = modifier)
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1e6)
    bytes >= 1_000 -> String.format("%.0f KB", bytes / 1e3)
    else -> bytes.toString() + " B"
}

fun formatSpeed(bps: Long): String = formatSize(bps) + "/s"
