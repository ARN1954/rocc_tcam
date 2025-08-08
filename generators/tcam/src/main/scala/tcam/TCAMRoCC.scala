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

  // Debug: observe incoming instruction only when accepted
  when(io.cmd.fire) {
    printf("TCAMRoCC DEBUG: FIRE opcode=0x%x funct=0x%x rd=%d\n",
      cmd.bits.inst.opcode, cmd.bits.inst.funct, cmd.bits.inst.rd)
  }

  // Small FSM to properly handshake cmd/resp and drive TCAM for one cycle
  val sIdle :: sExec :: sResp :: Nil = Enum(3)
  val state = RegInit(sIdle)

  // Latched fields
  val rdReg     = Reg(UInt(5.W))
  val wmaskReg  = Reg(UInt(4.W))
  val addrReg   = Reg(UInt(28.W))
  val wdataReg  = Reg(UInt(32.W))
  val inwebReg  = Reg(Bool()) // desired web ACTIVE-HIGH semantic at RoCC level
  val incsbReg  = Reg(Bool()) // desired csb ACTIVE-HIGH semantic at RoCC level
  val respData  = Reg(UInt(5.W))

  // Defaults
  io.cmd.ready := (state === sIdle)
  io.busy := (state =/= sIdle)

  io.resp.valid := (state === sResp)
  io.resp.bits.rd := rdReg
  io.resp.bits.data := respData

  // Drive TCAM defaults (inactive)
  tcam.io.in_clk   := clock
  tcam.io.in_wmask := 0.U
  tcam.io.in_addr  := 0.U
  tcam.io.in_wdata := 0.U
  // Active-low controls at the TCAM interface
  tcam.io.in_web   := true.B   // deassert write (active-low)
  tcam.io.in_csb   := true.B   // deassert chip select (active-low)

  switch(state) {
    is(sIdle) {
      when(cmd.fire) {
        // Extract and latch fields
        rdReg    := cmd.bits.inst.rd
        wmaskReg := cmd.bits.rs1(31, 28)
        addrReg  := cmd.bits.rs1(27, 0)
        wdataReg := cmd.bits.rs2(31, 0)
        inwebReg := cmd.bits.inst.funct(1)
        incsbReg := cmd.bits.inst.funct(0)

        // Extra trace
        printf("TCAMRoCC: LATCH rs1=0x%x rs2=0x%x funct=0x%x wmask=0x%x addr=0x%x inweb=%b incsb=%b\n",
          cmd.bits.rs1, cmd.bits.rs2, cmd.bits.inst.funct, cmd.bits.rs1(31,28), cmd.bits.rs1(27,0), cmd.bits.inst.funct(1), cmd.bits.inst.funct(0))

        state := sExec
      }
    }

    is(sExec) {
      // Map RoCC-level active-high enables to TCAM active-low signals
      // webActive=1 means perform write; csbActive=1 means enable chip
      // Cases (by prior convention):
      //   Cat(inweb, incsb):
      //   00 -> Write
      //   01 -> Read
      //   10 -> Search
      //   11 -> Reserved
      val op = Cat(inwebReg, incsbReg)

      // Common data/addr/wmask
      tcam.io.in_wmask := wmaskReg
      tcam.io.in_addr  := addrReg
      tcam.io.in_wdata := wdataReg

      switch(op) {
        is("b00".U) { // Write
          tcam.io.in_web := false.B // assert write (active-low)
          tcam.io.in_csb := false.B // enable chip (active-low)
          printf("TCAMRoCC: EXEC Write wmask=0x%x addr=0x%x wdata=0x%x\n", wmaskReg, addrReg, wdataReg)
        }
        is("b01".U) { // Read
          tcam.io.in_web := true.B  // no write
          tcam.io.in_csb := false.B // enable chip
          printf("TCAMRoCC: EXEC Read  wmask=0x%x addr=0x%x\n", wmaskReg, addrReg)
        }
        is("b10".U) { // Search
          tcam.io.in_web := true.B  // no write
          tcam.io.in_csb := false.B // enable chip
          printf("TCAMRoCC: EXEC Search wmask=0x%x addr=0x%x wdata=0x%x\n", wmaskReg, addrReg, wdataReg)
        }
        is("b11".U) { // Reserved/No-op
          tcam.io.in_web := true.B
          tcam.io.in_csb := true.B
          printf("TCAMRoCC: EXEC Reserved\n")
        }
      }

      // Capture output this cycle and advance to respond
      respData := tcam.io.out_pma
      state := sResp
    }

    is(sResp) {
      when(io.resp.ready) {
        state := sIdle
      }
    }
  }
}
