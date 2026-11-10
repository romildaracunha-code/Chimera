package lab.nfc
import android.app.Application
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import lab.nfc.core.apdu.*
import lab.nfc.core.storage.*

class LabViewModel(app: Application): AndroidViewModel(app) {
 val store=LabStore.get(app)
 val message=MutableStateFlow("")
 val profile=MutableStateFlow(store.profile)
 val busy=MutableStateFlow(false)
 val current=MutableStateFlow<String?>(null)
 fun select(name: String) { profile.value=name; store.profile=name }
 fun newSession(mode: String,technology: String): Session = Session(device="${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}",technology=technology,mode=mode,profile=profile.value,labAid=store.selectedAid,labScenario=if(store.rejectLabGetData) "REJECT_GET_DATA" else "NORMAL")
 fun simulate(repeat: Session?=null) = viewModelScope.launch(Dispatchers.IO) {
 try {
 val s=newSession("SIMULAÇÃO LOCAL", "Lógica APDU; sem rádio NFC").copy(profile=repeat?.profile ?: profile.value); store.save(s); current.value=s.id
 val engine=LabEngine(s.labAid,s.labScenario=="REJECT_GET_DATA")
 val commands=repeat?.exchanges?.map { it.command }?.ifEmpty { null } ?: listOf(LabAids.select(s.labAid),LabProtocol.GET_DATA)
 commands.forEach { hex -> val start=SystemClock.elapsedRealtime(); val response=engine.exchange(hex.bytes(),s.profile); store.append(s.id,Exchange(System.currentTimeMillis(),"Terminal → emulador SIMULADO",hex,response.hex(),SystemClock.elapsedRealtime()-start)) }
 val latest=store.sessions.value.first { it.id==s.id }; store.save(latest.copy(result=if(latest.exchanges.all { it.status=="9000" }) "Concluído" else "Com status de erro")); message.value="Simulação salva; nenhuma transmissão NFC ocorreu."
 } catch(e: Exception) { message.value=e.message ?: "Falha na simulação" }
 }
 fun startHce() { if(store.activeHce!=null) { message.value="Encerre a sessão HCE atual"; return }; val s=newSession("HARDWARE HCE", "ISO-DEP / HCE"); store.save(s); store.activeHce=s.id; current.value=s.id; message.value="HCE armado. Aguardando leitor físico; mantenha o aparelho desbloqueado." }
 fun stopHce() { store.activeHce?.let { id -> store.sessions.value.find { it.id==id }?.let { store.save(it.copy(result=if(it.exchanges.isEmpty()) "Encerrado sem comunicação física" else "Encerrado; APDUs observados")) } }; store.activeHce=null; message.value="Sessão HCE encerrada" }
 fun notes(id: String,notes: String) { store.sessions.value.find { it.id==id }?.let { store.save(it.copy(notes=notes)) } }
}
