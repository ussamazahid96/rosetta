# ignore the first 2 args, since Vivado HLS also passes -f tclname as args
set config_proj_name [lindex $argv 2]
puts "HLS project: $config_proj_name"
set config_hwsrcdir [lindex $argv 3]
puts "HW source dir: $config_hwsrcdir"
set config_clkperiod [lindex $argv 4]
puts "Clock period: $config_clkperiod"
set config_proj_part [lindex $argv 5]
puts "FPGA Chip: $config_proj_part"

set config_toplevelfxn "BlackBoxJam"

# set up project
set cppfiles [glob $config_hwsrcdir/*.cpp]
open_project $config_proj_name
add_files $cppfiles -cflags "-std=c++0x"
set_top $config_toplevelfxn
open_solution sol1
set_part $config_proj_part

# use 64-bit AXI MM addresses
config_interface -m_axi_addr64

# syntesize and export
create_clock -period $config_clkperiod -name default
csynth_design
export_design -format ip_catalog
exit 0
