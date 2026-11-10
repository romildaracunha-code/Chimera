package lab.nfc.core.chimera

import lab.nfc.core.emv.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

enum class EvidenceStage { UNVERIFIED, NFC_DETECTED, APPLICATION_SELECTED, PROTOCOL_PROCESSED, AUTHENTICATION_RESULT, TEST_AUTHORIZATION_RESULT }
enum class ArtifactRole { PHYSICAL_CONTEXT, READER_TRACE, TERMINAL_TRACE, SDK_RESULT, HOST_TRACE, OTHER }
enum class Team { RED, BLUE }

fun canonical(x:Any?):String = when(x) {
 null,JSONObject.NULL->"null"
 is JSONObject->x.keys().asSequence().toList().sorted().joinToString(",","{","}") { canonical(it)+":"+canonical(x.get(it)) }
 is JSONArray->(0 until x.length()).joinToString(",","[","]") { canonical(x.get(it)) }
 is String->buildString {
  append('"')
  x.forEach { c -> when(c) {
   '"'->append("\\\"")
   '\\'->append("\\\\")
   '\b'->append("\\b")
   '\u000C'->append("\\f")
   '\n'->append("\\n")
   '\r'->append("\\r")
   '\t'->append("\\t")
   else->if(c.code<32 || c.code>126) append("\\u"+c.code.toString(16).padStart(4,'0')) else append(c)
  } }
  append('"')
 }
 else->x.toString()
}
fun cloneJson(x:JSONObject)=JSONObject(x.toString())
fun JSONArray.objects():List<JSONObject> = (0 until length()).map { getJSONObject(it) }

/** Separate, bounded v3 store. Hash journal detects accidental/tampered state, not trusted hardware attestation. */
interface LedgerRecords {
 fun read(name:String):ByteArray?
 fun write(name:String,value:ByteArray)
 fun delete(name:String)
}
class ChimeraLedger(private val directory:File,seedCases:JSONArray,private val records:LedgerRecords?=null) {
 private fun read(name:String)=if(records!=null) records.read(name) else File(directory,name).takeIf { it.exists() }?.readBytes()
 private fun write(name:String,value:ByteArray) { if(records!=null) records.write(name,value) else {
  val target=File(directory,name);val tmp=File(directory,"$name.tmp")
  tmp.outputStream().use { it.write(value);it.fd.sync() }
  try { Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) }
  catch(_:java.nio.file.AtomicMoveNotSupportedException) { Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING) }
 } }
 private fun delete(name:String) { if(records!=null) records.delete(name) else File(directory,name).delete() }

 private val file=File(directory,"workspace-v3.json")
 private var root=JSONObject().put("schemaVersion",3).put("cases",JSONArray(seedCases.toString())).put("analyses",JSONArray()).put("runs",JSONArray()).put("artifacts",JSONArray()).put("journal",JSONArray())
 var integrityError:String?=null;private set
 init {
  directory.mkdirs()
  val loaded=runCatching { read("workspace-v3.json") }.getOrElse { integrityError="Falha de armazenamento: ${it.javaClass.simpleName}";null }
  if(loaded!=null) {
   val cleanSeed=cloneJson(root)
   runCatching { require(loaded.size<=16*1024*1024);root=JSONObject(loaded.toString(Charsets.UTF_8));require(root.getInt("schemaVersion")==3);verifyJournal(root) }.onFailure { root=cleanSeed;integrityError="Ledger inválido; original preservado: ${it.javaClass.simpleName}" }
  } else if(integrityError==null) runCatching { commit("INITIALIZE","workspace") }.onFailure { integrityError="Não foi possível inicializar o ledger; nenhum arquivo anterior foi substituído" }
 }
 @Synchronized fun snapshot():JSONObject=cloneJson(root).put("integrity",integrityError ?: "HASH_CHAIN_CONSISTENT_NOT_AUTHENTICATED")
 private fun dataDigest():String { val data=JSONObject();listOf("cases","analyses","runs","artifacts").forEach { data.put(it,root.getJSONArray(it)) };return digest(canonical(data).toByteArray()) }
 private fun persist() {
  val text=root.toString(2);require(text.length<=16*1024*1024)
  write("workspace-v3.json",text.toByteArray())
 }
 private fun commit(action:String,id:String) {
  check(integrityError==null) { integrityError!! }
  val journal=root.getJSONArray("journal");require(journal.length()<4096) { "Limite do journal atingido; exporte e arquive" }
  val previous=if(journal.length()==0) "GENESIS" else journal.getJSONObject(journal.length()-1).getString("hash")
  val e=JSONObject().put("sequence",journal.length()+1).put("timestamp",System.currentTimeMillis()).put("action",action).put("entityId",id).put("previousHash",previous).put("stateDigest",dataDigest())
  e.put("hash",digest(canonical(e).toByteArray()));journal.put(e);persist()
 }
 @Synchronized private fun mutate(action:String,id:String,body:()->Unit) {
  check(integrityError==null) { integrityError!! };val before=cloneJson(root)
  try { body();commit(action,id) } catch(e:Exception) { root=before;throw e }
 }
 @Synchronized fun find(collection:String,id:String):JSONObject = root.getJSONArray(collection).objects().firstOrNull { it.optString("id")==id }?.let { cloneJson(it) } ?: error("Registro não encontrado: $id")
 @Synchronized fun saveCase(value:JSONObject) {
  val c=cloneJson(value);val id=c.getString("id");require(id.matches(Regex("[A-Za-z0-9_-]{1,64}")))
  require(c.getString("title").length in 1..256 && c.getString("revision").length in 1..64)
  require(c.getString("kind") in listOf("LAB","OFFICIAL_EMV"));require(c.optString("officialReference").length<=1024)
  mutate("SAVE_CASE",id) { val all=root.getJSONArray("cases").objects().filter { it.getString("id")!=id };require(all.size<128);root.put("cases",JSONArray(all+c)) }
 }
 @Synchronized fun saveAnalysis(analysis:Analysis):String {
  require(analysis.origin!=DataOrigin.REAL_HARDWARE) { "Importação não vira hardware observado pelo app" }
  val id=UUID.randomUUID().toString();val a=analysis.json().put("id",id).put("savedAt",System.currentTimeMillis())
  mutate("SAVE_SANITIZED_ANALYSIS",id) { require(root.getJSONArray("analyses").length()<1024);root.getJSONArray("analyses").put(a) };return id
 }
 @Synchronized fun createRun(caseId:String,terminal:JSONObject,operator:String,phone:String,environmentRef:String,team:Team,version:String,baselineId:String=""):String {
  val c=find("cases",caseId);require(listOf(operator,phone,environmentRef,version).all { it.isNotBlank() && it.length<=512 })
  require(terminal.getString("internalId").isNotBlank());if(c.getString("kind")=="OFFICIAL_EMV") require(c.optString("officialReference").isNotBlank()) { "Bloqueado: caso/plano oficial não vinculado" }
  if(team==Team.BLUE) { val before=find("runs",baselineId);require(before.getString("team")=="RED") { "Blue exige baseline Red" } }
  val id=UUID.randomUUID().toString()
  val run=JSONObject().put("id",id).put("createdAt",System.currentTimeMillis()).put("team",team.name).put("baselineId",baselineId)
   .put("terminalSnapshot",cloneJson(terminal)).put("caseSnapshot",c).put("operator",operator).put("phoneReference",phone).put("nfcLabVersion",version)
   .put("environment",if(c.getString("kind")=="LAB") "LAB_PROTOCOL" else "OFFICIAL_HOMOLOGATION").put("environmentReference",environmentRef)
   .put("credential",if(c.getString("kind")=="LAB") "NFC_LAB_FICTITIOUS" else "OFFICIAL_TEST_CREDENTIAL")
   .put("captureOrigin","OPERATOR_REPORTED_PHYSICAL_NOT_AUTO_VERIFIED").put("physicalAttested",false).put("claimedStage","UNVERIFIED")
   .put("outcome","INCONCLUSIVE").put("notes","").put("reviewer","").put("reviewReference","").put("artifacts",JSONArray()).put("validation","UNVERIFIED")
  mutate("CREATE_RUN_PLAN",id) { require(root.getJSONArray("runs").length()<512);root.getJSONArray("runs").put(run) };return id
 }
 @Synchronized fun observe(runId:String,stage:EvidenceStage,outcome:String,notes:String,physicalAttested:Boolean) {
  require(outcome in listOf("OBSERVED","PASS","FAIL","INCONCLUSIVE"));require(notes.length<=4096)
  val r=find("runs",runId)
  if(stage in listOf(EvidenceStage.AUTHENTICATION_RESULT,EvidenceStage.TEST_AUTHORIZATION_RESULT)) require(r.getString("environment")=="OFFICIAL_HOMOLOGATION") { "Protocolo Lab não comprova autenticação/autorizações EMV" }
  mutate("RECORD_OPERATOR_OBSERVATION",runId) { val actual=root.getJSONArray("runs").objects().first { it.getString("id")==runId };actual.put("claimedStage",stage.name).put("outcome",outcome).put("notes",notes).put("physicalAttested",physicalAttested).put("reviewer","").put("reviewReference","").put("validation","UNVERIFIED") }
 }
 @Synchronized fun attach(runId:String,input:InputStream,displayName:String,role:ArtifactRole,authorizedRedacted:Boolean):String {
  require(authorizedRedacted) { "Confirme autorização e redação antes de importar" };find("runs",runId)
  val existingSize=root.getJSONArray("artifacts").objects().sumOf { it.getLong("size") }
  require(existingSize<64L*1024*1024) { "Cota de anexos atingida" }
  val id=UUID.randomUUID().toString();val buffer=java.io.ByteArrayOutputStream();var count=0L
  try {
   val buf=ByteArray(8192);while(true) { val n=input.read(buf);if(n<0) break;count+=n;require(count<=8*1024*1024) { "Anexo excede 8 MiB" };require(existingSize+count<=64L*1024*1024);buffer.write(buf,0,n) }
   val bytes=buffer.toByteArray();write("$id.bin",bytes)
   require(count>0) { "Anexo vazio" }
   val artifact=JSONObject().put("id",id).put("runId",runId).put("name",displayName.take(128)).put("role",role.name).put("size",count).put("sha256",digest(bytes))
    .put("registeredAt",System.currentTimeMillis()).put("origin","IMPORTED_AUTHORIZED").put("authenticity","NOT_AUTOMATICALLY_VERIFIED")
   mutate("ATTACH_AUTHORIZED_REDACTED_FILE",id) { root.getJSONArray("artifacts").put(artifact);root.getJSONArray("runs").objects().first { it.getString("id")==runId }.put("reviewer","").put("reviewReference","").put("validation","UNVERIFIED").getJSONArray("artifacts").put(id) }
  } catch(e:Exception) { delete("$id.bin");throw e };return id
 }
 fun artifactBytes(id:String):ByteArray { artifactFile(id);return read("$id.bin") ?: error("Anexo ausente") }
 fun artifactFile(id:String):File { require(id.matches(Regex("[a-f0-9-]{36}")));return File(directory,"$id.bin") }
 @Synchronized fun retentionPreview(days:Int):JSONObject {
  require(days in 1..3650);val cutoff=System.currentTimeMillis()-days*86400000L
  return JSONObject().put("days",days).put("cutoff",cutoff)
   .put("analyses",JSONArray(root.getJSONArray("analyses").objects().filter { it.getLong("savedAt")<cutoff }.map { it.getString("id") }))
   .put("artifacts",JSONArray(root.getJSONArray("artifacts").objects().filter { it.getLong("registeredAt")<cutoff }.map { it.getString("id") }))
   .put("limit","Manual confirmation required; attached evidence deletion invalidates reviews. Runs/journal are retained.")
 }
 @Synchronized fun applyRetention(preview:JSONObject):JSONObject {
  val plan=cloneJson(preview);require(plan.getInt("days") in 1..3650 && plan.getLong("cutoff")<=System.currentTimeMillis())
  val cutoff=plan.getLong("cutoff")
  val artifacts=plan.getJSONArray("artifacts");val analyses=plan.getJSONArray("analyses")
  // Validate all selected records before deletion; never add records absent from the reviewed preview.
  for(i in 0 until artifacts.length()) require(find("artifacts",artifacts.getString(i)).getLong("registeredAt")<cutoff)
  for(i in 0 until analyses.length()) require(find("analyses",analyses.getString(i)).getLong("savedAt")<cutoff)
  for(i in 0 until artifacts.length()) deleteArtifact(artifacts.getString(i))
  for(i in 0 until analyses.length()) deleteAnalysis(analyses.getString(i))
  return plan
 }
 @Synchronized fun deleteArtifact(id:String) {
  val artifact=find("artifacts",id);val runId=artifact.getString("runId")
  mutate("DELETE_ARTIFACT_INVALIDATE_REVIEW",id) {
   root.put("artifacts",JSONArray(root.getJSONArray("artifacts").objects().filter { it.getString("id")!=id }))
   val run=root.getJSONArray("runs").objects().first { it.getString("id")==runId }
   val refs=run.getJSONArray("artifacts");run.put("artifacts",JSONArray((0 until refs.length()).map { refs.getString(it) }.filter { it!=id }))
    .put("reviewer","").put("reviewReference","").put("validation","UNVERIFIED")
  };delete("$id.bin")
 }
 @Synchronized fun deleteAnalysis(id:String) {
  find("analyses",id);mutate("DELETE_ANALYSIS",id) { root.put("analyses",JSONArray(root.getJSONArray("analyses").objects().filter { it.getString("id")!=id })) }
 }
 @Synchronized fun importSdk(runId:String,raw:JSONObject) {
  val required=setOf("provider","caseId","environment","result","version","traceReference")
  val allowed=required+"authenticationResult"
  require(raw.keys().asSequence().all { it in allowed }) { "SDK schema contém campos não permitidos; forneça resumo sem chaves/credenciais" }
  require(required.all { raw.has(it) && raw.get(it) is String } && (!raw.has("authenticationResult") || raw.get("authenticationResult") is String)) { "Resumo SDK exige campos string, sem objetos/chaves aninhados" }
  if(raw.has("authenticationResult")) require(raw.getString("authenticationResult") in listOf("VALID","INVALID","NOT_PERFORMED","INCONCLUSIVE"))
  val r=find("runs",runId);require(r.getString("environment")=="OFFICIAL_HOMOLOGATION")
  require(raw.getString("environment")=="OFFICIAL_HOMOLOGATION" && raw.getString("caseId")==r.getJSONObject("caseSnapshot").getString("officialReference"))
  require(raw.getString("result") in listOf("PASS","FAIL","INCONCLUSIVE"));require(listOf("provider","version","traceReference").all { raw.getString(it).isNotBlank() && raw.getString(it).length<=512 })
  mutate("IMPORT_SDK_DECLARATION_NOT_LOCAL_CRYPTO_VERIFICATION",runId) { root.getJSONArray("runs").objects().first { it.getString("id")==runId }.put("sdkReport",cloneJson(raw).put("verification","SDK_REPORTED_NOT_LOCALLY_VERIFIED")).put("reviewer","").put("reviewReference","").put("validation","UNVERIFIED") }
 }
 @Synchronized fun decisions(runId:String,terminalResult:String,processorResult:String,correlation:String) {
  val allowed=setOf("UNKNOWN","APPROVED","DECLINED","NOT_REQUESTED")
  require(terminalResult in allowed && processorResult in allowed && correlation.length<=1024)
  val r=find("runs",runId);require(r.getString("environment")=="OFFICIAL_HOMOLOGATION") { "Resultados de processador de teste exigem homologação" }
  if(processorResult in listOf("APPROVED","DECLINED")) require(correlation.isNotBlank()) { "Correlação de host obrigatória" }
  mutate("RECORD_TERMINAL_PROCESSOR_DECLARATIONS",runId) { root.getJSONArray("runs").objects().first { it.getString("id")==runId }.put("terminalResult",terminalResult).put("processorResult",processorResult).put("hostCorrelation",correlation).put("reviewer","").put("reviewReference","").put("validation","UNVERIFIED") }
 }
 @Synchronized fun artifactIssues(runId:String):List<String> {
  val run=find("runs",runId);val refs=run.getJSONArray("artifacts");val problems=mutableListOf<String>()
  for(i in 0 until refs.length()) { val id=refs.getString(i);val a=runCatching { find("artifacts",id) }.getOrNull();val bytes=runCatching { artifactBytes(id) }.getOrNull()
   if(a==null || a.optString("runId")!=runId || bytes==null || bytes.size.toLong()!=a.getLong("size") || digest(bytes)!=a.getString("sha256")) problems.add("Artefato ausente/alterado: $id") }
  return problems
 }
 @Synchronized fun stageIssues(runId:String):List<String> {
  val r=find("runs",runId);val out=artifactIssues(runId).toMutableList()
  val terminal=r.getJSONObject("terminalSnapshot")
  if(listOf("manufacturer","model").any { terminal.optString(it).trim().uppercase() in listOf("","UNKNOWN","DESCONHECIDO","DESCONHECIDA") }) out.add("Equipamento não identificado")
  if(integrityError!=null) out.add(integrityError!!)
  if(!r.getBoolean("physicalAttested")) out.add("Execução física não atestada pelo operador")
  val stage=EvidenceStage.valueOf(r.getString("claimedStage"));if(stage==EvidenceStage.UNVERIFIED) out.add("Nenhuma etapa observada")
  val artifacts=root.getJSONArray("artifacts").objects().filter { it.getString("runId")==runId };val roles=artifacts.map { it.getString("role") }.toSet()
  if("PHYSICAL_CONTEXT" !in roles) out.add("Falta contexto físico identificado")
  if(stage!=EvidenceStage.UNVERIFIED && roles.intersect(setOf("READER_TRACE","TERMINAL_TRACE")).isEmpty()) out.add("Falta trace de transporte/terminal")
  if(stage==EvidenceStage.AUTHENTICATION_RESULT && ("SDK_RESULT" !in roles || !r.has("sdkReport"))) out.add("Falta resultado/traces do SDK oficial")
  if(stage==EvidenceStage.AUTHENTICATION_RESULT && r.optJSONObject("sdkReport")?.optString("authenticationResult") !in listOf("VALID","INVALID","NOT_PERFORMED")) out.add("Falta resultado de autenticação explícito do SDK; PASS do caso não significa autenticação válida")
  if(stage==EvidenceStage.AUTHENTICATION_RESULT && r.has("sdkReport") && r.getJSONObject("sdkReport").optString("result")!=r.getString("outcome")) out.add("Divergência entre resultado observado e resumo SDK")
  if(stage==EvidenceStage.TEST_AUTHORIZATION_RESULT && (r.optString("processorResult") !in listOf("APPROVED","DECLINED") || r.optString("hostCorrelation").isBlank())) out.add("Falta resultado/correlação do host de homologação")
  if(stage==EvidenceStage.TEST_AUTHORIZATION_RESULT && "HOST_TRACE" !in roles) out.add("Falta resposta do processador correlacionada")
  if(stage in listOf(EvidenceStage.AUTHENTICATION_RESULT,EvidenceStage.TEST_AUTHORIZATION_RESULT) && r.getString("environment")!="OFFICIAL_HOMOLOGATION") out.add("Ambiente inadequado")
  if(r.getString("outcome")=="INCONCLUSIVE") out.add("Resultado inconclusivo")
  return out
 }
 @Synchronized fun review(runId:String,reviewer:String,reference:String) {
  val r=find("runs",runId);require(reviewer.isNotBlank() && reviewer!=r.getString("operator") && reference.isNotBlank() && reference.length<=1024) { "Revisor distinto e referência de revisão obrigatórios" }
  require(stageIssues(runId).isEmpty()) { stageIssues(runId).joinToString("; ") }
  mutate("REVIEW_DOCUMENTED_EVIDENCE_NOT_BANK_APPROVAL",runId) { root.getJSONArray("runs").objects().first { it.getString("id")==runId }.put("reviewer",reviewer.take(512)).put("reviewReference",reference).put("reviewedAt",System.currentTimeMillis()).put("validation","REVIEWED_DOCUMENTED_NOT_HARDWARE_ATTESTATION") }
 }
 @Synchronized fun runReport(runId:String):JSONObject { val r=find("runs",runId);val issues=stageIssues(runId);return r.put("terminalProcessorDivergence",r.optString("terminalResult") in listOf("APPROVED","DECLINED") && r.optString("processorResult") in listOf("APPROVED","DECLINED") && r.optString("terminalResult")!=r.optString("processorResult")).put("currentIssues",JSONArray(issues)).put("effectiveStage",if(issues.isEmpty() && r.optString("reviewer").isNotBlank()) r.getString("claimedStage") else "UNVERIFIED").put("authenticationDeclaredBySdk",r.optJSONObject("sdkReport")?.optString("authenticationResult") ?: "UNKNOWN").put("cryptography","NOT_VERIFIED_BY_NFC_LAB").put("settlement","NOT_INFERRED").put("physicalAuthenticity","REQUIRES_HUMAN_REVIEW") }
 @Synchronized fun comparison(beforeId:String,afterId:String):JSONObject {
  val a=runReport(beforeId);val b=runReport(afterId);val c=a.getJSONObject("caseSnapshot");val d=b.getJSONObject("caseSnapshot")
  val comparable=a.getString("team")=="RED" && b.getString("team")=="BLUE" && b.getString("baselineId")==beforeId && c.getString("id")==d.getString("id") && c.getString("revision")==d.getString("revision") && c.optString("officialReference")==d.optString("officialReference") && a.getJSONObject("terminalSnapshot").getString("internalId")==b.getJSONObject("terminalSnapshot").getString("internalId") && a.getString("environmentReference")==b.getString("environmentReference") && a.getString("phoneReference")==b.getString("phoneReference") && a.getJSONObject("terminalSnapshot").optString("manufacturer")==b.getJSONObject("terminalSnapshot").optString("manufacturer") && a.getJSONObject("terminalSnapshot").optString("model")==b.getJSONObject("terminalSnapshot").optString("model") && b.getLong("createdAt")>=a.getLong("createdAt")
  val changes=JSONArray()
  listOf("model","manufacturer","firmware","paymentApp","contactlessKernel").forEach { field -> val x=a.getJSONObject("terminalSnapshot").optString(field);val y=b.getJSONObject("terminalSnapshot").optString(field);if(x!=y) changes.put(JSONObject().put("field",field).put("before",x).put("after",y)) }
  listOf("nfcLabVersion","outcome","effectiveStage","authenticationDeclaredBySdk","terminalResult","processorResult").forEach { field -> if(a.optString(field)!=b.optString(field)) changes.put(JSONObject().put("field",field).put("before",a.optString(field)).put("after",b.optString(field))) }
  return JSONObject().put("changes",changes).put("comparableContext",comparable).put("before",a).put("after",b).put("automaticFixClaim",false)
   .put("conclusion",if(comparable && a.getString("effectiveStage")!="UNVERIFIED" && b.getString("effectiveStage")!="UNVERIFIED") "DOCUMENTED_BEFORE_AFTER_MANUAL_INTERPRETATION_REQUIRED" else "UNVERIFIED_OR_NONCOMPARABLE")
 }
 @Synchronized fun export():JSONObject { check(integrityError==null) { integrityError!! };return snapshot().put("runReports",JSONArray(root.getJSONArray("runs").objects().map { runReport(it.getString("id")) })).put("authenticityLimit","Journal/hash não prova origem física nem impede reescrita integral por quem controla os arquivos")
 }
 companion object {
  fun verifyJournal(state:JSONObject) {
   var previous="GENESIS";val journal=state.getJSONArray("journal");require(journal.length()>0)
   journal.objects().forEachIndexed { i,e -> val copy=cloneJson(e);val hash=copy.remove("hash");require(copy.getInt("sequence")==i+1 && copy.getString("previousHash")==previous && digest(canonical(copy).toByteArray())==hash);previous=e.getString("hash") }
   val data=JSONObject();listOf("cases","analyses","runs","artifacts").forEach { data.put(it,state.getJSONArray(it)) }
   require(journal.getJSONObject(journal.length()-1).getString("stateDigest")==digest(canonical(data).toByteArray())) { "State digest mismatch" }
  }
 }
}
