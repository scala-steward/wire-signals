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

import com.wire.signals.testutils.result
import utils._
import org.scalatest.{BeforeAndAfter, FeatureSpec, Matchers, OptionValues}

import scala.language.postfixOps
import scala.concurrent.duration._
import scala.concurrent.Promise

class EventStreamSpec extends FeatureSpec with Matchers with OptionValues with BeforeAndAfter {

  import EventContext.Implicits.global

  feature("FlatMapLatest") {

    scenario("unsubscribe from source and current mapped signal on onUnwire") {
      val a: SourceStream[Int] = EventStream()
      val b: SourceStream[Int] = EventStream()

      val subscription = a.flatMap(_ => b) { _ => }
      a ! 1

      withClue("mapped event stream should have subscriber after element emitting from source event stream.") {
        b.hasListeners shouldBe true
      }

      subscription.unsubscribe()

      withClue("source event stream should have no subscribers after onUnwire was called on FlatMapLatestEventStream") {
        a.hasListeners shouldBe false
      }

      withClue("mapped event stream should have no subscribers after onUnwire was called on FlatMapLatestEventStream") {
        b.hasListeners shouldBe false
      }
    }

    scenario("discard old mapped event stream when new element emitted from source event stream") {
      val a: SourceStream[String] = EventStream()
      val b: SourceStream[String] = EventStream()
      val c: SourceStream[String] = EventStream()

      var flatMapCalledCount = 0
      var lastReceivedElement: Option[String] = None
      val subscription = a.flatMap { _ =>
        returning(if (flatMapCalledCount == 0) b else c) { _ => flatMapCalledCount += 1 }
      } { elem => lastReceivedElement = Some(elem) }

      a ! "a"

      withClue("mapped event stream 'b' should have subscriber after first element emitting from source event stream.") {
        b.hasListeners shouldBe true
      }

      b ! "b"

      withClue("flatMapLatest event stream should provide events emitted from mapped signal 'b'") {
        lastReceivedElement shouldBe Some("b")
      }

      a ! "a"

      withClue("mapped event stream 'b' should have no subscribers after second element emitting from source event stream.") {
        b.hasListeners shouldBe false
      }

      withClue("mapped event stream 'c' should have subscriber after second element emitting from source event stream.") {
        c.hasListeners shouldBe true
      }

      c ! "c"
      b ! "b"

      withClue("flatMapLatest event stream should provide events emitted from mapped signal 'c'") {
        lastReceivedElement shouldBe Some("c")
      }

      subscription.unsubscribe()
    }

  }

  feature("EventStream from a future") {
    scenario("emit an event when a future is successfully completed") {
      implicit val dq: DispatchQueue = SerialDispatchQueue()
      val promise = Promise[Int]()
      val resPromise = Promise[Int]()

      EventStream.from(promise.future){ event =>
        event shouldEqual 1
        resPromise.success(event)
      }

      testutils.withDelay(promise.success(1))

      testutils.result(resPromise.future) shouldEqual 1
    }

    scenario("don't emit an event when a future is completed with a failure") {
      val promise = Promise[Int]()
      val resPromise = Promise[Int]()

      EventStream.from(promise.future) { event => resPromise.success(event) }

      promise.failure(new IllegalArgumentException)

      testutils.tryResult(resPromise.future).isFailure shouldBe true
    }

    scenario("emit an event after delay by wrapping a cancellable future") {
      val t = System.currentTimeMillis()
      val ev = for {
        _ <- EventStream.from(CancellableFuture.delay(1 seconds))
        x = System.currentTimeMillis() - t
      } yield x

      result(ev.future) > 1000 shouldBe true
    }
  }

  case object FakeError extends Throwable
}
