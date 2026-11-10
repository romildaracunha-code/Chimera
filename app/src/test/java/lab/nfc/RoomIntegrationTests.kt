package lab.nfc
import android.content.Context
import lab.nfc.core.storage.SecureRepository
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.crypto.spec.SecretKeySpec
import java.io.File

/** Real Room/SQLite on Robolectric, with an injected JVM key. Does not validate Android Keystore hardware. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class RoomIntegrationTests {
 private lateinit var context:Context
 private lateinit var repo:SecureRepository
 private val key=SecretKeySpec(ByteArray(32) { 7 },"AES")
 @Before fun setup() { context=RuntimeEnvironment.getApplication();context.deleteDatabase("integration.db");repo=SecureRepository(context,key,"integration.db") }
 @After fun close() { repo.close();context.deleteDatabase("integration.db") }
 @Test fun chunksRoundTripAndReopen() {
  val bytes=ByteArray(256000) { (it%251).toByte() };repo.write("draft/large",bytes);assertArrayEquals(bytes,repo.read("draft/large"))
  repo.close();repo=SecureRepository(context,key,"integration.db");assertArrayEquals(bytes,repo.read("draft/large"))
 }
 @Test fun migratesOnlyAfterVerifiedReadBackAndIsIdempotent() {
  val file=File(context.cacheDir,"legacy.json").apply { writeText("[]") }
  assertEquals("[]",repo.migrate(file,"legacy/sessions")!!.toString(Charsets.UTF_8));assertFalse(file.exists())
  assertEquals("[]",repo.migrate(file,"legacy/sessions")!!.toString(Charsets.UTF_8))
  file.writeText("divergent");try { repo.migrate(file,"legacy/sessions");fail() } catch(_:java.util.concurrent.ExecutionException) {};assertTrue(file.exists());file.delete()
 }
 @Test fun atomicReplacementRollsBackOnInvalidRecordAndKeepsOldData() {
  repo.write("draft/old","old".toByteArray())
  try { repo.replace(mapOf("draft/new" to "new".toByteArray(),"draft/oversize" to ByteArray(21*1024*1024)));fail() } catch(_:java.util.concurrent.ExecutionException) {}
  assertEquals(listOf("draft/old"),repo.keys());assertEquals("old",repo.read("draft/old")!!.toString(Charsets.UTF_8))
 }
 @Test fun encryptionAuthenticatesOnReadAndDeletionRemovesRecord() {
  repo.write("draft/one","private".toByteArray());assertEquals("private",repo.read("draft/one")!!.toString(Charsets.UTF_8));repo.delete("draft/one");assertNull(repo.read("draft/one"))
 }
 @Test fun legacyWorkspaceAndPreferencesMigrateWithRestartRecovery() {
  val session=lab.nfc.core.storage.Session(device="SOFTWARE FIXTURE",technology="none",mode="HARDWARE HCE",profile="LAB-ALPHA")
  File(context.filesDir,"sessions.json").writeText(lab.nfc.core.storage.Export.json(listOf(session)))
  File(context.filesDir,"devices.json").writeText("[]");File(context.filesDir,"physical-experiments.json").writeText("[]")
  context.getSharedPreferences("lab",0).edit().clear().putString("profile","LAB-ALPHA").putString("activeHce",session.id).commit()
  val store=lab.nfc.core.storage.LabStore.forTesting(context,repo)
  assertFalse(File(context.filesDir,"sessions.json").exists());assertTrue(context.getSharedPreferences("lab",0).all.isEmpty())
  assertNull(store.activeHce);assertEquals("PROCESS_RESTART",store.sessions.value.single().error)
  assertEquals(session.id,store.sessions.value.single().id);assertTrue(store.sessions.value.single().result.startsWith("Interrompido"))
 }
 @Test fun restorePointSurvivesAtomicReplacement() {
  repo.write("legacy/sessions","[]".toByteArray());repo.saveRestorePoint()
  repo.replace(mapOf("draft/new" to "new".toByteArray()))
  assertEquals(listOf("draft/new"),repo.keys());repo.replace(repo.restorePoint())
  assertEquals(listOf("legacy/sessions"),repo.keys());assertEquals("[]",repo.read("legacy/sessions")!!.toString(Charsets.UTF_8))
 }
 @Test fun wrongKeyCannotReadExistingRecords() {
  repo.write("draft/sensitive","fixture".toByteArray());repo.close();repo=SecureRepository(context,SecretKeySpec(ByteArray(32) { 8 },"AES"),"integration.db")
  try { repo.read("draft/sensitive");fail() } catch(e:java.util.concurrent.ExecutionException) { assertTrue(e.cause is javax.crypto.AEADBadTagException) }
 }

 @Test fun staleWorkspaceWriterCannotOverwriteRestoredData() {
  val generation=repo.generation;repo.write("draft/old","old".toByteArray(),generation)
  repo.replace(mapOf("draft/current" to "current".toByteArray()))
  try { repo.write("draft/old","stale".toByteArray(),generation);fail() } catch(e:java.util.concurrent.ExecutionException) { assertTrue(e.cause is IllegalStateException) }
  assertEquals(listOf("draft/current"),repo.keys())
 }

}
