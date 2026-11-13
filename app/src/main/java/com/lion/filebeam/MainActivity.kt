@file:OptIn(ExperimentalMaterial3Api::class)

package com.lion.filebeam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
                    else -> HomeScreen(onSend = { mode = "send" }, onReceive = { mode = "receive" })
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onSend: () -> Unit, onReceive: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("FileBeam", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Telefon ↔ TV hızlı dosya aktarımı",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(36.dp))
            ModeCard("Gönder (Telefon)", "Bu cihazdan dosya gönder", Icons.Filled.Send, onSend)
            Spacer(Modifier.height(16.dp))
            ModeCard("Al (TV / Tablet)", "Bu cihazda dosya al", Icons.Filled.Tv, onReceive)
        }
    }
}

@Composable
fun ModeCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(120.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
