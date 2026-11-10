package lab.nfc.feature.dashboard
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
import lab.nfc.feature.emulator.*
import lab.nfc.feature.reader.*
import lab.nfc.feature.terminal.*
import lab.nfc.feature.inspector.*
import lab.nfc.feature.sessions.*
import lab.nfc.feature.devices.*
import lab.nfc.feature.physical.PhysicalScreen
import lab.nfc.feature.chimera.*
import lab.nfc.core.chimera.ArtifactRole
@Composable fun LabApp(vm: LabViewModel,caps: Capabilities,onReader:(String)->Unit,onStopReader:()->Unit,onHce:()->Unit,onSettings:()->Unit,onExport:(Boolean,Session?)->Unit,onPhysicalExport:(String,String)->Unit,chimera:ChimeraViewModel,onChimeraExport:(String,String,String)->Unit,onImportText:(((String)->Unit))->Unit,onAttach:(String,ArtifactRole,Boolean)->Unit,onEvidenceZip:()->Unit) {
 var page by rememberSaveablePage()
 val sessions by vm.store.sessions.collectAsStateWithLifecycle()
 val profiles by vm.store.profiles.collectAsStateWithLifecycle()
 val devices by vm.store.devices.collectAsStateWithLifecycle()
 val profile by vm.profile.collectAsStateWithLifecycle()
 val message by vm.message.collectAsStateWithLifecycle()
 val busy by vm.busy.collectAsStateWithLifecycle()
 var selected by remember { mutableStateOf<String?>(null) }
 var compare by remember { mutableStateOf<String?>(null) }
 val pages=listOf("Dashboard","Emulator","Reader","Inspector","Terminal","Sessions","Devices","Physical")+chimeraPages
 Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
 Column(Modifier.padding(16.dp)) {
 Text("NFC LAB",style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.primary)
 Text("Laboratório NFC • credenciais fictícias",style=MaterialTheme.typography.labelMedium)
 }
 ScrollableTabRow(selectedTabIndex=pages.indexOf(page),edgePadding=8.dp) { pages.forEach { title -> Tab(selected=title==page,onClick={ page=title },text={ Text(title) }) } }
 if(message.isNotEmpty()) Text(message,Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
 LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
 if(page in chimeraPages) { item { ChimeraScreen(page,chimera,vm,onChimeraExport,onImportText,onAttach,onEvidenceZip) } } else when(page) {
 "Dashboard" -> {
 item { Panel("Estado do dispositivo") {
 Text("NFC: ${if(caps.nfc) if(caps.enabled) "ligado" else "desligado" else "ausente"}")
 Text("HCE: ${if(caps.hce) "suportado" else "não suportado"}")
 Text("Modo leitor: ${if(caps.reader) "disponível" else "indisponível"}")
 Text("Perfil selecionado: $profile • fictício")
 Button(onClick=onSettings) { Text("Configurações NFC") }
 } }
 item { Panel("Acesso rápido") { pages.drop(1).forEach { title -> TextButton(onClick={ page=title }) { Text(title) } } } }
 item { Text("Últimas sessões",style=MaterialTheme.typography.titleLarge) }
 items(sessions.take(3),key={ it.id }) { s -> SessionCard(s) { selected=s.id; page="Inspector" } }
 }
 "Emulator" -> { item { EmulatorScreen(vm,caps,profiles,profile,onHce) } }
 "Reader" -> { item { ReaderScreen(caps,busy,onReader,onStopReader) } }
 "Terminal" -> { item { TerminalScreen(vm,{ page=it }) } }
 "Inspector" -> {
 item { Text("Comunicação do protocolo NFC Lab",style=MaterialTheme.typography.titleLarge); Text("Não há interceptação passiva de transações externas.") }
 item { Column { sessions.take(30).forEach { s -> TextButton(onClick={ selected=s.id }) { Text("${time(s.date)} • ${s.mode}") } } } }
 val s=sessions.find { it.id==selected } ?: sessions.firstOrNull()
 if(s!=null) {
 item { Panel("Sessão ${s.id}") { Text(s.mode); Text(s.result); Text(s.technology); Text(s.error); Row { TextButton(onClick={ onExport(false,s) }) { Text("JSON") }; TextButton(onClick={ onExport(true,s) }) { Text("CSV") } } } }
 items(s.exchanges) { e -> ExchangeCard(e) }
 }
 }
 "Sessions" -> {
 item { Row { TextButton(onClick={ onExport(false,null) }) { Text("Exportar JSON") }; TextButton(onClick={ onExport(true,null) }) { Text("Exportar CSV") } } }
 items(sessions,key={ it.id }) { s ->
 Panel("${time(s.date)} • ${s.mode}") {
 Text(s.id,style=MaterialTheme.typography.labelSmall); Text(s.device); Text("${s.profile} • ${s.result}"); if(s.error.isNotEmpty()) Text(s.error)
 var notes by remember(s.id,s.notes) { mutableStateOf(s.notes) }
 OutlinedTextField(notes,{ notes=it },label={ Text("Observações do pesquisador") },modifier=Modifier.fillMaxWidth())
 TextButton(onClick={ vm.notes(s.id,notes) }) { Text("Salvar observações") }
 Row { TextButton(onClick={ selected=s.id; page="Inspector" }) { Text("Inspecionar") }; TextButton(onClick={ vm.simulate(s) }) { Text("Repetir local") } }
 TextButton(onClick={ if(compare==null) compare=s.id else { selected=s.id } }) { Text(if(compare==s.id) "Referência selecionada" else "Comparar") }
 val ref=sessions.find { it.id==compare }
 if(ref!=null && selected==s.id && ref.id!=s.id) {
 Text("Comparação com ${ref.id}: ${ref.mode} / ${s.mode}")
 Text("Comandos: ${ref.exchanges.size} / ${s.exchanges.size}")
 Text("Respostas iguais: ${ref.exchanges.map { it.command to it.response } == s.exchanges.map { it.command to it.response }}")
 Text("Duração total: ${ref.exchanges.sumOf { it.durationMs }} / ${s.exchanges.sumOf { it.durationMs }} ms")
 TextButton(onClick={ compare=null }) { Text("Limpar comparação") }
 }
 }
 }
 }
 "Physical" -> { item { PhysicalScreen(vm,caps,onPhysicalExport) } }
 "Devices" -> { item { DeviceEditor(vm) }; items(devices) { d -> Panel("${d.manufacturer} ${d.model}") { Text("ID: ${d.internalId} • firmware: ${d.firmware}"); Text("NFC declarado: ${d.nfc}"); Text("SDK: ${d.sdk} • homologação: ${d.homologation}"); Text("Adquirente: ${d.acquirer} • app: ${d.paymentApp} • kernel: ${d.contactlessKernel}"); Text("Fonte: ${d.evidenceReference}"); Text(d.notes) } } }
 }
 if(sessions.isEmpty() && page in listOf("Inspector","Sessions")) item { Text("Nenhuma sessão. Execute uma simulação no Terminal.") }
 }
 }
}
