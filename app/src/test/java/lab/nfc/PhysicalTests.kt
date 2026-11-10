package lab.nfc

import lab.nfc.core.storage.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PhysicalTests {
 private fun plan() = PhysicalExperiment(terminalId="T001",operator="Lab",phoneReference="PHONE-01",environment=ExperimentEnvironment.LAB_PROTOCOL,environmentReference="Bancada autorizada",credential=CredentialKind.NFC_LAB_FICTITIOUS,emv=ProcessingResult.NOT_APPLICABLE,authorization=AuthorizationResult.NOT_REQUESTED,settlement=SettlementResult.NOT_OBSERVED)
 @Test fun noEvidenceMeansNoObservationClaim() {
  assertTrue(plan().issues().isEmpty())
  assertTrue(plan().copy(nfc=Observation.OBSERVED).issues().isNotEmpty())
  assertTrue(plan().copy(nfc=Observation.OBSERVED,evidenceRefs=listOf("trace.json")).issues().isEmpty())
 }
 @Test fun labCannotClaimFinancialApprovalOrEmv() {
  assertTrue(plan().copy(authorization=AuthorizationResult.APPROVED,evidenceRefs=listOf("x")).issues().isNotEmpty())
  assertTrue(plan().copy(emv=ProcessingResult.RECOGNIZED,evidenceRefs=listOf("x")).issues().isNotEmpty())
 }
 @Test fun officialNegativeOnlyInOfficialEnvironmentWithCase() {
  assertTrue(plan().copy(credential=CredentialKind.OFFICIAL_NEGATIVE).issues().isNotEmpty())
  val e=plan().copy(environment=ExperimentEnvironment.OFFICIAL_HOMOLOGATION,credential=CredentialKind.OFFICIAL_NEGATIVE,emv=ProcessingResult.UNKNOWN)
  assertTrue(e.issues().isNotEmpty())
  assertTrue(e.copy(officialCaseId="PROVIDER-CASE-v1").issues().isEmpty())
 }
 @Test fun unknownIsNotDeclineAndAuthorizationIsNotSettlement() {
  val e=plan().copy(environment=ExperimentEnvironment.OFFICIAL_HOMOLOGATION,credential=CredentialKind.OFFICIAL_POSITIVE,officialCaseId="OFFICIAL-1",emv=ProcessingResult.RECOGNIZED,authorization=AuthorizationResult.APPROVED,evidenceRefs=listOf("terminal.log","host.log"))
  assertTrue(e.issues().isEmpty()); assertEquals(SettlementResult.NOT_OBSERVED,e.settlement)
  assertTrue(e.copy(settlement=SettlementResult.DOCUMENTED).issues().isNotEmpty())
  assertTrue(e.copy(authorization=AuthorizationResult.UNKNOWN,settlement=SettlementResult.DOCUMENTED).issues().isNotEmpty())
  assertTrue(e.copy(rejection=RejectionLocation.PROTOCOL_INCOMPATIBILITY_DOCUMENTED).issues().isNotEmpty())
 }
 @Test fun physicalRecordAndLegacyCatalogRoundTrip() {
  val e=plan().copy(terminalSnapshot=DeviceRecord("FIXTURE","NOT REAL","T001","v1","?","?","UNIT TEST")); assertEquals(e,PhysicalExperiment.from(e.json()))
  assertEquals("OPERATOR_REPORTED_NOT_INDEPENDENTLY_VERIFIED",e.json().getString("attestation"))
  val old=JSONObject("""{"manufacturer":"A","model":"B","internalId":"T001","firmware":"?","nfc":"?","sdk":"?","notes":""}""")
  val d=DeviceRecord.from(old); assertEquals("Não confirmada",d.homologation)
  assertEquals(d,DeviceRecord.from(d.json()))
 }
 @Test fun nanosecondTimingAndCaptureProvenanceSurviveExport() {
  val s=Session(device="PHONE",technology="ISO-DEP",mode="HARDWARE HCE",profile="P",exchanges=listOf(Exchange(1,"HCE","00","6700",0,923,"HCE_RESPONSE_PREPARED_NOT_RF_ACKNOWLEDGED")))
  assertEquals(s,Session.from(s.json())); assertTrue(Export.csv(listOf(s)).contains("923"))
 }
}
