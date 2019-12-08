package fpgatidbits.axi

import chisel3._

// Define simple extensions of the Chisel Decoupled interfaces,
// with signal renaming to support auto inference of AXI stream interfaces in Vivado


class AXIStreamMasterIF[T <: Data](gen: T) extends Bundle {
  val TREADY = Input(Bool())
  val TVALID = Output(Bool())
  val TDATA = Output(gen)

  override def cloneType = {new AXIStreamMasterIF(gen).asInstanceOf[this.type]}
}

class AXIStreamSlaveIF[T <: Data](gen: T) extends Bundle {
  val TREADY = Output(Bool())
  val TVALID = Input(Bool())
  val TDATA = Input(gen)

  override def cloneType = {new AXIStreamSlaveIF(gen).asInstanceOf[this.type]}
}