// the dual-port BRAM Verilog below is adapted from Dan Strother's example:
// http://danstrother.com/2010/09/11/inferring-rams-in-fpgas/

module DualPortBRAM #(
    parameter DATA = 72,
    parameter ADDR = 10
) (
    input   wire               clock,

    // Port A
    input   wire                a_req_writeEn,
    input   wire    [ADDR-1:0]  a_req_addr,
    input   wire    [DATA-1:0]  a_req_writeData,
    output  reg     [DATA-1:0]  a_rsp_readData,

    // Port B
    input   wire                b_req_writeEn,
    input   wire    [ADDR-1:0]  b_req_addr,
    input   wire    [DATA-1:0]  b_req_writeData,
    output  reg     [DATA-1:0]  b_rsp_readData
);

// Shared memory
reg [DATA-1:0] mem [(2**ADDR)-1:0];

// Port A
always @(posedge clock) begin
    a_rsp_readData      <= mem[a_req_addr];
    if(a_req_writeEn) begin
        a_rsp_readData      <= a_req_writeData;
        mem[a_req_addr] <= a_req_writeData;
    end
end

// Port B
always @(posedge clock) begin
    b_rsp_readData      <= mem[b_req_addr];
    if(b_req_writeEn) begin
        b_rsp_readData      <= b_req_writeData;
        mem[b_req_addr] <= b_req_writeData;
    end
end

endmodule
