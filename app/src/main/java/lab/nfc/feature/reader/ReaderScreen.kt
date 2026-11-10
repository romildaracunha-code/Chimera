package lab.nfc.feature.reader
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

@Composable fun ReaderScreen(caps: Capabilities, busy: Boolean, onReader: (String)->Unit, onStopReader: ()->Unit) {
var custom by remember { mutableStateOf(LabProtocol.GET_DATA) }
Panel("Leitor ISO-DEP autorizado") {
 Text("Use apenas tags próprias ou autorizadas. SELECT do NFC Lab será enviado primeiro; o comando seguinte só é enviado se SELECT retornar 9000.")
 OutlinedTextField(custom,{ custom=it },label={ Text("APDU ISO 7816 hex; extensão depende do hardware") },modifier=Modifier.fillMaxWidth(),textStyle=LocalTextStyle.current.copy(fontFamily=FontFamily.Monospace))
 Button(onClick={ onReader(custom) },enabled=caps.enabled && !busy) { Text("Ativar leitor / teste físico") }
 OutlinedButton(onClick=onStopReader) { Text("Desativar leitor") }
 Text("Aproxime o segundo celular com HCE armado. As tecnologias e erros serão salvos; tags sem ISO-DEP também geram histórico.")
 }
}
