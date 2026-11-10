package lab.nfc.core.storage;
import androidx.room.*;
import java.util.List;
@Dao
public abstract class VaultDao {
 @Query("SELECT * FROM vault_chunks WHERE recordKey=:key ORDER BY part") public abstract List<VaultRow> read(String key);
 @Query("SELECT DISTINCT recordKey FROM vault_chunks ORDER BY recordKey") public abstract List<String> keys();
 @Query("DELETE FROM vault_chunks WHERE recordKey=:key") public abstract void delete(String key);
 @Insert public abstract void insert(List<VaultRow> rows);
 @Transaction public void replace(String key,List<VaultRow> rows) { delete(key);insert(rows); }
}
