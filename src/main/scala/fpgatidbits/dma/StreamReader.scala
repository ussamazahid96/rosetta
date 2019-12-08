package fpgatidbits.dma

import chisel3._
import chisel3.util._
import fpgatidbits.ocm._
import fpgatidbits.axi._
import fpgatidbits.streams._

class StreamReaderParams(
  val streamWidth: Int,
  val fifoElems: Int,
  val mem: MemReqParams,
  val maxBeats: Int,
  val chanID: Int,
  val disableThrottle: Boolean = false,
  val readOrderCache: Boolean = false,
  val readOrderTxns: Int = 4,
  val streamName: String = "stream"
)

class StreamReaderIF(w: Int, p: MemReqParams) extends Bundle {
  val start = Input(Bool())
  val active = Output(Bool())
  val finished = Output(Bool())
  val error = Output(Bool())
  val baseAddr = Input(UInt(p.addrWidth.W))
  val byteCount = Input(UInt(32.W))
  // stream data output
  val out = Decoupled(UInt(w.W))
  // interface towards memory port
  val req = Decoupled(new GenericMemoryRequest(p))
  val rsp = Flipped(Decoupled(new GenericMemoryResponse(p)))
  // controls for ID queue reinit
  val doInit = Input(Bool())                // re-initialize queue
  val initCount = Input(UInt(8.W))  // # IDs to initializes

  override def cloneType = { new StreamReaderIF(w, p).asInstanceOf[this.type] }
}

// size alignment in hardware
// if lower bits are not zero (=not aligned), increment upper bits by one,
// concatenate zeroes as the lower bits and return
object RoundUpAlign {
  def apply(align: Int, x: UInt): UInt = {
    val numZeroAddrBits = log2Up(align)
    val numOtherBits = x.getWidth-numZeroAddrBits
    val lower = x(numZeroAddrBits-1, 0)
    val upper = x(x.getWidth-1, numZeroAddrBits)
    val isAligned = (lower === 0.U)
    return Mux(isAligned, x, Cat(upper+1.U, 0.U(numZeroAddrBits.W)))
  }
}

class StreamReader(val p: StreamReaderParams) extends Module {
  val io = IO(new StreamReaderIF(p.streamWidth, p.mem))
  val StreamElem = UInt(p.streamWidth.W)

  // read request generator
  val rg = Module(new ReadReqGen(p.mem, p.chanID, p.maxBeats)).io
  // FIFO to store read data
  val fifo = Module(new FPGAQueue(StreamElem, p.fifoElems)).io
  val streamBytes = (p.streamWidth/8).U
  val memWidthBytes = (p.mem.dataWidth/8)

  rg.ctrl.start := io.start
  rg.ctrl.baseAddr := io.baseAddr
  // make sure byte count is a multiple of the mem data width,
  // otherwise the request generator will never finish
  // the superflous (alignment) bytes will be removed later
  rg.ctrl.byteCount := RoundUpAlign(memWidthBytes, io.byteCount)

  val regDoneBytes = RegInit(0.U(32.W))

  when(!io.start) { regDoneBytes := 0.U }
  .elsewhen(io.out.valid & io.out.ready) {
    regDoneBytes := regDoneBytes + (p.streamWidth/8).U
  }
  val allResponsesDone = (regDoneBytes === io.byteCount)
  io.active := io.start & !allResponsesDone
  io.finished := allResponsesDone
  io.error := rg.stat.error

  var orderedResponses = io.rsp
  // =====================================================================================
  // push out memory requests directly to memory channel
  rg.reqs <> io.req

  // create a StreamLimiter that lets only the first byteCount bytes pass
  // this gets rid of any alignment bytes introduced by RoundUpAlign
  def lim(in: AXIStreamMasterIF[UInt]): DecoupledIO[UInt] = {
    StreamLimiter(in, io.start, io.byteCount)
  }

  // read data responses (id etc filtered out)
  val rsp = ReadRespFilter(
    orderedResponses
  )
  // TODO add a StreamResizer to handle all 3 cases
  // if (p.mem.dataWidth == p.streamWidth) 
  // { 
  //   lim(rsp) <> fifo.enq 
  // }
  // else if (p.mem.dataWidth > p.streamWidth) 
  // {
  val st_dwnszr_out = StreamDownsizer(rsp, p.streamWidth, clock, reset) 
  lim(st_dwnszr_out) <>  fifo.enq
  // } 
  // else if (p.mem.dataWidth < p.streamWidth) 
  // {
  //   // TODO implement upsizing
  //   throw new Exception("StreamUpsizer not yet implemented")
  // }

  // expose FIFO output as the stream output
  fifo.deq <> io.out

  if(p.disableThrottle) { rg.ctrl.throttle := false.B }
  else {
    // throttling logic: don't ask more than what we can chew, limit the #
    // outstanding requested bytes to FIFO capacity
    val regBytesInFlight = RegInit(0.U(32.W))
    val fifoCount = UInt(32.W)
    val maxElemsInReq = (memWidthBytes * p.maxBeats / (p.streamWidth/8))
    if(p.fifoElems < 2*maxElemsInReq)
      throw new Exception("Too small FIFO in StreamReader")
    // cap the FIFO capacity at size-2*burst to have some slack; might overflow
    // due to stale feedback
    val fifoMax = (p.fifoElems-2*maxElemsInReq).U(32.W)
    // cap off the returned count at fifoMax to prevent underflows
    fifoCount := Mux(fifo.count > fifoMax, fifoMax, fifo.count)
    val fifoAvailBytes = (fifoMax - fifoCount) * streamBytes
    // calculate per-cycle updates to # bytes in flight
    val outReqBytes = UInt(32.W)
    val inRspBytes = UInt(32.W)
    outReqBytes := 0.U
    inRspBytes := 0.U
    when(rsp.valid & rsp.ready) { inRspBytes := memWidthBytes.U }
    when(io.req.valid & io.req.ready) { outReqBytes := io.req.bits.numBytes }
    regBytesInFlight := regBytesInFlight + outReqBytes - inRspBytes
    // throttle when we start getting too many requests
    rg.ctrl.throttle := RegNext(regBytesInFlight >= fifoAvailBytes)
  }
}