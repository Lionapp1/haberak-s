@file:OptIn(ExperimentalMaterial3Api::class)

package com.lion.filebeam

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tv
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
import com.lion.filebeam.ui.theme.FileBeamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FileBeamTheme {
                var mode by remember { mutableStateOf("home") }
                when (mode) {
                    "send" -> SenderScreen(onBack = { mode = "home" })
                    "receive" -> ReceiverScreen(onBack = { mode = "home" })
                    else -> HomeScreen(
                        onSend = { mode = "send" },
                        onReceive = { mode = "receive" },
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onSend: () -> Unit, onReceive: () -> Unit) {
    val context = LocalContext.current
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
                            Text("Yakınında. Hızlıca. Güvenle.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = if (isTv) 48.dp else 20.dp, vertical = if (isTv) 30.dp else 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().widthIn(max = if (isTv) 1180.dp else 900.dp)) {
                val wide = maxWidth >= 640.dp || isTv
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
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
                    Text(
                        "Dosyaların özgürce\nışınlansın.",
                        fontSize = if (isTv) 54.sp else if (wide) 38.sp else 30.sp,
                        lineHeight = if (isTv) 62.sp else if (wide) 44.sp else 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Telefonunla TV arasında fotoğraf, video ve belgeleri güvenle aktar.",
                        fontSize = if (isTv) 21.sp else 15.sp,
                        lineHeight = if (isTv) 30.sp else 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = if (isTv) 760.dp else 560.dp),
                    )
                    Spacer(Modifier.height(if (isTv) 42.dp else 30.dp))
                    if (wide) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(if (isTv) 28.dp else 16.dp),
                        ) {
                            ModeCard(
                                "Gönder",
                                "Bu cihazdan başka bir cihaza dosya yolla",
                                Icons.Filled.Send,
                                onSend,
                                Modifier.weight(1f),
                                isTv,
                            )
                            ModeCard(
                                "Al",
                                "Bu cihazı dosya almak için hazırla",
                                Icons.Filled.Tv,
                                onReceive,
                                Modifier.weight(1f),
                                isTv,
                            )
                        }
                    } else {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ModeCard("Gönder", "Bu cihazdan başka bir cihaza dosya yolla", Icons.Filled.Send, onSend)
                            ModeCard("Al", "Bu cihazı dosya almak için hazırla", Icons.Filled.Tv, onReceive)
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
            .heightIn(min = if (isTv) 250.dp else 154.dp)
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
        Column(
            Modifier.fillMaxSize().padding(if (isTv) 30.dp else 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(if (isTv) 66.dp else 48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(if (isTv) 32.dp else 24.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Column {
                Text(title, fontSize = if (isTv) 30.sp else 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(subtitle, fontSize = if (isTv) 18.sp else 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = if (isTv) 25.sp else 18.sp)
            }
        }
    }
}
