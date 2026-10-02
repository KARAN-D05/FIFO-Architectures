generate:
	scala-cli run Synchronous/src/fifo.scala

test:
	scala-cli test Synchronous/src/fifo.scala \
		Synchronous/src/FIFOTest.scala \
		--dependency org.scalatest::scalatest:3.2.20
