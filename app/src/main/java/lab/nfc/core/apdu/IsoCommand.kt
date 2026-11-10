package lab.nfc.core.apdu

/** ISO 7816 command framing, cases 1/2s/3s/4s/2e/3e/4e. Not a payment engine. */
data class IsoCommand(val cla:Int,val ins:Int,val p1:Int,val p2:Int,val data:ByteArray=byteArrayOf(),val le:Int?=null,val extended:Boolean=false) {
 init { require(listOf(cla,ins,p1,p2).all { it in 0..255 });require(data.size<=if(extended) 65535 else 255);require(le==null || le in 1..if(extended) 65536 else 256) }
 fun encode():ByteArray {
  val h=byteArrayOf(cla.toByte(),ins.toByte(),p1.toByte(),p2.toByte())
  if(data.isEmpty() && le==null) return h
  if(!extended) return h+(if(data.isEmpty()) byteArrayOf() else byteArrayOf(data.size.toByte())+data)+(le?.let { byteArrayOf(it.toByte()) } ?: byteArrayOf())
  fun word(n:Int)=byteArrayOf((n shr 8).toByte(),n.toByte())
  return h+byteArrayOf(0)+(if(data.isEmpty()) byteArrayOf() else word(data.size)+data)+(le?.let { word(it) } ?: byteArrayOf())
 }
 companion object { fun parse(b:ByteArray):IsoCommand {
  require(b.size in 4..65544) { "APDU fora do limite" };fun u(i:Int)=b[i].toInt() and 255
  fun word(i:Int)=(u(i) shl 8) or u(i+1)
  if(b.size==4) return IsoCommand(u(0),u(1),u(2),u(3))
  if(b.size==5) return IsoCommand(u(0),u(1),u(2),u(3),le=u(4).let { if(it==0) 256 else it })
  if(u(4)!=0) { val n=u(4);require(b.size==5+n || b.size==6+n);return IsoCommand(u(0),u(1),u(2),u(3),b.copyOfRange(5,5+n),if(b.size==6+n) u(b.lastIndex).let { if(it==0) 256 else it } else null) }
  require(b.size>=7);val n=word(5)
  if(b.size==7) return IsoCommand(u(0),u(1),u(2),u(3),le=if(n==0) 65536 else n,extended=true)
  require(n>0 && (b.size==7+n || b.size==9+n))
  return IsoCommand(u(0),u(1),u(2),u(3),b.copyOfRange(7,7+n),if(b.size==9+n) word(7+n).let { if(it==0) 65536 else it } else null,true)
 } }
}
object LabAids {
 val all=listOf("F04E46434C414201","F04E46434C414202","F04E46434C414203")
 fun requireLab(aid:String):String { require(aid in all) { "AID fora do namespace proprietário pré-registrado" };return aid }
 fun select(aid:String)=Command(0,0xA4,4,0,requireLab(aid).bytes(),256).encode().hex()
}
