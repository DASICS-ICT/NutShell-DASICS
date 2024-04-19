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

package device

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import top.Settings

import bus.axi4._
import utils._

class ClintIO(val nrHart: Int) extends Bundle {
  val mtip = Output(Vec(nrHart, Bool()))
  val msip = Output(Vec(nrHart, Bool()))
}

class AXI4CLINT(nrHart: Int = 1, sim: Boolean = false) extends AXI4SlaveModule(new AXI4Lite, new ClintIO(nrHart)) {
  val mtime = RegInit(0.U(64.W))  // unit: us
  val mtimecmp: List[UInt] = List.fill(nrHart)(RegInit(0.U(64.W)))
  val mtimecmpMap: Map[Int, (UInt, UInt, UInt => UInt, UInt)] = mtimecmp.zipWithIndex.map {
    case (r, hart) => MaskedRegMap(0x4000 + hart * 8, r)
  }.toMap

  val msip: List[UInt] = List.fill(nrHart)(RegInit(0.U(32.W)))
  val msipMap: Map[Int, (UInt, UInt, UInt => UInt, UInt)] = msip.zipWithIndex.map {
    case (r, hart) => MaskedRegMap(0x0 + hart * 4, r)
  }.toMap

  val clk = (if (!sim) 40 /* 40MHz / 1000000 */ else 10000)
  val freq = RegInit(clk.U(16.W))
  val inc = RegInit(1.U(16.W))

  val cnt = RegInit(0.U(16.W))
  val nextCnt = cnt + 1.U
  cnt := Mux(nextCnt < freq, nextCnt, 0.U)
  val tick = (nextCnt === freq)
  when (tick) { mtime := mtime + inc }

  if (sim) {
    val isWFI = WireInit(false.B)
      BoringUtils.addSink(isWFI, "isWFI0")
      when (isWFI) { mtime := mtime + 100000.U }
    if (nrHart==2){
      val dummywfi = WireInit(false.B)
      BoringUtils.addSink(dummywfi, "isWFI1")
    }
  }

  val mapping: Map[Int, (UInt, UInt, UInt => UInt, UInt)] = Map(
    MaskedRegMap(0x8000, freq),
    MaskedRegMap(0x8008, inc),
    MaskedRegMap(0xbff8, mtime)
  ) ++ msipMap ++ mtimecmpMap
  def getOffset(addr: UInt) = addr(15,0)

  MaskedRegMap.generate(mapping, getOffset(raddr), in.r.bits.data,
    getOffset(waddr), in.w.fire, in.w.bits.data)

  io.extra.get.mtip.zip(mtimecmp).foreach {
    case (t, cmp) => t := RegNext(mtime >= cmp)
  }
  io.extra.get.msip.zip(msip).foreach {
    case (s, r) => s := RegNext(r =/= 0.U)
  }
}
