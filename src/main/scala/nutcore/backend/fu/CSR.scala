/**************************************************************************************
* Copyright (c) 2020 Institute of Computing Technology, CAS
* Copyright (c) 2020 University of Chinese Academy of Sciences
* 
* NutShell is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2. 
* You may obtain a copy of Mulan PSL v2 at:
*             http://license.coscl.org.cn/MulanPSL2 
* 
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER 
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR 
* FIT FOR A PARTICULAR PURPOSE.  
*
* See the Mulan PSL v2 for more details.  
***************************************************************************************/

package nutcore

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import utils._
import top.Settings

object CSROpType {
  def jmp  = "b000".U
  def wrt  = "b001".U
  def set  = "b010".U
  def clr  = "b011".U
  def wrti = "b101".U
  def seti = "b110".U
  def clri = "b111".U
}

trait HasCSRConst {
  // User Trap Setup
  val Ustatus       = 0x000 
  val Uie           = 0x004
  val Utvec         = 0x005
  
  // User Trap Handling
  val Uscratch      = 0x040
  val Uepc          = 0x041
  val Ucause        = 0x042
  val Utval         = 0x043
  val Uip           = 0x044

  // User Floating-Point CSRs (not implemented)
  val Fflags        = 0x001
  val Frm           = 0x002
  val Fcsr          = 0x003

  // User Counter/Timers (not implemented)
  val Cycle         = 0xC00
  val Time          = 0xC01
  val Instret       = 0xC02
  
  // User DASICS Registers
  val DasicsLibCfg0 = 0x881
  val DasicsLibCfg1 = 0x882
  val DasicsLibBoundBase = 0x883  // 16 sets of DASICS-Lib registers, upper-lower

  val DasicsMaincallEntry = 0x8A3
  val DasicsReturnPC = 0x8A4
  val DasicsFreeZoneReturnPC = 0x8A5

  // Supervisor Trap Setup
  val Sstatus       = 0x100
  val Sedeleg       = 0x102
  val Sideleg       = 0x103
  val Sie           = 0x104
  val Stvec         = 0x105
  val Scounteren    = 0x106

  // Supervisor Trap Handling
  val Sscratch      = 0x140
  val Sepc          = 0x141
  val Scause        = 0x142
  val Stval         = 0x143
  val Sip           = 0x144

  // Supervisor Protection and Translation
  val Satp          = 0x180

  // Supervisor DASICS Protection
  val DasicsUMainCfg     = 0x5C0
  val DasicsUMainBoundHi = 0x5C1
  val DasicsUMainBoundLo = 0x5C2

  // Machine Information Registers 
  val Mvendorid     = 0xF11 
  val Marchid       = 0xF12 
  val Mimpid        = 0xF13 
  val Mhartid       = 0xF14 

  // Machine Trap Setup
  val Mstatus       = 0x300
  val Misa          = 0x301
  val Medeleg       = 0x302
  val Mideleg       = 0x303
  val Mie           = 0x304
  val Mtvec         = 0x305
  val Mcounteren    = 0x306 

  // Machine Trap Handling
  val Mscratch      = 0x340 
  val Mepc          = 0x341
  val Mcause        = 0x342
  val Mtval         = 0x343
  val Mip           = 0x344

  // Machine Memory Protection
  // TBD
  val Pmpcfg0       = 0x3A0
  val Pmpcfg1       = 0x3A1
  val Pmpcfg2       = 0x3A2
  val Pmpcfg3       = 0x3A3
  val PmpaddrBase   = 0x3B0 

  // Machine Counter/Timers 
  // Currently, NutCore uses perfcnt csr set instead of standard Machine Counter/Timers 
  // 0xB80 - 0x89F are also used as perfcnt csr

  // Machine Counter Setup (not implemented)
  // Debug/Trace Registers (shared with Debug Mode) (not implemented)
  // Debug Mode Registers (not implemented)

  // Machine DASICS Registers
  val DasicsSMainCfg     = 0xBC0
  val DasicsSMainBoundHi = 0xBC1
  val DasicsSMainBoundLo = 0xBC2

  def privEcall  = 0x000.U
  def privEbreak = 0x001.U
  def privMret   = 0x302.U
  def privSret   = 0x102.U
  def privUret   = 0x002.U

  def ModeM     = 0x3.U
  def ModeH     = 0x2.U
  def ModeS     = 0x1.U
  def ModeU     = 0x0.U

  def IRQ_UEIP  = 0 
  def IRQ_SEIP  = 1
  def IRQ_MEIP  = 3 

  def IRQ_UTIP  = 4 
  def IRQ_STIP  = 5 
  def IRQ_MTIP  = 7 

  def IRQ_USIP  = 8 
  def IRQ_SSIP  = 9 
  def IRQ_MSIP  = 11 

  val IntPriority = Seq(
    IRQ_MEIP, IRQ_MSIP, IRQ_MTIP,
    IRQ_SEIP, IRQ_SSIP, IRQ_STIP,
    IRQ_UEIP, IRQ_USIP, IRQ_UTIP
  )
}

trait HasExceptionNO {
  def instrAddrMisaligned = 0
  def instrAccessFault    = 1
  def illegalInstr        = 2
  def breakPoint          = 3
  def loadAddrMisaligned  = 4
  def loadAccessFault     = 5
  def storeAddrMisaligned = 6
  def storeAccessFault    = 7
  def ecallU              = 8
  def ecallS              = 9
  def ecallM              = 11
  def instrPageFault      = 12
  def loadPageFault       = 13
  def storePageFault      = 15

  // Customized exceptions added by DASICS mechanism
  def dasicsUInstrAccessFault = 24
  def dasicsSInstrAccessFault = 25

  def dasicsULoadAccessFault  = 26
  def dasicsSLoadAccessFault  = 27

  def dasicsUStoreAccessFault = 28
  def dasicsSStoreAccessFault = 29
  def dasicsUEcallFault = 30
  def dasicsSEcallFault = 31

  val ExcPriority = Seq(
      breakPoint, // TODO: different BP has different priority
      instrPageFault,
      instrAccessFault,
      illegalInstr,
      instrAddrMisaligned,
      ecallM, ecallS, ecallU,
      storeAddrMisaligned,
      loadAddrMisaligned,
      storePageFault,
      loadPageFault,
      storeAccessFault,
      loadAccessFault,

      // Customized DASICS exceptions
      dasicsSInstrAccessFault, dasicsSLoadAccessFault, dasicsSStoreAccessFault,
      dasicsUInstrAccessFault, dasicsULoadAccessFault, dasicsUStoreAccessFault,
      dasicsUEcallFault, dasicsSEcallFault
  )
}


class CSRIO extends FunctionUnitIO {
  val cfIn = Flipped(new CtrlFlowIO)
  val redirect = new RedirectIO
  // for exception check
  val instrValid = Input(Bool())
  val isBackendException = Input(Bool())
  val lsuIsLoad = Input(Bool())
  val lsuInSTrustedZone = Input(Bool())
  val lsuInUTrustedZone = Input(Bool())
  val lsuPermitLibLoad = Input(Bool())
  val lsuPermitLibStore = Input(Bool())
  // for differential testing
  val intrNO = Output(UInt(XLEN.W))
  val imemMMU = Flipped(new MMUIO)
  val dmemMMU = Flipped(new MMUIO)
  val wenFix = Output(Bool())
}

class CSR(implicit val p: NutCoreConfig) extends NutCoreModule with HasCSRConst{
  val io = IO(new CSRIO)

  val (valid, src1, src2, func) = (io.in.valid, io.in.bits.src1, io.in.bits.src2, io.in.bits.func)
  def access(valid: Bool, src1: UInt, src2: UInt, func: UInt): UInt = {
    this.valid := valid
    this.src1 := src1
    this.src2 := src2
    this.func := func
    io.out.bits
  }

  // CSR define

  class Priv extends Bundle {
    val m = Output(Bool())
    val h = Output(Bool())
    val s = Output(Bool())
    val u = Output(Bool())
  }

  val csrNotImplemented = RegInit(UInt(XLEN.W), 0.U)
   
  class MstatusStruct extends Bundle {
    val sd = Output(UInt(1.W))

    val pad1 = if (XLEN == 64) Output(UInt(27.W)) else null
    val sxl  = if (XLEN == 64) Output(UInt(2.W))  else null
    val uxl  = if (XLEN == 64) Output(UInt(2.W))  else null
    val pad0 = if (XLEN == 64) Output(UInt(9.W))  else Output(UInt(8.W))

    val tsr = Output(UInt(1.W))
    val tw = Output(UInt(1.W))
    val tvm = Output(UInt(1.W))
    val mxr = Output(UInt(1.W))
    val sum = Output(UInt(1.W))
    val mprv = Output(UInt(1.W))
    val xs = Output(UInt(2.W))
    val fs = Output(UInt(2.W))
    val mpp = Output(UInt(2.W))
    val hpp = Output(UInt(2.W))
    val spp = Output(UInt(1.W))
    val pie = new Priv  // FIXME: UBE -> hpie ???
    val ie = new Priv
  }

  class SatpStruct extends Bundle {
    val mode = UInt(4.W)
    val asid = UInt(16.W)
    val ppn  = UInt(44.W)
  }

  class Interrupt extends Bundle {
    val e = new Priv
    val t = new Priv
    val s = new Priv
  }

  // Machine-Level CSRs
  
  val mtvec = RegInit(UInt(XLEN.W), 0.U)
  val mcounteren = RegInit(UInt(XLEN.W), 0.U)
  val mcause = RegInit(UInt(XLEN.W), 0.U)
  val mtval = RegInit(UInt(XLEN.W), 0.U)
  val mepc = Reg(UInt(XLEN.W))

  val mie = RegInit(0.U(XLEN.W))
  val mipWire = WireInit(0.U.asTypeOf(new Interrupt))
  val mipReg  = RegInit(0.U.asTypeOf(new Interrupt).asUInt)
  val mipFixMask = "h77f".U
  val mip = (mipWire.asUInt | mipReg).asTypeOf(new Interrupt)

  def getMisaMxl(mxl: Int): UInt = {mxl.U << (XLEN-2)}
  def getMisaExt(ext: Char): UInt = {1.U << (ext.toInt - 'a'.toInt)} 
  var extList = List('a', 's', 'i', 'u')
  if(HasMExtension){ extList = extList :+ 'm'}
  if(HasCExtension){ extList = extList :+ 'c'}
  if(HasNExtension){ extList = extList :+ 'n'}
  val misaInitVal = getMisaMxl(2) | extList.foldLeft(0.U)((sum, i) => sum | getMisaExt(i)) //"h8000000000141105".U 
  val misa = RegInit(UInt(XLEN.W), misaInitVal) 
  // MXL = 2          | 0 | EXT = b 00 0000 0100 0001 0001 0000 0101
  // (XLEN-1, XLEN-2) |   |(25, 0)  ZY XWVU TSRQ PONM LKJI HGFE DCBA

  val mvendorid = RegInit(UInt(XLEN.W), 0.U) // this is a non-commercial implementation
  val marchid = RegInit(UInt(XLEN.W), 0.U) // return 0 to indicate the field is not implemented
  val mimpid = RegInit(UInt(XLEN.W), ImpID.U) // provides a unique encoding of the version of the processor implementation
  val mhartid = RegInit(UInt(XLEN.W), 0.U) // the hardware thread running the code
  val mstatus = RegInit(UInt(XLEN.W), "h00001800".U)  // FIXME: sxl and uxl is not set ???
  // val mstatus = RegInit(UInt(XLEN.W), "h8000c0100".U)
  // mstatus Value Table
  // | sd   |
  // | pad1 |
  // | sxl  | hardlinked to 10, use 00 to pass xv6 test  
  // | uxl  | hardlinked to 00
  // | pad0 |
  // | tsr  |
  // | tw   |
  // | tvm  |
  // | mxr  |
  // | sum  |
  // | mprv |
  // | xs   | 00 |
  // | fs   | 00 |
  // | mpp  | 00 |
  // | hpp  | 00 |
  // | spp  | 0 |
  // | pie  | 0000 |
  // | ie   | 0000 | uie hardlinked to 0, as N ext is not implemented
  val mstatusStruct = mstatus.asTypeOf(new MstatusStruct)
  def mstatusUpdateSideEffect(mstatus: UInt): UInt = {
    val mstatusOld = WireInit(mstatus.asTypeOf(new MstatusStruct))
    val mstatusNew = Cat(mstatusOld.fs === "b11".U, mstatus(XLEN-2, 0))
    mstatusNew
  }

  val medeleg = RegInit(UInt(XLEN.W), 0.U)
  val mideleg = RegInit(UInt(XLEN.W), 0.U)
  val mscratch = RegInit(UInt(XLEN.W), 0.U)

  val pmpcfg0 = RegInit(UInt(XLEN.W), 0.U)
  val pmpcfg1 = RegInit(UInt(XLEN.W), 0.U)
  val pmpcfg2 = RegInit(UInt(XLEN.W), 0.U)
  val pmpcfg3 = RegInit(UInt(XLEN.W), 0.U)
  val pmpaddr0 = RegInit(UInt(XLEN.W), 0.U) 
  val pmpaddr1 = RegInit(UInt(XLEN.W), 0.U) 
  val pmpaddr2 = RegInit(UInt(XLEN.W), 0.U) 
  val pmpaddr3 = RegInit(UInt(XLEN.W), 0.U) 

  // Machine-Level DASICS CSRs
  val dasicsMainCfg      = RegInit(UInt(XLEN.W), 0.U)
  val dasicsSMainBoundHi = RegInit(UInt(XLEN.W), 0.U)
  val dasicsSMainBoundLo = RegInit(UInt(XLEN.W), 0.U)

  val dasicsMachineMapping = Map(
    MaskedRegMap(DasicsSMainCfg, dasicsMainCfg, "hf".U(XLEN.W), MaskedRegMap.NoSideEffect, "hf".U(XLEN.W)),
    MaskedRegMap(DasicsSMainBoundHi, dasicsSMainBoundHi),
    MaskedRegMap(DasicsSMainBoundLo, dasicsSMainBoundLo)
  )

  // Superviser-Level CSRs

  // val sstatus = RegInit(UInt(XLEN.W), "h00000000".U)
  val sstatusWmask = "hc6122".U
  // Sstatus Write Mask
  // -------------------------------------------------------
  //    19           9   5     2
  // 0  1100 0000 0001 0010 0010
  // 0  c    0    1    2    2
  // -------------------------------------------------------
  val sstatusRmask = sstatusWmask | "h8000000300018000".U
  // Sstatus Read Mask = (SSTATUS_WMASK | (0xf << 13) | (1ull << 63) | (3ull << 32))
  val stvec = RegInit(UInt(XLEN.W), 0.U)
  // val sie = RegInit(0.U(XLEN.W))
  val sieMask = "h222".U & mideleg
  val sipMask  = "h222".U & mideleg
  // val satp = RegInit(UInt(XLEN.W), "h8000000000087fbe".U)
  val satp = RegInit(UInt(XLEN.W), 0.U)
  val sepc = RegInit(UInt(XLEN.W), 0.U)
  val scause = RegInit(UInt(XLEN.W), 0.U)
  val stval = Reg(UInt(XLEN.W))
  val sscratch = RegInit(UInt(XLEN.W), 0.U)
  val scounteren = RegInit(UInt(XLEN.W), 0.U)

  val sedeleg = RegInit(UInt(XLEN.W), 0.U)
  val sideleg = RegInit(UInt(XLEN.W), 0.U)

  // Supervisor-Level DASICS CSRs
  val dasicsUMainBoundHi = RegInit(UInt(XLEN.W), 0.U)
  val dasicsUMainBoundLo = RegInit(UInt(XLEN.W), 0.U)

  val dasicsSupervisorMapping = Map(
    MaskedRegMap(DasicsUMainCfg, dasicsMainCfg, "ha".U(XLEN.W), MaskedRegMap.NoSideEffect, "ha".U(XLEN.W)),
    MaskedRegMap(DasicsUMainBoundHi, dasicsUMainBoundHi),
    MaskedRegMap(DasicsUMainBoundLo, dasicsUMainBoundLo)
  )

  if (Settings.get("HasDTLB")) {
    BoringUtils.addSource(satp, "CSRSATP")
  }

  // User-Level CSRs
  val ustatusWmask = "h11".U(XLEN.W)  // UPIE and UIE
  val ustatusRmask = ustatusWmask
  val uieMask = "h111".U(XLEN.W) & sideleg
  val uipMask = "h111".U(XLEN.W) & sideleg
  val uepc = Reg(UInt(XLEN.W))
  val ucause = RegInit(UInt(XLEN.W), 0.U)
  val uscratch = RegInit(UInt(XLEN.W), 0.U)
  val utvec = RegInit(UInt(XLEN.W), 0.U)
  val utval = RegInit(UInt(XLEN.W), 0.U)

  // User-Level DASICS CSRs
  val dasicsLibBoundHiList = List.fill(dasicsLibGroups)(RegInit(UInt(XLEN.W), 0.U))
  val dasicsLibBoundLoList = List.fill(dasicsLibGroups)(RegInit(UInt(XLEN.W), 0.U))
  val dasicsLibBoundHiMapping = (0 until dasicsLibGroups).map { case i => MaskedRegMap(DasicsLibBoundBase + i * 2      , dasicsLibBoundHiList(i)) }
  val dasicsLibBoundLoMapping = (0 until dasicsLibGroups).map { case i => MaskedRegMap(DasicsLibBoundBase + i * 2 + 0x1, dasicsLibBoundLoList(i)) }

  val dasicsLibCfg0  = RegInit(UInt(XLEN.W), 0.U)
  val dasicsLibCfg1  = RegInit(UInt(XLEN.W), 0.U)
  val dasicsReturnPC = RegInit(UInt(XLEN.W), 0.U)
  val dasicsFreeZoneReturnPC = RegInit(UInt(XLEN.W), 0.U)
  val dasicsMaincallEntry    = RegInit(UInt(XLEN.W), 0.U)

  val dasicsUserMapping = Map(
    MaskedRegMap(DasicsLibCfg0, dasicsLibCfg0),
    MaskedRegMap(DasicsLibCfg1, dasicsLibCfg1),
    MaskedRegMap(DasicsMaincallEntry, dasicsMaincallEntry),
    MaskedRegMap(DasicsReturnPC, dasicsReturnPC),
    MaskedRegMap(DasicsFreeZoneReturnPC, dasicsFreeZoneReturnPC)
  ) ++ dasicsLibBoundHiMapping ++ dasicsLibBoundLoMapping

  val dasicsMapping = dasicsMachineMapping ++ dasicsSupervisorMapping ++ dasicsUserMapping

  // Atom LR/SC Control Bits
  val setLr = WireInit(Bool(), false.B)
  val setLrVal = WireInit(Bool(), false.B)
  val setLrAddr = WireInit(UInt(AddrBits.W), DontCare) //TODO : need check
  val lr = RegInit(Bool(), false.B)
  val lrAddr = RegInit(UInt(AddrBits.W), 0.U)
  BoringUtils.addSink(setLr, "set_lr")
  BoringUtils.addSink(setLrVal, "set_lr_val")
  BoringUtils.addSink(setLrAddr, "set_lr_addr")
  BoringUtils.addSource(lr, "lr")
  BoringUtils.addSource(lrAddr, "lr_addr")

  when(setLr){
    lr := setLrVal
    lrAddr := setLrAddr
  }

  // Hart Priviledge Mode
  val priviledgeMode = RegInit(UInt(2.W), ModeM)

  // perfcnt
  val hasPerfCnt = EnablePerfCnt && !p.FPGAPlatform
  val nrPerfCnts = if (hasPerfCnt) 0x80 else 0x3
  val perfCnts = List.fill(nrPerfCnts)(RegInit(0.U(64.W)))
  val perfCntsLoMapping = (0 until nrPerfCnts).map { case i => MaskedRegMap(0xb00 + i, perfCnts(i)) }
  val perfCntsHiMapping = (0 until nrPerfCnts).map { case i => MaskedRegMap(0xb80 + i, perfCnts(i)(63, 32)) }

  // CSR reg map
  val userMapping = if (!HasNExtension) Nil else Map(
    // User Trap Setup
    MaskedRegMap(Ustatus, mstatus, ustatusWmask, MaskedRegMap.NoSideEffect, ustatusRmask),
    MaskedRegMap(Uie, mie, uieMask, MaskedRegMap.Unwritable, uieMask),
    MaskedRegMap(Utvec, utvec),

    // User Trap Handling
    MaskedRegMap(Uscratch, uscratch),
    MaskedRegMap(Uepc, uepc),
    MaskedRegMap(Ucause, ucause),
    MaskedRegMap(Utval, utval),
    MaskedRegMap(Uip, mip.asUInt, uipMask, MaskedRegMap.Unwritable, uipMask)

    // User Floating-Point CSRs (not implemented)
    // MaskedRegMap(Fflags, fflags),
    // MaskedRegMap(Frm, frm),
    // MaskedRegMap(Fcsr, fcsr),

    // User Counter/Timers (not implemented)
    // MaskedRegMap(Cycle, cycle),
    // MaskedRegMap(Time, time),
    // MaskedRegMap(Instret, instret)
  )

  val mapping = Map(
    // Supervisor Trap Setup
    MaskedRegMap(Sstatus, mstatus, sstatusWmask, mstatusUpdateSideEffect, sstatusRmask),

    MaskedRegMap(Sedeleg, sedeleg),
    MaskedRegMap(Sideleg, sideleg),
    MaskedRegMap(Sie, mie, sieMask, MaskedRegMap.NoSideEffect, sieMask),
    MaskedRegMap(Stvec, stvec),
    MaskedRegMap(Scounteren, scounteren),

    // Supervisor Trap Handling
    MaskedRegMap(Sscratch, sscratch),
    MaskedRegMap(Sepc, sepc),
    MaskedRegMap(Scause, scause),
    MaskedRegMap(Stval, stval),
    MaskedRegMap(Sip, mip.asUInt, sipMask, MaskedRegMap.Unwritable, sipMask),

    // Supervisor Protection and Translation
    MaskedRegMap(Satp, satp),

    // Machine Information Registers 
    MaskedRegMap(Mvendorid, mvendorid, 0.U, MaskedRegMap.Unwritable), 
    MaskedRegMap(Marchid, marchid, 0.U, MaskedRegMap.Unwritable), 
    MaskedRegMap(Mimpid, mimpid, 0.U, MaskedRegMap.Unwritable), 
    MaskedRegMap(Mhartid, mhartid, 0.U, MaskedRegMap.Unwritable), 

    // Machine Trap Setup
    // MaskedRegMap(Mstatus, mstatus, "hffffffffffffffee".U, (x=>{printf("mstatus write: %x time: %d\n", x, GTimer()); x})),
    MaskedRegMap(Mstatus, mstatus, "hffffffffffffffff".U, mstatusUpdateSideEffect),
    MaskedRegMap(Misa, misa), // now MXL, EXT is not changeable
    MaskedRegMap(Medeleg, medeleg),
    MaskedRegMap(Mideleg, mideleg, "h222".U),
    MaskedRegMap(Mie, mie),
    MaskedRegMap(Mtvec, mtvec),
    MaskedRegMap(Mcounteren, mcounteren), 

    // Machine Trap Handling
    MaskedRegMap(Mscratch, mscratch),
    MaskedRegMap(Mepc, mepc),
    MaskedRegMap(Mcause, mcause),
    MaskedRegMap(Mtval, mtval),
    MaskedRegMap(Mip, mip.asUInt, 0.U, MaskedRegMap.Unwritable),

    // Machine Memory Protection
    MaskedRegMap(Pmpcfg0, pmpcfg0),
    MaskedRegMap(Pmpcfg1, pmpcfg1),
    MaskedRegMap(Pmpcfg2, pmpcfg2),
    MaskedRegMap(Pmpcfg3, pmpcfg3),
    MaskedRegMap(PmpaddrBase + 0, pmpaddr0),
    MaskedRegMap(PmpaddrBase + 1, pmpaddr1),
    MaskedRegMap(PmpaddrBase + 2, pmpaddr2),
    MaskedRegMap(PmpaddrBase + 3, pmpaddr3)

  ) ++ userMapping ++ perfCntsLoMapping ++ dasicsMapping //++ (if (XLEN == 32) perfCntsHiMapping else Nil)

  val addr = src2(11, 0)
  val rdata = Wire(UInt(XLEN.W))
  val csri = ZeroExt(io.cfIn.instr(19,15), XLEN) //unsigned imm for csri. [TODO]
  val wdata = LookupTree(func, List(
    CSROpType.wrt  -> src1,
    CSROpType.set  -> (rdata | src1),
    CSROpType.clr  -> (rdata & ~src1),
    CSROpType.wrti -> csri,//TODO: csri --> src2
    CSROpType.seti -> (rdata | csri),
    CSROpType.clri -> (rdata & ~csri)
  ))

  // SATP wen check
  val satpLegalMode = (wdata.asTypeOf(new SatpStruct).mode === 0.U) || (wdata.asTypeOf(new SatpStruct).mode === 8.U)

  // DASICS Main/Lib wen check
  // Note: when DASICS is enabled, lib functions cannot access CSRs
  def detectInZone(addr: UInt, hi: UInt, lo: UInt, en: Bool) : Bool = en && addr >= lo && addr <= hi
  val isSMainEnable = detectInZone(dasicsSMainBoundLo, dasicsSMainBoundHi, 0.U(XLEN.W), dasicsMainCfg(MCFG_SENA))
  val isUMainEnable = detectInZone(dasicsUMainBoundLo, dasicsUMainBoundHi, 0.U(XLEN.W), dasicsMainCfg(MCFG_UENA))

  // DASICS Load/Store checks are performed in ISU stage for timing considerations
  // Note: Privilege level cannot be known until EXU stage; however, we can consider DASICS CSRs as static
  val isuPC = WireInit(0.U(VAddrBits.W))
  BoringUtils.addSink(isuPC, name = "isu_pc")
  val isuInSTrustedZone = detectInZone(isuPC, dasicsSMainBoundHi, dasicsSMainBoundLo, isSMainEnable) || !isSMainEnable
  val isuInUTrustedZone = detectInZone(isuPC, dasicsUMainBoundHi, dasicsUMainBoundLo, isUMainEnable) || !isUMainEnable
  BoringUtils.addSource(isuInSTrustedZone, name = "isu_in_s_trusted_zone")
  BoringUtils.addSource(isuInUTrustedZone, name = "isu_in_u_trusted_zone")

  def detectInTrustedZone(addr: UInt) : Bool = {
    val inSMainZone = detectInZone(addr, dasicsSMainBoundHi, dasicsSMainBoundLo, priviledgeMode === ModeS && isSMainEnable)
    val inUMainZone = detectInZone(addr, dasicsUMainBoundHi, dasicsUMainBoundLo, priviledgeMode === ModeU && isUMainEnable)

    val inSTrustedZone = inSMainZone || priviledgeMode === ModeS && !isSMainEnable
    val inUTrustedZone = inUMainZone || priviledgeMode === ModeU && !isUMainEnable

    priviledgeMode > ModeS || inSTrustedZone || inUTrustedZone
  }
  val inTrustedZone = detectInTrustedZone(io.cfIn.pc)

  // General CSR wen check
  val wen = (valid && func =/= CSROpType.jmp) && (addr =/= Satp.U || satpLegalMode) && !io.isBackendException
  val isIllegalMode = priviledgeMode < addr(9, 8) || !inTrustedZone
  val justRead = (func === CSROpType.set || func === CSROpType.seti) && src1 === 0.U  // csrrs and csrrsi are exceptions when their src1 is zero
  val isIllegalWrite = wen && (addr(11, 10) === "b11".U) && !justRead  // Write a read-only CSR register
  val isIllegalAccess = isIllegalMode || isIllegalWrite

  MaskedRegMap.generate(mapping, addr, rdata, wen && !isIllegalAccess, wdata)
  val isIllegalAddr = MaskedRegMap.isIllegalAddr(mapping, addr)
  val resetSatp = addr === Satp.U && wen // write to satp will cause the pipeline be flushed
  io.out.bits := rdata

  // Fix Mip/Sip write
  val fixMapping = Map(
    MaskedRegMap(Mip, mipReg.asUInt, mipFixMask),
    MaskedRegMap(Sip, mipReg.asUInt, sipMask, MaskedRegMap.NoSideEffect, sipMask)
  )
  val rdataFix = Wire(UInt(XLEN.W))
  val wdataFix = LookupTree(func, List(
    CSROpType.wrt  -> src1,
    CSROpType.set  -> (rdataFix | src1),
    CSROpType.clr  -> (rdataFix & ~src1),
    CSROpType.wrti -> csri,//TODO: csri --> src2
    CSROpType.seti -> (rdataFix | csri),
    CSROpType.clri -> (rdataFix & ~csri)
  ))
  MaskedRegMap.generate(fixMapping, addr, rdataFix, wen && !isIllegalAccess, wdataFix)

  // DASICS -- Act when the S-CLS or U-CLS bit of dasicsMainCfg is set
  when (dasicsMainCfg(MCFG_SCLS) || dasicsMainCfg(MCFG_UCLS))  // Reset all dasics lib registers
  {
    when (dasicsMainCfg(MCFG_SCLS))
    {
      dasicsUMainBoundHi := 0.U
      dasicsUMainBoundLo := 0.U
    }

    dasicsLibBoundHiList.foreach(reg => reg := 0.U)
    dasicsLibBoundLoList.foreach(reg => reg := 0.U)
    dasicsLibCfg0 := 0.U
    dasicsLibCfg1 := 0.U

    dasicsMainCfg := Mux(dasicsMainCfg(MCFG_SCLS), Cat(0.U((XLEN-1).W), dasicsMainCfg(MCFG_SENA)),
                                                   Cat(0.U((XLEN-MCFG_UCLS).W), dasicsMainCfg(MCFG_SCLS, MCFG_SENA)))
  }

  // DASICS -- Check LSU DASICS exception
  val lsuIsValid = WireInit(false.B)
  val lsuAddr = WireInit(0.U(XLEN.W))  // Memory address where the inst at LSU will access in idle-state
  val isuAddr = WireInit(0.U(XLEN.W))   // Perform some of the calculations in ISU stage

  BoringUtils.addSink(lsuIsValid, "lsu_is_valid")
  BoringUtils.addSink(lsuAddr, "lsu_addr")
  BoringUtils.addSink(isuAddr, name = "isu_addr")

  val dasicsLibSeq = if (Settings.get("IsRV32"))
                          ((for (i <- 0 until 32 if i % 4 == 0)
                            yield (dasicsLibCfg0(i + 3, i), dasicsLibBoundHiList(i >> 2), dasicsLibBoundLoList(i >> 2))) ++
                           (for (i <- 0 until 32 if i % 4 == 0)
                            yield (dasicsLibCfg1(i + 3, i), dasicsLibBoundHiList((i >> 2) + 8), dasicsLibBoundLoList((i >> 2) + 8))))
                     else ((for (i <- 0 until 64 if i % 8 == 0)
                            yield (dasicsLibCfg0(i + 3, i), dasicsLibBoundHiList(i >> 3), dasicsLibBoundLoList(i >> 3))) ++
                           (for (i <- 0 until 64 if i % 8 == 0)
                            yield (dasicsLibCfg1(i + 3, i), dasicsLibBoundHiList((i >> 3) + 8), dasicsLibBoundLoList((i >> 3) + 8))))

  val isuPermitLibLoad  = dasicsLibSeq.map(cfg => detectInZone(isuAddr, cfg._2, cfg._3, cfg._1(LIBCFG_V) && cfg._1(LIBCFG_R))).foldRight(false.B)(_ || _)  // If there exists one pair, that's ok
  val isuPermitLibStore = dasicsLibSeq.map(cfg => detectInZone(isuAddr, cfg._2, cfg._3, cfg._1(LIBCFG_V) && cfg._1(LIBCFG_W))).foldRight(false.B)(_ || _)
  BoringUtils.addSource(isuPermitLibLoad, name = "isu_perm_lib_ld")
  BoringUtils.addSource(isuPermitLibStore, name = "isu_perm_lib_st")

  val (lsuIsLoad, lsuInSTrustedZone, lsuInUTrustedZone, lsuPermitLibLoad, lsuPermitLibStore) =
    (io.lsuIsLoad, io.lsuInSTrustedZone, io.lsuInUTrustedZone, io.lsuPermitLibLoad, io.lsuPermitLibStore)

  // Seperate access denying and exception raising
  val lsuSLibLoadDeny : Bool =  lsuIsLoad && priviledgeMode === ModeS && !lsuInSTrustedZone && !lsuPermitLibLoad
  val lsuULibLoadDeny : Bool =  lsuIsLoad && priviledgeMode === ModeU && !lsuInUTrustedZone && !lsuPermitLibLoad
  val lsuSLibStoreDeny: Bool = !lsuIsLoad && priviledgeMode === ModeS && !lsuInSTrustedZone && !lsuPermitLibStore
  val lsuULibStoreDeny: Bool = !lsuIsLoad && priviledgeMode === ModeU && !lsuInUTrustedZone && !lsuPermitLibStore
  val lsuDeny: Bool = lsuSLibLoadDeny || lsuULibLoadDeny || lsuSLibStoreDeny || lsuULibStoreDeny
  BoringUtils.addSource(lsuDeny, name = "cannot_access_memory")

  val lsuSLibLoadFault  = lsuIsValid && lsuSLibLoadDeny
  val lsuULibLoadFault  = lsuIsValid && lsuULibLoadDeny
  val lsuSLibStoreFault = lsuIsValid && lsuSLibStoreDeny
  val lsuULibStoreFault = lsuIsValid && lsuULibStoreDeny

//   when (io.cfIn.pc === 0x80202f30L.U)
//   {
//     printf("[DEBUG] lsuIsValid = %b, lsuIsLoad = %b, lsuAddr = 0x%x, lsuSLibStoreFault = %b\n", lsuIsValid, lsuIsLoad, lsuAddr, lsuSLibStoreFault)
//     printf("[DEBUG] libSeq0: (%x, 0x%x, 0x%x), libSeq1: (%x, 0x%x, 0x%x), libSeq2: (%x, 0x%x, 0x%x), libSeq3: (%x, 0x%x, 0x%x)\n",
//           dasicsLibSeq(0)._1, dasicsLibSeq(0)._2, dasicsLibSeq(0)._3,
//           dasicsLibSeq(1)._1, dasicsLibSeq(1)._2, dasicsLibSeq(1)._3,
//           dasicsLibSeq(2)._1, dasicsLibSeq(2)._2, dasicsLibSeq(2)._3,
//           dasicsLibSeq(3)._1, dasicsLibSeq(3)._2, dasicsLibSeq(3)._3)
//     printf("[DEBUG] libSeq4: (%x, 0x%x, 0x%x), libSeq5: (%x, 0x%x, 0x%x), libSeq6: (%x, 0x%x, 0x%x), libSeq7: (%x, 0x%x, 0x%x)\n",
//           dasicsLibSeq(4)._1, dasicsLibSeq(4)._2, dasicsLibSeq(4)._3,
//           dasicsLibSeq(5)._1, dasicsLibSeq(5)._2, dasicsLibSeq(5)._3,
//           dasicsLibSeq(6)._1, dasicsLibSeq(6)._2, dasicsLibSeq(6)._3,
//           dasicsLibSeq(7)._1, dasicsLibSeq(7)._2, dasicsLibSeq(7)._3)
//     printf("[DEBUG] libSeq8: (%x, 0x%x, 0x%x), libSeq9: (%x, 0x%x, 0x%x), libSeq10: (%x, 0x%x, 0x%x), libSeq11: (%x, 0x%x, 0x%x)\n",
//           dasicsLibSeq(8)._1, dasicsLibSeq(8)._2, dasicsLibSeq(8)._3,
//           dasicsLibSeq(9)._1, dasicsLibSeq(9)._2, dasicsLibSeq(9)._3,
//           dasicsLibSeq(10)._1, dasicsLibSeq(10)._2, dasicsLibSeq(10)._3,
//           dasicsLibSeq(11)._1, dasicsLibSeq(11)._2, dasicsLibSeq(11)._3)
//     printf("[DEBUG] libSeq12: (%x, 0x%x, 0x%x), libSeq13: (%x, 0x%x, 0x%x), libSeq14: (%x, 0x%x, 0x%x), libSeq15: (%x, 0x%x, 0x%x)\n",
//           dasicsLibSeq(12)._1, dasicsLibSeq(12)._2, dasicsLibSeq(12)._3,
//           dasicsLibSeq(13)._1, dasicsLibSeq(13)._2, dasicsLibSeq(13)._3,
//           dasicsLibSeq(14)._1, dasicsLibSeq(14)._2, dasicsLibSeq(14)._3,
//           dasicsLibSeq(15)._1, dasicsLibSeq(15)._2, dasicsLibSeq(15)._3)
//   }

  // DASICS -- Check ALU DASICS exception
  val aluRedirectValid = WireInit(false.B)
  val aluRedirectTarget = WireInit(0.U(XLEN.W))
  val aluIsPulpret = WireInit(false.B)

  BoringUtils.addSink(aluRedirectValid, "redirect_valid")
  BoringUtils.addSink(aluRedirectTarget, "redirect_target")
  BoringUtils.addSink(aluIsPulpret, "is_pulpret")

  def detectInLibFreeZone(addr: UInt, trustedZone: Bool) : Bool = !trustedZone && dasicsLibSeq.map(cfg => detectInZone(addr, cfg._2, cfg._3, cfg._1(LIBCFG_V) && cfg._1(LIBCFG_X))).foldRight(false.B)(_ || _)
  val inLibFreeZone = detectInLibFreeZone(io.cfIn.pc, inTrustedZone)
  val targetInTrustedZone = detectInTrustedZone(aluRedirectTarget)
  val targetInLibFreeZone = detectInLibFreeZone(aluRedirectTarget, targetInTrustedZone)

  when (aluRedirectValid && inTrustedZone && !targetInTrustedZone && !aluIsPulpret)  // Jump/branch from trusted to untrusted
  {
    dasicsReturnPC := io.cfIn.pc + 4.U
  }

  /* This register should be set by software
  when (aluRedirectValid && !inTrustedZone && !inLibFreeZone && targetInLibFreeZone)  // Jump/branch from untrusted-nonfree to untrusted-free
  {
    dasicsFreeZoneReturnPC := io.cfIn.pc + 4.U
  }
   */

  val aluPermitRedirect = inTrustedZone       || (!inTrustedZone &&  targetInTrustedZone && (aluRedirectTarget === dasicsReturnPC || aluRedirectTarget === dasicsMaincallEntry)) ||
                          targetInLibFreeZone || ( inLibFreeZone && !targetInTrustedZone && !targetInLibFreeZone && aluRedirectTarget === dasicsFreeZoneReturnPC)

  val aluSLibInstrFault = isSMainEnable && priviledgeMode === ModeS && aluRedirectValid && !aluPermitRedirect
  val aluULibInstrFault = isUMainEnable && priviledgeMode === ModeU && aluRedirectValid && !aluPermitRedirect

//  when (aluSLibInstrFault || aluULibInstrFault)
//  {
//    printf("[DEBUG] aluULibInstrFault = %b, io.cfIn.pc = 0x%x, uepc = 0x%x, sepc = 0x%x\n", aluULibInstrFault, io.cfIn.pc, uepc, sepc)
//    printf("[DEBUG] inTrustedZone = %b, targetInTrustedZone = %b, inLibFreeZone = %b, targetInLibFreeZone = %b\n",
//      inTrustedZone, targetInTrustedZone, inLibFreeZone, targetInLibFreeZone)
//    printf("[DEBUG] aluRedirectTarget = 0x%x, dasicsReturnPC = 0x%x, dasicsFreeZoneReturnPC = 0x%x\n",
//      aluRedirectTarget, dasicsReturnPC, dasicsFreeZoneReturnPC)
//  }

  // CSR inst decode
  val ret = Wire(Bool())
  val isEbreak = addr === privEbreak && func === CSROpType.jmp && !io.isBackendException
  val isEcall = addr === privEcall && func === CSROpType.jmp && !io.isBackendException
  val isMret = addr === privMret   && func === CSROpType.jmp && !io.isBackendException
  val isSret = addr === privSret   && func === CSROpType.jmp && !io.isBackendException
  val isUret = addr === privUret   && func === CSROpType.jmp && !io.isBackendException

  Debug(wen, "csr write: pc %x addr %x rdata %x wdata %x func %x\n", io.cfIn.pc, addr, rdata, wdata, func)
  Debug(wen, "[MST] time %d pc %x mstatus %x mideleg %x medeleg %x mode %x\n", GTimer(), io.cfIn.pc, mstatus, mideleg , medeleg, priviledgeMode)

  // MMU Permission Check

  // def MMUPermissionCheck(ptev: Bool, pteu: Bool): Bool = ptev && !(priviledgeMode === ModeU && !pteu) && !(priviledgeMode === ModeS && pteu && mstatusStruct.sum.asBool)
  // def MMUPermissionCheckLoad(ptev: Bool, pteu: Bool): Bool = ptev && !(priviledgeMode === ModeU && !pteu) && !(priviledgeMode === ModeS && pteu && mstatusStruct.sum.asBool) && (pter || (mstatusStruct.mxr && ptex))
  // imem
  // val imemPtev = true.B
  // val imemPteu = true.B
  // val imemPtex = true.B
  // val imemReq = true.B
  // val imemPermissionCheckPassed = MMUPermissionCheck(imemPtev, imemPteu)
  // val hasInstrPageFault = imemReq && !(imemPermissionCheckPassed && imemPtex) 
  // assert(!hasInstrPageFault)

  // dmem
  // val dmemPtev = true.B
  // val dmemPteu = true.B
  // val dmemReq = true.B
  // val dmemPermissionCheckPassed = MMUPermissionCheck(dmemPtev, dmemPteu)
  // val dmemIsStore = true.B

  // val hasLoadPageFault  = dmemReq && !dmemIsStore && !(dmemPermissionCheckPassed) 
  // val hasStorePageFault = dmemReq &&  dmemIsStore && !(dmemPermissionCheckPassed) 
  // assert(!hasLoadPageFault)
  // assert(!hasStorePageFault)

  //TODO: Havn't test if io.dmemMMU.priviledgeMode is correct yet
  io.imemMMU.priviledgeMode := priviledgeMode
  io.dmemMMU.priviledgeMode := Mux(mstatusStruct.mprv.asBool, mstatusStruct.mpp, priviledgeMode)
  io.imemMMU.status_sum := mstatusStruct.sum.asBool
  io.dmemMMU.status_sum := mstatusStruct.sum.asBool
  io.imemMMU.status_mxr := DontCare
  io.dmemMMU.status_mxr := mstatusStruct.mxr.asBool

  val hasInstrPageFault = Wire(Bool())
  val hasLoadPageFault = Wire(Bool())
  val hasStorePageFault = Wire(Bool())
  val hasStoreAddrMisaligned = Wire(Bool())
  val hasLoadAddrMisaligned = Wire(Bool())

  val dmemPagefaultAddr = Wire(UInt(VAddrBits.W))
  val dmemAddrMisalignedAddr = Wire(UInt(VAddrBits.W))
  val lsuExecAddr = WireInit(0.U(64.W))
  BoringUtils.addSink(lsuExecAddr, "LSUEXECADDR")
  if(EnableOutOfOrderExec){
    hasInstrPageFault      := valid && io.cfIn.exceptionVec(instrPageFault)
    hasLoadPageFault       := valid && io.cfIn.exceptionVec(loadPageFault)
    hasStorePageFault      := valid && io.cfIn.exceptionVec(storePageFault)
    hasStoreAddrMisaligned := valid && io.cfIn.exceptionVec(storeAddrMisaligned)
    hasLoadAddrMisaligned  := valid && io.cfIn.exceptionVec(loadAddrMisaligned)
    dmemPagefaultAddr := src1 // LSU -> wbresult -> prf -> beUop.data.src1
    dmemAddrMisalignedAddr := src1
  }else{
    hasInstrPageFault := io.cfIn.exceptionVec(instrPageFault) && valid
    hasLoadPageFault := io.dmemMMU.loadPF
    hasStorePageFault := io.dmemMMU.storePF
    hasStoreAddrMisaligned := io.cfIn.exceptionVec(storeAddrMisaligned)
    hasLoadAddrMisaligned := io.cfIn.exceptionVec(loadAddrMisaligned)
    dmemPagefaultAddr := io.dmemMMU.addr
    dmemAddrMisalignedAddr := lsuExecAddr
  }

  when(aluSLibInstrFault || lsuSLibLoadFault || lsuSLibStoreFault)  // Lower priority
  {
    mtval := Mux(aluSLibInstrFault, SignExt(aluRedirectTarget, XLEN), SignExt(lsuAddr, XLEN))
  }

  when(hasInstrPageFault || hasLoadPageFault || hasStorePageFault){
    val tval = Mux(hasInstrPageFault, Mux(io.cfIn.crossPageIPFFix, SignExt((io.cfIn.pc + 2.U)(VAddrBits-1,0), XLEN), SignExt(io.cfIn.pc(VAddrBits-1,0), XLEN)), SignExt(dmemPagefaultAddr, XLEN))
    when(priviledgeMode === ModeM){
      mtval := tval
    }.otherwise{
      stval := tval
    }
    Debug("[PF] %d: ipf %b tval %x := addr %x pc %x priviledgeMode %x\n", GTimer(), hasInstrPageFault, tval, SignExt(dmemPagefaultAddr, XLEN), io.cfIn.pc, priviledgeMode)
  }

  when(hasLoadAddrMisaligned || hasStoreAddrMisaligned)
  {
    mtval := SignExt(dmemAddrMisalignedAddr, XLEN)
    Debug("[ML] %d: addr %x pc %x priviledgeMode %x\n", GTimer(), SignExt(dmemAddrMisalignedAddr, XLEN), io.cfIn.pc, priviledgeMode)
  }

  when(aluULibInstrFault || lsuULibLoadFault || lsuULibStoreFault)
  {
    utval := Mux(aluULibInstrFault, SignExt(aluRedirectTarget, XLEN), SignExt(lsuAddr, XLEN))
//    printf("[DEBUG] uepc = 0x%x, ucause = 0x%x, utval = 0x%x, returnPC = 0x%x, fzReturnPC = 0x%x\n", uepc, ucause, utval, dasicsReturnPC, dasicsFreeZoneReturnPC)
  }

  // Exception and Intr

  // interrupts

  val mtip = WireInit(false.B)
  val meip = WireInit(false.B)
  val msip = WireInit(false.B)
  BoringUtils.addSink(mtip, "mtip")
  BoringUtils.addSink(meip, "meip")
  BoringUtils.addSink(msip, "msip")
  mipWire.t.m := mtip
  mipWire.e.m := meip
  mipWire.s.m := msip
  
  // SEIP from PLIC is only used to raise interrupt,
  // but it is not stored in the CSR
  val seip = meip    // FIXME: PLIC should generate SEIP different from MEIP
  val mipRaiseIntr = WireInit(mip)
  mipRaiseIntr.e.s := mip.e.s | seip

  val ideleg =  (mideleg & mipRaiseIntr.asUInt)
  def priviledgedEnableDetect(x: Bool): Bool = Mux(x, ((priviledgeMode === ModeS) && mstatusStruct.ie.s) || (priviledgeMode < ModeS),
                                   ((priviledgeMode === ModeM) && mstatusStruct.ie.m) || (priviledgeMode < ModeM))

  val intrVecEnable = Wire(Vec(InterruptTypes, Bool()))
  intrVecEnable.zip(ideleg.asBools).map{case(x,y) => x := priviledgedEnableDetect(y)}
  val intrVec = mie(11,0) & mipRaiseIntr.asUInt & intrVecEnable.asUInt
  BoringUtils.addSource(intrVec, "intrVecIDU")
  // val intrNO = PriorityEncoder(intrVec)
  
  val intrNO = IntPriority.foldRight(0.U)((i: Int, sum: UInt) => Mux(io.cfIn.intrVec(i), i.U, sum))
  // val intrNO = PriorityEncoder(io.cfIn.intrVec)
  val raiseIntr = io.cfIn.intrVec.asUInt.orR

  // exceptions

  // TODO: merge iduExceptionVec, csrExceptionVec as raiseExceptionVec
  val csrExceptionVec = Wire(Vec(ExceptionTypes, Bool()))
  csrExceptionVec.map(_ := false.B)
  csrExceptionVec(breakPoint) := io.in.valid && isEbreak
  csrExceptionVec(ecallM) := priviledgeMode === ModeM && io.in.valid && isEcall
  csrExceptionVec(ecallS) := priviledgeMode === ModeS && io.in.valid && isEcall && inTrustedZone
  csrExceptionVec(ecallU) := priviledgeMode === ModeU && io.in.valid && isEcall && inTrustedZone
  csrExceptionVec(illegalInstr) := (isIllegalAddr || isIllegalAccess) && wen && !io.isBackendException // Trigger an illegal instr exception when unimplemented csr is being read/written or not having enough priviledge
  csrExceptionVec(loadPageFault) := hasLoadPageFault
  csrExceptionVec(storePageFault) := hasStorePageFault
  csrExceptionVec(dasicsUInstrAccessFault) := aluULibInstrFault
  csrExceptionVec(dasicsSInstrAccessFault) := aluSLibInstrFault
  csrExceptionVec(dasicsULoadAccessFault) := lsuULibLoadFault
  csrExceptionVec(dasicsSLoadAccessFault) := lsuSLibLoadFault
  csrExceptionVec(dasicsUStoreAccessFault) := lsuULibStoreFault
  csrExceptionVec(dasicsSStoreAccessFault) := lsuSLibStoreFault
  csrExceptionVec(dasicsUEcallFault) := priviledgeMode === ModeU && io.in.valid && isEcall && !inTrustedZone
  csrExceptionVec(dasicsSEcallFault) := priviledgeMode === ModeS && io.in.valid && isEcall && !inTrustedZone
  val iduExceptionVec = io.cfIn.exceptionVec
  val raiseExceptionVec = csrExceptionVec.asUInt() | iduExceptionVec.asUInt()
  val raiseException = raiseExceptionVec.orR
  val exceptionNO = ExcPriority.foldRight(0.U)((i: Int, sum: UInt) => Mux(raiseExceptionVec(i), i.U, sum))
  io.wenFix := raiseException

  val causeNO = (raiseIntr << (XLEN-1)) | Mux(raiseIntr, intrNO, exceptionNO)
  io.intrNO := Mux(raiseIntr, causeNO, 0.U)

  val raiseExceptionIntr = (raiseException || raiseIntr) && io.instrValid
  val retTarget = Wire(UInt(VAddrBits.W))
  val trapTarget = Wire(UInt(VAddrBits.W))
  io.redirect.valid := (valid && func === CSROpType.jmp) || raiseExceptionIntr || resetSatp
  io.redirect.rtype := 0.U
  io.redirect.target := Mux(resetSatp, io.cfIn.pc + 4.U, Mux(raiseExceptionIntr, trapTarget, retTarget))

  Debug(raiseExceptionIntr, "excin %b excgen %b", csrExceptionVec.asUInt(), iduExceptionVec.asUInt())
  Debug(raiseExceptionIntr, "int/exc: pc %x int (%d):%x exc: (%d):%x\n",io.cfIn.pc, intrNO, io.cfIn.intrVec.asUInt, exceptionNO, raiseExceptionVec.asUInt)
  Debug(raiseExceptionIntr, "[MST] time %d pc %x mstatus %x mideleg %x medeleg %x mode %x\n", GTimer(), io.cfIn.pc, mstatus, mideleg , medeleg, priviledgeMode)
  Debug(io.redirect.valid, "redirect to %x\n", io.redirect.target)
  Debug(resetSatp, "satp reset\n")

  // Branch control

  val delegVecM = Mux(raiseIntr, mideleg, medeleg)
  val delegVecS = Mux(raiseIntr, sideleg, sedeleg)
  // val delegS = ((delegVecM & (1 << (causeNO & 0xf))) != 0) && (priviledgeMode < ModeM);
  val delegS = (delegVecM(causeNO(4,0))) && (priviledgeMode < ModeM)
  val delegU = (delegVecS(causeNO(4,0))) && (priviledgeMode < ModeS)
  val tvalWen = !(hasInstrPageFault || hasLoadPageFault || hasStorePageFault || hasLoadAddrMisaligned || hasStoreAddrMisaligned ||
                  aluULibInstrFault || lsuULibLoadFault || lsuULibStoreFault ||
                  aluSLibInstrFault || lsuSLibLoadFault || lsuSLibStoreFault) || raiseIntr // in nutcore-riscv64, no exception will come together with PF

  ret := isMret || isSret || isUret
  trapTarget := Mux(delegS, Mux(delegU, utvec, stvec), mtvec)(VAddrBits-1, 0)
  retTarget := DontCare
  // TODO redirect target
  // val illegalEret = TODO

  // DASICS DEBUG
//  when (lsuULibLoadFault || lsuULibStoreFault) {
//    printf("[DEBUG] utvec = 0x%x, ucause = 0x%x, uepc = 0x%x, utval = 0x%x\n", utvec, ucause, uepc, utval)
//    printf("[DEBUG] delegS = %b, delegU = %b\n", delegS, delegU)
//    printf("[DEBUG] trapTarget = 0x%x, mtvec = 0x%x, stvec = 0x%x\n", trapTarget, mtvec, stvec)
//  }

  when (valid && isMret) {
    val mstatusOld = WireInit(mstatus.asTypeOf(new MstatusStruct))
    val mstatusNew = WireInit(mstatus.asTypeOf(new MstatusStruct))
    // mstatusNew.mpp.m := ModeU //TODO: add mode U
    mstatusNew.ie.m := mstatusOld.pie.m
    priviledgeMode := mstatusOld.mpp
    mstatusNew.pie.m := true.B
    mstatusNew.mpp := ModeU
    mstatus := mstatusNew.asUInt
    lr := false.B
    retTarget := mepc(VAddrBits-1, 0)
  }

  when (valid && isSret) {
    val mstatusOld = WireInit(mstatus.asTypeOf(new MstatusStruct))
    val mstatusNew = WireInit(mstatus.asTypeOf(new MstatusStruct))
    // mstatusNew.mpp.m := ModeU //TODO: add mode U
    mstatusNew.ie.s := mstatusOld.pie.s
    priviledgeMode := Cat(0.U(1.W), mstatusOld.spp)
    mstatusNew.pie.s := true.B
    mstatusNew.spp := ModeU
    mstatus := mstatusNew.asUInt
    lr := false.B
    retTarget := sepc(VAddrBits-1, 0)
  }

  when (valid && isUret) {
    val mstatusOld = WireInit(mstatus.asTypeOf(new MstatusStruct))
    val mstatusNew = WireInit(mstatus.asTypeOf(new MstatusStruct))
    // mstatusNew.mpp.m := ModeU //TODO: add mode U
    mstatusNew.ie.u := mstatusOld.pie.u
    priviledgeMode := ModeU
    mstatusNew.pie.u := true.B
    mstatus := mstatusNew.asUInt
    retTarget := uepc(VAddrBits-1, 0)
  }

  when (raiseExceptionIntr) {
    val mstatusOld = WireInit(mstatus.asTypeOf(new MstatusStruct))
    val mstatusNew = WireInit(mstatus.asTypeOf(new MstatusStruct))

    when (delegS && !delegU) {
      scause := causeNO
      sepc := SignExt(io.cfIn.pc, XLEN)
      mstatusNew.spp := priviledgeMode
      mstatusNew.pie.s := mstatusOld.ie.s
      mstatusNew.ie.s := false.B
      priviledgeMode := ModeS
      when(tvalWen){stval := 0.U} // TODO: should not use =/=
      // printf("[*] mstatusNew.spp %x\n", mstatusNew.spp)
      // trapTarget := stvec(VAddrBits-1. 0)
    }.elsewhen (delegS && delegU) {
      ucause := causeNO
      uepc := SignExt(io.cfIn.pc, XLEN)
      mstatusNew.pie.u := mstatusOld.ie.u
      mstatusNew.ie.u := false.B
      priviledgeMode := ModeU
      when(tvalWen){utval := 0.U}
    }.otherwise {
      mcause := causeNO
      mepc := SignExt(io.cfIn.pc, XLEN)
      mstatusNew.mpp := priviledgeMode
      mstatusNew.pie.m := mstatusOld.ie.m
      mstatusNew.ie.m := false.B
      priviledgeMode := ModeM
      when(tvalWen){mtval := 0.U} // TODO: should not use =/=
      // trapTarget := mtvec(VAddrBits-1. 0)
    }
    // mstatusNew.pie.m := LookupTree(priviledgeMode, List(
    //   ModeM -> mstatusOld.ie.m,
    //   ModeH -> mstatusOld.ie.h, //ERROR
    //   ModeS -> mstatusOld.ie.s,
    //   ModeU -> mstatusOld.ie.u
    // ))

    mstatus := mstatusNew.asUInt
  }

  io.in.ready := true.B
  io.out.valid := valid

  // perfcnt

  val generalPerfCntList = Map(
    "Mcycle"      -> (0xb00, "perfCntCondMcycle"     ),
    "Minstret"    -> (0xb02, "perfCntCondMinstret"   ),
    "MultiCommit" -> (0xb03, "perfCntCondMultiCommit"),
    "MimemStall"  -> (0xb04, "perfCntCondMimemStall" ),
    "MaluInstr"   -> (0xb05, "perfCntCondMaluInstr"  ),
    "MbruInstr"   -> (0xb06, "perfCntCondMbruInstr"  ),
    "MlsuInstr"   -> (0xb07, "perfCntCondMlsuInstr"  ),
    "MmduInstr"   -> (0xb08, "perfCntCondMmduInstr"  ),
    "McsrInstr"   -> (0xb09, "perfCntCondMcsrInstr"  ),
    "MloadInstr"  -> (0xb0a, "perfCntCondMloadInstr" ),
    "MmmioInstr"  -> (0xb0b, "perfCntCondMmmioInstr" ),
    "MicacheHit"  -> (0xb0c, "perfCntCondMicacheHit" ),
    "MdcacheHit"  -> (0xb0d, "perfCntCondMdcacheHit" ),
    "MmulInstr"   -> (0xb0e, "perfCntCondMmulInstr"  ),
    "MifuFlush"   -> (0xb0f, "perfCntCondMifuFlush"  ),
    "MbpBRight"   -> (0xb10, "MbpBRight"             ),
    "MbpBWrong"   -> (0xb11, "MbpBWrong"             ),
    "MbpJRight"   -> (0xb12, "MbpJRight"             ),
    "MbpJWrong"   -> (0xb13, "MbpJWrong"             ),
    "MbpIRight"   -> (0xb14, "MbpIRight"             ),
    "MbpIWrong"   -> (0xb15, "MbpIWrong"             ),
    "MbpRRight"   -> (0xb16, "MbpRRight"             ),
    "MbpRWrong"   -> (0xb17, "MbpRWrong"             ),
    "Ml2cacheHit" -> (0xb18, "perfCntCondMl2cacheHit"),
    "Custom1"     -> (0xb19, "Custom1"               ),
    "Custom2"     -> (0xb1a, "Custom2"               ),
    "Custom3"     -> (0xb1b, "Custom3"               ),
    "Custom4"     -> (0xb1c, "Custom4"               ),
    "Custom5"     -> (0xb1d, "Custom5"               ),
    "Custom6"     -> (0xb1e, "Custom6"               ),
    "Custom7"     -> (0xb1f, "Custom7"               ),
    "Custom8"     -> (0xb20, "Custom8"               )
  )

  val sequentialPerfCntList = Map(
    "MrawStall"   -> (0xb31, "perfCntCondMrawStall"    ),
    "MexuBusy"    -> (0xb32, "perfCntCondMexuBusy"     ),
    "MloadStall"  -> (0xb33, "perfCntCondMloadStall"   ),
    "MstoreStall" -> (0xb34, "perfCntCondMstoreStall"  ),
    "ISUIssue"    -> (0xb35, "perfCntCondISUIssue"     )
  )

  val outOfOrderPerfCntList = Map(
    "MrobFull"    -> (0xb31, "perfCntCondMrobFull"     ),
    "Malu1rsFull" -> (0xb32, "perfCntCondMalu1rsFull"  ),
    "Malu2rsFull" -> (0xb33, "perfCntCondMalu2rsFull"  ),
    "MbrursFull"  -> (0xb34, "perfCntCondMbrursFull"   ),
    "MlsursFull"  -> (0xb35, "perfCntCondMlsursFull"   ),
    "MmdursFull"  -> (0xb36, "perfCntCondMmdursFull"   ),
    "MmemqFull"   -> (0xb37, "perfCntCondMmemqFull"    ),
    "MrobEmpty"   -> (0xb38, "perfCntCondMrobEmpty"    ),
    "MstqFull"    -> (0xb39, "perfCntCondMstqFull"     ),
    "McmtCnt0"    -> (0xb40, "perfCntCondMcmtCnt0"     ),
    "McmtCnt1"    -> (0xb41, "perfCntCondMcmtCnt1"     ),
    "McmtCnt2"    -> (0xb42, "perfCntCondMcmtCnt2"     ),
    "McmtStrHaz1" -> (0xb43, "perfCntCondMcmtStrHaz1"  ),
    "McmtStrHaz2" -> (0xb44, "perfCntCondMcmtStrHaz2"  ),
    "MaluInstr2"  -> (0xb45, "perfCntCondMaluInstr2"   ),
    "Mdispatch0"  -> (0xb46, "perfCntCondMdispatch0"   ),
    "Mdispatch1"  -> (0xb47, "perfCntCondMdispatch1"   ),
    "Mdispatch2"  -> (0xb48, "perfCntCondMdispatch2"   ),
    "MlsuIssue"   -> (0xb49, "perfCntCondMlsuIssue"    ),
    "MmduIssue"   -> (0xb4a, "perfCntCondMmduIssue"    ),
    "MbruCmt"     -> (0xb4b, "perfCntCondMbruCmt"       ),
    "MbruCmtWrong"-> (0xb4c, "perfCntCondMbruCmtWrong"  ),
    "MicacheLoss" -> (0xb4d, "perfCntCondMicacheLoss"   ),
    "MdcacheLoss" -> (0xb4e, "perfCntCondMdcacheLoss"   ),
    "Ml2cacheLoss"-> (0xb4f, "perfCntCondMl2cacheLoss"  ),
    "MbrInROB_0"  -> (0xb50, "perfCntCondMbrInROB_0"   ),
    "MbrInROB_1"  -> (0xb51, "perfCntCondMbrInROB_1"   ),
    "MbrInROB_2"  -> (0xb52, "perfCntCondMbrInROB_2"   ),
    "MbrInROB_3"  -> (0xb53, "perfCntCondMbrInROB_3"   ),
    "MbrInROB_4"  -> (0xb54, "perfCntCondMbrInROB_4"   ),
    "Mdp1StBlk"   -> (0xb55, "perfCntCondMdp1StBlk"   ),
    "Mdp1StRSf"   -> (0xb56, "perfCntCondMdp1StRSf"   ),
    "Mdp1StROBf"  -> (0xb57, "perfCntCondMdp1StROBf"   ),
    "Mdp1StConf"  -> (0xb58, "perfCntCondMdp1StConf"   ),
    "Mdp1StCnt"   -> (0xb59, "perfCntCondMdp1StCnt"   ),
    "Mdp2StBlk"   -> (0xb5a, "perfCntCondMdp2StBlk"   ),
    "Mdp2StRSf"   -> (0xb5b, "perfCntCondMdp2StRSf"   ),
    "Mdp2StROBf"  -> (0xb5c, "perfCntCondMdp2StROBf"   ),
    "Mdp2StConf"  -> (0xb5d, "perfCntCondMdp2StConf"   ),
    "Mdp2StSeq"   -> (0xb5e, "perfCntCondMdp2StSeq"   ),
    "Mdp2StCnt"   -> (0xb5f, "perfCntCondMdp2StCnt"   ),
    "MloadCnt"    -> (0xb60, "perfCntCondMloadCnt"   ),
    "MstoreCnt"   -> (0xb61, "perfCntCondMstoreCnt"   ),
    "MmemSBL"     -> (0xb62, "perfCntCondMmemSBL"   ),
    "MpendingLS  "-> (0xb63, "perfCntCondMpendingLS"   ),     //Maunally updated
    "MpendingSCmt"-> (0xb64, "perfCntCondMpendingSCmt"   ), //Maunally updated
    "MpendingSReq"-> (0xb65, "perfCntCondMpendingSReq"   ), //Maunally updated
    "MicacheReq"  -> (0xb66, "perfCntCondMicacheReq"   ),
    "MdcacheReq"  -> (0xb67, "perfCntCondMdcacheReq"   ),
    "Ml2cacheReq" -> (0xb68, "perfCntCondMl2cacheReq"   ),
    "MdpNoInst"   -> (0xb69, "perfCntCondMdpNoInst"   )
    // "MmemLBS"  -> (0xb6a, "perfCntCondMmemLBS"   ),//TODO
  )

  val perfCntList = generalPerfCntList ++  (if (EnableOutOfOrderExec) outOfOrderPerfCntList else sequentialPerfCntList) 

	val perfCntCond = List.fill(0x80)(WireInit(false.B))
  (perfCnts zip perfCntCond).map { case (c, e) => { when (e) { c := c + 1.U } } }
  // Manually update perf counter
  val pendingLS = WireInit(0.U(5.W))
  val pendingSCmt = WireInit(0.U(5.W))
  val pendingSReq = WireInit(0.U(5.W))
  BoringUtils.addSink(pendingLS, "perfCntSrcMpendingLS")
  BoringUtils.addSink(pendingSCmt, "perfCntSrcMpendingSCmt")
  BoringUtils.addSink(pendingSReq, "perfCntSrcMpendingSReq")
  when(perfCntCond(0xb03 & 0x7f)) { perfCnts(0xb02 & 0x7f) := perfCnts(0xb02 & 0x7f) + 2.U } // Minstret += 2 when MultiCommit
  if (hasPerfCnt) {
    when(true.B) { perfCnts(0xb63 & 0x7f) := perfCnts(0xb63 & 0x7f) + pendingLS } 
    when(true.B) { perfCnts(0xb64 & 0x7f) := perfCnts(0xb64 & 0x7f) + pendingSCmt } 
    when(true.B) { perfCnts(0xb65 & 0x7f) := perfCnts(0xb66 & 0x7f) + pendingSReq } 
  }

  BoringUtils.addSource(WireInit(true.B), "perfCntCondMcycle")
  perfCntList.map { case (name, (addr, boringId)) => {
    BoringUtils.addSink(perfCntCond(addr & 0x7f), boringId)
    if (!hasPerfCnt) {
      // do not enable perfcnts except for Mcycle and Minstret
      if (addr != perfCntList("Mcycle")._1 && addr != perfCntList("Minstret")._1) {
        perfCntCond(addr & 0x7f) := false.B
      }
    }
  }}

  val nutcoretrap = WireInit(false.B)
  BoringUtils.addSink(nutcoretrap, "nutcoretrap")
  def readWithScala(addr: Int): UInt = mapping(addr)._1

  if (!p.FPGAPlatform) {
    // to monitor
    BoringUtils.addSource(readWithScala(perfCntList("Mcycle")._1), "simCycleCnt")
    BoringUtils.addSource(readWithScala(perfCntList("Minstret")._1), "simInstrCnt")

    if (hasPerfCnt && false) {
      // display all perfcnt when nutcoretrap is executed
      val PrintPerfCntToCSV = true
      when (nutcoretrap) {
        printf("======== PerfCnt =========\n")
        perfCntList.toSeq.sortBy(_._2._1).map { case (name, (addr, boringId)) =>
          printf("%d <- " + name + "\n", readWithScala(addr)) }
        if(PrintPerfCntToCSV){
        printf("======== PerfCntCSV =========\n\n")
        perfCntList.toSeq.sortBy(_._2._1).map { case (name, (addr, boringId)) =>
          printf(name + ", ")}
        printf("\n\n\n")
        perfCntList.toSeq.sortBy(_._2._1).map { case (name, (addr, boringId)) =>
          printf("%d, ", readWithScala(addr)) }
        printf("\n\n\n")
        }
      }
    }

    // for differential testing
    BoringUtils.addSource(RegNext(priviledgeMode), "difftestMode")
    BoringUtils.addSource(RegNext(mstatus), "difftestMstatus")
    BoringUtils.addSource(RegNext(mstatus & sstatusRmask), "difftestSstatus")
    BoringUtils.addSource(RegNext(mepc), "difftestMepc")
    BoringUtils.addSource(RegNext(sepc), "difftestSepc")
    BoringUtils.addSource(RegNext(mcause), "difftestMcause")
    BoringUtils.addSource(RegNext(scause), "difftestScause")
  } else {
    if (!p.FPGAPlatform) {
      BoringUtils.addSource(readWithScala(perfCntList("Mcycle")._1), "simCycleCnt")
      BoringUtils.addSource(readWithScala(perfCntList("Minstret")._1), "simInstrCnt")
    } else {
      BoringUtils.addSource(readWithScala(perfCntList("Minstret")._1), "ilaInstrCnt")
    }
  }
}
