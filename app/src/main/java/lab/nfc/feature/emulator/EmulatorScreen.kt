package lab.nfc.feature.emulator
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.content.ComponentName
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lab.nfc.core.apdu.*
import lab.nfc.core.hce.LabHceService
import lab.nfc.core.nfc.*
import lab.nfc.core.storage.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


import lab.nfc.*
import lab.nfc.feature.shared.*

@Composable fun EmulatorScreen(vm: LabViewModel, caps: Capabilities, profiles: List<String>, profile: String, onHce: ()->Unit) {
var profileName by remember { mutableStateOf("") }
Panel("Perfis fictícios de laboratório") {
 profiles.forEach { name -> Row { RadioButton(selected=profile==name,onClick={ vm.select(name) }); Text(name,Modifier.padding(top=12.dp)) } }
 OutlinedTextField(profileName,{ profileName=it },label={ Text("Novo perfil (ASCII, até 32)") },modifier=Modifier.fillMaxWidth())
 Button(onClick={ runCatching { vm.store.addProfile(profileName); vm.select(profileName); profileName="" }.onFailure { vm.message.value="Use letras, números, _ ou - (1–32)" } }) { Text("Criar perfil fictício") }
 var aid by remember { mutableStateOf(vm.store.selectedAid) }
 var reject by remember { mutableStateOf(vm.store.rejectLabGetData) }
 Text("AID proprietário selecionado: $aid",fontFamily=FontFamily.Monospace)
 LabAids.all.forEach { candidate -> TextButton(onClick={ aid=candidate;vm.store.selectedAid=candidate }) { Text("${if(aid==candidate) "●" else "○"} $candidate") } }
 Row { Checkbox(checked=reject,onCheckedChange={ reject=it;vm.store.rejectLabGetData=it });Text("Caso negativo fictício: GET DATA retorna 6985") }
 Text("AID e comportamento são fixados ao iniciar cada sessão; não são credenciais EMV.")
 Text("Requer outro leitor NFC. Apenas armar a sessão não comprova comunicação física.")
 Button(onClick=onHce,enabled=caps.hce && caps.enabled) { Text("Iniciar sessão HCE") }
 OutlinedButton(onClick={ vm.stopHce() }) { Text("Encerrar sessão HCE") }
 Text("O perfil fica fixo durante cada sessão. O aparelho deve permanecer desbloqueado.")
 }
}
