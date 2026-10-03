//> using test.dep org.scalatest::scalatest:3.2.20

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.flatspec.AnyFlatSpec
import scala.collection.mutable

class FIFOTestHarness extends Module {

  val io = IO(new Bundle {
    val din   = Input(UInt(8.W))
    val wrEn  = Input(Bool())
    val rdEn  = Input(Bool())

    val dout  = Output(UInt(8.W))
    val full  = Output(Bool())
    val empty = Output(Bool())
  })

  // rdClock = clock / 2
  val rdClockReg = RegInit(false.B)
  rdClockReg := ~rdClockReg

  val fifo = Module(new FIFO)

  fifo.io.wrClock := clock
  fifo.io.rdClock := rdClockReg.asClock

  fifo.io.din  := io.din
  fifo.io.wrEn := io.wrEn
  fifo.io.rdEn := io.rdEn

  io.dout  := fifo.io.dout
  io.full  := fifo.io.full
  io.empty := fifo.io.empty
}

class FIFOTest extends AnyFlatSpec with ChiselSim {

  val Depth = 16

  def pattern(i: Int): BigInt = BigInt((i * 13 + 5) & 0xFF)

  // Runs `body` on a freshly reset DUT with helpers in scope.
  private def withDut(body: (FIFOTestHarness, Helpers) => Unit): Unit =
    simulate(new FIFOTestHarness) { dut =>
      dut.io.din.poke(0.U)
      dut.io.wrEn.poke(false.B)
      dut.io.rdEn.poke(false.B)
      dut.clock.step(4)
      body(dut, new Helpers(dut))
    }

  class Helpers(dut: FIFOTestHarness) {

    // wrClock = clock, so 1 step = 1 write edge
    def write(data: BigInt): Unit = {
      dut.io.din.poke(data.U)
      dut.io.wrEn.poke(true.B)
      dut.clock.step()
      dut.io.wrEn.poke(false.B)
      dut.clock.step()
    }

    // rdClock = clock/2, so 2 steps = exactly 1 read edge
    def read(expected: BigInt): Unit = {
      dut.io.rdEn.poke(true.B)
      dut.clock.step(2)
      dut.io.rdEn.poke(false.B)
      dut.io.dout.expect(expected.U)
      dut.clock.step()
    }

    // wait for the write pointer to cross into the read domain
    def waitNotEmpty(): Unit = dut.clock.step(8)

    // wait for the read pointer to cross into the write domain
    def waitFullRelease(): Unit = dut.clock.step(8)

    def fill(): Unit =
      for (i <- 0 until Depth) write(pattern(i))

    def drain(): Unit = {
      waitNotEmpty()
      for (i <- 0 until Depth) read(pattern(i))
    }
  }

  "Asynchronous FIFO" should "come out of reset empty and not full" in {
    withDut { (dut, h) =>
      dut.io.empty.expect(true.B)
      dut.io.full.expect(false.B)
    }
  }

  it should "return data in order for a basic write/read" in {
    withDut { (dut, h) =>
      for (i <- 1 to 3) h.write(i * 10)

      h.waitNotEmpty()
      dut.io.empty.expect(false.B)

      h.read(10)
      h.read(20)
      h.read(30)

      dut.clock.step(2)
      dut.io.empty.expect(true.B)
    }
  }

  it should "ignore reads while empty (underflow)" in {
    withDut { (dut, h) =>
      dut.io.rdEn.poke(true.B)
      dut.clock.step(10)
      dut.io.rdEn.poke(false.B)
      dut.io.empty.expect(true.B)

      // FIFO must still work normally afterwards
      h.write(0x42)
      h.waitNotEmpty()
      h.read(0x42)
    }
  }

  it should "assert full after Depth writes" in {
    withDut { (dut, h) =>
      h.fill()
      dut.io.full.expect(true.B)
    }
  }

  it should "drop writes while full (overflow)" in {
    withDut { (dut, h) =>
      h.fill()
      dut.io.full.expect(true.B)

      h.write(0xFF)
      dut.io.full.expect(true.B)

      // the 0xFF must never show up
      h.drain()
      dut.clock.step(4)
      dut.io.empty.expect(true.B)
      dut.io.full.expect(false.B)
    }
  }

  it should "release full after a read" in {
    withDut { (dut, h) =>
      h.fill()
      dut.io.full.expect(true.B)

      h.waitNotEmpty()
      h.read(pattern(0))
      h.waitFullRelease()
      dut.io.full.expect(false.B)

      // the freed slot can be reused, and order is preserved
      h.write(0x99)
      for (i <- 1 until Depth) h.read(pattern(i))
      h.read(0x99)

      dut.clock.step(4)
      dut.io.empty.expect(true.B)
    }
  }

  it should "wrap its pointers over several fill/drain rounds" in {
    withDut { (dut, h) =>
      for (_ <- 0 until 3) {
        h.fill()
        dut.io.full.expect(true.B)
        h.drain()
        h.waitFullRelease()
        dut.io.empty.expect(true.B)
        dut.io.full.expect(false.B)
      }
    }
  }

  it should "pass random concurrent traffic against a reference queue" in {
    withDut { (dut, h) =>
      val rng    = new scala.util.Random(1234)
      val model  = mutable.Queue[BigInt]()
      val total  = 300
      var sent   = 0
      var rcvd   = 0
      var guard  = 0

      // One slot = 2 clock steps = 2 write edges and 1 read edge.
      while (rcvd < total && guard < 20000) {
        guard += 1

        val doWrite = sent < total && !dut.io.full.peek().litToBoolean && rng.nextInt(4) != 0
        val doRead  = !dut.io.empty.peek().litToBoolean && rng.nextInt(3) != 0

        if (doWrite) {
          dut.io.din.poke(pattern(sent).U)
          dut.io.wrEn.poke(true.B)
        }
        if (doRead) dut.io.rdEn.poke(true.B)

        dut.clock.step()
        dut.io.wrEn.poke(false.B)      // wrEn lasts exactly 1 write edge
        if (doWrite) { model.enqueue(pattern(sent)); sent += 1 }

        dut.clock.step()
        dut.io.rdEn.poke(false.B)      // rdEn lasted 2 steps = 1 read edge

        if (doRead) {
          assert(model.nonEmpty, "FIFO returned data that was never written")
          dut.io.dout.expect(model.dequeue().U)
          rcvd += 1
        }
      }

      assert(rcvd == total, s"only received $rcvd of $total words")
      dut.clock.step(8)
      dut.io.empty.expect(true.B)
    }
  }
}
