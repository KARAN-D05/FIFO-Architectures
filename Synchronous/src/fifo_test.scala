import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.flatspec.AnyFlatSpec
import java.nio.file.Paths
import scala.collection.mutable.Queue
import scala.util.Random

class FIFOTest extends AnyFlatSpec with ChiselSim {

  "FIFO" should "correctly implement FIFO behavior" in {
    simulate(new FIFO) { dut =>

      def resetFIFO(): Unit = {
        dut.reset.poke(true.B)
        dut.clock.step()
        dut.reset.poke(false.B)
      }

      def write(data: BigInt): Unit = {
        dut.io.wrEn.poke(true.B)
        dut.io.rdEn.poke(false.B)
        dut.io.din.poke(data.U)
        dut.clock.step()
        dut.io.wrEn.poke(false.B)
      }

      def read(expected: BigInt): Unit = {
        dut.io.wrEn.poke(false.B)
        dut.io.rdEn.poke(true.B)
        dut.clock.step()
        dut.io.dout.expect(expected.U)
        dut.io.rdEn.poke(false.B)
      }

      resetFIFO()

      dut.io.empty.expect(true.B)
      dut.io.full.expect(false.B)

      write(10)
      write(20)
      write(30)

      dut.io.empty.expect(false.B)

      read(10)
      read(20)
      read(30)

      dut.io.empty.expect(true.B)

      for (i <- 0 until 16) {
        write(i)
      }

      dut.io.full.expect(true.B)
      dut.io.empty.expect(false.B)

      dut.io.wrEn.poke(true.B)
      dut.io.din.poke(99.U)
      dut.clock.step()
      dut.io.wrEn.poke(false.B)

      dut.io.full.expect(true.B)

      for (i <- 0 until 16) {
        read(i)
      }

      dut.io.empty.expect(true.B)
      dut.io.full.expect(false.B)

      val random = new Random(42)
      val reference = Queue[BigInt]()

      for (_ <- 0 until 500) {

        val doWrite = random.nextBoolean()
        val doRead  = random.nextBoolean()

        val canWrite = reference.size < 16
        val canRead  = reference.nonEmpty

        val write = doWrite && canWrite
        val read  = doRead && canRead

        var writeData = BigInt(0)

        if (write) {
          writeData = BigInt(8, random)
        }

        dut.io.wrEn.poke(write.B)
        dut.io.rdEn.poke(read.B)
        dut.io.din.poke(writeData.U)

        val expectedRead =
          if (read) Some(reference.front)
          else None

        dut.clock.step()

        expectedRead.foreach { expected =>
          dut.io.dout.expect(expected.U)
        }

        if (read) {
          reference.dequeue()
        }

        if (write) {
          reference.enqueue(writeData)
        }

        dut.io.empty.expect(reference.isEmpty.B)
        dut.io.full.expect((reference.size == 16).B)
      }
    }
  }
}
