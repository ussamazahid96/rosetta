
def TestRegOps():
	from TestRegOps import TestRegOps
	accel = TestRegOps()
	print("Signature: {}".format(hex(accel.get_signature())))
	a = int(input("Enter op 1 = "))
	b = int(input("Enter op 2 = "))
	accel.set_op_0(a)
	accel.set_op_1(b)
	result = accel.get_sum()
	print("Result = {}, Expected = {}".format(result, a+b))
	assert result==a+b, "Test Failed"

def BRAMExample():
	from BRAMExample import BRAMExample
	accel = BRAMExample()
	print("Signature: {}".format(hex(accel.get_signature())))

	while True:
		cmd = input("Enter command {(w)rite, (r)ead, (q)uit}: ")
		if cmd == 'q':
			break
		elif cmd == 'w':
			(addr, data) = input("Enter addr and data: ").split(',')
			accel.set_write_addr(int(addr))
			accel.set_write_data(int(data))
			accel.set_write_enable(1)
			accel.set_write_enable(0)
		elif cmd == 'r':
			addr = input("Enter addr: ")
			accel.set_read_addr(int(addr))
			print(accel.get_read_data())
		else:
			print("Invalid Command!")


def DRAMExample():
	import pynq
	import time
	import numpy as np
	from DRAMExample import DRAMExample
	accel = DRAMExample()
	print("Signature: {}".format(hex(accel.get_signature())))
	ub = int(input("Enter upper bound of sum sequence, divisible by 16: "))
	assert ub%16==0, "Error: Upper bound must be divisible by 16"
	in_buf = pynq.allocate(shape=(ub,), dtype=np.uint32)
	in_buf[:] = range(1, ub+1)
	
	accel.set_baseAddr(in_buf.physical_address)
	accel.set_byteCount(len(in_buf)*4)
	accel.set_start(1)
	while accel.get_finished() != 1:
		pass
	del in_buf
	result = accel.get_sum()

	expected = (ub*(ub+1))//2
	print("Result = {}, Expected = {}".format(result, expected))
	assert result==expected, "Test Failed"

def TestAccumulateVector():
	import random
	from TestAccumulateVector import TestAccumulateVector
	accel = TestAccumulateVector()
	print("Signature: {}".format(hex(accel.get_signature())))
	expected = 0
	for i in range(1, accel.get_vector_num_elems()+1):
		val = random.randint(0,100)
		expected += val
		accel.set_vector_in_addr(i-1)
		accel.set_vector_in_data(val)
		accel.set_vector_in_write_enable(1)
		accel.set_vector_in_write_enable(0)

	accel.set_vector_sum_enable(1)
	while accel.get_vector_sum_done() != 1:
		pass
	result = accel.get_result()
	print("Result = {}, Expected = {}".format(result, expected))
	assert result==expected, "Test Failed"

def MemCpyExample():
	import pynq
	import numpy as np
	from MemCpyExample import MemCpyExample
	accel = MemCpyExample()
	print("Signature: {}".format(hex(accel.get_signature())))
	ub = int(input("Enter upper bound of sum sequence, divisible by 16: "))
	assert ub%16==0, "Error: Upper bound must be divisible by 16"
	in_buf = pynq.allocate(shape=(ub,), dtype=np.uint32)
	out_buf = pynq.allocate(shape=(ub,), dtype=np.uint32)
	in_buf[:] = range(1, ub+1)	

	accel.set_srcAddr(in_buf.physical_address)
	accel.set_destAddr(out_buf.physical_address)
	accel.set_byteCount(len(in_buf)*4)
	accel.set_start(1)
	while accel.get_finished() != 1:
		pass
	cc = accel.get_cycleCount()
	accel.set_start(0)

	if not np.array_equal(in_buf, out_buf):
		Exception("Test Failed")
	print("Cycle coutnt = {}".format(cc))
	del in_buf
	del out_buf


if __name__ == '__main__':
	TestRegOps()
	# BRAMExample()
	# DRAMExample()
	# MemCpyExample()
	# TestAccumulateVector()
