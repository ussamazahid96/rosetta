package rosetta

import chisel3._
import chisel3.util._
import fpgatidbits.ocm._

class BRAMExample(numMemLines: Int) extends RosettaAccelerator {
  val numMemPorts = 0
  val io = IO(new RosettaAcceleratorIF(numMemPorts) {
    val write_enable = Input(Bool())
    val write_addr = Input(UInt(log2Up(numMemLines).W))
    val write_data = Input(UInt(32.W))
    val read_addr = Input(UInt(log2Up(numMemLines).W))
    val read_data = Output(UInt(32.W))
  })

    // instantiate the BRAM wrapper from the fpgatidbits library
    val bram = Module(new DualPortBRAM(addrBits = log2Up(numMemLines), dataBits = 32))
    bram.io.clock := clock

    // Xilinx BRAMs have two ports that can be used simultaneously
    // however, you should avoid writing and reading to the same memory address
    // in the same cycle -- unexpected things may happen.
    // use port 0 as the write port
    val write_port = bram.io.a
    // link up against I/O
    write_port.req.addr := io.write_addr
    write_port.req.writeData := io.write_data
    write_port.req.writeEn := io.write_enable

    // use port 1 as the read port
    val read_port = bram.io.b
    // writes always disabled for read port
    read_port.req.writeEn := false.B
    // link up against I/O
    read_port.req.addr := io.read_addr
    // note: the result of the read will appear in the *next* clock cycle
    // software is too slow to notice this, but any hardware making use of BRAM
    // needs to pay attention to this 1-cycle delay
    io.read_data := read_port.rsp.readData

    // the signature can be e.g. used for checking that the accelerator has the
    // correct version. here the signature is regenerated from the current date.
    io.signature := makeDefaultSignature()
}
