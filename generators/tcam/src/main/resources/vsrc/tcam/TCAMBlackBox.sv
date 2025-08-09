`timescale 1ns/1ps

module TCAMBlackBox (
    input	logic	in_clk,
    input	logic	in_csb,
    input	logic	in_web,
    input	logic	[3:0]	in_wmask,
    input	logic	[27:0]	in_addr,
    input	logic	[31:0]	in_wdata,
    output	logic	[5:0]	out_pma
);

    // memory block selection for write logic
    wire	[3:0]	block_sel;
    assign block_sel[0] = (in_addr[9:8] == 2'd0);
    assign block_sel[1] = (in_addr[9:8] == 2'd1);
    assign block_sel[2] = (in_addr[9:8] == 2'd2);
    assign block_sel[3] = (in_addr[9:8] == 2'd3);

    // logic for write mask
    wire	[3:0]	wmask0;
    wire	[3:0]	wmask1;
    wire	[3:0]	wmask2;
    wire	[3:0]	wmask3;
    assign wmask0 = { 4{block_sel[0]} } & in_wmask;
    assign wmask1 = { 4{block_sel[1]} } & in_wmask;
    assign wmask2 = { 4{block_sel[2]} } & in_wmask;
    assign wmask3 = { 4{block_sel[3]} } & in_wmask;

    // logic for write addresses
    wire	[7:0]	aw_addr0;
    wire	[7:0]	aw_addr1;
    wire	[7:0]	aw_addr2;
    wire	[7:0]	aw_addr3;
    assign aw_addr0 = { 8{block_sel[0]} } & in_addr[7:0];
    assign aw_addr1 = { 8{block_sel[1]} } & in_addr[7:0];
    assign aw_addr2 = { 8{block_sel[2]} } & in_addr[7:0];
    assign aw_addr3 = { 8{block_sel[3]} } & in_addr[7:0];

    // address mux for all N blocks (selects between read or write addresses)
    wire	[7:0]	vtb_addr0;
    wire	[7:0]	vtb_addr1;
    wire	[7:0]	vtb_addr2;
    wire	[7:0]	vtb_addr3;
    assign vtb_addr0 = in_web ? { 1'b0, in_addr[  6:  0] } : aw_addr0;
    assign vtb_addr1 = in_web ? { 1'b0, in_addr[ 13:  7] } : aw_addr1;
    assign vtb_addr2 = in_web ? { 1'b0, in_addr[ 20: 14] } : aw_addr2;
    assign vtb_addr3 = in_web ? { 1'b0, in_addr[ 27: 21] } : aw_addr3;

    // TCAM memory block instances
    wire	[63:0]	out_rdata0;
    wire	[63:0]	out_rdata1;
    wire	[63:0]	out_rdata2;
    wire	[63:0]	out_rdata3;

    tcam7x64 tcam7x64_dut0 (
        .in_clk      (      in_clk),
        .in_csb      (      in_csb),
        .in_web      (      in_web),
        .in_wmask    (    in_wmask),
        .in_addr     (   vtb_addr0),
        .in_wdata    (    in_wdata),
        .out_rdata   (   out_rdata0)
    );
    tcam7x64 tcam7x64_dut1 (
        .in_clk      (      in_clk),
        .in_csb      (      in_csb),
        .in_web      (      in_web),
        .in_wmask    (    in_wmask),
        .in_addr     (   vtb_addr1),
        .in_wdata    (    in_wdata),
        .out_rdata   (   out_rdata1)
    );
    tcam7x64 tcam7x64_dut2 (
        .in_clk      (      in_clk),
        .in_csb      (      in_csb),
        .in_web      (      in_web),
        .in_wmask    (    in_wmask),
        .in_addr     (   vtb_addr2),
        .in_wdata    (    in_wdata),
        .out_rdata   (   out_rdata2)
    );
    tcam7x64 tcam7x64_dut3 (
        .in_clk      (      in_clk),
        .in_csb      (      in_csb),
        .in_web      (      in_web),
        .in_wmask    (    in_wmask),
        .in_addr     (   vtb_addr3),
        .in_wdata    (    in_wdata),
        .out_rdata   (   out_rdata3)
    );

    // AND gate instantiations
    wire	[63:0]	out_andgate0;
    wire	[63:0]	out_andgate1;
    wire	[63:0]	out_andgate2;
    wire	[63:0]	out_andgate;
    and_gate andgate_dut0 (.out_data(out_andgate0), .in_dataA(out_rdata0), .in_dataB(out_rdata1));
    and_gate andgate_dut1 (.out_data(out_andgate1), .in_dataA(out_andgate0), .in_dataB(out_rdata2));
    and_gate andgate_dut2 (.out_data(out_andgate), .in_dataA(out_andgate1), .in_dataB(out_rdata3));

    // Priority Encoder instantiations
    priority_encoder_64x6 priority_encoder_dut0(
        .in_data  (out_andgate  ),
        .out_data (out_pma      )
    );

    // Debug code to monitor address mapping
    always @(posedge in_clk) begin
        if (!in_csb) begin  // Only print when chip is selected
            $display("=== TCAM Address Debug ===");
            $display("Input address: %h", in_addr);
            $display("in_web: %b", in_web);
            $display("Block 0 search addr = %b (%h)", in_addr[6:0], in_addr[6:0]);
            $display("Block 1 search addr = %b (%h)", in_addr[13:7], in_addr[13:7]);
            $display("Block 2 search addr = %b (%h)", in_addr[20:14], in_addr[20:14]);
            $display("Block 3 search addr = %b (%h)", in_addr[27:21], in_addr[27:21]);
            $display("vtb_addr0 = %h", vtb_addr0);
            $display("vtb_addr1 = %h", vtb_addr1);
            $display("vtb_addr2 = %h", vtb_addr2);
            $display("vtb_addr3 = %h", vtb_addr3);
            // TCAM block read data
            $display("out_rdata0 = %064b", out_rdata0);
            $display("out_rdata1 = %064b", out_rdata1);
            $display("out_rdata2 = %064b", out_rdata2);
            $display("out_rdata3 = %064b", out_rdata3);
            // AND gate stage details
            $display("AND0 inA=out_rdata0=%064b", out_rdata0);
            $display("AND0 inB=out_rdata1=%064b", out_rdata1);
            $display("AND0 out = out_andgate0=%064b", out_andgate0);
            $display("AND1 inA=out_andgate0=%064b", out_andgate0);
            $display("AND1 inB=out_rdata2  =%064b", out_rdata2);
            $display("AND1 out = out_andgate1=%064b", out_andgate1);
            $display("AND2 inA=out_andgate1=%064b", out_andgate1);
            $display("AND2 inB=out_rdata3  =%064b", out_rdata3);
            $display("AND2 out = out_andgate =%064b", out_andgate);
            // Priority encoder input/output
            $display("priority_in (to encoder) = %064b", out_andgate);
            $display("out_pma = %h", out_pma);
            $display("=========================");
        end
    end

    // One-cycle delayed status print to follow SRAM dout prints
    logic        print_pending;
    logic [5:0]  out_pma_q;
    always @(posedge in_clk) begin
        if (in_csb) begin
            print_pending <= 1'b0;
        end else begin
            if (in_web && !print_pending) begin
                out_pma_q <= out_pma;
                print_pending <= 1'b1;
            end else if (print_pending) begin
                $display("TCAM match status: 0x%08h", {26'd0, out_pma_q});
                print_pending <= 1'b0;
            end
        end
    end

endmodule

