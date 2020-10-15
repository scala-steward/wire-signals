package com.wire.signals

import scala.concurrent.ExecutionContext

/** The usual entry point for publishing events.
  *
  * Create a new source stream either using the default constructor or the `EventStream.apply[V]()` method. The source stream exposes
  * methods you can use for publishing new events. Then you can combine it with other event streams and finally subscribe a function
  * to it which will receive the resulting events.
  *
  * @tparam E the type of the event
  */
class SourceStream[E] extends EventStream[E] {
  /** Publishes the event to all subscribers.
    *
    * @see [[EventStream.publish]]
    *
    * The original `publish` method of the [[EventStream]] class is `protected` to ensure that intermediate event streams - those created
    * by methods like `map`, `flatMap`, `filter`, etc. - will not be used to directly publish events to them. The source stream
    * exposes this method for public use.
    *
    * @param event The event to be published.
    */
  override def publish(event: E): Unit = dispatch(event, None)

  /** Publishes the event to all subscriber, using the given execution context.
    *
    * @see [[EventStream.publish]]
    *
    * @param event The event to be published.
    * @param ec The execution context used for dispatching. The default implementation ensures that if `ec` is the same as
    *           the execution context used to register the subscriber, the subscriber will be called immediately. Otherwise,
    *           a future working in the subscriber's execution context will be created and `ec` will be ignored.
    */
  def publish(event: E, ec: ExecutionContext): Unit = dispatch(event, Some(ec))

  /** An alias for the `publish` method. */
  @inline final def !(event: E): Unit = publish(event)
}
