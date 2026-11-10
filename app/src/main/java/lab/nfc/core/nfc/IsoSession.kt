package lab.nfc.core.nfc
import lab.nfc.core.apdu.*

interface IsoTransport: AutoCloseable { val maxCommandBytes:Int;val extendedSupported:Boolean;fun connect();fun transceive(command:ByteArray):ByteArray }
class IsoPreflightFailure(cause:Exception):IllegalArgumentException("Comando rejeitado antes do transporte",cause)
class IsoMalformedResponse(val raw:ByteArray,cause:Exception):IllegalArgumentException("Resposta recebida sem framing válido",cause)
enum class IsoSessionState { NEW, CONNECTED, APPLICATION_SELECTED, CLOSED }
/** One owner per physical connection. No retries, relay, or implicit financial application selection. */
class IsoSession(private val wire:IsoTransport,val aid:String):AutoCloseable {
 init { LabAids.requireLab(aid) }
 var state=IsoSessionState.NEW;private set
 fun connect() { check(state==IsoSessionState.NEW);try { wire.connect();state=IsoSessionState.CONNECTED } catch(e:Exception) { close();throw e } }
 fun transceive(command:ByteArray):ByteArray {
  val c=try {
   check(state in listOf(IsoSessionState.CONNECTED,IsoSessionState.APPLICATION_SELECTED))
   val parsed=IsoCommand.parse(command);require(command.size<=wire.maxCommandBytes);require(!parsed.extended || wire.extendedSupported)
   if(parsed.ins==0xA4) require(parsed.p1==4 && parsed.data.size==8 && parsed.data.hex().startsWith("F04E46434C4142")) { "Somente namespace proprietário Lab" }
   else check(state==IsoSessionState.APPLICATION_SELECTED) { "Selecione o AID Lab antes de enviar outros comandos" }
   parsed
  } catch(e:Exception) { throw IsoPreflightFailure(e) }
  try { val r=wire.transceive(command);val response=try { Response.parse(r) } catch(e:Exception) { throw IsoMalformedResponse(r,e) }
   if(c.ins==0xA4) state=if(response.sw==0x9000 && c.data.hex()==aid) IsoSessionState.APPLICATION_SELECTED else IsoSessionState.CONNECTED
   return r
  } catch(e:Exception) { close();throw e }
 }
 override fun close() { if(state!=IsoSessionState.CLOSED) { state=IsoSessionState.CLOSED;runCatching { wire.close() } } }
}
