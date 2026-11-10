package lab.nfc.feature.inspector
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.content.ComponentName
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lab.nfc.core.apdu.*
import lab.nfc.core.hce.LabHceService
import lab.nfc.core.nfc.*
import lab.nfc.core.storage.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


import lab.nfc.*
import lab.nfc.feature.shared.*

@Composable fun ExchangeCard(e: Exchange) { Panel("${time(e.timestamp)} • ${e.durationMs} ms") { Text(e.direction); Text("Captura: ${e.capturePoint} • ${e.durationNanos} ns"); if(e.transportError.isNotBlank()) Text(e.transportError); Text(LabProtocol.decode(e.command)); SelectionContainer { Column { Text("TX ${e.command}",fontFamily=FontFamily.Monospace); Text("RX ${e.response}",fontFamily=FontFamily.Monospace) } }; Text("SW ${e.status} • ${statusDescription(e.status)}"); val ascii=runCatching { e.response.dropLast(4).bytes().map { if((it.toInt() and 255) in 32..126) it.toInt().toChar() else '.' }.joinToString("") }.getOrDefault(""); Text("ASCII $ascii",fontFamily=FontFamily.Monospace) } }
