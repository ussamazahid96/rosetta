if {$argc != 1} {
  puts "Expected: <project_to_synthesize>"
  exit
}

open_project [lindex $argv 0]

launch_runs impl_1 -to_step write_bitstream -jobs 2
wait_on_run impl_1
