package fpgatidbits.ocm

import chisel3._
import chisel3.util._
import chisel3.experimental._

// A module for inferring true dual-pPort BRAMs on FPGAs

// Since (Xilinx) FPGA synthesis tools do not infer TDP BRAMs from
// Chisel-generated Verilog (both ports in the same "always" block),
// we use a BlackBox with a premade Verilog BRAM template.

class DualPortBRAMIO(addrBits: Int, dataBits: Int) extends Bundle {
  val a = new OCMSlaveIF(dataBits, dataBits, addrBits)
  val b = new OCMSlaveIF(dataBits, dataBits, addrBits)
  val clock = Input(Clock())

  override def cloneType = { new DualPortBRAMIO(addrBits, dataBits).asInstanceOf[this.type] }

}

class DualPortBRAM(addrBits: Int, dataBits: Int) extends BlackBox(Map("DATA" -> dataBits, "ADDR" -> addrBits)){
  val io = IO(new DualPortBRAMIO(addrBits, dataBits))
}