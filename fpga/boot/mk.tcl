# usage: xsct [this tcl file] [prj-brd] [standalone (true/false)]

if {[llength $argv] > 0} {
  set project_name [lindex $argv 0]
} else {
  puts "xsa file path is not given!"
  return 1
}

if {[llength $argv] > 1} {
  set standalone [lindex $argv 1]
} else {
  puts "standalone mode is not given!"
  return 1
}

set s [split $project_name -]
set prj [lindex $s 0]
set brd [lindex $s 1]

set script_dir [file normalize [file dirname [info script]]]
set build_dir ${script_dir}/build/${project_name}

set device_tree_repo_path "../../../device-tree-xlnx"

switch -regexp -- $brd {
  zedboard|pynq {
    set processor ps7_cortexa9_0
    set brd_version zedboard
    set arch zynq
  }
  zcu102|sidewinder|ultraZ|axu3cg|PXIe {
    set processor psu_cortexa53_0
    set brd_version zcu102-rev1.0
    set arch zynqmp
  }
  default {
    puts "Unsupported board $brd"
    return 1
  }
}

exec mkdir -p ${script_dir}/build/${arch}
set xsa_file ${script_dir}/build/${arch}/ps.xsa
set hw_design [hsi open_hw_design ${xsa_file}]

hsi generate_app -hw $hw_design -os standalone -proc $processor -app ${arch}_fsbl -sw fsbl -dir ${build_dir}/fsbl
if {$brd == "sidewinder"} {
  # see bug-list.md
  exec sed -i -e "s/0x03FFFFFFU, 0x02000FFFU);/0x03FFFFFFU, 0x03FFFFFFU);/g" ${build_dir}/fsbl/psu_init.c
}
if { [catch { exec make -C ${build_dir}/fsbl } msg ] } { }

if {$arch == "zynqmp"} {
  generate_app -hw $hw_design -os standalone -proc psu_pmu_0 -app zynqmp_pmufw -compile -sw pmufw -dir ${build_dir}/pmufw
  exec ln -sf ${build_dir}/pmufw/executable.elf ${script_dir}/build/${arch}/pmufw.elf
}

exec mkdir -p ${script_dir}/build/${arch}
exec ln -sf ${build_dir}/fsbl/executable.elf ${script_dir}/build/${arch}/fsbl.elf

if {$standalone == "true"} {
  set bif_file ${script_dir}/bootgen-${arch}-standalone.bif
  if { [catch { exec make -C ${script_dir}/../resource/fsbl-loader PRJ_FULL=${project_name}} msg ] } { }
} else {
  set bif_file ${script_dir}/bootgen-${arch}.bif
}
exec bootgen -arch ${arch} -image $bif_file -w -o i ${build_dir}/BOOT.BIN

#device tree
hsi set_repo_path ${device_tree_repo_path}
hsi create_sw_design device-tree -os device_tree -proc $processor
if {$brd != "ultraZ"} {
  hsi set_property CONFIG.periph_type_overrides "{BOARD ${brd_version}}" [hsi get_os]
}
hsi generate_target -dir ${build_dir}/dts

exit
