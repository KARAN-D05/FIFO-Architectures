`timescale 1ns/1ns
`include "FIFO.sv"

module testbench;

  parameter WIDTH = 8;
  parameter DEPTH = 16;

  logic [WIDTH-1:0] din;
  logic wrEn;
  logic rdEn;
  logic clock;
  logic reset;

  logic [WIDTH-1:0] dout;
  logic full;
  logic empty;

  FIFO dut (
    .clock (clock),
    .reset (reset),
    .io_din   (din),
    .io_wrEn  (wrEn),
    .io_rdEn  (rdEn),
    .io_dout  (dout),
    .io_full  (full),
    .io_empty (empty)
  );

  initial clock = 0;
  always #5 clock = ~clock;

  task write(input logic [WIDTH-1:0] data);
    begin
      @(negedge clock);
      din  = data;
      wrEn = 1'b1;
      rdEn = 1'b0;

      @(posedge clock);
      #1;

      wrEn = 1'b0;
    end
  endtask

  task read(input logic [WIDTH-1:0] expected);
    begin
      @(negedge clock);
      wrEn = 1'b0;
      rdEn = 1'b1;

      @(posedge clock);
      #1;

      if (dout !== expected)
        $display("FAIL: Expected %h, got %h", expected, dout);
      else
        $display("PASS: Read %h", dout);

      rdEn = 1'b0;
    end
  endtask

  initial begin

    $monitor("time = %0t | din = %h | wrEn = %b | rdEn = %b | dout = %h | full = %b | empty = %b", $time, din, wrEn, rdEn, dout, full, empty);

    $dumpfile("FIFO.vcd");
    $dumpvars(0, testbench);

    din  = '0;
    wrEn = 1'b0;
    rdEn = 1'b0;

    @(negedge clock);
    reset = 1'b1;

    @(negedge clock);
    reset = 1'b0;
    #1;

    if (!empty || full)
      $display("FAIL: FIFO reset state incorrect");
    else
      $display("PASS: FIFO reset state");

    write(8'h0A);
    write(8'h14);
    write(8'h1E);

    read(8'h0A);
    read(8'h14);
    read(8'h1E);

    if (empty)
      $display("PASS: FIFO empty after draining");
    else
      $display("FAIL: FIFO should be empty");

    for (int i = 0; i < DEPTH; i++) begin
      write(i);
    end

    #1;

    if (full)
      $display("PASS: FIFO full");
    else
      $display("FAIL: FIFO should be full");

    for (int i = 0; i < DEPTH; i++) begin
      read(i);
    end

    #1;

    if (empty && !full)
      $display("PASS: FIFO drained successfully");
    else
      $display("FAIL: FIFO final state incorrect");

    $display("Simulation Complete!");
    $finish;

  end

endmodule