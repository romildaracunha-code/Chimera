package lab.nfc.core.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Versioned authenticated envelopes. Context binds ciphertext to its intended record. */
object SecureCodec {
 private val magic="NFCLAB01".toByteArray()
 fun seal(plain:ByteArray,key:SecretKey,context:String):ByteArray {
  val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key);val nonce=c.iv;require(nonce.size==12);c.updateAAD(context.toByteArray())
  return magic+nonce+c.doFinal(plain)
 }
 fun open(bytes:ByteArray,key:SecretKey,context:String):ByteArray {
  require(bytes.size>=36 && bytes.copyOfRange(0,8).contentEquals(magic)) { "Envelope inválido" }
  val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,bytes.copyOfRange(8,20)));c.updateAAD(context.toByteArray())
  return c.doFinal(bytes.copyOfRange(20,bytes.size))
 }
 fun sealingStream(out:java.io.OutputStream,key:SecretKey,context:String):java.io.OutputStream {
  val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key);require(c.iv.size==12);c.updateAAD(context.toByteArray());out.write(magic);out.write(c.iv)
  return javax.crypto.CipherOutputStream(out,c)
 }
 fun openingStream(input:java.io.InputStream,key:SecretKey,context:String):java.io.InputStream {
  val header=ByteArray(20);var offset=0;while(offset<header.size) { val n=input.read(header,offset,header.size-offset);require(n>0) { "Envelope truncado" };offset+=n }
  require(header.copyOfRange(0,8).contentEquals(magic));val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,header.copyOfRange(8,20)));c.updateAAD(context.toByteArray());return javax.crypto.CipherInputStream(input,c)
 }
 private fun derive(password:CharArray,salt:ByteArray):SecretKey {
  val spec=PBEKeySpec(password,salt,310000,256)
  return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES") } finally { spec.clearPassword() }
 }
 fun backup(plain:ByteArray,password:CharArray):ByteArray {
  require(password.size>=12) { "Senha do backup deve ter ao menos 12 caracteres" }
  val salt=ByteArray(16).also { SecureRandom().nextBytes(it) }
  return "NFCPBK01".toByteArray()+salt+seal(plain,derive(password,salt),"portable-backup-v1")
 }
 fun restore(bytes:ByteArray,password:CharArray):ByteArray {
  require(bytes.size>=60 && bytes.size<=100*1024*1024 && String(bytes.copyOfRange(0,8))=="NFCPBK01") { "Backup inválido/excessivo" }
  return open(bytes.copyOfRange(24,bytes.size),derive(password,bytes.copyOfRange(8,24)),"portable-backup-v1")
 }
}
