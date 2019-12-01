#include <iostream>
#include <ctime>
#include "platform.h"
#include "TestRegOps.hpp"
using namespace std;

bool Run_TestRegOps(WrapperRegDriver * platform) 
{
  
  TestRegOps accel(platform);

  unsigned int a, b;
  cout << "Signature: " << hex << accel.get_signature() << dec << endl;
  
  cout << "Enter op_0: ";
  cin >> a;
  
  cout << "Enter op_1: ";
  cin >> b;

  accel.set_op_0(a);
  accel.set_op_1(b);

  cout << "Result: " << accel.get_sum() << " expected: " << a+b << endl;

  return (a+b) == accel.get_sum();
}


int main()
{
  WrapperRegDriver * platform = initPlatform();

  Run_TestRegOps(platform);

  deinitPlatform(platform);
  
  return 0;
}
