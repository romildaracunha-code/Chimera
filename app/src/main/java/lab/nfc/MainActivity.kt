package lab.nfc
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

import lab.nfc.feature.dashboard.LabApp

class MainActivity: ComponentActivity() {
 private val vm: LabViewModel by viewModels()
 private lateinit var reader: ReaderController
 private val capability=mutableStateOf(Capabilities(false,false,false,false))
 private var exportText=""
 private val exportJson=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> writeExport(uri) }
 private val exportCsv=registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> writeExport(uri) }
 private fun writeExport(uri: android.net.Uri?) { if(uri!=null) runCatching { contentResolver.openOutputStream(uri)?.use { it.write(exportText.toByteArray()) } ?: error("Destino indisponível") }.onSuccess { vm.message.value="Exportação concluída" }.onFailure { vm.message.value="Falha ao exportar: ${it.message}" } }
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); reader=ReaderController(this,vm)
 setContent {
 MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF29D8FF),secondary=Color(0xFF418BFF),background=Color(0xFF0B0F16),surface=Color(0xFF151D29))) {
 Surface(Modifier.fillMaxSize()) { LabApp(vm,capability.value,
 onReader={ cmd -> runCatching { vm.stopHce(); reader.start(cmd) }.onFailure { vm.message.value=it.message ?: "Erro" } },
 onStopReader={ reader.stop(); vm.message.value="Leitor desativado" },
 onHce={ reader.stop(); if(capability.value.enabled && capability.value.hce) { vm.startHce(); preferHce() } else vm.message.value="HCE requer NFC ligado e suporte HCE" },
 onSettings={ runCatching { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }.onFailure { vm.message.value="Configuração NFC não disponível neste Android" } },
 onPhysicalExport={ filename,body -> exportText=body; exportJson.launch(filename) },
 onExport={ csv,session -> val list=session?.let { listOf(it) } ?: vm.store.sessions.value; exportText=if(csv) Export.csv(list) else Export.json(list); if(csv) exportCsv.launch("nfc-lab.csv") else exportJson.launch("nfc-lab.json") }) }
 }
 }
 }
 private fun preferHce() { if(capability.value.hce) NfcAdapter.getDefaultAdapter(this)?.let { CardEmulation.getInstance(it).setPreferredService(this,ComponentName(this,LabHceService::class.java)) } }
 override fun onResume() { super.onResume(); capability.value=Capabilities.detect(this); if(vm.store.activeHce!=null) preferHce() }
 override fun onPause() { reader.stop(); if(capability.value.hce) NfcAdapter.getDefaultAdapter(this)?.let { CardEmulation.getInstance(it).unsetPreferredService(this) }; super.onPause() }
 override fun onDestroy() { reader.destroy(); super.onDestroy() }
}
fun time(value: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS",Locale.getDefault()).format(Date(value))
