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

import com.wire.signals.testutils.{result, waitForResult}

class ScanSignalSpec extends munit.FunSuite {
  test("Normal scanning") {
    val received = Signal(Seq.empty[Int])
    val capture: Int => Unit = { value => received.mutate(_ :+ value) }

    val s = Signal(1)
    val scanned = s.scan(0)(_ + _)
    assertEquals(result(scanned.future), 1)

    scanned.onCurrent(capture)
    assertEquals(result(scanned.future), 1)
    waitForResult(received, Seq(1))

    Seq(2, 3, 1).foreach(s ! _)

    waitForResult(received, Seq(1, 3, 6, 7))
    assertEquals(result(scanned.future), 7)
  }

  test("disable autowiring when fetching current value") {
    val s = Signal(1)
    val scanned = s.scan(0)(_ + _)
    assertEquals(result(scanned.future), 1)

    Seq(2, 3, 1).foreach(s ! _)
    waitForResult(s, 1)
    assertEquals(result(scanned.future), 7)
  }

  test("Chained scanning") {
    val received = Signal(Seq.empty[Int])
    val capture: Int => Unit = { value => received.mutate(_ :+ value) }

    val s = Signal(1)
    val scanned = s.scan(0)(_ + _).scan(1)(_ * _)
    assertEquals(result(scanned.future), 1)

    scanned.onCurrent(capture)
    Seq(2, 3, 1).foreach(s ! _)
    waitForResult(s, 1)

    assertEquals(result(scanned.future), 3 * 6 *7)
    waitForResult(received, Seq(1, 3, 3 * 6, 3 * 6 * 7))
  }

  test("No subscribers will be left behind") {
    val received = Signal(Seq.empty[Int])
    val capture: Int => Unit = { value => received.mutate(_ :+ value) }

    val s = Signal(1)
    val scanned = s.scan(0)(_ + _)
    val sub = scanned.onCurrent(capture)
    Seq(2, 3) foreach (s ! _)
    assert(s.hasSubscribers)
    assert(scanned.hasSubscribers)
    sub.destroy()
    assert(!s.hasSubscribers)
    assert(!scanned.hasSubscribers)
    s ! 4
    waitForResult(received, Seq(1, 3, 6))
  }
}
