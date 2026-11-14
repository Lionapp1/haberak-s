@file:OptIn(ExperimentalMaterial3Api::class)

package com.lion.filebeam.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lion.filebeam.update.UpdateManager

@Composable
fun SettingsScreen(
    themeMode: String,
    mirrorProfile: String,
    onThemeChange: (String) -> Unit,
    onMirrorProfileChange: (String) -> Unit,
    onUpdates: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val currentVersion = UpdateManager.currentVersion(context)

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
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val horizontal = if (isTv) 34.dp else 18.dp
            Column(
                Modifier.fillMaxWidth().widthIn(max = if (isTv) 1040.dp else 720.dp)
                    .align(Alignment.TopCenter).verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontal, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(id = com.lion.filebeam.R.drawable.ic_launcher_foreground),
                                    contentDescription = "FileBeam",
                                    modifier = Modifier.fillMaxSize().padding(7.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("FileBeam", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Sürüm $currentVersion · ${if (isTv) "Android TV" else "Android"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Yerel ağda doğrudan dosya aktarımı", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                SettingsSection(
                    icon = { Icon(Icons.Filled.Palette, null, tint = MaterialTheme.colorScheme.primary) },
                    title = "Görünüm",
                    description = "Telefon ve TV'de aynı renk sistemini kullan."
                ) {
                    listOf("system" to "Sistem varsayılanı", "light" to "Açık tema", "dark" to "Koyu tema").forEach { (value, label) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                        Triple("economy", "Ekonomik · 540p / 24 FPS", "Zayıf Wi-Fi ve eski TV'ler için daha hafif."),
                        Triple("balanced", "Uyumlu · 720p / 30 FPS", "Görüntü kalitesi ve uyumluluk dengesi."),
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
                        "H.264 kodlama kullanılır. Değişiklikler sonraki yansıtma oturumunda uygulanır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SettingsSection(
                    icon = { Icon(Icons.Filled.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary) },
                    title = "Uygulama güncellemesi",
                    description = "Resmî GitHub Releases sürümlerini kontrol et, APK'yı indir ve doğrula."
                ) {
                    Button(onClick = onUpdates, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.SystemUpdate, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Güncellemeleri kontrol et")
                    }
                }

                SettingsSection(
                    icon = { Icon(Icons.Filled.Security, null, tint = MaterialTheme.colorScheme.primary) },
                    title = "Dosya kurulum izni",
                    description = "APK dosyalarını FileBeam üzerinden açmak için Android'in izin ayarlarını yönet."
                ) {
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val uri = Uri.parse("package:${context.packageName}")
                                val intents = listOf(
                                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri),
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri),
                                    Intent(Settings.ACTION_SECURITY_SETTINGS),
                                    Intent(Settings.ACTION_SETTINGS),
                                )
                                var opened = false
                                for (intent in intents) {
                                    if (!opened) try {
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                        opened = true
                                    } catch (_: ActivityNotFoundException) {
                                    } catch (_: SecurityException) {
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Android izin ayarlarını aç") }
                    Text(
                        "Reeder TV bazı Android sürümlerinde özel izin sayfasını sunmayabilir. Böyle bir durumda TV Ayarları > Uygulamalar bölümünü veya Dosya Yöneticisi'ni kullan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SettingsSection(
                    icon = { Icon(Icons.Filled.Notifications, null, tint = MaterialTheme.colorScheme.primary) },
                    title = "Bildirimler",
                    description = "Aktarım tamamlandı bildirimlerini Android sistem ayarlarından yönet."
                ) {
                    OutlinedButton(
                        onClick = {
                            val intent = if (Build.VERSION.SDK_INT >= 26) {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Bildirim ayarlarını aç") }
                }

                SettingsSection(
                    icon = { Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.primary) },
                    title = "Bağlantı ve gizlilik",
                    description = "Aktarımlar yerel ağda doğrudan cihazlar arasında yapılır."
                ) {
                    Text("Dosyaların buluta yüklenmez. Ekran yansıtma yalnızca sen izin verdiğinde başlar. Telefon ve TV aynı Wi-Fi ağına bağlı olmalıdır.", style = MaterialTheme.typography.bodyMedium)
                }

                SettingsSection(
                    icon = { Icon(Icons.Filled.Tv, null, tint = MaterialTheme.colorScheme.primary) },
                    title = "Cihaz bilgisi",
                    description = if (isTv) "Bu cihaz FileBeam TV arayüzünü kullanıyor." else "Bu cihaz FileBeam telefon arayüzünü kullanıyor."
                ) {
                    Text("Uygulama kimliği: ${context.packageName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Sürüm: $currentVersion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) { icon() }
                }
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            content()
        }
    }
}
