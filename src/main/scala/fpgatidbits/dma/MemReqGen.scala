package fpgatidbits.dma

import chisel3._
import chisel3.util._

// control interface for (simple) request generators
class ReqGenCtrl(addrWidth: Int) extends Bundle {
  val start = Input(Bool())
  val throttle = Input(Bool())
  val baseAddr = Input(UInt(addrWidth.W))
  val byteCount = Input(UInt(addrWidth.W))
}

// status interface for (simple) request generators
class ReqGenStatus() extends Bundle {
  val finished = Output(Bool())
  val active = Output(Bool())
  val error = Output(Bool())
}

// a generic memory request generator,
// only for contiguous accesses for now (no indirects, no strides)
// only burst-aligned addresses (no error checking!)
// will report error if start address is not word-aligned
// TODO do we want to support sub-word accesses?
class ReadReqGen(p: MemReqParams, chanID: Int, maxBeats: Int) extends Module {
  val reqGenParams = p
  val io = IO(new Bundle {
    // control/status interface
    val ctrl = new ReqGenCtrl(p.addrWidth)
    val stat = new ReqGenStatus()
    // requests
    val reqs = Decoupled(new GenericMemoryRequest(p))
  })
  // shorthands for convenience
  val bytesPerBeat = (p.dataWidth/8)
  val bytesPerBurst = maxBeats * bytesPerBeat
  // state machine definitions & internal registers
  val sIdle :: sRun :: sFinished :: sError :: Nil = Enum(4)
  val regState = RegInit(sIdle)
  val regAddr = RegInit(0.U(p.addrWidth.W))
  val regBytesLeft = RegInit(0.U(p.addrWidth.W))
  // default outputs
  io.stat.error := false.B
  io.stat.finished := false.B
  io.stat.active := (regState != sIdle)
  io.reqs.valid := false.B
  io.reqs.bits.channelID := chanID.U
  io.reqs.bits.isWrite := false.B
  io.reqs.bits.addr := regAddr
  io.reqs.bits.metaData := 0.U
  // decide on length of burst depending on #bytes left
  val doBurst = (regBytesLeft >= bytesPerBurst.U)
  val burstLen = Mux(doBurst, bytesPerBurst.U, bytesPerBeat.U)
  io.reqs.bits.numBytes := burstLen

  // address needs to be aligned to burst size
  val numZeroAddrBits = log2Up(bytesPerBurst)
  val unalignedAddr = (io.ctrl.baseAddr(numZeroAddrBits-1, 0) != 0.U)
  // number of bytes needs to be aligned to bus width
  val numZeroSizeBits = log2Up(bytesPerBeat)
  val unalignedSize = (io.ctrl.byteCount(numZeroSizeBits-1, 0) != 0.U)
  val isUnaligned = unalignedSize || unalignedAddr

  switch(regState) {
      is(sIdle) {
        regAddr := io.ctrl.baseAddr
        regBytesLeft := io.ctrl.byteCount
        when (io.ctrl.start) { regState := Mux(isUnaligned, sError, sRun) }
      }

      is(sRun) {
        when (regBytesLeft === 0.U) { regState := sFinished }
        .elsewhen (!io.ctrl.throttle) {
          // issue the current request
          io.reqs.valid := true.B
          when (io.reqs.ready) {
            // next request: update address & left request count
            regAddr := regAddr + burstLen
            regBytesLeft := regBytesLeft - burstLen
          }
        }
      }

      is(sFinished) {
        io.stat.finished := true.B
        when (!io.ctrl.start) { regState := sIdle }
      }

      is(sError) {
        // only way out is reset
        io.stat.error := true.B
        printf("Error in MemReqGen! regAddr = %x byteCount = %d \n", regAddr, io.ctrl.byteCount)
        printf("Unaligned addr? %d size? %d \n", unalignedAddr, unalignedSize)
      }
  }
}

class WriteReqGen(p: MemReqParams, chanID: Int, maxBeats: Int = 1) extends ReadReqGen(p, chanID, maxBeats) {
  // force single beat per burst for now
  // TODO support write bursts -- needs support in interleaver
  io.reqs.bits.isWrite := true.B
}