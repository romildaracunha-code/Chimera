package lab.nfc.feature.chimera
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import lab.nfc.core.nfc.DeviceDiagnostics
import lab.nfc.feature.shared.Panel

@Composable fun DiagnosticsScreen(export:(String,String,String)->Unit) {
 val context=LocalContext.current
 var report by remember { mutableStateOf(DeviceDiagnostics.capture(context)) }
 Panel("Diagnóstico do celular • informações do Android") {
  Text("Este diagnóstico lê o Android em que o aplicativo está aberto. Recursos declarados pelo sistema não comprovam troca física NFC. Não coleta PAN, serial, Android ID, chaves ou credenciais.")
  SelectionContainer { Text(report.toString(2)) }
  TextButton(onClick={ report=DeviceDiagnostics.capture(context) }) { Text("Atualizar capacidades") }
  Button(onClick={ export("nfc-device-diagnostics.json","application/json",report.toString(2)) }) { Text("Exportar diagnóstico do aparelho") }
 }
}
