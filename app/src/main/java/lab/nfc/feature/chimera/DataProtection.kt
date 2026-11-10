package lab.nfc.feature.chimera

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lab.nfc.LabViewModel
import lab.nfc.core.chimera.objects
import lab.nfc.core.storage.*
import lab.nfc.feature.shared.Panel
import java.io.File

@Composable fun DataProtection(vm:ChimeraViewModel,legacy:LabViewModel) {
 val context=LocalContext.current
 val busy by vm.busy.collectAsStateWithLifecycle();val state by vm.state.collectAsStateWithLifecycle()
 var password by remember { mutableStateOf("") };var staged by remember { mutableStateOf<Map<String,ByteArray>?>(null) }
 var confirmed by remember { mutableStateOf(false) };var preparedPath by rememberSaveable { mutableStateOf<String?>(null) }
 val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
  val file=preparedPath?.let { File(it) }?.takeIf { it.canonicalFile.parentFile==context.cacheDir.canonicalFile };preparedPath=null
  if(uri!=null && file!=null) vm.work { try { context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("Destino indisponível");"Backup criptografado exportado; guarde a senha separadamente" } finally { file.delete() } } else file?.delete()
 }
 val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  if(uri!=null) { val pass=password.toCharArray();password="";vm.work {
   val bytes=context.contentResolver.openInputStream(uri)?.use { input ->
    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true) { val n=input.read(buffer);if(n<0) break;require(out.size()+n<=100*1024*1024);out.write(buffer,0,n) };out.toByteArray()
   } ?: error("Backup indisponível")
   val records=vm.validateRestore(bytes,pass);Handler(Looper.getMainLooper()).post { staged=records;confirmed=false };"Backup autenticado e validado; revisão antes de substituir os dados"
  } }
 }
 Panel("Dados locais, backup e recuperação") {
  Text("Registros e anexos: Room + AES-GCM, chave Android Keystore. Não exige biometria para permitir HCE. Não presume chave protegida por hardware. O backup portátil usa senha independente (mínimo 12 caracteres); ela não é salva.")
  OutlinedTextField(password,{ if(it.length<=256) password=it },label={ Text("Senha do backup portátil") },visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),singleLine=true)
  Button(enabled=!busy && password.length>=12,onClick={ val pass=password.toCharArray();password="";vm.work { val file=vm.backup(pass);Handler(Looper.getMainLooper()).post { preparedPath=file.absolutePath;save.launch("NFC-Lab-backup.nfcb") };"Backup criptografado preparado" } }) { Text("Criar backup criptografado completo") }
  Button(enabled=!busy && password.isNotEmpty(),onClick={ staged=null;restore.launch(arrayOf("application/octet-stream","*/*")) }) { Text("Abrir e validar backup para restauração") }
  staged?.let { records ->
   Text("Prévia: ${records.size} registros; ${records.values.sumOf { it.size.toLong() }} bytes. A restauração substitui todo o workspace local e encerra o fluxo atual. Nenhuma importação eleva o status físico dos dados.")
   Row { Checkbox(confirmed,{ confirmed=it });Text("Revisei a prévia e quero substituir o workspace; leitor e HCE encerrados") }
   Button(enabled=!busy && confirmed && legacy.store.activeHce==null && !legacy.busy.value,onClick={ vm.work {
    legacy.stopHce();vm.flushDraft();vm.repository.saveRestorePoint();vm.restore(records)
    Handler(Looper.getMainLooper()).post {
     WorkspaceBootstrap.reset();LabStore.reset()
     val intent=context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
     context.startActivity(intent);(context as? Activity)?.finish()
    };"Workspace restaurado; reiniciando interface"
   } }) { Text("Restaurar atomicamente e reabrir") }
  }
  TextButton(enabled=!busy && vm.repository.hasRestorePoint(),onClick={ vm.work {
   val records=vm.repository.restorePoint();Handler(Looper.getMainLooper()).post { staged=records;confirmed=false };"Ponto local autenticado; confirme a substituição na prévia"
  } }) { Text("Revisar ponto de recuperação anterior") }
  Text("Um ponto de recuperação criptografado é salvo antes da substituição. Se o seletor de arquivos for interrompido, repita a operação; a senha precisa ser informada novamente.")
 }
 Panel("Retenção e revisão de exportação") {
  var automaticConsent by remember { mutableStateOf(false) }
  var schedulingStatus by remember { mutableStateOf("Consulte ou configure o agendamento Android") }
  Text(schedulingStatus)
  TextButton(enabled=!busy,onClick={ vm.work {
   val policy=RetentionScheduler.policy(vm.repository);Handler(Looper.getMainLooper()).post { schedulingStatus="Retenção automática: ${policy.getBoolean("enabled")} • ${policy.getInt("days")} dias" };"Política consultada"
  } }) { Text("Consultar política automática") }
  Row { Checkbox(automaticConsent,{ automaticConsent=it });Text("Autorizo exclusão automática de anexos e análises antigos, invalidando revisões; execuções e journal são mantidos") }
  var retentionDays by remember { mutableStateOf("90") };var retentionPreview by remember { mutableStateOf<org.json.JSONObject?>(null) }
  OutlinedTextField(retentionDays,{ retentionDays=it.take(4);retentionPreview=null },label={ Text("Retenção manual: dias (1–3650)") },singleLine=true)
  Button(enabled=!busy && automaticConsent,onClick={ val days=retentionDays.toIntOrNull();vm.work {
   RetentionScheduler.configure(context,vm.repository,true,days ?: error("Informe dias válidos"));Handler(Looper.getMainLooper()).post { schedulingStatus="Retenção automática configurada: $days dias; Android agenda quando ocioso e carregando" };"Agendamento periódico solicitado ao Android"
  } }) { Text("Ativar retenção automática com este prazo") }
  TextButton(enabled=!busy,onClick={ vm.work { RetentionScheduler.configure(context,vm.repository,false,90);Handler(Looper.getMainLooper()).post { schedulingStatus="Retenção automática desativada" };"Agendamento cancelado" } }) { Text("Desativar retenção automática") }
  TextButton(enabled=!busy,onClick={ val days=retentionDays.toIntOrNull();vm.work {
   val plan=vm.ledger.retentionPreview(days ?: error("Informe dias válidos"));Handler(Looper.getMainLooper()).post { retentionPreview=plan };"Prévia de retenção calculada; nada removido"
  } }) { Text("Pré-visualizar retenção") }
  retentionPreview?.let { preview ->
   Text("Remover ${preview.getJSONArray("analyses").length()} análises e ${preview.getJSONArray("artifacts").length()} anexos antigos; manter execuções e journal. Exclusão invalida revisões.")
   Button(enabled=!busy,onClick={ val plan=org.json.JSONObject(preview.toString());retentionPreview=null;vm.work { vm.ledger.applyRetention(plan);"Retenção aplicada; revisões afetadas invalidadas" } }) { Text("Confirmar retenção e exclusões da prévia") }
  }
  Text("A exclusão é explícita e registrada no journal. Remover um anexo invalida a revisão associada. Exclusão lógica não garante apagamento físico em memória flash.")
  var deleteId by remember { mutableStateOf("") }
  state.getJSONArray("artifacts").objects().forEach { a ->
   Text("${a.optString("name")} • ${a.optString("role")} • ${a.optLong("size")} bytes")
   TextButton(enabled=!busy,onClick={ deleteId=a.getString("id") }) { Text("Revisar exclusão deste anexo") }
  }
  state.getJSONArray("analyses").objects().takeLast(20).forEach { a -> TextButton(enabled=!busy,onClick={ val id=a.getString("id");vm.work { vm.ledger.deleteAnalysis(id);"Análise sanitizada removida; evento de exclusão mantido" } }) { Text("Excluir análise ${a.getString("id").take(8)}") } }
  if(deleteId.isNotBlank()) {
   Text("Excluir anexo $deleteId e invalidar a revisão? O registro de exclusão permanecerá no journal.")
   Button(enabled=!busy,onClick={ val id=deleteId;deleteId="";vm.work { vm.ledger.deleteArtifact(id);"Anexo excluído e revisão invalidada" } }) { Text("Confirmar exclusão do anexo selecionado") }
   TextButton(onClick={ deleteId="" }) { Text("Cancelar") }
  }
  Text("Antes de exportar relatórios/ZIP, confira nomes, notas e anexos. Análises omitem valores sensíveis conhecidos; o app não faz revisão automática do conteúdo de fotos/PDFs ou texto livre.")
 }
}
