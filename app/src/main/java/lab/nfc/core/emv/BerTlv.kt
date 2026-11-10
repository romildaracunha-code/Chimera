package lab.nfc.core.emv
import lab.nfc.core.apdu.hex

data class TlvNode(val tag:String,val offset:Int,val length:Int,val value:ByteArray,val children:List<TlvNode>) {
 val constructed get()=tag.take(2).toInt(16) and 0x20 != 0
 fun flatten():List<TlvNode> = listOf(this)+children.flatMap { it.flatten() }
}
data class TlvTree(val nodes:List<TlvNode>,val warnings:List<String>)
class TlvException(val position:Int,reason:String):IllegalArgumentException("TLV offset $position: $reason")
/** EMV BER profile: definite lengths, bounded recursion/nodes/input. Not an ASN.1 general decoder. */
object BerTlv {
 const val MAX_BYTES=65536
 fun parse(input:ByteArray,maxDepth:Int=16,maxNodes:Int=2048,allowPadding:Boolean=true):TlvTree {
  require(input.size<=MAX_BYTES);require(maxDepth in 1..64 && maxNodes in 1..16384)
  var count=0;val warnings=mutableListOf<String>()
  fun fail(p:Int,s:String):Nothing=throw TlvException(p,s)
  fun range(start:Int,end:Int,depth:Int):List<TlvNode> {
   if(depth>maxDepth) fail(start,"profundidade excedida")
   var p=start;val result=mutableListOf<TlvNode>()
   fun read():Int { if(p>=end) fail(p,"estrutura truncada");return input[p++].toInt() and 255 }
   while(p<end) {
    if(allowPadding && (input[p].toInt() and 255) in listOf(0,255)) { warnings.add("PADDING@$p");p++;continue }
    val offset=p;val first=read();if(first in listOf(0,255)) fail(offset,"tag reservada")
    if(first and 31==31) { var size=1;var next=read();if(next and 127==0) fail(p-1,"tag longa inválida");while(next and 128!=0) { if(++size>=4) fail(p,"tag excessiva");next=read() } }
    val tag=input.copyOfRange(offset,p).hex();val lp=p;val l=read();var len=l.toLong()
    if(l and 128!=0) { val n=l and 127;if(n==0) fail(lp,"comprimento indefinido não permitido em EMV");if(n>4) fail(lp,"comprimento excessivo");len=0
     repeat(n) { val x=read();if(it==0 && x==0) warnings.add("NON_MINIMAL_LENGTH@$lp");len=(len shl 8) or x.toLong() };if(len<128) warnings.add("NON_MINIMAL_LENGTH@$lp") }
    if(len>(end-p).toLong()) fail(lp,"valor ultrapassa contêiner")
    if(++count>maxNodes) fail(offset,"número de nós excedido")
    val finish=p+len.toInt();val children=if(first and 32!=0) range(p,finish,depth+1) else emptyList()
    result.add(TlvNode(tag,offset,len.toInt(),input.copyOfRange(p,finish),children));p=finish
   };return result
  };return TlvTree(range(0,input.size,0),warnings.distinct())
 }
}
data class TagInfo(val name:String,val min:Int=0,val max:Int=65535,val sensitive:Boolean=false)
object EmvTags {
 val dictionary=mapOf(
 "6F" to TagInfo("FCI Template"),"A5" to TagInfo("FCI Proprietary"),"BF0C" to TagInfo("FCI Issuer Discretionary"),"61" to TagInfo("Application Template"),"70" to TagInfo("Record Template"),"77" to TagInfo("Response Template 2"),"80" to TagInfo("Response Template 1",sensitive=true),
 "4F" to TagInfo("AID",5,16),"84" to TagInfo("DF Name",5,16),"50" to TagInfo("Application Label",1,16),"87" to TagInfo("Application Priority",1,1),
 "82" to TagInfo("Application Interchange Profile",2,2),"94" to TagInfo("Application File Locator",4,252),"95" to TagInfo("Terminal Verification Results",5,5),
 "9F33" to TagInfo("Terminal Capabilities",3,3),"9F34" to TagInfo("CVM Results",3,3),"9F35" to TagInfo("Terminal Type",1,1),"9F66" to TagInfo("TTQ",4,4),"9F6C" to TagInfo("CTQ",2,2),
 "9F38" to TagInfo("PDOL"),"8C" to TagInfo("CDOL1"),"8D" to TagInfo("CDOL2"),"8E" to TagInfo("CVM List",8,252),"9F02" to TagInfo("Amount",6,6),"9F03" to TagInfo("Other Amount",6,6),"5F2A" to TagInfo("Currency",2,2),"5F28" to TagInfo("Issuer Country",2,2),"9F1A" to TagInfo("Terminal Country",2,2),"9A" to TagInfo("Transaction Date",3,3),"9C" to TagInfo("Transaction Type",1,1),"9F27" to TagInfo("Cryptogram Information Data",1,1),"9B" to TagInfo("Transaction Status Information",2,2),
 "5A" to TagInfo("PAN",0,19,true),"57" to TagInfo("Track 2 Equivalent",0,255,true),"56" to TagInfo("Track 1",0,255,true),"9F6B" to TagInfo("Track 2",0,255,true),"5F20" to TagInfo("Cardholder Name",0,255,true),"5F24" to TagInfo("Expiration",3,3,true),"9F26" to TagInfo("Application Cryptogram",8,8,true),"9F10" to TagInfo("Issuer Application Data",0,255,true),"9F4B" to TagInfo("Signed Dynamic Data",0,65535,true),"90" to TagInfo("Issuer Certificate",0,65535,true),"91" to TagInfo("Issuer Authentication",0,255,true),"9F46" to TagInfo("ICC Certificate",0,65535,true),"92" to TagInfo("Issuer PK Remainder",0,255,true),"9F48" to TagInfo("ICC PK Remainder",0,255,true))
 fun preview(n:TlvNode):String { val t=dictionary[n.tag];return when { n.constructed->"[CONSTRUCTED]";t==null->"[OPAQUE ${n.length} bytes]";t.sensitive->"[REDACTED ${n.length} bytes]";else->n.value.hex() } }
}
