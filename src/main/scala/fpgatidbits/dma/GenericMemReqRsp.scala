package fpgatidbits.dma

import chisel3._
import chisel3.util._
import fpgatidbits.streams.PrintableBundle

// MemReqParams describes what memory requests look like
class MemReqParams(
  // all units are "number of bits"
  val addrWidth: Int,       // width of memory addresses
  val dataWidth: Int,       // width of reads/writes
  val idWidth: Int,         // width of channel ID
  val metaDataWidth: Int,   // width of metadata (cache, prot, etc.)
  val sameIDInOrder: Boolean = true // whether requests with the same
                                    // ID return in-order, like in AXI
)

// a generic memory request structure, inspired by AXI with some diffs
class GenericMemoryRequest(p: MemReqParams) extends PrintableBundle {
  // ID of the request channel (useful for out-of-order data returns)
  val channelID = UInt(p.idWidth.W)
  // whether this request is a read (if false) or write (if true)
  val isWrite = Bool()
  // start address of the request
  val addr = UInt(p.addrWidth.W)
  // number of bytes to read/write by this request
  val numBytes = UInt(8.W)
  // metadata information (can be protection bits, caching bits, etc.)
  val metaData = UInt(p.metaDataWidth.W)

  val printfStr = "id %d addr %d numBytes %d \n"
  val printfElems = {() => Seq(channelID, addr, numBytes)}

  override def cloneType = {
    new GenericMemoryRequest(p).asInstanceOf[this.type]
  }

  def driveDefaults() = {
    channelID := 0.U
    isWrite := false.B
    addr := 0.U
    numBytes := 0.U
    metaData := 0.U
  }
}

object GenericMemoryRequest {
  def apply(p: MemReqParams): GenericMemoryRequest = {
    val n = new GenericMemoryRequest(p)
    n.driveDefaults
    n
  }

  def apply(p: MemReqParams, addr: UInt, write:Bool,
    id: UInt, numBytes: UInt): GenericMemoryRequest = {
    val n = new GenericMemoryRequest(p)
    n.metaData := 0.U
    n.addr := addr
    n.isWrite := write
    n.channelID := id
    n.numBytes := numBytes
    n
  }
}

// a generic memory response structure
class GenericMemoryResponse(p: MemReqParams) extends PrintableBundle {
  // ID of the request channel (useful for out-of-order data returns)
  val channelID = UInt(p.idWidth.W)
  // returned read data (always single beat, bursts broken down into
  // multiple beats while returning)
  val readData = UInt(p.dataWidth.W)
  // is this response from a write?
  val isWrite = Bool()
  // is this response the last one in a burst of responses?
  val isLast = Bool()
  // metadata information (can be status/error bits, etc.)
  val metaData = UInt(p.metaDataWidth.W)

  val printfStr = "id %d readData %x isLast %d \n"
  val printfElems = {() => Seq(channelID, readData, isLast)}

  override def cloneType = {
    new GenericMemoryResponse(p).asInstanceOf[this.type]
  }

  def driveDefaults() = {
    channelID := 0.U
    readData := 0.U
    metaData := 0.U
    isLast := false.B
    isWrite := false.B
  }
}

object GenericMemoryResponse {
  def apply(p: MemReqParams): GenericMemoryResponse = {
    val n = new GenericMemoryResponse(p)
    n.driveDefaults
    n
  }
}

class GenericMemoryMasterPort(p: MemReqParams) extends Bundle {
  // req - rsp interface for memory reads
  val memRdReq = Decoupled(new GenericMemoryRequest(p))
  val memRdRsp = Flipped(Decoupled(new GenericMemoryResponse(p)))
  // req - rsp interface for memory writes
  val memWrReq = Decoupled(new GenericMemoryRequest(p))
  val memWrDat = Decoupled(UInt(p.dataWidth.W))
  val memWrRsp = Flipped(Decoupled(new GenericMemoryResponse(p)))

    override def cloneType = { new GenericMemoryMasterPort(p).asInstanceOf[this.type]}
}

class GenericMemorySlavePort(p: MemReqParams) extends Bundle {
  // req - rsp interface for memory reads
  val memRdReq = Flipped(Decoupled(new GenericMemoryRequest(p)))
  val memRdRsp = Decoupled(new GenericMemoryResponse(p))
  // req - rsp interface for memory writes
  val memWrReq = Flipped(Decoupled(new GenericMemoryRequest(p)))
  val memWrDat = Flipped(Decoupled(UInt(p.dataWidth.W)))
  val memWrRsp = Decoupled(new GenericMemoryResponse(p))

  override def cloneType = { new GenericMemorySlavePort(p).asInstanceOf[this.type]}

}
