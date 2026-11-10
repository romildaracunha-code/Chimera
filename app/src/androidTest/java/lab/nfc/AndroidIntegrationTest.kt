package lab.nfc
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import lab.nfc.core.nfc.Capabilities
import lab.nfc.core.storage.*
import lab.nfc.core.apdu.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONArray

@RunWith(AndroidJUnit4::class)
class AndroidIntegrationTest {
 @Test fun viewModelSimulationPersistsAndNeverMarksHardware() {
  var model: LabViewModel? = null
  ActivityScenario.launch(MainActivity::class.java).use { scenario ->
   scenario.onActivity { activity -> model = androidx.lifecycle.ViewModelProvider(activity)[LabViewModel::class.java] }
   val vm = requireNotNull(model)
   kotlinx.coroutines.runBlocking { vm.simulate().join() }
   val session = vm.store.sessions.value.first { it.id == vm.current.value }
   assertEquals("SIMULAÇÃO LOCAL", session.mode)
   assertEquals(listOf("9000", "9000"), session.exchanges.map { it.status })
   assertEquals("Concluído", session.result)
   scenario.recreate()
   scenario.onActivity { activity -> assertTrue(LabStore.get(activity).sessions.value.any { it.id == session.id }) }
  }
 }
 @Test fun hceServiceRegisteredWithLabAidAndBindingPermission() {
  val c = ApplicationProvider.getApplicationContext<Context>()
  val info = c.packageManager.getServiceInfo(android.content.ComponentName(c, lab.nfc.core.hce.LabHceService::class.java), android.content.pm.PackageManager.GET_META_DATA)
  assertEquals("android.permission.BIND_NFC_SERVICE", info.permission)
  assertTrue(info.exported)
  assertTrue(info.metaData.getInt("android.nfc.cardemulation.host_apdu_service") != 0)
 }

 @Test fun launchAndDetectActualCapabilities() {
 ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity { activity ->
 val caps=Capabilities.detect(activity)
 assertEquals(activity.packageManager.hasSystemFeature("android.hardware.nfc.hce") && caps.nfc,caps.hce)
 assertFalse(caps.enabled && !caps.nfc)
 } }
 }
 @Test fun persistedSessionSurvivesDiskRoundTripAndExports() {
 val c=ApplicationProvider.getApplicationContext<Context>(); val store=LabStore.get(c)
 val e=LabEngine(); val s=Session(device="Android instrumentation",technology="Logical only",mode="SIMULAÇÃO LOCAL",profile="LAB-TEST")
 store.save(s); store.append(s.id,Exchange(1,"SIMULATED",LabProtocol.SELECT,e.exchange(LabProtocol.SELECT.bytes(),s.profile).hex(),0))
 val disk=java.io.File(c.filesDir,"sessions.json").readText(); val a=JSONArray(disk)
 val found=(0 until a.length()).map { Session.from(a.getJSONObject(it)) }.first { it.id==s.id }
 assertEquals("9000",found.exchanges.single().status)
 assertTrue(Export.csv(listOf(found)).contains("SIMULATED"))
 assertEquals(found,Session.from(JSONArray(Export.json(listOf(found))).getJSONObject(0)))
 }
}

@RunWith(AndroidJUnit4::class)
class PhysicalStorageIntegrationTest {
 @Test fun plannedRecordPersistsAndSimulationCannotBePhysicalEvidence() {
  val c=ApplicationProvider.getApplicationContext<Context>(); val store=LabStore.get(c)
  val id="UNIT-FIXTURE-NOT-A-REAL-TERMINAL"
  store.addDevice(DeviceRecord("TEST FIXTURE","NO HARDWARE",id,"unknown","unknown","none","Automated storage fixture, not physical evidence"))
  val simulated=Session(device="TEST FIXTURE",technology="none",mode="SIMULAÇÃO LOCAL",profile="P",exchanges=listOf(Exchange(1,"SIMULATED","00","6700",0)))
  store.save(simulated)
  val plan=PhysicalExperiment(terminalId=id,operator="AUTOMATED TEST",phoneReference="TEST FIXTURE",environment=ExperimentEnvironment.LAB_PROTOCOL,environmentReference="AUTOMATED STORAGE ONLY",credential=CredentialKind.NFC_LAB_FICTITIOUS,emv=ProcessingResult.NOT_APPLICABLE,authorization=AuthorizationResult.NOT_REQUESTED,notes="No physical experiment executed")
  assertThrows(IllegalArgumentException::class.java) { store.saveExperiment(plan.copy(labSessionId=simulated.id)) }
  store.saveExperiment(plan)
  val disk=JSONArray(java.io.File(c.filesDir,"physical-experiments.json").readText())
  val saved=(0 until disk.length()).map { PhysicalExperiment.from(disk.getJSONObject(it)) }.first { it.id==plan.id }
  assertEquals(plan.copy(terminalSnapshot=store.devices.value.first { it.internalId==id }),saved); assertEquals(Observation.UNKNOWN,saved.nfc)
 }
}
