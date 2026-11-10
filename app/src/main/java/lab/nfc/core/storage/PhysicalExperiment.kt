package lab.nfc.core.storage

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ExperimentEnvironment { LAB_PROTOCOL, OFFICIAL_HOMOLOGATION, PRODUCTION_OBSERVATION }
enum class CredentialKind { NFC_LAB_FICTITIOUS, OFFICIAL_POSITIVE, OFFICIAL_NEGATIVE, LEGITIMATE }
enum class Observation { UNKNOWN, OBSERVED, NOT_OBSERVED, NOT_APPLICABLE }
enum class ProcessingResult { UNKNOWN, RECOGNIZED, REJECTED, NOT_APPLICABLE }
enum class AuthorizationResult { UNKNOWN, APPROVED, DECLINED, NOT_REQUESTED }
enum class SettlementResult { UNKNOWN, NOT_OBSERVED, DOCUMENTED }
enum class RejectionLocation { UNKNOWN, TERMINAL_DOCUMENTED, PROCESSOR_DOCUMENTED, PROTOCOL_INCOMPATIBILITY_DOCUMENTED, NOT_APPLICABLE }

/** Operator observations, never an automatic claim of physical verification. No payment credential data. */
data class PhysicalExperiment(
 val id: String = UUID.randomUUID().toString(),
 val createdAt: Long = System.currentTimeMillis(),
 val terminalId: String,
 val operator: String,
 val phoneReference: String,
 val environment: ExperimentEnvironment,
 val environmentReference: String,
 val credential: CredentialKind,
 val officialCaseId: String = "",
 val nfc: Observation = Observation.UNKNOWN,
 val emv: ProcessingResult = ProcessingResult.UNKNOWN,
 val authorization: AuthorizationResult = AuthorizationResult.UNKNOWN,
 val settlement: SettlementResult = SettlementResult.UNKNOWN,
 val rejection: RejectionLocation = RejectionLocation.UNKNOWN,
 val labSessionId: String = "",
 val evidenceRefs: List<String> = emptyList(),
 val notes: String = "",
 val terminalSnapshot: DeviceRecord? = null
) {
 fun issues(): List<String> = buildList {
  if (terminalId.isBlank()) add("Identificação interna do terminal obrigatória")
  if (operator.isBlank() || phoneReference.isBlank()) add("Informe operador e referência do celular")
  if (environmentReference.isBlank()) add("Informe ambiente e referência de acesso/execução")
  if (credential in listOf(CredentialKind.OFFICIAL_POSITIVE, CredentialKind.OFFICIAL_NEGATIVE)) {
   if (environment != ExperimentEnvironment.OFFICIAL_HOMOLOGATION) add("Casos oficiais restritos à homologação")
   if (officialCaseId.isBlank()) add("Informe ID/versão do caso oficial")
  }
  if (environment == ExperimentEnvironment.OFFICIAL_HOMOLOGATION && credential !in listOf(CredentialKind.OFFICIAL_POSITIVE, CredentialKind.OFFICIAL_NEGATIVE)) add("Homologação exige credencial oficial de teste")
  if (environment == ExperimentEnvironment.LAB_PROTOCOL && credential != CredentialKind.NFC_LAB_FICTITIOUS) add("Protocolo NFC Lab exige perfil fictício NFC Lab")
  if (credential == CredentialKind.NFC_LAB_FICTITIOUS && environment != ExperimentEnvironment.LAB_PROTOCOL) add("NFC Lab não é credencial EMV")
  if (environment == ExperimentEnvironment.LAB_PROTOCOL && (emv != ProcessingResult.NOT_APPLICABLE || authorization != AuthorizationResult.NOT_REQUESTED || settlement != SettlementResult.NOT_OBSERVED)) add("Laboratório não produz resultado EMV, autorização ou liquidação")
  if (settlement == SettlementResult.DOCUMENTED && environment != ExperimentEnvironment.PRODUCTION_OBSERVATION) add("Homologação não comprova liquidação financeira real")
  if (settlement == SettlementResult.DOCUMENTED && authorization != AuthorizationResult.APPROVED) add("Liquidação documentada exige autorização registrada e evidência independente")
  val claimed = nfc in listOf(Observation.OBSERVED, Observation.NOT_OBSERVED) || emv in listOf(ProcessingResult.RECOGNIZED, ProcessingResult.REJECTED) || authorization in listOf(AuthorizationResult.APPROVED, AuthorizationResult.DECLINED) || settlement == SettlementResult.DOCUMENTED || rejection in listOf(RejectionLocation.TERMINAL_DOCUMENTED, RejectionLocation.PROCESSOR_DOCUMENTED, RejectionLocation.PROTOCOL_INCOMPATIBILITY_DOCUMENTED)
  if (claimed && evidenceRefs.none { it.isNotBlank() }) add("Observação conclusiva exige referência de evidência")
  if (rejection == RejectionLocation.PROTOCOL_INCOMPATIBILITY_DOCUMENTED && authorization in listOf(AuthorizationResult.APPROVED, AuthorizationResult.DECLINED)) add("Incompatibilidade de protocolo não determina autorização")
 }
 fun json(): JSONObject = JSONObject().put("schemaVersion",1).put("id",id).put("createdAt",createdAt)
  .put("terminalId",terminalId).put("operator",operator).put("phoneReference",phoneReference)
  .put("environment",environment.name).put("environmentReference",environmentReference)
  .put("credential",credential.name).put("officialCaseId",officialCaseId).put("nfc",nfc.name)
  .put("emv",emv.name).put("authorization",authorization.name).put("settlement",settlement.name)
  .put("rejection",rejection.name).put("labSessionId",labSessionId).put("evidenceRefs",JSONArray(evidenceRefs))
  .put("notes",notes).put("terminalSnapshot",terminalSnapshot?.json() ?: JSONObject.NULL).put("attestation","OPERATOR_REPORTED_NOT_INDEPENDENTLY_VERIFIED")
 companion object {
  fun from(j: JSONObject): PhysicalExperiment = PhysicalExperiment(
   j.getString("id"),j.getLong("createdAt"),j.getString("terminalId"),j.getString("operator"),j.getString("phoneReference"),
   ExperimentEnvironment.valueOf(j.getString("environment")),j.getString("environmentReference"),CredentialKind.valueOf(j.getString("credential")),
   j.optString("officialCaseId"),Observation.valueOf(j.getString("nfc")),ProcessingResult.valueOf(j.getString("emv")),
   AuthorizationResult.valueOf(j.getString("authorization")),SettlementResult.valueOf(j.getString("settlement")),
   RejectionLocation.valueOf(j.getString("rejection")),j.optString("labSessionId"),j.getJSONArray("evidenceRefs").let { a -> (0 until a.length()).map { a.getString(it) } },j.optString("notes"),j.optJSONObject("terminalSnapshot")?.let { DeviceRecord.from(it) })
 }
}

object PhysicalExport {
 fun json(experiments: List<PhysicalExperiment>,devices: List<DeviceRecord>): String = JSONObject()
  .put("schemaVersion",1).put("attestation","OPERATOR_REPORTED_NOT_INDEPENDENTLY_VERIFIED")
  .put("terminals",JSONArray(devices.map { it.json() })).put("experiments",JSONArray(experiments.map { it.json() })).toString(2)
}
