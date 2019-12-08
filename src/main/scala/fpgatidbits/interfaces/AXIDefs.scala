package fpgatidbits.axi

import chisel3._

// Part I: Definitions for the actual data carried over AXI channels
// in part II we will provide definitions for the actual AXI interfaces
// by wrapping the part I types in Decoupled (ready/valid) bundles

// AXI channel data definitions

class AXIAddress(addrWidthBits: Int, idBits: Int) extends Bundle {
  // address for the transaction, should be burst aligned if bursts are used
  val ADDR    = UInt(addrWidthBits.W)
  // size of data beat in bytes
  // set to UInt(log2Up((dataBits/8)-1)) for full-width bursts
  val SIZE    = UInt(3.W)
  // number of data beats -1 in burst: max 255 for incrementing, 15 for wrapping
  val LEN     = UInt(8.W)
  // burst mode: 0 for fixed, 1 for incrementing, 2 for wrapping
  val BURST   = UInt(2.W)
  // transaction ID for multiple outstanding requests
  val ID      = UInt(idBits.W)
  // set to 1 for exclusive access
  val LOCK    = Bool()
  // cachability, set to 0010 or 0011
  val CACHE   = UInt(4.W)
  // generally ignored, set to to all zeroes
  val PROT    = UInt(3.W)
  // not implemented, set to zeroes
  val QOS     = UInt(4.W)
  override def cloneType = { new AXIAddress(addrWidthBits, idBits).asInstanceOf[this.type] }
}

class AXIWriteData(dataWidthBits: Int) extends Bundle {
  val WDATA    = UInt(dataWidthBits.W)
  val WSTRB    = UInt((dataWidthBits/8).W)
  val WLAST    = Bool()
  override def cloneType = { new AXIWriteData(dataWidthBits).asInstanceOf[this.type] }
}

class AXIWriteResponse(idBits: Int) extends Bundle {
  val BID      = UInt(idBits.W)
  val BRESP    = UInt(2.W)
  override def cloneType = { new AXIWriteResponse(idBits).asInstanceOf[this.type] }
}

class AXIReadData(dataWidthBits: Int, idBits: Int) extends Bundle {
  val RDATA    = UInt(dataWidthBits.W)
  val RID      = UInt(idBits.W)
  val RLAST    = Bool()
  val RRESP    = UInt(2.W)
  override def cloneType = { new AXIReadData(dataWidthBits, idBits).asInstanceOf[this.type] }
}

// Part II: Definitions for the actual AXI interfaces

// TODO add full slave interface definition

class AXIMasterIF(addrWidthBits: Int, dataWidthBits: Int, idBits: Int) extends Bundle {
  // write address channel
  val AWADDR = Output(UInt(addrWidthBits.W))
  val AWSIZE = Output(UInt(3.W))
  val AWLEN = Output(UInt(8.W))
  val AWBURST = Output(UInt(2.W))
  val AWID = Output(UInt(idBits.W))
  val AWLOCK = Output(Bool())
  val AWCACHE = Output(UInt(4.W))
  val AWPROT = Output(UInt(3.W))
  val AWQOS = Output(UInt(4.W))
  val AWREADY = Input(Bool())
  val AWVALID = Output(Bool())
  
  // write data channel
  val WDATA = Output(UInt(dataWidthBits.W))
  val WSTRB = Output(UInt((dataWidthBits/8).W))
  val WLAST = Output(Bool())
  val WREADY = Input(Bool())
  val WVALID = Output(Bool())
  
  // write response channel (for memory consistency)
  val BID = Input(UInt(idBits.W))
  val BRESP = Input(UInt(2.W))
  val BREADY = Output(Bool())
  val BVALID = Input(Bool())

  // read address channel
  val ARADDR = Output(UInt(addrWidthBits.W))
  val ARSIZE = Output(UInt(3.W))
  val ARLEN = Output(UInt(8.W))
  val ARBURST = Output(UInt(2.W))
  val ARID = Output(UInt(idBits.W))
  val ARLOCK = Output(Bool())
  val ARCACHE = Output(UInt(4.W))
  val ARPROT = Output(UInt(3.W))
  val ARQOS = Output(UInt(4.W))
  val ARREADY = Input(Bool())
  val ARVALID = Output(Bool())
  
  // read data channel
  val RDATA = Input(UInt(dataWidthBits.W))
  val RID = Input(UInt(idBits.W))
  val RLAST = Input(Bool())
  val RRESP = Input(UInt(2.W))
  val RREADY = Output(Bool())
  val RVALID = Input(Bool())

  // drive default/"harmless" values to leave no output uninitialized
  def driveDefaults() {
    AWVALID := false.B
    WVALID := false.B
    BREADY := false.B
    ARVALID := false.B
    RREADY := false.B
    // write address channel
    AWADDR := 0.U
    AWPROT := 0.U
    AWSIZE := 0.U
    AWLEN := 0.U
    AWBURST := 0.U
    AWLOCK := false.B
    AWCACHE := 0.U
    AWQOS := 0.U
    AWID := 0.U
    // write data channel
    WDATA := 0.U
    WSTRB := 0.U
    WLAST := false.B
    // read address channel
    ARADDR := 0.U
    ARPROT := 0.U
    ARSIZE := 0.U
    ARLEN := 0.U
    ARBURST := 0.U
    ARLOCK := false.B
    ARCACHE := 0.U
    ARQOS := 0.U
    ARID := 0.U

    
  }

  override def cloneType = { new AXIMasterIF(addrWidthBits, dataWidthBits, idBits).asInstanceOf[this.type] }
}
