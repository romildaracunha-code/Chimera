package lab.nfc.core.storage
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import org.json.JSONArray

class LabStore private constructor(context: Context) {
 private val file = File(context.filesDir,"sessions.json")
 private val deviceFile = File(context.filesDir,"devices.json")
 private val experimentFile = File(context.filesDir,"physical-experiments.json")
 private val prefs = context.getSharedPreferences("lab",Context.MODE_PRIVATE)
 val sessions = MutableStateFlow(readSessions())
 val devices = MutableStateFlow(runCatching { val a=JSONArray(deviceFile.readText()); (0 until a.length()).map { DeviceRecord.from(a.getJSONObject(it)) } }.getOrDefault(emptyList()))
 val experiments = MutableStateFlow(if (!experimentFile.exists()) emptyList() else JSONArray(experimentFile.readText()).let { a -> (0 until a.length()).map { PhysicalExperiment.from(a.getJSONObject(it)) } })
 val profiles = MutableStateFlow(prefs.getStringSet("profiles",setOf("LAB-ALPHA", "LAB-BETA"))!!.sorted())
 var profile: String
 get() = prefs.getString("profile","LAB-ALPHA")!!
 set(value) { prefs.edit().putString("profile",value).commit() }
 var activeHce: String?
 get() = prefs.getString("activeHce",null)
 set(value) { prefs.edit().putString("activeHce",value).commit() }
 private fun readSessions(): List<Session> = if(!file.exists()) emptyList() else runCatching { val a=JSONArray(file.readText()); (0 until a.length()).map { Session.from(a.getJSONObject(it)) } }.getOrElse { throw IllegalStateException("Histórico inválido; arquivo preservado",it) }
 @Synchronized fun save(s: Session) {
 val next = listOf(s) + sessions.value.filter { it.id != s.id }
 val temp = File(file.parentFile,"sessions.tmp"); temp.writeText(Export.json(next)); check(temp.renameTo(file)) { "Falha ao salvar histórico" }; sessions.value = next
 }
 @Synchronized fun append(id: String, e: Exchange) { sessions.value.find { it.id==id }?.let { save(it.copy(exchanges=it.exchanges+e,result=if(it.result=="Encerrado sem comunicação física") "Encerrado; comandos HCE observados" else it.result)) } }
 fun addProfile(name: String) { require(name.matches(Regex("[A-Za-z0-9_-]{1,32}"))); profiles.value=(profiles.value+name).distinct().sorted(); prefs.edit().putStringSet("profiles",profiles.value.toSet()).commit(); profile=name }
 @Synchronized fun addDevice(d: DeviceRecord) { val next=listOf(d)+devices.value.filter { it.internalId != d.internalId }; val tmp=File(deviceFile.parentFile,"devices.tmp"); tmp.writeText(JSONArray(next.map { it.json() }).toString(2)); check(tmp.renameTo(deviceFile)); devices.value=next }
 @Synchronized fun saveExperiment(e: PhysicalExperiment) {
  require(e.issues().isEmpty()) { e.issues().joinToString("; ") }
  require(devices.value.any { it.internalId == e.terminalId }) { "Cadastre o terminal em Devices" }
  if(e.labSessionId.isNotBlank()) require(sessions.value.any { it.id == e.labSessionId && it.mode in listOf("HARDWARE HCE","HARDWARE LEITOR") && it.exchanges.isNotEmpty() }) { "Vínculo exige sessão física com APDUs; simulação não é evidência" }
  val snapshot=e.terminalSnapshot ?: devices.value.first { it.internalId == e.terminalId }
  require(snapshot.internalId == e.terminalId) { "Snapshot e terminal divergentes" }
  val saved=e.copy(terminalSnapshot=snapshot)
  val next=listOf(saved)+experiments.value.filter { it.id != e.id }
  val tmp=File(experimentFile.parentFile,"physical-experiments.tmp"); tmp.writeText(JSONArray(next.map { it.json() }).toString(2)); check(tmp.renameTo(experimentFile)); experiments.value=next
 }
 companion object { @Volatile private var instance: LabStore?=null; fun get(context: Context): LabStore = instance ?: synchronized(this) { instance ?: LabStore(context.applicationContext).also { instance=it } } }
}
