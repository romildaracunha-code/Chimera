package lab.nfc.feature.shared
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
@Composable fun NfcLabTheme(content:@Composable ()->Unit) {
 MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF29D8FF),secondary=Color(0xFF418BFF),background=Color(0xFF0B0F16),surface=Color(0xFF151D29)),content=content)
}
