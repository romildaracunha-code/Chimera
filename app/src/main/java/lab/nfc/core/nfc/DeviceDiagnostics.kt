package lab.nfc.core.nfc
import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.util.UUID

/** OS-reported capabilities are observations, not proof of an RF exchange or payment processing. */
object DeviceDiagnostics {
 fun capture(context:Context):JSONObject {
  val c=Capabilities.detect(context)
  return JSONObject().put("schemaVersion",1).put("id",UUID.randomUUID().toString()).put("timestamp",System.currentTimeMillis())
   .put("origin","DEVICE_OS_REPORTED").put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL)
   .put("androidRelease",Build.VERSION.RELEASE).put("androidSdk",Build.VERSION.SDK_INT)
   .put("nfcLabVersion",context.packageManager.getPackageInfo(context.packageName,0).versionName ?: "UNKNOWN")
   .put("capabilities",JSONObject().put("nfcFeature",c.nfc).put("nfcEnabled",c.enabled).put("hceFeature",c.hce).put("readerAdapterAvailable",c.reader))
   .put("physicalExchangeValidated",false).put("emvSdkIntegration","NOT_CONFIGURED")
   .put("limit","Android feature/adapter reports only; no physical NFC exchange, terminal authentication or authorization was tested by this diagnostic")
 }
}
