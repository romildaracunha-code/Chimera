package lab.nfc.core.emv

/** Generic EMV Book 3 fields; contactless kernel-specific TTQ/CTQ are deliberately not generalized. */
object EmvSemantics {
 private fun bits(b:ByteArray,items:List<Triple<Int,Int,String>>) = items.filter { (index,mask,_) -> b[index].toInt() and mask!=0 }.map { it.third }
 private val tvr=listOf(
  Triple(0,128,"Offline data authentication not performed"),Triple(0,64,"SDA failed"),Triple(0,32,"ICC data missing"),Triple(0,16,"Card on terminal exception file"),Triple(0,8,"DDA failed"),Triple(0,4,"CDA failed"),Triple(0,2,"SDA selected"),
  Triple(1,128,"Application version mismatch"),Triple(1,64,"Expired application"),Triple(1,32,"Application not yet effective"),Triple(1,16,"Requested service not allowed"),Triple(1,8,"New card"),
  Triple(2,128,"Cardholder verification not successful"),Triple(2,64,"Unrecognised CVM"),Triple(2,32,"PIN try limit exceeded"),Triple(2,16,"PIN pad missing or broken"),Triple(2,8,"Required PIN not entered"),Triple(2,4,"Online PIN entered"),
  Triple(3,128,"Transaction exceeds floor limit"),Triple(3,64,"Lower consecutive offline limit exceeded"),Triple(3,32,"Upper consecutive offline limit exceeded"),Triple(3,16,"Randomly selected online"),Triple(3,8,"Merchant forced online"),
  Triple(4,128,"Default TDOL used"),Triple(4,64,"Issuer authentication failed"),Triple(4,32,"Script processing failed before final GENERATE AC"),Triple(4,16,"Script processing failed after final GENERATE AC"))
 private val tsi=listOf(Triple(0,128,"Offline data authentication performed"),Triple(0,64,"Cardholder verification performed"),Triple(0,32,"Card risk management performed"),Triple(0,16,"Issuer authentication performed"),Triple(0,8,"Terminal risk management performed"),Triple(0,4,"Script processing performed"))
 private val aip=listOf(Triple(0,64,"SDA supported"),Triple(0,32,"DDA supported"),Triple(0,16,"Cardholder verification supported"),Triple(0,8,"Terminal risk management required"),Triple(0,4,"Issuer authentication supported"),Triple(0,1,"CDA supported"))
 fun cvm(code:Int)=when(code and 63) { 0->"Fail CVM processing";1->"Plaintext PIN verified by ICC";2->"Enciphered PIN verified online";3->"Plaintext PIN and signature";4->"Enciphered PIN verified by ICC";5->"Enciphered PIN and signature";30->"Signature";31->"No CVM required";63->"No CVM performed";else->"RFU/payment-system specific (${code and 63})" }
 fun decode(tag:String,b:ByteArray):List<String> {
  val expected=mapOf("95" to 5,"9B" to 2,"82" to 2,"9F34" to 3)
  expected[tag]?.let { if(b.size!=it) return listOf("INVALID_SEMANTIC_LENGTH $tag expected=$it actual=${b.size}") }
  return when(tag) {
   "95"->bits(b,tvr).ifEmpty { listOf("No generic TVR condition set; does not prove authentication success") }
   "9B"->bits(b,tsi).ifEmpty { listOf("No generic TSI processing flag set") }
   "82"->bits(b,aip)+"AIP interpreted using generic EMV Book 3; contactless kernel extensions require its specification"
   "9F34"->listOf("CVM: ${cvm(b[0].toInt())}","If unsuccessful: ${if(b[0].toInt() and 64!=0) "apply succeeding CV rule" else "fail CVM processing"}","Condition code: ${b[1].toInt() and 255}","CVM result: ${when(b[2].toInt() and 255) { 0->"UNKNOWN";1->"FAILED";2->"SUCCESSFUL";else->"RFU" }}; declared field, not cryptographic verification")
   "8E"->{ if(b.size<8 || (b.size-8)%2!=0) listOf("INVALID_CVM_LIST_LENGTH") else {
    val out=mutableListOf("CVM List: amount X/Y followed by ${(b.size-8)/2} rules; amounts depend on application currency")
    for(i in 8 until b.size step 2) out.add("Rule ${(i-8)/2}: ${cvm(b[i].toInt())}; condition=${b[i+1].toInt() and 255}; continueOnFailure=${b[i].toInt() and 64!=0}");out
   } }
   "9F66","9F6C"->listOf("Kernel-specific field: select the official kernel/version specification before interpreting bits")
   else->emptyList()
  }
 }
 fun consistency(nodes:List<TlvNode>):List<String> {
  val values=nodes.associate { it.tag to it.value };val tv=values["95"];val ts=values["9B"];val cv=values["9F34"]
  val out=mutableListOf<String>()
  if(tv?.size==5 && ts?.size==2 && tv[0].toInt() and 128!=0 && ts[0].toInt() and 128!=0) out.add("REVIEW_CONTEXT: TVR reports ODA not performed while TSI reports ODA performed; check capture scope/kernel")
  if(tv?.size==5 && cv?.size==3 && tv[2].toInt() and 128!=0 && cv[2].toInt()==2) out.add("REVIEW_CONTEXT: successful CVM result with TVR CVM failure; verify chronology and scope")
  return out
 }
}
