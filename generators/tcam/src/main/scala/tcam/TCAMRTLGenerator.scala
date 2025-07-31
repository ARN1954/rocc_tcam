package tcam

import java.io.{File, FileWriter, PrintWriter}

// Enhanced RTL Generator for TCAM with table-specific configurations
class TCAMRTLGenerator(params: TCAMParams) {
  
  def generateRTL(): Unit = {
    val chipyardDir = System.getProperty("user.dir")
    val tcamDir = s"$chipyardDir/generators/tcam/src/main/resources/vsrc/tcam"
    
    // Create directory if it doesn't exist
    new File(tcamDir).mkdirs()
    
    // Get table configuration or use defaults
    val tableConfig = params.tableConfig.getOrElse(TCAMTableConfig(
      queryStrLen = params.keyWidth,
      subStrLen = params.keyWidth / 4,
      totalSubStr = 4,
      potMatchAddr = params.entries
    ))
    
    // Check if we're using the specific tcam_32x28 configuration
    val isTCAM32x28 = params.keyWidth == 28 && params.entries == 32 && 
                       params.tableConfig.exists(tc => tc.queryStrLen == 28 && tc.potMatchAddr == 32)
    
    // Check if we're using the tcam_64x28 configuration
    val isTCAM64x28 = params.keyWidth == 28 && params.entries == 64 && 
                       params.tableConfig.exists(tc => tc.queryStrLen == 28 && tc.potMatchAddr == 64)
    
    // Generate TCAMBlackBox.sv with configuration-specific logic
    generateTCAMBlackBox(tcamDir)
    
    // Generate SRAM model (always needed)
    generateSRAMModel(tcamDir)
    
    if (isTCAM64x28) {
      // For tcam_64x28: generate all supporting files
      generateTCAMBlock(tcamDir)
      generatePriorityEncoder(tcamDir)
      generateAndGate(tcamDir)
    }
    // For tcam_32x28: no additional files needed (everything is in TCAMBlackBox)
    
    println(s"Generated TCAM RTL in: $tcamDir")
  }
  
  private def generateTCAMBlackBox(outputDir: String): Unit = {
    val writer = new PrintWriter(new FileWriter(s"$outputDir/TCAMBlackBox.sv"))
    
    // Get table configuration or use defaults
    val tableConfig = params.tableConfig.getOrElse(TCAMTableConfig(
      queryStrLen = params.keyWidth,
      subStrLen = params.keyWidth / 4,
      totalSubStr = 4,
      potMatchAddr = params.entries
    ))
    
    // Check if we're using the specific tcam_32x28 configuration
    val isTCAM32x28 = params.keyWidth == 28 && params.entries == 32 && 
                       params.tableConfig.exists(tc => tc.queryStrLen == 28 && tc.potMatchAddr == 32)
    
    // Check if we're using the tcam_64x28 configuration
    val isTCAM64x28 = params.keyWidth == 28 && params.entries == 64 && 
                       params.tableConfig.exists(tc => tc.queryStrLen == 28 && tc.potMatchAddr == 64)
    
    if (isTCAM32x28) {
      // Generate the specific tcam_32x28 logic directly in TCAMBlackBox
      generateTCAM32x28Logic(writer)
    } else if (isTCAM64x28) {
      // Generate a wrapper that instantiates tcam7x64, priority encoder, and AND gate
      generateTCAM64x28Wrapper(writer, tableConfig)
    } else {
      // Only support specific configurations: tcam_32x28 and tcam_64x28
      throw new IllegalArgumentException(s"Unsupported TCAM configuration: keyWidth=${params.keyWidth}, entries=${params.entries}. Only tcam_32x28 and tcam_64x28 are supported.")
    }
  }
  
  private def generateTCAM32x28Logic(writer: PrintWriter): Unit = {
    writer.write("""`timescale 1ns/1ps

module TCAMBlackBox (
    input   logic           in_clk,
    input   logic           in_csb,
    input   logic           in_web,
    input   logic   [3:0]   in_wmask,
    input   logic   [27:0]  in_addr,
    input   logic   [31:0]  in_wdata,
    output  logic   [5:0]   out_pma
);

//////////////////////////////////////////////////////
// Read Address/Search Query bit division           //
//////////////////////////////////////////////////////

    logic   [7:0]   raddr_vtb1; // tcam virtual block 1
    logic   [6:0]   raddr_vtb2; // tcam virtual block 2
    logic   [7:0]   raddr_vtb3; // tcam virtual block 3
    logic   [6:0]   raddr_vtb4; // tcam virtual block 4
    logic           c_hi;

    always_comb begin : search_query_bits
        raddr_vtb1 = {1'b0, in_addr[27:21]};
        // raddr_vtb2 = in_addr[20:14];
        raddr_vtb3 = {1'b0, in_addr[13:7]};
        // raddr_vtb4 = in_addr[6:0];
        c_hi = 1'b1;
    end

//////////////////////////////////////////////////////
// write address bit division                       //
//////////////////////////////////////////////////////

    logic           aw_select;
    logic           we;
    logic   [7:0]   aw_sb1;     // sram block 1
    logic   [7:0]   aw_sb2;     // sram block 2
    logic   [7:0]   addr1;
    logic   [7:0]   addr2;

    always_comb begin : write_address_demux
        aw_select   = in_addr[8];
        we          = ~in_web;
        aw_sb1      = {8{we}} & {8{(~aw_select)}} & in_addr[7:0];
        aw_sb2      = {8{we}} & {8{aw_select}} & in_addr[7:0];
        addr1       = in_web ? raddr_vtb1 : aw_sb1;
        addr2       = in_web ? raddr_vtb3 : aw_sb2;
    end

//////////////////////////////////////////////////////
//	            write masking                       //
//////////////////////////////////////////////////////

    logic   [3:0]   wmask1;
    logic   [3:0]   wmask2;

    always_comb begin
        wmask1 = in_wmask & {4{~aw_select}};
        wmask2 = in_wmask & {4{aw_select}};
    end

//////////////////////////////////////////////////////
// Read/Write clock gating                          //
////////////////////////////////////////////////////// 

    logic vtb_clk;
    always_comb begin
        vtb_clk = (in_web) ? in_clk : 1'b0;
    end

//////////////////////////////////////////////////////
// Output logic                                     //
////////////////////////////////////////////////////// 

    logic   [31:0]  vtb_out1;
    logic   [31:0]  vtb_out2;
    logic   [31:0]  vtb_out3;
    logic   [31:0]  vtb_out4;
    logic   [31:0]  p_out;
    logic   [5:0]   rdata;
    logic   [31:0]  vtb_out1_and_vtb_out2;
    logic   [31:0]  vtb_out3_and_vtb_out4;

    always_comb begin
        vtb_out1_and_vtb_out2 = vtb_out1 & vtb_out2;
        vtb_out3_and_vtb_out4 = vtb_out3 & vtb_out4;
        p_out = vtb_out1_and_vtb_out2 & vtb_out3_and_vtb_out4;
    end

//////////////////////////////////////////////////////
// Priority Encoding                                //
////////////////////////////////////////////////////// 

    always @(p_out) begin
        if (p_out[0])
            rdata = 6'd1;
        else if (p_out[1])  rdata = 6'd2;
        else if (p_out[2])  rdata = 6'd3;
        else if (p_out[3])  rdata = 6'd4;
        else if (p_out[4])  rdata = 6'd5;
        else if (p_out[5])  rdata = 6'd6;
        else if (p_out[6])  rdata = 6'd7;
        else if (p_out[7])  rdata = 6'd8;
        else if (p_out[8])  rdata = 6'd9; 
        else if (p_out[9])  rdata = 6'd10; 
        else if (p_out[10]) rdata = 6'd11; 
        else if (p_out[11]) rdata = 6'd12; 
        else if (p_out[12]) rdata = 6'd13; 
        else if (p_out[13]) rdata = 6'd14; 
        else if (p_out[14]) rdata = 6'd15; 
        else if (p_out[15]) rdata = 6'd16; 
        else if (p_out[16]) rdata = 6'd17;
        else if (p_out[17]) rdata = 6'd18;
        else if (p_out[18]) rdata = 6'd19;
        else if (p_out[19]) rdata = 6'd20;
        else if (p_out[20]) rdata = 6'd21;
        else if (p_out[21]) rdata = 6'd22;
        else if (p_out[22]) rdata = 6'd23;
        else if (p_out[23]) rdata = 6'd24;
        else if (p_out[24]) rdata = 6'd25;
        else if (p_out[25]) rdata = 6'd26;
        else if (p_out[26]) rdata = 6'd27;
        else if (p_out[27]) rdata = 6'd28;
        else if (p_out[28]) rdata = 6'd29;
        else if (p_out[29]) rdata = 6'd30;
        else if (p_out[30]) rdata = 6'd31;
        else if (p_out[31]) rdata = 6'd32;
        else
            rdata = 6'd0;
    end

    always_comb begin
        out_pma = rdata;
    end

//////////////////////////////////////////////////////
// SRAM/Virtual TCAM blocks                         //
////////////////////////////////////////////////////// 

    sky130_sram_1kbyte_1rw1r_32x256_8 vtb_sb1(
        `ifdef USE_POWER_PINS
        .vccd1  (),
        .vssd1  (),
        `endif
        // Port 0: RW
        .clk0   (in_clk),
        .csb0   (in_csb),
        .web0   (in_web),
        .wmask0 (wmask1),
        .addr0  (addr1),
        .din0   (in_wdata),
        .dout0  (vtb_out1),
        // Port 1: R
        .clk1   (vtb_clk),
        .csb1   (in_csb),
        .addr1  ({c_hi,in_addr[20:14]}),
        .dout1  (vtb_out2)
    );

    sky130_sram_1kbyte_1rw1r_32x256_8 vtb_sb2(
        `ifdef USE_POWER_PINS
        .vccd1	(),
        .vssd1	(),
        `endif
        // Port 0: RW
        .clk0	(in_clk),
        .csb0	(in_csb),
        .web0	(in_web),
        .wmask0	(wmask2),
        .addr0	(addr2),
        .din0	(in_wdata),
        .dout0	(vtb_out3),
        // Port 1: R
        .clk1	(vtb_clk),
        .csb1	(in_csb),
        .addr1	({c_hi,in_addr[6:0]}),
        .dout1	(vtb_out4)
    );

endmodule
""")
    writer.close()
  }
  

  
  private def generateSRAMModel(outputDir: String): Unit = {
    val writer = new PrintWriter(new FileWriter(s"$outputDir/sky130_sram_1kbyte_1rw1r_32x256_8.sv"))
    
    try {
      writer.write("""// OpenRAM SRAM model
// Words: 256
// Word size: 32
// Write size: 8

parameter NUM_WMASKS = 4;
parameter DATA_WIDTH = 32;
parameter ADDR_WIDTH = 8;
parameter RAM_DEPTH	 = 1 << ADDR_WIDTH;
// FIXME: This delay is arbitrary.
parameter DELAY		= 3;
parameter VERBOSE	= 1;	// Set to 0 to only display warnings
parameter T_HOLD	= 1;	// Delay to hold dout value after posedge. Value is arbitrary

module sky130_sram_1kbyte_1rw1r_32x256_8 (
	`ifdef USE_POWER_PINS
	inout vccd1,
	inout vssd1,
	`endif
	// Port 0: RW
	input	logic						clk0,		// clock
	input	logic						csb0,		// active low chip select
	input	logic						web0,		// active low write control
	input	logic	[NUM_WMASKS-1:0]	wmask0,		// write mask
	input	logic	[ADDR_WIDTH-1:0]	addr0,
	input	logic	[DATA_WIDTH-1:0]	din0,
	output	logic	[DATA_WIDTH-1:0]	dout0,
	// Port 1: R
	input	logic						clk1,		// clock
	input	logic						csb1,		// active low chip select
	input	logic	[ADDR_WIDTH-1:0]	addr1,
	output	logic	[DATA_WIDTH-1:0]	dout1
);

	reg						csb0_reg;
	reg						web0_reg;
	reg	[NUM_WMASKS-1:0]	wmask0_reg;
	reg	[ADDR_WIDTH-1:0]	addr0_reg;
	reg	[DATA_WIDTH-1:0]	din0_reg;

	// * All inputs are registers
	always @(posedge clk0) begin
		csb0_reg	= csb0;
		web0_reg	= web0;
		wmask0_reg	= wmask0;
		addr0_reg	= addr0;
		din0_reg	= din0;
		#(T_HOLD) dout0 = 32'bx;
		if (!csb0_reg && web0_reg && VERBOSE) 
			$display($time, "Reading %m addr0= %b dout0= %b", addr0_reg, mem[addr0_reg]);
		if (!csb0_reg && !web0_reg && VERBOSE)
			$display($time, "Writing %m addr0= %b din0= %b wmask0= %b", addr0_reg, din0_reg, wmask0_reg);
	end

	reg						csb1_reg;
	reg	[ADDR_WIDTH-1:0]	addr1_reg;

	// * All inputs are registers
	always @(posedge clk1) begin
		csb1_reg = csb1;
		addr1_reg = addr1;
		if (!csb0 && !web0 && !csb1 && (addr0 == addr1))
			$display($time, "WARNING: Writing and reading addr0= %b and addr1= %b simultaneously!", addr0, addr1);
		#(T_HOLD) dout1 = 32'bx;
		if (!csb1_reg && VERBOSE) 
			$display($time," Reading %m addr1=%b dout1=%b",addr1_reg,mem[addr1_reg]);
	end

	reg [DATA_WIDTH-1:0] mem [0:RAM_DEPTH-1];

	// * Memory Write Block Port 0
	// Write Operation : When web0 = 0, csb0 = 0
	always @ (negedge clk0) begin : MEM_WRITE0
		if ( !csb0_reg && !web0_reg ) begin
			if (wmask0_reg[0])
				mem[addr0_reg][7:0]		= din0_reg[7:0];
			if (wmask0_reg[1])
				mem[addr0_reg][15:8]	= din0_reg[15:8];
			if (wmask0_reg[2])
				mem[addr0_reg][23:16]	= din0_reg[23:16];
			if (wmask0_reg[3])
				mem[addr0_reg][31:24]	= din0_reg[31:24];
		end
	end

	// * Memory Read Block Port 0
	// Read Operation : When web0 = 1, csb0 = 0
	always @ (negedge clk0) begin : MEM_READ0
		if (!csb0_reg && web0_reg)
			dout0 <= #(DELAY) mem[addr0_reg];
	end

	// * Memory Read Block Port 1
	// Read Operation : When web1 = 1, csb1 = 0
	always @ (negedge clk1) begin : MEM_READ1
		if (!csb1_reg)
			dout1 <= #(DELAY) mem[addr1_reg];
	end

endmodule
""")
    } finally {
      writer.close()
    }
  }
  
  private def generateTCAMBlock(outputDir: String): Unit = {
    val tableConfig = params.tableConfig.getOrElse(TCAMTableConfig(
      queryStrLen = params.keyWidth,
      subStrLen = params.keyWidth / 4,
      totalSubStr = 4,
      potMatchAddr = params.entries
    ))
    
    val writer = new PrintWriter(new FileWriter(s"$outputDir/tcam${tableConfig.subStrLen}x${tableConfig.potMatchAddr}.sv"))
    
    try {
      writer.write(s"""`timescale 1ns/1ps
module tcam${tableConfig.subStrLen}x${tableConfig.potMatchAddr} (
    input   logic           in_clk,
    input   logic           in_csb,
    input   logic           in_web,
    input   logic   [3:0]   in_wmask,
    input   logic   [7:0]   in_addr,
    input   logic   [31:0]  in_wdata,
    output  logic   [${tableConfig.potMatchAddr-1}:0]  out_rdata
);

    // * ------------------------------ Write address
    logic [7:0] aw_addr;

    assign aw_addr = {8{~in_web}} & in_addr;

    // * ------------------------------ Read/Search address
    logic [7:0] ar_addr1;
    logic [7:0] ar_addr2;

    // always read/search lower 128 rows
    assign ar_addr1 = {1'b0, in_addr[6:0]};
    // always read/search upper 128 rows
    assign ar_addr2 = {1'b1, in_addr[6:0]} & {8{in_web}};

    // * ------------------------------ PMA
    logic [31:0] rdata_lower;
    logic [31:0] rdata_upper;
    logic [${tableConfig.potMatchAddr-1}:0] rdata;

    assign rdata = {rdata_upper, rdata_lower};
    assign out_rdata = rdata;    

    sky130_sram_1kbyte_1rw1r_32x256_8 dut_vtb(
        // Port 0: RW
        .clk0       (in_clk),
        .csb0       (in_csb),
        .web0       (in_web),
        .wmask0     (in_wmask),
        .addr0      ((in_web ? ar_addr1: aw_addr)),
        .din0       (in_wdata),
        .dout0      (rdata_lower),
        // Port 1: R
        .clk1       (in_clk),
        .csb1       (in_csb),
        .addr1      (ar_addr2),
        .dout1      (rdata_upper)
    );

endmodule
""")
    } finally {
      writer.close()
    }
  }
  
  private def generatePriorityEncoder(outputDir: String): Unit = {
    val tableConfig = params.tableConfig.getOrElse(TCAMTableConfig(
      queryStrLen = params.keyWidth,
      subStrLen = params.keyWidth / 4,
      totalSubStr = 4,
      potMatchAddr = params.entries
    ))
    
    val encoderWidth = log2Ceil(tableConfig.potMatchAddr)
    val dataWidth = tableConfig.potMatchAddr
    
    val writer = new PrintWriter(new FileWriter(s"$outputDir/priority_encoder_${dataWidth}x${encoderWidth}.sv"))
    
    try {
      writer.write(s"""`timescale 1ns/1ps
module priority_encoder_${dataWidth}x${encoderWidth} (
    input   logic [${dataWidth-1}:0]    in_data,
    output  logic [${encoderWidth-1}:0]     out_data
);    

    always @(*) begin
""")
      
      // Generate proper else-if chain for priority encoder (highest bit has highest priority)
      for (i <- 0 until dataWidth) {
        val bitIndex = dataWidth - 1 - i  // Start from MSB (highest priority)
        if (i == 0) {
          writer.write(s"""        if(in_data[${bitIndex}] == 1)        out_data=${encoderWidth}'d${bitIndex};
""")
        } else {
          writer.write(s"""        else if(in_data[${bitIndex}] == 1)   out_data=${encoderWidth}'d${bitIndex};
""")
        }
      }
      
      writer.write(s"""        else
            out_data=${encoderWidth}'d0;
    end

endmodule
""")
    } finally {
      writer.close()
    }
  }
  
  private def generateAndGate(outputDir: String): Unit = {
    val tableConfig = params.tableConfig.getOrElse(TCAMTableConfig(
      queryStrLen = params.keyWidth,
      subStrLen = params.keyWidth / 4,
      totalSubStr = 4,
      potMatchAddr = params.entries
    ))
    
    val writer = new PrintWriter(new FileWriter(s"$outputDir/and_gate.sv"))
    
    try {
      writer.write(s"""`timescale 1ns/1ps
module and_gate (
    input   logic   [${tableConfig.potMatchAddr-1}:0]  in_dataA,
    input   logic   [${tableConfig.potMatchAddr-1}:0]  in_dataB,
    output  logic   [${tableConfig.potMatchAddr-1}:0]  out_data
);

    assign out_data = in_dataA & in_dataB;

endmodule
""")
    } finally {
      writer.close()
    }
  }

  private def generateTCAM64x28Wrapper(writer: PrintWriter, tableConfig: TCAMTableConfig): Unit = {
    writer.write("""`timescale 1ns/1ps

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
        .in_clk      (in_clk),
        .in_csb      (in_csb),
        .in_web      (in_web),
        .in_wmask    (in_wmask),
        .in_addr     (vtb_addr0),
        .in_wdata    (in_wdata),
        .out_rdata   (out_rdata0)
    );
    tcam7x64 tcam7x64_dut1 (
        .in_clk      (in_clk),
        .in_csb      (in_csb),
        .in_web      (in_web),
        .in_wmask    (in_wmask),
        .in_addr     (vtb_addr1),
        .in_wdata    (in_wdata),
        .out_rdata   (out_rdata1)
    );
    tcam7x64 tcam7x64_dut2 (
        .in_clk      (in_clk),
        .in_csb      (in_csb),
        .in_web      (in_web),
        .in_wmask    (in_wmask),
        .in_addr     (vtb_addr2),
        .in_wdata    (in_wdata),
        .out_rdata   (out_rdata2)
    );
    tcam7x64 tcam7x64_dut3 (
        .in_clk      (in_clk),
        .in_csb      (in_csb),
        .in_web      (in_web),
        .in_wmask    (in_wmask),
        .in_addr     (vtb_addr3),
        .in_wdata    (in_wdata),
        .out_rdata   (out_rdata3)
    );

    // AND gate instantiations
    wire	[63:0]	out_gate0;
    wire	[63:0]	out_gate1;    
    wire	[63:0]	out_andgate;
    and_gate and_gate_dut0 (.out_data(out_gate0), .in_dataA(out_rdata0), .in_dataB(out_rdata1));
    and_gate and_gate_dut1 (.out_data(out_gate1), .in_dataA(out_gate0), .in_dataB(out_rdata2));
    and_gate and_gate_dut2 (.out_data(out_andgate), .in_dataA(out_gate1), .in_dataB(out_rdata3));

    // Priority Encoder instantiations
    priority_encoder_64x6 priority_encoder_dut0(
        .in_data  (out_andgate),
        .out_data (out_pma)
    );

endmodule
""")
    writer.close()
  }
  
  private def log2Ceil(x: Int): Int = {
    if (x <= 1) 1 else 32 - Integer.numberOfLeadingZeros(x - 1)
  }
} 