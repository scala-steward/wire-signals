/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.wire.signals

import com.wire.signals.testutils.{awaitAllTasks, result, waitForResult}

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps

class EventStreamSpec extends munit.FunSuite {

  import EventContext.Implicits.global

  test("unsubscribe from source and current mapped signal on onUnwire") {
    val a: SourceStream[Int] = EventStream()
    val b: SourceStream[Int] = EventStream()

    val subscription = a.flatMap(_ => b).onCurrent { _ => }
    a ! 1

    assert(b.hasSubscribers, "mapped event stream should have subscriber after element emitting from source event stream")

    subscription.unsubscribe()

    assert(!a.hasSubscribers, "source event stream should have no subscribers after onUnwire was called on FlatMapLatestEventStream")
    assert(!b.hasSubscribers, "mapped event stream should have no subscribers after onUnwire was called on FlatMapLatestEventStream")
  }

  test("discard old mapped event stream when new element emitted from source event stream") {
    val a: SourceStream[String] = EventStream()
    val b: SourceStream[String] = EventStream()
    val c: SourceStream[String] = EventStream()

    var flatMapCalledCount = 0
    var lastReceivedElement: Option[String] = None
    val subscription = a.flatMap { _ => returning(if (flatMapCalledCount == 0) b else c) { _ => flatMapCalledCount += 1 } }
                        .onCurrent { elem => lastReceivedElement = Some(elem) }
    a ! "a"

    assert(b.hasSubscribers, "mapped event stream 'b' should have subscriber after first element emitting from source event stream")

    b ! "b"

    assertEquals(lastReceivedElement, Some("b"), "flatMapLatest event stream should provide events emitted from mapped signal 'b'")

    a ! "a"

    assert(!b.hasSubscribers, "mapped event stream 'b' should have no subscribers after second element emitting from source event stream")

    assert(c.hasSubscribers, "mapped event stream 'c' should have subscriber after second element emitting from source event stream")

    c ! "c"
    b ! "b"

    assertEquals(lastReceivedElement, Some("c")) // flatMapLatest event stream should provide events emitted from mapped signal 'c'

    subscription.unsubscribe()
  }

  test("emit an event when a future is successfully completed") {
    implicit val dq: DispatchQueue = SerialDispatchQueue()
    val promise = Promise[Int]()
    val resPromise = Promise[Int]()

    EventStream.from(promise.future).onCurrent { event =>
      assertEquals(event, 1)
      resPromise.success(event)
    }

    testutils.withDelay(promise.success(1))

    assertEquals(testutils.result(resPromise.future), 1)
  }

  test("don't emit an event when a future is completed with a failure") {
    val promise = Promise[Int]()
    val resPromise = Promise[Int]()

    EventStream.from(promise.future).onCurrent { event => resPromise.success(event) }

    promise.failure(new IllegalArgumentException)

    assert(testutils.tryResult(resPromise.future)(1 seconds).isFailure)
  }

  test("emit an event after delay by wrapping a cancellable future") {
    val promise = Promise[Long]()
    val t = System.currentTimeMillis()
    val stream = EventStream.from(CancellableFuture.delay(1 seconds))

    stream.onCurrent { _ => promise.success(System.currentTimeMillis() - t) }

    assert(result(promise.future) >= 1000L)
  }

  test("zip two streams and emit an event coming from either of them") {
    val stream1 = EventStream[Int]()
    val stream2 = EventStream[Int]()
    val zip = EventStream.zip(stream1, stream2)

    val expected = Signal(0)
    val eventReceived = Signal(false)
    zip.foreach { n =>
      eventReceived ! true
      waitForResult(expected, n)
    }

    def test(n: Int, source: SourceStream[Int]): Unit = {
      eventReceived ! false
      expected ! n
      source ! n
      waitForResult(eventReceived, true)
    }

    test(1, stream1)
    test(2, stream2)
    test(3, stream1)
    test(4, stream2)
  }

  test("zip the first stream with another and emit an event coming from either of them") {
    val stream1 = EventStream[Int]()
    val stream2 = EventStream[Int]()
    val zip = stream1.zip(stream2)

    val expected = Signal(0)
    val eventReceived = Signal(false)
    zip.foreach { n =>
      eventReceived ! true
      waitForResult(expected, n)
    }

    def test(n: Int, source: SourceStream[Int]): Unit = {
      eventReceived ! false
      expected ! n
      source ! n
      waitForResult(eventReceived, true)
    }

    test(1, stream1)
    test(2, stream2)
    test(3, stream1)
    test(4, stream2)
  }

  test("pipe events from the first stream to another") {
    val stream1 = EventStream[Int]()
    val stream2 = EventStream[Int]()

    val expected = Signal(0)
    val eventReceived = Signal(false)
    stream1.pipeTo(stream2)
    stream2.foreach { n =>
      eventReceived ! true
      waitForResult(expected, n)
    }

    def test(n: Int): Unit = {
      eventReceived ! false
      expected ! n
      stream1 ! n
      waitForResult(eventReceived, true)
    }

    test(1)
    test(2)
    test(3)
    test(4)
  }

  test("pipe events with the | operator from the first stream to another") {
    val stream1 = EventStream[Int]()
    val stream2 = EventStream[Int]()

    val expected = Signal(0)
    val eventReceived = Signal(false)
    stream1 | stream2
    stream2.foreach { n =>
      eventReceived ! true
      waitForResult(expected, n)
    }

    def test(n: Int): Unit = {
      eventReceived ! false
      expected ! n
      stream1 ! n
      waitForResult(eventReceived, true)
    }

    test(1)
    test(2)
    test(3)
    test(4)
  }

  test("create an event stream from a signal") {
    val signal = Signal[Int]()
    val stream = EventStream.from(signal)

    val expected = Signal(0)
    val eventReceived = Signal(false)
    stream.foreach { n =>
      eventReceived ! true
      waitForResult(expected, n)
    }

    def test(n: Int): Unit = {
      eventReceived ! false
      expected ! n
      signal ! n
      waitForResult(eventReceived, true)
    }

    test(1)
    test(2)
    test(3)
    test(4)
  }

  test("create an event stream from a future") {
    val promise = Promise[Int]()
    val stream = EventStream.from(promise.future)

    val received = Signal(0)
    stream.foreach { n =>
      received ! n
    }

    promise.success(1)
    waitForResult(received, 1)
  }

  test("create an event stream from a future on a separate execution context") {
    implicit val dq: DispatchQueue = SerialDispatchQueue()
    val promise = Promise[Int]()
    val stream = EventStream.from(promise.future, dq)

    val received = Signal(0)
    stream.foreach { n =>
      received ! n
    }

    promise.success(1)
    waitForResult(received, 1)
  }

  test("ensure mapSync maintains the order of mapped events") {
    implicit val dq: DispatchQueue = UnlimitedDispatchQueue()

    val source = EventStream[Int]()

    val mappedSync = source.mapSync { n =>
      if (n % 2 == 0) Future {
        Thread.sleep(500)
        n + 100
      } else Future {
        n + 100
      }
    }

    val resultsSync = Signal(Seq.empty[Int])

    mappedSync.foreach { n =>
      resultsSync.mutate(_ :+ n)
    }

    source ! 2
    source ! 3
    source ! 4
    source ! 5

    waitForResult(resultsSync, Seq(102, 103, 104, 105))
  }

  test("filter numbers to even and odd") {
    implicit val dq: DispatchQueue = SerialDispatchQueue()

    val numbers = Seq(1, 2, 3, 4, 5, 6, 7, 8, 9)

    val source = EventStream[Int]()
    val evenEvents = source.filter(_ % 2 == 0)
    val oddEvents = source.filter(_ % 2 != 0)

    var evenResults = List[Int]()
    var oddResults = List[Int]()
    val waitForMe = Promise[Unit]()

    def add(n: Int, toEven: Boolean) = {
      if (toEven) evenResults :+= n else oddResults :+= n
      if (evenResults.length + oddResults.length == numbers.length) waitForMe.success(())
    }

    evenEvents.foreach(add(_, toEven = true))
    oddEvents.foreach(add(_, toEven = false))

    numbers.foreach(source ! _)

    result(waitForMe.future)

    assertEquals(evenResults, List(2, 4, 6, 8))
    assertEquals(oddResults, List(1, 3, 5, 7, 9))
  }

  test("collect only odd numbers and add 100 to them") {
    implicit val dq: DispatchQueue = SerialDispatchQueue()

    val numbers = Seq(1, 2, 3, 4, 5, 6, 7, 8, 9)

    val source = EventStream[Int]()
    val oddEvents = source.collect { case n if n % 2 != 0 => n + 100 }

    var oddResults = List[Int]()
    val waitForMe = Promise[Unit]()

    oddEvents.foreach { n =>
      oddResults :+= n
      if (oddResults.length == 5) waitForMe.success(())
    }

    numbers.foreach(source ! _)

    result(waitForMe.future)

    assertEquals(oddResults, List(101, 103, 105, 107, 109))
  }

  test("scan the numbers to create their multiplication") {
    implicit val dq: DispatchQueue = SerialDispatchQueue()

    val numbers = Seq(1, 2, 3, 4, 5, 6, 7, 8, 9)

    val source = EventStream[Int]()
    val scanned = source.scan(1)(_ * _)

    var oddResults = List[Int]()
    val waitForMe = Promise[Unit]()

    scanned.foreach { n =>
      oddResults :+= n
      if (oddResults.length == numbers.length) waitForMe.success(())
    }

    numbers.foreach(source ! _)

    result(waitForMe.future)

    assertEquals(oddResults, List(1, 2, 6, 24, 120, 720, 5040, 40320, 362880))
  }

  test("Take the next event in the event stream as a cancellable future") {
    implicit val dq: DispatchQueue = SerialDispatchQueue()

    val source = EventStream[Int]()
    var results = List[Int]()

    var intercepted = -1
    source.foreach(results :+= _)

    source ! 1
    awaitAllTasks
    source.next.foreach(intercepted = _)

    source ! 2
    awaitAllTasks

    assertEquals(intercepted, 2)
    assertEquals(results, List(1, 2))

    source ! 3
    awaitAllTasks

    assertEquals(intercepted, 2)
    assertEquals(results, List(1, 2, 3))
  }

  test("Turn an event stream of booleans to an event stream of units") {
    implicit val dq: DispatchQueue = SerialDispatchQueue()

    val booleans = List(true, false, true, false, true)

    val source = EventStream[Boolean]()
    var howMuchTrue = 0
    var howMuchFalse = 0

    source.ifTrue.foreach { _ => howMuchTrue += 1 }
    source.ifFalse.foreach { _ => howMuchFalse += 1 }

    booleans.foreach(source ! _)
    awaitAllTasks

    assertEquals(howMuchTrue, 3)
    assertEquals(howMuchFalse, 2)
  }
}
