package lab.nfc.core.chimera
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EvidenceBundle {
 fun write(ledger:ChimeraLedger,out:File):File {
  val snapshot=ledger.export()
  require(ledger.integrityError==null) { "Ledger sem integridade; pacote de evidência bloqueado" }
  snapshot.getJSONArray("runs").objects().forEach { require(ledger.artifactIssues(it.getString("id")).isEmpty()) { "Anexo ausente/alterado" } }
  ZipOutputStream(out.outputStream()).use { z ->
   z.putNextEntry(ZipEntry("manifest.json"));z.write(snapshot.toString(2).toByteArray());z.closeEntry()
   snapshot.getJSONArray("artifacts").objects().forEach { a ->
    z.putNextEntry(ZipEntry("artifacts/${a.getString("id")}.bin"));ledger.artifactFile(a.getString("id")).inputStream().use { it.copyTo(z) };z.closeEntry()
   }
  };return out
 }
}
