include Makefile.check

default: project

STANDALONE ?= false

# vivado project name
PRJ ?= myproject
PRJ_FULL = $(PRJ)-$(BOARD)
VIVADO_FLAG = -nolog -nojournal -notrace

#--------------------------------------------------------------------
# Project building and implementation
#--------------------------------------------------------------------

PRJ_ROOT = board/$(BOARD)/build/$(PRJ_FULL)
XPR_FILE = $(PRJ_ROOT)/$(PRJ_FULL).xpr
$(XPR_FILE):
	make -C .. BOARD=$(BOARD)
	vivado $(VIVADO_FLAG) -mode batch -source board/$(BOARD)/mk.tcl -tclargs $(PRJ_FULL) $(STANDALONE)

project: $(XPR_FILE)

ARCH ?= zynq
BOOT_BUILD = boot/build
ABS_PRJ_ROOT = $(abspath $(PRJ_ROOT))

bootgen: $(BOOT_BUILD)/$(PRJ_FULL)/BOOT.BIN

$(BOOT_BUILD)/$(PRJ_FULL)/BOOT.BIN: project
	mkdir -p $(BOOT_BUILD)/$(ARCH)
	ln -sf $(ABS_PRJ_ROOT)/system_top_wrapper.xsa $(BOOT_BUILD)/$(ARCH)/ps.xsa
	ln -sf $(ABS_PRJ_ROOT)/$(PRJ_FULL).runs/impl_1/system_top_wrapper.bit $(BOOT_BUILD)/$(ARCH)/fpga.bit
	cd boot && xsct mk.tcl $(PRJ_FULL) $(STANDALONE)

vivado: $(XPR_FILE)
	vivado $(VIVADO_FLAG) $(XPR_FILE) &

.PHONY: default project vivado bootgen

clean-pynq-vivado:
	rm -rf board/pynq/build

clean-boot:
	rm -rf $(BOOT_BUILD)

clean: clean-boot clean-pynq-vivado

.PHONY: clean clean-boot clean-pynq-vivado
