import chisel3._
import rosetta._
import chisel3.iotesters.Driver
import chisel3.iotesters.PeekPokeTester

object testhardware {

  // Tester-derived class to give stimulus and observe the outputs for the
  // Module to be tested
  class AddTest(dut: TestRegOps) extends PeekPokeTester(dut) {
    // use poke() to set I/O input signal values
    poke(dut.io.op(0), 10)
    poke(dut.io.op(1), 20)
    // use step() to advance the clock cycle
    step(1)
    // use expect() to read and check I/O output signal values
    expect(dut.io.sum, 10+20)
    // use peek() to read I/O output signal values
    println(s"Sum given by the hardware is = "+peek(dut.io.sum))
  }

  def main(args: Array[String]): Unit = 
  {
    println("\n================ Testing hardware ================\n")
    val rawargs = Array("--target-dir", "build/test")
    val result = Driver.execute(rawargs, () => new TestRegOps()) { dut => new AddTest(dut)}
    assert(result)
    println("\n=================== SUCCESS!! ===================\n")
  }

}
