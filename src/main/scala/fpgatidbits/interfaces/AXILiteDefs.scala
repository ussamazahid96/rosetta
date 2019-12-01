package fpgatidbits.axi

import chisel3._
import chisel3.util._

class AXILiteSlaveIF(addrWidthBits: Int, dataWidthBits: Int) extends Bundle {

  // write address channel
  val AWADDR = Input(UInt(addrWidthBits.W))
  val AWPROT = Input(UInt(3.W))
  val AWREADY = Output(Bool())
  val AWVALID = Input(Bool())

  // write data channel
  val WDATA = Input(UInt(addrWidthBits.W))
  val WSTRB = Input(UInt((dataWidthBits/8).W))
  val WREADY = Output(Bool())
  val WVALID = Input(Bool())
  
  // write response channel (for memory consistency)
  val BRESP = Output(UInt(2.W))
  val BREADY = Input(Bool())
  val BVALID = Output(Bool())

  // read address channel
  val ARADDR = Input(UInt(addrWidthBits.W))
  val ARPROT = Input(UInt(3.W))
  val ARREADY = Output(Bool())
  val ARVALID = Input(Bool())
  
  // read data channel
  val RDATA = Output(UInt(dataWidthBits.W))
  val RRESP = Output(UInt(2.W))
  val RREADY = Input(Bool())
  val RVALID = Output(Bool())

  override def cloneType = { new AXILiteSlaveIF(addrWidthBits, dataWidthBits).asInstanceOf[this.type] }
}