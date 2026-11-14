@file:OptIn(ExperimentalMaterial3Api::class)

package com.lion.filebeam.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lion.filebeam.Discovery
import com.lion.filebeam.SettingsStore
import com.lion.filebeam.mirror.ScreenMirrorReceiver
import com.lion.filebeam.mirror.ScreenMirrorService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ScreenMirrorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isTv = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    var host by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(if (isTv) "TV bağlantı bekliyor" else "Yakındaki TV'ler aranıyor…") }
    var receiver by remember { mutableStateOf<ScreenMirrorReceiver?>(null) }
    val devices = remember { mutableStateListOf<Discovery.Beacon>() }
    val tvPin = remember { SettingsStore.mirrorPin(context) }
    var active by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val statusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.getStringExtra(ScreenMirrorService.EXTRA_STATUS)?.let { status = it }
            }
        }
        ContextCompat.registerReceiver(
            context,
            statusReceiver,
            IntentFilter(ScreenMirrorService.ACTION_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(statusReceiver) } }
    }

    BackHandler(enabled = isTv, onBack = onBack)

    DisposableEffect(activity, isTv) {
        val window = activity?.window
        val oldFlags = window?.attributes?.flags ?: 0
        if (isTv && window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            receiver?.stop()
            if (window != null && isTv) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                window.attributes = window.attributes.apply { flags = flags or (oldFlags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
        }
    }

    LaunchedEffect(isTv) {
        if (isTv) {
            Discovery.broadcastLoop(ScreenMirrorService.MIRROR_PORT)
        } else {
            Discovery.listen(context) { beacon ->
                if (beacon.port == ScreenMirrorService.MIRROR_PORT) {
                    val index = devices.indexOfFirst { it.ip == beacon.ip }
                    if (index >= 0) devices[index] = beacon else devices.add(beacon)
                    if (host.isBlank()) {
                        host = beacon.ip
                        SettingsStore.mirrorPinFor(context, beacon.ip)?.let { pinInput = it }
                    }
                }
            }
        }
    }

    val mirrorScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { contents ->
            val match = Regex("filebeam-mirror://([0-9.]+):([0-9]+)#([0-9]{6})").find(contents)
            if (match != null) {
                host = match.groupValues[1]
                pinInput = match.groupValues[3]
                SettingsStore.saveMirrorPair(context, host, pinInput)
                status = "TV QR koduyla eşleştirildi"
            } else {
                status = "Yansıtma QR kodu tanınmadı"
            }
        }
    }

    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            SettingsStore.saveMirrorPair(context, host.trim(), pinInput)
            val service = Intent(context, ScreenMirrorService::class.java).apply {
                putExtra(ScreenMirrorService.EXTRA_RESULT, result.resultCode)
                putExtra(ScreenMirrorService.EXTRA_DATA, result.data)
                putExtra(ScreenMirrorService.EXTRA_HOST, host.trim())
                putExtra(ScreenMirrorService.EXTRA_PIN, pinInput)
                putExtra(ScreenMirrorService.EXTRA_PROFILE, SettingsStore.mirrorProfile(context))
            }
            ContextCompat.startForegroundService(context, service)
            status = "TV'ye bağlanılıyor…"
        } else status = "Ekran paylaşım izni verilmedi"
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        } else {
            status = "Ses izni verilmedi · görüntü aktarımı yine başlatılabilir"
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        }
    }

    if (isTv) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        keepScreenOn = true
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                receiver?.stop()
                                receiver = ScreenMirrorReceiver(holder.surface, tvPin) { message ->
                                    status = message
                                    active = message.startsWith("Görüntü alınıyor") || message.startsWith("Yansıtma başladı")
                                }.also { it.start() }
                            }
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                receiver?.stop()
                                receiver = null
                                active = false
                            }
                        })
                    }
                }
            )
            if (!active) Surface(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(26.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri · çıkış", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FILEBEAM · TV HAZIR", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Text("Telefon kamerasıyla QR kodu okut", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        QrImage(
                            content = "filebeam-mirror://" + Discovery.localIp() + ":" + ScreenMirrorService.MIRROR_PORT + "#" + tvPin,
                            modifier = Modifier.size(190.dp).background(Color.White, RoundedCornerShape(14.dp)).padding(8.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("PIN  $tvPin", color = MaterialTheme.colorScheme.primary, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 5.sp)
                        Text(Discovery.localIp() + ":" + ScreenMirrorService.MIRROR_PORT, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
            if (!active) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text("Telefon ve TV aynı Wi-Fi ağına bağlı olmalı", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp))
                }
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Ekran yansıtma", fontWeight = FontWeight.Bold)
                            Text("Yakındaki FileBeam TV'leri", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                    actions = {
                        if (!isTv) IconButton(onClick = {
                            mirrorScanLauncher.launch(
                                ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    .setPrompt("TV ekranındaki yansıtma QR kodunu okut")
                                    .setBeepEnabled(false).setOrientationLocked(true)
                            )
                        }) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "QR ile eşleştir") }
                        TextButton(onClick = {
                            context.startService(Intent(context, ScreenMirrorService::class.java).setAction(ScreenMirrorService.ACTION_STOP))
                            status = "Yansıtma durduruldu"
                        }) { Text("Durdur") }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Cast, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Telefon ekranını TV'ye aktar", fontWeight = FontWeight.Bold)
                                Text("TV otomatik bulunur. QR koduyla ilk eşleştirme yapılır; sonraki bağlantılarda PIN hatırlanır.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item { Text(if (devices.isEmpty()) "TV aranıyor…" else "Bulunan TV'ler", fontWeight = FontWeight.Bold) }
                items(devices) { device ->
                    OutlinedButton(
                        onClick = {
                            host = device.ip
                            pinInput = SettingsStore.mirrorPinFor(context, device.ip).orEmpty()
                            status = if (pinInput.length == 6) "Eşleştirilmiş TV seçildi" else "TV seçildi · Ekrandaki PIN'i gir"
                        },
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                    ) {
                        Icon(Icons.Filled.Tv, null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text(device.name, fontWeight = FontWeight.SemiBold)
                            Text(device.ip, style = MaterialTheme.typography.labelSmall)
                        }
                        if (host == device.ip) Text("Seçili", color = MaterialTheme.colorScheme.primary)
                    }
                }
                item {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it.trim() },
                        label = { Text("TV IP adresi") },
                        placeholder = { Text("Otomatik bulunur") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.filter(Char::isDigit).take(6) },
                        label = { Text("TV PIN'i · ilk eşleştirmede") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Button(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= 29 &&
                                ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                            ) {
                                audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            } else {
                                val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                projectionLauncher.launch(manager.createScreenCaptureIntent())
                            }
                        },
                        enabled = host.isNotBlank() && pinInput.length == 6 && activity != null,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Filled.Cast, null)
                        Spacer(Modifier.width(9.dp))
                        Text("Tam ekran yansıtmayı başlat", fontWeight = FontWeight.Bold)
                    }
                }
                item {
                    Text(status, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Görüntü ve desteklenen uygulama sesleri TV'ye aktarılır. Android ekran ve ses izinleri gerekir; bazı uygulamalar ses paylaşımını engelleyebilir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
