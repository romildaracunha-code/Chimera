package lab.nfc.feature.terminal
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

@Composable fun TerminalScreen(vm: LabViewModel, navigate: (String)->Unit) {
Panel("Terminal experimental") {
 Text("SIMULAÇÃO LOCAL • processamento lógico no aparelho, sem transmissão NFC.")
 Button(onClick={ vm.simulate() }) { Text("Executar SELECT + GET DATA local") }
 HorizontalDivider()
 Text("TESTE FÍSICO • dois aparelhos Android; um em Emulator e outro em Reader.")
 Button(onClick={ navigate("Reader") }) { Text("Usar este aparelho como leitor") }
 OutlinedButton(onClick={ navigate("Emulator") }) { Text("Usar como emulador HCE") }
 }
}
