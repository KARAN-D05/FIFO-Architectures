`timescale 1ns/1ns
`include "FIFO.sv"

module testbench;

  parameter WIDTH = 8;
  parameter DEPTH = 16;
  parameter NUM_RANDOM = 200;

  logic [WIDTH-1:0] din;
  logic wrEn;
  logic rdEn;
  logic wrClk;
  logic rdClk;
  logic reset;

  logic [WIDTH-1:0] dout;
  logic full;
  logic empty;

  integer errors = 0;

  FIFO dut (
    .clock      (wrClk),
    .reset      (reset),
    .io_wrClock (wrClk),
    .io_rdClock (rdClk),
    .io_din     (din),
    .io_wrEn    (wrEn),
    .io_rdEn    (rdEn),
    .io_dout    (dout),
    .io_full    (full),
    .io_empty   (empty)
  );

  initial wrClk = 0;
  always #5 wrClk = ~wrClk;

  initial rdClk = 0;
  always #7 rdClk = ~rdClk;

  function automatic logic [WIDTH-1:0] pattern(input integer i);
    pattern = (i * 13 + 5) & {WIDTH{1'b1}};
  endfunction

  task check(input bit cond, input string msg);
    begin
      if (cond)
        $display("PASS: %s", msg);
      else begin
        $display("FAIL: %s", msg);
        errors = errors + 1;
      end
    end
  endtask

  task write(input logic [WIDTH-1:0] data);
    begin
      @(negedge wrClk);
      din  = data;
      wrEn = 1'b1;

      @(posedge wrClk);
      #1;

      wrEn = 1'b0;
    end
  endtask

  task read(input logic [WIDTH-1:0] expected);
    integer guard;
    begin
      guard = 0;
      @(negedge rdClk);
      while (empty && guard < 20) begin
        @(negedge rdClk);
        guard = guard + 1;
      end

      if (empty) begin
        $display("FAIL: FIFO stayed empty, cannot read %h", expected);
        errors = errors + 1;
      end else begin
        rdEn = 1'b1;

        @(posedge rdClk);
        #1;

        if (dout !== expected) begin
          $display("FAIL: Expected %h, got %h", expected, dout);
          errors = errors + 1;
        end else
          $display("PASS: Read %h", dout);

        rdEn = 1'b0;
      end
    end
  endtask

  initial begin
    #2000000;
    $display("FAIL: Timeout");
    $finish;
  end

  initial begin

    $monitor("time = %0t | din = %h | wrEn = %b | rdEn = %b | dout = %h | full = %b | empty = %b",$time, din, wrEn, rdEn, dout, full, empty);

    $dumpfile("FIFO.vcd");
    $dumpvars(0, testbench);

    din   = '0;
    wrEn  = 1'b0;
    rdEn  = 1'b0;
    reset = 1'b1;

    repeat (3) @(negedge wrClk);
    reset = 1'b0;
    repeat (2) @(negedge wrClk);
    #1;

    check(empty && !full, "FIFO reset state");

    write(8'h0A);
    write(8'h14);
    write(8'h1E);

    read(8'h0A);
    read(8'h14);
    read(8'h1E);

    #1;
    check(empty, "FIFO empty after draining");

    @(negedge rdClk);
    rdEn = 1'b1;
    repeat (3) @(posedge rdClk);
    #1;
    rdEn = 1'b0;
    check(empty, "Underflow read ignored, still empty");

    repeat (4) @(negedge wrClk);

    for (int i = 0; i < DEPTH; i++) begin
      write(pattern(i));
    end

    #1;
    check(full, "FIFO full");

    write(8'hFF);
    #1;
    check(full, "Still full after overflow write");

    for (int i = 0; i < DEPTH; i++) begin
      read(pattern(i));
    end

    #1;
    check(empty && !full, "FIFO drained, overflow write was dropped");

    repeat (4) @(negedge wrClk);
    for (int i = 0; i < DEPTH; i++) begin
      write(pattern(i));
    end
    #1;
    check(full, "FIFO full again");

    read(pattern(0));
    repeat (4) @(negedge wrClk);
    check(!full, "Full deasserted after one read");

    for (int i = 1; i < DEPTH; i++) begin
      read(pattern(i));
    end
    #1;
    check(empty, "FIFO empty again");

    $display("--- Concurrent test: %0d words ---", NUM_RANDOM);

    fork

      begin : writer
        integer i;
        i = 0;
        while (i < NUM_RANDOM) begin
          repeat ($urandom_range(0, 3)) @(negedge wrClk);
          @(negedge wrClk);
          if (!full) begin
            din  = pattern(i);
            wrEn = 1'b1;
            @(posedge wrClk);
            #1;
            wrEn = 1'b0;
            i = i + 1;
          end
        end
      end

      begin : reader
        integer j;
        j = 0;
        while (j < NUM_RANDOM) begin
          repeat ($urandom_range(0, 3)) @(negedge rdClk);
          @(negedge rdClk);
          if (!empty) begin
            rdEn = 1'b1;
            @(posedge rdClk);
            #1;
            rdEn = 1'b0;
            if (dout !== pattern(j)) begin
              $display("FAIL: word %0d expected %h, got %h", j, pattern(j), dout);
              errors = errors + 1;
            end
            j = j + 1;
          end
        end
      end

    join

    repeat (6) @(negedge rdClk);
    #1;
    check(empty, "FIFO empty after concurrent test");

    if (errors == 0)
      $display("ALL TESTS PASSED");
    else
      $display("%0d TEST(S) FAILED", errors);

    $display("Simulation Complete");
    $finish;

  end

endmodule
