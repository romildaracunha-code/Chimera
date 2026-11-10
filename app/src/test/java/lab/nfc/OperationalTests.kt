package lab.nfc
import android.content.Context
import lab.nfc.core.storage.*
import lab.nfc.core.emv.digest
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.crypto.spec.SecretKeySpec
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class OperationalTests {
 private lateinit var context:Context
 private lateinit var repo:SecureRepository
 private val key=SecretKeySpec(ByteArray(32) { 31 },"AES")
 @Before fun setup() {
  context=RuntimeEnvironment.getApplication();context.deleteDatabase("operational.db")
  File(context.filesDir,"active-vault.json").delete();File(context.filesDir,"active-vault.json.bak").delete()
  repo=SecureRepository(context,key,"operational.db")
 }
 @After fun teardown() { repo.close();context.deleteDatabase("operational.db");File(context.filesDir,"active-vault.json").delete() }
 private fun session(i:Int)=Session(id="SESSION-$i",date=10000L+i,device="SOFTWARE FIXTURE",technology="none",mode="SIMULAÇÃO LOCAL",profile="LAB-TEST",result="Concluído")
 @Test fun migratesWholeLegacySnapshotIntoBoundedPagesAndIndexes() {
  val old=Export.json((0 until 123).map { session(it) }).toByteArray();repo.write("legacy/sessions",old)
  val history=SessionRepository(repo);history.migrateLegacy(old)
  assertNull(repo.read("legacy/sessions"));assertEquals(123,history.count())
  assertEquals(50,history.page().size);assertEquals("SESSION-122",history.page().first().id)
  assertEquals("SESSION-72",history.page(50).first().id);assertEquals(23,history.page(100).size)
  assertEquals(session(3),history.get("SESSION-3"));assertEquals(123,history.all().count())
  history.migrateLegacy(null);assertEquals(123,history.count())
 }
 @Test fun migrationRejectsDuplicateIdsWithoutDestroyingLegacy() {
  val bytes=Export.json(listOf(session(0),session(0))).toByteArray();repo.write("legacy/sessions",bytes)
  try { SessionRepository(repo).migrateLegacy(bytes);fail() } catch(_:IllegalArgumentException) {}
  assertArrayEquals(bytes,repo.read("legacy/sessions"));assertEquals(0,repo.countKeys("session/"))
 }
 @Test fun migrationRejectsDivergentRecordAndKeepsLegacy() {
  val history=SessionRepository(repo);history.save(session(0).copy(notes="newer data"))
  val bytes=Export.json(listOf(session(0))).toByteArray();repo.write("legacy/sessions",bytes)
  try { history.migrateLegacy(bytes);fail() } catch(_:IllegalArgumentException) {}
  assertArrayEquals(bytes,repo.read("legacy/sessions"));assertEquals("newer data",history.get("SESSION-0")!!.notes)
 }
 @Test fun appendingOneSessionDoesNotRewriteOtherSessions() {
  val history=SessionRepository(repo);history.save(session(0));history.save(session(1))
  val unaffected=repo.read(SessionRepository.recordKey(session(1)))!!
  history.save(session(0).copy(notes="new observation"))
  assertArrayEquals(unaffected,repo.read(SessionRepository.recordKey(session(1))))
  assertEquals(2,history.count());assertEquals("new observation",history.get("SESSION-0")!!.notes)
 }
 @Test fun dateChangeMovesIndexWithoutLeavingDuplicateRecord() {
  val history=SessionRepository(repo);val original=session(1);history.save(original);history.save(original.copy(date=900000))
  assertEquals(1,history.count());assertNull(repo.read(SessionRepository.recordKey(original)));assertEquals(900000,history.get(original.id)!!.date)
 }
 @Test fun indexedBackupValidatesBindingsAndRejectsMissingIndex() {
  SessionRepository(repo).save(session(0));PortableBackup.validate(repo.snapshot())
  val records=repo.snapshot().toMutableMap();records.remove(SessionRepository.indexKey("SESSION-0"))
  try { PortableBackup.validate(records);fail() } catch(_:IllegalArgumentException) {}
 }
 @Test fun streamExportsAllPagesAndCsvHeaderAppearsOnce() {
  val history=SessionRepository(repo);history.migrateLegacy(Export.json((0 until 151).map { session(it) }).toByteArray())
  val json=java.io.ByteArrayOutputStream();repo.consistentRead { Export.writeJson(json,history.all()) }
  val rows=JSONArray(json.toString("UTF-8"));assertEquals(151,rows.length());assertEquals("SESSION-150",rows.getJSONObject(0).getString("id"))
  val csv=java.io.ByteArrayOutputStream();repo.consistentRead { Export.writeCsv(csv,history.all()) }
  assertEquals(152,csv.toString("UTF-8").trimEnd().lines().size);assertEquals(1,Regex("session,date,device").findAll(csv.toString("UTF-8")).count())
 }
 @Test fun recoverySwitchesOnlyAfterVerifiedSeparateVaultAndPreservesOriginal() {
  repo.write("draft/original","UNREADABLE ORIGINAL FIXTURE".toByteArray())
  val original=context.getDatabasePath("operational.db");assertTrue(original.exists())
  val records=mapOf("legacy/sessions" to "[]".toByteArray())
  val recovered=SecureRepository.recover(context,records) { name,alias -> SecureRepository(context,key,name,alias,true) }
  try {
   assertTrue(original.exists());assertEquals("UNREADABLE ORIGINAL FIXTURE",repo.read("draft/original")!!.toString(Charsets.UTF_8))
   val pointer=JSONObject(File(context.filesDir,"active-vault.json").readText());assertTrue(pointer.getString("database").startsWith("nfclab-recovered-"))
   assertEquals("[]",recovered.read("legacy/sessions")!!.toString(Charsets.UTF_8))
   val reopened=SecureRepository.configured(context,key);try { assertEquals("[]",reopened.read("legacy/sessions")!!.toString(Charsets.UTF_8)) } finally { reopened.close() }
  } finally { recovered.close() }
 }
 @Test fun invalidRecoveryNeverChangesActivePointerOrOriginalRecords() {
  repo.write("draft/original","original".toByteArray())
  try { SecureRepository.recover(context,mapOf("../escape" to byteArrayOf())) { _,_ -> error("Should not create vault") };fail() } catch(_:IllegalArgumentException) {}
  assertFalse(File(context.filesDir,"active-vault.json").exists());assertEquals("original",repo.read("draft/original")!!.toString(Charsets.UTF_8))
 }
 @Test fun malformedPointerFailsClosed() {
  File(context.filesDir,"active-vault.json").writeText(JSONObject().put("database","../../escape.db").put("keyAlias","bad").toString())
  try { SecureRepository.configured(context,key);fail() } catch(_:IllegalArgumentException) {}
  assertTrue(File(context.filesDir,"active-vault.json").exists())
 }
 @Test fun thousandSessionsLoadOnePageAndExportAllWithHostMetrics() {
  val history=SessionRepository(repo)
  val start=System.nanoTime();history.migrateLegacy(Export.json((0 until 1000).map { session(it) }).toByteArray());val migration=System.nanoTime()-start
  val pageStart=System.nanoTime();val page=history.page(950);val pageDuration=System.nanoTime()-pageStart
  assertEquals(50,page.size);assertEquals("SESSION-49",page.first().id);assertEquals(1000,history.count())
  val exported=java.io.ByteArrayOutputStream();repo.consistentRead { Export.writeJson(exported,history.all()) };assertEquals(1000,JSONArray(exported.toString("UTF-8")).length())
  val report=File("build/operational-metrics/thousand-sessions-host.json");report.parentFile.mkdirs()
  report.writeText(JSONObject().put("origin","ROBOLECTRIC_SQLITE_HOST_SYNTHETIC").put("sessions",1000).put("pageSize",page.size).put("migrationMs",migration/1e6).put("pageReadMs",pageDuration/1e6).put("physicalHardwareValidated",false).toString(2))
 }
 @Test fun retentionSchedulingIsOptInPersistedIdleChargingAndCancellable() {
  assertFalse(RetentionScheduler.policy(repo).getBoolean("enabled"))
  RetentionScheduler.configure(context,repo,true,60)
  val scheduler=context.getSystemService(android.app.job.JobScheduler::class.java)
  val job=scheduler.getPendingJob(RetentionScheduler.JOB_ID)!!
  assertTrue(job.isPersisted);assertTrue(job.isRequireCharging);assertTrue(job.isRequireDeviceIdle)
  assertEquals(60,RetentionScheduler.policy(repo).getInt("days"))
  RetentionScheduler.configure(context,repo,false,60);assertNull(scheduler.getPendingJob(RetentionScheduler.JOB_ID))
 }

 @Test fun recoveryWriteFailureLeavesExistingActivePointerUntouched() {
  val before=File(context.filesDir,"active-vault.json");val first=SecureRepository.recover(context,mapOf("draft/a" to "old".toByteArray())) { name,alias -> SecureRepository(context,key,name,alias,true) }
  try {
   val pointer=before.readBytes()
   try {
    SecureRepository.recover(context,mapOf("draft/b" to "new".toByteArray())) { name,alias -> SecureRepository(context,key,name,alias,true).also { it.close() } };fail()
   } catch(_:java.util.concurrent.RejectedExecutionException) {}
   assertArrayEquals(pointer,before.readBytes());assertEquals("old",first.read("draft/a")!!.toString(Charsets.UTF_8))
  } finally { first.close() }
 }
 @Test fun cancelledRetentionDoesNotDeleteAnySelectedRecord() {
  val dir=File(context.cacheDir,"cancelled-retention").apply { mkdirs() }
  val ledger=lab.nfc.core.chimera.ChimeraLedger(dir,JSONArray())
  val id=ledger.saveAnalysis(lab.nfc.core.emv.EmvAnalyzer.analyze("82022000",lab.nfc.core.emv.InputKind.TLV,lab.nfc.core.emv.DataOrigin.PUBLIC_TEST,"unit fixture"))
  val preview=JSONObject().put("days",1).put("cutoff",System.currentTimeMillis()).put("artifacts",JSONArray()).put("analyses",JSONArray().put(id))
  val result=ledger.applyRetention(preview) { false };assertTrue(result.getBoolean("interrupted"));assertEquals(0,result.getInt("removedAnalyses"))
  assertEquals(id,ledger.find("analyses",id).getString("id"));dir.deleteRecursively()
 }
 @Test fun writesIndexedBackupForIndependentVerifier() {
  SessionRepository(repo).save(session(1));SessionRepository(repo).save(session(2))
  val file=File("build/operational-metrics/indexed-sessions-software-fixture.nfcb");file.parentFile.mkdirs()
  file.writeBytes(PortableBackup.pack(repo.snapshot(),"SOFTWARE-FIXTURE-NOT-A-SECRET".toCharArray()))
 }

 @Test fun csvEscapesSpreadsheetFormulaWhileJsonPreservesOriginalNotes() {
  val row=session(1).copy(notes=" =1+1")
  assertTrue(Export.csv(listOf(row)).contains("' =1+1"));assertEquals(" =1+1",JSONArray(Export.json(listOf(row))).getJSONObject(0).getString("notes"))
 }

}
