package lab.nfc.core.storage
import lab.nfc.core.chimera.LedgerRecords
import java.io.File
class VaultLedgerRecords(private val repo:SecureRepository,private val legacy:File):LedgerRecords {
 private val generation=repo.generation
 override fun read(name:String):ByteArray? { require(name=="workspace-v3.json" || name.matches(Regex("[a-f0-9-]{36}\\.bin")));return repo.migrate(File(legacy,name),"chimera/$name") }
 override fun write(name:String,value:ByteArray)=repo.write("chimera/$name",value,generation)
 override fun delete(name:String)=repo.delete("chimera/$name",generation)
}
