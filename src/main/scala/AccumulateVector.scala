package rosetta

import chisel3._
import chisel3.util._
class TestAccumulateVector(vecElems: Int) extends RosettaAccelerator {
  val numMemPorts = 0
  val io = IO(new RosettaAcceleratorIF(numMemPorts) {
    val vector_num_elems = Output(UInt(32.W))
    val vector_in_addr = Input(UInt(log2Up(vecElems).W))
    val vector_in_data = Input(UInt(32.W))
    val vector_in_write_enable = Input(Bool())
    val vector_sum_enable = Input(Bool())
    val vector_sum_done = Output(Bool())
    val result = Output(UInt(32.W))
  })
  // instantiate the vector memory
  val memVec = Mem(vecElems, UInt(32.W))
  // set up finite state machine for vector summation
  val sIdle :: sAccumulate :: sDone :: Nil = Enum(3)
  val regState = RegInit(sIdle)
  // current vector index for accumulation
  val regVecInd = RegInit(0.U(log2Up(vecElems).W))
  // accumulator register
  val regVecAccumulator = RegInit(0.U(32.W))
  // drive the vector sum output from the sum register
  io.result := regVecAccumulator
  // drive result ready signal to low by default
  io.vector_sum_done := false.B
  // drive number of vector elements from constant
  io.vector_num_elems := vecElems.U
  // the signature can be e.g. used for checking that the accelerator has the
  // correct version. here the signature is regenerated from the current date.
  io.signature := makeDefaultSignature()

  switch(regState) {
      is(sIdle) {
        regVecAccumulator := 0.U
        regVecInd := 0.U
        when(io.vector_in_write_enable) {
          // enable writes to vector memory when write enable is high
          memVec(io.vector_in_addr) := io.vector_in_data
        }
        when(io.vector_sum_enable) {
          // go to accumulation state when sum is enabled
          regState := sAccumulate
        }
      }

      is(sAccumulate) {
        // accumulate vector at current index, increment index by one
        regVecAccumulator := regVecAccumulator + memVec(regVecInd)
        regVecInd := regVecInd + 1.U

        when(regVecInd === (vecElems-1).U) {
          // exit accumulation and go to done when all elements processed
          regState := sDone
        }
      }

      is(sDone) {
        // indicate that we are done, wait until sum enable is set to low
        io.vector_sum_done := true.B
        when(!io.vector_sum_enable) {
          regState := sIdle
        }
      }
  }
}
