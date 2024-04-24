////////////////////////////////////////////////////////////////////////////////
//
// Filename: 	demoaxi.v
// {{{
// Project:	WB2AXIPSP: bus bridges and other odds and ends
//
// Purpose:	Demonstrate an AXI-lite bus design.  The goal of this design
//		is to support a completely pipelined AXI-lite transaction
//	which can transfer one data item per clock.
//
//	Note that the AXI spec requires that there be no combinatorial
//	logic between input ports and output ports.  Hence all of the *valid
//	and *ready signals produced here are registered.  This forces us into
//	the buffered handshake strategy.
//
//	Some curious variable meanings below:
//
//	!axi_arvalid is synonymous with having a request, but stalling because
//		of a current request sitting in axi_rvalid with !axi_rready
//	!axi_awvalid is also synonymous with having an axi address being
//		received, but either the axi_bvalid && !axi_bready, or
//		no write data has been received
//	!axi_wvalid is similar to axi_awvalid.
//
// Creator:	Dan Gisselquist, Ph.D.
//		Gisselquist Technology, LLC
//
////////////////////////////////////////////////////////////////////////////////
// }}}
// Copyright (C) 2018-2022, Gisselquist Technology, LLC
// {{{
// This file is part of the WB2AXIP project.
//
// The WB2AXIP project contains free software and gateware, licensed under the
// Apache License, Version 2.0 (the "License").  You may not use this project,
// or this file, except in compliance with the License.  You may obtain a copy
// of the License at
//
//	http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
// License for the specific language governing permissions and limitations
// under the License.
//
////////////////////////////////////////////////////////////////////////////////
//
//
`default_nettype wire
//
`timescale 1 ns / 1 ps
// }}}
module	axi4e1000 #(
		// {{{
		// Users to add parameters here
		parameter [0:0] OPT_READ_SIDEEFFECTS = 1,
		// User parameters ends
		// Do not modify the parameters beyond this line
		// Width of S_AXI data bus
		parameter integer C_S_AXI_DATA_WIDTH	= 32,
		// Width of S_AXI address bus
		parameter integer C_S_AXI_ADDR_WIDTH	= 32
		// }}}
	) (
		// {{{
		// Users to add ports here
		output wire has_int,
		// User ports ends

		// Do not modify the ports beyond this line
		// Global Clock Signal
		input wire  S_AXI_ACLK,
		// Global Reset Signal. This Signal is Active LOW
		input wire  S_AXI_ARESETN,
		// Write address (issued by master, acceped by Slave)
		input wire [C_S_AXI_ADDR_WIDTH-1 : 0] S_AXI_AWADDR,
		// Write channel Protection type. This signal indicates the
    		// privilege and security level of the transaction, and whether
    		// the transaction is a data access or an instruction access.
		input wire [2 : 0] S_AXI_AWPROT,
		// Write address valid. This signal indicates that the master
		// signaling valid write address and control information.
		input wire  S_AXI_AWVALID,
		// Write address ready. This signal indicates that the slave
		// is ready to accept an address and associated control signals.
		output wire  S_AXI_AWREADY,
		// Write data (issued by master, acceped by Slave)
		input wire [C_S_AXI_DATA_WIDTH-1 : 0] S_AXI_WDATA,
		// Write strobes. This signal indicates which byte lanes hold
    		// valid data. There is one write strobe bit for each eight
    		// bits of the write data bus.
		input wire [(C_S_AXI_DATA_WIDTH/8)-1 : 0] S_AXI_WSTRB,
		// Write valid. This signal indicates that valid write
    		// data and strobes are available.
		input wire  S_AXI_WVALID,
		// Write ready. This signal indicates that the slave
    		// can accept the write data.
		output wire  S_AXI_WREADY,
		// Write response. This signal indicates the status
    		// of the write transaction.
		output wire [1 : 0] S_AXI_BRESP,
		// Write response valid. This signal indicates that the channel
    		// is signaling a valid write response.
		output wire  S_AXI_BVALID,
		// Response ready. This signal indicates that the master
    		// can accept a write response.
		input wire  S_AXI_BREADY,
		// Read address (issued by master, acceped by Slave)
		input wire [C_S_AXI_ADDR_WIDTH-1 : 0] S_AXI_ARADDR,
		// Protection type. This signal indicates the privilege
    		// and security level of the transaction, and whether the
    		// transaction is a data access or an instruction access.
		input wire [2 : 0] S_AXI_ARPROT,
		// Read address valid. This signal indicates that the channel
    		// is signaling valid read address and control information.
		input wire  S_AXI_ARVALID,
		// Read address ready. This signal indicates that the slave is
    		// ready to accept an address and associated control signals.
		output wire  S_AXI_ARREADY,
		// Read data (issued by slave)
		output wire [C_S_AXI_DATA_WIDTH-1 : 0] S_AXI_RDATA,
		// Read response. This signal indicates the status of the
    		// read transfer.
		output wire [1 : 0] S_AXI_RRESP,
		// Read valid. This signal indicates that the channel is
    		// signaling the required read data.
		output wire  S_AXI_RVALID,
		// Read ready. This signal indicates that the master can
    		// accept the read data and response information.
		input wire  S_AXI_RREADY
		// }}}
	);

	// Local declarations
	// {{{
	// AXI4LITE signals
	reg		axi_awready;
	reg		axi_wready;
	reg		axi_bvalid;
	reg		axi_arready;
	reg [C_S_AXI_DATA_WIDTH-1 : 0] 	axi_rdata;
	reg		axi_rvalid;

	// Example-specific design signals
	// local parameter for addressing 32 bit / 64 bit C_S_AXI_DATA_WIDTH
	// ADDR_LSB is used for addressing 32/64 bit registers/memories
	// ADDR_LSB = 2 for 32 bits (n downto 2)
	// ADDR_LSB = 3 for 64 bits (n downto 3)
	localparam integer ADDR_LSB = 2;
	localparam integer AW = 13;
	localparam integer DW = C_S_AXI_DATA_WIDTH;
	//----------------------------------------------
	//-- Signals for user logic register space example
	//------------------------------------------------
	wire [DW-1:0]	e1000_regs_rdata;

	// I/O Connections assignments

	assign S_AXI_AWREADY	= axi_awready;
	assign S_AXI_WREADY	= axi_wready;
	assign S_AXI_BRESP	= 2'b00; // The OKAY response
	assign S_AXI_BVALID	= axi_bvalid;
	assign S_AXI_ARREADY	= axi_arready;
	assign S_AXI_RDATA	= axi_rdata;
	assign S_AXI_RRESP	= 2'b00; // The OKAY response
	assign S_AXI_RVALID	= axi_rvalid;
	// Implement axi_*wready generation
	// }}}
	//////////////////////////////////////
	//
	// Read processing
	//
	//
	wire	valid_read_request,
		read_response_stall;

	assign	valid_read_request  =  S_AXI_ARVALID || !S_AXI_ARREADY;
	assign	read_response_stall =  S_AXI_RVALID  && !S_AXI_RREADY;

	//
	// The read response channel valid signal
	//
	initial	axi_rvalid = 1'b0;
	always @(posedge S_AXI_ACLK )
	if (!S_AXI_ARESETN)
		axi_rvalid <= 0;
	else if (read_response_stall)
		// Need to stay valid as long as the return path is stalled
		axi_rvalid <= 1'b1;
	else if (valid_read_request)
		axi_rvalid <= 1'b1;
	else
		// Any stall has cleared, so we can always
		// clear the valid signal in this case
		axi_rvalid <= 1'b0;

	reg [C_S_AXI_ADDR_WIDTH-1 : 0] 	pre_raddr, rd_addr;

	// Buffer the address
	always @(posedge S_AXI_ACLK)
	if (S_AXI_ARREADY)
		pre_raddr <= S_AXI_ARADDR;

	always @(*)
	if (!axi_arready)
		rd_addr = pre_raddr;
	else
		rd_addr = S_AXI_ARADDR;

	//
	// Read the data
	//
	always @(posedge S_AXI_ACLK)
	if (!read_response_stall
		&&(!OPT_READ_SIDEEFFECTS || valid_read_request))
		// If the outgoing channel is not stalled (above)
		// then read
		axi_rdata <= e1000_regs_rdata;

	//
	// The read address channel ready signal
	//
	initial	axi_arready = 1'b0;
	always @(posedge S_AXI_ACLK)
	if (!S_AXI_ARESETN)
		axi_arready <= 1'b1;
	else if (read_response_stall)
	begin
		// Outgoing channel is stalled
		//    As long as something is already in the buffer,
		//    axi_arready needs to stay low
		axi_arready <= !valid_read_request;
	end else
		axi_arready <= 1'b1;

	//////////////////////////////////////
	//
	// Write processing
	//
	//
	reg [C_S_AXI_ADDR_WIDTH-1 : 0]		pre_waddr, waddr;
	reg [C_S_AXI_DATA_WIDTH-1 : 0]		pre_wdata, wdata;
	reg [(C_S_AXI_DATA_WIDTH/8)-1 : 0]	pre_wstrb, wstrb;

	wire	valid_write_address, valid_write_data,
		write_response_stall;

	assign	valid_write_address = S_AXI_AWVALID || !axi_awready;
	assign	valid_write_data    = S_AXI_WVALID  || !axi_wready;
	assign	write_response_stall= S_AXI_BVALID  && !S_AXI_BREADY;

	//
	// The write address channel ready signal
	//
	initial	axi_awready = 1'b1;
	always @(posedge S_AXI_ACLK)
	if (!S_AXI_ARESETN)
		axi_awready <= 1'b1;
	else if (write_response_stall)
	begin
		// The output channel is stalled
		//	If our buffer is full, we need to remain stalled
		//	Likewise if it is empty, and there's a request,
		//	  we'll need to stall.
		axi_awready <= !valid_write_address;
	end else if (valid_write_data)
		// The output channel is clear, and write data
		// are available
		axi_awready <= 1'b1;
	else
		// If we were ready before, then remain ready unless an
		// address unaccompanied by data shows up
		axi_awready <= ((axi_awready)&&(!S_AXI_AWVALID));
		// This is equivalent to
		// axi_awready <= !valid_write_address

	//
	// The write data channel ready signal
	//
	initial	axi_wready = 1'b1;
	always @(posedge S_AXI_ACLK)
	if (!S_AXI_ARESETN)
		axi_wready <= 1'b1;
	else if (write_response_stall)
		// The output channel is stalled
		//	We can remain ready until valid
		//	write data shows up
		axi_wready <= !valid_write_data;
	else if (valid_write_address)
		// The output channel is clear, and a write address
		// is available
		axi_wready <= 1'b1;
	else
		// if we were ready before, and there's no new data avaialble
		// to cause us to stall, remain ready
		axi_wready <= (axi_wready)&&(!S_AXI_WVALID);
		// This is equivalent to
		// axi_wready <= !valid_write_data


	// Buffer the address
	always @(posedge S_AXI_ACLK)
	if (S_AXI_AWREADY)
		pre_waddr <= S_AXI_AWADDR;

	// Buffer the data
	always @(posedge S_AXI_ACLK)
	if (S_AXI_WREADY)
	begin
		pre_wdata <= S_AXI_WDATA;
		pre_wstrb <= S_AXI_WSTRB;
	end

	always @(*)
	if (!axi_awready)
		// Read the write address from our "buffer"
		waddr = pre_waddr;
	else
		waddr = S_AXI_AWADDR;

	always @(*)
	if (!axi_wready)
	begin
		// Read the write data from our "buffer"
		wstrb = pre_wstrb;
		wdata = pre_wdata;
	end else begin
		wstrb = S_AXI_WSTRB;
		wdata = S_AXI_WDATA;
	end

	// Actual read & write module
	e1000_regs #(
		.E_ADDR_WIDTH(AW),
		.E_DATA_WIDTH(DW)
	) e1000_regs_u (
		.CLK(S_AXI_ACLK),
		.RESET(!S_AXI_ARESETN),
		.WADDR(waddr[AW+ADDR_LSB-1:ADDR_LSB]),
		.WDATA(wdata),
		.WSTRB(wstrb),
	// If the output channel isn't stalled, and
		// If we have a valid address, and
		// If we have valid data
		.WEN(!write_response_stall && valid_write_address && valid_write_data),

		.RADDR(rd_addr[AW+ADDR_LSB-1:ADDR_LSB]),
		.RDATA(e1000_regs_rdata),
		.REN(!read_response_stall &&(!OPT_READ_SIDEEFFECTS || valid_read_request)),

		.HAS_INT(has_int)
	);

	//
	// The write response channel valid signal
	//
	initial	axi_bvalid = 1'b0;
	always @(posedge S_AXI_ACLK )
	if (!S_AXI_ARESETN)
		axi_bvalid <= 1'b0;
	//
	// The outgoing response channel should indicate a valid write if ...
		// 1. We have a valid address, and
	else if (valid_write_address
			// 2. We had valid data
			&& valid_write_data)
		// It doesn't matter here if we are stalled or not
		// We can keep setting ready as often as we want
		axi_bvalid <= 1'b1;
	else if (S_AXI_BREADY)
		// Otherwise, if BREADY was true, then it was just accepted
		// and can return to idle now
		axi_bvalid <= 1'b0;

endmodule

module e1000_regs #(
	parameter integer E_ADDR_WIDTH = 13,
	parameter integer E_DATA_WIDTH = 32
) (
	input wire CLK,
	input wire RESET,
	input wire [E_ADDR_WIDTH-1 : 0] WADDR,
	input wire [E_DATA_WIDTH-1 : 0] WDATA,
	input wire [(E_DATA_WIDTH/8)-1 : 0] WSTRB,
	input wire WEN,
	input wire [E_ADDR_WIDTH-1 : 0] RADDR,
	output wire [E_DATA_WIDTH-1 : 0] RDATA,
	input wire REN,
	output wire HAS_INT
);
	localparam integer DW = E_DATA_WIDTH;

	reg [DW-1:0]	icr_reg;
	// ICS uses ICR register
	reg [DW-1:0]	ims_reg;
	// IMC uses IMS register
	reg [  26:0]	rctl_reg;	// highest 5 bits are 0
	reg [DW-1:4]	rdbal_reg;	// RDBAL, TDBAL: lowest 4 bits are 0
	reg [DW-1:0]	rdbah_reg;
	reg [  19:7]	rdlen_reg;	// RDLEN, TDLEN: highest 12 bits and lowest 7 bits reads as 0
	reg [  15:0]	rdh_reg;	// RDH, RDT, TDH, TDT: highest 16 bits reads as 0
	reg [  15:0]	rdt_reg;
	reg [DW-1:0]	ral0_reg;
	reg [DW-1:0]	rah0_reg;
	reg [  25:0]	tctl_reg;	// highest 6 bits are 0
	reg [DW-1:4]	tdbal_reg;
	reg [DW-1:0]	tdbah_reg;
	reg [  19:7]	tdlen_reg;
	reg [  15:0]	tdh_reg;
	reg [  15:0]	tdt_reg;

	// Selection logic
	wire icr_r, ims_r,
		rctl_r, rdbal_r, rdbah_r, rdlen_r, rdh_r, rdt_r, ral0_r, rah0_r,
		tctl_r, tdbal_r, tdbah_r, tdlen_r, tdh_r, tdt_r;
	wire icr_w, ics_w, ims_w, imc_w,
		rctl_w, rdbal_w, rdbah_w, rdlen_w, rdh_w, rdt_w, ral0_w, rah0_w,
		tctl_w, tdbal_w, tdbah_w, tdlen_w, tdh_w, tdt_w;
	
	assign icr_r	= (RADDR == 13'b000_0000_1100_00);
	// ICS write only
	assign ims_r	= (RADDR == 13'b000_0000_1101_00);
	// IMC write only
	assign rctl_r	= (RADDR == 13'b000_0001_0000_00);
	assign rdbal_r	= (RADDR == 13'b010_1000_0000_00);
	assign rdbah_r	= (RADDR == 13'b010_1000_0000_01);
	assign rdlen_r	= (RADDR == 13'b010_1000_0000_10);
	assign rdh_r	= (RADDR == 13'b010_1000_0001_00);
	assign rdt_r	= (RADDR == 13'b010_1000_0001_10);
	assign ral0_r	= (RADDR == 13'b101_0100_0000_00);
	assign rah0_r	= (RADDR == 13'b101_0100_0000_01);
	assign tctl_r	= (RADDR == 13'b000_0100_0000_00);
	assign tdbal_r	= (RADDR == 13'b011_1000_0000_00);
	assign tdbah_r	= (RADDR == 13'b011_1000_0000_01);
	assign tdlen_r	= (RADDR == 13'b011_1000_0000_10);
	assign tdh_r	= (RADDR == 13'b011_1000_0001_00);
	assign tdt_r	= (RADDR == 13'b011_1000_0001_10);

	assign icr_w	= (WADDR == 13'b000_0000_1100_00);
	assign ics_w	= (WADDR == 13'b000_0000_1100_10);
	assign ims_w	= (WADDR == 13'b000_0000_1101_00);
	assign imc_w	= (WADDR == 13'b000_0000_1101_10);
	assign rctl_w	= (WADDR == 13'b000_0001_0000_00);
	assign rdbal_w	= (WADDR == 13'b010_1000_0000_00);
	assign rdbah_w	= (WADDR == 13'b010_1000_0000_01);
	assign rdlen_w	= (WADDR == 13'b010_1000_0000_10);
	assign rdh_w	= (WADDR == 13'b010_1000_0001_00);
	assign rdt_w	= (WADDR == 13'b010_1000_0001_10);
	assign ral0_w	= (WADDR == 13'b101_0100_0000_00);
	assign rah0_w	= (WADDR == 13'b101_0100_0000_01);
	assign tctl_w	= (WADDR == 13'b000_0100_0000_00);
	assign tdbal_w	= (WADDR == 13'b011_1000_0000_00);
	assign tdbah_w	= (WADDR == 13'b011_1000_0000_01);
	assign tdlen_w	= (WADDR == 13'b011_1000_0000_10);
	assign tdh_w	= (WADDR == 13'b011_1000_0001_00);
	assign tdt_w	= (WADDR == 13'b011_1000_0001_10);

	// Read
	assign RDATA =
		({DW{icr_r	}} & icr_reg	) |
		// ICS write only
		({DW{ims_r	}} & ims_reg	) |
		// IMC write only
		({DW{rctl_r	}} & {5'b0, rctl_reg}) |	// highest 5 bits reads as 0
		({DW{rdbal_r}} & {rdbal_reg, 4'b0}) |	// RDBAL, TDBAL: lowest 4 bits are 0
		({DW{rdbah_r}} & rdbah_reg	) |
		({DW{rdlen_r}} & {12'b0, rdlen_reg[19:7], 7'b0}) |	// RDLEN, TDLEN: highest 12 bits and lowest 7 bits reads as 0
		({DW{rdh_r	}} & {16'b0, rdh_reg}) |	// RDH, RDT, TDH, TDT: highest 16 bits reads as 0
		({DW{rdt_r	}} & {16'b0, rdt_reg}) |
		({DW{ral0_r	}} & ral0_reg	) |
		({DW{rah0_r	}} & rah0_reg	) |
		({DW{tctl_r	}} & {6'b0, tctl_reg}) |	// highest 6 bits reads as 0
		({DW{tdbal_r}} & {tdbal_reg, 4'b0}) |
		({DW{tdbah_r}} & tdbah_reg	) |
		({DW{tdlen_r}} & {12'b0, tdlen_reg[19:7], 7'b0}) |
		({DW{tdh_r	}} & {16'b0, tdh_reg}) |
		({DW{tdt_r	}} & {16'b0, tdt_reg});
	
	// Write
	// ICR (ICS)
	always @(posedge CLK) begin
		if (RESET) icr_reg <= 32'b0;
		else if (REN && icr_r) icr_reg <= 32'b0;	// reading ICR clears it
		else if (WEN) begin
			if (icr_w) begin	// write 1b clears the bit
				if (WSTRB[0])	icr_reg[7:0]   <= icr_reg[7:0]  & ~WDATA[7:0];
				if (WSTRB[1])	icr_reg[15:8]  <= icr_reg[15:8] & ~WDATA[15:8];
				if (WSTRB[2])	icr_reg[23:16] <= icr_reg[23:16]& ~WDATA[23:16];
				if (WSTRB[3])	icr_reg[31:24] <= icr_reg[31:24]& ~WDATA[31:24];
			end
			else if (ics_w) begin	// write 1b to ICS sets the bit in ICR
				if (WSTRB[0])	icr_reg[7:0]   <= icr_reg[7:0]  | WDATA[7:0];
				if (WSTRB[1])	icr_reg[15:8]  <= icr_reg[15:8] | WDATA[15:8];
				if (WSTRB[2])	icr_reg[23:16] <= icr_reg[23:16]| WDATA[23:16];
				if (WSTRB[3])	icr_reg[31:24] <= icr_reg[31:24]| WDATA[31:24];
			end
		end
	end
	// IMS (IMC)
	always @(posedge CLK) begin
		if (WEN) begin
			if (ims_w) begin	// write 1b sets the bit
				if (WSTRB[0])	ims_reg[7:0]   <= ims_reg[7:0]  | WDATA[7:0];
				if (WSTRB[1])	ims_reg[15:8]  <= ims_reg[15:8] | WDATA[15:8];
				if (WSTRB[2])	ims_reg[23:16] <= ims_reg[23:16]| WDATA[23:16];
				if (WSTRB[3])	ims_reg[31:24] <= ims_reg[31:24]| WDATA[31:24];
			end
			else if (imc_w) begin	// write 1b to IMC clears the bit in IMS
				if (WSTRB[0])	ims_reg[7:0]   <= ims_reg[7:0]  & ~WDATA[7:0];
				if (WSTRB[1])	ims_reg[15:8]  <= ims_reg[15:8] & ~WDATA[15:8];
				if (WSTRB[2])	ims_reg[23:16] <= ims_reg[23:16]& ~WDATA[23:16];
				if (WSTRB[3])	ims_reg[31:24] <= ims_reg[31:24]& ~WDATA[31:24];
			end
		end
	end
	// RCTL
	always @(posedge CLK) begin
		if (RESET) rctl_reg <= 27'b0;
		else if (WEN && rctl_w) begin
			if (WSTRB[0])	rctl_reg[7:0]	<= WDATA[7:0];
			if (WSTRB[1])	rctl_reg[15:8]	<= {WDATA[15], 1'b0, WDATA[13:12], 2'b0, WDATA[9:8]};	// bits 14, 11, 10 reads as 0
			if (WSTRB[2])	rctl_reg[23:16]	<= {WDATA[23:22], 1'b0, WDATA[20:16]};	// bit 21 reads as 0
			if (WSTRB[3])	rctl_reg[26:24]	<= {WDATA[26:25], 1'b0};	// bits 31:27 and 24 reads as 0
		end
	end
	// TCTL
	always @(posedge CLK) begin
		if (RESET) tctl_reg <= 26'b0;
		else if (WEN && tctl_w) begin
			if (WSTRB[0])	tctl_reg[7:0]	<= {WDATA[7:3], 1'b0, WDATA[1:0]};	// bit 2 reads as 0
			if (WSTRB[1])	tctl_reg[15:8]	<= WDATA[15:8];
			if (WSTRB[2])	tctl_reg[23:16]	<= {1'b0, WDATA[22:16]};	// bit 23 reads as 0
			if (WSTRB[3])	tctl_reg[25:24]	<= WDATA[25:24];	// highest 6 bits reads as 0
		end
	end
	// RDBAL, TDBAL
	always @(posedge CLK) begin
		if (WEN) begin	// lowest 4 bits are ignored
			if (rdbal_w) begin
				if (WSTRB[0])	rdbal_reg[7:4]	<= WDATA[7:4];
				if (WSTRB[1])	rdbal_reg[15:8]	<= WDATA[15:8];
				if (WSTRB[2])	rdbal_reg[23:16]<= WDATA[23:16];
				if (WSTRB[3])	rdbal_reg[31:24]<= WDATA[31:24];
			end
			if (tdbal_w) begin
				if (WSTRB[0])	tdbal_reg[7:4]	<= WDATA[7:4];
				if (WSTRB[1])	tdbal_reg[15:8]	<= WDATA[15:8];
				if (WSTRB[2])	tdbal_reg[23:16]<= WDATA[23:16];
				if (WSTRB[3])	tdbal_reg[31:24]<= WDATA[31:24];
			end
		end
	end
	// RDLEN, TDLEN
	always @(posedge CLK) begin
		if (RESET) begin 
			rdlen_reg <= 13'b0;
			tdlen_reg <= 13'b0;
		end
		else if (WEN) begin
			if (rdlen_w) begin
				if (WSTRB[0])	rdlen_reg[7]    <= WDATA[7];	// lowest 7 bits are ignored
				if (WSTRB[1])	rdlen_reg[15:8]	<= WDATA[15:8];
				if (WSTRB[2])	rdlen_reg[19:16]<= WDATA[19:16];	// highest 12 bits reads as 0
			end
			if (tdlen_w) begin
				if (WSTRB[0])	tdlen_reg[7]	<= WDATA[7];
				if (WSTRB[1])	tdlen_reg[15:8]	<= WDATA[15:8];
				if (WSTRB[2])	tdlen_reg[19:16]<= WDATA[19:16];
			end
		end
	end
	// RDH, RDT, TDH, TDT
	always @(posedge CLK) begin
		if (RESET) begin
			rdh_reg <= 16'b0;
			rdt_reg <= 16'b0;
			tdh_reg <= 16'b0;
			tdt_reg <= 16'b0;
		end
		else if (WEN) begin	// highest 16 bits reads as 0
			if (rdh_w) begin
				if (WSTRB[0])	rdh_reg[7:0]	<= WDATA[7:0];
				if (WSTRB[1])	rdh_reg[15:8]	<= WDATA[15:8];
			end
			if (rdt_w) begin
				if (WSTRB[0])	rdt_reg[7:0]	<= WDATA[7:0];
				if (WSTRB[1])	rdt_reg[15:8]	<= WDATA[15:8];
			end
			if (tdh_w) begin
				if (WSTRB[0])	tdh_reg[7:0]	<= WDATA[7:0];
				if (WSTRB[1])	tdh_reg[15:8]	<= WDATA[15:8];
			end
			if (tdt_w) begin
				if (WSTRB[0])	tdt_reg[7:0]	<= WDATA[7:0];
				if (WSTRB[1])	tdt_reg[15:8]	<= WDATA[15:8];
			end
		end
	end
	// RAH0
	always @(posedge CLK) begin
		if (RESET) rah0_reg[31:18] <= 14'b0;
		else if (WEN && rah0_w) begin	// bits 30:18 reads as 0
			if (WSTRB[0])	rah0_reg[7:0]	<= WDATA[7:0];
			if (WSTRB[1])	rah0_reg[15:8]	<= WDATA[15:8];
			if (WSTRB[2])	rah0_reg[17:16]	<= WDATA[17:16];
			if (WSTRB[3])	rah0_reg[31]	<= WDATA[31];
		end
	end
	// Others
	always @(posedge CLK) begin
		if (WEN) begin
			if (rdbah_w) begin
				if (WSTRB[0])	rdbah_reg[7:0]	<= WDATA[7:0];
				if (WSTRB[1])	rdbah_reg[15:8]	<= WDATA[15:8];
				if (WSTRB[2])	rdbah_reg[23:16]<= WDATA[23:16];
				if (WSTRB[3])	rdbah_reg[31:24]<= WDATA[31:24];
			end
			if (ral0_w) begin
				if (WSTRB[0])	ral0_reg[7:0]	<= WDATA[7:0];
				if (WSTRB[1])	ral0_reg[15:8]	<= WDATA[15:8];
				if (WSTRB[2])	ral0_reg[23:16]	<= WDATA[23:16];
				if (WSTRB[3])	ral0_reg[31:24]	<= WDATA[31:24];
			end
			if (tdbah_w) begin
				if (WSTRB[0])	tdbah_reg[7:0]	<= WDATA[7:0];
				if (WSTRB[1])	tdbah_reg[15:8]	<= WDATA[15:8];
				if (WSTRB[2])	tdbah_reg[23:16]<= WDATA[23:16];
				if (WSTRB[3])	tdbah_reg[31:24]<= WDATA[31:24];
			end
		end
	end

	// Interrupt generation
	assign HAS_INT = |(icr_reg & ims_reg);
endmodule
