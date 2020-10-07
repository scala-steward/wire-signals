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

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest._

class EventContextSpec extends FeatureSpec with Matchers with BeforeAndAfter with TableDrivenPropertyChecks {
  test =>
  private var received = Seq[Int]()
  private val capture = (value: Int) => received = received :+ value

  before {
    received = Seq[Int]()
  }

  feature("Event context lifecycle") {
    scenario("Pausing, resuming and destroying the global event context") {
      implicit val ec: EventContext = EventContext.Global
      val s = Signal(1)
      s(capture)

      ec.isContextStarted shouldEqual true
      s.hasSubscribers shouldEqual true

      ec.stop()
      s ! 2
      ec.isContextStarted shouldEqual true
      s.hasSubscribers shouldEqual true

      ec.start()
      s ! 3
      ec.isContextStarted shouldEqual true
      s.hasSubscribers shouldEqual true

      ec.destroy()
      s ! 4
      ec.isContextStarted shouldEqual true
      s.hasSubscribers shouldEqual true

      received shouldEqual Seq(1, 2, 3, 4)
    }

    scenario("Pausing, resuming and destroying a normal event context") {
      implicit val ec: EventContext = EventContext()

      ec.isContextStarted shouldEqual true
      ec.stop()
      ec.isContextStarted shouldEqual false

      val s = Signal(0)
      s(capture)

      s.hasSubscribers shouldEqual false
      Seq(1, 2) foreach (s ! _)

      ec.start()
      s ! 3
      ec.isContextStarted shouldEqual true
      s.hasSubscribers shouldEqual true

      ec.stop()
      Seq(4, 5) foreach (s ! _)
      ec.isContextStarted shouldEqual false
      s.hasSubscribers shouldEqual false

      ec.start()
      Seq(6, 7) foreach (s ! _)
      ec.isContextStarted shouldEqual true
      s.hasSubscribers shouldEqual true

      ec.destroy()
      Seq(8, 9) foreach (s ! _)
      ec.isContextStarted shouldEqual false
      s.hasSubscribers shouldEqual false

      received shouldEqual Seq(2, 3, 5, 6, 7)
    }

    scenario("Pausing, resuming and destroying a normal event context, but with forced event sources") {
      implicit val ec: EventContext = EventContext()
      val s = new SourceSignal[Int](Some(0)) with ForcedEventSource[Int]
      s(capture)

      s.hasSubscribers shouldEqual true
      Seq(1, 2) foreach (s ! _)

      ec.start()
      s ! 3
      s.hasSubscribers shouldEqual true

      ec.stop()
      Seq(4, 5) foreach (s ! _)
      s.hasSubscribers shouldEqual true

      ec.start()
      Seq(6, 7) foreach (s ! _)
      s.hasSubscribers shouldEqual true

      ec.destroy()
      Seq(8, 9) foreach (s ! _)
      s.hasSubscribers shouldEqual false

      received shouldEqual Seq(0, 1, 2, 3, 4, 5, 6, 7)
    }
  }
}
