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

trait Observable[Listener] {

  private object lock

  private var autowiring = true
  @volatile private[signals] var wired = false
  @volatile private var listeners = Set.empty[Listener]

  protected def onWire(): Unit

  protected def onUnwire(): Unit

  private[signals] def subscribe(listener: Listener): Unit = lock.synchronized {
    listeners += listener
    if (!wired) {
      wired = true
      onWire()
    }
  }

  private[signals] def unsubscribe(listener: Listener): Unit = lock.synchronized {
    listeners -= listener
    if (wired && autowiring && listeners.isEmpty) {
      wired = false
      onUnwire()
    }
  }

  private[signals] def notifyListeners(invoke: Listener => Unit): Unit = lock.synchronized {
    listeners.foreach(invoke)
  }

  private[signals] def hasListeners = lock.synchronized { listeners.nonEmpty }

  def unsubscribeAll(): Unit = lock.synchronized {
    listeners = Set.empty
    if (wired && autowiring) {
      wired = false
      onUnwire()
    }
  }

  def disableAutowiring(): this.type = lock.synchronized {
    autowiring = false
    if (!wired) {
      wired = true
      onWire()
    }
    this
  }
}
