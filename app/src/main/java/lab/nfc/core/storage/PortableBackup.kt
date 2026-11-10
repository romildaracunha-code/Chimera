package lab.nfc.core.storage
import lab.nfc.core.chimera.ChimeraLedger
import lab.nfc.core.chimera.objects
import lab.nfc.core.emv.digest
import lab.nfc.core.security.SecureCodec
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Authenticated whole-workspace backup, validated before Room's atomic replacement. */
object PortableBackup {
 fun pack(records:Map<String,ByteArray>,password:CharArray):ByteArray {
  return SecureCodec.backup(encode(records),password)
 }
 fun encode(records:Map<String,ByteArray>):ByteArray {
  validate(records)
  val array=JSONArray();records.toSortedMap().forEach { (key,value) -> array.put(JSONObject().put("key",key).put("sha256",digest(value)).put("data",Base64.getEncoder().encodeToString(value))) }
  val plain=JSONObject().put("format","NFCLAB_PORTABLE_1").put("records",array).toString().toByteArray()
  require(plain.size<=100*1024*1024) { "Backup excede limite de 100 MiB" }
  return plain
 }
 fun unpack(encrypted:ByteArray,password:CharArray):Map<String,ByteArray> {
  return decode(SecureCodec.restore(encrypted,password))
 }
 fun decode(plain:ByteArray):Map<String,ByteArray> {
  require(plain.size<=100*1024*1024)
  val root=JSONObject(plain.toString(Charsets.UTF_8));require(root.getString("format")=="NFCLAB_PORTABLE_1")
  val records=linkedMapOf<String,ByteArray>();val array=root.getJSONArray("records");require(array.length()<=10000)
  array.objects().forEach { row -> val key=row.getString("key");require(key !in records);val bytes=Base64.getDecoder().decode(row.getString("data"));require(digest(bytes)==row.getString("sha256"));records[key]=bytes }
  validate(records);return records
 }
 fun validate(records:Map<String,ByteArray>) {
  require(records.isNotEmpty() && records.size<=10000 && records.values.sumOf { it.size.toLong() }<=70L*1024*1024)
  records.forEach { (key,bytes) -> require(bytes.size<=20*1024*1024 && (key in setOf("legacy/sessions","legacy/devices","legacy/experiments","settings/lab","settings/retention","chimera/workspace-v3.json") || key.matches(Regex("chimera/[a-f0-9-]{36}\\.bin")) || key.matches(Regex("session/[0-9]{20}/[A-F0-9]{64}")) || key.matches(Regex("session-id/[A-F0-9]{64}")) || key.matches(Regex("draft/[a-zA-Z0-9_-]{1,64}")))) { "Registro desconhecido/excessivo no backup" } }
  records.filterKeys { it.startsWith("session/") }.forEach { (key,bytes) ->
   val session=Session.from(JSONObject(bytes.toString(Charsets.UTF_8)));require(SessionRepository.recordKey(session)==key)
   require(records[SessionRepository.indexKey(session.id)]?.toString(Charsets.UTF_8)==key) { "Índice de sessão ausente ou divergente" }
  }
  records.filterKeys { it.startsWith("session-id/") }.forEach { (key,bytes) ->
   val target=bytes.toString(Charsets.UTF_8);val row=records[target] ?: error("Índice aponta para sessão ausente")
   require(target.startsWith("session/") && SessionRepository.indexKey(Session.from(JSONObject(row.toString(Charsets.UTF_8))).id)==key)
  }
  records["legacy/sessions"]?.let { JSONArray(it.toString(Charsets.UTF_8)).objects().forEach { row -> Session.from(row) } }
  records["legacy/devices"]?.let { JSONArray(it.toString(Charsets.UTF_8)).objects().forEach { row -> DeviceRecord.from(row) } }
  records["legacy/experiments"]?.let { JSONArray(it.toString(Charsets.UTF_8)).objects().forEach { row -> PhysicalExperiment.from(row) } }
  records["settings/retention"]?.let { val policy=JSONObject(it.toString(Charsets.UTF_8));require(policy.get("enabled") is Boolean && policy.getInt("days") in 1..3650) }
  records["settings/lab"]?.let { val s=JSONObject(it.toString(Charsets.UTF_8));lab.nfc.core.apdu.LabAids.requireLab(s.getString("selectedAid"));require(s.getJSONArray("profiles").length() in 1..128) }
  records["chimera/workspace-v3.json"]?.let { bytes ->
   val ledger=JSONObject(bytes.toString(Charsets.UTF_8));require(ledger.getInt("schemaVersion")==3);ChimeraLedger.verifyJournal(ledger)
   ledger.getJSONArray("artifacts").objects().forEach { a -> val b=records["chimera/${a.getString("id")}.bin"] ?: error("Backup incompleto: anexo ausente");require(b.size.toLong()==a.getLong("size") && digest(b)==a.getString("sha256")) }
  }
 }
}
