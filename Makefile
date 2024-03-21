TOP = TopMain
SIM_TOP = SimTop
FPGA_TOP = Top


BUILD_DIR = $(abspath ./build)

RTL_DIR = $(BUILD_DIR)
RTL_SUFFIX ?= v
SIM_TOP_V = $(RTL_DIR)/$(SIM_TOP).$(RTL_SUFFIX)
FPGA_TOP_V = $(RTL_DIR)/$(FPGA_TOP).$(RTL_SUFFIX)
TOP_V = $(RTL_DIR)/$(TOP).$(RTL_SUFFIX)

SCALA_FILE = $(shell find ./src/main/scala -name '*.scala')
TEST_FILE = $(shell find ./src/test/scala -name '*.scala')

USE_READY_TO_RUN_NEMU = true

SIMTOP = top.TopMain
IMAGE ?= ready-to-run/linux.bin

DATAWIDTH ?= 64
BOARD ?= sim# sim  pynq  axu3cg
CORE  ?= inorder# inorder  ooo  embedded

MILL_ARGS_ALL  = $(MILL_ARGS)
MILL_ARGS_ALL += --target-dir $(RTL_DIR) BOARD=$(BOARD) CORE=$(CORE)
FPGA_ARGS =

ifneq ($(FIRTOOL),)
MILL_ARGS_ALL += --firtool-binary-path $(FIRTOOL)
endif

#MILL_ARGS_ALL += --split-verilog

.DEFAULT_GOAL = verilog

help:
	mill -i generator.test.runMain top.$(TOP) --help $(MILL_ARGS_ALL)



$(TOP_V): $(SCALA_FILE) # FOR FPGA RUNNING 
	mkdir -p $(@D)
	mill -i generator.test.runMain top.$(TOP) $(MILL_ARGS_ALL) $(FPGA_ARGS)
	mv $(FPGA_TOP_V) $(TOP_V)
	sed -i -e 's/_\(aw\|ar\|w\|r\|b\)_\(\|bits_\)/_\1/g' $@
	sed -i '/\/\/ ----- 8< ----- FILE "firrtl_black_box_resource_files\.f" ----- 8< -----/,$$d' $@
	sed -n -e '/\/\/ ----- 8< ----- FILE "\.\/DifftestRunaheadEvent\.v" ----- 8< -----/,$$p' $@ > ./build/DifftestRunaheadEvent.v
	sed -i '/\/\/ ----- 8< ----- FILE "\.\/DifftestRunaheadEvent\.v" ----- 8< -----/,$$d' $@
	@git log -n 1 >> .__head__
	@git diff >> .__diff__
	@sed -i 's/^/\/\// ' .__head__
	@sed -i 's/^/\/\//' .__diff__
	@cat .__head__ .__diff__ $@ > .__out__
	@mv .__out__ $@
	@rm .__head__ .__diff__

deploy: build/top.zip


build/top.zip: $(TOP_V)
	@zip -r $@ $< $<.conf build/*.anno.json

.PHONY: deploy build/top.zip

ifeq ($(BOARD),sim)
verilog: sim-verilog
else
verilog: $(TOP_V)
endif

$(SIM_TOP_V): $(SCALA_FILE) $(TEST_FILE)
	mkdir -p $(@D)
	mill -i generator.test.runMain $(SIMTOP) $(MILL_ARGS_ALL)
	sed -i -e 's/$$fatal/xs_assert(`__LINE__)/g' "$@"
	sed -i -e "s/\$$error(/\$$fwrite(32\'h80000002, /g" "$@"
	sed -i '/\/\/ ----- 8< ----- FILE "firrtl_black_box_resource_files\.f" ----- 8< -----/,$$d' $@

sim-verilog: $(SIM_TOP_V)

emu: sim-verilog
	$(MAKE) -C ./difftest emu WITH_CHISELDB=0 WITH_CONSTANTIN=0 RTL_SUFFIX=$(RTL_SUFFIX)

emu-run: sim-verilog
	$(MAKE) -C ./difftest emu-run RTL_SUFFIX=$(RTL_SUFFIX)

simv: sim-verilog
	$(MAKE) -C ./difftest simv WITH_CHISELDB=0 WITH_CONSTANTIN=0 RTL_SUFFIX=$(RTL_SUFFIX)

init:
	git submodule update --init

clean:
	rm -rf $(BUILD_DIR)

bsp:
	mill -i mill.bsp.BSP/install

idea:
	mill -i mill.idea.GenIdea/idea

.PHONY: verilog emu clean help $(REF_SO)
