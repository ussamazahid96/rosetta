package fpgatidbits.streams

import chisel3._
import chisel3.util._


abstract class PrintableBundle extends Bundle {
  def printfStr: String
  def printfElems: () => Seq[Data]
}