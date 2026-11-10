package lab.nfc.feature.chimera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lab.nfc.LabViewModel
import lab.nfc.core.chimera.*
import lab.nfc.core.emv.*
import lab.nfc.feature.shared.Panel
import lab.nfc.feature.devices.DeviceEditor
import org.json.JSONObject

val chimeraPages=listOf("Device Diagnostics","Guided Workflow","Data Protection","SDK Integrations","EMV Explorer","Vulnerability Research","Physical Test Lab","Terminal Inventory","Test Case Manager","Security Evidence","Red/Blue Comparison","Technical Reports")
@Composable private fun Field(value:String,set:(String)->Unit,label:String)=OutlinedTextField(value,{ if(it.length<=32768) set(it) },label={ Text(label) },modifier=Modifier.fillMaxWidth(),maxLines=8)
@Composable private fun <T:Enum<T>> Cycle(label:String,value:T,values:List<T>,set:(T)->Unit) { TextButton(onClick={ set(values[(values.indexOf(value)+1)%values.size]) }) { Text("$label: ${value.name} ▸") } }

/** Functional controls over the existing hardware path and separate evidence ledger. No financial emulation. */
@Composable fun ChimeraScreen(page:String,vm:ChimeraViewModel,legacy:LabViewModel,
 export:(String,String,String)->Unit,importText:(String,(String)->Unit)->Unit,
 attach:(String,ArtifactRole,Boolean)->Unit,exportZip:()->Unit) {
 val state by vm.state.collectAsStateWithLifecycle()
 val message by vm.message.collectAsStateWithLifecycle()
 val output by vm.output.collectAsStateWithLifecycle()
 val busy by vm.busy.collectAsStateWithLifecycle()
 val draft by vm.draft.collectAsStateWithLifecycle()
 val activeRun=draft.optString("selectedRun")
 val devices by legacy.store.devices.collectAsStateWithLifecycle()
 val cases=state.getJSONArray("cases").objects();val runs=state.getJSONArray("runs").objects()
 var runId by remember { mutableStateOf(vm.selectedRun) }
 fun selectRun(id:String) { if(busy) return;runId=id;vm.selectedRun=id }
 Column(verticalArrangement=Arrangement.spacedBy(androidx.compose.ui.unit.Dp(12f))) {
  Text(message,color=MaterialTheme.colorScheme.primary)
  if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
  if(vm.ledger.integrityError!=null) { Text("Módulo V3 bloqueado; arquivos originais preservados: ${vm.ledger.integrityError}");return@Column }
  when(page) {
   "Device Diagnostics" -> DiagnosticsScreen(export)
   "Guided Workflow" -> GuidedWorkflow(vm,legacy,attach,export)
   "Data Protection" -> DataProtection(vm,legacy)
   "SDK Integrations" -> {
    TypedSdkSummary(vm)
    IntegrationProfile(vm)
    Panel("Integração de fabricante • bloqueios explícitos") {
     Text("O contrato OfficialSdkAdapter está disponível no código. Nenhum SDK de fabricante foi instalado nem pagamento executado. Informar fornecedor/versão não configura acesso ao terminal.")
     Text("Indispensáveis: biblioteca/documentação do SDK, modelo/firmware, plano L2/L3 e credenciais oficiais, ambiente de homologação e canal autorizado de logs. O adaptador não configurado retorna Blocked com essas dependências.")
    }
   }
   "EMV Explorer" -> {
    var hex by remember { mutableStateOf("") };var source by remember { mutableStateOf("Entrada autorizada do pesquisador") }
    var context by remember { mutableStateOf("") };var kind by remember { mutableStateOf(InputKind.TLV) };var origin by remember { mutableStateOf(DataOrigin.IMPORTED_AUTHORIZED) }
    val recovered by vm.recoveredImport.collectAsStateWithLifecycle()
    LaunchedEffect(recovered) { recovered?.takeIf { it.first=="analysis" }?.let { hex=it.second;kind=if(hex.trimStart().startsWith("{") || hex.trimStart().startsWith("[")) InputKind.TRACE_JSON else InputKind.TLV;origin=DataOrigin.IMPORTED_AUTHORIZED;source="Arquivo autorizado importado por SAF após recriação";vm.recoveredImport.value=null } }
    Panel("BER-TLV / APDU • análise offline") {
     Text("Não envia APDUs nem comprova autenticação. PAN/trilhas/criptogramas/certificados e tags desconhecidas são omitidos nos relatórios. Não insira dados reais secretos.")
     Cycle("Formato",kind,InputKind.entries) { kind=it }
     Text("Origem: $origin. Importações nunca se tornam hardware observado pelo app.")
     Button(onClick={ importText("analysis") { hex=it;if(it.trimStart().startsWith("{") || it.trimStart().startsWith("[")) kind=InputKind.TRACE_JSON;origin=DataOrigin.IMPORTED_AUTHORIZED;source="Arquivo autorizado importado por SAF" } }) { Text("Importar arquivo autorizado (hex / JSON NFC Lab / PCSC)") }
     vm.vectors.getJSONArray("vectors").objects().forEach { v -> TextButton(onClick={ hex=v.getString("hex");source=vm.vectors.getString("sourceUrl")+"#"+v.getString("id");kind=InputKind.TLV;origin=DataOrigin.PUBLIC_TEST }) { Text("Fixture pública: ${v.getString("id")}") } }
     TextButton(onClick={ origin=DataOrigin.SIMULATED;source="Fixture sintética declarada pelo operador" }) { Text("Marcar entrada como sintética") }
     Field(hex,{ hex=it;if(origin==DataOrigin.PUBLIC_TEST) { origin=DataOrigin.IMPORTED_AUTHORIZED;source="Fixture editada pelo operador" } },if(kind==InputKind.TRACE_JSON) "JSON: exchanges [{command,response,timestamp}]" else "Hexadecimal BER-TLV / APDU")
     Field(context,{ context=it },"Comando de contexto (resposta APDU; opcional)")
     Field(source,{ source=it;if(origin==DataOrigin.PUBLIC_TEST) origin=DataOrigin.IMPORTED_AUTHORIZED },"Fonte / proveniência")
     Button(enabled=!busy,onClick={ vm.analyze(hex,kind,origin,source,context) }) { Text("Analisar e salvar resultado sanitizado") }
    }
    val analyses=state.getJSONArray("analyses").objects()
    var left by remember { mutableStateOf("") };var right by remember { mutableStateOf("") }
    Panel("Análises salvas / comparação estrutural") {
     analyses.takeLast(20).forEach { a -> Row { TextButton(onClick={ left=a.getString("id");vm.output.value=a.toString(2) }) { Text("A ${a.getString("id").take(8)} ${a.getString("origin")}") };TextButton(onClick={ right=a.getString("id") }) { Text("B") } } }
     Text("A=$left • B=$right")
     Button(enabled=left.isNotBlank() && right.isNotBlank(),onClick={ vm.work {
      val a=vm.ledger.find("analyses",left);val b=vm.ledger.find("analyses",right)
      vm.output.value=TraceImport.compare(a,b).toString(2);"Comparação sanitizada e métricas declaradas calculadas"
     } }) { Text("Comparar A/B") }
    }
    if(output.isNotBlank()) Panel("Resultado técnico") { SelectionContainer { Text(if(output.length>65536) output.take(65536)+"\n[Prévia limitada; exportação mantém conteúdo completo]" else output) };TextButton(onClick={ export("emv-analysis.txt","text/plain",output) }) { Text("Exportar análise") } }
   }
   "Vulnerability Research" -> vm.research.objects().forEach { r -> Panel("${r.getString("id")} • ${r.getString("title")}") {
    listOf("url","evidenceClass","summary","conditions","publishedEvidence","mitigations","currentStatus","labCheck","dependencies").forEach { key -> Text("$key: ${r.getString(key)}") }
    Text("Verificação atual: NÃO EXECUTADA. Não pressupor vulnerabilidade pelo fabricante.")
   } }
   "Terminal Inventory" -> {
    DeviceEditor(legacy)
    devices.forEach { d -> Panel("${d.internalId} • ${d.manufacturer} ${d.model}") { Text("Firmware ${d.firmware} • app ${d.paymentApp} • kernel ${d.contactlessKernel}");Text("Homologação ${d.homologation} • fonte ${d.evidenceReference}") } }
   }
   "Test Case Manager" -> {
    var id by remember { mutableStateOf("") };var title by remember { mutableStateOf("") };var kind by remember { mutableStateOf("LAB") }
    var revision by remember { mutableStateOf("1") };var official by remember { mutableStateOf("") };var expected by remember { mutableStateOf("") };var dependencies by remember { mutableStateOf("") };var research by remember { mutableStateOf("") }
    Panel("Casos e dependências") {
     Text("IDs locais não são casos oficiais. EMV exige vincular referência/versão do provedor e executar pela ferramenta oficial; o app não fornece chaves nem gera criptogramas.")
     cases.forEach { c -> TextButton(onClick={ id=c.getString("id");title=c.getString("title");kind=c.getString("kind");revision=c.getString("revision");official=c.optString("officialReference");expected=c.optString("expected");dependencies=c.optString("dependencies");research=c.optString("researchId") }) { Text("${c.getString("id")} • ${if(c.getString("kind")=="OFFICIAL_EMV" && c.optString("officialReference").isBlank()) "BLOQUEADO: caso oficial ausente" else "definido, hardware pendente"}") } }
     Field(id,{ id=it },"ID local");Field(title,{ title=it },"Título");TextButton(onClick={ kind=if(kind=="LAB") "OFFICIAL_EMV" else "LAB" }) { Text("Tipo: $kind") }
     Field(revision,{ revision=it },"Revisão do caso");Field(official,{ official=it },"Referência oficial / versão do provedor (EMV)")
     Field(expected,{ expected=it },"Resultado esperado segundo o plano");Field(dependencies,{ dependencies=it },"Dependências / acesso");Field(research,{ research=it },"Pesquisa associada")
     Button(enabled=!busy,onClick={ vm.work { vm.ledger.saveCase(JSONObject().put("id",id).put("title",title).put("kind",kind).put("revision",revision).put("officialReference",official).put("expected",expected).put("dependencies",dependencies).put("researchId",research).put("source","OPERATOR_DEFINED_CASE"));"Caso salvo; snapshots anteriores preservados" } }) { Text("Salvar caso") }
    }
   }
   "Physical Test Lab" -> {
    var device by remember { mutableStateOf("") };var case by remember { mutableStateOf("") };var operator by remember { mutableStateOf("") };var phone by remember { mutableStateOf("") };var environment by remember { mutableStateOf("") };var team by remember { mutableStateOf(Team.RED) };var baseline by remember { mutableStateOf("") }
    Panel("Plano físico • contexto congelado por execução") {
     Text("Sem hardware conectado ao executor. Cada execução começa UNVERIFIED. Os módulos Emulator/Reader continuam a realizar somente o protocolo proprietário; EMV pertence ao SDK/kit oficial.")
     devices.forEach { d -> TextButton(onClick={ device=d.internalId }) { Text("${if(device==d.internalId) "●" else "○"} ${d.internalId} • ${d.model}") } }
     cases.forEach { c -> TextButton(onClick={ case=c.getString("id") }) { Text("${if(case==c.getString("id")) "●" else "○"} ${c.getString("id")} • ${c.getString("title")}") } }
     Field(operator,{ operator=it },"Operador Red/Blue");Field(phone,{ phone=it },"Celular / referência do diagnóstico");Field(environment,{ environment=it },"Bancada ou ambiente/tenant oficial e versão")
     Cycle("Equipe",team,Team.entries) { team=it }
     if(team==Team.BLUE) runs.filter { it.getString("team")=="RED" }.forEach { before -> TextButton(onClick={ baseline=before.getString("id") }) { Text("${if(baseline==before.getString("id")) "●" else "○"} Baseline Red ${before.getString("id").take(8)}") } }
     Button(enabled=!busy,onClick={ vm.work { val d=devices.firstOrNull { it.internalId==device } ?: error("Selecione terminal no inventário");val created=vm.ledger.createRun(case,d.json(),operator,phone,environment,team,vm.appVersion,baseline);vm.selectedRun=created;"Plano $created criado; evidência física ainda ausente" } }) { Text("Criar experimento físico (não executa pagamento)") }
    }
    runs.takeLast(20).forEach { r -> Panel("${r.getString("id")} • ${r.getString("team")}") { Text("${r.getJSONObject("terminalSnapshot").optString("model")} • ${r.getJSONObject("caseSnapshot").getString("id")}");Text("Observação: ${r.getString("claimedStage")} / ${r.getString("outcome")}; ${r.getString("validation")}");TextButton(onClick={ selectRun(r.getString("id")) }) { Text("Selecionar para Security Evidence") } } }
   }
   "Security Evidence" -> {
    TypedSdkSummary(vm)
    var stage by remember { mutableStateOf(EvidenceStage.UNVERIFIED) };var outcome by remember { mutableStateOf("INCONCLUSIVE") };var notes by remember { mutableStateOf("") };var physical by remember { mutableStateOf(false) }
    var role by remember { mutableStateOf(ArtifactRole.PHYSICAL_CONTEXT) };var authorized by remember { mutableStateOf(false) };var reviewer by remember { mutableStateOf("") };var reviewRef by remember { mutableStateOf("") };var sdk by remember { mutableStateOf("") }
    val recovered by vm.recoveredImport.collectAsStateWithLifecycle()
    LaunchedEffect(recovered) { recovered?.takeIf { it.first=="sdk" }?.let { sdk=it.second;vm.recoveredImport.value=null } }
    Panel("Captura, hash e revisão de evidências") {
     runs.forEach { r -> TextButton(onClick={ selectRun(r.getString("id"));stage=EvidenceStage.UNVERIFIED;physical=false }) { Text("${if(runId==r.getString("id")) "●" else "○"} ${r.getString("id").take(8)} • ${r.getJSONObject("terminalSnapshot").optString("model")}") } }
     Text("Execução: ${vm.selectedRun}")
     Cycle("Etapa declarada",stage,EvidenceStage.entries) { stage=it }
     TextButton(onClick={ val all=listOf("INCONCLUSIVE","OBSERVED","PASS","FAIL");outcome=all[(all.indexOf(outcome)+1)%all.size] }) { Text("Resultado declarado: $outcome") }
     Field(notes,{ notes=it },"Observações sem segredos / condições / limitações")
     Row { Checkbox(checked=physical,onCheckedChange={ physical=it });Text("Atesto execução presencial com equipamentos identificados; não é simulação") }
     Button(enabled=!busy && vm.selectedRun.isNotBlank(),onClick={ vm.work { vm.ledger.observe(activeRun,stage,outcome,notes,physical);"Observação declarada, ainda UNVERIFIED até anexos/revisão" } }) { Text("Salvar observação") }
     Cycle("Tipo de anexo",role,ArtifactRole.entries) { role=it }
     Row { Checkbox(checked=authorized,onCheckedChange={ authorized=it });Text("Arquivo autorizado e já redigido, sem PAN/PIN/trilhas/chaves") }
     Button(enabled=!busy && authorized && vm.selectedRun.isNotBlank(),onClick={ attach(vm.selectedRun,role,authorized) }) { Text("Anexar arquivo real por SAF") }
     Text("Hash confirma bytes, não autenticidade nem conteúdo. Anexo importado nunca vira captura do app automaticamente.")
     Field(sdk,{ sdk=it },"Resumo SDK JSON; authenticationResult obrigatório para etapa de autenticação")
     Text("result PASS/FAIL é veredito do caso. authenticationResult VALID/INVALID/NOT_PERFORMED é separado; nenhum deles é verificado criptograficamente pelo app.")
     TextButton(onClick={ importText("sdk") { sdk=it } }) { Text("Importar resumo SDK autorizado") }
     Button(enabled=!busy && vm.selectedRun.isNotBlank(),onClick={ vm.sdk(sdk) }) { Text("Associar resultado declarado pelo SDK") }
     var terminalDecision by remember { mutableStateOf("UNKNOWN") };var hostDecision by remember { mutableStateOf("UNKNOWN") };var correlation by remember { mutableStateOf("") }
     TextButton(onClick={ val v=listOf("UNKNOWN","APPROVED","DECLINED","NOT_REQUESTED");terminalDecision=v[(v.indexOf(terminalDecision)+1)%v.size] }) { Text("Resultado terminal: $terminalDecision") }
     TextButton(onClick={ val v=listOf("UNKNOWN","APPROVED","DECLINED","NOT_REQUESTED");hostDecision=v[(v.indexOf(hostDecision)+1)%v.size] }) { Text("Processador de homologação: $hostDecision") }
     Field(correlation,{ correlation=it },"Referência de correlação do host (sem segredos)")
     Button(enabled=!busy && vm.selectedRun.isNotBlank(),onClick={ vm.work { vm.ledger.decisions(activeRun,terminalDecision,hostDecision,correlation);"Resultados separados registrados; divergências serão sinalizadas" } }) { Text("Registrar terminal / processador") }
     Field(reviewer,{ reviewer=it },"Revisor Blue distinto do operador");Field(reviewRef,{ reviewRef=it },"Referência da revisão e correlação dos traces")
     Button(enabled=!busy && vm.selectedRun.isNotBlank(),onClick={ vm.work { vm.ledger.review(activeRun,reviewer,reviewRef);"Revisão documental registrada; não é aprovação financeira ou atestado criptográfico" } }) { Text("Revisar evidências") }
     Button(enabled=!busy && vm.selectedRun.isNotBlank(),onClick={ vm.work { vm.output.value=vm.ledger.runReport(activeRun).toString(2);"Validação e hashes recalculados" } }) { Text("Verificar anexos e etapa efetiva") }
     state.getJSONArray("artifacts").objects().filter { it.getString("runId")==vm.selectedRun }.forEach { a -> Text("${a.getString("role")} • ${a.getLong("size")} bytes • SHA256 ${a.getString("sha256")}") }
    }
    if(output.isNotBlank()) SelectionContainer { Text(if(output.length>65536) output.take(65536)+"\n[Prévia limitada; exportação mantém conteúdo completo]" else output) }
   }
   "Red/Blue Comparison" -> {
    var before by remember { mutableStateOf("") };var after by remember { mutableStateOf("") }
    Panel("Antes/depois • comparação sem conclusão automática de correção") {
     runs.forEach { r -> TextButton(onClick={ if(r.getString("team")=="RED") before=r.getString("id") else after=r.getString("id") }) { Text("${r.getString("team")} ${r.getString("id").take(8)} • ${r.getJSONObject("caseSnapshot").getString("id")}") } }
     Text("Red $before • Blue $after")
     Button(enabled=!busy && before.isNotBlank() && after.isNotBlank(),onClick={ vm.work { vm.output.value=vm.ledger.comparison(before,after).toString(2);"Comparação calculada; interpretação e causalidade exigem revisão" } }) { Text("Comparar contexto, evidências e resultados") }
     if(output.isNotBlank()) { SelectionContainer { Text(if(output.length>65536) output.take(65536)+"\n[Prévia limitada; exportação mantém conteúdo completo]" else output) };TextButton(onClick={ export("chimera-red-blue.json","application/json",output) }) { Text("Exportar comparação") } }
    }
   }
   "Technical Reports" -> Panel("Relatórios reproduzíveis / pacote de evidências") {
    var reviewed by remember { mutableStateOf(false) }
    Row { Checkbox(reviewed,{ reviewed=it });Text("Revisei notas, nomes e anexos; autorizo a exportação destes dados") }
    Text("JSON inclui snapshots, análises sanitizadas, resultados, hashes e journal. CSV/Markdown distinguem UNVERIFIED. ZIP contém somente anexos que você autorizou importar; revise antes de compartilhar.")
    Button(enabled=!busy && reviewed,onClick={ vm.work { export("chimera-report.json","application/json",vm.exportText(false));"Relatório JSON preparado" } }) { Text("Exportar JSON") }
    Button(enabled=!busy && reviewed,onClick={ vm.work { export("chimera-report.md","text/markdown",vm.exportText(true));"Relatório Markdown preparado" } }) { Text("Exportar Markdown") }
    Button(enabled=!busy && reviewed,onClick={ vm.work { export("chimera-report.csv","text/csv",vm.exportCsv());"Relatório CSV preparado" } }) { Text("Exportar CSV") }
    Button(enabled=!busy && reviewed,onClick=exportZip) { Text("Exportar ZIP com anexos e manifesto") }
    Text("Planos ${runs.size} • análises ${state.getJSONArray("analyses").length()} • anexos ${state.getJSONArray("artifacts").length()}. A contagem não representa testes físicos aprovados.")
   }
  }
 }
}
