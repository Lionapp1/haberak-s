@file:OptIn(ExperimentalMaterial3Api::class)

package com.lion.filebeam.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.lion.filebeam.update.InstallPermissionRequiredException
import com.lion.filebeam.update.ReleaseInfo
import com.lion.filebeam.update.UpdateManager
import kotlinx.coroutines.launch

@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Güncellemeler kontrol edilmeye hazır.") }
    var error by remember { mutableStateOf<String?>(null) }
    val current = remember { UpdateManager.currentVersion(context) }

    fun checkUpdates() {
        if (checking || downloading) return
        checking = true
        error = null
        status = "GitHub Releases kontrol ediliyor…"
        scope.launch {
            try {
                release = UpdateManager.check(context)
                status = if (release == null) "Şu anda en güncel sürümü kullanıyorsun." else "Yeni sürüm hazır: ${release!!.version}"
            } catch (e: Exception) {
                error = e.message ?: "Güncelleme kontrol edilemedi."
                status = "Kontrol tamamlanamadı."
            } finally {
                checking = false
            }
        }
    }

    LaunchedEffect(Unit) { checkUpdates() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Uygulama güncellemesi", fontWeight = FontWeight.Bold)
                        Text("GitHub Releases üzerinden güvenli sürüm takibi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                        Column(Modifier.weight(1f)) {
                            Text("FileBeam", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text("Yüklü sürüm: $current", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(status, fontWeight = FontWeight.SemiBold)
                    if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (downloading) {
                        LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("APK indiriliyor: %$progress", style = MaterialTheme.typography.bodySmall)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { checkUpdates() }, enabled = !checking && !downloading, modifier = Modifier.weight(1f)) {
                            Text("Tekrar kontrol et")
                        }
                        Button(
                            onClick = {
                                val latest = release ?: return@Button
                                downloading = true
                                progress = 0
                                error = null
                                status = "Güncelleme indiriliyor…"
                                scope.launch {
                                    try {
                                        UpdateManager.downloadAndInstall(context, latest) { progress = it }
                                        status = "Kurulum ekranı açıldı."
                                    } catch (e: InstallPermissionRequiredException) {
                                        status = "Kurulum izni gerekiyor. İzni verdikten sonra tekrar İndir ve kur'a dokun."
                                        error = e.message
                                    } catch (e: Exception) {
                                        error = e.message ?: "Güncelleme indirilemedi."
                                        status = "Güncelleme tamamlanamadı."
                                    } finally {
                                        downloading = false
                                    }
                                }
                            },
                            enabled = release != null && !checking && !downloading,
                            modifier = Modifier.weight(1f),
                        ) { Text("İndir ve kur") }
                    }
                }
            }
            release?.let { latest ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(latest.title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Sürüm ${latest.version}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        HorizontalDivider()
                        Text("Sürüm notları", fontWeight = FontWeight.Bold)
                        Text(latest.notes, style = MaterialTheme.typography.bodyMedium)
                        Text("İndirme yalnızca resmi Lionapp1/haberak-s GitHub Releases kaynağından yapılır.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(
                "Güncellemeler GitHub'da yayımlanan kararlı Releases sürümlerinden alınır. Android, uygulamanın üzerine kurulumu tamamlamak için son aşamada sistem onayı isteyebilir.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
