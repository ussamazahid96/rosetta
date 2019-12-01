package fpgatidbits.PlatformWrapper

// MemReqParams describes what memory requests look like
class MemReqParams(
  // all units are "number of bits"
  val addrWidth: Int,       // width of memory addresses
  val dataWidth: Int,       // width of reads/writes
  val idWidth: Int,         // width of channel ID
  val metaDataWidth: Int,   // width of metadata (cache, prot, etc.)
  val sameIDInOrder: Boolean = true // whether requests with the same
                                    // ID return in-order, like in AXI
) {}


trait PlatformWrapperParams {
  def numMemPorts: Int
  def platformName: String
  def memAddrBits: Int
  def memDataBits: Int
  def memIDBits: Int
  def memMetaBits: Int
  def sameIDInOrder: Boolean
  def coherentMem: Boolean
  val csrDataBits: Int

  def toMemReqParams(): MemReqParams = {
    new MemReqParams(memAddrBits, memDataBits, memIDBits, memMetaBits, sameIDInOrder)
  }

  // the values below are useful for characterizing memory system performance,
  // for instance, when deciding how many outstanding txns are needed to hide
  // the memory latency for a big, sequential stream of data
  // TODO latency in cycles depends on clock freq, should use ns and specify fclk
  def typicalMemLatencyCycles: Int
  // TODO expose a list of supported burst sizes instead of a single preferred one
  def burstBeats: Int
  def seqStreamTxns(): Int = { typicalMemLatencyCycles / burstBeats }
}