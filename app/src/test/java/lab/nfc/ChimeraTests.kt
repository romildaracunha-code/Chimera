package lab.nfc

import lab.nfc.core.chimera.*
import lab.nfc.core.emv.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

class ChimeraTests {
 private fun seed()=JSONArray().put(JSONObject().put("id","LAB").put("title","Software fixture, not physical evidence").put("kind","LAB").put("revision","1").put("officialReference",""))
 private fun device()=JSONObject().put("internalId","FIXTURE-T001").put("manufacturer","UNIT TEST").put("model","NO HARDWARE").put("firmware","fixture-v1")
 private fun use(block:(ChimeraLedger,File)->Unit) { val dir=Files.createTempDirectory("chimera-unit-").toFile();try { block(ChimeraLedger(dir,seed()),dir) } finally { dir.deleteRecursively() } }
 private fun run(l:ChimeraLedger,team:Team=Team.RED,base:String="",env:String="UNIT-FIXTURE",case:String="LAB")=l.createRun(case,device(),"RED-UNIT-FIXTURE","PHONE-UNIT-FIXTURE",env,team,"UNIT-FIXTURE-NOT-INSTALLED",base)
 private fun file(l:ChimeraLedger,id:String,role:ArtifactRole)=l.attach(id,ByteArrayInputStream("SYNTHETIC UNIT FIXTURE NOT PHYSICAL EVIDENCE".toByteArray()),"../../fixture.txt",role,true)
 private fun observed(l:ChimeraLedger,id:String) { file(l,id,ArtifactRole.PHYSICAL_CONTEXT);file(l,id,ArtifactRole.READER_TRACE);l.observe(id,EvidenceStage.PROTOCOL_PROCESSED,"PASS","Software fixture; no real experiment",true);l.review(id,"BLUE-UNIT-FIXTURE","UNIT TEST LOGIC ONLY") }
 private fun official(l:ChimeraLedger,ref:String="FIXTURE-PROVIDER-CASE") { l.saveCase(JSONObject().put("id","OFFICIAL").put("title","TEST FIXTURE").put("revision","1").put("kind","OFFICIAL_EMV").put("officialReference",ref)) }
 @Test fun plannedRunCannotImplyHardwareOrAuthorization()=use { l,_ -> val id=run(l);val r=l.runReport(id);assertEquals("UNVERIFIED",r.getString("effectiveStage"));assertEquals("NOT_INFERRED",r.getString("settlement"));assertThrows(IllegalArgumentException::class.java) { l.observe(id,EvidenceStage.TEST_AUTHORIZATION_RESULT,"PASS","",true) } }
 @Test fun officialCasesStayBlockedWithoutProviderReference()=use { l,_ -> official(l,"");assertThrows(IllegalArgumentException::class.java) { run(l,case="OFFICIAL") };official(l);assertTrue(run(l,case="OFFICIAL").isNotBlank()) }
 @Test fun simulatedOrImportedAnalysisNeverBecomesObservedHardware()=use { l,_ ->
  val a=EmvAnalyzer.analyze("5A0401020304",InputKind.TLV,DataOrigin.IMPORTED_AUTHORIZED,"UNIT FIXTURE")
  l.saveAnalysis(a);assertEquals(0,l.snapshot().getJSONArray("runs").length());assertFalse(l.export().toString().contains("01020304"))
  assertThrows(IllegalArgumentException::class.java) { l.saveAnalysis(a.copy(origin=DataOrigin.REAL_HARDWARE)) }
 }
 @Test fun snapshotsSurviveCaseAndInventoryChanges()=use { l,dir -> val d=device();val id=l.createRun("LAB",d,"OPERATOR","PHONE","FIXTURE",Team.RED,"VERSION")
  d.put("firmware","changed");val c=l.find("cases","LAB");c.put("revision","2");l.saveCase(c)
  val loaded=ChimeraLedger(dir,seed());assertNull(loaded.integrityError);assertEquals("fixture-v1",loaded.find("runs",id).getJSONObject("terminalSnapshot").getString("firmware"));assertEquals("1",loaded.find("runs",id).getJSONObject("caseSnapshot").getString("revision"))
 }
 @Test fun attachmentsAreConfinedAndTamperingInvalidatesReview()=use { l,dir -> val id=run(l);observed(l,id)
  assertEquals("PROTOCOL_PROCESSED",l.runReport(id).getString("effectiveStage"))
  val a=l.snapshot().getJSONArray("artifacts").getJSONObject(0);assertEquals(dir.canonicalFile,l.artifactFile(a.getString("id")).canonicalFile.parentFile)
  l.artifactFile(a.getString("id")).writeText("CHANGED UNIT FIXTURE")
  assertEquals("UNVERIFIED",l.runReport(id).getString("effectiveStage"));assertThrows(IllegalArgumentException::class.java) { l.review(id,"BLUE-UNIT-FIXTURE","REVIEW") }
  assertThrows(IllegalArgumentException::class.java) { EvidenceBundle.write(l,File(dir,"bad.zip")) }
 }
 @Test fun permissionEmptyAndOversizeAttachmentsDoNotLeaveRecords()=use { l,_ -> val id=run(l)
  assertThrows(IllegalArgumentException::class.java) { l.attach(id,ByteArrayInputStream(byteArrayOf(1)),"x",ArtifactRole.OTHER,false) }
  assertThrows(IllegalArgumentException::class.java) { l.attach(id,ByteArrayInputStream(byteArrayOf()),"x",ArtifactRole.OTHER,true) }
  assertThrows(IllegalArgumentException::class.java) { l.attach(id,ByteArrayInputStream(ByteArray(8*1024*1024+1)),"x",ArtifactRole.OTHER,true) }
  assertEquals(0,l.snapshot().getJSONArray("artifacts").length())
 }
 @Test fun sdkDeclarationsAreBoundAndContradictionsBlockReview()=use { l,_ -> official(l);val id=run(l,case="OFFICIAL")
  val sdk=JSONObject().put("provider","TEST FIXTURE").put("caseId","FIXTURE-PROVIDER-CASE").put("environment","OFFICIAL_HOMOLOGATION").put("result","PASS").put("version","UNIT-1").put("traceReference","UNIT TRACE").put("authenticationResult","INVALID")
  assertThrows(IllegalArgumentException::class.java) { l.importSdk(id,JSONObject(sdk.toString()).put("secretKey","NOT A REAL KEY")) }
  assertThrows(IllegalArgumentException::class.java) { l.importSdk(id,JSONObject(sdk.toString()).put("provider",JSONObject().put("hidden","x"))) }
  assertThrows(IllegalArgumentException::class.java) { l.importSdk(id,JSONObject(sdk.toString()).put("caseId","WRONG CASE")) }
  l.importSdk(id,sdk);file(l,id,ArtifactRole.PHYSICAL_CONTEXT);file(l,id,ArtifactRole.TERMINAL_TRACE);file(l,id,ArtifactRole.SDK_RESULT)
  l.observe(id,EvidenceStage.AUTHENTICATION_RESULT,"FAIL","UNIT FIXTURE",true);assertTrue(l.stageIssues(id).any { it.contains("Divergência") })
  l.observe(id,EvidenceStage.AUTHENTICATION_RESULT,"PASS","UNIT FIXTURE",true);l.review(id,"BLUE-UNIT-FIXTURE","FIXTURE REVIEW")
  assertEquals("NOT_VERIFIED_BY_NFC_LAB",l.runReport(id).getString("cryptography"));assertEquals("INVALID",l.runReport(id).getString("authenticationDeclaredBySdk"))
 }
 @Test fun processorAndTerminalDivergenceIsSeparateFromTestPass()=use { l,_ -> official(l);val id=run(l,case="OFFICIAL")
  l.decisions(id,"APPROVED","DECLINED","UNIT-FIXTURE-CORRELATION")
  file(l,id,ArtifactRole.PHYSICAL_CONTEXT);file(l,id,ArtifactRole.TERMINAL_TRACE);file(l,id,ArtifactRole.HOST_TRACE)
  l.observe(id,EvidenceStage.TEST_AUTHORIZATION_RESULT,"PASS","Negative case expected decline; software test fixture",true)
  l.review(id,"BLUE-UNIT-FIXTURE","FIXTURE REVIEW");val r=l.runReport(id)
  assertTrue(r.getBoolean("terminalProcessorDivergence"));assertEquals("DECLINED",r.getString("processorResult"));assertEquals("NOT_INFERRED",r.getString("settlement"))
 }
 @Test fun newObservationOrAttachmentInvalidatesPriorReview()=use { l,_ -> val id=run(l);observed(l,id);file(l,id,ArtifactRole.OTHER);assertEquals("UNVERIFIED",l.runReport(id).getString("effectiveStage"));l.observe(id,EvidenceStage.NFC_DETECTED,"OBSERVED","Changed observation",true);assertEquals("",l.find("runs",id).getString("reviewer")) }
 @Test fun redBlueComparisonDoesNotProveMitigationAndRequiresComparableContext()=use { l,_ -> val a=run(l);observed(l,a);val b=run(l,Team.BLUE,a);observed(l,b)
  val comparison=l.comparison(a,b);assertTrue(comparison.getBoolean("comparableContext"));assertFalse(comparison.getBoolean("automaticFixClaim"));assertTrue(comparison.has("changes"))
  val c=run(l,Team.BLUE,a,"OTHER-ENVIRONMENT");observed(l,c);assertFalse(l.comparison(a,c).getBoolean("comparableContext"))
 }
 @Test fun journalStateTamperingBecomesReadOnlyAndPreservesOriginal()=use { l,dir -> run(l);val f=File(dir,"workspace-v3.json");val broken=JSONObject(f.readText());broken.getJSONArray("runs").getJSONObject(0).put("outcome","PASS");f.writeText(broken.toString());val original=f.readText()
  val reloaded=ChimeraLedger(dir,seed());assertNotNull(reloaded.integrityError);assertThrows(IllegalStateException::class.java) { reloaded.export() };assertThrows(IllegalStateException::class.java) { run(reloaded) };assertEquals(original,f.readText())
 }
 @Test fun canonicalJsonIsPortableAndEvidenceZipContainsVerifiableBytes()=use { l,dir ->
  assertEquals("{\"\\u00e9\":\"\\ud83d\\udd2c\\n\"}",canonical(JSONObject().put("é","🔬\n")))
  val id=run(l);observed(l,id);val archive=EvidenceBundle.write(l,File(dir,"bundle.zip"))
  val portable=File("build/chimera-cross-language/software-fixture-not-physical.zip");portable.parentFile.mkdirs();EvidenceBundle.write(l,portable)
  ZipFile(archive).use { z -> val manifest=JSONObject(z.getInputStream(z.getEntry("manifest.json")).bufferedReader().readText());ChimeraLedger.verifyJournal(manifest)
   val art=manifest.getJSONArray("artifacts").getJSONObject(0);assertEquals(art.getString("sha256"),digest(z.getInputStream(z.getEntry("artifacts/${art.getString("id")}.bin")).readBytes()))
  }
 }
}
