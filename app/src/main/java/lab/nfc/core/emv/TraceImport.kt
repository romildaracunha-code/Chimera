package lab.nfc.core.emv
import lab.nfc.core.apdu.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Import is provenance-preserving analysis, never an assertion of locally observed RF. */
object TraceImport {
 fun analyze(raw:String,source:String):List<Analysis> {
  require(raw.length<=400000)
  val sessions=if(raw.trimStart().startsWith("[")) JSONArray(raw) else JSONArray().put(JSONObject(raw))
  require(sessions.length() in 1..128)
  return (0 until sessions.length()).map { index ->
   val session=sessions.getJSONObject(index);val packets=session.getJSONArray("exchanges");require(packets.length() in 1..128)
   val simulated=session.optString("mode").let { it.contains("SIMUL",true) || it.contains("LOCAL",true) }
   val origin=if(simulated) DataOrigin.SIMULATED else DataOrigin.IMPORTED_AUTHORIZED
   val format=when { session.optString("mode")=="PHYSICAL_PCSC_LAB"->"NFC_LAB_PCSC_V1";session.has("profile") && session.has("technology")->"NFC_LAB_SESSION";else->"EXCHANGES_JSON" }
   val clean=JSONArray();val attempts=JSONArray();val times=mutableListOf<Double>();var failures=0
   for(i in 0 until packets.length()) {
    val p=packets.getJSONObject(i);val command=p.optString("command");val response=p.optString("response")
    val c=runCatching { IsoCommand.parse(command.bytes()) }.getOrNull()
    val error=p.optString("transportError").ifBlank { p.optString("error") }
    val r=runCatching { Response.parse(response.bytes()) }.getOrNull()
    val failed=error.isNotBlank() || c==null || r==null
    if(failed) failures++
    val duration=when { p.has("durationNanos")->p.optDouble("durationNanos",Double.NaN)/1000000.0;p.has("durationMs")->p.optDouble("durationMs",Double.NaN);else->Double.NaN }
    if(duration.isFinite() && duration>=0 && duration<=3600000) times.add(duration)
    val point=p.optString("capturePoint","UNSPECIFIED").take(128)
    val a=JSONObject().put("index",i).put("instruction",c?.ins?.let { "%02X".format(it) } ?: "INVALID").put("stage",c?.let { EmvAnalyzer.stage(it.ins) } ?: "INVALID")
     .put("statusWord",r?.let { "%04X".format(it.sw) } ?: "UNAVAILABLE").put("failed",failed).put("capturePoint",point)
     .put("errorClass",if(error.isNotBlank()) "REPORTED_TRANSPORT_ERROR" else if(c==null || r==null) "INVALID_OR_MISSING_FRAMING" else "NONE")
    if(duration.isFinite() && duration>=0 && duration<=3600000) a.put("declaredDurationMs",duration)
    val timestamp=p.opt("timestamp");val parsed=when(timestamp) { is Number->timestamp.toLong();is String->runCatching { Instant.parse(timestamp).toEpochMilli() }.getOrNull();else->null }
    if(parsed!=null) a.put("declaredTimestamp",parsed)
    attempts.put(a)
    if(c!=null && r!=null) clean.put(JSONObject().put("command",command).put("response",response).put("timestamp",parsed))
   }
   val base=if(clean.length()>0) EmvAnalyzer.analyzeTrace(JSONObject().put("exchanges",clean).toString(),origin,source) else Analysis(origin,InputKind.TRACE_JSON,source,digest(raw.toByteArray()),"IMPORTED_PROTOCOL_TIMELINE","",emptyList(),listOf("No complete APDU pair available"))
   val summary=JSONObject().put("format",format).put("declaredOrigin",if(simulated) "SIMULATED" else "EXTERNAL_NOT_LOCALLY_OBSERVED")
    .put("attempts",attempts).put("attemptCount",packets.length()).put("failureCount",failures).put("latency",latency(times))
    .put("timingLimit","API/PCSC timing is source-declared and includes host overhead; not RF timing")
   val config=JSONObject()
   listOf("technology","mode","labScenario").forEach { key -> if(session.has(key)) config.put(key,session.optString(key).take(128)) }
   val aid=session.optString("labAid",session.optString("aid"));if(aid in LabAids.all) config.put("labAid",aid)
   summary.put("configuration",config)
   base.copy(inputHash=digest(session.toString().toByteArray()),findings=base.findings+"$format session=$index imported; failures=$failures; source-declared timings",traceSummary=summary)
  }
 }
 fun latency(values:List<Double>):JSONObject {
  val s=values.sorted();val out=JSONObject().put("count",s.size)
  if(s.isNotEmpty()) { val n=s.size;val median=if(n%2==0) (s[n/2-1]+s[n/2])/2 else s[n/2];out.put("minMs",s.first()).put("maxMs",s.last()).put("medianMs",median).put("p95Ms",s[(kotlin.math.ceil(n*0.95).toInt()-1).coerceAtLeast(0)]) };return out
 }
 fun compare(a:JSONObject,b:JSONObject):JSONObject {
  fun values(x:JSONObject)=x.getJSONArray("nodes").let { ar -> (0 until ar.length()).associate { val n=ar.getJSONObject(it);n.getString("path") to "${n.getString("tag")}:${n.getInt("length")}:${n.getString("value")}" } }
  val x=values(a);val y=values(b);val sa=a.optJSONObject("traceSummary");val sb=b.optJSONObject("traceSummary")
  val out=JSONObject().put("leftOrigin",a.getString("origin")).put("rightOrigin",b.getString("origin"))
   .put("changedPaths",JSONArray((x.keys+y.keys).filter { x[it]!=y[it] }.sorted()))
   .put("leftTrace",sa ?: JSONObject.NULL).put("rightTrace",sb ?: JSONObject.NULL)
   .put("limit","Redacted equality does not prove original equality; timing comparisons require equal capture scopes, hardware and workload; no authorization inferred")
  if(sa!=null && sb!=null) {
   fun sequence(s:JSONObject):List<String> { val ar=s.getJSONArray("attempts");return (0 until ar.length()).map { val n=ar.getJSONObject(it);n.getString("instruction")+":"+n.getString("statusWord")+":"+n.getBoolean("failed") } }
   out.put("sequenceEqual",sequence(sa)==sequence(sb)).put("configurationEqual",sa.getJSONObject("configuration").toString()==sb.getJSONObject("configuration").toString())
   fun scopes(s:JSONObject):Set<String> { val ar=s.getJSONArray("attempts");return (0 until ar.length()).map { ar.getJSONObject(it).getString("capturePoint") }.toSet() }
   out.put("sameDeclaredTimingScope",scopes(sa)==scopes(sb) && "UNSPECIFIED" !in scopes(sa))
  }
  return out
 }
}
