package lab.nfc

import lab.nfc.core.apdu.*
import lab.nfc.core.emv.*
import lab.nfc.core.nfc.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class EmvTests {
 @Test fun nestedFciAndOffsets() { val t=BerTlv.parse("6F0A8408F04E46434C414201".bytes());assertEquals("6F",t.nodes[0].tag);assertEquals(2,t.nodes[0].children[0].offset);assertEquals("F04E46434C414201",t.nodes[0].children[0].value.hex()) }
 @Test fun definiteLongLengthAndEmptyValue() { val t=BerTlv.parse("DF018180".bytes()+ByteArray(128));assertEquals(128,t.nodes.single().length);assertEquals(2,BerTlv.parse("E3020100".bytes()).nodes.single().flatten().size) }
 @Test fun rejectsIndefiniteTruncatedOverflowAndBadTag() { listOf("6F800000","9F","5A040102","5A85FFFFFFFFFF","5A84FFFFFFFF","DF800100","6F028401").forEach { assertThrows(IllegalArgumentException::class.java) { BerTlv.parse(it.bytes()) } } }
 @Test fun boundedInputDepthAndNodeBudget() {
  assertThrows(IllegalArgumentException::class.java) { BerTlv.parse(ByteArray(65537)) }
  var b="5000".bytes();repeat(20) { b=byteArrayOf(0xE3.toByte(),b.size.toByte())+b }
  assertThrows(IllegalArgumentException::class.java) { BerTlv.parse(b) }
  assertThrows(IllegalArgumentException::class.java) { BerTlv.parse("500100".repeat(10).bytes(),maxNodes=5) }
 }
 @Test fun paddingAndNonMinimalLengthAreExplicit() { assertTrue(BerTlv.parse("0050810456495341FF".bytes()).warnings.any { it.startsWith("NON_MINIMAL") });assertThrows(IllegalArgumentException::class.java) { BerTlv.parse("005000".bytes(),allowPadding=false) } }
 @Test fun piiCryptogramsAndUnknownValuesNeverReachAnalysisExport() {
  val a=EmvAnalyzer.analyze("5A04010203049F26081122334455667788DF0106998877665544",InputKind.TLV,DataOrigin.IMPORTED_AUTHORIZED,"SOFTWARE FIXTURE")
  val s=a.json().toString();assertFalse(s.contains("01020304"));assertFalse(s.contains("1122334455667788"));assertFalse(s.contains("998877665544"));assertTrue(s.contains("REDACTED"));assertTrue(s.contains("OPAQUE"))
  val format1=EmvAnalyzer.analyze("800801020304050607089000",InputKind.RESPONSE_APDU,DataOrigin.IMPORTED_AUTHORIZED,"FIXTURE","80AE000000")
  assertFalse(format1.json().toString().contains("0102030405060708"))
 }
 @Test fun gpoAflDolAndCidAreStructuralNotAuthorization() {
  val a=EmvAnalyzer.analyze("80061234080101009000",InputKind.RESPONSE_APDU,DataOrigin.PUBLIC_TEST,"SYNTHETIC UNIT FIXTURE","80A8000002830000")
  assertEquals("GET_PROCESSING_OPTIONS",a.stage);assertEquals("NOT_INFERRED",a.json().getString("authorization"));assertTrue(a.findings.any { it.contains("AIP=1234") })
  val b=EmvAnalyzer.analyze("9404080201019F2701809F38039F0206",InputKind.TLV,DataOrigin.SIMULATED,"UNIT FIXTURE")
  assertTrue(b.findings.any { it.contains("INVALID_AFL_ENTRY") });assertTrue(b.findings.any { it.contains("ARQC") });assertEquals(listOf("9F02:6","9F1A:2"),EmvAnalyzer.parseDol("9F02069F1A02".bytes()))
  assertThrows(IllegalArgumentException::class.java) { EmvAnalyzer.parseDol("9F02".bytes()) }
 }
 @Test fun invalidKnownLengthAndRepeatedTagAreReported() { val a=EmvAnalyzer.analyze("82010082020000",InputKind.TLV,DataOrigin.SIMULATED,"UNIT FIXTURE");assertTrue(a.findings.any { it.startsWith("INVALID_LENGTH") });assertTrue(a.findings.any { it.startsWith("REPEATED_TAG") }) }
 @Test fun publishedPublicCorpusIsActuallyParsed() {
  val j=JSONObject(requireNotNull(javaClass.classLoader!!.getResourceAsStream("chimera/public-vectors.json")).bufferedReader().use { it.readText() })
  val a=j.getJSONArray("vectors");assertTrue(a.length()>=3)
  for(i in 0 until a.length()) { val v=a.getJSONObject(i);val tree=BerTlv.parse(v.getString("hex").bytes());assertEquals(v.getString("id"),v.getInt("nodeCount"),tree.nodes.flatMap { it.flatten() }.size);assertEquals(v.getString("rootTag"),tree.nodes.first().tag);assertEquals(DataOrigin.PUBLIC_TEST,EmvAnalyzer.analyze(v.getString("hex"),InputKind.TLV,DataOrigin.PUBLIC_TEST,j.getString("sourceUrl")).origin) }
 }
 @Test fun allIso7816CasesRoundTripAndMalformedFramesFail() {
  listOf("00CA0000","00CA000000","00A4040008F04E46434C414201","00A4040008F04E46434C41420100","00CA0000000000","00CA0000000001").forEach { assertEquals(it,IsoCommand.parse(it.bytes()).encode().hex()) }
  val random=Random(9);repeat(50) { val c=IsoCommand(0,0xCA,0,0,random.nextBytes(random.nextInt(1,700)),random.nextInt(1,65537),true);assertArrayEquals(c.encode(),IsoCommand.parse(c.encode()).encode()) }
  listOf("00","00CA00000000","00CA000000000001","00CA0000000002AA","00CA000001AABBCC").forEach { assertThrows(IllegalArgumentException::class.java) { IsoCommand.parse(it.bytes()) } }
  assertEquals(65536,IsoCommand.parse("00CA0000000000".bytes()).le)
 }
 @Test fun configurableLabAidsPreserveOldDefaultAndNegativeProfile() {
  LabAids.all.forEach { aid -> val engine=LabEngine(aid,true);assertEquals("9000",engine.exchange(LabAids.select(aid).bytes(),"FIXTURE").hex());assertEquals("6985",engine.exchange(LabProtocol.GET_DATA.bytes(),"FIXTURE").hex()) }
  val extended=LabEngine();assertEquals("9000",extended.exchange(IsoCommand(0,0xA4,4,0,LabProtocol.AID.bytes(),65536,true).encode(),"FIXTURE").hex());assertTrue(extended.exchange(IsoCommand(0,0xCA,0,0,le=65536,extended=true).encode(),"FIXTURE").hex().endsWith("9000"))
  assertThrows(IllegalArgumentException::class.java) { LabAids.requireLab("A0000000031010") }
  assertEquals(LabProtocol.SELECT,LabAids.select(LabProtocol.AID))
 }
 private class Wire:IsoTransport { override val maxCommandBytes=1024;override var extendedSupported=false;var closed=0;var sent=0;var response="9000".bytes();var error=false
  override fun connect() {} ;override fun close() { closed++ };override fun transceive(command:ByteArray):ByteArray { sent++;if(error) throw java.io.IOException("UNIT FIXTURE DISCONNECT");return response } }
 @Test fun isoSessionGuardsStateAndClosesOnTransportFailure() {
  val w=Wire();val s=IsoSession(w,LabProtocol.AID);s.connect();assertThrows(IsoPreflightFailure::class.java) { s.transceive(LabProtocol.GET_DATA.bytes()) };assertEquals(0,w.sent)
  s.transceive(LabProtocol.SELECT.bytes());assertEquals(IsoSessionState.APPLICATION_SELECTED,s.state)
  w.error=true;assertThrows(java.io.IOException::class.java) { s.transceive(LabProtocol.GET_DATA.bytes()) };s.close();assertEquals(1,w.closed);assertEquals(IsoSessionState.CLOSED,s.state)
 }
 @Test fun isoSessionPreservesMalformedResponseAndPreflightMeansNotSent() {
  val w=Wire();val s=IsoSession(w,LabProtocol.AID);s.connect();assertThrows(IsoPreflightFailure::class.java) { s.transceive(IsoCommand(0,0xCA,0,0,ByteArray(300),1,true).encode()) };assertEquals(0,w.sent)
  w.response="90".bytes();val error=assertThrows(IsoMalformedResponse::class.java) { s.transceive(LabProtocol.SELECT.bytes()) };assertEquals("90",error.raw.hex());assertEquals(1,w.closed)
 }
 @Test fun importedTraceHasTimelineWithoutRawCredentialsOrFalseHardware() {
  val raw="""{"exchanges":[{"command":"00B2010C00","response":"70065A04010203049000","timestamp":123},{"command":"00","response":"90"}]}"""
  val report=EmvAnalyzer.analyzeTrace(raw,DataOrigin.IMPORTED_AUTHORIZED,"SOFTWARE FIXTURE")
  assertEquals("IMPORTED_PROTOCOL_TIMELINE",report.stage);assertTrue(report.findings.any { it.contains("SEQUENCE_INCOMPLETE") });assertTrue(report.findings.any { it.contains("INVALID") })
  assertFalse(report.json().toString().contains("01020304"));assertEquals(123L,report.rows[0].getLong("declaredPacketTimestamp"))
 }
 @Test fun traceLimitsAndMalformedJsonFailWithoutEchoingPayload() {
  assertThrows(IllegalArgumentException::class.java) { EmvAnalyzer.analyzeTrace("not-json-PRIVATE-FIXTURE",DataOrigin.IMPORTED_AUTHORIZED,"FIXTURE") }
  assertThrows(IllegalArgumentException::class.java) { EmvAnalyzer.analyzeTrace("{\"exchanges\":[]}",DataOrigin.IMPORTED_AUTHORIZED,"FIXTURE") }
 }
 @Test fun structuralComparisonRetainsOriginAndRedactionLimit() {
  val a=EmvAnalyzer.analyze("82020000",InputKind.TLV,DataOrigin.SIMULATED,"A");val b=EmvAnalyzer.analyze("82020001",InputKind.TLV,DataOrigin.IMPORTED_AUTHORIZED,"B")
  val diff=EmvAnalyzer.compare(a,b);assertEquals(1,diff.getJSONArray("changedPaths").length());assertEquals("SIMULATED",diff.getString("leftOrigin"))
 }
}
