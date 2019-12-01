#!/bin/bash

if [[ ("$BOARD" == "Pynq-Z1") || ("$BOARD" == "Pynq-Z2") ]]; then
  DEF_BOARD="PYNQ"  
elif [[ ("$BOARD" == "Ultra96") ]]; then
  DEF_BOARD="ULTRA"
else
  echo "Error: BOARD variable has to be Ultra96, Pynq-Z1 and Pynq-Z2 Board."
  exit 1
fi

g++ -D$DEF_BOARD -pthread -O3 -std=c++11 *.cpp -lcma -o app
