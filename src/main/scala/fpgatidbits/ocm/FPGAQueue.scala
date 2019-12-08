package fpgatidbits.ocm

import chisel3._
import chisel3.util._

class Q_srl(depthElems: Int, widthBits: Int) extends BlackBox(Map("depth" -> depthElems, "width" -> widthBits)) {
  val io = IO(new Bundle {
    val iData = Input(UInt(widthBits.W))
    val iValid = Input(Bool())
    val iBackPressure = Output(Bool())
    val oData = Output(UInt(widthBits.W))
    val oValid = Output(Bool())
    val oBackPressure = Input(Bool())
    
    val count = Output(UInt(log2Up(depthElems+1).W))
    val clock = Input(Clock())
    val reset = Input(Reset())
  })
}

class SRLQueue[T <: Data](gen: T, val entries: Int) extends Module {
  val io = IO(new QueueIO(gen, entries))
  val srlQ = Module(new Q_srl(entries, gen.getWidth)).io
  srlQ.clock := clock
  srlQ.reset := reset

  io.count := srlQ.count
  srlQ.iValid := io.enq.valid
  srlQ.iData := io.enq.bits.do_asUInt
  io.deq.valid := srlQ.oValid
  io.deq.bits := srlQ.oData.asTypeOf(io.deq.bits)

  // Q_srl uses backpressure, while Chisel queues use "ready"
  // invert signals while connecting
  srlQ.oBackPressure := !io.deq.ready
  io.enq.ready := !srlQ.iBackPressure
}


class BRAMQueue[T <: Data](gen: T, val entries: Int) extends Module {
  val io = IO(new QueueIO(gen, entries))

  // create a big queue that will use FPGA BRAMs as storage
  // the source code here is mostly copy-pasted from the regular Chisel
  // Queue, but with DualPortBRAM as the data storage
  // some simplifications has been applied, since pipe = false and
  // flow = false (no comb. paths between prod/cons read/valid signals)

  val enq_ptr = Counter(entries)
  val deq_ptr = Counter(entries)
  val maybe_full = RegInit(false.B)

  // due to the 1-cycle read latency of BRAMs, we add a small regular
  // SRLQueue at the output to correct the interface semantics by
  // "prefetching" the top two elements ("handshaking across latency")
  // TODO support higher BRAM latencies with parametrization here
  val readLatency = 1
  val pf = Module(new FPGAQueue(gen, readLatency + 2)).io
  // will be used as the "ready" signal for the prefetch queue
  // the threshold here needs to be (pfQueueCap-BRAM latency)
  val canPrefetch = (pf.count < 2.U)

  val bram = Module(new DualPortBRAM(log2Up(entries), gen.getWidth)).io
  bram.clock := clock

  val writePort = bram.a
  val readPort = bram.b
  writePort.req.writeData := io.enq.bits.do_asUInt
  writePort.req.writeEn := false.B
  writePort.req.addr := enq_ptr.value

  readPort.req.writeData := 0.U
  readPort.req.writeEn := false.B
  readPort.req.addr := deq_ptr.value

  val ptr_match = enq_ptr.value === deq_ptr.value
  val empty = ptr_match && !maybe_full
  val full = ptr_match && maybe_full

  val do_enq = io.enq.ready && io.enq.valid
  val do_deq = canPrefetch && !empty
  when (do_enq) {
    writePort.req.writeEn := true.B
    enq_ptr.inc()
  }
  when (do_deq) {
    deq_ptr.inc()
  }
  when (do_enq != do_deq) {
    maybe_full := do_enq
  }

  io.enq.ready := !full

  val register = RegInit(false.B)
  register := do_deq
  
  pf.enq.valid := register

  pf.enq.bits := readPort.rsp.readData.asTypeOf(pf.enq.bits)


  pf.deq <> io.deq

  val ptr_diff = enq_ptr.value - deq_ptr.value
  if (isPow2(entries)) {
    io.count := Cat(maybe_full && ptr_match, ptr_diff) + pf.count
  } else {
    io.count := Mux(ptr_match,
                    Mux(maybe_full,
                      entries.U, 0.U),
                    Mux(deq_ptr.value > enq_ptr.value,
                      entries.U + ptr_diff, ptr_diff)) + pf.count
  }
}


// creates a queue either using standard Chisel queues (for smaller queues)
// or with FPGA TDP BRAMs as the storage (for larger queues)
class FPGAQueue[T <: Data](gen: T, val entries: Int) extends Module {
  val thresholdBigQueue = 64 // threshold for deciding big or small queue impl
  val io = IO(new QueueIO(gen, entries))
  if(entries < thresholdBigQueue) {
    // create a shift register (SRL)-based queue
    val theQueue = Module(new SRLQueue(gen, entries)).io
    theQueue <> io
  } else {
    // create a BRAM queue
    val theQueue = Module(new BRAMQueue(gen, entries)).io
    theQueue <> io
  }
}

object FPGAQueue
{
  def apply[T <: Data](enq: DecoupledIO[T], entries: Int = 2): DecoupledIO[T]  = {
    val q = Module(new FPGAQueue(enq.bits.cloneType, entries))
    q.io.enq.valid := enq.valid // not using <> so that override is allowed
    q.io.enq.bits := enq.bits
    enq.ready := q.io.enq.ready
    q.io.deq
  }
}
