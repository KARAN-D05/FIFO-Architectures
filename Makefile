generate:
	scala-cli run fifo.scala

test:
	scala-cli test fifo.scala fifo_test.scala --dependency org.scalatest::scalatest:3.2.20
