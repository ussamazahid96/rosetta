package rosetta

import chisel3._
import fpgatidbits.dma._
import fpgatidbits.streams._
import fpgatidbits.PlatformWrapper._

// read and sum a contiguous stream of 32-bit integers from PYNQ's DRAM
class DRAMExample() extends RosettaAccelerator {
  val numMemPorts = 1
  val io = IO(new RosettaAcceleratorIF(numMemPorts) {
    val start = Input(Bool())
    val finished = Output(Bool())
    val baseAddr = Input(UInt(64.W))
    val byteCount = Input(UInt(32.W))
    val sum = Output(UInt(32.W))
    val cycleCount = Output(UInt(32.W))
  })
  // to read the data stream from DRAM, we'll use a component called StreamReader
  // from fpgatidbits.dma
  // we'll start by describing the "static" (unchanging) properties of the data
  // stream
  val rdP = new StreamReaderParams(
    streamWidth = 32, /* read a stream of 32 bits */
    fifoElems = 8,    /* add a stream FIFO of 8 elements */
    mem = PYNQU96Params.toMemReqParams(),  /* PYNQ memory request parameters */
    maxBeats = 1, /* do not use bursts (set to e.g. 8 for better DRAM bandwidth)*/
    chanID = 0, /* stream ID for distinguishing between returned responses */
    disableThrottle = true  /* disable throttling */
  )
  // now instantiate the StreamReader with these parameters
  val reader = Module(new StreamReader(rdP)).io
  // we'll use a StreamReducer to consume the data stream we get from DRAM
  val red = Module(new StreamReducer(
    32,     /* stream is 32-bit wide */
    0,      /* initial value for the reducer is 0 */
    {_+_}   /* use the + operator for reduction */
  )).io

  reader.doInit := false.B
  reader.initCount := 0.U
  // wire up the stream reader and reducer to the parameters that will be
  // specified by the user at runtime
  // start signal
  reader.start := io.start
  red.start := io.start
  reader.baseAddr := io.baseAddr    // pointer to start of data
  // number of bytes to read for both reader and reducer
  // IMPORTANT: it's best to provide a byteCount which is divisible by
  // 64, as the fpgatidbits streaming DMA components have some limitations.
  reader.byteCount := io.byteCount
  red.byteCount := io.byteCount
  // indicate when the reduced is finished, and expose the reduction result (sum)
  io.sum := red.reduced
  io.finished := red.finished
  // wire up the read requests-responses against the memory port interface
  reader.req <> io.memPort(0).memRdReq
  io.memPort(0).memRdRsp <> reader.rsp
  // push the read stream into the reducer
  reader.out <> red.streamIn
  
  // plug the unused write port
  io.memPort(0).memWrReq.valid := false.B
  io.memPort(0).memWrReq.bits.driveDefaults()

  io.memPort(0).memWrDat.valid := false.B
  io.memPort(0).memWrDat.bits := 0.U

  io.memPort(0).memWrRsp.ready := false.B

  // instantiate a cycle counter for benchmarking
  val regCycleCount = RegInit(0.U(32.W))
  io.cycleCount := regCycleCount
  when(!io.start) {regCycleCount := 0.U}
  .elsewhen(io.start & !io.finished) {regCycleCount := regCycleCount + 1.U}

  // the signature can be e.g. used for checking that the accelerator has the
  // correct version. here the signature is regenerated from the current date.
  io.signature := makeDefaultSignature()
}
