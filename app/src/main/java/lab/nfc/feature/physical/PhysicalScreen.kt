package lab.nfc.feature.physical

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import lab.nfc.LabViewModel
import lab.nfc.time
import lab.nfc.core.nfc.Capabilities
import lab.nfc.core.storage.*
import lab.nfc.feature.shared.Panel
import org.json.JSONObject

@Composable private fun <T: Enum<T>> Choice(label: String, value: T, values: List<T>, onChange: (T)->Unit) {
 TextButton(onClick={ onChange(values[(values.indexOf(value)+1) % values.size]) }) { Text("$label: ${value.name} ▸") }
}

@Composable fun PhysicalScreen(vm: LabViewModel, caps: Capabilities, export: (String,String)->Unit) {
 val devices by vm.store.devices.collectAsState()
 val sessions by vm.store.sessions.collectAsState()
 val experiments by vm.store.experiments.collectAsState()
 var terminal by remember { mutableStateOf("") }
 var operator by remember { mutableStateOf("") }
 var phone by remember { mutableStateOf("${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}") }
 var env by remember { mutableStateOf(ExperimentEnvironment.LAB_PROTOCOL) }
 var envRef by remember { mutableStateOf("") }
 var credential by remember { mutableStateOf(CredentialKind.NFC_LAB_FICTITIOUS) }
 var caseId by remember { mutableStateOf("") }
 var nfc by remember { mutableStateOf(Observation.UNKNOWN) }
 var emv by remember { mutableStateOf(ProcessingResult.NOT_APPLICABLE) }
 var auth by remember { mutableStateOf(AuthorizationResult.NOT_REQUESTED) }
 var settlement by remember { mutableStateOf(SettlementResult.NOT_OBSERVED) }
 var rejection by remember { mutableStateOf(RejectionLocation.UNKNOWN) }
 var session by remember { mutableStateOf("") }
 var refs by remember { mutableStateOf("") }
 var notes by remember { mutableStateOf("") }
 Panel("Sprint físico • EMV / homologação") {
  Text("Este formulário registra observações do operador. Não executa EMV nem confirma evidências automaticamente. O AID NFC Lab não é um cartão de pagamento.")
  Text("NFC: ${caps.nfc} • ligado: ${caps.enabled} • HCE declarado pelo Android: ${caps.hce}")
  Button(onClick={
   val report=JSONObject().put("schemaVersion",1).put("capturedAt",System.currentTimeMillis())
    .put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL).put("android",Build.VERSION.RELEASE).put("sdk",Build.VERSION.SDK_INT)
    .put("nfcFeature",caps.nfc).put("nfcEnabled",caps.enabled).put("hceFeature",caps.hce).put("readerAvailable",caps.reader)
    .put("attestation","ANDROID_REPORTED_CAPABILITIES_NOT_RF_VERIFICATION")
   export("phone-capabilities.json",report.toString(2))
  }) { Text("Exportar diagnóstico do celular") }
  Text("Cadastre o terminal em Devices. Identifique versão do app, firmware e adquirente pela tela normal de informações ou documentação autorizada.")
  devices.forEach { d -> TextButton(onClick={ terminal=d.internalId }) { Text("${if(terminal==d.internalId) "●" else "○"} ${d.internalId} • ${d.manufacturer} ${d.model}") } }
  OutlinedTextField(operator,{ operator=it },label={ Text("Operador / pesquisador") },modifier=Modifier.fillMaxWidth())
  OutlinedTextField(phone,{ phone=it },label={ Text("Celular / referência do diagnóstico") },modifier=Modifier.fillMaxWidth())
  Choice("Ambiente",env,ExperimentEnvironment.entries) { env=it; if(it==ExperimentEnvironment.LAB_PROTOCOL) { credential=CredentialKind.NFC_LAB_FICTITIOUS; emv=ProcessingResult.NOT_APPLICABLE; auth=AuthorizationResult.NOT_REQUESTED; settlement=SettlementResult.NOT_OBSERVED } else { credential=if(it==ExperimentEnvironment.OFFICIAL_HOMOLOGATION) CredentialKind.OFFICIAL_POSITIVE else CredentialKind.LEGITIMATE; emv=ProcessingResult.UNKNOWN; auth=AuthorizationResult.UNKNOWN } }
  OutlinedTextField(envRef,{ envRef=it },label={ Text("Ambiente/tenant de teste ou referência da execução") },modifier=Modifier.fillMaxWidth())
  Choice("Credencial (sem dados do cartão)",credential,CredentialKind.entries) { credential=it }
  OutlinedTextField(caseId,{ caseId=it },label={ Text("ID e versão do caso oficial / kit de teste") },modifier=Modifier.fillMaxWidth())
  Choice("Detecção NFC",nfc,Observation.entries) { nfc=it }
  Choice("Processamento EMV",emv,ProcessingResult.entries) { emv=it }
  Choice("Autorização (host)",auth,AuthorizationResult.entries) { auth=it }
  Choice("Liquidação",settlement,SettlementResult.entries) { settlement=it }
  Choice("Origem documentada da rejeição",rejection,RejectionLocation.entries) { rejection=it }
  Text("Uma recusa na tela ou APDU de erro não identifica sozinha terminal/processador. Registre UNKNOWN quando faltar trace oficial ou correlação do host.")
  TextButton(onClick={ session="" }) { Text("Sem vínculo APDU / limpar vínculo") }
  sessions.filter { it.mode in listOf("HARDWARE HCE","HARDWARE LEITOR") && it.exchanges.isNotEmpty() }.take(10).forEach { s -> TextButton(onClick={ session=s.id }) { Text("${if(session==s.id) "●" else "○"} APDU ${time(s.date)} • ${s.id.take(8)}") } }
  Text("Vínculo selecionado: ${session.ifBlank { "nenhum" }}")
  OutlinedTextField(refs,{ refs=it },label={ Text("Arquivos de evidência (uma referência por linha)") },modifier=Modifier.fillMaxWidth())
  OutlinedTextField(notes,{ notes=it },label={ Text("Observações; não inclua PAN, PIN, trilhas ou chaves") },modifier=Modifier.fillMaxWidth())
  Button(onClick={
   runCatching { vm.store.saveExperiment(PhysicalExperiment(terminalId=terminal,operator=operator,phoneReference=phone,environment=env,environmentReference=envRef,credential=credential,officialCaseId=caseId,nfc=nfc,emv=emv,authorization=auth,settlement=settlement,rejection=rejection,labSessionId=session,evidenceRefs=refs.lines().map { it.trim() }.filter { it.isNotEmpty() },notes=notes)) }
    .onSuccess { vm.message.value="Registro salvo como declaração do operador; evidência ainda requer revisão." }
    .onFailure { vm.message.value=it.message ?: "Registro inválido" }
  }) { Text("Salvar observação física") }
  Button(onClick={ export("physical-experiments.json",PhysicalExport.json(experiments,devices)) }) { Text("Exportar inventário + experimentos") }
  Text("Nenhum resultado EMV é inferido de sessões simuladas. A emissão, autenticação e autorização de teste usam ferramentas e credenciais oficiais do provedor.")
 }
 experiments.take(20).forEach { e -> Panel("${e.terminalId} • ${time(e.createdAt)}") {
  Text("${e.environment} / ${e.credential}"); Text("NFC ${e.nfc} • EMV ${e.emv}")
  Text("Autorização ${e.authorization} • liquidação ${e.settlement}")
  Text("Rejeição ${e.rejection} • caso ${e.officialCaseId.ifBlank { "n/a" }}")
  Text("Evidências referenciadas: ${e.evidenceRefs.size}; não verificadas pelo aplicativo")
  Text(e.notes)
 } }
}
