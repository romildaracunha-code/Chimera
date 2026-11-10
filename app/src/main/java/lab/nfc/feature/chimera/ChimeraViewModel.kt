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
 val ledger=ChimeraLedger(File(app.filesDir,"chimera-v3"),JSONArray(app.assets.open("chimera/cases.json").bufferedReader().use { it.readText() }))
 val appVersion=app.packageManager.getPackageInfo(app.packageName,0).versionName ?: "UNKNOWN"
 val research=JSONArray(app.assets.open("chimera/research.json").bufferedReader().use { it.readText() })
 val vectors=JSONObject(app.assets.open("chimera/public-vectors.json").bufferedReader().use { it.readText() })
 val state=MutableStateFlow(ledger.snapshot())
 val message=MutableStateFlow(ledger.integrityError ?: "Chimera • sem inferência automática de aprovação financeira")
 val output=MutableStateFlow("")
 val busy=MutableStateFlow(false)
 var selectedRun:String=""
 var attachRole=ArtifactRole.OTHER
 var pendingHexImport:((String)->Unit)?=null
 fun work(action:()->String) { viewModelScope.launch(Dispatchers.IO) { busy.value=true;try { message.value=action();state.value=ledger.snapshot() } catch(e:Exception) { message.value=e.message ?: e.javaClass.simpleName } finally { busy.value=false } } }
 fun analyze(hex:String,kind:InputKind,origin:DataOrigin,source:String,context:String) = work {
  if(origin==DataOrigin.PUBLIC_TEST) require(vectors.getJSONArray("vectors").objects().any { v -> v.getString("hex").replace(" ","").equals(hex.replace(Regex("\\s"),""),true) && source==vectors.getString("sourceUrl")+"#"+v.getString("id") }) { "Fixture pública alterada: marque como importada/sintética" }
  val report=if(kind==InputKind.TRACE_JSON) EmvAnalyzer.analyzeTrace(hex,origin,source) else EmvAnalyzer.analyze(hex,kind,origin,source,context);val id=ledger.saveAnalysis(report);output.value=report.text();"Análise sanitizada salva: $id; não é evidência física"
 }
 fun importAnalysisFile(input:InputStream):String { val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true) { val n=input.read(buffer);if(n<0) break;require(output.size()+n<=400000) { "Arquivo excessivo" };output.write(buffer,0,n) };val bytes=output.toByteArray();require(bytes.size<=400000) { "Arquivo excede limite de entrada hexadecimal" };return bytes.toString(Charsets.UTF_8) }
 fun attach(input:InputStream,name:String,authorized:Boolean,runId:String=selectedRun,role:ArtifactRole=attachRole) = work { input.use { ledger.attach(runId,it,name,role,authorized) };"Arquivo anexado; hash consistente não comprova autenticidade física" }
 fun sdk(raw:String) = work { require(raw.length<=32768);ledger.importSdk(selectedRun,runCatching { JSONObject(raw) }.getOrElse { error("Resumo SDK JSON inválido; dados não salvos") });"Resumo SDK importado; resultado declarado externamente, não verificado criptograficamente pelo app" }
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
 fun evidenceZip():File = EvidenceBundle.write(ledger,File(getApplication<Application>().cacheDir,"chimera-evidence.zip"))
}
