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

package utils

import chisel3._
import chisel3.util.experimental

import nutcore.NutCoreConfig

// Wrapper for chisel3.util.experimental.BoringUtils to contain HartID for NutCore
object BoringUtils {
  def addSource(component: Data, name: String, disableDedup: Boolean = false, uniqueName: Boolean = false)
               (implicit p: NutCoreConfig): String =
    experimental.BoringUtils.addSource(component, name + p.HartID.toString, disableDedup, uniqueName)

  def addSink(component: Data, name: String, disableDedup: Boolean = false, forceExists: Boolean = false)
             (implicit p: NutCoreConfig): Unit =
    experimental.BoringUtils.addSink(component, name + p.HartID.toString, disableDedup, forceExists)
}
