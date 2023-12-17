package nutcore

import chisel3._
import top.Settings

class DasicsIsuIO extends NutCoreBundle {
  val pc    = Input(UInt(VAddrBits.W))
  val addr  = Input(UInt(XLEN.W))
  val InSTrustedZone   = Output(Bool())
  val InUTrustedZone   = Output(Bool())
  val PermitLibLoad    = Output(Bool())
  val PermitLibStore   = Output(Bool())
}

class DasicsAluIO extends NutCoreBundle{
  val RedirectValid   = Input(Bool())
  val RedirectTarget  = Input(UInt(XLEN.W))
  val IsDasicscall    = Input(Bool())
}

class DasicsLsuIO extends NutCoreBundle{
  val valid           = Input(Bool())
  val addr            = Input(UInt(XLEN.W))
  val IsLoad          = Input(Bool())
  val InSTrustedZone  = Input(Bool())
  val InUTrustedZone  = Input(Bool())
  val PermitLibLoad   = Input(Bool())
  val PermitLibStore  = Input(Bool())
  val Deny            = Output(Bool())
}

class DasicsIsuCsrIO extends NutCoreBundle with HasDasicsConst{

  val MainCfg       = Input(UInt(XLEN.W))
  val SMainBoundHi  = Input(UInt(XLEN.W))
  val SMainBoundLo  = Input(UInt(XLEN.W))
  val UMainBoundHi  = Input(UInt(XLEN.W))
  val UMainBoundLo  = Input(UInt(XLEN.W))

  val LibCfgBase    = Input(UInt(XLEN.W))

  val LibBoundHiList = Input(Vec(NumDasicsMemBounds,UInt(XLEN.W)))
  val LibBoundLoList = Input(Vec(NumDasicsMemBounds, UInt(XLEN.W)))
}

class DasicsExuCsrIO extends DasicsIsuCsrIO{
  val pc    = Input(UInt(VAddrBits.W))
  val pmode = Input(UInt(2.W))

  val ReturnPC      = Input(UInt(XLEN.W))
  val ActiveZoneReturnPC = Input(UInt(XLEN.W))
  val MaincallEntry = Input(UInt(XLEN.W))

  val inTrustedZone = Output(Bool())
  val inJumpZone = Output(Bool())
  val targetInTrustedZone = Output(Bool())
  val targetInJumpZone = Output(Bool())
  val aluSLibInstrFault  = Output(Bool())
  val aluULibInstrFault  = Output(Bool())

  val lsuAddr = Output(UInt(XLEN.W))
  val lsuSLibLoadFault = Output(Bool())
  val lsuSLibStoreFault = Output(Bool())
  val lsuULibLoadFault = Output(Bool())
  val lsuULibStoreFault = Output(Bool())

  val JumpCfgBase   = Input(UInt(XLEN.W))
  val JumpBoundHiList = Input(Vec(NumDasicsJumpBounds,UInt(XLEN.W)))
  val JumpBoundLoList = Input(Vec(NumDasicsJumpBounds, UInt(XLEN.W)))
}

class DasicsIsuCheckerIO extends NutCoreBundle{
  val isu = new DasicsIsuIO
  val csr = new DasicsIsuCsrIO
}

class DasicsExuCheckerIO extends NutCoreBundle{
  val alu = new DasicsAluIO
  val lsu = new DasicsLsuIO
  val csr = new DasicsExuCsrIO
}

class DasicsIsuChecker(implicit val p: NutCoreConfig) extends NutCoreModule with HasCSRConst{
  val io = IO(new DasicsIsuCheckerIO)
  def detectInZone(addr: UInt, hi: UInt, lo: UInt, en: Bool) : Bool = en && addr >= lo && addr <= hi
  val isSMainEnable = detectInZone(io.csr.SMainBoundLo, io.csr.SMainBoundHi, 0.U(XLEN.W), io.csr.MainCfg(MCFG_SENA))
  val isUMainEnable = detectInZone(io.csr.UMainBoundLo, io.csr.UMainBoundHi, 0.U(XLEN.W), io.csr.MainCfg(MCFG_UENA))
  val isuInSTrustedZone = detectInZone(io.isu.pc, io.csr.SMainBoundHi, io.csr.SMainBoundLo, isSMainEnable) || !isSMainEnable
  val isuInUTrustedZone = detectInZone(io.isu.pc, io.csr.UMainBoundHi, io.csr.UMainBoundLo, isUMainEnable) || !isUMainEnable
  // DASICS Load/Store checks are performed in ISU stage for timing considerations
  // Note: Privilege level cannot be known until EXU stage; however, we can consider DASICS CSRs as static
  val dasicsLibSeq = if (Settings.get("IsRV32"))
    ((for (i <- 0 until 32 if i % 4 == 0)
      yield (io.csr.LibCfgBase(i + 3, i), io.csr.LibBoundHiList(i >> 2), io.csr.LibBoundLoList(i >> 2))))
  else ((for (i <- 0 until 64 if i % 4 == 0)
    yield (io.csr.LibCfgBase(i + 3, i), io.csr.LibBoundHiList(i >> 2), io.csr.LibBoundLoList(i >> 2))))
  val isuPermitLibLoad  = dasicsLibSeq.map(cfg => detectInZone(io.isu.addr, cfg._2, cfg._3, cfg._1(LIBCFG_V) && cfg._1(LIBCFG_R))).foldRight(false.B)(_ || _)  // If there exists one pair, that's ok
  val isuPermitLibStore = dasicsLibSeq.map(cfg => detectInZone(io.isu.addr, cfg._2, cfg._3, cfg._1(LIBCFG_V) && cfg._1(LIBCFG_W))).foldRight(false.B)(_ || _)
  io.isu.InSTrustedZone   := isuInSTrustedZone // "isu_in_s_trusted_zone"
  io.isu.InUTrustedZone   := isuInUTrustedZone // "isu_in_u_trusted_zone"
  io.isu.PermitLibLoad    := isuPermitLibLoad // "isu_perm_lib_ld"
  io.isu.PermitLibStore   := isuPermitLibStore // "isu_perm_lib_st"
}

class DasicsExuChecker(implicit val p: NutCoreConfig) extends NutCoreModule with HasCSRConst{

  val io = IO(new DasicsExuCheckerIO)
  // DASICS Main/Lib wen check
  // Note: when DASICS is enabled, lib functions cannot access CSRs
  def detectInZone(addr: UInt, hi: UInt, lo: UInt, en: Bool) : Bool = en && addr >= lo && addr <= hi
  val isSMainEnable = detectInZone(io.csr.SMainBoundLo, io.csr.SMainBoundHi, 0.U(XLEN.W), io.csr.MainCfg(MCFG_SENA))
  val isUMainEnable = detectInZone(io.csr.UMainBoundLo, io.csr.UMainBoundHi, 0.U(XLEN.W), io.csr.MainCfg(MCFG_UENA))

  def detectInTrustedZone(addr: UInt) : Bool = {
    val inSMainZone = detectInZone(addr, io.csr.SMainBoundHi, io.csr.SMainBoundLo, io.csr.pmode === ModeS && isSMainEnable)
    val inUMainZone = detectInZone(addr, io.csr.UMainBoundHi, io.csr.UMainBoundLo, io.csr.pmode === ModeU && isUMainEnable)

    val inSTrustedZone = inSMainZone || io.csr.pmode === ModeS && !isSMainEnable
    val inUTrustedZone = inUMainZone || io.csr.pmode === ModeU && !isUMainEnable

    io.csr.pmode > ModeS || inSTrustedZone || inUTrustedZone
  }
  val inTrustedZone = detectInTrustedZone(io.csr.pc)

  // DASICS -- Check LSU DASICS exception

  val (lsuIsValid, lsuIsLoad, lsuInSTrustedZone, lsuInUTrustedZone, lsuPermitLibLoad, lsuPermitLibStore) =
    (io.lsu.valid, io.lsu.IsLoad, io.lsu.InSTrustedZone, io.lsu.InUTrustedZone, io.lsu.PermitLibLoad, io.lsu.PermitLibStore)

  // Seperate access denying and exception raising
  val lsuSLibLoadDeny : Bool =  lsuIsLoad && io.csr.pmode === ModeS && !lsuInSTrustedZone && !lsuPermitLibLoad
  val lsuULibLoadDeny : Bool =  lsuIsLoad && io.csr.pmode === ModeU && !lsuInUTrustedZone && !lsuPermitLibLoad
  val lsuSLibStoreDeny: Bool = !lsuIsLoad && io.csr.pmode === ModeS && !lsuInSTrustedZone && !lsuPermitLibStore
  val lsuULibStoreDeny: Bool = !lsuIsLoad && io.csr.pmode === ModeU && !lsuInUTrustedZone && !lsuPermitLibStore
  val lsuDeny: Bool = lsuSLibLoadDeny || lsuULibLoadDeny || lsuSLibStoreDeny || lsuULibStoreDeny

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
//  val dasicsLibSeq = if (Settings.get("IsRV32"))
//    ((for (i <- 0 until 32 if i % 4 == 0)
//      yield (io.csr.LibCfgBase(i + 3, i), io.csr.LibBoundHiList(i >> 2), io.csr.LibBoundLoList(i >> 2))))
//  else ((for (i <- 0 until 64 if i % 4 == 0)
//    yield (io.csr.LibCfgBase(i + 3, i), io.csr.LibBoundHiList(i >> 2), io.csr.LibBoundLoList(i >> 2))))

   val dasicsJumpSeq = if (Settings.get("IsRV32"))
     ((for (i <- 0 until 32 if i % 16 == 0)
       yield (io.csr.JumpCfgBase(i + 15, i), io.csr.JumpBoundHiList(i >> 4), io.csr.JumpBoundLoList(i >> 4))))
   else ((for (i <- 0 until 64 if i % 16 == 0)
     yield (io.csr.JumpCfgBase(i + 15, i), io.csr.JumpBoundHiList(i >> 4), io.csr.JumpBoundLoList(i >> 4))))

  def detectInJumpZone(addr: UInt, trustedZone: Bool) : Bool = !trustedZone && dasicsJumpSeq.map(cfg => detectInZone(addr, cfg._2, cfg._3, cfg._1(JUMPCFG_V))).foldRight(false.B)(_ || _)
  val inJumpZone = detectInJumpZone(io.csr.pc, inTrustedZone)
  val targetInTrustedZone = detectInTrustedZone(io.alu.RedirectTarget)
  val targetInJumpZone = detectInJumpZone(io.alu.RedirectTarget, targetInTrustedZone)

  val aluPermitRedirect = inTrustedZone || (!inTrustedZone &&  targetInTrustedZone && (io.alu.RedirectTarget === io.csr.ReturnPC || io.alu.RedirectTarget === io.csr.MaincallEntry)) ||
    targetInJumpZone || ( inJumpZone && !targetInTrustedZone && !targetInJumpZone && io.alu.RedirectTarget === io.csr.ActiveZoneReturnPC)

  val aluSLibInstrFault = isSMainEnable && io.csr.pmode === ModeS && io.alu.RedirectValid && !aluPermitRedirect
  val aluULibInstrFault = isUMainEnable && io.csr.pmode === ModeU && io.alu.RedirectValid && !aluPermitRedirect

  //  when (aluSLibInstrFault || aluULibInstrFault)
  //  {
  //    printf("[DEBUG] aluULibInstrFault = %b, io.cfIn.pc = 0x%x, uepc = 0x%x, sepc = 0x%x\n", aluULibInstrFault, io.cfIn.pc, uepc, sepc)
  //    printf("[DEBUG] inTrustedZone = %b, targetInTrustedZone = %b, inLibFreeZone = %b, targetInLibFreeZone = %b\n",
  //      inTrustedZone, targetInTrustedZone, inLibFreeZone, targetInLibFreeZone)
  //    printf("[DEBUG] aluRedirectTarget = 0x%x, dasicsReturnPC = 0x%x, dasicsActiveZoneReturnPC = 0x%x\n",
  //      aluRedirectTarget, dasicsReturnPC, dasicsActiveZoneReturnPC)
  //  }

  //output
  io.csr.inTrustedZone := inTrustedZone
  io.csr.inJumpZone := inJumpZone
  io.csr.targetInTrustedZone := targetInTrustedZone
  io.csr.targetInJumpZone := targetInJumpZone
  io.csr.aluSLibInstrFault  := aluSLibInstrFault
  io.csr.aluULibInstrFault  := aluULibInstrFault
  io.csr.lsuAddr := io.lsu.addr
  io.csr.lsuSLibLoadFault := lsuSLibLoadFault
  io.csr.lsuSLibStoreFault := lsuSLibStoreFault
  io.csr.lsuULibLoadFault := lsuULibLoadFault
  io.csr.lsuULibStoreFault := lsuULibStoreFault

  io.lsu.Deny := lsuDeny
}
