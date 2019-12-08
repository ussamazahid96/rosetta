package rosetta

import chisel3._
import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}

import fpgatidbits.axi._
import fpgatidbits.PlatformWrapper._

import sys.process._

object Settings {
  // Rosetta will use myInstFxn to instantiate your accelerator
  // edit below to change which accelerator will be instantiated
  // val myInstFxn = {() => new TestRegOps()}
  // val myInstFxn = {() => new BRAMExample(1024)}
  val myInstFxn = {() => new DRAMExample()}

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
// 2. path to Rosetta drivers
object DriverMain{
  // utility functions to copy files inside Scala
  def fileCopy(from: String, to: String) = {
    s"cp -f $from $to" !
  }

  def fileCopyBulk(fromDir: String, toDir: String, fileNames: Seq[String]) = {
    for(f <- fileNames)
      fileCopy(s"$fromDir/$f", s"$toDir/$f")
  }

  // a list of files that will be needed for compiling drivers for platform
  val baseDriverFiles: Array[String] = Array[String](
    "platform.h", "wrapperregdriver.h"
  )
  val platformDriverFiles = baseDriverFiles ++ Array[String](
    "platform-xlnk.cpp", "xlnkdriver.hpp"
  )

  def main(args: Array[String]): Unit = {
    val outDir = args(0)
    val drvSrcDir = args(1)

    // instantiate the wrapper accelerator in a module builder and 
    // generate the register driver
    class drv extends MultiIOModule {
        val t = Module(new RosettaWrapper(Settings.myInstFxn))
        t.generateRegDriver(outDir)
    }
    // disable FIRRTL pass as we are not generating hardware
    val rawargs = Array("--no-run-firrtl")
    chisel3.Driver.execute(rawargs, {() => new drv})

    // copy additional driver files
    fileCopyBulk(drvSrcDir, outDir, platformDriverFiles)
  }
}