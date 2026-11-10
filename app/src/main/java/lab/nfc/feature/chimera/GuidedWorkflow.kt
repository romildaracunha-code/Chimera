package lab.nfc.feature.chimera

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lab.nfc.LabViewModel
import lab.nfc.core.chimera.*
import lab.nfc.core.integration.*
import lab.nfc.feature.shared.Panel
import org.json.JSONObject

@Composable private fun DraftField(vm:ChimeraViewModel,key:String,label:String,default:String="") {
 val draft by vm.draft.collectAsStateWithLifecycle()
 OutlinedTextField(draft.optString(key,default),{ if(it.length<=4096) vm.editDraft(key,it) },label={ Text(label) },modifier=Modifier.fillMaxWidth(),maxLines=4)
}
@Composable fun GuidedWorkflow(vm:ChimeraViewModel,legacy:LabViewModel,attach:(String,ArtifactRole,Boolean)->Unit,export:(String,String,String)->Unit) {
 val state by vm.state.collectAsStateWithLifecycle();val draft by vm.draft.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
 val devices by legacy.store.devices.collectAsStateWithLifecycle()
 val steps=listOf("Identificar","Planejar","Registrar execução","Anexar","Revisar","Relatório")
 val step=draft.optString("step","0").toIntOrNull()?.coerceIn(0,5) ?: 0
 val runId=draft.optString("selectedRun");val run=state.getJSONArray("runs").objects().firstOrNull { it.getString("id")==runId }
 Panel("Roteiro guiado • ${step+1}/6 • ${steps[step]}") {
  Text("Rascunho salvo no armazenamento criptografado. A criação do plano não executa o teste; nenhum resultado físico é preenchido automaticamente.")
  Row { TextButton(enabled=step>0 && !busy,onClick={ vm.editDraft("step",(step-1).toString()) }) { Text("Anterior") };TextButton(enabled=step<5 && !busy,onClick={ vm.editDraft("step",(step+1).toString());vm.flushDraft() }) { Text("Próxima etapa") } }
  Text("Execução: ${runId.ifBlank { "nenhuma selecionada" }}")
  if(step==0) {
   Text("Cadastre fabricante, modelo, firmware, aplicativo e acesso de homologação em Terminal Inventory. Se desconhecido, registre a limitação; não adivinhe.")
   devices.forEach { d -> TextButton(enabled=!busy,onClick={ vm.editDraft("terminal",d.internalId) }) { Text("${if(d.internalId==draft.optString("terminal")) "●" else "○"} ${d.internalId} • ${d.manufacturer} ${d.model}") } }
   DraftField(vm,"operator","Operador");DraftField(vm,"phone","Celular / versão Android / referência do diagnóstico");DraftField(vm,"environment","Bancada ou homologação / versão / referência")
  }
  if(step==1) {
   state.getJSONArray("cases").objects().forEach { c -> TextButton(enabled=!busy,onClick={ vm.editDraft("case",c.getString("id")) }) { Text("${if(c.getString("id")==draft.optString("case")) "●" else "○"} ${c.getString("id")} • ${c.getString("title")}") } }
   Button(enabled=!busy,onClick={ val d=devices.firstOrNull { it.internalId==draft.optString("terminal") };val c=draft.optString("case");val op=draft.optString("operator");val phone=draft.optString("phone");val env=draft.optString("environment");vm.work {
    require(d!=null) { "Selecione o terminal na etapa 1" };vm.selectedRun=vm.ledger.createRun(c,d.json(),op,phone,env,Team.RED,vm.appVersion);vm.editDraft("step","2");vm.flushDraft();"Plano criado e selecionado; execução presencial pendente"
   } }) { Text("Criar plano Red com snapshots") }
   state.getJSONArray("runs").objects().takeLast(20).forEach { r -> TextButton(enabled=!busy,onClick={ vm.selectedRun=r.getString("id");vm.editDraft("step","2") }) { Text("Retomar ${r.getString("id").take(8)} • ${r.getJSONObject("terminalSnapshot").optString("model")}") } }
  }
  if(step==2) {
   Text("Execute presencialmente. Para protocolo proprietário: Emulator/Reader ou PCSC. Para EMV: ferramenta e credencial oficiais, em homologação. Distingua NFC, seleção, processamento, autenticação e resposta do host.")
   val stage=runCatching { EvidenceStage.valueOf(draft.optString("stage","UNVERIFIED")) }.getOrDefault(EvidenceStage.UNVERIFIED)
   TextButton(enabled=!busy,onClick={ vm.editDraft("stage",EvidenceStage.entries[(stage.ordinal+1)%EvidenceStage.entries.size].name) }) { Text("Etapa observada: $stage") }
   val outcomes=listOf("INCONCLUSIVE","OBSERVED","PASS","FAIL");val outcome=draft.optString("outcome","INCONCLUSIVE")
   TextButton(enabled=!busy,onClick={ vm.editDraft("outcome",outcomes[(outcomes.indexOf(outcome)+1)%outcomes.size]) }) { Text("Resultado: $outcome") }
   DraftField(vm,"notes","Condições, falhas e observações sem segredos")
   // Attestation is intentionally ephemeral: never automatically restored as affirmative.
   var attested by remember(runId) { mutableStateOf(false) }
   Row { Checkbox(attested,{ attested=it });Text("Realizei a execução física identificada acima") }
   Button(enabled=!busy && run!=null,onClick={ val notes=draft.optString("notes");vm.work { vm.ledger.observe(runId,stage,outcome,notes,attested);"Observação registrada; evidências e revisão ainda necessárias" } }) { Text("Registrar observação") }
  }
  if(step==3) {
   Text("Anexe contexto físico e traces de leitor/terminal; SDK e host quando a etapa exigir. Revise e remova dados bancários antes de importar.")
   var authorized by remember(runId) { mutableStateOf(false) };Row { Checkbox(authorized,{ authorized=it });Text("Arquivos autorizados e redigidos, sem segredos") }
   ArtifactRole.entries.forEach { role -> TextButton(enabled=!busy && run!=null && authorized,onClick={ attach(runId,role,authorized) }) { Text("Anexar $role") } }
   state.getJSONArray("artifacts").objects().filter { it.optString("runId")==runId }.forEach { Text("${it.getString("role")} • ${it.getLong("size")} bytes • ${it.getString("sha256").take(16)}…") }
   Text("Resultados oficiais de SDK e de host podem ser associados em Security Evidence. O app não substitui a ferramenta do fabricante.")
  }
  if(step==4) {
   DraftField(vm,"reviewer","Revisor distinto do operador");DraftField(vm,"reviewReference","Referência da revisão e correlação dos traces")
   Button(enabled=!busy && run!=null,onClick={ vm.work { vm.output.value=vm.ledger.runReport(runId).toString(2);"Pendências e hashes recalculados" } }) { Text("Verificar pendências") }
   Button(enabled=!busy && run!=null,onClick={ val reviewer=draft.optString("reviewer");val ref=draft.optString("reviewReference");vm.work { vm.ledger.review(runId,reviewer,ref);vm.editDraft("step","5");"Revisão documental salva" } }) { Text("Concluir revisão documental") }
   val output by vm.output.collectAsStateWithLifecycle();Text(output.take(16000))
  }
  if(step==5) {
   Text("O relatório recalcula as pendências; revisão documental não comprova autorização financeira ou autenticidade física por si só.")
   Button(enabled=!busy && run!=null,onClick={ vm.work { export("experiment-$runId.json","application/json",vm.ledger.runReport(runId).toString(2));"Relatório individual preparado" } }) { Text("Exportar relatório desta execução") }
  }
 }
}

@Composable fun TypedSdkSummary(vm:ChimeraViewModel) {
 Panel("Resumo oficial • campos estruturados") {
  DraftField(vm,"sdkProvider","Fornecedor SDK");DraftField(vm,"sdkVersion","Versão SDK");DraftField(vm,"sdkCase","Referência oficial do caso");DraftField(vm,"sdkTrace","Referência do trace autorizado")
  var result by remember { mutableStateOf(CaseVerdict.INCONCLUSIVE) };var auth by remember { mutableStateOf(AuthenticationVerdict.INCONCLUSIVE) }
  TextButton(onClick={ result=CaseVerdict.entries[(result.ordinal+1)%CaseVerdict.entries.size] }) { Text("Veredito do caso: $result") }
  TextButton(onClick={ auth=AuthenticationVerdict.entries[(auth.ordinal+1)%AuthenticationVerdict.entries.size] }) { Text("Autenticação declarada: $auth") }
  val busy by vm.busy.collectAsStateWithLifecycle()
  Button(enabled=!busy && vm.selectedRun.isNotBlank(),onClick={ val run=vm.selectedRun;val p=vm.draftValue("sdkProvider");val v=vm.draftValue("sdkVersion");val c=vm.draftValue("sdkCase");val t=vm.draftValue("sdkTrace");val verdict=result;val authentication=auth;vm.work { vm.ledger.importSdk(run,SdkSummary(p,v,c,t,verdict,authentication).json());"Resumo oficial associado; declaração externa, sem verificação criptográfica local" } }) { Text("Associar ao experimento selecionado") }
 }
}

@Composable fun IntegrationProfile(vm:ChimeraViewModel) {
 Panel("Perfil de integração oficial • somente configuração") {
  DraftField(vm,"integrationProvider","Fornecedor");DraftField(vm,"integrationVersion","Versão da biblioteca SDK")
  DraftField(vm,"integrationModel","Modelo de terminal compatível");DraftField(vm,"integrationDocs","Referência da documentação / contrato")
  DraftField(vm,"integrationEnvironment","Identificador do ambiente de homologação (sem segredo)")
  val busy by vm.busy.collectAsStateWithLifecycle()
  Button(enabled=!busy,onClick={ val profile=SdkProfile(vm.draftValue("integrationProvider"),vm.draftValue("integrationVersion"),vm.draftValue("integrationModel"),vm.draftValue("integrationDocs"),vm.draftValue("integrationEnvironment"));vm.work {
   vm.flushDraft();vm.output.value=org.json.JSONObject().put("adapter","UNCONFIGURED").put("canExecute",false).put("dependencies",org.json.JSONArray(UnconfiguredSdkAdapter().prerequisites(profile))).toString(2);"Perfil salvo; dependências reais de SDK avaliadas"
  } }) { Text("Salvar perfil e identificar bloqueios") }
  val output by vm.output.collectAsStateWithLifecycle();Text(output.take(12000))
 }
}
