package com.wire.signals

import java.util.concurrent.atomic.AtomicReference
import com.wire.signals.utils._
import org.threeten.bp.{Duration, Instant}

import java.util.Random
import scala.concurrent.duration.{FiniteDuration, _}
import scala.annotation.tailrec
import scala.concurrent.{Await, ExecutionContext, Future, TimeoutException}
import scala.util.{Failure, Try}

package object testutils {
  sealed trait Roughly[V] {
    type Tolerance
    def roughlyEquals(other: V)(implicit tolerance: Tolerance): Boolean
  }

  object Roughly extends {
    implicit class RoughlyInstant(val instant: Instant) extends Roughly[Instant] {
      override type Tolerance = Long
      override def roughlyEquals(other: Instant)(implicit tolerance: Long): Boolean =
        this.instant.toEpochMilli >= other.toEpochMilli - tolerance &&
          this.instant.toEpochMilli <= other.toEpochMilli + tolerance
    }
  }

  private val localRandom = new ThreadLocal[Random] {
    override def initialValue: Random = new Random
  }

  def random: Random = localRandom.get

  implicit class SignalToSink[A](val signal: Signal[A]) extends AnyVal {
    def sink: SignalSink[A] = returning(new SignalSink[A])(_.subscribe(signal)(EventContext.Global))
  }

  class SignalSink[A] {
    @volatile private var sub = Option.empty[Subscription]

    def subscribe(s: Signal[A])(implicit ctx: EventContext = EventContext.Global): Unit = sub = Some(s(v => value = Some(v)))

    def unsubscribe(): Unit = sub.foreach { s =>
      s.destroy()
      sub = None
    }

    @volatile private[testutils] var value = Option.empty[A]

    def current: Option[A] = value
  }

  implicit class EnrichedInt(val a: Int) extends AnyVal {
    def times(f: => Unit): Unit = (1 to a).foreach(_ => f)
  }

  implicit class RichInstant(val a: Instant) extends AnyVal {
    def until(b: Instant): Duration = Duration.ofMillis(b.toEpochMilli - a.toEpochMilli)
  }

  implicit class RichOption[A](val opt: Option[A]) extends AnyVal {
    @inline final def fold2[B](ifEmpty: => B, f: A => B): B = if (opt.isEmpty) ifEmpty else f(opt.get)
    // option's catamorphism with better type inference properties than the one provided by the std lib
  }

  @tailrec
  def compareAndSet[A](ref: AtomicReference[A])(updater: A => A): A = {
    val current = ref.get
    val updated = updater(current)
    if (ref.compareAndSet(current, updated)) updated
    else compareAndSet(ref)(updater)
  }

  def withDelay[T](body: => T, delay: FiniteDuration = 300.millis)(implicit ec: ExecutionContext): CancellableFuture[T] =
    CancellableFuture.delayed(delay)(body)

  val DefaultTimeout: FiniteDuration = 5.seconds

  def result[A](future: Future[A])(implicit duration: FiniteDuration = DefaultTimeout): A =
    Await.result(future, duration)

  def tryResult[A](future: Future[A])(implicit duration: FiniteDuration = DefaultTimeout): Try[A] =
    try {
      Try(Await.result(future, duration))
    } catch {
      case t: Throwable => Failure(t)
    }

  /**
    * Very useful for checking that something DOESN'T happen (e.g., ensure that a signal doesn't get updated after
    * performing a series of actions)
    */
  def awaitAllTasks(implicit timeout: FiniteDuration = DefaultTimeout, dq: DispatchQueue): Unit = {
    if (!tasksCompletedAfterWait)
      throw new TimeoutException(s"Background tasks didn't complete in ${timeout.toSeconds} seconds")
  }

  def tasksRemaining(implicit dq: DispatchQueue): Boolean = dq.hasRemainingTasks

  private def tasksCompletedAfterWait(implicit timeout: FiniteDuration = DefaultTimeout, dq: DispatchQueue) = {
    val start = Instant.now
    val before = start.plusMillis(timeout.toMillis)
    while(tasksRemaining && Instant.now().isBefore(before)) Thread.sleep(10)
    !tasksRemaining
  }

  def andThen(millis: Long = 100): Unit = Thread.sleep(millis)
}
