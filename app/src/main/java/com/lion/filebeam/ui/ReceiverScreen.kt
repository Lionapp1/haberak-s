@file:OptIn(ExperimentalMaterial3Api::class)

package com.lion.filebeam.ui

import android.app.Activity
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
                log.add(0, "â¬  " + name)
            },
            onProgress = { _, bytes, total, speed ->
                incoming = TransferClient.Progress("aktarÄ±m", bytes, total, speed)
            },
            onFileDone = { name ->
                incoming = null
                log.add(0, "â  " + name + " alÄ±ndÄ±")
            },
            onError = { e -> error = e },
        )
        port = server.start()
        serverRef = server
        Discovery.broadcastLoop(port, pin)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AlÄ±cÄ± â " + (android.os.Build.MODEL ?: "TV"), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Bu cihaz hazÄ±r ðº", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Telefonda FileBeam > GÃ¶nder > bu cihazÄ± seÃ§",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            if (ip.isNotBlank() && port > 0) {
                QrImage(
                    content = "filebeam://" + ip + ":" + port + "#" + pin,
                    modifier = Modifier.size(260.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(8.dp),
                )
            } else {
                CircularProgressIndicator()
            }

            Spacer(Modifier.height(16.dp))
            Text("PIN", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(pin, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
            Text(ip + ":" + port, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            incoming?.let { p ->
                Spacer(Modifier.height(16.dp))
                Text(p.fileName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    progress = { if (p.total > 0) p.bytes.toFloat() / p.total else 0f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Text(
                    formatSize(p.bytes) + " / " + formatSize(p.total) + "  â¢  " + formatSpeed(p.speedBps),
                    fontSize = 13.sp,
                )
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text("â  " + it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            if (log.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(log) { line ->
                        Text(line, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
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
    Image(bitmap = bmp.asImageBitmap(), contentDescription = "QR kod", modifier = modifier)
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1e6)
    bytes >= 1_000 -> String.format("%.0f KB", bytes / 1e3)
    else -> bytes.toString() + " B"
}

fun formatSpeed(bps: Long): String = formatSize(bps) + "/s"
