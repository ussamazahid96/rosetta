#!/bin/sh

g++ -pthread -O3 -std=c++11 *.cpp -lcma -o app
