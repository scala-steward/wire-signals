# Wire Signals
#### or Yet Another Reactive Library for Scala

![Scala CI](https://github.com/wireapp/wire-signals/workflows/Scala%20CI/badge.svg)

About two thirds of Wire Android code is written in Scala making it unique among Android apps - most of them being implemented in Java and/or Kotlin. Wire is a messenger and as such it must be very responsive: it has to quickly react to any events coming from the backend, as well as from the user, and from the Android OS itself. The Wire Android team developed its own implementation of event streams and "signals" - special event streams with caches holding the last propagated value. They proved to be a very flexible and concise way of handling events all over the Scala code in Wire.

Wire Signals are used extensively in our application, from [fetching and decoding data from another device](https://github.com/wireapp/wire-android-sync-engine/blob/develop/zmessaging/src/main/scala/com/waz/service/push/PushService.scala) to [updating the list of messages displayed in a conversation](https://github.com/wireapp/wire-android/blob/develop/app/src/main/scala/com/waz/zclient/messages/MessagesController.scala).

#### How to use
To include `wire-signals` in your project, add this to your library dependencies in sbt:
```
libraryDependencies += "com.wire" %% "wire-signals" % "1.0.0"
```
Currently `wire-signals` work with Scala 2.11 (because Android), 2.12, 2.13, and it's ready for Scala 3.

Similarly, you can add `wire-signals-extensions` to get some additional functionality. The reason for splitting the library into two is that the core library is enough to perform the main task of event streams and signals, which is to transport events across the app. For some users this might be all they need and so they will be interested more in lack of additional dependencies, small codebase, and performance. The core library is dependent in production only on the standard Scala library.


In short, you can create a `SourceSignal` somewhere in the code:
```
val intSignal = Signal(1) // SourceSignal[Int] with the initial value 1
val strSignal = Signal[String]() // initially empty SourceSignal[String]
```

and subscribe it in another place:
```
intSignal.foreach { number => println("number: $number") }
strSignal.foreach { str => println("str: $str") }
```

Now every time you publish something to the signals, the functions you provided above will be executed, just as in case of a regular event stream...
```
scala> intSignal ! 2
number: 2
```
... but if you happen to subscribe to a signal after an event was published, the subscriber will still have access to that event. On the moment of subscription the provided function will be executed with the last event in the signal if there is one. So at this point in the example subscribing to `intSignal` will result in the number being displayed:
```
> intSignal.foreach { number => println("number: $number") }
number: 2
```
but subscribing to `strSignal` will not display anything, because `strSignal` is still empty. Or, if you simply don't need that functionality, you can use a standard `EventStream` instead.

You can also of course `map` and `flatMap` signals, `zip` them, `throttle`, `fold`, or make any future or an event stream into one. With a bit of Scala magic you can even do for-comprehensions:
```
val fooSignal = for {
 number <- intSignal
 str    <- if (number % 3 == 0) Signal.const("Foo") else strSignal
} yield str
```

If you want to know more about how we use it:
* [Scala Docs](https://wire.engineering/wire-signals/api/com/wire/signals/index.html)
* [Wire Signals - Yet Another Event Streams Library](https://youtu.be/IgKjd_fhM0M) - a video based on a talk from [Functional Scala 2020](https://www.functionalscala.com/). It discusses the whole theory behind event streams and provides examples from the Wire Signals library.
* [An article on Medium.com](https://makingthematrix.medium.com/wire-signals-81918bbcc07f?source=friends_link&sk=948c6f03e507e6f0188737711511a4b0) - basically a transcript of the video above.
* Another article on [how Scala futures and Wire Signals interact](https://github.com/wireapp/wire-signals/wiki/Futures-in-the-context-of-Wire-Signals)
* ... and a slightly outdated video about how we use it at Wire Android: [Scala on Wire](https://www.youtube.com/watch?v=dnsyd-h5piI)



### Contributors 

* Dean Cook (https://github.com/deanrobertcook)
* Maciej Gorywoda (https://github.com/makinthematrix)
* Zbigniew Szymański (https://github.com/zbsz)

