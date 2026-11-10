package lab.nfc.core.storage;
import androidx.annotation.NonNull;
import androidx.room.Entity;
@Entity(tableName="vault_chunks", primaryKeys={"recordKey","part"})
public class VaultRow {
 @NonNull public String recordKey="";
 public int part;
 @NonNull public byte[] payload=new byte[0];
 public VaultRow(@NonNull String recordKey,int part,@NonNull byte[] payload) { this.recordKey=recordKey;this.part=part;this.payload=payload; }
}
