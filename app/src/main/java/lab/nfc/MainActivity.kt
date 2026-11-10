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
 private var pendingImportKind:String?=null
 private var pendingRead:((String)->Unit)?=null
 private var pendingAttachment:Triple<String,ArtifactRole,Boolean>?=null
 private val saveChimera=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
  val uri=result.data?.data;val text=pendingText;val file=pendingFile;pendingText=null;pendingFile=null
  if(result.resultCode==RESULT_OK && uri!=null) lifecycleScope.launch(Dispatchers.IO) {
   val restoredText=text ?: SecureRepository.get(applicationContext).read("draft/saf-export")?.toString(Charsets.UTF_8)
   runCatching { require(restoredText!=null || file!=null) { "Exportação interrompida; gere novamente após rotação" };contentResolver.openOutputStream(uri)?.use { out -> if(file!=null) (if(file.name.endsWith(".enc")) chimera.repository.openSealedFile(file) else file.inputStream()).use { it.copyTo(out) } else out.write(restoredText!!.toByteArray()) } ?: error("Destino indisponível") }
    .onSuccess { chimera.message.value="Arquivo exportado; revise antes de compartilhar";chimera.repository.delete("draft/saf-export");file?.delete() }.onFailure { chimera.message.value=it.message ?: "Erro de exportação" }
  }
 }
 private val readChimera=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  val callback=pendingRead;val kind=pendingImportKind;pendingRead=null;pendingImportKind=null
  if(uri!=null && (callback!=null || kind in listOf("analysis","sdk"))) lifecycleScope.launch(Dispatchers.IO) {
   runCatching { contentResolver.openInputStream(uri)?.use { chimera.importAnalysisFile(it) } ?: error("Arquivo indisponível") }
    .onSuccess { text -> runOnUiThread { if(callback!=null) callback(text) else if(kind!=null) chimera.recoveredImport.value=kind to text } }.onFailure { chimera.message.value=it.message ?: "Importação inválida" }
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
 private fun offerExport(name:String,mime:String,text:String?=null,file:java.io.File?=null) {
  lifecycleScope.launch(Dispatchers.IO) {
   runCatching {
    if(text!=null) chimera.repository.write("draft/saf-export",text.toByteArray()) else chimera.repository.delete("draft/saf-export")
    kotlinx.coroutines.withContext(Dispatchers.Main) {
     pendingText=text;pendingFile=file
     saveChimera.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,name))
    }
   }.onFailure { chimera.message.value=it.message ?: "Erro ao preparar exportação" }
  }
 }
 private var ready=false
 private lateinit var reader: ReaderController
 private val capability=mutableStateOf(Capabilities(false,false,false,false))
 private var exportText=""
 private val exportJson=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> writeExport(uri) }
 private val exportCsv=registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> writeExport(uri) }
 private fun offerLegacyExport(name:String,body:String,csv:Boolean=false) {
  lifecycleScope.launch(Dispatchers.IO) {
   runCatching {
    SecureRepository.get(applicationContext).write("draft/legacy-export",body.toByteArray());exportText=body
    kotlinx.coroutines.withContext(Dispatchers.Main) { if(csv) exportCsv.launch(name) else exportJson.launch(name) }
   }.onFailure { vm.message.value=it.message ?: "Erro ao preparar exportação" }
  }
 }
 private fun writeExport(uri: android.net.Uri?) {
  if(uri!=null) lifecycleScope.launch(Dispatchers.IO) {
   runCatching {
    val text=exportText.ifEmpty { SecureRepository.get(applicationContext).read("draft/legacy-export")?.toString(Charsets.UTF_8) ?: error("Exportação interrompida; gere novamente") }
    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("Destino indisponível")
    SecureRepository.get(applicationContext).delete("draft/legacy-export");exportText=""
   }.onSuccess { vm.message.value="Exportação concluída" }.onFailure { vm.message.value="Falha ao exportar: ${it.message}" }
  }
 }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  pendingImportKind=savedInstanceState?.getString("pendingImportKind")
  savedInstanceState?.getString("pendingFile")?.let { path -> java.io.File(path).takeIf { it.canonicalFile.parentFile==cacheDir.canonicalFile }?.let { pendingFile=it } }
  savedInstanceState?.getString("attachmentRun")?.let { run -> runCatching { pendingAttachment=Triple(run,ArtifactRole.valueOf(savedInstanceState.getString("attachmentRole")!!),savedInstanceState.getBoolean("attachmentAuthorized")) } }
  openWorkspace()
 }
 private fun openWorkspace() {
  setContent { MaterialTheme(colorScheme=darkColorScheme()) { Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(24.dp)) { Text("NFC Lab • abrindo e verificando armazenamento…");LinearProgressIndicator() } } } }
  lifecycleScope.launch(Dispatchers.IO) {
   runCatching { WorkspaceBootstrap.prepare(applicationContext);runCatching { RetentionScheduler.reconcile(applicationContext,SecureRepository.get(applicationContext)) }.onFailure { android.util.Log.w("NfcLab","Optional retention schedule unavailable",it) } }.onSuccess {
    kotlinx.coroutines.withContext(Dispatchers.Main) { ready=true;reader=ReaderController(this@MainActivity,vm);showApp();capability.value=Capabilities.detect(this@MainActivity) }
   }.onFailure { error -> runOnUiThread { setContent { lab.nfc.feature.chimera.RecoveryScreen(error.cause?.message ?: error.message ?: "Erro de abertura") { openWorkspace() } } } }
  }
 }
 private fun showApp() { setContent {
 lab.nfc.feature.shared.NfcLabTheme {
 Surface(Modifier.fillMaxSize()) { LabApp(vm,capability.value,
 chimera=chimera,
 onChimeraExport={ name,mime,body -> offerExport(name,mime,body) },
 onImportText={ kind,callback -> require(kind in listOf("analysis","sdk"));pendingImportKind=kind;pendingRead=callback;readChimera.launch(arrayOf("text/*","application/json","application/octet-stream")) },
 onAttach={ id,role,authorized -> pendingAttachment=Triple(id,role,authorized);attachChimera.launch(arrayOf("*/*")) },
 onEvidenceZip={ chimera.work { val bundle=chimera.evidenceZip();offerExport("chimera-evidence.zip","application/zip",file=bundle);"Pacote verificado preparado" } },
 onReader={ cmd -> runCatching { vm.stopHce(); reader.start(cmd) }.onFailure { vm.message.value=it.message ?: "Erro" } },
 onStopReader={ reader.stop(); vm.message.value="Leitor desativado" },
 onHce={ reader.stop(); if(capability.value.enabled && capability.value.hce) { vm.startHce(); preferHce() } else vm.message.value="HCE requer NFC ligado e suporte HCE" },
 onSettings={ runCatching { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }.onFailure { vm.message.value="Configuração NFC não disponível neste Android" } },
 onPhysicalExport={ filename,body -> offerLegacyExport(filename,body) },
 onExport={ csv,session -> lifecycleScope.launch(Dispatchers.IO) {
  runCatching {
   val file=java.io.File(cacheDir,if(csv) "nfc-history.csv.enc" else "nfc-history.json.enc")
   chimera.repository.sealFile(file) { out -> if(session==null) vm.store.exportHistory(out,csv) else if(csv) Export.writeCsv(out,sequenceOf(session)) else Export.writeJson(out,sequenceOf(session)) }
   offerExport(if(csv) "nfc-lab.csv" else "nfc-lab.json",if(csv) "text/csv" else "application/json",file=file)
  }.onFailure { vm.message.value=it.message ?: "Erro ao exportar histórico" }
 } }) }
 }
 }
 }
 override fun onSaveInstanceState(out:Bundle) {
  pendingImportKind?.let { out.putString("pendingImportKind",it) }
  pendingFile?.let { out.putString("pendingFile",it.absolutePath) }
  pendingAttachment?.let { out.putString("attachmentRun",it.first);out.putString("attachmentRole",it.second.name);out.putBoolean("attachmentAuthorized",it.third) }
  super.onSaveInstanceState(out)
 }
 private fun preferHce() { if(capability.value.hce) NfcAdapter.getDefaultAdapter(this)?.let { CardEmulation.getInstance(it).setPreferredService(this,ComponentName(this,LabHceService::class.java)) } }
 override fun onResume() { super.onResume(); capability.value=Capabilities.detect(this); if(ready && vm.store.activeHce!=null) preferHce() }
 override fun onPause() { if(::reader.isInitialized) reader.stop(); if(capability.value.hce) NfcAdapter.getDefaultAdapter(this)?.let { CardEmulation.getInstance(it).unsetPreferredService(this) }; super.onPause() }
 override fun onDestroy() { if(ready) chimera.flushDraft();if(::reader.isInitialized) reader.destroy(); super.onDestroy() }
}
fun time(value: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS",Locale.getDefault()).format(Date(value))
