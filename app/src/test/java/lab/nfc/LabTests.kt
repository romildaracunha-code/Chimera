package lab.nfc
import lab.nfc.core.apdu.*
import lab.nfc.core.nfc.Capabilities
import lab.nfc.core.storage.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LabTests {
 @Test fun responseParsingAndSerialization() {
 val r=Response.parse("41429000".bytes()); assertEquals(0x9000,r.sw); assertEquals("4142",r.data.hex()); assertEquals("41429000",r.encode().hex())
 assertThrows(IllegalArgumentException::class.java) { Response.parse("90".bytes()) }
 assertEquals("Sucesso",statusDescription("9000"))
 }
 @Test fun rejectOversizedCommandAndInvalidLe() {
 assertThrows(IllegalArgumentException::class.java) { Command(0,0,0,0,ByteArray(256)) }
 assertThrows(IllegalArgumentException::class.java) { Command(0,0,0,0,le=257) }
 }
 @Test fun parseAllShortCasesAndRoundTrip() {
 listOf("00CA0000","00CA000000","00CA000010","00A4040008F04E46434C414201","00A4040008F04E46434C41420100").forEach { assertEquals(it,Command.parse(it.bytes()).encode().hex()) }
 assertEquals(256,Command.parse("00CA000000".bytes()).le)
 }
 @Test fun malformedCommandsRejected() {
 listOf("","00","00A400","00A4040002AA","00A4040000AABB","00A4040001AABBCC").forEach { assertThrows(IllegalArgumentException::class.java) { Command.parse(it.bytes()) } }
 listOf("Z0","0","12-34").forEach { assertThrows(IllegalArgumentException::class.java) { it.bytes() } }
 }
 @Test fun selectAndData() {
 val e=LabEngine(); assertEquals("9000",e.exchange(LabProtocol.SELECT.bytes(),"LAB-ALPHA").hex())
 val r=e.exchange(LabProtocol.GET_DATA.bytes(),"LAB-ALPHA")
 assertEquals("NFC LAB | FICTITIOUS | LAB-ALPHA",r.dropLast(2).toByteArray().toString(Charsets.US_ASCII)); assertEquals("9000",r.takeLast(2).toByteArray().hex())
 }
 @Test fun selectionRequiredAndReset() {
 val e=LabEngine(); assertEquals("6985",e.exchange(LabProtocol.GET_DATA.bytes(),"P").hex()); e.exchange(LabProtocol.SELECT.bytes(),"P"); e.reset(); assertEquals("6985",e.exchange(LabProtocol.GET_DATA.bytes(),"P").hex())
 }
 @Test fun wrongAidClearsSelection() {
 val e=LabEngine(); e.exchange(LabProtocol.SELECT.bytes(),"P"); assertEquals("6A82",e.exchange("00A4040008F04E46434C41420200".bytes(),"P").hex()); assertEquals("6985",e.exchange(LabProtocol.GET_DATA.bytes(),"P").hex())
 }
 @Test fun statusWordsAndErrors() {
 val e=LabEngine()
 mapOf("00" to "6700","80CA000000" to "6E00","00FF0000" to "6D00","00A4000008F04E46434C414201" to "6A86","00CA010000" to "6A86","00CA000001AA" to "6700").forEach { (c,r) -> assertEquals(r,e.exchange(c.bytes(),"P").hex()) }
 e.exchange(LabProtocol.SELECT.bytes(),"P"); assertEquals("6C18",e.exchange("00CA000001".bytes(),"P").hex())
 }
 @Test fun whitespaceHexAndDecode() { assertEquals("00CA000000","00 CA 00 00 00".bytes().hex()); assertEquals("SELECT AID",LabProtocol.decode(LabProtocol.SELECT)); assertEquals("APDU inválido",LabProtocol.decode("00")) }
 @Test fun persistenceAndJsonRoundTrip() {
 val s=Session(device="Test phone",technology="ISO-DEP",mode="SIMULAÇÃO LOCAL",profile="P",notes="comma, quote \" and newline\n",exchanges=listOf(Exchange(123,"TX",LabProtocol.SELECT,"9000",4)))
 val file=File.createTempFile("nfc-session",".json")
 try { file.writeText(Export.json(listOf(s))); val result=Session.from(JSONArray(file.readText()).getJSONObject(0)); assertEquals(s,result); assertEquals("9000",result.exchanges.first().status) } finally { file.delete() }
 }
 @Test fun csvEscapesNotesAndExportsEmptySessions() {
 val s=Session(device="Phone",technology="none",mode="SIM",profile="P",notes="a, \"b\"\nc")
 val csv=Export.csv(listOf(s)); assertTrue(csv.contains("\"a, \"\"b\"\"\nc\"")); assertTrue(csv.contains(s.id)); assertTrue(csv.startsWith("session,date"))
 }
 @Test fun capabilityMatrixDoesNotAssumeHce() {
 assertEquals(Capabilities(true,true,false,true),Capabilities.evaluate(true,true,false,true))
 assertEquals(Capabilities(false,false,false,false),Capabilities.evaluate(false,true,true,false))
 assertEquals(Capabilities(true,false,true,true),Capabilities.evaluate(true,false,true,true))
 assertEquals(Capabilities(true,false,true,false),Capabilities.evaluate(true,true,true,false))
 }
 @Test fun deviceCatalogRoundTrip() { val d=DeviceRecord("A","B","C","unknown","declared","unknown","notes"); assertEquals(d,DeviceRecord.from(d.json())) }
}
