package com.wire.signals

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import testutils._

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
    import Threading.defaultContext
    var res = 0

    val p = Promise[Int]()
    val f = p.future
    f.foreach { res = _ }

    val cf = CancellableFuture.lift(
      future = f,
      onCancel = { res = -1 }
    )

    p.success(1)
    await(f)
    assertEquals(res, 1)

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

    val cf = CancellableFuture.lift(
      future = f,
      onCancel = { res = -1 }
    )

    assert(cf.cancel())
    await(cf)
    assertEquals(res, -1)

    p.success(1)
    await(f)
    assertEquals(res, -1) // completing the promise doesn't change the result after cf is cancelled
  }

  test("A cancellable future can't be cancelled twice") {
    import Threading.defaultContext
    var res = 0

    val p = Promise[Int]()

    val cf = CancellableFuture.lift(
      future = p.future,
      onCancel = { res -= 1 }
    )

    assert(cf.cancel())
    await(cf)
    assertEquals(res, -1)

    assert(!cf.cancel()) // if cancelling cf twice worked, this would be true
    await(cf)
    assertEquals(res, -1) // if cancelling cf twice worked, this would be -2
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
}
