package lab.nfc.core.storage
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Exchange(val timestamp: Long, val direction: String, val command: String, val response: String, val durationMs: Long, val durationNanos: Long = durationMs * 1_000_000, val capturePoint: String = "LEGACY_UNSPECIFIED", val transportError: String = "") {
 val status get() = response.takeLast(4)
 fun json() = JSONObject().put("timestamp",timestamp).put("direction",direction).put("command",command).put("response",response).put("durationMs",durationMs).put("durationNanos",durationNanos).put("capturePoint",capturePoint).put("transportError",transportError)
}
data class Session(val id: String = UUID.randomUUID().toString(), val date: Long = System.currentTimeMillis(), val device: String, val technology: String, val mode: String, val profile: String, val result: String = "Em andamento", val error: String = "", val notes: String = "", val exchanges: List<Exchange> = emptyList()) {
 fun json() = JSONObject().put("id",id).put("date",date).put("device",device).put("technology",technology).put("mode",mode).put("profile",profile).put("result",result).put("error",error).put("notes",notes).put("exchanges",JSONArray(exchanges.map { it.json() }))
 companion object {
  fun from(j: JSONObject): Session {
   val array = j.getJSONArray("exchanges")
   val records = (0 until array.length()).map { index ->
    val x = array.getJSONObject(index)
    Exchange(x.getLong("timestamp"), x.getString("direction"), x.getString("command"), x.getString("response"), x.getLong("durationMs"),x.optLong("durationNanos",x.getLong("durationMs")*1_000_000),x.optString("capturePoint","LEGACY_UNSPECIFIED"),x.optString("transportError"))
   }
   return Session(j.getString("id"), j.getLong("date"), j.getString("device"), j.getString("technology"), j.getString("mode"), j.getString("profile"), j.getString("result"), j.optString("error"), j.optString("notes"), records)
  }
 }

}
object Export {
 fun json(sessions: List<Session>) = JSONArray(sessions.map { it.json() }).toString(2)
 private fun cell(s: Any) = "\"" + s.toString().replace("\"", "\"\"") + "\""
 fun csv(sessions: List<Session>): String = "session,date,device,technology,mode,profile,result,error,notes,timestamp,direction,command,response,status,durationMs,durationNanos,capturePoint,transportError\r\n" + sessions.flatMap { s -> (s.exchanges.ifEmpty { listOf(Exchange(s.date,"","","",0)) }).map { e -> listOf(s.id,s.date,s.device,s.technology,s.mode,s.profile,s.result,s.error,s.notes,e.timestamp,e.direction,e.command,e.response,e.status,e.durationMs,e.durationNanos,e.capturePoint,e.transportError).joinToString(",") { cell(it) } } }.joinToString("\r\n")
}
data class DeviceRecord(
 val manufacturer: String, val model: String, val internalId: String, val firmware: String,
 val nfc: String, val sdk: String, val notes: String,
 val acquirer: String = "Desconhecida", val paymentApp: String = "Desconhecido",
 val contactlessKernel: String = "Desconhecido", val homologation: String = "Não confirmada",
 val evidenceReference: String = "", val accessContact: String = ""
) {
 fun json() = JSONObject().put("manufacturer",manufacturer).put("model",model).put("internalId",internalId).put("firmware",firmware).put("nfc",nfc).put("sdk",sdk).put("notes",notes)
  .put("acquirer",acquirer).put("paymentApp",paymentApp).put("contactlessKernel",contactlessKernel).put("homologation",homologation).put("evidenceReference",evidenceReference).put("accessContact",accessContact)
 companion object { fun from(j: JSONObject) = DeviceRecord(j.getString("manufacturer"),j.getString("model"),j.getString("internalId"),j.getString("firmware"),j.getString("nfc"),j.getString("sdk"),j.getString("notes"),j.optString("acquirer","Desconhecida"),j.optString("paymentApp","Desconhecido"),j.optString("contactlessKernel","Desconhecido"),j.optString("homologation","Não confirmada"),j.optString("evidenceReference"),j.optString("accessContact")) }
}
