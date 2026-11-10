package lab.nfc.core.storage
import lab.nfc.core.emv.digest
import lab.nfc.core.chimera.objects
import org.json.JSONArray
import org.json.JSONObject

/** Encrypted per-session records with an encrypted ID index; pages do not decode the whole history. */
class SessionRepository(private val vault:SecureRepository) {
 private val generation=vault.generation
 companion object {
  fun indexKey(id:String)="session-id/"+digest(id.toByteArray())
  fun recordKey(session:Session):String { require(session.date>=0);return "session/"+session.date.toString().padStart(20,'0')+"/"+digest(session.id.toByteArray()) }
 }
 fun migrateLegacy(bytes:ByteArray?) {
  if(bytes==null) return
  val sessions=JSONArray(bytes.toString(Charsets.UTF_8)).objects().map { Session.from(it) }
  require(sessions.map { it.id }.distinct().size==sessions.size) { "IDs de sessão duplicados; legado preservado" }
  val records=linkedMapOf<String,ByteArray>()
  sessions.forEach { s -> val key=recordKey(s);val json=s.json().toString().toByteArray()
   vault.read(key)?.let { require(it.contentEquals(json)) { "Sessão divergente; cópias preservadas" } }
   records[key]=json;records[indexKey(s.id)]=key.toByteArray()
  }
  // The legacy snapshot is removed in the same SQLite transaction as all rows/indexes.
  vault.writeBatch(records,listOf("legacy/sessions"),generation)
 }
 fun count()=vault.countKeys("session/")
 fun page(offset:Int=0,limit:Int=50):List<Session> =vault.pageKeys("session/",limit,offset).map { key -> Session.from(JSONObject(vault.read(key)!!.toString(Charsets.UTF_8))) }
 fun get(id:String):Session? {
  val key=vault.read(indexKey(id))?.toString(Charsets.UTF_8) ?: return null
  require(key.matches(Regex("session/[0-9]{20}/[A-F0-9]{64}")))
  return vault.read(key)?.let { Session.from(JSONObject(it.toString(Charsets.UTF_8))).also { s -> require(s.id==id && recordKey(s)==key) } }
 }
 fun save(s:Session) {
  val oldKey=vault.read(indexKey(s.id))?.toString(Charsets.UTF_8);val key=recordKey(s)
  vault.writeBatch(mapOf(key to s.json().toString().toByteArray(),indexKey(s.id) to key.toByteArray()),if(oldKey!=null && oldKey!=key) listOf(oldKey) else emptyList(),generation)
 }
 fun all():Sequence<Session> = sequence {
  var offset=0
  while(true) { val page=page(offset);if(page.isEmpty()) break;yieldAll(page);offset+=page.size }
 }
 fun recoverInterrupted() { all().filter { it.result=="Em andamento" }.forEach { save(it.copy(result="Interrompido: processo reiniciado; retome com nova sessão",error="PROCESS_RESTART")) } }
}
