package chipyard.example

import sys.process._

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, BaseModule}
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.{BaseSubsystem, PBUS}
import org.chipsalliance.cde.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

// TCAM Parameters
case class TCAMParams(
  address: BigInt = 0x4000,
  width: Int = 32,
  entries: Int = 64,         
  keyWidth: Int = 28
)

case object TCAMKey extends Field[Option[TCAMParams]](None)

class TCAMIO extends Bundle{
  val clk_i = Input(Clock())
  val csb_i = Input(Bool())
  val web_i = Input(Bool())
  val wmask_i = Input(UInt(4.W))
  val addr_i = Input(UInt(28.W))
  val wdata_i = Input(UInt(32.W))
  val rdata_o = Output(UInt(5.W))
}

class TCAMBlackBox extends BlackBox with HasBlackBoxPath {
  val io = IO(new TCAMIO)
  
  val chipyardDir = System.getProperty("user.dir")
  val tcamDir = s"$chipyardDir/generators/chipyard/src/main/resources/vsrc/tcam"
  
  // Add each Verilog file
  //addPath(s"$tcamDir/and_gate.sv")
  //addPath(s"$tcamDir/priority_encoder_64x6.sv")
  //addPath(s"$tcamDir/sky130_sram_1kbyte_1rw1r_32x256_8.sv")
  //addPath(s"$tcamDir/tcam_7x64.sv")
  //addPath(s"$tcamDir/TCAMBlackBox.sv")
  addPath(s"$tcamDir/TCAMBlackBox.sv")
  addPath(s"$tcamDir/sky130_sram_1kbyte_1rw1r_32x256_8.sv")
}

class TCAMTL(params: TCAMParams, beatBytes: Int)(implicit p: Parameters) extends ClockSinkDomain(ClockSinkParameters())(p) {
  val device = new SimpleDevice("tcam", Seq("ucbbar,tcam")) 
  val node = TLRegisterNode(Seq(AddressSet(params.address, 4096-1)), device, "reg/control", beatBytes=beatBytes)
    override lazy val module = new TCAMImpl
  class TCAMImpl extends Impl {
    withClockAndReset(clock, reset) {
      val tcam = Module(new TCAMBlackBox)
      
      
      val status = RegInit(0.U(5.W))     
      val control = RegInit(0.U(8.W))    
      val writeData = RegInit(0.U(32.W))  
      val address = RegInit(0.U(28.W))

      // Connect Chisel signals to new Verilog port names
      tcam.io.clk_i := clock
      tcam.io.csb_i := ~control(0)
      tcam.io.web_i := ~control(1)
      tcam.io.wmask_i := control(7, 4)
      tcam.io.addr_i := address
      tcam.io.wdata_i := writeData

      status := tcam.io.rdata_o

      
      node.regmap(
        0x00 -> Seq(RegField.r(5, status)),
        0x04 -> Seq(RegField(8, control)),
        0x08 -> Seq(RegField(32, writeData)),
        0x0C -> Seq(RegField(28, address)),
      )
    }
  }
}

trait CanHavePeripheryTCAM { this: BaseSubsystem =>
  private val portName = "tcam"
  private val pbus = locateTLBusWrapper(PBUS)
  p(TCAMKey).map { params =>
    val tcam = LazyModule(new TCAMTL(params, pbus.beatBytes)(p))
    tcam.clockNode := pbus.fixedClockNode
    pbus.coupleTo(portName) {
      TLInwardClockCrossingHelper("tcam_crossing", tcam, tcam.node)(SynchronousCrossing()) :=
      TLFragmenter(pbus.beatBytes, pbus.blockBytes) := _
    }
  }
}

class WithTCAM extends Config((site, here, up) => {
  case TCAMKey => Some(TCAMParams())
}) 
