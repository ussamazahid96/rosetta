package fpgatidbits.dma

import chisel3._
import chisel3.util._
import fpgatidbits.axi._

class AXIMemReqAdp(p: MemReqParams) extends Module {
  val io = IO(new Bundle {
    val genericReqIn = Flipped(Decoupled(new GenericMemoryRequest(p)))
    val axiReqOut = Decoupled(new AXIAddress(p.addrWidth, p.idWidth))
  })

  io.genericReqIn.ready := io.axiReqOut.ready
  io.axiReqOut.valid := io.genericReqIn.valid

  val reqIn = io.genericReqIn.bits
  val axiOut = io.axiReqOut.bits

  axiOut.ADDR := reqIn.addr
  axiOut.SIZE := (log2Up((p.dataWidth/8)-1)).U // only full-width
  val beats = (reqIn.numBytes / (p.dataWidth/8).U)
  axiOut.LEN := beats - 1.U // AXI defines len = beats-1
  axiOut.BURST := 1.U // incrementing burst
  axiOut.ID := reqIn.channelID
  axiOut.LOCK := false.B
  // TODO use metadata to alter cache bits as desired?
  axiOut.CACHE := "b0010".U // no alloc, modifiable, no buffer
  axiOut.PROT := 0.U
  axiOut.QOS := 0.U
}

class AXIReadRspAdp(p: MemReqParams) extends Module {
  val io = IO(new Bundle {
    val axiReadRspIn = Flipped(Decoupled(new AXIReadData(p.dataWidth, p.idWidth)))
    val genericRspOut = Decoupled(new GenericMemoryResponse(p))
  })

  io.genericRspOut.valid := io.axiReadRspIn.valid
  io.axiReadRspIn.ready := io.genericRspOut.ready

  val axiIn = io.axiReadRspIn.bits
  val rspOut = io.genericRspOut.bits

  rspOut.readData := axiIn.RDATA
  rspOut.channelID := axiIn.RID
  rspOut.metaData := 0.U // TODO add resp code from AXI response?
  rspOut.isWrite := false.B
  rspOut.isLast := axiIn.RLAST
}

class AXIWriteRspAdp(p: MemReqParams) extends Module {
  val io = IO(new Bundle {
    val axiWriteRspIn = Flipped(Decoupled(new AXIWriteResponse(p.idWidth)))
    val genericRspOut = Decoupled(new GenericMemoryResponse(p))
  })

  io.genericRspOut.valid := io.axiWriteRspIn.valid
  io.axiWriteRspIn.ready := io.genericRspOut.ready

  val axiIn = io.axiWriteRspIn.bits
  val rspOut = io.genericRspOut.bits

  rspOut.readData := 0.U
  rspOut.channelID := axiIn.BID
  rspOut.metaData := 0.U // TODO add resp from AXI response?
  rspOut.isWrite := true.B
  rspOut.isLast := false.B  // not applicable for write responses
}