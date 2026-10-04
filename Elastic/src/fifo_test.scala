import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.flatspec.AnyFlatSpec
import scala.collection.mutable.Queue
import scala.util.Random

class FIFOTest extends AnyFlatSpec with ChiselSim {

  "FIFO" should "correctly implement elastic FIFO behavior" in {
    simulate(new FIFO) { dut =>

      def resetFIFO(): Unit = {
        dut.reset.poke(true.B)
        dut.clock.step()
        dut.reset.poke(false.B)
      }

      def write(data: BigInt): Unit = {
        dut.io.inValid.poke(true.B)
        dut.io.din.poke(data.U)

        dut.io.inReady.expect(true.B)

        dut.clock.step()

        dut.io.inValid.poke(false.B)
      }

      def read(expected: BigInt): Unit = {
        dut.io.outValid.expect(true.B)
        dut.io.outReady.poke(true.B)
        dut.io.dout.expect(expected.U)

        dut.clock.step()

        dut.io.outReady.poke(false.B)
      }

      resetFIFO()

      dut.io.outValid.expect(false.B)
      dut.io.inReady.expect(true.B)

      write(10)
      write(20)
      write(30)

      dut.io.outValid.expect(true.B)

      read(10)
      read(20)
      read(30)

      dut.io.outValid.expect(false.B)
      dut.io.inReady.expect(true.B)

      for (i <- 0 until 16) {
        write(i)
      }

      dut.io.inReady.expect(false.B)
      dut.io.outValid.expect(true.B)

      dut.io.inValid.poke(true.B)
      dut.io.din.poke(99.U)

      dut.io.inReady.expect(false.B)

      dut.clock.step()

      dut.io.inValid.poke(false.B)

      dut.io.inReady.expect(false.B)
      dut.io.outValid.expect(true.B)
      dut.io.dout.expect(0.U)

      for (i <- 0 until 16) {
        read(i)
      }

      dut.io.outValid.expect(false.B)
      dut.io.inReady.expect(true.B)

      write(123)

      dut.io.outValid.expect(true.B)
      dut.io.outReady.poke(false.B)

      val heldData = dut.io.dout.peek().litValue

      for (_ <- 0 until 5) {
        dut.io.outValid.expect(true.B)
        dut.io.dout.expect(heldData.U)
        dut.clock.step()
      }

      dut.io.outReady.poke(true.B)

      dut.io.outValid.expect(true.B)
      dut.io.dout.expect(123.U)

      dut.clock.step()

      dut.io.outReady.poke(false.B)

      dut.io.outValid.expect(false.B)
      dut.io.inReady.expect(true.B)

      val random = new Random(42)
      val reference = Queue[BigInt]()

      for (_ <- 0 until 500) {

        val producerValid = random.nextBoolean()
        val consumerReady = random.nextBoolean()

        var writeData = BigInt(0)

        if (producerValid) {
          writeData = BigInt(8, random)
        }

        val canWrite = reference.size < 16
        val canRead  = reference.nonEmpty

        val write = producerValid && canWrite
        val read  = consumerReady && canRead

        dut.io.inValid.poke(producerValid.B)
        dut.io.din.poke(writeData.U)

        dut.io.outReady.poke(consumerReady.B)

        dut.io.inReady.expect(canWrite.B)
        dut.io.outValid.expect(canRead.B)

        if (read) {
          dut.io.dout.expect(reference.front.U)
        }

        dut.clock.step()

        if (read) {
          reference.dequeue()
        }

        if (write) {
          reference.enqueue(writeData)
        }

        dut.io.outValid.expect(reference.nonEmpty.B)
        dut.io.inReady.expect((reference.size < 16).B)
      }

      dut.io.inValid.poke(false.B)
      dut.io.outReady.poke(false.B)
    }
  }
}
