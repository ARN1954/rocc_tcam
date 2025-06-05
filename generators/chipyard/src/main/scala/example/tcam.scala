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
import chipyard.harness.{BuildTop}

// TCAM Parameters
case class TCAMParams(
  address: BigInt = 0x2000,  
  width: Int = 32,           
  entries: Int = 64,         
  keyWidth: Int = 28         
)

// TCAM Key
case object TCAMKey extends Field[Option[TCAMParams]](None)

// TCAM BlackBox Interface
class TCAMBlackBoxIO extends Bundle {
  val in_clk = Input(Clock())           
  val in_csb = Input(Bool())            
  val in_web = Input(Bool())            
  val in_wmask = Input(UInt(4.W))       
  val in_addr = Input(UInt(28.W))       
  val in_wdata = Input(UInt(32.W))      
  val out_pma = Output(UInt(6.W))       
}

// TCAM BlackBox Wrapper
class TCAMBlackBox extends BlackBox with HasBlackBoxResource {
  val io = IO(new TCAMBlackBoxIO)
  
  addResource("/vsrc/tcam/top_tcam_mem_64x28.sv")
  addResource("/vsrc/tcam/tcam_7x64.sv")
  addResource("/vsrc/tcam/priority_encoder_64x6.sv")
  addResource("/vsrc/tcam/sky130_sram_1kbyte_1rw1r_32x256_8.sv")
  addResource("/vsrc/tcam/and_gate.sv")
}

// TCAM TileLink Wrapper
class TCAMTL(params: TCAMParams, beatBytes: Int)(implicit p: Parameters) extends ClockSinkDomain(ClockSinkParameters())(p) {
  val device = new SimpleDevice("tcam", Seq("ucbbar,tcam"))
  
  val node = TLRegisterNode(
    address = Seq(AddressSet(params.address, 4096-1)),
    device = device,
    beatBytes = beatBytes
  )

  override lazy val module = new TCAMImpl
  class TCAMImpl extends Impl {
    withClockAndReset(clock, reset) {
      val tcam = Module(new TCAMBlackBox)
      
      
      val status = RegInit(0.U(32.W))     
      val control = RegInit(0.U(32.W))    
      val writeData = RegInit(0.U(32.W))  
      val readData = RegInit(0.U(32.W))   
      val address = RegInit(0.U(32.W))    

      
      tcam.io.in_clk := clock
      tcam.io.in_csb := ~control(0)       
      tcam.io.in_web := ~control(1)       
      tcam.io.in_wmask := control(7, 4)   
      tcam.io.in_addr := address(27, 0)   
      tcam.io.in_wdata := writeData       

      
      status := tcam.io.out_pma

      
      node.regmap(
        TCAMRegs.status -> Seq(RegField.r(32, status)),
        TCAMRegs.control -> Seq(RegField.w(32, control)),
        TCAMRegs.writeData -> Seq(RegField.w(32, writeData)),
        TCAMRegs.readData -> Seq(RegField.r(32, readData)),
        TCAMRegs.address -> Seq(RegField.w(32, address))
      )
    }
  }
}

// Register offsets
object TCAMRegs {
  val status = 0x00
  val control = 0x04
  val writeData = 0x08
  val readData = 0x0C
  val address = 0x10
}

// Trait to add TCAM to the system
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

// Configuration fragment to enable TCAM
class WithTCAM extends Config((site, here, up) => {
  case TCAMKey => Some(TCAMParams())
}) 
