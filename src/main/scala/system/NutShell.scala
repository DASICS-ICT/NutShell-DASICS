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

package system

import nutcore._
import bus.axi4.{AXI4, AXI4Lite}
import bus.simplebus._
import device.{AXI4CLINT, AXI4PLIC}
import top.Settings

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

trait HasSoCParameter {
  val EnableILA = Settings.get("EnableILA")
  val HasL2cache = Settings.get("HasL2cache")
  val HasPrefetch = Settings.get("HasPrefetch")
  val HasDualCore = Settings.get("HasDualCore")
}

class ILABundle extends NutCoreBundle {
  val WBUpc = UInt(VAddrBits.W)
  val WBUvalid = UInt(1.W)
  val WBUrfWen = UInt(1.W)
  val WBUrfDest = UInt(5.W)
  val WBUrfData = UInt(XLEN.W)
  val InstrCnt = UInt(64.W)
}

class NutShell(implicit val p: NutCoreConfig) extends Module with HasSoCParameter {
  val io = IO(new Bundle{
    val mem = new AXI4
    val mmio = (if (p.FPGAPlatform) { new AXI4 } else { new SimpleBusUC })
    val frontend = Flipped(new AXI4)
    val meip = Input(UInt(Settings.getInt("NrExtIntr").W))
    val ila = if (p.FPGAPlatform && EnableILA) Some(Output(new ILABundle)) else None
  })

  val nutcore = Module(new NutCore)
  val cohMg = Module(new CoherenceManager)
  val xbar = Module(new SimpleBusCrossbarNto1(2))
  cohMg.io.in <> nutcore.io.imem.mem
  nutcore.io.dmem.coh <> cohMg.io.out.coh
  xbar.io.in(0) <> cohMg.io.out.mem
  xbar.io.in(1) <> nutcore.io.dmem.mem

  val axi2sb = Module(new AXI42SimpleBusConverter())
  axi2sb.io.in <> io.frontend
  nutcore.io.frontend <> axi2sb.io.out

  val memport = xbar.io.out.toMemPort()
  memport.resp.bits.data := DontCare
  memport.resp.valid := DontCare
  memport.req.ready := DontCare

  val mem = if (HasL2cache) {
    val l2cacheOut = Wire(new SimpleBusC)
    val l2cacheIn = if (HasPrefetch) {
      val prefetcher = Module(new Prefetcher)
      val l2cacheIn = Wire(new SimpleBusUC)
      prefetcher.io.in <> xbar.io.out.req
      l2cacheIn.req <> prefetcher.io.out
      xbar.io.out.resp <> l2cacheIn.resp
      l2cacheIn
    } else xbar.io.out
    val l2Empty = Wire(Bool())
    l2cacheOut <> Cache(in = l2cacheIn, mmio = 0.U.asTypeOf(new SimpleBusUC) :: Nil, flush = "b00".U, empty = l2Empty, enable = true)(
      CacheConfig(name = "l2cache", totalSize = 128, cacheLevel = 2), p)
    l2cacheOut.coh.resp.ready := true.B
    l2cacheOut.coh.req.valid := false.B
    l2cacheOut.coh.req.bits := DontCare
    l2cacheOut.mem
  } else {
    xbar.io.out
  }

  val memMapRegionBits = Settings.getInt("MemMapRegionBits")
  val memMapBase = Settings.getLong("MemMapBase")
  val memAddrMap = Module(new SimpleBusAddressMapper((memMapRegionBits, memMapBase)))
  memAddrMap.io.in <> mem
  io.mem <> memAddrMap.io.out.toAXI4(true)
  
  nutcore.io.imem.coh.resp.ready := true.B
  nutcore.io.imem.coh.req.valid := false.B
  nutcore.io.imem.coh.req.bits := DontCare

  val addrSpace = List(
    (0x38000000L, 0x00010000L), // CLINT
    (0x3c000000L, 0x04000000L), // PLIC
    if (Settings.get("PLPeriphery")) { (0x70000000L, 0x90000000L) }
    else { (Settings.getLong("MMIOBase"), Settings.getLong("MMIOSize")) }, // external devices
  )
  val mmioXbar = Module(new SimpleBusCrossbar1toN(addrSpace))
  mmioXbar.io.in <> nutcore.io.mmio

  val extDev = mmioXbar.io.out(2)
  if (p.FPGAPlatform) { io.mmio <> extDev.toAXI4() }
  else { io.mmio <> extDev }

  val clint = Module(new AXI4CLINT(nrHart = if (HasDualCore) 2 else 1, sim = !p.FPGAPlatform))
  clint.io.in <> mmioXbar.io.out(0).toAXI4Lite()
  BoringUtils.bore(clint.io.extra.get.mtip(0), Seq(nutcore.mtipSync))
  BoringUtils.bore(clint.io.extra.get.msip(0), Seq(nutcore.msipSync))

  val plic = Module(new AXI4PLIC(nrIntr = Settings.getInt("NrExtIntr"), nrHart = if (HasDualCore) 2 else 1))
  plic.io.in <> mmioXbar.io.out(1).toAXI4Lite()
  plic.io.extra.get.intrVec := RegNext(RegNext(io.meip))
  val meipSync = plic.io.extra.get.meip(0)
  BoringUtils.bore(meipSync, Seq(nutcore.meipSync))
  

  // ILA
  if (p.FPGAPlatform) {
    def BoringUtilsConnect(sink: UInt, id: String) {
      val temp = WireInit(0.U(64.W))
      BoringUtils.addSink(temp, id + p.HartID.toString)
      sink := temp
    }

    val dummy = WireInit(0.U.asTypeOf(new ILABundle))
    val ila = io.ila.getOrElse(dummy)
    BoringUtilsConnect(ila.WBUpc      ,"ilaWBUpc")
    BoringUtilsConnect(ila.WBUvalid   ,"ilaWBUvalid")
    BoringUtilsConnect(ila.WBUrfWen   ,"ilaWBUrfWen")
    BoringUtilsConnect(ila.WBUrfDest  ,"ilaWBUrfDest")
    BoringUtilsConnect(ila.WBUrfData  ,"ilaWBUrfData")
    BoringUtilsConnect(ila.InstrCnt   ,"ilaInstrCnt")

    // Ignore ILA for Core 1; just prevent BoringUtils errors
    // BoringUtils.addSink(dummy.WBUpc, "ilaWBUpc1")
    // BoringUtils.addSink(dummy.WBUvalid, "ilaWBUvalid1")
    // BoringUtils.addSink(dummy.WBUrfWen, "ilaWBUrfWen1")
    // BoringUtils.addSink(dummy.WBUrfDest, "ilaWBUrfDest1")
    // BoringUtils.addSink(dummy.WBUrfData, "ilaWBUrfData1")
    // BoringUtils.addSink(dummy.InstrCnt, "ilaInstrCnt1")
  }

  if (HasDualCore) {
    val p1 = p.copy(HartID = 1)
    val nutcore1 = Module(new NutCore()(p1))
    val cohMg1 = Module(new CoherenceManager)
    val xbar1 = Module(new SimpleBusCrossbarNto1(2))
    cohMg1.io.in <> nutcore1.io.imem.mem
    nutcore1.io.dmem.coh <> cohMg1.io.out.coh
    xbar1.io.in(0) <> cohMg1.io.out.mem
    xbar1.io.in(1) <> nutcore1.io.dmem.mem

    nutcore1.io.frontend := DontCare
    nutcore1.io.frontend.req.valid := false.B

    val memport1 = xbar1.io.out.toMemPort()
    memport1.resp.bits.data := DontCare
    memport1.resp.valid := DontCare
    memport1.req.ready := DontCare

    // Dual Core automatically disables L2 cache
    val ccc = Module(new CrossCoreCoherence(2))
    ccc.io.in(0) <> xbar.io.out
    ccc.io.in(1) <> xbar1.io.out

    val cccMem = if (HasL2cache) {
      val l2cacheOut = Wire(new SimpleBusC)
      val l2cacheIn = ccc.io.out
      val l2Empty = Wire(Bool())
      l2cacheOut <> Cache(
        in = l2cacheIn, mmio = 0.U.asTypeOf(new SimpleBusUC) :: Nil, flush = "b00".U, empty = l2Empty
      )(CacheConfig(name = "l2cache", totalSize = 128, cacheLevel = 2), p)
      l2cacheOut.coh.resp.ready := true.B
      l2cacheOut.coh.req.valid := false.B
      l2cacheOut.coh.req.bits := DontCare
      l2cacheOut.mem
    } else {
      ccc.io.out
    }

    memAddrMap.io.in <> cccMem

    nutcore1.io.imem.coh.resp.ready := true.B
    nutcore1.io.imem.coh.req.valid := false.B
    nutcore1.io.imem.coh.req.bits := DontCare

    val mmioBus = Module(new SimpleBusCrossbarNto1(2))
    mmioBus.io.in(0) <> nutcore.io.mmio
    mmioBus.io.in(1) <> nutcore1.io.mmio
    mmioXbar.io.in <> mmioBus.io.out

    BoringUtils.bore(clint.io.extra.get.mtip(1), Seq(nutcore1.mtipSync))
    BoringUtils.bore(clint.io.extra.get.msip(1), Seq(nutcore1.msipSync))
    BoringUtils.bore(plic.io.extra.get.meip(1), Seq(nutcore1.meipSync))
  }
}
