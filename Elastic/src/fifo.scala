//> using scala 2.13.18
//> using dep org.chipsalliance::chisel:7.16.0
//> using plugin org.chipsalliance:::chisel-plugin:7.16.0

import chisel3._
import chisel3.util._

class ElasticFIFO(val width: Int, val depth: Int) extends Module {
  require(width > 0)
  require(depth > 0)

  val io = IO(new Bundle {
    val inData  = Input(UInt(width.W))
    val inValid = Input(Bool())
    val inReady = Output(Bool())

    val outData  = Output(UInt(width.W))
    val outValid = Output(Bool())
    val outReady = Input(Bool())
  })

  val mem = Mem(depth, UInt(width.W))

  val ptrWidth = math.max(1, log2Ceil(depth))
  val countWidth = math.max(1, log2Ceil(depth + 1))

  val wrPtr = RegInit(0.U(ptrWidth.W))
  val rdPtr = RegInit(0.U(ptrWidth.W))

  val count = RegInit(0.U(countWidth.W))

  val empty = count === 0.U
  val full = count === depth.U

  io.inReady := !full
  io.outValid := !empty

  val doWrite = io.inValid && io.inReady
  val doRead = io.outValid && io.outReady

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
    mem.write(wrPtr, io.inData)
    wrPtr := nextWrPtr
  }

  when (doRead) {
    rdPtr := nextRdPtr
  }

  io.outData := mem.read(rdPtr)

  when (doWrite && !doRead) {
    count := count + 1.U
  }.elsewhen (!doWrite && doRead) {
    count := count - 1.U
  }
}

class FIFO extends Module {
  val io = IO(new Bundle {

    val din      = Input(UInt(8.W))
    val inValid  = Input(Bool())
    val inReady  = Output(Bool())

    val dout     = Output(UInt(8.W))
    val outValid = Output(Bool())
    val outReady = Input(Bool())
  })

  val fifo = Module(new ElasticFIFO(8, 16))

  fifo.io.inData  := io.din
  fifo.io.inValid := io.inValid

  io.inReady := fifo.io.inReady

  io.dout     := fifo.io.outData
  io.outValid := fifo.io.outValid

  fifo.io.outReady := io.outReady
}

object FIFO extends App {
  emitVerilog(new FIFO)
}
