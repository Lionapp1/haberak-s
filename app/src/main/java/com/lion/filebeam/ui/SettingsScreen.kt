@file:OptIn(ExperimentalMaterial3Api::class)

package com.lion.filebeam.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    themeMode: String,
    mirrorProfile: String,
    onThemeChange: (String) -> Unit,
    onMirrorProfileChange: (String) -> Unit,
    onUpdates: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ayarlar", fontWeight = FontWeight.Bold)
                        Text("FileBeam deneyimini kişiselleştir", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SettingsSection(
                icon = { Icon(Icons.Filled.Palette, null, tint = MaterialTheme.colorScheme.primary) },
                title = "Görünüm",
                description = "Uygulamanın açık veya koyu görünümünü seç."
            ) {
                listOf("system" to "Sistem varsayılanı", "light" to "Açık tema", "dark" to "Koyu tema").forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp)
                        RadioButton(selected = themeMode == value, onClick = { onThemeChange(value) })
                    }
                }
            }
            SettingsSection(
                icon = { Icon(Icons.Filled.HighQuality, null, tint = MaterialTheme.colorScheme.primary) },
                title = "Ekran yansıtma kalitesi",
                description = "Ağ ve TV performansına göre görüntü profilini belirle."
            ) {
                listOf(
                    Triple("economy", "Ekonomik · 540p / 24 FPS", "Zayıf Wi-Fi ve eski TV'lerde daha kararlı aktarım."),
                    Triple("balanced", "Uyumlu · 720p / 30 FPS", "Daha net görüntü ve geniş Android TV uyumluluğu."),
                ).forEach { (value, label, hint) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
                            Text(label, fontWeight = FontWeight.SemiBold)
                            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RadioButton(selected = mirrorProfile == value, onClick = { onMirrorProfileChange(value) })
                    }
                }
                Text(
                    "Uyumluluk için H.264 Baseline kullanılır. Değişiklikler sonraki oturumda uygulanır; gerçek performans cihaz ve Wi-Fi ağına bağlıdır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingsSection(
                icon = { Icon(Icons.Filled.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary) },
                title = "Uygulama güncellemesi",
                description = "GitHub Releases sürümlerini kontrol et. Yeni APK'yi indir, doğrula ve Android kurulumunu başlat."
            ) {
                Button(onClick = onUpdates, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.SystemUpdate, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Güncellemeleri kontrol et")
                }
            }
            SettingsSection(
                icon = { Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.primary) },
                title = "Bağlantı ve gizlilik",
                description = "Aktarımlar yerel ağda doğrudan cihazlar arasında yapılır. Dosyaların bir bulut sunucusuna gönderilmez."
            ) {
                Text("Ekran yansıtma yalnızca sen izin verdiğinde başlar. Android'in ekran paylaşımı bildirimi oturum boyunca görünür.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SettingsSection(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                icon()
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            content()
        }
    }
}
