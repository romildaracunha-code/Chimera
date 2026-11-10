package lab.nfc.core.apdu

fun ByteArray.hex() = joinToString("") { "%02X".format(it.toInt() and 255) }
fun String.bytes(): ByteArray {
 val s = replace(Regex("\\s"), "")
 require(s.length % 2 == 0 && s.matches(Regex("[0-9a-fA-F]*"))) { "Hexadecimal inválido" }
 return s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
data class Command(val cla: Int, val ins: Int, val p1: Int, val p2: Int, val data: ByteArray = byteArrayOf(), val le: Int? = null) {
 init { require(listOf(cla,ins,p1,p2).all { it in 0..255 }); require(data.size <= 255); require(le == null || le in 1..256) }
 fun encode(): ByteArray = byteArrayOf(cla.toByte(),ins.toByte(),p1.toByte(),p2.toByte()) + (if(data.isEmpty()) byteArrayOf() else byteArrayOf(data.size.toByte()) + data) + (le?.let { byteArrayOf((it % 256).toByte()) } ?: byteArrayOf())
 companion object {
 fun parse(b: ByteArray): Command {
 require(b.size >= 4) { "Cabeçalho incompleto" }
 var data = byteArrayOf(); var le: Int? = null
 if(b.size == 5) le = (b[4].toInt() and 255).let { if(it == 0) 256 else it }
 if(b.size > 5) {
 val n = b[4].toInt() and 255
 require(n > 0 && (b.size == 5+n || b.size == 6+n)) { "Lc inválido; APDU estendido não suportado" }
 data = b.copyOfRange(5,5+n)
 if(b.size == 6+n) le = (b.last().toInt() and 255).let { if(it == 0) 256 else it }
 }
 return Command(b[0].toInt() and 255,b[1].toInt() and 255,b[2].toInt() and 255,b[3].toInt() and 255,data,le)
 }
 }
}
object LabProtocol {
 const val AID = "F04E46434C414201"
 const val SELECT = "00A4040008F04E46434C41420100"
 const val GET_DATA = "00CA000000"
 fun decode(hex: String) = runCatching { when(Command.parse(hex.bytes()).ins) { 0xA4 -> "SELECT AID"; 0xCA -> "GET DATA"; else -> "Instrução desconhecida" } }.getOrDefault("APDU inválido")
}
class LabEngine(private val aid: String=LabProtocol.AID, private val rejectGetData: Boolean=false) {
 init { LabAids.requireLab(aid) }
 private var selected = false
 fun reset() { selected = false }
 fun exchange(raw: ByteArray, profile: String): ByteArray {
 val c = try { IsoCommand.parse(raw) } catch(_: IllegalArgumentException) { return "6700".bytes() }
 if(c.cla != 0) return "6E00".bytes()
 return when(c.ins) {
 0xA4 -> {
 selected = false
 when { c.p1 != 4 || c.p2 != 0 -> "6A86".bytes(); c.data.hex() != aid -> "6A82".bytes(); else -> { selected = true; "9000".bytes() } }
 }
 0xCA -> when { c.p1 != 0 || c.p2 != 0 -> "6A86".bytes(); c.data.isNotEmpty() -> "6700".bytes(); !selected || rejectGetData -> "6985".bytes(); else -> {
 val data = "NFC LAB | FICTITIOUS | $profile".toByteArray(Charsets.US_ASCII)
 if(c.le != null && c.le < data.size) byteArrayOf(0x6C, data.size.toByte()) else data + "9000".bytes()
 } }
 else -> "6D00".bytes()
 }
 }
}

/** Response APDU (short/extended response body, 2-byte status trailer). */
data class Response(val data: ByteArray, val sw: Int) {
 init { require(sw in 0..65535) }
 fun encode() = data + byteArrayOf((sw shr 8).toByte(),sw.toByte())
 companion object { fun parse(raw: ByteArray): Response { require(raw.size>=2) { "Resposta sem status word" }; return Response(raw.copyOfRange(0,raw.size-2),((raw[raw.size-2].toInt() and 255) shl 8) or (raw.last().toInt() and 255)) } }
}
fun statusDescription(sw: String) = when(sw) {
 "9000" -> "Sucesso"; "6700" -> "Comprimento inválido"; "6985" -> "Condições não satisfeitas / sessão não selecionada"; "6A82" -> "AID não encontrado"; "6A86" -> "P1/P2 inválidos"; "6D00" -> "INS não suportada"; "6E00" -> "CLA não suportada"; else -> if(sw.startsWith("6C")) "Le incorreto; tamanho indicado em SW2" else "Status externo / desconhecido"
}
