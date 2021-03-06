# Rosetta (upgraded to chisel3)
Rosetta is a project template to rapidly deploy chisel3 and Vivado HLS accelerators on the Xilinx PYNQ/Ultra96 platform. It uses the PlatformWrapper components from the fpga-tidbits framework for easy memory mapped register file management. 

For Chisel accelerators, use the chisel3 branch. For Vivado HLS accelerators, use the hls branch. For now chisel3 branch has examples with AXILite and AXIMaster interface.

## Requirements
1. A working Chisel3 and sbt setup
2. Xilinx Vivado 2019.1 (make sure vivado is in PATH)
3. A PYNQ/Ultra96 board with pynq>=2.4

## Quickstart
1. Clone this repository and cd into it
2. Run make pynq, this may take several minutes. This will create a deployment folder under build/rosetta
3. Run make rsync to copy generated files to the PYNQ board. You may have to edit the BOARD_URI variable in the Makefile to get this working.
4. Open a PYNQ terminal via ssh, and cd into ~/rosetta
5. Run `sudo python3 main.py`
6. Enter two integers -- you should see their sum printed correctly, as computed by the FPGA accelerator.

## Under the Hood
1. Have a look at the hardware description under the src/main/scala -- the accelerator definition is in Accelerator.scala, the "entry point" for code generation is in Main.scala, and the infrastructure (where the magic happens) is in Rosetta.scala
2. Have a look at the example application under src/main/python -- note that it uses the auto-generated pynq driver to access the hardware signals. The pynq driver will be generated in build/hw/driver
3. Have a look at what the different Makefile targets generate inside the build/ folder. You can also try launching Vivado with the make launch_vivado_gui target after the project has been generated.
