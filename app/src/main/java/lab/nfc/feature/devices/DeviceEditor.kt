package lab.nfc.feature.devices
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

@Composable fun DeviceEditor(vm: LabViewModel) {
 val labels=listOf("Fabricante","Modelo / variante","Identificação interna","Firmware (ou desconhecido)","Suporte NFC declarado","SDK / versão e acesso confirmado","Observações","Adquirente / distribuidor","Aplicativo de pagamento / versão","Kernel contactless / versão e fonte","Homologação (não confirmada por padrão)","Referências: foto de etiqueta / menu / documento","Contato ou ticket de acesso (sem segredos)")
 val values=remember { mutableStateListOf("","","","Desconhecido","Desconhecido","Não confirmado","","Desconhecida","Desconhecido","Desconhecido","Não confirmada","","") }
 val devices by vm.store.devices.collectAsStateWithLifecycle()
 Panel("Inventário físico / carregar ficha") {
 Text("Somente informações observadas em etiqueta/menu normal ou documento autorizado. Não presuma acesso administrativo. Atualizar pelo mesmo ID interno preserva a identificação.")
 devices.forEach { d -> TextButton(onClick={ val fields=listOf(d.manufacturer,d.model,d.internalId,d.firmware,d.nfc,d.sdk,d.notes,d.acquirer,d.paymentApp,d.contactlessKernel,d.homologation,d.evidenceReference,d.accessContact); fields.forEachIndexed { i,v -> values[i]=v } }) { Text("Carregar ${d.internalId}") } }
 labels.forEachIndexed { i,label -> OutlinedTextField(values[i],{ values[i]=it },label={ Text(label) },modifier=Modifier.fillMaxWidth()) }
 Button(onClick={ if(values[0].isBlank() || values[1].isBlank() || values[2].isBlank()) vm.message.value="Informe fabricante, modelo e ID interno; use Desconhecido se ainda não observado" else { vm.store.addDevice(DeviceRecord(values[0],values[1],values[2],values[3],values[4],values[5],values[6],values[7],values[8],values[9],values[10],values[11],values[12])); vm.message.value="Inventário salvo; dados declarados, não homologação automática" } }) { Text("Salvar / atualizar dispositivo") }
 }
}
