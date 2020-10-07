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

import org.scalatest.{FeatureSpec, Matchers}
import testutils._

class ButtonSignalSpec extends FeatureSpec with Matchers {

  scenario("Simple button press") {

    val service = Signal.const("service")
    val buttonState = Signal(false)


    val button = ButtonSignal(service, buttonState)( {
      (_, state) => {
        buttonState ! !state
      }
    }).disableAutowiring()

    button.press()
    result(button.filter(_ == true).head)

    button.press()
    result(button.filter(_ == false).head)
  }

  scenario("OnClick should not be performed on unwired signal") {
    val service = Signal.const("service")
    val buttonState = Signal(false)

    val button = new ButtonSignal(service, buttonState)( {
      (_, state) => {
        buttonState ! !state
      }
    })

    button.press()
    result(button.filter(_ == false).head)
  }

}
