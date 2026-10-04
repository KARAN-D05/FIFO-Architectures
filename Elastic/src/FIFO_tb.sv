`timescale 1ns/1ns
`include "FIFO.sv"

module testbench;

  parameter WIDTH = 8;
  parameter DEPTH = 16;

  logic [WIDTH-1:0] din;
  logic inValid;
  logic inReady;

  logic [WIDTH-1:0] dout;
  logic outValid;
  logic outReady;

  logic clock;
  logic reset;

  logic [WIDTH-1:0] heldData;

  FIFO dut (
    .clock       (clock),
    .reset       (reset),
    .io_din      (din),
    .io_inValid  (inValid),
    .io_inReady  (inReady),
    .io_dout     (dout),
    .io_outValid (outValid),
    .io_outReady (outReady)
  );

  initial clock = 0;
  always #5 clock = ~clock;

  task write(input logic [WIDTH-1:0] data);
    begin
      @(negedge clock);

      din     = data;
      inValid = 1'b1;

      if (!inReady)
        $display("FAIL: FIFO not ready for write");

      @(posedge clock);
      #1;

      inValid = 1'b0;
    end
  endtask

  task read(input logic [WIDTH-1:0] expected);
    begin
      @(negedge clock);

      outReady = 1'b1;

      if (!outValid)
        $display("FAIL: FIFO has no valid data");
      else if (dout !== expected)
        $display("FAIL: Expected %h, got %h", expected, dout);
      else
        $display("PASS: Read %h", dout);

      @(posedge clock);
      #1;

      outReady = 1'b0;
    end
  endtask

  initial begin

    $monitor("time = %0t | din = %h | inValid = %b | inReady = %b | dout = %h | outValid = %b | outReady = %b",
             $time, din, inValid, inReady, dout, outValid, outReady);

    $dumpfile("FIFO.vcd");
    $dumpvars(0, testbench);

    din      = '0;
    inValid  = 1'b0;
    outReady = 1'b0;
    reset    = 1'b0;

    @(negedge clock);
    reset = 1'b1;

    @(negedge clock);
    reset = 1'b0;
    #1;

    if (outValid || !inReady)
      $display("FAIL: FIFO reset state incorrect");
    else
      $display("PASS: FIFO reset state");

    write(8'h0A);
    write(8'h14);
    write(8'h1E);

    read(8'h0A);
    read(8'h14);
    read(8'h1E);

    #1;

    if (!outValid && inReady)
      $display("PASS: FIFO empty after draining");
    else
      $display("FAIL: FIFO should be empty");

    for (int i = 0; i < DEPTH; i++) begin
      write(i);
    end

    #1;

    if (!inReady && outValid)
      $display("PASS: FIFO full");
    else
      $display("FAIL: FIFO should be full");

    inValid = 1'b1;
    din     = 8'h63;

    @(posedge clock);
    #1;

    inValid = 1'b0;

    if (!inReady && outValid)
      $display("PASS: FIFO correctly rejected write while full");
    else
      $display("FAIL: FIFO accepted write while full");

    for (int i = 0; i < DEPTH; i++) begin
      read(i);
    end

    #1;

    if (!outValid && inReady)
      $display("PASS: FIFO drained successfully");
    else
      $display("FAIL: FIFO final state incorrect");

    write(8'h7B);

    @(negedge clock);
    outReady = 1'b0;

    #1;

    heldData = dout;

    repeat (5) begin
      @(posedge clock);
      #1;

      if (!outValid)
        $display("FAIL: outValid dropped while consumer stalled");

      if (dout !== heldData)
        $display("FAIL: Data changed while consumer stalled");
    end

    outReady = 1'b1;

    if (outValid && dout === 8'h7B)
      $display("PASS: FIFO held data during backpressure");
    else
      $display("FAIL: Backpressure behavior incorrect");

    @(posedge clock);
    #1;

    outReady = 1'b0;

    if (!outValid && inReady)
      $display("PASS: FIFO empty after backpressure test");
    else
      $display("FAIL: FIFO state incorrect after backpressure test");

    $display("Simulation Complete");
    $finish;

  end

endmodule