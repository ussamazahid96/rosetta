package rosetta

import chisel3._
import chisel3.experimental.{DataMirror, Direction}

// add your custom accelerator here, derived from RosettaAccelerator

// here we have a test for register reads and writes: add two 64-bit values
// the io bundle has the following signals:
// op: vector of two 64-bit signals, input values to be added
// sum: output 64-bit signal, equal to op(0)+op(1)
// cc: the number of clock cycles that have elapsed since last reset
class TestRegOps() extends RosettaAccelerator {
  val numMemPorts = 0
  val io = IO(new RosettaAcceleratorIF(numMemPorts) {
    val op = Input(Vec(2, UInt(64.W)))
    val sum = Output(UInt(64.W))
    val cc = Output(UInt(32.W))
  })
  // wire sum output to sum of op inputs
  io.sum := io.op(0) + io.op(1)

  // instantiate a clock cycle counter register
  val regCC = RegInit(0.U(32.W))

  // increment counter by 1 every clock cycle
  regCC := regCC + 1.U
  // expose counter through the output called cc
  io.cc := regCC

  // in addition to the signals we defined here, there are some signals that
  // are always present in the io bundle, as we derive from RosettaAcceleratorIF
  // the signature can be e.g. used for checking that the accelerator has the
  // correct version. here the signature is regenerated from the current date.
  io.signature := makeDefaultSignature()
  
}
