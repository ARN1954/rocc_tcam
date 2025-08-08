package tcam

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile._
import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket.{TLBConfig, HellaCacheArbiter}
import freechips.rocketchip.util.DecoupledHelper
import freechips.rocketchip.rocket.constants.MemoryOpConstants
import freechips.rocketchip.tilelink._

class TCAMRoCC(opcodes: OpcodeSet, tcamParams: TCAMParams)(implicit p: Parameters)
    extends LazyRoCC(opcodes) {
  override lazy val module = new TCAMRoCCModule(this, tcamParams)
}

class TCAMRoCCModule(outer: TCAMRoCC, tcamParams: TCAMParams)
    extends LazyRoCCModuleImp(outer) {
  val cmd = io.cmd
  val tcam = Module(new TCAMBlackBox(tcamParams))
  
  // Debug print to see raw instruction fields
  when (cmd.valid) {
    printf("TCAMRoCC DEBUG: cmd.bits.inst.opcode = 0x%x, cmd.bits.inst.funct = 0x%x\n",
      cmd.bits.inst.opcode, cmd.bits.inst.funct)
  }

  // Default: ready for new command
  io.cmd.ready := !cmd.valid || io.resp.ready
  io.resp.valid := false.B
  io.resp.bits.rd := cmd.bits.inst.rd
  io.resp.bits.data := 0.U
  io.busy := false.B

  // Connect TCAM interface (default values)
  tcam.io.in_clk := clock
  tcam.io.in_wmask := 0.U
  tcam.io.in_addr := 0.U
  tcam.io.in_wdata := 0.U
  tcam.io.in_web := false.B
  tcam.io.in_csb := false.B

  when (cmd.valid) {
    // Debug print to see raw instruction fields
    printf("TCAMRoCC DEBUG: cmd.bits.inst.opcode = 0x%x, cmd.bits.inst.funct = 0x%x\n",
      cmd.bits.inst.opcode, cmd.bits.inst.funct)
      
    val wmask   = cmd.bits.rs1(31,28)
    val address = cmd.bits.rs1(27,0)
    val wdata   = cmd.bits.rs2(31,0)
    val inweb   = cmd.bits.inst.funct(1)
    val incsb   = cmd.bits.inst.funct(0)
    val op      = Cat(inweb, incsb)
    
    // Debug print to trace command flow
    printf("TCAMRoCC: rs1=0x%x, rs2=0x%x, funct=0x%x, wmask=0x%x, address=0x%x, inweb=%b, incsb=%b\n",
      cmd.bits.rs1, cmd.bits.rs2, cmd.bits.inst.funct, wmask, address, inweb, incsb)
      
    // Additional debug to see the operation being performed
    printf("TCAMRoCC DEBUG: op = 0x%x (inweb=%b, incsb=%b)\n", op, inweb, incsb)

    switch(op) {
      is("b00".U) { // inweb=0, incsb=0
        tcam.io.in_wmask := wmask
        tcam.io.in_addr  := address
        tcam.io.in_wdata := wdata
        tcam.io.in_web   := false.B
        tcam.io.in_csb   := false.B
        io.resp.valid := true.B
        io.resp.bits.data := tcam.io.out_pma
        printf("TCAMRoCC: Write operation - wmask=0x%x, addr=0x%x, wdata=0x%x\n", wmask, address, wdata)
      }
      is("b01".U) { // inweb=0, incsb=1
        tcam.io.in_wmask := wmask
        tcam.io.in_addr  := address
        tcam.io.in_wdata := wdata
        tcam.io.in_web   := false.B
        tcam.io.in_csb   := true.B
        io.resp.valid := true.B
        io.resp.bits.data := tcam.io.out_pma
        printf("TCAMRoCC: Read operation - wmask=0x%x, addr=0x%x, wdata=0x%x\n", wmask, address, wdata)
      }
      is("b10".U) { // inweb=1, incsb=0
        tcam.io.in_wmask := wmask
        tcam.io.in_addr  := address
        tcam.io.in_wdata := wdata
        tcam.io.in_web   := true.B
        tcam.io.in_csb   := false.B
        io.resp.valid := true.B
        io.resp.bits.data := tcam.io.out_pma
        printf("TCAMRoCC: Search operation - wmask=0x%x, addr=0x%x, wdata=0x%x\n", wmask, address, wdata)
      }
      is("b11".U) { // inweb=1, incsb=1
        tcam.io.in_wmask := wmask
        tcam.io.in_addr  := address
        tcam.io.in_wdata := wdata
        tcam.io.in_web   := true.B
        tcam.io.in_csb   := true.B
        io.resp.valid := true.B
        io.resp.bits.data := tcam.io.out_pma
        printf("TCAMRoCC: Reserved operation - wmask=0x%x, addr=0x%x, wdata=0x%x\n", wmask, address, wdata)
      }
    }
  }
}
