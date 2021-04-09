package com.wire.signals

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import testutils._

import scala.util.{Failure, Success, Try}

class CancellableFutureSpec extends munit.FunSuite {
  test("Transform between a future and a cancellable future") {
    import CancellableFuture._
    val f1: Future[Unit] = Future.successful(())
    val cf1 = f1.toCancellable
    cf1 match {
      case _: CancellableFuture[Unit] =>
      case _ => fail("Future[Unit] should be transformed into CancellableFuture[Unit]")
    }

    val f2 = cf1.future
    f2 match {
      case _: Future[Unit] =>
      case _ => fail("CancellableFuture[Unit] should wrap over Future[Unit]")
    }

    val cf2 = CancellableFuture.lift(f2)
    cf2 match {
      case _: CancellableFuture[Unit] =>
      case _ => fail("Future[Unit] should be lifted into CancellableFuture[Unit]")
    }
  }

  test("Create a cancellable future from the body") {
    import Threading.defaultContext
    var res = 0

    val cf = CancellableFuture { res = 1 }

    await(cf)

    assertEquals(res, 1)
  }

  test("Create an already completed cancellable future from the body") {
    var res = 0

    val cf = CancellableFuture.successful { res = 1 }

    assertEquals(res, 1)
  }

  test("Create an already failed cancellable future") {
    val cf = CancellableFuture.failed(new IllegalArgumentException)
    assert(cf.isCompleted)
  }

  test("Create an already cancelled cancellable future") {
    val cf = CancellableFuture.cancelled()
    assert(cf.isCompleted)
  }


  test(" A cancellable future succeeds just like a standard one") {
    implicit val ec: DispatchQueue = SerialDispatchQueue()
    var res = 0

    val p = Promise[Int]()
    val f = p.future
    f.foreach { res = _ }

    val cf = new CancellableFuture(p)
    cf.onCancelled{ res = -1 }

    p.success(1)
    assertEquals(result(f), 1)

    assert(!cf.cancel())
    await(cf)
    assertEquals(res, 1) // cancellation doesn't change the result after the promise is completed
  }

  test("A cancellable future can be cancelled (wow! who would expect that)"){
    import Threading.defaultContext
    var res = 0

    val p = Promise[Int]()
    val f = p.future
    f.foreach { res = _ }

    val cf = new CancellableFuture(p)
    cf.onCancelled { res = -1 }

    assert(cf.cancel())
    await(cf)
    assertEquals(res, -1)
    intercept[java.lang.IllegalStateException](p.success(1)) // the promise is already cancelled
  }

  test("A cancellable future can't be cancelled twice") {
    import Threading.defaultContext
    var res = 0

    val p = Promise[Int]()
    val f = p.future
    f.foreach { res = _ }

    val cf = new CancellableFuture(p)
    cf.onCancelled{ res = -1 }

    assert(cf.cancel())
    await(cf)
    assertEquals(res, -1)

    assert(!cf.cancel()) // if cancelling cf twice worked, this would be true
    await(cf)
    assertEquals(res, -1) // if cancelling cf twice worked, this would be -2*/
  }

  test(" Complete a delayed cancellable future") {
    import Threading.defaultContext
    var res = 0

    val cf: CancellableFuture[Unit] = CancellableFuture.delay(500.millis).map { _ => res = 1 }
    assertEquals(res, 0)

    await(cf)

    assertEquals(res, 1)
  }

  test("Complete a cancellable future delayed in another way") {
    import Threading.defaultContext
    var res = 0

    val cf: CancellableFuture[Unit] = CancellableFuture.delayed(500.millis) { res = 1 }
    assertEquals(res, 0)

    await(cf)

    assertEquals(res, 1)
  }

  test("Cancel a delayed future") {
    import Threading.defaultContext
    var res = 0

    val cf: CancellableFuture[Unit] = CancellableFuture.delayed(500.millis) { res = 1 }
    assertEquals(res, 0)
    assert(cf.cancel())

    await(cf)

    assertEquals(res, 0)
  }

  test("Repeat a task until cancelled") {
    import Threading.defaultContext

    var timestamps = Seq.empty[Long]
    val offset = System.currentTimeMillis
    val cf = CancellableFuture.repeat(100.millis){ timestamps :+= (System.currentTimeMillis - offset) }

    CancellableFuture.delayed(500.millis) { cf.cancel() }

    await(cf)

    assert(timestamps.size >= 4)
  }

  test("Turn a sequence of cancellable futures into one") {
    import Threading.defaultContext

    var timestamps = Seq.empty[Long]
    val offset = System.currentTimeMillis
    val cf1 = CancellableFuture.delayed(100.millis) { timestamps :+= (System.currentTimeMillis - offset) }
    val cf2 = CancellableFuture.delayed(200.millis) { timestamps :+= (System.currentTimeMillis - offset) }
    val cfSeq = CancellableFuture.sequence(Seq(cf1, cf2))

    await(cfSeq)

    assert(timestamps.size == 2)
  }

  test("You can't cancel a lifted future") {
    import Threading.defaultContext
    import CancellableFuture._

    var theFlag = false // this should stay false if the future was cancelled (but it won't)
    var semaphore = false
    val f1: Future[Unit] = Future {
      while(!semaphore) Thread.sleep(100L)
      theFlag = true
    }

    val cf1 = f1.toCancellable
    assert(!cf1.cancel())

    semaphore = true
    await(f1)

    assert(theFlag)
  }
}
