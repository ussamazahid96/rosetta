if {$argc != 5} {
  puts "Expected: <rosetta root> <jam repo> <proj name> <proj dir> <freq>"
  exit
}

# pull cmdline variables to use during setup
set config_rosetta_root  [lindex $argv 0]
set config_rosetta_verilog "$config_rosetta_root/src/main/verilog"
set config_jam_repo [lindex $argv 1]
set config_proj_name [lindex $argv 2]
set config_proj_dir [lindex $argv 3]
set config_freq [lindex $argv 4]

# fixed for platform
set config_proj_part "xczu3eg-sbva484-1-i"
set xdc_dir "$config_rosetta_root/src/main/script/host/ultra96"

# set up project
create_project $config_proj_name $config_proj_dir -part $config_proj_part
set_property ip_repo_paths [list $config_jam_repo] [current_project]
update_ip_catalog

#Add XDC
add_files -fileset constrs_1 -norecurse "${xdc_dir}/ultra96.xdc"


# create block design
create_bd_design "procsys"
source "${xdc_dir}/ultra96.tcl"
set_property -dict [list CONFIG.PSU__USE__M_AXI_GP0 {1} CONFIG.PSU__USE__S_AXI_GP2 {1}] [get_bd_cells zynq_ultra_ps_e_0]


# instantiate jam
create_bd_cell -type ip -vlnv xilinx.com:hls:BlackBoxJam:1.0 BlackBoxJam_0

apply_bd_automation -rule xilinx.com:bd_rule:axi4 -config { Clk_master {/zynq_ultra_ps_e_0/pl_clk2 ($config_freq MHz)} Clk_slave {/zynq_ultra_ps_e_0/pl_clk2 ($config_freq MHz)} Clk_xbar {/zynq_ultra_ps_e_0/pl_clk2 ($config_freq MHz)} Master {/zynq_ultra_ps_e_0/M_AXI_HPM0_FPD} Slave {/BlackBoxJam_0/s_axi_control} intc_ip {New AXI Interconnect} master_apm {0}}  [get_bd_intf_pins BlackBoxJam_0/s_axi_control]
apply_bd_automation -rule xilinx.com:bd_rule:axi4 -config { Clk_master {/zynq_ultra_ps_e_0/pl_clk2 ($config_freq MHz)} Clk_slave {/zynq_ultra_ps_e_0/pl_clk2 ($config_freq MHz)} Clk_xbar {/zynq_ultra_ps_e_0/pl_clk2 ($config_freq MHz)} Master {/BlackBoxJam_0/m_axi_hostmem} Slave {/zynq_ultra_ps_e_0/S_AXI_HP0_FPD} intc_ip {Auto} master_apm {0}}  [get_bd_intf_pins zynq_ultra_ps_e_0/S_AXI_HP0_FPD]

# make the block design look prettier
regenerate_bd_layout
validate_bd_design
save_bd_design


# create HDL wrapper
make_wrapper -files [get_files $config_proj_dir/$config_proj_name.srcs/sources_1/bd/procsys/procsys.bd] -top
add_files -norecurse $config_proj_dir/$config_proj_name.srcs/sources_1/bd/procsys/hdl/procsys_wrapper.v
update_compile_order -fileset sources_1
update_compile_order -fileset sim_1
set_property top procsys_wrapper [current_fileset]


# set synthesis strategy
set_property strategy Flow_PerfOptimized_high [get_runs synth_1]

set_property STEPS.SYNTH_DESIGN.ARGS.DIRECTIVE AlternateRoutability [get_runs synth_1]
set_property STEPS.SYNTH_DESIGN.ARGS.RETIMING true [get_runs synth_1]

set_property strategy Performance_ExtraTimingOpt [get_runs impl_1]
set_property STEPS.OPT_DESIGN.ARGS.DIRECTIVE Explore [get_runs impl_1]
set_property STEPS.POST_ROUTE_PHYS_OPT_DESIGN.ARGS.DIRECTIVE AggressiveExplore [get_runs impl_1]
set_property STEPS.PHYS_OPT_DESIGN.ARGS.DIRECTIVE AggressiveExplore [get_runs impl_1]
set_property STEPS.POST_ROUTE_PHYS_OPT_DESIGN.IS_ENABLED true [get_runs impl_1]
write_bd_tcl $config_proj_dir/procsys.tcl
