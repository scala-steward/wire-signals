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

import java.util.concurrent.atomic.AtomicReference

import testutils._

final case class Follower[A](signal: Signal[A]) {
  private val receivedValues = new AtomicReference(Vector.empty[A])

  def received: Vector[A] = receivedValues.get

  def lastReceived: Option[A] = received.lastOption

  def receive(a: A): Unit = compareAndSet(receivedValues)(_ :+ a)

  def subscribed(implicit ec: EventContext = EventContext.Global): Follower[A] = returning(this)(_ => signal {
    receive
  })
}
