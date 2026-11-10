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
import lab.nfc.feature.chimera.ChimeraViewModel
import lab.nfc.core.chimera.ArtifactRole
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class MainActivity: ComponentActivity() {
 private val vm: LabViewModel by viewModels()
 private val chimera: ChimeraViewModel by viewModels()
 private var pendingText:String?=null
 private var pendingFile:java.io.File?=null
 private var pendingRead:((String)->Unit)?=null
 private var pendingAttachment:Triple<String,ArtifactRole,Boolean>?=null
 private val saveChimera=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
  val uri=result.data?.data;val text=pendingText;val file=pendingFile;pendingText=null;pendingFile=null
  if(result.resultCode==RESULT_OK && uri!=null) lifecycleScope.launch(Dispatchers.IO) {
   runCatching { require(text!=null || file!=null) { "Exportação interrompida; gere novamente após rotação" };contentResolver.openOutputStream(uri)?.use { out -> if(file!=null) file.inputStream().use { it.copyTo(out) } else out.write(text!!.toByteArray()) } ?: error("Destino indisponível") }
    .onSuccess { chimera.message.value="Arquivo exportado; revise antes de compartilhar" }.onFailure { chimera.message.value=it.message ?: "Erro de exportação" }
  }
 }
 private val readChimera=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  val callback=pendingRead;pendingRead=null
  if(uri!=null && callback!=null) lifecycleScope.launch(Dispatchers.IO) {
   runCatching { contentResolver.openInputStream(uri)?.use { chimera.importAnalysisFile(it) } ?: error("Arquivo indisponível") }
    .onSuccess { text -> runOnUiThread { callback(text) } }.onFailure { chimera.message.value=it.message ?: "Importação inválida" }
  }
 }
 private val attachChimera=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  val request=pendingAttachment;pendingAttachment=null
  if(uri!=null && request!=null) runCatching {
   val name=contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor -> if(cursor.moveToFirst()) cursor.getString(0) else "artifact.bin" } ?: "artifact.bin"
   val stream=contentResolver.openInputStream(uri) ?: error("Anexo indisponível")
   chimera.attach(stream,name,request.third,request.first,request.second)
  }.onFailure { chimera.message.value=it.message ?: "Anexo inválido" }
 }
 private fun offerExport(name:String,mime:String,text:String?=null,file:java.io.File?=null) { runOnUiThread {
  pendingText=text;pendingFile=file
  saveChimera.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,name))
 } }
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
 chimera=chimera,
 onChimeraExport={ name,mime,body -> offerExport(name,mime,body) },
 onImportText={ callback -> pendingRead=callback;readChimera.launch(arrayOf("text/*","application/json","application/octet-stream")) },
 onAttach={ id,role,authorized -> pendingAttachment=Triple(id,role,authorized);attachChimera.launch(arrayOf("*/*")) },
 onEvidenceZip={ chimera.work { val bundle=chimera.evidenceZip();offerExport("chimera-evidence.zip","application/zip",file=bundle);"Pacote verificado preparado" } },
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
