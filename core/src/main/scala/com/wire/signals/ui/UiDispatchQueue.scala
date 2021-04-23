package com.wire.signals.ui

import com.wire.signals.{DispatchQueue, EventContext, EventStream, Signal, Subscription}

/** This is a utility class to help you set up wire-signals to transport events between the default execution context
  * (`Threading.defaultContext`) and other custom contexts, and a secondary execution context usually associated with GUI.
  * On platforms such as Android or JavaFX, a `Runnable` task that involves changes to the app's GUI requires to be run
  * in a special execution context - otherwise it will either not work, will result in errors, or may even crash the app.
  * Your platform should provide you with a method which you can call with the given task to execute it properly.
  * If that method is of the type `Runnable => Unit` (or if you can wrap it in a function of this type) you can pass it
  * to `UiDispatchQueue` in the app's initialization code. Later, `UiDispatchQueue` will let you use extension methods
  * `.onUi` on event streams and signals. They work exactly like `.foreach` but they will run the subscriber code in
  * your GUI platform execution context.
  *
  * Examples:
  *
  * ### Android initialization:
  *
  * ```
  * import android.os.{Handler, Looper}
  * import com.wire.signals.ui.UiDispatchQueue
  *
  * val handler = new Handler(Looper.getMainLooper)
  * UiDispatchQueue.setUi(handler.post)
  * ```
  *
  * ### JavaFX initialization:
  *
  * ```
  * import javafx.application.Platform
  * import com.wire.signals.ui.UiDispatchQueue
  *
  * UiDispatchQueue.setUi(Platform.runLater)
  *```
  *
  * ### Usage in all cases:
  *
  * ```
  * import com.wire.signals.ui.UiDispatchQueue._
  *
  * signal ! true
  * signal.onUi { value => ... }
  * Future { ... }(Ui)
  * ```
  *
  * @param runUiWith A function from the GUI platform you're working with that will execute a given task in the GUI context
  */
final class UiDispatchQueue(private val runUiWith: Runnable => Unit) extends DispatchQueue {
  override def execute(runnable: Runnable): Unit = runUiWith(runnable)
}

object UiDispatchQueue {
  private val Empty: DispatchQueue = new UiDispatchQueue(_ => ())
  private var _ui: DispatchQueue = Empty

  object Implicit {
    implicit def Ui: DispatchQueue = _ui
  }

  def Ui: DispatchQueue = _ui

  def setUi(ui: DispatchQueue): Unit = this._ui = ui

  def setUi(runUiWith: Runnable => Unit): Unit = this._ui = new UiDispatchQueue(runUiWith)

  def clearUi(): Unit = this._ui = Empty

  implicit final class RichSignal[V](val signal: Signal[V]) extends AnyVal {
    def onUi(subscriber: V => Unit)(implicit context: EventContext = EventContext.Global): Subscription =
      signal.on(_ui)(subscriber)(context)
  }

  implicit final class RichEventStream[E](val stream: EventStream[E]) extends AnyVal {
    def onUi(subscriber: E => Unit)(implicit context: EventContext = EventContext.Global): Subscription =
      stream.on(_ui)(subscriber)(context)
  }
}