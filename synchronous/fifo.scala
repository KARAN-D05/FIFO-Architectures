//> using scala 2.13.18
//> using dep org.chipsalliance::chisel:7.16.0
//> using plugin org.chipsalliance:::chisel-plugin:7.16.0

import chisel3._
import chisel3.util._

class SyncFIFO(val width: Int, val depth: Int) extends Module {
  require(width > 0)
  require(depth > 0)

  val io = IO(new Bundle {
    val din   = Input(UInt(width.W))
    val wrEn  = Input(Bool())
    val rdEn  = Input(Bool())
    val dout  = Output(UInt(width.W))
    val full  = Output(Bool())
    val empty = Output(Bool())
  })

  val mem = Mem(depth, UInt(width.W))

  val ptrWidth   = math.max(1, log2Ceil(depth))
  val countWidth = log2Ceil(depth + 1)

  val wrPtr   = RegInit(0.U(ptrWidth.W))
  val rdPtr   = RegInit(0.U(ptrWidth.W))
  val count   = RegInit(0.U(countWidth.W))
  val doutReg = RegInit(0.U(width.W))

  io.empty := count === 0.U
  io.full  := count === depth.U

  val doWrite = io.wrEn && !io.full
  val doRead  = io.rdEn && !io.empty

  val nextWrPtr = Wire(UInt(ptrWidth.W))
  val nextRdPtr = Wire(UInt(ptrWidth.W))

  when (wrPtr === (depth - 1).U) {
    nextWrPtr := 0.U
  }.otherwise {
    nextWrPtr := wrPtr + 1.U
  }

  when (rdPtr === (depth - 1).U) {
    nextRdPtr := 0.U
  }.otherwise {
    nextRdPtr := rdPtr + 1.U
  }

  when (doWrite) {
    mem.write(wrPtr, io.din)
    wrPtr := nextWrPtr
  }

  when (doRead) {
    doutReg := mem.read(rdPtr)
    rdPtr   := nextRdPtr
  }

  io.dout := doutReg

  when (doWrite && !doRead) {
    count := count + 1.U
  }.elsewhen (!doWrite && doRead) {
    count := count - 1.U
  }
}

class FIFO extends Module {
  val io = IO(new Bundle {
    val din   = Input(UInt(8.W))
    val wrEn  = Input(Bool())
    val rdEn  = Input(Bool())
    val dout  = Output(UInt(8.W))
    val full  = Output(Bool())
    val empty = Output(Bool())
  })

  val fifo = Module(new SyncFIFO(8, 16))

  fifo.io.din  := io.din
  fifo.io.wrEn := io.wrEn
  fifo.io.rdEn := io.rdEn

  io.dout  := fifo.io.dout
  io.full  := fifo.io.full
  io.empty := fifo.io.empty
}

object FIFO extends App {
  emitVerilog(new FIFO)
}
