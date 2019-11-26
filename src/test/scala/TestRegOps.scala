import Chisel._
import rosetta._
import RosettaTest._
import Chisel.iotesters.Driver
import Chisel.iotesters.PeekPokeTester


// class TestRegOpsSuite extends JUnitSuite {
//   @Test def AddTest {

//     // Tester-derived class to give stimulus and observe the outputs for the
//     // Module to be tested
//     class AddTest(c: TestRegOps) extends PeekPokeTester(c) {
//       // use peek() to read I/O output signal values
//       peek(c.io.signature)
//       // use poke() to set I/O input signal values
//       poke(c.io.op(0), 10)
//       poke(c.io.op(1), 20)
//       // use step() to advance the clock cycle
//       step(1)
//       // use expect() to read and check I/O output signal values
//       expect(c.io.sum, 10+20)
//     }

//     // Chisel arguments to pass to chiselMainTest
//     def testArgs = RosettaTest.stdArgs
//     // function that instantiates the Module to be tested
//     def testModuleInstFxn = () => { Module(new TestRegOps()) }
//     // function that instantiates the Tester to test the Module
//     def testTesterInstFxn = c => new AddTest(c)

//     // actually run the test
//     chiselMainTest(
//       testArgs,
//       testModuleInstFxn
//     ) {
//       testTesterInstFxn
//     }
//   }
// }

object testhardware {

  // Tester-derived class to give stimulus and observe the outputs for the
  // Module to be tested
  class AddTest(c: TestRegOps) extends PeekPokeTester(c) {
    // use peek() to read I/O output signal values
    peek(c.io.signature)
    // use poke() to set I/O input signal values
    poke(c.io.op(0), 10)
    poke(c.io.op(1), 20)
    // use step() to advance the clock cycle
    step(1)
    // use expect() to read and check I/O output signal values
    expect(c.io.sum, 10+20)
  }

  def testModuleInstFxn = () => { Module(new TestRegOps()) }

  def testTesterInstFxn = c => new AddTest(c)

  def testArgs = RosettaTest.stdArgs

  def main(args: Array[String]): Unit = 
  {
    println("\n================ Testing hardware ================\n")
    chiselMainTest(testArgs, testModuleInstFxn) {testTesterInstFxn}
    println("\n=================== SUCCESS!! ===================\n")
  }

}
