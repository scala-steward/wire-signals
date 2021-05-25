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

import Threading._
import testutils._

import scala.concurrent.Promise

class AggregatingSignalSpec extends munit.FunSuite {

  test("new aggregator, no subscribers") {
    val promise = Promise[Seq[Int]]()
    val updater = EventStream[String]()

    val as = new AggregatingSignal[String, Seq[Int]](
      () => promise.future,
      updater,
      (b, a) => b :+ a.length
    )

    assertEquals(as.value, None)

    promise.success(Seq(42))
    updater ! "meep"

    assertEquals(as.value, None)
  }

  test("one subscriber") {
    val promise = Promise[Seq[Int]]()
    val updater = EventStream[String]()

    val as = new AggregatingSignal[String, Seq[Int]](
      () => promise.future,
      updater,
      (b, a) => b :+ a.length
    )

    val value = Signal(Seq.empty[Int])

    as.foreach { value ! _ }
    waitForResult(value, Seq.empty)
    assertEquals(as.value, None)

    promise.success(Seq(42))

    waitForResult(value, Seq(42))
    waitForResult(as, Seq(42))


    updater ! "meep"

    waitForResult(value, Seq(42, 4))
    waitForResult(as, Seq(42, 4))

    as.unsubscribeAll()

    updater ! "yay"
    awaitAllTasks

    waitForResult(value, Seq(42, 4))
    waitForResult(as, Seq(42, 4))
  }

  test("events while subscribed but still loading") {
    val promise = Promise[Seq[Int]]()
    def loader() = promise.future
    val updater = EventStream[String]()

    val as = new AggregatingSignal[String, Seq[Int]](
      loader _,
      updater,
      (b, a) => b :+ a.length
    )

    val value = Signal(Seq.empty[Int])

    as.foreach { value ! _ }
    waitForResult(value, Seq.empty)
    assertEquals(as.value, None)

    updater ! "meep"
    updater ! "moop"
    updater ! "eek"

    waitForResult(value, Seq.empty)
    assertEquals(as.value, None)

    updater ! "!"
    promise.success(Seq(42))
    updater ! "supercalifragilisticexpialidocious"

    waitForResult(value, Seq(42, 4, 4, 3, 1, 34))
    waitForResult(as, Seq(42, 4, 4, 3, 1, 34))
  }

  test("reload on re-wire"){
    var promise = Promise[Seq[Int]]()
    def loader() = promise.future
    val updater = EventStream[String]()

    val as = new AggregatingSignal[String, Seq[Int]](
      loader _,
      updater,
      (b, a) => b :+ a.length
    )

    val value = Signal(Seq.empty[Int])

    as.foreach { value ! _ }
    promise.success(Seq(42))

    updater ! "wow"
    updater ! "such"
    updater ! "publish"

    waitForResult(value, Seq(42, 3, 4, 7))
    waitForResult(as, Seq(42, 3, 4, 7))

    as.unsubscribeAll()
    awaitAllTasks
    // still holds to the last computed value after unsubscribing
    waitForResult(value, Seq(42, 3, 4, 7))
    waitForResult(as, Seq(42, 3, 4, 7))
    // triggers reload
    updater ! "publisher"
    awaitAllTasks

    // still the old value
    waitForResult(value, Seq(42, 3, 4, 7))
    // a new value after reload
    waitForResult(as, Seq(42, 9))

    promise = Promise[Seq[Int]]()
    as.foreach { value ! _ }

    awaitAllTasks
    waitForResult(value, Seq(42, 3, 4, 7))
    waitForResult(as, Seq(42, 9))

    updater ! "much amaze"

    waitForResult(value, Seq(42, 9, 10))
    waitForResult(as, Seq(42, 9, 10))

    promise.success(Seq(42, 3, 4, 7, 9))

    waitForResult(value, Seq(42, 3, 4, 7, 9, 10))
    waitForResult(as, Seq(42, 3, 4, 7, 9, 10))

    updater ! "much"
    updater ! "amaze"

    waitForResult(value, Seq(42, 3, 4, 7, 9, 10, 4, 5))
    waitForResult(as, Seq(42, 3, 4, 7, 9, 10, 4, 5))
  }
}
