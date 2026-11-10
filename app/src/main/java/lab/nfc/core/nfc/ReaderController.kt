package lab.nfc.core.nfc
import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.SystemClock
import lab.nfc.LabViewModel
import lab.nfc.core.apdu.*
import lab.nfc.core.storage.*
import kotlinx.coroutines.*

class ReaderController(private val activity: Activity,private val vm: LabViewModel) {
 private val inFlight=java.util.concurrent.atomic.AtomicBoolean(false)
 private val adapter=NfcAdapter.getDefaultAdapter(activity)
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 @Volatile private var connected: IsoDep?=null
 @Volatile private var sessionId: String?=null
 @Volatile var enabled=false; private set
 private var commands=listOf(LabAids.select(vm.store.selectedAid),LabProtocol.GET_DATA)
 fun start(custom: String) {
 require(adapter?.isEnabled==true) { "NFC indisponível ou desligado" }
 commands=listOf(LabAids.select(vm.store.selectedAid),custom.ifBlank { LabProtocol.GET_DATA }.also { IsoCommand.parse(it.bytes()) })
 enabled=true
 adapter.enableReaderMode(activity,{ tag -> scope.launch { read(tag) } }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,null)
 vm.message.value="Leitor ativo: aproxime uma tag própria/autorizada."
 }
 private fun read(tag: Tag) {
 if(!enabled || !inFlight.compareAndSet(false,true)) return
 vm.busy.value=true
 val batch=commands.toList()
 val iso=IsoDep.get(tag)
 var managed: IsoSession?=null
 val tech=tag.techList.joinToString(); val s=vm.newSession("HARDWARE LEITOR",tech).copy(labAid=IsoCommand.parse(batch.first().bytes()).data.hex(),labScenario="PEER_UNSPECIFIED",notes="Tag ID: ${tag.id.hex()} (não é credencial)")
 sessionId=s.id
 try {
 vm.store.save(s); vm.current.value=s.id
 if(iso==null) { vm.store.save(s.copy(result="Tag detectada; sem ISO-DEP")); vm.message.value="Tecnologias: $tech"; return }
 connected=iso
 managed=IsoSession(object: IsoTransport {
  override val maxCommandBytes get()=iso.maxTransceiveLength
  override val extendedSupported get()=iso.isExtendedLengthApduSupported
  override fun connect() { iso.connect();iso.timeout=5000 }
  override fun transceive(command:ByteArray)=iso.transceive(command)
  override fun close() { iso.close() }
 },s.labAid)
 managed.connect()
 vm.message.value="ISO-DEP • max transceive ${iso.maxTransceiveLength} • extended ${iso.isExtendedLengthApduSupported} • histórico ${iso.historicalBytes?.hex().orEmpty()} • hi-layer ${iso.hiLayerResponse?.hex().orEmpty()}"
 for(hex in batch) {
 check(enabled) { "Leitura cancelada" }
 val timestamp=System.currentTimeMillis(); val start=SystemClock.elapsedRealtimeNanos()
 val result=try { managed.transceive(hex.bytes()) } catch(e: Exception) {
  val duration=SystemClock.elapsedRealtimeNanos()-start
  val raw=if(e is IsoMalformedResponse) e.raw.hex() else ""
  val capture=when(e) { is IsoPreflightFailure->"PRECHECK_REJECTED_NOT_TRANSMITTED";is IsoMalformedResponse->"ISODEP_RESPONSE_RETURNED_INVALID_FRAMING";else->"TRANSCEIVE_ATTEMPT_NO_RESPONSE" }
  vm.store.append(s.id,Exchange(timestamp,"Leitor → tag FÍSICA",hex,raw,duration/1_000_000,duration,capture,e.javaClass.simpleName+": "+e.message));throw e
 }
 val duration=SystemClock.elapsedRealtimeNanos()-start
 vm.store.append(s.id,Exchange(timestamp,"Leitor → tag FÍSICA",hex,result.hex(),duration/1_000_000,duration,"ISODEP_RESPONSE_RETURNED"))
 if(result.hex().takeLast(4)!="9000") break
 }
 val latest=vm.store.sessions.value.first { it.id==s.id }; vm.store.save(latest.copy(result=if(latest.exchanges.all { it.status=="9000" }) "Concluído; rádio NFC observado" else "Resposta de erro no hardware"))
 } catch(e: Exception) {
 val latest=vm.store.sessions.value.find { it.id==s.id } ?: s
 vm.store.save(latest.copy(result="Erro de comunicação",error=e.javaClass.simpleName+": "+e.message)); vm.message.value="Erro: ${e.message}"
 } finally { managed?.close();runCatching { iso?.close() }; connected=null; sessionId=null; vm.busy.value=false; inFlight.set(false) }
 }
 fun stop() { enabled=false; adapter?.disableReaderMode(activity); runCatching { connected?.close() } }
 fun destroy() { stop(); scope.cancel() }
}
