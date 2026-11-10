package lab.nfc

import lab.nfc.core.apdu.*
import lab.nfc.core.chimera.*
import lab.nfc.core.emv.*
import lab.nfc.core.integration.*
import lab.nfc.core.security.SecureCodec
import lab.nfc.core.storage.PortableBackup
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import javax.crypto.spec.SecretKeySpec

class HardeningTests {
 private fun rejects(action:()->Unit) { try { action();fail("Expected rejection") } catch(_:IllegalArgumentException) {} }
 @Test fun aesEnvelopeAuthenticatesContextAndTampering() {
  val key=SecretKeySpec(ByteArray(32) { it.toByte() },"AES");val plain="not a real credential".toByteArray()
  val sealed=SecureCodec.seal(plain,key,"row-a");assertArrayEquals(plain,SecureCodec.open(sealed,key,"row-a"))
  try { SecureCodec.open(sealed,key,"row-b");fail() } catch(_:javax.crypto.AEADBadTagException) {}
  sealed[sealed.lastIndex]=(sealed.last().toInt() xor 1).toByte()
  try { SecureCodec.open(sealed,key,"row-a");fail() } catch(_:javax.crypto.AEADBadTagException) {}
 }
 @Test fun independentRandomNonces() {
  val key=SecretKeySpec(ByteArray(32),"AES");assertFalse(SecureCodec.seal(byteArrayOf(1),key,"x").contentEquals(SecureCodec.seal(byteArrayOf(1),key,"x")))
 }
 @Test fun portableBackupAuthenticatesPasswordAndRejectsTampering() {
  val records=mapOf("legacy/sessions" to "[]".toByteArray());val pass="correct horse battery".toCharArray()
  val backup=PortableBackup.pack(records,pass);assertArrayEquals(records.values.first(),PortableBackup.unpack(backup,pass)["legacy/sessions"])
  try { PortableBackup.unpack(backup,"wrong password!".toCharArray());fail() } catch(_:javax.crypto.AEADBadTagException) {}
  backup[backup.lastIndex]=(backup.last().toInt() xor 8).toByte()
  try { PortableBackup.unpack(backup,pass);fail() } catch(_:javax.crypto.AEADBadTagException) {}
 }
 @Test fun backupRejectsUnknownRecordsMissingArtifactsAndBadJournal() {
  rejects { PortableBackup.validate(mapOf("../../secret" to byteArrayOf())) }
  val dir=Files.createTempDirectory("backup-fixture").toFile()
  try { val ledger=ChimeraLedger(dir,JSONArray());val data=ledger.snapshot();data.getJSONArray("journal").getJSONObject(0).put("hash","BAD")
   rejects { PortableBackup.validate(mapOf("chimera/workspace-v3.json" to data.toString().toByteArray())) }
  } finally { dir.deleteRecursively() }
 }
 @Test fun tvRtsiAipAndCvmDecodeWithoutInferringApproval() {
  assertTrue(EmvSemantics.decode("95","0000800000".bytes()).contains("Cardholder verification not successful"))
  assertTrue(EmvSemantics.decode("9B","C000".bytes()).contains("Cardholder verification performed"))
  assertTrue(EmvSemantics.decode("82","2000".bytes()).contains("DDA supported"))
  assertTrue(EmvSemantics.decode("9F34","1F0002".bytes()).any { it.contains("No CVM required") })
  assertTrue(EmvSemantics.decode("9F34","1F0002".bytes()).any { it.contains("not cryptographic") })
  assertTrue(EmvSemantics.decode("95",byteArrayOf()).first().startsWith("INVALID"))
 }
 @Test fun cvmListAndKernelScope() {
  assertTrue(EmvSemantics.decode("8E","00000000000000001F00".bytes()).any { it.contains("Rule 0") })
  assertTrue(EmvSemantics.decode("8E",byteArrayOf(0)).first().startsWith("INVALID"))
  assertTrue(EmvSemantics.decode("9F66",ByteArray(4)).single().contains("Kernel-specific"))
 }
 @Test fun contextualContradictionIsReviewFindingOnly() {
  val analysis=EmvAnalyzer.analyze("950500008000009F34031F0002",InputKind.TLV,DataOrigin.PUBLIC_TEST,"synthetic unit fixture")
  assertTrue(analysis.findings.any { it.startsWith("REVIEW_CONTEXT") });assertTrue(analysis.json().getString("authorization")=="NOT_INFERRED")
 }
 private fun trace(mode:String="PHYSICAL_PCSC_LAB")=JSONObject().put("mode",mode).put("exchanges",JSONArray()
  .put(JSONObject().put("command",LabAids.select(LabAids.all[0])).put("response","9000").put("durationNanos",10000000).put("timestamp","2026-01-01T00:00:00Z").put("capturePoint","PCSC_API"))
  .put(JSONObject().put("command",LabProtocol.GET_DATA).put("response","").put("error","timeout secret message omitted").put("durationMs",50).put("capturePoint","PCSC_API")))
 @Test fun pcscImportPreservesFailuresTimingAndProvenance() {
  val a=TraceImport.analyze(trace().toString(),"fixture PCSC").single();val s=a.traceSummary!!
  assertEquals(DataOrigin.IMPORTED_AUTHORIZED,a.origin);assertEquals("NFC_LAB_PCSC_V1",s.getString("format"));assertEquals(1,s.getInt("failureCount"))
  assertEquals(30.0,s.getJSONObject("latency").getDouble("medianMs"),0.001)
  assertEquals(1767225600000L,s.getJSONArray("attempts").getJSONObject(0).getLong("declaredTimestamp"))
  assertFalse(a.json().toString().contains("secret message"))
 }
 @Test fun simulationImportCannotBecomePhysicalAndBatchIsSeparated() {
  val raw=JSONArray().put(trace("SIMULAÇÃO LOCAL")).put(trace()).toString();val reports=TraceImport.analyze(raw,"unit fixture")
  assertEquals(listOf(DataOrigin.SIMULATED,DataOrigin.IMPORTED_AUTHORIZED),reports.map { it.origin })
 }
 @Test fun comparisonIncludesFailureSequenceConfigAndTimingScope() {
  val a=TraceImport.analyze(trace().toString(),"fixture").single().json();val b=JSONObject(a.toString())
  assertTrue(TraceImport.compare(a,b).getBoolean("sequenceEqual"));assertTrue(TraceImport.compare(a,b).getBoolean("sameDeclaredTimingScope"))
  b.getJSONObject("traceSummary").getJSONArray("attempts").getJSONObject(0).put("statusWord","6A82")
  assertFalse(TraceImport.compare(a,b).getBoolean("sequenceEqual"))
 }
 @Test fun typedSdkContractCannotProduceLocalVerification() {
  val json=SdkSummary("official provider","test-1","official-case","trace-ref",CaseVerdict.PASS,AuthenticationVerdict.INVALID).json()
  assertEquals("INVALID",json.getString("authenticationResult"));assertEquals("PASS",json.getString("result"))
  assertTrue(UnconfiguredSdkAdapter().prerequisites(SdkProfile("","","","","")).size>=8)
 }
 @Test fun encryptedRecordBackendAndDeletionRemainAuditable() {
  val map=mutableMapOf<String,ByteArray>();val key=SecretKeySpec(ByteArray(32),"AES")
  val store=object:LedgerRecords {
   override fun read(name:String)=map[name]?.let { SecureCodec.open(it,key,name) }
   override fun write(name:String,value:ByteArray) { map[name]=SecureCodec.seal(value,key,name) }
   override fun delete(name:String) { map.remove(name) }
  }
  val dir=Files.createTempDirectory("ledger-encrypted").toFile()
  try {
   val ledger=ChimeraLedger(dir,JSONArray(),store);val id=ledger.saveAnalysis(EmvAnalyzer.analyze("82022000",InputKind.TLV,DataOrigin.PUBLIC_TEST,"fixture"))
   assertFalse(String(map.getValue("workspace-v3.json")).contains("fixture"));ledger.deleteAnalysis(id)
   val reopened=ChimeraLedger(dir,JSONArray(),store);assertNull(reopened.integrityError);assertEquals(0,reopened.snapshot().getJSONArray("analyses").length())
   assertEquals("DELETE_ANALYSIS",reopened.snapshot().getJSONArray("journal").getJSONObject(2).getString("action"))
  } finally { dir.deleteRecursively() }
 }
 @Test fun streamingEnvelopeAndPortableFixtureForIndependentVerification() {
  val key=SecretKeySpec(ByteArray(32),"AES");val out=java.io.ByteArrayOutputStream()
  SecureCodec.sealingStream(out,key,"stream-fixture").use { it.write(ByteArray(128000) { 42 }) }
  assertArrayEquals(ByteArray(128000) { 42 },SecureCodec.openingStream(out.toByteArray().inputStream(),key,"stream-fixture").use { it.readBytes() })
  val file=java.io.File("build/hardening-cross-language/software-fixture-not-physical.nfcb");file.parentFile.mkdirs()
  file.writeBytes(PortableBackup.pack(mapOf("legacy/sessions" to "[]".toByteArray()),"SOFTWARE-FIXTURE-NOT-A-SECRET".toCharArray()))
 }

 @Test fun artifactDeletionInvalidatesReviewAndExportUsesDecryptedBytes() {
  val dir=Files.createTempDirectory("hardening-evidence").toFile();val map=mutableMapOf<String,ByteArray>();val key=SecretKeySpec(ByteArray(32),"AES")
  val store=object:LedgerRecords {
   override fun read(name:String)=map[name]?.let { SecureCodec.open(it,key,name) }
   override fun write(name:String,value:ByteArray) { map[name]=SecureCodec.seal(value,key,name) }
   override fun delete(name:String) { map.remove(name) }
  }
  try {
   val cases=JSONArray().put(JSONObject().put("id","LAB-UNIT").put("title","Synthetic unit case").put("revision","1").put("kind","LAB"))
   val ledger=ChimeraLedger(dir,cases,store)
   val terminal=JSONObject().put("internalId","UNIT-FIXTURE").put("manufacturer","SYNTHETIC").put("model","NO REAL TERMINAL")
   val run=ledger.createRun("LAB-UNIT",terminal,"operator","synthetic host","unit fixture",Team.RED,"test-version")
   ledger.observe(run,EvidenceStage.NFC_DETECTED,"OBSERVED","SOFTWARE FIXTURE: not a physical experiment",true)
   val artifact=ledger.attach(run,"SYNTHETIC PHYSICAL CONTEXT FIXTURE".byteInputStream(),"fixture.txt",ArtifactRole.PHYSICAL_CONTEXT,true)
   ledger.attach(run,"SYNTHETIC TRACE FIXTURE".byteInputStream(),"trace.txt",ArtifactRole.READER_TRACE,true)
   ledger.review(run,"reviewer","software-only unit reference")
   assertTrue(ledger.artifactIssues(run).isEmpty());val out=java.io.ByteArrayOutputStream();EvidenceBundle.write(ledger,out)
   val entries=mutableMapOf<String,ByteArray>()
   java.util.zip.ZipInputStream(out.toByteArray().inputStream()).use { z -> while(true) { val entry=z.nextEntry ?: break;entries[entry.name]=z.readBytes() } }
   assertEquals("SYNTHETIC PHYSICAL CONTEXT FIXTURE",entries.getValue("artifacts/$artifact.bin").toString(Charsets.UTF_8))
   ledger.deleteArtifact(artifact);assertFalse(map.containsKey("$artifact.bin"))
   assertEquals("UNVERIFIED",ledger.runReport(run).getString("effectiveStage"));assertEquals("",ledger.find("runs",run).getString("reviewer"))
   ChimeraLedger.verifyJournal(ledger.snapshot())
  } finally { dir.deleteRecursively() }
 }
 @Test fun retentionDeletesOnlyExplicitPreviewIds() {
  val dir=Files.createTempDirectory("hardening-retention").toFile()
  try {
   val ledger=ChimeraLedger(dir,JSONArray());val analysis=EmvAnalyzer.analyze("82022000",InputKind.TLV,DataOrigin.PUBLIC_TEST,"unit fixture")
   val first=ledger.saveAnalysis(analysis);val second=ledger.saveAnalysis(analysis)
   val cutoff=System.currentTimeMillis()+1;Thread.sleep(3)
   val preview=JSONObject().put("days",1).put("cutoff",cutoff).put("artifacts",JSONArray()).put("analyses",JSONArray().put(first))
   ledger.applyRetention(preview);assertEquals(second,ledger.snapshot().getJSONArray("analyses").getJSONObject(0).getString("id"))
   assertEquals(1,ledger.snapshot().getJSONArray("analyses").length());ChimeraLedger.verifyJournal(ledger.snapshot())
  } finally { dir.deleteRecursively() }
 }

}
