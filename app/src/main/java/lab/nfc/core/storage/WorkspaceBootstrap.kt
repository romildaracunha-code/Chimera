package lab.nfc.core.storage
import android.content.Context
import lab.nfc.core.chimera.ChimeraLedger
import org.json.JSONArray
import java.io.File
/** Preload/migrate off the UI thread before exposing operational controls. */
object WorkspaceBootstrap {
 @Volatile private var ledger:ChimeraLedger?=null
 fun reset() { synchronized(this) { ledger=null } }
 fun prepare(context:Context) { LabStore.get(context);getLedger(context) }
 fun getLedger(context:Context):ChimeraLedger=ledger ?: synchronized(this) {
  ledger ?: ChimeraLedger(File(context.filesDir,"chimera-v3"),JSONArray(context.assets.open("chimera/cases.json").bufferedReader().use { it.readText() }),VaultLedgerRecords(SecureRepository.get(context),File(context.filesDir,"chimera-v3"))).also {
   // Eagerly migrate every referenced artifact before offering a portable backup.
   it.snapshot().getJSONArray("artifacts").let { a -> for(i in 0 until a.length()) it.artifactBytes(a.getJSONObject(i).getString("id")) }
   ledger=it
  }
 }
}
