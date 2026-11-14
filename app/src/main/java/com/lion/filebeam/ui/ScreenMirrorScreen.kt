@file:OptIn(ExperimentalMaterial3Api::class)

package com.lion.filebeam.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.lion.filebeam.Discovery
import com.lion.filebeam.SettingsStore
import com.lion.filebeam.mirror.ScreenMirrorReceiver
import com.lion.filebeam.mirror.ScreenMirrorService

@Composable
fun ScreenMirrorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isTv = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    var host by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("TV bağlantısı için hazır") }
    var receiver by remember { mutableStateOf<ScreenMirrorReceiver?>(null) }
    val tvPin = remember { (100000..999999).random().toString() }

    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val service = Intent(context, ScreenMirrorService::class.java).apply {
                putExtra(ScreenMirrorService.EXTRA_RESULT, result.resultCode)
                putExtra(ScreenMirrorService.EXTRA_DATA, result.data)
                putExtra(ScreenMirrorService.EXTRA_HOST, host.trim())
                putExtra(ScreenMirrorService.EXTRA_PIN, pinInput)
                putExtra(ScreenMirrorService.EXTRA_PROFILE, SettingsStore.mirrorProfile(context))
            }
            ContextCompat.startForegroundService(context, service)
            status = "Ekran aktarımı başlatılıyor…"
        } else status = "Ekran paylaşım izni verilmedi"
    }

    DisposableEffect(Unit) {
        onDispose { receiver?.stop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("Ekran yansıtma", fontWeight = FontWeight.Bold)
                    Text(if (isTv) "Telefonunu bu TV'ye bağla" else "Telefon ekranını TV'de göster", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = {
                    if (!isTv) TextButton(onClick = {
                        context.startService(Intent(context, ScreenMirrorService::class.java).setAction(ScreenMirrorService.ACTION_STOP))
                        status = "Yansıtma durduruldu"
                    }) { Text("Durdur") }
                }
            )
        }
    ) { padding ->
        if (isTv) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("TV bağlantıya hazır", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Telefonunda bu TV'nin IP adresini ve PIN kodunu gir.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(horizontal = 28.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TV IP ADRESİ", style = MaterialTheme.typography.labelMedium)
                        Text(Discovery.localIp(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("BAĞLANTI PIN'İ", style = MaterialTheme.typography.labelMedium)
                        Text(tvPin, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp)
                    }
                }
                Text(status, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { ctx ->
                        SurfaceView(ctx).also { view ->
                            view.holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    receiver?.stop()
                                    receiver = ScreenMirrorReceiver(holder.surface, tvPin) { status = it }.also { it.start() }
                                }
                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                                override fun surfaceDestroyed(holder: SurfaceHolder) { receiver?.stop(); receiver = null }
                            })
                        }
                    }
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Cast, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Telefon ekranını TV'ye aktar", fontWeight = FontWeight.Bold)
                            Text("İki cihaz aynı Wi-Fi ağına bağlı olmalı.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                OutlinedTextField(
                    value = host, onValueChange = { host = it.trim() }, label = { Text("TV IP adresi") },
                    placeholder = { Text("192.168.1.25") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pinInput, onValueChange = { pinInput = it.filter(Char::isDigit).take(6) },
                    label = { Text("TV'de görünen 6 haneli PIN") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val manager = context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        projectionLauncher.launch(manager.createScreenCaptureIntent())
                    },
                    enabled = host.isNotBlank() && pinInput.length == 6 && activity != null,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) { Icon(Icons.Filled.Cast, null); Spacer(Modifier.width(8.dp)); Text("Yansıtmayı başlat") }
                Text(status, color = MaterialTheme.colorScheme.primary)
                Text("Yansıtma başladığında Android'in ekran paylaşımı onay penceresi açılır. Bildirim çubuğu ve hassas içerikler de ekrana yansıyabilir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
