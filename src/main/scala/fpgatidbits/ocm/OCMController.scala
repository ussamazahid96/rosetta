package fpgatidbits.ocm

import chisel3._
import chisel3.util._


class OCMRequest(writeWidth: Int, addrWidth: Int) extends Bundle {
  val addr = UInt(addrWidth.W)
  val writeData = UInt(writeWidth.W)
  val writeEn = Bool()

  override def cloneType = {new OCMRequest(writeWidth, addrWidth).asInstanceOf[this.type]}
}

class OCMResponse(readWidth: Int) extends Bundle {
  val readData = UInt(readWidth.W)

  override def cloneType = {new OCMResponse(readWidth).asInstanceOf[this.type]}
}

// slave interface is just the master interface flipped
class OCMSlaveIF(writeWidth: Int, readWidth: Int, addrWidth: Int) extends Bundle {
  val req = Input(new OCMRequest(writeWidth, addrWidth))
  val rsp = Output(new OCMResponse(readWidth))

  override def cloneType = { new OCMSlaveIF(writeWidth, readWidth, addrWidth).asInstanceOf[this.type] }
}