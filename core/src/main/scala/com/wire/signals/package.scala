package com.wire

package object signals {
  /*
  This is an equivalent of the `tap` method in Scala 2.13, but wire-signals must be compatible also with 2.11
   */
  @inline private[signals] def returning[A](a: A)(body: A => Unit): A = {
    body(a)
    a
  }
}
