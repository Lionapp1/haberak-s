@file:OptIn(ExperimentalMaterial3Api::class)

package com.lion.filebeam

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lion.filebeam.ui.ReceiverScreen
import com.lion.filebeam.ui.SenderScreen
import com.lion.filebeam.ui.ScreenMirrorScreen
import com.lion.filebeam.ui.SettingsScreen
import com.lion.filebeam.ui.HistoryScreen
import com.lion.filebeam.ui.UpdateScreen
import com.lion.filebeam.ui.theme.FileBeamTheme
import com.lion.filebeam.update.UpdateManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1905)
        }
        setContent {
            val context = LocalContext.current
            var themeMode by remember { mutableStateOf(SettingsStore.themeMode(context)) }
            var mirrorProfile by remember { mutableStateOf(SettingsStore.mirrorProfile(context)) }
            val darkTheme = when (themeMode) { "dark" -> true; "light" -> false; else -> androidx.compose.foundation.isSystemInDarkTheme() }
            FileBeamTheme(darkTheme = darkTheme) {
                var mode by remember { mutableStateOf("home") }
                BackHandler(enabled = mode != "home") { mode = "home" }
                when (mode) {
                    "send" -> SenderScreen(onBack = { mode = "home" })
                    "receive" -> ReceiverScreen(onBack = { mode = "home" })
                    "mirror" -> ScreenMirrorScreen(onBack = { mode = "home" })
                    "settings" -> SettingsScreen(themeMode, mirrorProfile, { v -> themeMode = v; SettingsStore.setThemeMode(context, v) }, { v -> mirrorProfile = v; SettingsStore.setMirrorProfile(context, v) }, { mode = "updates" }, { mode = "home" })
                    "history" -> HistoryScreen(onBack = { mode = "home" })
                    "updates" -> UpdateScreen(onBack = { mode = "settings" })
                    else -> HomeScreen(
                        onSend = { mode = "send" },
                        onReceive = { mode = "receive" },
                        onMirror = { mode = "mirror" },
                        onSettings = { mode = "settings" },
                        onHistory = { mode = "history" },
                        onUpdates = { mode = "updates" },
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onSend: () -> Unit, onReceive: () -> Unit, onMirror: () -> Unit, onSettings: () -> Unit, onHistory: () -> Unit, onUpdates: () -> Unit) {
    val context = LocalContext.current
    var updateVersion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try { updateVersion = UpdateManager.check(context)?.version } catch (_: Exception) { /* Keep the home screen available when offline. */ }
    }
    val isTv = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("F", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary, fontSize = 23.sp)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("FileBeam", fontWeight = FontWeight.Bold)
                            Text("Yerel ağda hızlı ve güvenli aktarım", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onHistory) { Icon(Icons.Filled.History, contentDescription = "Aktarım geçmişi") }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Ayarlar") }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = if (isTv) 48.dp else 20.dp, vertical = if (isTv) 30.dp else 20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().widthIn(max = if (isTv) 1180.dp else 900.dp)) {
                val wide = maxWidth >= 640.dp || isTv
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = if (isTv) 20.dp else 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape,
                    ) {
                        Text(
                            "AYNI WI-FI • DOĞRUDAN AKTARIM",
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = if (isTv) 14.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                    Spacer(Modifier.height(if (isTv) 28.dp else 22.dp))
                    updateVersion?.let { version ->
                        Card(
                            onClick = onUpdates,
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Yeni FileBeam sürümü hazır", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text("Sürüm $version · Ayrıntıları görüntüle", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall)
                                }
                                Text("GÜNCELLE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                    Text(
                        "Dosyalarını hızlıca\nve güvenle aktar.",
                        fontSize = if (isTv) 54.sp else if (wide) 38.sp else 30.sp,
                        lineHeight = if (isTv) 62.sp else if (wide) 44.sp else 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Fotoğraf, video ve belgelerini telefonunla Android TV arasında doğrudan aktar.",
                        fontSize = if (isTv) 21.sp else 15.sp,
                        lineHeight = if (isTv) 30.sp else 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = if (isTv) 760.dp else 560.dp),
                    )
                    Spacer(Modifier.height(if (isTv) 42.dp else 30.dp))
                    if (isTv) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(22.dp),
                        ) {
                            ModeCard("Gönder", "Telefonundan veya TV'nden dosya gönder", Icons.Filled.Send, onSend, Modifier.weight(1f).height(205.dp), true)
                            ModeCard("Al", "Dosyaları bu cihaza kaydet", Icons.Filled.Tv, onReceive, Modifier.weight(1f).height(205.dp), true)
                            ModeCard("Yansıt", "Telefon ekranını TV'de tam ekran göster", Icons.Filled.Cast, onMirror, Modifier.weight(1f).height(205.dp), true)
                        }
                    } else if (wide) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ModeCard("Gönder", "Bu cihazdan başka bir cihaza dosya yolla", Icons.Filled.Send, onSend, Modifier.weight(1f))
                            ModeCard("Al", "Bu cihazı dosya almak için hazırla", Icons.Filled.Tv, onReceive, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        ModeCard("Ekran yansıt", "Telefon ekranını aynı ağdaki Android TV'de göster", Icons.Filled.Cast, onMirror, Modifier.fillMaxWidth())
                    } else {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ModeCard("Gönder", "Bu cihazdan başka bir cihaza dosya yolla", Icons.Filled.Send, onSend)
                            ModeCard("Al", "Bu cihazı dosya almak için hazırla", Icons.Filled.Tv, onReceive)
                            ModeCard("Ekran yansıt", "Telefon ekranını aynı ağdaki Android TV'de göster", Icons.Filled.Cast, onMirror)
                        }
                    }
                    Spacer(Modifier.height(if (isTv) 28.dp else 22.dp))
                    Text(
                        "İnternet gerekmez  ·  Dosyaların buluta yüklenmez",
                        fontSize = if (isTv) 16.sp else 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun ModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isTv: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (isTv) 190.dp else 104.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(if (isTv) 26.dp else 20.dp),
            ),
        shape = RoundedCornerShape(if (isTv) 26.dp else 20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (focused) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (focused) 8.dp else 2.dp),
    ) {
        if (isTv) {
            Column(
                Modifier.fillMaxSize().padding(22.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(shape = RoundedCornerShape(15.dp), color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                    }
                }
                Column {
                    Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(subtitle, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                }
            }
        }
    }
}
