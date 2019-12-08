package fpgatidbits.streams

import chisel3._
import chisel3.util._
import fpgatidbits.axi._


class ParallelInSerialOut(parWidth: Int, serWidth: Int) extends Module {
  val numShiftSteps = parWidth/serWidth

  val io = IO(new Bundle {
    val parIn = Input(UInt(parWidth.W))
    val parWrEn = Input(Bool())
    val serIn = Input(UInt(serWidth.W))
    val serOut = Output(UInt(serWidth.W))
    val shiftEn = Input(Bool())
  })

  val stages = RegInit(VecInit(Seq.fill(numShiftSteps)(0.U(serWidth.W))))

  when( io.parWrEn ) {
    // load entire register from parallel input
    for(i <- 0 to numShiftSteps-1) {
      stages(i) := io.parIn((i+1)*serWidth-1, i*serWidth)
    }
  } .elsewhen( io.shiftEn ) {
    // load highest stage from serial input
    stages(numShiftSteps-1) := io.serIn
    // shift all stages to the right
    for(i <- 1 to numShiftSteps-1) {
      stages(i-1) := stages(i)
    }
  }

  // provide serial output from lowest stage
  io.serOut := stages(0)
}

object StreamDownsizer {
  def apply(in: DecoupledIO[UInt], outW: Int, clk: Clock, rst: Reset): AXIStreamMasterIF[UInt] = {
    val ds = Module(new AXIStreamDownsizer(in.bits.getWidth, outW))
    ds.wide.TDATA := in.bits
    ds.wide.TVALID := in.valid
    in.ready := ds.wide.TREADY
    ds.clock := clk
    ds.reset := rst
    ds.narrow
  }
}

class AXIStreamDownsizer(inWidth: Int, outWidth: Int) extends RawModule {
  val numShiftSteps = inWidth/outWidth

  val wide = IO(new AXIStreamSlaveIF(UInt(inWidth.W)))
  val narrow = IO(new AXIStreamMasterIF(UInt(outWidth.W)))
  val clock = IO(Input(Clock()))
  val reset = IO(Input(Reset()))

  // the shift register
  withClockAndReset(clock, reset){
  val shiftReg = Module(new ParallelInSerialOut(inWidth, outWidth))

  shiftReg.io.parIn := wide.TDATA
  shiftReg.io.serIn := 0.U
  narrow.TDATA := shiftReg.io.serOut
  shiftReg.io.parWrEn := false.B
  shiftReg.io.shiftEn := false.B

  // FSM and register definitions
  val sWaitInput :: sShift :: sLastStep :: Nil = Enum(3)
  val regState = RegInit(sWaitInput)
  val regShiftCount = RegInit(0.U(log2Up(numShiftSteps).W))

  // default outputs
  wide.TREADY := false.B
  narrow.TVALID := false.B

  // state machine
  switch( regState ) {
    is( sWaitInput ) {
      // enable parallel load to shift register
      shiftReg.io.parWrEn := true.B
      // signal to input that we are ready to go
      wide.TREADY := true.B
      // reset the count register
      regShiftCount := 0.U
      // wait until data is available at the input
      when ( wide.TVALID ) { regState := sShift }
    }

    is( sShift ) {
      // signal to output that data is available
      narrow.TVALID := true.B
      // wait for ack from output
      when (narrow.TREADY) {
        // increment shift counter
        regShiftCount := regShiftCount + 1.U
        // enable shift
        shiftReg.io.shiftEn := true.B
        // go to last state when appropriate, stay here otherwise
        // note that we don't have to shift the very last step,
        // hence the one before last is numShiftSteps-2
        when (regShiftCount === (numShiftSteps-2).U) { regState := sLastStep }
      }
    }

    is( sLastStep ) {
      // signal to output that data is available
      narrow.TVALID := true.B
      // wait for ack from output
      when (narrow.TREADY) {
        // next action depends on both the in and out sides
        when ( wide.TVALID ) {
          // new data already available on input, grab it
          shiftReg.io.parWrEn := true.B
          wide.TREADY := true.B
          // reset counter and go to shift state
          regShiftCount := 0.U
          regState := sShift
        } .otherwise {
          // go to sWaitInput
          regState := sWaitInput
        }
      }
    }
  }}
}