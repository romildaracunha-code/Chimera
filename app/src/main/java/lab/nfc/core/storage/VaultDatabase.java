package lab.nfc.core.storage;
import androidx.room.*;
@Database(entities={VaultRow.class},version=1,exportSchema=true)
public abstract class VaultDatabase extends RoomDatabase { public abstract VaultDao vault(); }
