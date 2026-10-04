//> using scala 2.13.18
//> using dep org.chipsalliance::chisel:7.16.0
//> using plugin org.chipsalliance:::chisel-plugin:7.16.0

import chisel3._
import chisel3.util._

class AsyncFIFO(val width: Int, val depth: Int) extends Module {

  require(width > 0)
  require(depth >= 2)
  require((depth & (depth - 1)) == 0)

  val io = IO(new Bundle {
    val wrClock = Input(Clock())
    val rdClock = Input(Clock())

    val din  = Input(UInt(width.W))
    val wrEn = Input(Bool())

    val rdEn = Input(Bool())
    val dout = Output(UInt(width.W))

    val full  = Output(Bool())
    val empty = Output(Bool())
  })

  val addrWidth = log2Ceil(depth)
  val ptrWidth  = addrWidth + 1

  val mem = withClock(io.wrClock) { Mem(depth, UInt(width.W)) }

  val wrPtrGray = Wire(UInt(ptrWidth.W))
  val rdPtrGray = Wire(UInt(ptrWidth.W))

  withClockAndReset(io.wrClock, reset.asAsyncReset) {

    val wrPtr        = RegInit(0.U(ptrWidth.W))
    val wrPtrGrayReg = RegInit(0.U(ptrWidth.W))
    val fullReg      = RegInit(false.B)

    val rdPtrGraySync1 = RegInit(0.U(ptrWidth.W))
    val rdPtrGraySync2 = RegInit(0.U(ptrWidth.W))

    wrPtrGray := wrPtrGrayReg

    rdPtrGraySync1 := rdPtrGray
    rdPtrGraySync2 := rdPtrGraySync1

    val wrDo = io.wrEn && !fullReg

    val wrPtrNext     = wrPtr + wrDo.asUInt
    val wrPtrGrayNext = wrPtrNext ^ (wrPtrNext >> 1)

    val fullCompare = Cat(~rdPtrGraySync2(ptrWidth - 1, ptrWidth - 2), rdPtrGraySync2(ptrWidth - 3, 0))

    val fullNext = wrPtrGrayNext === fullCompare

    when(wrDo) {
      mem.write(wrPtr(addrWidth - 1, 0), io.din)
    }

    wrPtr        := wrPtrNext
    wrPtrGrayReg := wrPtrGrayNext
    fullReg      := fullNext

    io.full := fullReg
  }

  withClockAndReset(io.rdClock, reset.asAsyncReset) {

    val rdPtr        = RegInit(0.U(ptrWidth.W))
    val rdPtrGrayReg = RegInit(0.U(ptrWidth.W))
    val emptyReg     = RegInit(true.B)

    val wrPtrGraySync1 = RegInit(0.U(ptrWidth.W))
    val wrPtrGraySync2 = RegInit(0.U(ptrWidth.W))

    val doutReg = RegInit(0.U(width.W))

    rdPtrGray := rdPtrGrayReg

    wrPtrGraySync1 := wrPtrGray
    wrPtrGraySync2 := wrPtrGraySync1

    val rdDo = io.rdEn && !emptyReg

    val rdPtrNext     = rdPtr + rdDo.asUInt
    val rdPtrGrayNext = rdPtrNext ^ (rdPtrNext >> 1)

    val emptyNext = rdPtrGrayNext === wrPtrGraySync2

    when(rdDo) {
      doutReg := mem.read(rdPtr(addrWidth - 1, 0), io.rdClock)
    }

    rdPtr        := rdPtrNext
    rdPtrGrayReg := rdPtrGrayNext
    emptyReg     := emptyNext

    io.dout  := doutReg
    io.empty := emptyReg
  }
}

class FIFO extends Module {

  val io = IO(new Bundle {
    val wrClock = Input(Clock())
    val rdClock = Input(Clock())

    val din  = Input(UInt(8.W))
    val wrEn = Input(Bool())

    val rdEn = Input(Bool())
    val dout = Output(UInt(8.W))

    val full  = Output(Bool())
    val empty = Output(Bool())
  })

  val fifo = Module(new AsyncFIFO(8, 16))

  fifo.io.wrClock := io.wrClock
  fifo.io.rdClock := io.rdClock

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
