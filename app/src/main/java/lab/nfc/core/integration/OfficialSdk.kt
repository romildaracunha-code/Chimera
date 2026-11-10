package lab.nfc.core.integration
import org.json.JSONObject

enum class CaseVerdict { PASS, FAIL, INCONCLUSIVE }
enum class AuthenticationVerdict { VALID, INVALID, NOT_PERFORMED, INCONCLUSIVE }
data class SdkProfile(val provider:String,val version:String,val terminalModel:String,val documentationReference:String,val environmentReference:String) {
 fun issues()=listOf("provider" to provider,"version" to version,"terminalModel" to terminalModel,"documentationReference" to documentationReference,"environmentReference" to environmentReference).filter { it.second.isBlank() || it.second.length>512 }.map { "Missing/invalid ${it.first}" }
}
data class OfficialCaseRequest(val officialReference:String,val runId:String)
data class SdkSummary(val provider:String,val version:String,val caseReference:String,val traceReference:String,val result:CaseVerdict,val authentication:AuthenticationVerdict) {
 fun json():JSONObject { require(listOf(provider,version,caseReference,traceReference).all { it.isNotBlank() && it.length<=512 });return JSONObject().put("provider",provider).put("version",version).put("caseId",caseReference).put("environment","OFFICIAL_HOMOLOGATION").put("traceReference",traceReference).put("result",result.name).put("authenticationResult",authentication.name) }
}
sealed interface SdkExecution {
 data class Blocked(val reasons:List<String>):SdkExecution
 data class Reported(val summary:SdkSummary):SdkExecution
}
/** Vendor implementations must link official SDK dependencies and restrict execution to their test environment. */
interface OfficialSdkAdapter {
 val id:String
 fun prerequisites(profile:SdkProfile):List<String>
 suspend fun execute(profile:SdkProfile,request:OfficialCaseRequest):SdkExecution
}
/** A contract and import path, not a fake vendor implementation. */
class UnconfiguredSdkAdapter:OfficialSdkAdapter {
 override val id="official-sdk-unconfigured"
 override fun prerequisites(profile:SdkProfile)=profile.issues()+listOf("Vendor SDK/library and integration documentation not installed","Official L2/L3 plan and test credential required","Authorized homologation connection and terminal access required")
 override suspend fun execute(profile:SdkProfile,request:OfficialCaseRequest)=SdkExecution.Blocked(prerequisites(profile))
}
