package lab.nfc
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import lab.nfc.feature.chimera.*
import lab.nfc.core.storage.*
import lab.nfc.core.chimera.ChimeraLedger
import org.json.*
import java.io.File
import javax.crypto.spec.SecretKeySpec
import org.robolectric.RuntimeEnvironment
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import lab.nfc.feature.chimera.RecoveryScreen
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual Compose semantics/input on Robolectric; no hardware NFC or Keystore claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeOperationalTests {
 @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
 @Test fun recoveryScreenRequiresPasswordAndDoesNotSilentlyReset() {
  compose.setContent { RecoveryScreen("Synthetic unavailable-key fixture") { error("Recovery must not happen without backup") } }
  compose.onNodeWithText("NFC Lab • recuperação de armazenamento").assertExists()
  compose.onNodeWithText("Abrir e validar backup").assertIsNotEnabled()
  compose.onNodeWithText("Senha do backup portátil").performTextInput("synthetic-password")
  compose.onNodeWithText("Abrir e validar backup").assertIsEnabled()
  compose.onNodeWithText("Recuperar em banco separado").assertDoesNotExist()
  screenshot("recovery-software-host.png")
 }
 private fun screenshot(name:String) {
  val file=File("build/operational-ui/$name");file.parentFile.mkdirs()
  compose.waitForIdle()
  compose.runOnUiThread {
   val view=compose.activity.window.decorView
   val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
   view.draw(android.graphics.Canvas(bitmap))
   file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
  }
 }
 @Test fun guidedWorkflowCreatesUnverifiedPlanAndDraftSurvivesViewModelReconstruction() {
  val app=RuntimeEnvironment.getApplication();app.deleteDatabase("compose-guided.db")
  val repo=SecureRepository(app,SecretKeySpec(ByteArray(32) { 11 },"AES"),"compose-guided.db")
  val store=LabStore.forTesting(app,repo)
  store.addDevice(DeviceRecord("SOFTWARE","FIXTURE","UNIT-TERM","unknown","unknown","none","No physical terminal"))
  val dir=File(app.cacheDir,"compose-ledger").apply { deleteRecursively();mkdirs() }
  val seed=JSONArray().put(JSONObject().put("id","LAB-UNIT").put("title","Unit fixture case").put("revision","1").put("kind","LAB"))
  val ledger=ChimeraLedger(dir,seed);val vm=ChimeraViewModel(app,repo,ledger);val legacy=LabViewModel(app,store)
  val scope=androidx.lifecycle.ViewModelStore().apply { put("chimera",vm);put("legacy",legacy) }
  try {
   compose.setContent { lab.nfc.feature.shared.NfcLabTheme { Column(Modifier.verticalScroll(rememberScrollState())) { GuidedWorkflow(vm,legacy,{ _,_,_ -> error("No artifact was selected") },{ _,_,_ -> error("No export selected") }) } } }
   compose.onNodeWithText("UNIT-TERM",substring=true).performScrollTo().performClick()
   compose.onNodeWithText("Operador").performScrollTo().performTextInput("Unit operator")
   compose.onNodeWithText("Celular / versão Android / referência do diagnóstico").performScrollTo().performTextInput("Host fixture")
   compose.onNodeWithText("Bancada ou homologação / versão / referência").performScrollTo().performTextInput("Synthetic lab")
   compose.onNodeWithText("Próxima etapa").performScrollTo().performClick()
   compose.onNodeWithText("Unit fixture case",substring=true).performScrollTo().performClick()
   compose.onNodeWithText("Criar plano Red com snapshots").performScrollTo().performClick()
   compose.waitUntil(10000) { vm.state.value.getJSONArray("runs").length()==1 && !vm.busy.value }
   val run=vm.state.value.getJSONArray("runs").getJSONObject(0)
   Assert.assertFalse(run.getBoolean("physicalAttested"));Assert.assertEquals("UNVERIFIED",run.getString("claimedStage"))
   vm.flushDraft();val restored=ChimeraViewModel(app,repo,ledger)
   Assert.assertEquals(run.getString("id"),restored.selectedRun);Assert.assertEquals("2",restored.draftValue("step"))
   screenshot("guided-plan-software-host.png")
  } finally { scope.clear();repo.close();dir.deleteRecursively() }
 }
 @Test fun diagnosticsShowsOsReportsAndNeverPhysicalValidation() {
  var export:String?=null
  compose.setContent { lab.nfc.feature.shared.NfcLabTheme { Column(Modifier.verticalScroll(rememberScrollState())) { DiagnosticsScreen { _,_,body -> export=body } } } }
  compose.onNodeWithText("Exportar diagnóstico do aparelho").performScrollTo().performClick()
  val report=JSONObject(export!!);Assert.assertEquals("DEVICE_OS_REPORTED",report.getString("origin"));Assert.assertFalse(report.getBoolean("physicalExchangeValidated"))
 }

}
