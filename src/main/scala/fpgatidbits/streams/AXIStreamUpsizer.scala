package fpgatidbits.streams

import chisel3._
import chisel3.util._
import chisel3.iotesters.PeekPokeTester

import fpgatidbits.axi._

class SerialInParallelOutIO(parWidth: Int, serWidth: Int) extends Bundle {
  val serIn = Input(UInt(serWidth.W))
  val parOut = Output(UInt(parWidth.W))
  val shiftEn = Input(Bool())

  override def cloneType = { new SerialInParallelOutIO(parWidth, serWidth).asInstanceOf[this.type]}
}

class SerialInParallelOut(parWidth: Int, serWidth: Int) extends Module {
  val numShiftSteps = parWidth/serWidth

  val io = IO(new SerialInParallelOutIO(parWidth, serWidth))

  val stages = RegInit(VecInit(Seq.fill(numShiftSteps)(0.U(serWidth.W))))

  when (io.shiftEn) {
    // fill highest stage from serial input
    stages(numShiftSteps-1) := io.serIn
    // shift all stages to the right
    for(i <- 0 until numShiftSteps-1) {
      stages(i) := stages(i+1)
    }
  }
  // Cat does concat as 0 1 2 .. N
  // reverse the order to get N .. 2 1 0
  io.parOut := Cat(stages.reverse)
}


class AXIStreamUpsizer(inWidth: Int, outWidth: Int) extends Module {
  val io = IO(new Bundle {
    val in = new AXIStreamSlaveIF(UInt(inWidth.W))
    val out = new AXIStreamMasterIF(UInt(outWidth.W))
  })
  if(inWidth >= outWidth) {
    println("AXIStreamUpsizer needs inWidth < outWidth")
    System.exit(-1)
  }
  val numShiftSteps = outWidth/inWidth
  val shiftReg = Module(new SerialInParallelOut(outWidth, inWidth)).io
  shiftReg.serIn := io.in.TDATA
  shiftReg.shiftEn := false.B

  io.in.TREADY := false.B
  io.out.TVALID := false.B
  io.out.TDATA := shiftReg.parOut

  val sWaitInput :: sWaitOutput :: Nil = Enum(2)
  val regState = RegInit(sWaitInput)

  val regAcquiredStages = RegInit(0.U(32.W))
  val readyForOutput = (regAcquiredStages === (numShiftSteps-1).U)

  switch(regState) {
      is(sWaitInput) {
        io.in.TREADY := true.B
        when (io.in.TVALID) {
          shiftReg.shiftEn := true.B
          regAcquiredStages := regAcquiredStages + 1.U
          regState := Mux(readyForOutput, sWaitOutput, sWaitInput)
        }
      }
      is(sWaitOutput) {
        io.out.TVALID := true.B
        when (io.out.TREADY) {
          regAcquiredStages := 0.U
          regState := sWaitInput
        }
      }
  }
}

object StreamUpsizer {
  def apply(in: DecoupledIO[UInt], outW: Int): AXIStreamMasterIF[UInt] = {
    val ds = Module(new AXIStreamUpsizer(in.bits.getWidth, outW)).io
    in.ready := ds.in.TREADY
    ds.in.TVALID := in.valid
    ds.in.TDATA := in.bits
    ds.out
  }
}

class AXIStreamUpsizerTester(c: AXIStreamUpsizer) extends PeekPokeTester(c) {
  // simple test 8 -> 32 upsizing
  expect(c.io.in.TREADY, 1)
  expect(c.io.out.TVALID, 0)
  poke(c.io.out.TREADY, 0)
  poke(c.io.in.TVALID, 1)
  poke(c.io.in.TDATA, "hef".U(8.W).litValue())
  step(1)
  poke(c.io.in.TDATA, "hbe".U(8.W).litValue())
  step(1)
  poke(c.io.in.TDATA, "had".U(8.W).litValue())
  step(1)
  poke(c.io.in.TDATA, "hde".U(8.W).litValue())
  step(1)
  poke(c.io.in.TVALID, 0)
  expect(c.io.in.TREADY, 0)
  expect(c.io.out.TVALID, 1)
  expect(c.io.out.TDATA, "hdeadbeef".U(32.W).litValue())
  step(1)
  poke(c.io.out.TREADY, 1)
  step(1)
  expect(c.io.in.TREADY, 1)
  expect(c.io.out.TVALID, 0)
}


import chisel3.iotesters.Driver

object testhardware {

  def main(args: Array[String]): Unit = 
  {
    println("\n================ Testing hardware ================\n")
    // val result = Driver.execute( Array[String]() , () => new hardware() ) { dut => new tester(dut) }
    val result = Driver(() => new AXIStreamUpsizer(8,32)) {c => new AXIStreamUpsizerTester(c)}
    assert(result)
    println("\n=================== SUCCESS!! ===================\n")
  }

}