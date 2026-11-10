package lab.nfc.core.hce
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.SystemClock
import lab.nfc.core.apdu.*
import lab.nfc.core.storage.*

class LabHceService: HostApduService() {
 private val persistence=java.util.concurrent.Executors.newSingleThreadExecutor()
 private val engine=LabEngine()
 private var sessionId: String?=null
 override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
 val store=LabStore.get(this); val id=store.activeHce ?: return "6985".bytes()
 val session=store.sessions.value.find { it.id==id && it.result=="Em andamento" } ?: return "6985".bytes()
 if(sessionId!=id) { engine.reset(); sessionId=id }
 val timestamp=System.currentTimeMillis(); val start=SystemClock.elapsedRealtimeNanos()
 val result=engine.exchange(commandApdu,session.profile)
 val elapsed=SystemClock.elapsedRealtimeNanos()-start
 val event=Exchange(timestamp,"Leitor → HCE físico",commandApdu.hex(),result.hex(),elapsed/1_000_000,elapsed,"HCE_RESPONSE_PREPARED_NOT_RF_ACKNOWLEDGED")
 persistence.execute { runCatching { store.append(id,event) }.onFailure { android.util.Log.e("NfcLab","HCE persistence failed; response preparation was not proof of reader receipt",it) } }
 return result
 }
 override fun onDestroy() { persistence.shutdown(); super.onDestroy() }
 override fun onDeactivated(reason: Int) { engine.reset(); sessionId=null }
}
