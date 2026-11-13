@file:OptIn(ExperimentalMaterial3Api::class)

package com.lion.filebeam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("F", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("FileBeam", fontWeight = FontWeight.Bold)
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
                val wide = maxWidth >= 640.dp
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
                            "YEREL AĞ • DOĞRUDAN AKTARIM",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(
                        "Dosyaların özgürce\nışınlansın.",
                        fontSize = if (wide) 38.sp else 30.sp,
                        lineHeight = if (wide) 44.sp else 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Telefonunla TV arasında fotoğraf, video ve belgeleri aynı Wi-Fi ağı üzerinden aktar.",
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.widthIn(max = 560.dp),
                    )
                    Spacer(Modifier.height(30.dp))
                    if (wide) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ModeCard(
                                "Gönder",
                                "Bu cihazdan TV'ye dosya yolla",
                                Icons.Filled.Send,
                                onSend,
                                Modifier.weight(1f),
                            )
                            ModeCard(
                                "Al",
                                "Bu cihazda dosya almaya başla",
                                Icons.Filled.Tv,
                                onReceive,
                                Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ModeCard("Gönder", "Bu cihazdan TV'ye dosya yolla", Icons.Filled.Send, onSend)
                            ModeCard("Al", "Bu cihazda dosya almaya başla", Icons.Filled.Tv, onReceive)
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(
                        "İnternet gerekmez  ·  Dosyalar buluta yüklenmez",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 154.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Column {
                Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
