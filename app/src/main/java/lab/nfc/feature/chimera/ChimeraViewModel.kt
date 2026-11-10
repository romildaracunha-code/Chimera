package lab.nfc.feature.chimera

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import lab.nfc.core.chimera.*
import lab.nfc.core.emv.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ChimeraViewModel(app:Application):AndroidViewModel(app) {
 val repository=lab.nfc.core.storage.SecureRepository.get(app)
 private val generation=repository.generation
 val ledger=lab.nfc.core.storage.WorkspaceBootstrap.getLedger(app)
 val appVersion=app.packageManager.getPackageInfo(app.packageName,0).versionName ?: "UNKNOWN"
 val research=JSONArray(app.assets.open("chimera/research.json").bufferedReader().use { it.readText() })
 val vectors=JSONObject(app.assets.open("chimera/public-vectors.json").bufferedReader().use { it.readText() })
 val state=MutableStateFlow(ledger.snapshot())
 val message=MutableStateFlow(ledger.integrityError ?: "Chimera • sem inferência automática de aprovação financeira")
 val output=MutableStateFlow("")
 val busy=MutableStateFlow(false)
 val draft=MutableStateFlow(runCatching { repository.read("draft/workflow")?.let { JSONObject(it.toString(Charsets.UTF_8)) } ?: JSONObject() }.getOrElse { throw IllegalStateException("Rascunho inválido; preservado",it) })
 private var draftJob:kotlinx.coroutines.Job?=null
 private val draftLock=Any()
 fun editDraft(key:String,value:String) {
  require(key.length<=128 && value.length<=4096)
  synchronized(draftLock) { draft.value=JSONObject(draft.value.toString()).put(key,value) }
  draftJob?.cancel();draftJob=viewModelScope.launch(Dispatchers.IO) { kotlinx.coroutines.delay(350);synchronized(draftLock) { repository.write("draft/workflow",draft.value.toString().toByteArray(),generation) } }
 }
 fun draftValue(key:String,default:String="")=draft.value.optString(key,default)
 var selectedRun:String
  get()=draftValue("selectedRun")
  set(value) { editDraft("selectedRun",value) }
 @Volatile var replacementCommitted=false;private set
 fun flushDraft() { if(replacementCommitted) return;synchronized(draftLock) { repository.write("draft/workflow",draft.value.toString().toByteArray(),generation) } }

 var attachRole=ArtifactRole.OTHER
 var pendingHexImport:((String)->Unit)?=null
 private val operation=java.util.concurrent.atomic.AtomicBoolean(false)
 fun work(action:()->String) { if(!operation.compareAndSet(false,true)) { message.value="Aguarde a operação atual";return };busy.value=true;viewModelScope.launch(Dispatchers.IO) {try { message.value=action();state.value=ledger.snapshot() } catch(e:Exception) { message.value=e.message ?: e.javaClass.simpleName } finally { state.value=ledger.snapshot();busy.value=false;operation.set(false) } } }
 fun analyze(hex:String,kind:InputKind,origin:DataOrigin,source:String,context:String) = work {
  if(origin==DataOrigin.PUBLIC_TEST) require(vectors.getJSONArray("vectors").objects().any { v -> v.getString("hex").replace(" ","").equals(hex.replace(Regex("\\s"),""),true) && source==vectors.getString("sourceUrl")+"#"+v.getString("id") }) { "Fixture pública alterada: marque como importada/sintética" }
  if(kind==InputKind.TRACE_JSON) { val reports=TraceImport.analyze(hex,source);reports.forEach { ledger.saveAnalysis(it) };output.value=reports.joinToString("\n\n") { it.text()+"\n"+it.traceSummary?.toString(2) };return@work "${reports.size} sessão(ões) importada(s), sem atestado físico" };val report=EmvAnalyzer.analyze(hex,kind,origin,source,context);val id=ledger.saveAnalysis(report);output.value=report.text();"Análise sanitizada salva: $id; não é evidência física"
 }
 fun importAnalysisFile(input:InputStream):String { val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true) { val n=input.read(buffer);if(n<0) break;require(output.size()+n<=400000) { "Arquivo excessivo" };output.write(buffer,0,n) };val bytes=output.toByteArray();require(bytes.size<=400000) { "Arquivo excede limite de entrada hexadecimal" };return bytes.toString(Charsets.UTF_8) }
 fun attach(input:InputStream,name:String,authorized:Boolean,runId:String=selectedRun,role:ArtifactRole=attachRole) = work { input.use { ledger.attach(runId,it,name,role,authorized) };"Arquivo anexado; hash consistente não comprova autenticidade física" }
 fun sdk(raw:String) { val run=selectedRun;work { require(raw.length<=32768);ledger.importSdk(run,runCatching { JSONObject(raw) }.getOrElse { error("Resumo SDK JSON inválido; dados não salvos") });"Resumo SDK importado; resultado declarado externamente, não verificado criptograficamente pelo app" } }
 fun exportText(markdown:Boolean):String {
  val data=ledger.export()
  if(!markdown) return data.toString(2)
  fun clean(x:String)=x.replace("|","/").replace("\n"," ")
  return buildString {
   appendLine("# Operação Chimera • relatório técnico");appendLine("Integridade: ${data.getString("integrity")}")
   appendLine("Hash/journal não prova execução física. Autenticação criptográfica e liquidação não são inferidas.")
   appendLine("\n| ID | Terminal / firmware | Caso | Equipe | Origem | Etapa efetiva | Resultado |")
   appendLine("| --- | --- | --- | --- | --- | --- | --- |")
   data.getJSONArray("runReports").objects().forEach { r -> val t=r.getJSONObject("terminalSnapshot");appendLine("| ${r.getString("id")} | ${clean(t.optString("model"))} / ${clean(t.optString("firmware"))} | ${r.getJSONObject("caseSnapshot").getString("id")} | ${r.getString("team")} | OPERATOR_REPORTED | ${r.getString("effectiveStage")} | ${r.getString("outcome")} |")
    r.getJSONArray("currentIssues").let { a -> for(i in 0 until a.length()) appendLine("- ${r.getString("id")}: ${clean(a.getString(i))}") }
   }
   appendLine("\nAnálises: ${data.getJSONArray("analyses").length()}; anexos: ${data.getJSONArray("artifacts").length()}; nenhum dado bruto de análise é persistido.")
  }
 }
 fun exportCsv():String {
  fun cell(s:String):String { val safe=if(s.trimStart().firstOrNull() in listOf('=','+','-','@')) "'"+s else s;return "\""+safe.replace("\"","\"\"")+"\"" }
  val rows=ledger.export().getJSONArray("runReports").objects()
  return "id,terminal,firmware,case,team,environment,stage,outcome,validation,createdAt,nfcLabVersion,phoneReference,caseRevision,credential,operator,reviewer\r\n"+rows.joinToString("\r\n") { r -> listOf(r.getString("id"),r.getJSONObject("terminalSnapshot").optString("model"),r.getJSONObject("terminalSnapshot").optString("firmware"),r.getJSONObject("caseSnapshot").getString("id"),r.getString("team"),r.getString("environment"),r.getString("effectiveStage"),r.getString("outcome"),r.getString("validation"),r.getLong("createdAt").toString(),r.getString("nfcLabVersion"),r.getString("phoneReference"),r.getJSONObject("caseSnapshot").getString("revision"),r.getString("credential"),r.getString("operator"),r.getString("reviewer")).joinToString(",") { cell(it) } }
 }
 fun backup(password:CharArray):File {
  return try { flushDraft();val bytes=lab.nfc.core.storage.PortableBackup.pack(repository.snapshot(),password);File(getApplication<Application>().cacheDir,"nfclab-backup.nfcb").apply { writeBytes(bytes) } } finally { password.fill('\u0000') }
 }
 fun validateRestore(bytes:ByteArray,password:CharArray):Map<String,ByteArray> =try { lab.nfc.core.storage.PortableBackup.unpack(bytes,password) } finally { password.fill('\u0000') }
 fun restore(records:Map<String,ByteArray>) {
  lab.nfc.core.storage.PortableBackup.validate(records)
  // Current database remains untouched until validation; Room replaces all records in one transaction.
  draftJob?.cancel();repository.replace(records);replacementCommitted=true
 }
 fun evidenceZip():File = File(getApplication<Application>().cacheDir,"chimera-evidence.zip.enc").also { file -> repository.sealFile(file) { EvidenceBundle.write(ledger,it) } }
}
