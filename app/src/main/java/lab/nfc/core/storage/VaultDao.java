package lab.nfc.core.storage;
import androidx.room.*;
import java.util.List;
@Dao
public abstract class VaultDao {
 @Query("SELECT * FROM vault_chunks WHERE recordKey=:key ORDER BY part") public abstract List<VaultRow> read(String key);
 @Query("SELECT DISTINCT recordKey FROM vault_chunks ORDER BY recordKey") public abstract List<String> keys();
 @Query("SELECT DISTINCT recordKey FROM vault_chunks WHERE recordKey >= :prefix AND recordKey < :end ORDER BY recordKey DESC LIMIT :limit OFFSET :offset") public abstract List<String> pageKeys(String prefix,String end,int limit,int offset);
 @Query("SELECT COUNT(DISTINCT recordKey) FROM vault_chunks WHERE recordKey >= :prefix AND recordKey < :end") public abstract int countKeys(String prefix,String end);
 @Query("DELETE FROM vault_chunks WHERE recordKey=:key") public abstract void delete(String key);
 @Insert public abstract void insert(List<VaultRow> rows);
 @Transaction public void replace(String key,List<VaultRow> rows) { delete(key);insert(rows); }
}
