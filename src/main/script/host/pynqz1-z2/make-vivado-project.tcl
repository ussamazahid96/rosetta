if {$argc != 5} {
  puts "Expected: <rosetta root> <accel verilog> <proj name> <proj dir> <freq>"
  exit
}

# pull cmdline variables to use during setup
set config_rosetta_root  [lindex $argv 0]
set config_rosetta_verilog "$config_rosetta_root/src/main/verilog"
set config_accel_verilog [lindex $argv 1]
set config_proj_name [lindex $argv 2]
set config_proj_dir [lindex $argv 3]
set config_freq [lindex $argv 4]
puts $config_rosetta_verilog
# fixed for platform
set config_proj_part "xc7z020clg400-1"
set xdc_dir "$config_rosetta_root/src/main/script/host/pynqz1-z2"

# set up project
create_project $config_proj_name $config_proj_dir -part $config_proj_part
update_ip_catalog

#Add PYNQ XDC
add_files -fileset constrs_1 -norecurse "${xdc_dir}/PYNQ-Z1_C.xdc"

# add the Verilog implementation for the accelerator
add_files -norecurse $config_accel_verilog
# add misc verilog files used by fpga-rosetta
add_files -norecurse $config_rosetta_verilog/Q_srl.v $config_rosetta_verilog/DualPortBRAM.v

# create block design
create_bd_design "procsys"
create_bd_cell -type ip -vlnv xilinx.com:ip:processing_system7:5.5 ps7
set ps7 [get_bd_cells ps7]
apply_bd_automation -rule xilinx.com:bd_rule:processing_system7 -config {make_external "FIXED_IO, DDR" Master "Disable" Slave "Disable" } $ps7
source "${xdc_dir}/pynq_revC.tcl"
set_property -dict [apply_preset $ps7] $ps7

# FPGA clocks
set_property -dict [list CONFIG.PCW_FPGA0_PERIPHERAL_FREQMHZ $config_freq CONFIG.PCW_FPGA1_PERIPHERAL_FREQMHZ {142.86} CONFIG.PCW_FPGA2_PERIPHERAL_FREQMHZ {200} CONFIG.PCW_FPGA3_PERIPHERAL_FREQMHZ {166.67} CONFIG.PCW_EN_CLK1_PORT {1} CONFIG.PCW_EN_CLK2_PORT {1} CONFIG.PCW_EN_CLK3_PORT {1} CONFIG.PCW_USE_M_AXI_GP0 {1}] $ps7

# add the accelerator RTL module into the block design
create_bd_cell -type module -reference RosettaWrapper RosettaWrapper_0
# connect control-status registers
apply_bd_automation -rule xilinx.com:bd_rule:axi4 -config {Master "/ps7/M_AXI_GP0" Clk "Auto" }  [get_bd_intf_pins RosettaWrapper_0/io_csr]

# rewire reset port to use active-high
disconnect_bd_net [get_bd_nets rst_ps7*peripheral_aresetn] [get_bd_pins RosettaWrapper_0/reset]
connect_bd_net [get_bd_pins [get_bd_cells *rst_ps7*]/peripheral_reset] [get_bd_pins RosettaWrapper_0/reset]

# make the block design look prettier
regenerate_bd_layout
validate_bd_design
save_bd_design
# write block design tcl
write_bd_tcl $config_proj_dir/rosetta.tcl

# create HDL wrapper
make_wrapper -files [get_files $config_proj_dir/$config_proj_name.srcs/sources_1/bd/procsys/procsys.bd] -top
add_files -norecurse $config_proj_dir/$config_proj_name.srcs/sources_1/bd/procsys/hdl/procsys_wrapper.v
update_compile_order -fileset sources_1
update_compile_order -fileset sim_1
set_property top procsys_wrapper [current_fileset]

# use manual compile order to ensure accel verilog is processed prior to block design
#update_compile_order -fileset sources_1
#set_property source_mgmt_mode DisplayOnly [current_project]


# set synthesis strategy
set_property strategy Flow_PerfOptimized_high [get_runs synth_1]

set_property STEPS.SYNTH_DESIGN.ARGS.DIRECTIVE AlternateRoutability [get_runs synth_1]
set_property STEPS.SYNTH_DESIGN.ARGS.RETIMING true [get_runs synth_1]

set_property strategy Performance_ExtraTimingOpt [get_runs impl_1]
set_property STEPS.OPT_DESIGN.ARGS.DIRECTIVE Explore [get_runs impl_1]
set_property STEPS.POST_ROUTE_PHYS_OPT_DESIGN.ARGS.DIRECTIVE AggressiveExplore [get_runs impl_1]
set_property STEPS.PHYS_OPT_DESIGN.ARGS.DIRECTIVE AggressiveExplore [get_runs impl_1]
set_property STEPS.POST_ROUTE_PHYS_OPT_DESIGN.IS_ENABLED true [get_runs impl_1]
