package lab.nfc.core.storage
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import org.json.JSONArray

class LabStore private constructor(context: Context,private val vault:SecureRepository=SecureRepository.get(context)) {
 private val generation=vault.generation
 private val file = File(context.filesDir,"sessions.json")
 private val deviceFile = File(context.filesDir,"devices.json")
 private val experimentFile = File(context.filesDir,"physical-experiments.json")
 private val prefs = context.getSharedPreferences("lab",Context.MODE_PRIVATE)
 @Volatile private var settings:org.json.JSONObject=run {
  val saved=vault.read("settings/lab")
  if(saved!=null) org.json.JSONObject(saved.toString(Charsets.UTF_8)).also { if(prefs.all.isNotEmpty()) check(prefs.edit().clear().commit()) } else {
   val legacy=org.json.JSONObject().put("profile",prefs.getString("profile","LAB-ALPHA"))
    .put("selectedAid",prefs.getString("selectedAid","F04E46434C414201"))
    .put("rejectLabGetData",prefs.getBoolean("rejectLabGetData",false))
    .put("activeHce",prefs.getString("activeHce",null) ?: "")
    .put("profiles",JSONArray(prefs.getStringSet("profiles",setOf("LAB-ALPHA","LAB-BETA"))!!.sorted()))
   vault.write("settings/lab",legacy.toString().toByteArray(),generation)
   check(vault.read("settings/lab")!!.toString(Charsets.UTF_8)==legacy.toString());check(prefs.edit().clear().commit());legacy
  }
 }
 @Synchronized private fun setting(name:String,value:Any) {
  val next=org.json.JSONObject(settings.toString()).put(name,value)
  vault.write("settings/lab",next.toString().toByteArray(),generation);settings=next
 }
 val sessions = MutableStateFlow(readSessions())
 val devices = MutableStateFlow(run { val a=JSONArray(readLegacy(deviceFile,"devices") ?: "[]"); (0 until a.length()).map { DeviceRecord.from(a.getJSONObject(it)) } })
 val experiments = MutableStateFlow(JSONArray(readLegacy(experimentFile,"experiments") ?: "[]").let { a -> (0 until a.length()).map { PhysicalExperiment.from(a.getJSONObject(it)) } })
 val profiles = MutableStateFlow((0 until settings.getJSONArray("profiles").length()).map { settings.getJSONArray("profiles").getString(it) }.sorted())
 var profile: String
 get() = settings.optString("profile","LAB-ALPHA")
 set(value) { setting("profile",value) }
 var selectedAid: String
 get() = settings.optString("selectedAid","F04E46434C414201")
 set(value) { lab.nfc.core.apdu.LabAids.requireLab(value); setting("selectedAid",value) }
 var rejectLabGetData: Boolean
 get() = settings.optBoolean("rejectLabGetData",false)
 set(value) { setting("rejectLabGetData",value) }
 var activeHce: String?
 get() = settings.optString("activeHce").ifBlank { null }
 set(value) { setting("activeHce",value ?: "") }
 init {
  val interrupted=sessions.value.filter { it.result=="Em andamento" }
  interrupted.forEach { save(it.copy(result="Interrompido: processo reiniciado; retome com nova sessão",error="PROCESS_RESTART")) }
  activeHce=null
 }
 private fun readLegacy(file:File,key:String)=vault.migrate(file,"legacy/$key")?.toString(Charsets.UTF_8)
 private fun readSessions(): List<Session> = runCatching { val a=JSONArray(readLegacy(file,"sessions") ?: "[]"); (0 until a.length()).map { Session.from(a.getJSONObject(it)) } }.getOrElse { throw IllegalStateException("Histórico inválido; arquivo preservado",it) }
 @Synchronized fun save(s: Session) {
 val next = listOf(s) + sessions.value.filter { it.id != s.id }
 vault.write("legacy/sessions",Export.json(next).toByteArray(),generation); sessions.value = next
 }
 @Synchronized fun append(id: String, e: Exchange) { sessions.value.find { it.id==id }?.let { save(it.copy(exchanges=it.exchanges+e,result=if(it.result=="Encerrado sem comunicação física") "Encerrado; comandos HCE observados" else it.result)) } }
 @Synchronized fun addProfile(name: String) { require(profiles.value.size<128 || name in profiles.value);require(name.matches(Regex("[A-Za-z0-9_-]{1,32}"))); profiles.value=(profiles.value+name).distinct().sorted(); setting("profiles",JSONArray(profiles.value)); profile=name }
 @Synchronized fun addDevice(d: DeviceRecord) { val next=listOf(d)+devices.value.filter { it.internalId != d.internalId }; vault.write("legacy/devices",JSONArray(next.map { it.json() }).toString(2).toByteArray(),generation); devices.value=next }
 @Synchronized fun saveExperiment(e: PhysicalExperiment) {
  require(e.issues().isEmpty()) { e.issues().joinToString("; ") }
  require(devices.value.any { it.internalId == e.terminalId }) { "Cadastre o terminal em Devices" }
  if(e.labSessionId.isNotBlank()) require(sessions.value.any { it.id == e.labSessionId && it.mode in listOf("HARDWARE HCE","HARDWARE LEITOR") && it.exchanges.isNotEmpty() }) { "Vínculo exige sessão física com APDUs; simulação não é evidência" }
  val snapshot=e.terminalSnapshot ?: devices.value.first { it.internalId == e.terminalId }
  require(snapshot.internalId == e.terminalId) { "Snapshot e terminal divergentes" }
  val saved=e.copy(terminalSnapshot=snapshot)
  val next=listOf(saved)+experiments.value.filter { it.id != e.id }
  vault.write("legacy/experiments",JSONArray(next.map { it.json() }).toString(2).toByteArray(),generation); experiments.value=next
 }
 companion object { internal fun forTesting(context:Context,vault:SecureRepository)=LabStore(context,vault);fun reset() { synchronized(this) { instance=null } }; @Volatile private var instance: LabStore?=null; fun get(context: Context): LabStore = instance ?: synchronized(this) { instance ?: LabStore(context.applicationContext).also { instance=it } } }
}
