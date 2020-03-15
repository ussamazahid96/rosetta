package rosetta

import chisel3._
import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}

import fpgatidbits.axi._
import fpgatidbits.PlatformWrapper._

import sys.process._

object Settings {
  // Rosetta will use myInstFxn to instantiate your accelerator
  // edit below to change which accelerator will be instantiated
  val myInstFxn = {() => new TestRegOps()}
  // val myInstFxn = {() => new DRAMExample()}
  // val myInstFxn = {() => new MemCpyExample()}
  // val myInstFxn = {() => new BRAMExample(1024)}
  // val myInstFxn = {() => new TestAccumulateVector(4)}

}

// call this object's main method to generate Chisel Verilog and C++ emulation
// output products. all cmdline arguments are passed straight to Chisel.
object ChiselMain {
  def main(args: Array[String]): Unit = {
    chisel3.Driver.execute(args, () => new RosettaWrapper(Settings.myInstFxn))
  }
}

// call this object's main method to generate the register driver for your
// accelerator. expects the following command line arguments, in order:
// 1. path to output directory for generated files
object DriverMain{

  def main(args: Array[String]): Unit = {
    val outDir = args(0)

    // instantiate the wrapper accelerator in a module builder and 
    // generate the register driver
    class drv extends MultiIOModule {
        val t = Module(new RosettaWrapper(Settings.myInstFxn))
        t.generateRegDriver(outDir)
    }
    // disable FIRRTL pass as we are not generating hardware
    val rawargs = Array("--no-run-firrtl")
    chisel3.Driver.execute(rawargs, {() => new drv})
  }
}