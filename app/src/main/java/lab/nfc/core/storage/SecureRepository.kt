package lab.nfc.core.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.Room
import lab.nfc.core.security.SecureCodec
import java.io.File
import java.security.KeyStore
import java.util.concurrent.Executors
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Room transactions store authenticated, bounded chunks. No destructive migration fallback. */
class SecureRepository internal constructor(context:Context,private val injectedKey:SecretKey?=null,databaseName:String="nfclab-secure.db",private val keyAlias:String="nfclab.storage.v1",private val isolated:Boolean=false) {
 @Volatile var generation:Long=0;private set
 private val restorePoint=File(context.filesDir,if(databaseName=="nfclab-secure.db") "restore-point.enc" else "$databaseName.restore-point.enc")
 @Volatile private var workerThread:Thread?=null
 @Volatile private var closed=false
 private val worker=Executors.newSingleThreadExecutor { task -> Thread(task,"nfclab-vault").also { workerThread=it } }
 private val db=Room.databaseBuilder(context,VaultDatabase::class.java,databaseName).build()
 private val key:SecretKey by lazy {
  injectedKey ?: run {
  val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  (store.getKey(keyAlias,null) as? SecretKey) ?: run {
   // Existing ciphertext with a missing key must never be silently replaced.
   check(db.vault().keys().isEmpty()) { "Chave Keystore ausente; restaure backup portátil, não limpe os dados" }
   KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
    init(KeyGenParameterSpec.Builder(keyAlias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
     .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
   }.generateKey()
  }
 }
 }
 private fun <T> io(body:()->T):T=if(Thread.currentThread()===workerThread) body() else worker.submit<T> { body() }.get()
 fun read(name:String):ByteArray?=io { readInternal(name) }
 private fun readInternal(name:String):ByteArray? {
  val rows=db.vault().read(name);if(rows.isEmpty()) return null
  require(rows.withIndex().all { it.index==it.value.part }) { "Registro incompleto" }
  return SecureCodec.open(java.io.ByteArrayOutputStream().apply { rows.forEach { write(it.payload) } }.toByteArray(),key,name)
 }
 fun write(name:String,bytes:ByteArray,expectedGeneration:Long?=null)=io { if(expectedGeneration!=null) check(expectedGeneration==generation) { "Workspace substituído; reabra a tela" };writeInternal(name,bytes) }
 private fun writeInternal(name:String,bytes:ByteArray) {
  require(bytes.size<=20*1024*1024) { "Registro excede 20 MiB" }
  val sealed=SecureCodec.seal(bytes,key,name)
  db.vault().replace(name,(sealed.indices step 65536).mapIndexed { i,start -> VaultRow(name,i,sealed.copyOfRange(start,minOf(start+65536,sealed.size))) })
 }
 /** Import verifies read-back before removing the old plaintext. Failed imports preserve originals. */
 fun migrate(file:File,name:String):ByteArray?=io {
  if(isolated) return@io readInternal(name)
  val existing=readInternal(name)
  if(existing!=null) { if(file.exists()) { require(file.readBytes().contentEquals(existing)) { "Legado diverge do Room; ambos preservados" };check(file.delete()) };return@io existing }
  if(!file.exists()) return@io null
  require(file.length()<=20*1024*1024);val original=file.readBytes()
  writeInternal(name,original);check(readInternal(name)!!.contentEquals(original));check(file.delete()) { "Migração salva; não foi possível remover legado" };original
 }
 fun pageKeys(prefix:String,limit:Int,offset:Int):List<String> =io { require(limit in 1..500 && offset>=0);db.vault().pageKeys(prefix,prefix+'\uffff',limit,offset) }
 fun countKeys(prefix:String):Int=io { db.vault().countKeys(prefix,prefix+'\uffff') }
 fun writeBatch(records:Map<String,ByteArray>,deleted:List<String> = emptyList(),expectedGeneration:Long=generation)=io {
  check(expectedGeneration==generation) { "Workspace substituído" }
  db.runInTransaction { records.forEach { (name,value) -> writeInternal(name,value) };deleted.forEach { db.vault().delete(it) } }
 }
 @Synchronized fun close() { if(!closed) { io { db.close() };worker.shutdown();closed=true } }
 fun <T> consistentRead(body:()->T):T=io { db.runInTransaction(java.util.concurrent.Callable { body() }) }
 fun keys():List<String> =io { db.vault().keys() }
 fun delete(name:String,expectedGeneration:Long?=null)=io { if(expectedGeneration!=null) check(expectedGeneration==generation) { "Workspace substituído" };db.vault().delete(name) }
 /** All authenticated records validated before an atomic database replacement. */
 fun snapshot():Map<String,ByteArray> =io { db.vault().keys().associateWith { readInternal(it)!! } }
 fun replace(records:Map<String,ByteArray>)=io {
  require(records.size<=10000 && records.values.sumOf { it.size.toLong() }<=96L*1024*1024)
  db.runInTransaction {
   db.vault().keys().forEach { db.vault().delete(it) }
   records.forEach { (name,value) -> writeInternal(name,value) }
  };generation++
 }
 fun sealFile(file:File,writer:(java.io.OutputStream)->Unit) {
  val material=io { key };try { file.outputStream().use { raw -> SecureCodec.sealingStream(raw,material,"cache/${file.name}").use(writer) } } catch(e:Exception) { file.delete();throw e }
 }
 fun openSealedFile(file:File):java.io.InputStream { val material=io { key };return SecureCodec.openingStream(file.inputStream(),material,"cache/${file.name}") }
 fun saveRestorePoint() { val plain=PortableBackup.encode(snapshot());io {
  val tmp=File(restorePoint.parentFile,"restore-point.tmp");tmp.outputStream().use { it.write(SecureCodec.seal(plain,key,"restore-point-v1"));it.fd.sync() }
  java.nio.file.Files.move(tmp.toPath(),restorePoint.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
 } }
 fun hasRestorePoint()=restorePoint.exists()
 fun restorePoint():Map<String,ByteArray> =io { PortableBackup.decode(SecureCodec.open(restorePoint.readBytes(),key,"restore-point-v1")) }
 companion object {
  @Volatile private var instance:SecureRepository?=null
  internal fun configured(context:Context,testingKey:SecretKey?=null):SecureRepository {
   val pointer=android.util.AtomicFile(File(context.filesDir,"active-vault.json"))
   if(!pointer.baseFile.exists() && !File(context.filesDir,"active-vault.json.bak").exists()) return SecureRepository(context,testingKey)
   val value=org.json.JSONObject(pointer.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
   val name=value.getString("database");val alias=value.getString("keyAlias")
   require(name.matches(Regex("nfclab-recovered-[a-f0-9-]{36}\\.db")) && alias=="nfclab.storage.recovery."+name.removePrefix("nfclab-recovered-").removeSuffix(".db")) { "Ponteiro de recuperação inválido; original preservado" }
   return SecureRepository(context,testingKey,name,alias,true)
  }
  /** Recovery builds a separate database and key; the inaccessible original is never overwritten. */
  internal fun recover(context:Context,records:Map<String,ByteArray>,factory:((String,String)->SecureRepository)?=null):SecureRepository = synchronized(this) {
   PortableBackup.validate(records)
   val id=java.util.UUID.randomUUID().toString();val name="nfclab-recovered-$id.db";val alias="nfclab.storage.recovery.$id"
   val candidate=factory?.invoke(name,alias) ?: SecureRepository(context,null,name,alias,true)
   try {
    candidate.replace(records)
    val check=candidate.snapshot();require(check.keys==records.keys && records.all { (key,value) -> check.getValue(key).contentEquals(value) }) { "Falha na conferência do banco recuperado" }
    val pointer=android.util.AtomicFile(File(context.filesDir,"active-vault.json"));val stream=pointer.startWrite()
    try { stream.write(org.json.JSONObject().put("database",name).put("keyAlias",alias).toString().toByteArray());pointer.finishWrite(stream) } catch(e:Exception) { pointer.failWrite(stream);throw e }
   } catch(e:Exception) { candidate.close();throw e }
   runCatching { instance?.close() };instance=candidate;candidate
  }
  fun recoverFromBackup(context:Context,records:Map<String,ByteArray>) { recover(context,records);WorkspaceBootstrap.reset();LabStore.reset() }
  fun get(context:Context)=instance ?: synchronized(this) { instance ?: configured(context.applicationContext).also { instance=it } }
 }
}
