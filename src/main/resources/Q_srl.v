// original source:
// https://github.com/nachiket/tdfc/blob/master/verilog/queues/Q_srl_oreg3_prefull_SIMPLE.v

// changes by Yaman Umuroglu <maltanar@gmail.com>:
// - added copy of license text at the start of file
// - changed to synchronous active-high reset
// - added "count" output for monitoring FIFO #elems
// - added default assignments to comp process to prevent latches

// Copyright (c) 1999 The Regents of the University of California
// Copyright (c) 2010 The Regents of the University of Pennsylvania
// Copyright (c) 2011 Department of Electrical and Electronic Engineering, Imperial College London
//
// Permission to use, copy, modify, and distribute this software and
// its documentation for any purpose, without fee, and without a
// written agreement is hereby granted, provided that the above copyright
// notice and this paragraph and the following two paragraphs appear in
// all copies.
//
// IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY FOR
// DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING
// LOST PROFITS, ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION,
// EVEN IF THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF
// SUCH DAMAGE.
//
// THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
// AND FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE PROVIDED HEREUNDER IS ON
// AN "AS IS" BASIS, AND THE UNIVERSITY OF CALIFORNIA HAS NO OBLIGATIONS TO
// PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
//

// Q_srl_oreg3_prefull_SIMPLE.v
//
//  - In-page queue with parameterizable depth, bit width
//  - Stream I/O is triple (data, valid, back-pressure),
//      with EOS concatenated into the data
//  - Flow control for input & output is combinationally decoupled
//  - 2 <= depth <= 256
//      * (depth >= 2)  is required to decouple I/O flow control,
//          where empty => no produce,  full => no consume,
//          and depth 1 would ping-pong between the two at half rate
//      * (depth <= 256) can be modified
//           by changing ''synthesis loop_limit X'' below
//          and changing ''addrwidth'' or its log computation
//  - 1 <= width
//  - Queue storage is in SRL16E, up to depth 16 per LUT per bit-slice,
//      plus output register (for fast output)
//  - Queue addressing is done by ''addr'' up-down counter
//  - Queue fullness is checked by comparator (addr==depth)
//  - Queue fullness                           is pre-computed for next cycle
//  - Queue input back-pressure                is pre-computed for next cycle
//  - Queue output valid (state!=state__empty) is pre-computed for next cycle
//      (necessary since SRL data output reg requires non-boolean state)
//  - FSM has 3 states (empty, one, more)
//  - When empty, continue to emit most recently emitted value (for debugging)
//
//  - Queue slots used      = / (state==state_empty) ? 0
//                            | (state==state_one)   ? 1
//                            \ (state==state_more)  ? addr+2
//  - Queue slots used     <=  depth
//  - Queue slots remaining =  depth - used
//                          = / (state==state_empty) ? depth
//                            | (state==state_one)   ? depth-1
//                            \ (state==state_more)  ? depth-2-addr
//
//  - Synplify 7.1 / 8.0
//  - Eylon Caspi,  9/11/03, 8/18/04, 3/29/05


`ifdef  Q_srl
`else
`define Q_srl


module Q_srl (clock, reset, iData, iValid, iBackPressure, oData, oValid, oBackPressure, count);

   parameter depth = 16;   // - greatest #items in queue  (2 <= depth <= 256)
   parameter width = 16;   // - width of data (iData, oData)

   `define LOG2 (  (((depth))     ==0) ? 0	/* - depth==0   LOG2=0 */ \
		 : (((depth-1)>>0)==0) ? 0	/* - depth<=1   LOG2=0 */ \
		 : (((depth-1)>>1)==0) ? 1	/* - depth<=2   LOG2=1 */ \
		 : (((depth-1)>>2)==0) ? 2	/* - depth<=4   LOG2=2 */ \
		 : (((depth-1)>>3)==0) ? 3	/* - depth<=8   LOG2=3 */ \
		 : (((depth-1)>>4)==0) ? 4	/* - depth<=16  LOG2=4 */ \
		 : (((depth-1)>>5)==0) ? 5	/* - depth<=32  LOG2=5 */ \
		 : (((depth-1)>>6)==0) ? 6	/* - depth<=64  LOG2=6 */ \
		 : (((depth-1)>>7)==0) ? 7	/* - depth<=128 LOG2=7 */ \
		 :                       8)	/* - depth<=256 LOG2=8 */

// parameter addrwidth = LOG2;			// - width of queue addr

   parameter addrwidth =
		(  (((depth))     ==0) ? 0	// - depth==0   LOG2=0
		 : (((depth-1)>>0)==0) ? 0	// - depth<=1   LOG2=0
		 : (((depth-1)>>1)==0) ? 1	// - depth<=2   LOG2=1
		 : (((depth-1)>>2)==0) ? 2	// - depth<=4   LOG2=2
		 : (((depth-1)>>3)==0) ? 3	// - depth<=8   LOG2=3
		 : (((depth-1)>>4)==0) ? 4	// - depth<=16  LOG2=4
		 : (((depth-1)>>5)==0) ? 5	// - depth<=32  LOG2=5
		 : (((depth-1)>>6)==0) ? 6	// - depth<=64  LOG2=6
		 : (((depth-1)>>7)==0) ? 7	// - depth<=128 LOG2=7
		 :                       8)	// - depth<=256 LOG2=8
		 ;

   input     clock;
   input     reset;

   input  [width-1:0] iData;	// - input  stream data (concat data + eos)
   input              iValid;	// - input  stream valid
   output             iBackPressure;	// - input  stream back-pressure

   output [width-1:0] oData;	// - output stream data (concat data + eos)
   output             oValid;	// - output stream valid
   input              oBackPressure;	// - output stream back-pressure

   output [addrwidth:0] count;  // - output number of elems in queue

   reg    [addrwidth-1:0] addr, addr_, a_;		// - SRL16 address
							//     for data output
   reg 			  shift_en_;			// - SRL16 shift enable
   reg    [width-1:0] 	  srl [depth-2:0];		// - SRL16 memory
   reg 			  shift_en_o_;			// - SRLO  shift enable
   reg    [width-1:0] 	  srlo_, srlo			// - SRLO  output reg
			  /* synthesis syn_allow_retiming=0 */ ;

   parameter state_empty = 2'd0;    // - state empty : oValid=0 oData=UNDEFINED
   parameter state_one   = 2'd1;    // - state one   : oValid=1 oData=srlo
   parameter state_more  = 2'd2;    // - state more  : oValid=1 oData=srlo
				    //     #items in srl = addr+2

   reg [1:0] state, state_;	    // - state register

   wire      addr_full_;	    // - true iff addr==depth-2 on NEXT cycle
   reg       addr_full; 	    // - true iff addr==depth-2
   wire      addr_zero_;	    // - true iff addr==0
   wire      oValid_reg_;		    // - true iff state_empty   on NEXT cycle
   reg       oValid_reg  		    // - true iff state_empty
	     /* synthesis syn_allow_retiming=0 */ ;
   wire      iBackPressure_reg_;		    // - true iff !full         on NEXT cycle
   reg       iBackPressure_reg  		    // - true iff !full
	     /* synthesis syn_allow_retiming=0 */ ;

   assign addr_full_ = (state_==state_more) && (addr_==depth-2);
						// - queue full
   assign addr_zero_ = (addr==0);		// - queue contains 2 (or 1,0)
   assign oValid_reg_   = (state_!=state_empty);	// - output valid if non-empty
   assign iBackPressure_reg_   = addr_full_;		// - input bp if full
   assign oData = srlo;				// - output data from queue
   assign oValid = oValid_reg;			// - output valid if non-empty
   assign iBackPressure = iBackPressure_reg;			// - input bp if full

   assign count = (state==state_more ? addr+2 : (state==state_one ? 1 : 0));

   // - ''always'' block with both FFs and SRL16 does not work,
   //      since FFs need reset but SRL16 does not

   always @(posedge clock) begin	// - seq always: FFs
      if (reset) begin
	 state     <= state_empty;
	 addr      <= 0;
         addr_full <= 0;
	 oValid_reg   <= 0;
	 iBackPressure_reg   <= 1;
      end
      else begin
	 state     <= state_;
	 addr      <= addr_;
         addr_full <= addr_full_;
	 oValid_reg   <= oValid_reg_;
	 iBackPressure_reg   <= iBackPressure_reg_;
      end
   end // always @ (posedge clock)

   always @(posedge clock) begin	// - seq always: srlo
      // - infer enabled output reg at end of shift chain
      // - input first element from iData, all subsequent elements from SRL16
      if (reset) begin
	 srlo <= 0;
      end
      else begin
	 if (shift_en_o_) begin
	    srlo <= srlo_;
	 end
      end
   end // always @ (posedge clock)

   always @(posedge clock) begin			// - seq always: srl
      // - infer enabled SRL16E from shifting srl array
      // - no reset capability;  srl[] contents undefined on reset
      if (shift_en_) begin
	 // synthesis loop_limit 256
	 for (a_=depth-2; a_>0; a_=a_-1) begin
	    srl[a_] <= srl[a_-1];
	 end
	 srl[0] <= iData;
      end
   end // always @ (posedge clock or negedge reset)

   always @* begin					// - combi always
        srlo_       <=  'bx;
        shift_en_o_ <= 1'bx;
        shift_en_   <= 1'bx;
        addr_       <=  'bx;
        state_      <= 2'bx;
      case (state)

	state_empty: begin		    // - (empty, will not produce)
	      if (iValid) begin		    // - empty & iValid => consume
		 srlo_       <= iData;
		 shift_en_o_ <= 1;
		 shift_en_   <= 1'bx;
		 addr_       <= 0;
		 state_      <= state_one;
	      end
	      else	begin		    // - empty & !iValid => idle
		 srlo_       <= 'bx;
		 shift_en_o_ <= 0;
		 shift_en_   <= 1'bx;
		 addr_       <= 0;
		 state_      <= state_empty;
	      end
	end

	state_one: begin		    // - (contains one)
	      if (iValid && oBackPressure) begin	    // - one & iValid & oBackPressure => consume
		 srlo_       <= 'bx;
		 shift_en_o_ <= 0;
		 shift_en_   <= 1;
		 addr_       <= 0;
		 state_      <= state_more;
	      end
	      else if (iValid && !oBackPressure) begin   // - one & iValid & !oBackPressure => cons+prod
		 srlo_       <= iData;
		 shift_en_o_ <= 1;
		 shift_en_   <= 1;
		 addr_       <= 0;
		 state_      <= state_one;
	      end
	      else if (!iValid && oBackPressure) begin   // - one & !iValid & oBackPressure => idle
		 srlo_       <= 'bx;
		 shift_en_o_ <= 0;
		 shift_en_   <= 1'bx;
		 addr_       <= 0;
		 state_      <= state_one;
	      end
	      else if (!iValid && !oBackPressure) begin  // - one & !iValid & !oBackPressure => produce
		 srlo_       <= 'bx;
		 shift_en_o_ <= 0;
		 shift_en_   <= 1'bx;
		 addr_       <= 0;
		 state_      <= state_empty;
	      end
	end // case: state_one

	state_more: begin		    // - (contains more than one)
	   if (addr_full || (depth==2)) begin
					    // - (full, will not consume)
					    // - (full here if depth==2)
	      if (oBackPressure) begin		    // - full & oBackPressure => idle
		 srlo_       <= 'bx;
		 shift_en_o_ <= 0;
		 shift_en_   <= 0;
		 addr_       <= addr;
		 state_      <= state_more;
	      end
	      else begin		    // - full & !oBackPressure => produce
		 srlo_       <= srl[addr];
		 shift_en_o_ <= 1;
		 shift_en_   <= 0;
//		 addr_       <= addr-1;
//		 state_      <= state_more;
		 addr_       <= addr_zero_ ? 0         : addr-1;
		 state_      <= addr_zero_ ? state_one : state_more;
	      end
	   end
	   else begin			    // - (mid: neither empty nor full)
	      if (iValid && oBackPressure) begin	    // - mid & iValid & oBackPressure => consume
		 srlo_       <= 'bx;
		 shift_en_o_ <= 0;
		 shift_en_   <= 1;
		 addr_       <= addr+1;
		 state_      <= state_more;
	      end
	      else if (iValid && !oBackPressure) begin   // - mid & iValid & !oBackPressure => cons+prod
		 srlo_       <= srl[addr];
		 shift_en_o_ <= 1;
		 shift_en_   <= 1;
		 addr_       <= addr;
		 state_      <= state_more;
	      end
	      else if (!iValid && oBackPressure) begin   // - mid & !iValid & oBackPressure => idle
		 srlo_       <= 'bx;
		 shift_en_o_ <= 0;
		 shift_en_   <= 0;
		 addr_       <= addr;
		 state_      <= state_more;
	      end
	      else if (!iValid && !oBackPressure) begin  // - mid & !iValid & !oBackPressure => produce
		 srlo_       <= srl[addr];
		 shift_en_o_ <= 1;
		 shift_en_   <= 0;
		 addr_       <= addr_zero_ ? 0         : addr-1;
		 state_      <= addr_zero_ ? state_one : state_more;
	      end
	   end // else: !if(addr_full)
	end // case: state_more

	default: begin
		 srlo_       <=  'bx;
		 shift_en_o_ <= 1'bx;
		 shift_en_   <= 1'bx;
		 addr_       <=  'bx;
		 state_      <= 2'bx;
	end // case: default

      endcase // case(state)
   end // always @ *

endmodule // Q_srl


`endif  // `ifdef  Q_srl
