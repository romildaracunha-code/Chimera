package lab.nfc.core.chimera
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EvidenceBundle {
 fun write(ledger:ChimeraLedger,out:File):File { out.outputStream().use { write(ledger,it) };return out }
 fun write(ledger:ChimeraLedger,out:java.io.OutputStream) {
  val snapshot=ledger.export()
  require(ledger.integrityError==null) { "Ledger sem integridade; pacote de evidência bloqueado" }
  snapshot.getJSONArray("runs").objects().forEach { require(ledger.artifactIssues(it.getString("id")).isEmpty()) { "Anexo ausente/alterado" } }
  ZipOutputStream(out).use { z ->
   z.putNextEntry(ZipEntry("manifest.json"));z.write(snapshot.toString(2).toByteArray());z.closeEntry()
   snapshot.getJSONArray("artifacts").objects().forEach { a ->
    z.putNextEntry(ZipEntry("artifacts/${a.getString("id")}.bin"));z.write(ledger.artifactBytes(a.getString("id")));z.closeEntry()
   }
  }
 }
}
