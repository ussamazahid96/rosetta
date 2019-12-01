# For PynqZ1
# NAME := pynqz1-z2
# BOARD_URI := xilinx@192.168.2.99:~/

# For Ultra96
NAME := ultra96
BOARD_URI := xilinx@192.168.3.1:~/

# target frequency for Vivado FPGA synthesis
FREQ_MHZ := 150.0
# controls whether Vivado will run in command-line or GUI mode
VIVADO_MODE := batch # gui

# other project settings
SBT ?= sbt
SBT_FLAGS ?= -Dsbt.log.noformat=true

# internal build dirs and names for the Makefile
TOP ?= $(shell readlink -f .)
BUILD_DIR ?= $(TOP)/build
BUILD_DIR_PYNQ := $(BUILD_DIR)/rosetta
BUILD_DIR_VERILOG := $(BUILD_DIR)/hw/verilog
BUILD_DIR_HWCPP := $(BUILD_DIR)/hw/cpp_emu
BUILD_DIR_HWDRV := $(BUILD_DIR)/hw/driver
BUILD_DIR_EMULIB_CPP := $(BUILD_DIR)/hw/cpp_emulib
VERILOG_SRC_DIR := $(TOP)/src/main/verilog
DRV_SRC_DIR := $(TOP)/src/main/cpp/regdriver
APP_SRC_DIR := $(TOP)/src/main/cpp/app
VIVADO_PROJ_SCRIPT := $(TOP)/src/main/script/host/$(NAME)/make-vivado-project.tcl
VIVADO_SYNTH_SCRIPT := $(TOP)/src/main/script/host/synth-vivado-project.tcl
PYNQ_SCRIPT_DIR := $(TOP)/src/main/script/pynq
HW_VERILOG := $(BUILD_DIR_VERILOG)/RosettaWrapper.v
BITFILE_PRJNAME := bitfile_synth
BITFILE_PRJDIR := $(BUILD_DIR)/bitfile_synth
GEN_BITFILE_PATH := $(BITFILE_PRJDIR)/$(BITFILE_PRJNAME).runs/impl_1/procsys_wrapper.bit
HWH_FILE := $(BITFILE_PRJDIR)/$(BITFILE_PRJNAME).srcs/sources_1/bd/procsys/hw_handoff/procsys.hwh
VIVADO_IN_PATH := $(shell command -v vivado 2> /dev/null)

# note that all targets are phony targets, no proper dependency tracking
.PHONY: hw_verilog test_hw hw_driver hw_vivadoproj bitfile pynq_hw pynq_sw pynq rsync test  check_vivado

check_vivado:
ifndef VIVADO_IN_PATH
    $(error "vivado not found in path")
endif

# generate Verilog for the Chisel accelerator
hw_verilog:
	$(SBT) $(SBT_FLAGS) "runMain rosetta.ChiselMain --target-dir $(BUILD_DIR_VERILOG)"

# testing hardware using the chisel tester
test_hw:
	$(SBT) $(SBT_FLAGS) "test:runMain testhardware"

# generate register driver for the Chisel accelerator
hw_driver:
	mkdir -p "$(BUILD_DIR_HWDRV)"; $(SBT) $(SBT_FLAGS) "runMain rosetta.DriverMain $(BUILD_DIR_HWDRV) $(DRV_SRC_DIR)"

# create a new Vivado project
hw_vivadoproj: hw_verilog check_vivado
	vivado -mode $(VIVADO_MODE) -notrace -source $(VIVADO_PROJ_SCRIPT) -tclargs $(TOP) $(HW_VERILOG) $(BITFILE_PRJNAME) $(BITFILE_PRJDIR) $(FREQ_MHZ)

# launch Vivado in GUI mode with created project
launch_vivado_gui: check_vivado
	vivado -mode gui $(BITFILE_PRJDIR)/$(BITFILE_PRJNAME).xpr

# run bitfile synthesis for the Vivado project
bitfile: hw_vivadoproj
	vivado -mode $(VIVADO_MODE) -notrace -source $(VIVADO_SYNTH_SCRIPT) -tclargs $(BITFILE_PRJDIR)/$(BITFILE_PRJNAME).xpr

# copy bitfile to the deployment folder, make tcl and hwh script for bitfile loader
pynq_hw: bitfile
	mkdir -p $(BUILD_DIR_PYNQ); cp $(GEN_BITFILE_PATH) $(BUILD_DIR_PYNQ)/rosetta.bit; cp $(BITFILE_PRJDIR)/rosetta.tcl $(BUILD_DIR_PYNQ)/; cp $(HWH_FILE) $(BUILD_DIR_PYNQ)/rosetta.hwh

# copy all user sources and driver sources to the deployment folder
pynq_sw: hw_driver
	mkdir -p $(BUILD_DIR_PYNQ); cp $(BUILD_DIR_HWDRV)/* $(BUILD_DIR_PYNQ)/; cp -r $(APP_SRC_DIR)/* $(BUILD_DIR_PYNQ)/

# copy scripts to the deployment folder
pynq_script:
	cp $(PYNQ_SCRIPT_DIR)/* $(BUILD_DIR_PYNQ)/

# get everything ready to copy onto the PYNQ
pynq: pynq_hw pynq_sw pynq_script

# use rsync to synchronize contents of the deployment folder onto the PYNQ
rsync:
	rsync -avz $(BUILD_DIR_PYNQ) $(BOARD_URI)

# remove everything that is built
clean:
	rm -rf $(BUILD_DIR) vivado* NA .Xil _1.*
