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

import com.wire.signals.testutils.waitForResult

class PartialUpdateSignalSpec extends munit.FunSuite {

  import EventContext.Implicits.global
  import PartialUpdateSignalSpec._

  test("Basic") {

    val original = Signal(Data(0, 0))

    val updates = Signal(Seq.empty[Data])
    original.onPartialUpdate(_.value1).onCurrent { d =>
      updates.mutate(_ :+ d)
    }

    original ! Data(0, 1)

    original ! Data(0, 2)

    original ! Data(1, 2)

    original ! Data(1, 3)

    original ! Data(2, 3)

    waitForResult(updates, Seq(Data(0, 0), Data(1, 2), Data(2, 3)))
  }

  test("New subscribers get latest value even if the select doesn't match") {
    val original = Signal(Data(0, 0))

    original.onPartialUpdate(_.value1).onCurrent { d =>
      assertEquals(d, Data(0, 0))
    }

    original ! Data(0, 1)

    original.onPartialUpdate(_.value1).onCurrent { d =>
      assertEquals(d, Data(0, 1))
    }
  }
}

object PartialUpdateSignalSpec {
  final case class Data(value1: Int, value2: Int)
}
