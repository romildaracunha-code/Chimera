package lab.nfc.core.emv
import lab.nfc.core.apdu.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

enum class DataOrigin { PUBLIC_TEST, IMPORTED_AUTHORIZED, SIMULATED, REAL_HARDWARE }
enum class InputKind { TLV, RESPONSE_APDU, COMMAND_APDU, TRACE_JSON }
fun digest(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).hex()
data class Analysis(val origin:DataOrigin,val kind:InputKind,val source:String,val inputHash:String,val stage:String,val status:String,val rows:List<JSONObject>,val findings:List<String>,val traceSummary:JSONObject?=null) {
 fun json()=JSONObject().put("schemaVersion",3).put("origin",origin.name).put("kind",kind.name).put("source",source).put("inputSha256",inputHash).put("stage",stage).put("statusWord",status).put("nodes",JSONArray(rows)).put("findings",JSONArray(findings)).put("traceSummary",traceSummary ?: JSONObject.NULL).put("authentication","NOT_CRYPTOGRAPHICALLY_VERIFIED").put("authorization","NOT_INFERRED")
 fun text():String=buildString { appendLine("# EMV Explorer • $origin");appendLine("Fonte: $source");appendLine("SHA256: $inputHash");appendLine("Etapa: $stage • SW: $status");appendLine("Não verifica criptografia, autorização ou liquidação. Valores sensíveis/unknown omitidos.");rows.forEach { appendLine("${it.getString("path")} ${it.getString("tag")} ${it.getString("name")} [${it.getInt("length")}] ${it.getString("value")}") };findings.forEach { appendLine("- $it") } }
}
/** Offline structural analysis with explicit provenance; never generates/replays payment traffic. */
object EmvAnalyzer {
 fun stage(ins:Int)=when(ins) { 0xA4->"APPLICATION_SELECTION";0xA8->"GET_PROCESSING_OPTIONS";0xB2->"READ_RECORD";0xAE->"GENERATE_AC_STRUCTURE_ONLY";0x88,0x82->"AUTHENTICATION_STRUCTURE_ONLY";0xCA->"GET_DATA";else->"OTHER_COMMAND" }
 fun analyze(hex:String,kind:InputKind,origin:DataOrigin,source:String,commandContext:String=""):Analysis {
  require(kind!=InputKind.TRACE_JSON) { "Use analyzeTrace para trace JSON" }
  require(source.isNotBlank() && source.length<=1024);require(hex.length<=400000)
  val raw=hex.bytes();require(raw.size<=65544);var body=raw;var sw="";var step="TLV_STRUCTURE";var ins:Int?=null
  val notes=mutableListOf<String>();val rows=mutableListOf<JSONObject>()
  if(kind==InputKind.COMMAND_APDU) { val c=IsoCommand.parse(raw);ins=c.ins;step=stage(c.ins);notes.add("CLA=${c.cla} Lc=${c.data.size} Le=${c.le} extended=${c.extended}; framing não autentica comando");body=if(c.ins==0xA8) c.data else byteArrayOf();if(c.ins!=0xA8) notes.add("Dados do comando omitidos; não há reenvio pelo analisador") }
  else if(kind==InputKind.RESPONSE_APDU) { val response=Response.parse(raw);sw="%04X".format(response.sw);body=response.data;if(commandContext.isNotBlank()) { ins=IsoCommand.parse(commandContext.bytes()).ins;step=stage(ins) } else step="RESPONSE_WITHOUT_COMMAND_CONTEXT";notes.add("SW $sw: ${statusDescription(sw)}; não comprova autenticação/aprovação") }
  if(body.isNotEmpty()) {
   val tree=BerTlv.parse(body);notes.addAll(tree.warnings)
   fun visit(nodes:List<TlvNode>,parent:String) {
    val seen=mutableMapOf<String,Int>()
    nodes.forEach { n -> val i=seen.getOrDefault(n.tag,0);seen[n.tag]=i+1;val path="$parent/${n.tag}[$i]";val info=EmvTags.dictionary[n.tag]
     if(i>0 && n.tag !in setOf("61","70","EF","86","71","72")) notes.add("REPEATED_TAG $path: verificar escopo")
     if(info!=null && n.length !in info.min..info.max) notes.add("INVALID_LENGTH $path esperado ${info.min}..${info.max}")
     rows.add(JSONObject().put("path",path).put("tag",n.tag).put("offset",n.offset).put("length",n.length).put("name",info?.name ?: "Unknown/proprietary").put("value",EmvTags.preview(n)).put("decoded",JSONArray(EmvSemantics.decode(n.tag,n.value))))
     notes.addAll(EmvSemantics.decode(n.tag,n.value).map { "$path $it" })
     if(n.tag=="94") checkAfl(n.value,notes)
     if(n.tag in setOf("8C","8D","9F38")) runCatching { parseDol(n.value) }.onSuccess { notes.add("DOL $path: ${it.joinToString()}") }.onFailure { notes.add("INVALID_DOL $path") }
     if(n.tag=="9F27" && n.length==1) notes.add("CID ${when(n.value[0].toInt() and 0xC0) { 0->"AAC";0x40->"TC (resultado offline, não liquidação)";0x80->"ARQC (pedido online, não autorização)";else->"RFU" }}; criptografia não verificada")
     if(n.tag=="80" && ins==0xA8) { if(n.length<2) notes.add("INVALID_GPO_FORMAT1") else { notes.add("GPO format1 AIP=${n.value.copyOfRange(0,2).hex()}");if(n.length>2) checkAfl(n.value.copyOfRange(2,n.length),notes) } }
     visit(n.children,path)
    }
   };visit(tree.nodes,"");notes.addAll(EmvSemantics.consistency(tree.nodes.flatMap { it.flatten() }))
   if(ins==0xA4 && tree.nodes.none { it.tag=="6F" }) notes.add("SELECT_RESPONSE_WITHOUT_FCI: avaliar perfil, não erro universal")
  }
  if(rows.isEmpty()) notes.add("Nenhum TLV disponível para análise")
  return Analysis(origin,kind,source,digest(raw),step,sw,rows,notes)
 }
 fun checkAfl(b:ByteArray,out:MutableList<String>) { if(b.size%4!=0) { out.add("INVALID_AFL_LENGTH");return };b.asList().chunked(4).forEachIndexed { i,a -> val sfi=(a[0].toInt() and 255) shr 3;val first=a[1].toInt() and 255;val last=a[2].toInt() and 255;val oda=a[3].toInt() and 255;if(sfi !in 1..30 || a[0].toInt() and 7!=0 || first==0 || last<first || oda>last-first+1) out.add("INVALID_AFL_ENTRY $i") } }
 fun parseDol(b:ByteArray):List<String> { var p=0;val out=mutableListOf<String>();while(p<b.size) { val start=p;val first=b[p++].toInt() and 255;require(first !in listOf(0,255));if(first and 31==31) { var n=0;while(true) { require(p<b.size && ++n<=3);val x=b[p++].toInt() and 255;require(n!=1 || x and 127!=0);if(x and 128==0) break } };val tag=b.copyOfRange(start,p).hex();require(p<b.size);out.add("$tag:${b[p++].toInt() and 255}") };return out }
 fun analyzeTrace(raw:String,origin:DataOrigin,source:String):Analysis {
  require(raw.length<=400000 && source.isNotBlank() && source.length<=1024)
  val trace=runCatching { JSONObject(raw) }.getOrElse { throw IllegalArgumentException("Trace JSON inválido; conteúdo não persistido") }
  val packets=runCatching { trace.getJSONArray("exchanges") }.getOrElse { throw IllegalArgumentException("Trace exige array exchanges") };require(packets.length() in 1..128)
  var selected=false;var gpo=false;val notes=mutableListOf<String>();val rows=mutableListOf<JSONObject>()
  for(i in 0 until packets.length()) {
   try {
    val p=packets.getJSONObject(i);val command=p.getString("command");val response=p.getString("response")
    val c=IsoCommand.parse(command.bytes());val r=Response.parse(response.bytes())
    val a=analyze(response,InputKind.RESPONSE_APDU,origin,source,command)
    notes.add("PACKET_$i ${stage(c.ins)} SW=${r.sw.toString(16).uppercase()}")
    if(c.ins==0xA8 && !selected) notes.add("SEQUENCE_INCOMPLETE: GPO sem SELECT observado; captura pode ser parcial")
    if(c.ins in listOf(0xB2,0xAE) && !gpo) notes.add("SEQUENCE_INCOMPLETE: operação sem GPO observado; não prova vulnerabilidade")
    if(c.ins==0xA4 && r.sw==0x9000) selected=true
    if(c.ins==0xA8 && r.sw==0x9000) gpo=true
    a.rows.forEach { node -> val copy=JSONObject(node.toString()).put("path","packet[$i]"+node.getString("path")).put("packetStage",a.stage).put("statusWord",a.status)
     val timestamp=p.opt("timestamp");if(timestamp is Number) copy.put("declaredPacketTimestamp",timestamp.toLong())
     rows.add(copy)
    }
    notes.addAll(a.findings.map { "PACKET_$i $it" })
   } catch(_:IllegalArgumentException) { notes.add("PACKET_${i}_INVALID_FRAMING_OR_TLV") }
   catch(_:org.json.JSONException) { notes.add("PACKET_${i}_INVALID_SCHEMA") }
  }
  notes.add("Ordem parcial e SW não comprovam criptografia/autorização; nenhum APDU é reenviado")
  return Analysis(origin,InputKind.TRACE_JSON,source,digest(raw.toByteArray()),"IMPORTED_PROTOCOL_TIMELINE","",rows,notes)
 }
 fun compare(a:Analysis,b:Analysis):JSONObject {
  fun values(x:Analysis):Map<String,String> {
   return x.rows.associate { it.getString("path") to (it.getString("tag")+":"+it.getInt("length")+":"+it.getString("value")) }
  }
  val x=values(a);val y=values(b)
  return JSONObject().put("leftOrigin",a.origin.name).put("rightOrigin",b.origin.name)
   .put("changedPaths",JSONArray((x.keys+y.keys).filter { x[it]!=y[it] }.sorted()))
   .put("limit","Igualdade após redação não prova igualdade dos dados originais")
 }
}
