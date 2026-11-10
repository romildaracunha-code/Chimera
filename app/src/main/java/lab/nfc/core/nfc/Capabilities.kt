package lab.nfc.core.nfc
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter

data class Capabilities(val nfc: Boolean,val enabled: Boolean,val hce: Boolean,val reader: Boolean) {
 companion object {
 fun evaluate(nfc: Boolean,enabled: Boolean,hce: Boolean,adapter: Boolean) = Capabilities(nfc, nfc && adapter && enabled, nfc && hce, nfc && adapter)
 fun detect(c: Context): Capabilities { val a=NfcAdapter.getDefaultAdapter(c); return evaluate(c.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC),a?.isEnabled==true,c.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION),a!=null) }
 }
}
