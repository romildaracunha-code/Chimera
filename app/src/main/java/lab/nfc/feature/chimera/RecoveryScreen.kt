package lab.nfc.feature.chimera

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import lab.nfc.core.storage.*

/** This screen never opens/deletes the inaccessible database. Recovery commits a new vault pointer last. */
@Composable fun RecoveryScreen(reason:String,onRecovered:()->Unit) {
 val context=LocalContext.current;val scope=rememberCoroutineScope()
 var password by remember { mutableStateOf("") };var busy by remember { mutableStateOf(false) }
 var status by remember { mutableStateOf(reason) };var staged by remember { mutableStateOf<Map<String,ByteArray>?>(null) }
 var confirmed by remember { mutableStateOf(false) }
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  if(uri!=null) { val pass=password.toCharArray();password="";busy=true
   scope.launch {
    try {
     val records=withContext(Dispatchers.IO) {
      try {
       val bytes=context.contentResolver.openInputStream(uri)?.use { input ->
        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true) { val n=input.read(buffer);if(n<0) break;require(out.size()+n<=100*1024*1024) { "Backup excede 100 MiB" };out.write(buffer,0,n) };out.toByteArray()
       } ?: error("Backup indisponível")
       PortableBackup.unpack(bytes,pass)
      } finally { pass.fill('\u0000') }
     }
     staged=records;confirmed=false;status="Backup autenticado e validado. O banco original continuará preservado."
    } catch(e:Exception) { status="Falha ao validar: ${e.javaClass.simpleName}. Dados originais preservados." }
    finally { busy=false }
   }
  }
 }
 lab.nfc.feature.shared.NfcLabTheme {
  Surface(Modifier.fillMaxSize()) {
   Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    Text("NFC Lab • recuperação de armazenamento",style=MaterialTheme.typography.headlineSmall)
    Text("Não desinstale nem limpe os dados. Se a chave antiga estiver indisponível, somente um backup portátil com senha válida permite recuperar seu conteúdo. O aplicativo não recria a chave sobre o banco original.")
    Text(status)
    OutlinedTextField(password,{ if(it.length<=256) password=it },label={ Text("Senha do backup portátil") },visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=!busy)
    Button(enabled=!busy && password.isNotEmpty(),onClick={ staged=null;picker.launch(arrayOf("application/octet-stream","*/*")) }) { Text("Abrir e validar backup") }
    staged?.let { records ->
     Text("Prévia: ${records.size} registros, ${records.values.sumOf { it.size.toLong() }} bytes. Será criado um banco separado e uma nova chave; a seleção ativa muda somente após conferência da gravação.")
     Row { Checkbox(confirmed,{ confirmed=it },enabled=!busy);Text("Quero usar este backup como workspace ativo, preservando o original") }
     Button(enabled=!busy && confirmed,onClick={ busy=true;scope.launch {
      try { withContext(Dispatchers.IO) { SecureRepository.recoverFromBackup(context,records) };onRecovered() }
      catch(e:Exception) { status="Recuperação bloqueada: ${e.javaClass.simpleName}. Banco original preservado." }
      finally { busy=false }
     } }) { Text("Recuperar em banco separado") }
    }
    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
   }
  }
 }
}
