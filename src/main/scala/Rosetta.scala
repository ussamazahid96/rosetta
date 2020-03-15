package rosetta

import chisel3._
import chisel3.util._
import chisel3.core.{Element, Aggregate}
import chisel3.experimental.{DataMirror, Direction}

import fpgatidbits.axi._
import fpgatidbits.dma._
import fpgatidbits.ocm._
import fpgatidbits.regfile._
import fpgatidbits.PlatformWrapper._

import scala.collection.mutable.LinkedHashMap

// this file contains the infrastructure that Rosetta instantiates to link
// your accelerator against the existing interfaces on the PYNQ. it also
// defines an (extensible) interface specification for accelerators, in order
// to give easy access to particular I/O functions on the PYNQ board.

// normally there is no need to edit this file to make your own accelerators
// -- just derive from the RosettaAccelerator class. but if you'd like to add new
// interfaces or capabilities into the wrapper, this is the place.


// interface definition for PYNQ accelerators
// in your own accelerator, you can derive from this class and add as many new
// members as you want like this:
// val io = new RosettaAcceleratorIF(0) {
//   val myInput = Input(UInt(4.W))
//   val myOutput = Output(UInt(4.W))
// }
// all signals you add will be automatically mapped to registers for CPU
// access, and a setter (for inputs) or getter (for outputs) function will be
// created in the generated C++ register driver.
// note that you also get some ports/interfaces "for free":
// * an autogenerated signature for version/sanity checking your hardware

// In order to target Ultra96 just change PYNQZ1Params to PYNQU96Params

class RosettaAcceleratorIF(numMemPorts: Int) extends Bundle { 
  // use the signature field for sanity and version checks. auto-generated each
  // time the accelerator verilog is regenerated.
  val memPort = Vec(numMemPorts, new GenericMemoryMasterPort(PYNQU96Params.toMemReqParams()))
  val signature = Output(UInt(PYNQU96Params.csrDataBits.W))
}

// base class for Rosetta accelerators
// what's important here is that the io is not just any Chisel Bundle, but a
// RosettaAcceleratorIF instance. this lets the wrapper to "specialize" what
// some of the ports do,
abstract class RosettaAccelerator() extends Module {
  def io: RosettaAcceleratorIF
  def numMemPorts: Int

  def hexcrc32(s: String): String = {
    import java.util.zip.CRC32
    val crc=new CRC32
    crc.update(s.getBytes)
    crc.getValue.toHexString
  }

  def makeDefaultSignature(): UInt = {
    import java.util.Date
    import java.text.SimpleDateFormat
    val dateFormat = new SimpleDateFormat("yyyyMMdd");
    val date = new Date();
    val dateString = dateFormat.format(date);
    val fullSignature = this.getClass.getSimpleName + "-" + dateString
    val hexSignature = "h" + hexcrc32(fullSignature)
    println("Signature of the hardware is = "+hexcrc32(fullSignature))
    return hexSignature.U
  }
  
  // drive default values for memory read port i
  def plugMemReadPort(i: Int) {
    io.memPort(i).memRdReq.valid := false.B
    io.memPort(i).memRdReq.bits.driveDefaults()
    io.memPort(i).memRdRsp.ready := false.B
  }
  // drive default values for memory write port i
  def plugMemWritePort(i: Int) {
    io.memPort(i).memWrReq.valid := false.B
    io.memPort(i).memWrReq.bits.driveDefaults()
    io.memPort(i).memWrDat.valid := false.B
    io.memPort(i).memWrDat.bits := 0.U
    io.memPort(i).memWrRsp.ready := false.B
  }
}

// the wrapper, which contains the instantiated accelerator, register file,
// and other components that bridge the accelerator to the rest of the PYNQ
class RosettaWrapper(instFxn: () => RosettaAccelerator) extends Module 
{
  val p = PYNQU96Params
  val numPYNQMemPorts: Int = 2
  val io = IO(new Bundle {
    
    // AXI slave interface for control-status registers
    val csr = new AXILiteSlaveIF(p.memAddrBits, p.csrDataBits)
    
    // AXI master interfaces for reading and writing memory
    val mem = Vec(numPYNQMemPorts, new AXIMasterIF(p.memAddrBits, p.memDataBits, p.memIDBits))

  })

  // instantiate the accelerator
  val regWrapperReset = RegInit(false.B)
  val accel = Module(instFxn())

  // ==========================================================================
  // wrapper part 1: register file mappings
  // here we will generate a register file with enough entries to cover the I/O
  // of the accelerator, and link the I/O signals with the respective entry
  // of the register file.
  // =========================================================================
  type RegFileMap = LinkedHashMap[String, Array[Int]]
  // permits controlling the accelerator's reset from both the wrapper's reset,
  // and by using a special register file command (see hack further down :)
  accel.reset := reset.asBool() | regWrapperReset
  // separate out the mem port signals, won't map the to the regfile
  val ownFilter = { x: (String, Data) => !(x._1.startsWith("memPort"))}
  import scala.collection.immutable.ListMap
  
  // two utility functions added for mapping the Vec in IOs to seperate Reg File entries
  def flatten(data: Data): Seq[Element] = data match {
    case elt: Element => Seq(elt)
    case agg: Aggregate => agg.getElements.flatMap(flatten)
  }

  def process_vec(map: ListMap[String, Data]): ListMap[String, Data] = {
    val dynamic_map: LinkedHashMap[String, Data] = LinkedHashMap.empty[String, Data]
    for ((k,v) <- map)
    {
        if (!v.isInstanceOf[Vec[Data]]) { dynamic_map(k) = v}
        else 
        {
            val vectoarr = flatten(v)
            var i = 0
            for (vecs <- vectoarr) 
            { 
                dynamic_map(k+"_"+i) = vecs
                i += 1 
            }
        }
    }
    val immutable_map: ListMap[String, Data] = ListMap.empty[String, Data] ++ dynamic_map
    immutable_map
  }
  
  val temp = process_vec(accel.io.elements)
  val ownIO = ListMap(temp.filter(ownFilter).toSeq.sortBy(_._1):_*)  

  // each I/O is assigned to at least one register index, possibly more if wide
  // round each I/O width to nearest csrWidth multiple, sum, divide by csrWidth
  val wCSR = p.csrDataBits
  def roundMultiple(n: Int, m: Int) = { (n + m-1) / m * m}
  val fxn = {x: (String, Data) => (roundMultiple(x._2.getWidth, wCSR))}
  val numRegs = ownIO.map(fxn).reduce({_+_}) / wCSR

  // instantiate the register file
  val regAddrBits = log2Up(numRegs)
  val regFile = Module(new RegFile(numRegs, regAddrBits, wCSR)).io

  // hack: detect writes to register 0 to control accelerator reset
  val rfcmd = regFile.extIF.cmd
  when(rfcmd.valid & rfcmd.bits.write & rfcmd.bits.regID === 0.U) {
    regWrapperReset := rfcmd.bits.writeData(0)
  }
  println("Generating register file mappings...")
  // traverse the accel I/Os and connect to the register file
  var regFileMap = new RegFileMap
  var allocReg = 0
  // hand-place the signature register at 0
  regFileMap("signature") = Array(allocReg)
  regFile.regIn(allocReg).valid := true.B
  regFile.regIn(allocReg).bits := ownIO("signature")
  println("Signal signature mapped to single reg " + allocReg.toString)
  allocReg += 1

  for((name, bits) <- ownIO) 
  {
    if(name != "signature") 
    {
      val w = bits.getWidth
      if(w > wCSR) 
      {
        // signal is wide, maps to several registers
        val numRegsToAlloc = roundMultiple(w, wCSR) / wCSR
        regFileMap(name) = (allocReg until allocReg + numRegsToAlloc).toArray
        // connect the I/O signal to the register file appropriately
        if(DataMirror.directionOf(bits) == Direction.Input) 
        {
          // concatanate all assigned registers, connect to input
          bits := regFileMap(name).map(regFile.regOut(_)).reduce(Cat(_,_))
          
          // disable internal writes for this register
          for(i <- 0 until numRegsToAlloc) 
          {
            regFile.regIn(allocReg + i).valid := false.B
            regFile.regIn(allocReg + i).bits := 0.U
          }
        } 
        else if(DataMirror.directionOf(bits) == Direction.Output) 
        {
          for(i <- 0 until numRegsToAlloc) 
          {
            regFile.regIn(allocReg + i).valid := true.B
            regFile.regIn(allocReg + i).bits := bits.asUInt()(i*wCSR+wCSR-1, i*wCSR)
          }
        } 
        else 
        { 
          throw new Exception("Wire in IO: "+name) 
        }

        println("Signal " + name + " mapped to regs " + regFileMap(name).map(_.toString).reduce(_+" "+_))
        allocReg += numRegsToAlloc
      } 
      else 
      {
        // signal is narrow enough, maps to a single register
        regFileMap(name) = Array(allocReg)
        // connect the I/O signal to the register file appropriately
        if(DataMirror.directionOf(bits) == Direction.Input) 
        {
          // handle Bool input cases,"multi-bit signal to Bool" error
          if(bits.getWidth == 1) 
          {
            bits := regFile.regOut(allocReg)(0)
          } 
          else 
          { 
            bits := regFile.regOut(allocReg) 
          }
          // disable internal write for this register
          regFile.regIn(allocReg).valid := false.B
          regFile.regIn(allocReg).bits := 0.U

        } 
        else if(DataMirror.directionOf(bits) == Direction.Output) 
        {
          // TODO don't always write (change detect?)
          regFile.regIn(allocReg).valid := true.B
          regFile.regIn(allocReg).bits := bits
        }
        else 
        { 
          throw new Exception("Wire in IO: "+name) 
        }

        println("Signal " + name + " mapped to single reg " + allocReg.toString)
        allocReg += 1
      }
    }
  }

  // memory port adapters and connections
  for(i <- 0 until accel.numMemPorts) {
    // instantiate AXI request and response adapters for the mem interface
    val mrp = p.toMemReqParams()
    // read requests
    val readReqAdp = Module(new AXIMemReqAdp(mrp)).io
    readReqAdp.genericReqIn <> accel.io.memPort(i).memRdReq

    io.mem(i).ARADDR := readReqAdp.axiReqOut.bits.ADDR   
    io.mem(i).ARSIZE := readReqAdp.axiReqOut.bits.SIZE
    io.mem(i).ARLEN := readReqAdp.axiReqOut.bits.LEN
    io.mem(i).ARBURST := readReqAdp.axiReqOut.bits.BURST
    io.mem(i).ARID := readReqAdp.axiReqOut.bits.ID
    io.mem(i).ARLOCK := readReqAdp.axiReqOut.bits.LOCK     
    io.mem(i).ARCACHE := readReqAdp.axiReqOut.bits.CACHE
    io.mem(i).ARPROT := readReqAdp.axiReqOut.bits.PROT
    io.mem(i).ARQOS := readReqAdp.axiReqOut.bits.QOS
    io.mem(i).ARVALID := readReqAdp.axiReqOut.valid
    readReqAdp.axiReqOut.ready := io.mem(i).ARREADY

    // read responses
    val readRspAdp = Module(new AXIReadRspAdp(mrp)).io

    readRspAdp.axiReadRspIn.bits.RDATA := io.mem(i).RDATA
    readRspAdp.axiReadRspIn.bits.RID := io.mem(i).RID
    readRspAdp.axiReadRspIn.bits.RLAST := io.mem(i).RLAST
    readRspAdp.axiReadRspIn.bits.RRESP := io.mem(i).RRESP
    readRspAdp.axiReadRspIn.valid := io.mem(i).RVALID
    io.mem(i).RREADY := readRspAdp.axiReadRspIn.ready

    readRspAdp.genericRspOut <> accel.io.memPort(i).memRdRsp
    
    // write requests
    val writeReqAdp = Module(new AXIMemReqAdp(mrp)).io
    writeReqAdp.genericReqIn <> accel.io.memPort(i).memWrReq

    io.mem(i).AWADDR := writeReqAdp.axiReqOut.bits.ADDR   
    io.mem(i).AWSIZE := writeReqAdp.axiReqOut.bits.SIZE
    io.mem(i).AWLEN := writeReqAdp.axiReqOut.bits.LEN
    io.mem(i).AWBURST := writeReqAdp.axiReqOut.bits.BURST
    io.mem(i).AWID := writeReqAdp.axiReqOut.bits.ID
    io.mem(i).AWLOCK := writeReqAdp.axiReqOut.bits.LOCK     
    io.mem(i).AWCACHE := writeReqAdp.axiReqOut.bits.CACHE
    io.mem(i).AWPROT := writeReqAdp.axiReqOut.bits.PROT
    io.mem(i).AWQOS := writeReqAdp.axiReqOut.bits.QOS
    io.mem(i).AWVALID := writeReqAdp.axiReqOut.valid
    writeReqAdp.axiReqOut.ready := io.mem(i).AWREADY

    // write data
    // add a small write data queue to ensure we can provide both req ready and
    // data ready at the same time (otherwise this is up to the AXI slave)
    val wrDataQ = FPGAQueue(accel.io.memPort(i).memWrDat, 2)
    // TODO handle this with own adapter?
    io.mem(i).WDATA := wrDataQ.bits
    // TODO fix this: forces all writes bytelanes valid!
    io.mem(i).WSTRB := ~(0.U((p.memDataBits/8).W))

    // TODO fix this: write bursts won't work properly!
    io.mem(i).WLAST := true.B
    io.mem(i).WVALID := wrDataQ.valid
    wrDataQ.ready := io.mem(i).WREADY
    
    // write responses
    val writeRspAdp = Module(new AXIWriteRspAdp(mrp)).io
    writeRspAdp.axiWriteRspIn.bits.BID := io.mem(i).BID
    writeRspAdp.axiWriteRspIn.bits.BRESP := io.mem(i).BRESP
    writeRspAdp.axiWriteRspIn.valid := io.mem(i).BVALID
    io.mem(i).BREADY := writeRspAdp.axiWriteRspIn.ready
    writeRspAdp.genericRspOut <> accel.io.memPort(i).memWrRsp
  }


  // the accelerator may be using fewer memory ports than what the platform
  // exposes; plug the unused ones
  for(i <- accel.numMemPorts until numPYNQMemPorts) {
    println("Plugging unused memory port "+ i.toString)
    io.mem(i).driveDefaults()
  }

  // ==========================================================================
  // wrapper part 2: bridging to PYNQ signals
  // here we will instantiate various adapters and similar components to make
  // our accelerator be able to talk to the various interfaces on the PYNQ
  // ==========================================================================
  // rename signals to support Vivado interface inference
  io.csr.suggestName("csr")

  // AXI regfile read/write logic
  // slow and clumsy, but ctrl/status is not supposed to be performance-
  // critical anyway
  io.csr.AWREADY := false.B
  io.csr.WREADY := false.B
  io.csr.BVALID := false.B
  io.csr.BRESP := 0.U
  io.csr.ARREADY := false.B
  io.csr.RVALID := false.B
  io.csr.RDATA := regFile.extIF.readData.bits
  io.csr.RRESP := 0.U

  regFile.extIF.cmd.valid := false.B
  regFile.extIF.cmd.bits.driveDefaults()

  val sRead :: sReadRsp :: sWrite :: sWriteD :: sWriteRsp :: Nil = Enum(5)
  
  val regState = RegInit(sRead)

  val regModeWrite = RegInit(false.B)
  val regRdReq = RegInit(false.B)
  val regRdAddr = RegInit(0.U(p.memAddrBits.W))
  val regWrReq = RegInit(false.B)
  val regWrAddr = RegInit(0.U(p.memAddrBits.W))
  val regWrData = RegInit(0.U(p.csrDataBits.W))
  // AXI typically uses byte addressing, whereas regFile indices are
  // element indices -- so the AXI addr needs to be divided by #bytes
  // in one element to get the regFile ind
  // Note that this permits reading/writing only the entire width of one
  // register

  val addrDiv = (p.csrDataBits/8).U
  
  when(!regModeWrite) {
    regFile.extIF.cmd.valid := regRdReq
    regFile.extIF.cmd.bits.read := true.B
    regFile.extIF.cmd.bits.regID := regRdAddr / addrDiv
  } .otherwise {
    regFile.extIF.cmd.valid := regWrReq
    regFile.extIF.cmd.bits.write := true.B
    regFile.extIF.cmd.bits.regID := regWrAddr / addrDiv
    regFile.extIF.cmd.bits.writeData := regWrData
  }

  // state machine for bridging register file reads/writes to AXI slave ops
  switch(regState) {
      is(sRead) {
        io.csr.ARREADY := true.B

        when(io.csr.ARVALID) {
          regRdReq := true.B
          regRdAddr := io.csr.ARADDR
          regModeWrite := false.B
          regState := sReadRsp
        }.otherwise {
          regState := sWrite
        }
      }

      is(sReadRsp) {
        io.csr.RVALID := regFile.extIF.readData.valid
        when (io.csr.RREADY & regFile.extIF.readData.valid) {
          regState := sWrite
          regRdReq := false.B
        }
      }

      is(sWrite) {
        io.csr.AWREADY := true.B

        when(io.csr.AWVALID) {
          regModeWrite := true.B
          regWrReq := false.B // need to wait until data is here
          regWrAddr := io.csr.AWADDR
          regState := sWriteD
        } .otherwise {
          regState := sRead
        }
      }

      is(sWriteD) {
        io.csr.WREADY := true.B
        when(io.csr.WVALID) {
          regWrData := io.csr.WDATA
          regWrReq := true.B // now we can set the request
          regState := sWriteRsp
        }
      }

      is(sWriteRsp) {
        io.csr.BVALID := true.B
        when(io.csr.BREADY) {
          regWrReq := false.B
          regState := sRead
        }
      }
    }

    // ==========================================================================
    // wrapper part 3: pynq driver generation functions
    // these functions will be called to generate python code that accesses the
    // register file that we have instantiated via pynq package
    // ==========================================================================

    def makeRegReadFxn(regName: String): String = {
      var fxnStr: String = ""
      val regs = regFileMap(regName)
      if(regs.size == 1) 
      {
        // single register read
        fxnStr += "\tdef get_" + regName + "(self):\n"
        fxnStr += "\t\treturn self.mmio.read(self.offset*" + regs(0).toString + ")\n"
      } 
      else if(regs.size == 2) 
      {
        // two-register read
        // TODO this uses a hardcoded assumption about wCSR=32
        if(wCSR != 32) 
          throw new Exception("Violating assumption on wCSR=32")
        fxnStr += "\tdef get_" + regName + "(self):\n"
        fxnStr += "\t\tvalue_high = self.mmio.read(self.offset*"+regs(1).toString+")\n"
        fxnStr += "\t\tvalue_low = self.mmio.read(self.offset*"+regs(0).toString+")\n"
        fxnStr += "\t\treturn value_high << 32 | value_low\n"
      } 
      else 
      { 
        throw new Exception("Multi-reg reads not yet implemented") 
      }
      return fxnStr
    }

    def makeRegWriteFxn(regName: String): String = {
      var fxnStr: String = ""
      val regs = regFileMap(regName)
      if(regs.size == 1) 
      {
        // single register write
        fxnStr += "\tdef set_" + regName + "(self, value):\n"
        fxnStr += "\t\tself.mmio.write(self.offset*"+ regs(0).toString + ", value)\n"
      } 
      else if(regs.size == 2) 
      {
        // two-register write
        // TODO this uses a hardcoded assumption about wCSR=32
        if(wCSR != 32) 
          throw new Exception("Violating assumption on wCSR=32")
        fxnStr += "\tdef set_" + regName + "(self, value):\n"
        fxnStr += "\t\tvalue_high = value >> 32\n"
        fxnStr += "\t\tvalue_low = value & 0xffffffff\n"
        fxnStr += "\t\tself.mmio.write(self.offset*"+regs(0).toString+", value_high)\n"
        fxnStr += "\t\tself.mmio.write(self.offset*"+regs(1).toString+", value_low)\n"
      } 
      else { throw new Exception("Multi-reg writes not yet implemented") }

      return fxnStr
    }

    def generateRegDriver(targetDir: String) = {
      var driverStr: String = ""
      val driverName: String = accel.name
      var readWriteFxns: String = ""
      for((name, bits) <- ownIO) 
      {
        if(DataMirror.directionOf(bits) == Direction.Input) 
        {
          readWriteFxns += makeRegWriteFxn(name) + "\n"
          // readWriteFxns += makeRegReadFxn(name) + "\n"
        } 
        else if(DataMirror.directionOf(bits) == Direction.Output) 
        {
          readWriteFxns += makeRegReadFxn(name) + "\n"
        }
      }

      def statRegToCPPMapEntry(regName: String): String = {
        val inds = regFileMap(regName).map(_.toString).reduce(_ + ", " + _)
        return s""" {"$regName", {$inds}} """
      }
      val statRegs = ownIO.filter(x => DataMirror.directionOf(x._2) == Direction.Output).map(_._1)
      val statRegMap = statRegs.map(statRegToCPPMapEntry).reduce(_ + ", " + _)

      driverStr += s"""
import pynq

class ${driverName}:
\tdef __init__(self):
\t\tself.overlay = pynq.Overlay('rosetta.bit', download=True)
\t\tself.mmio = self.overlay.RosettaWrapper_0.io_csr.mmio
\t\tself.offset = 4

$readWriteFxns
      """

      import java.io._
      val writer = new PrintWriter(new File(targetDir+"/"+driverName+".py" ))
      writer.write(driverStr)
      writer.close()
      println("=======> Driver written to "+driverName+".py")

    }
}
