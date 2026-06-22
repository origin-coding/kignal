# Kignal

Kignal is a Kotlin/JVM learning project for building a small fine-grained reactivity runtime inspired by signals libraries such as `alien-signals`.

The first MVP focuses on a clear synchronous core instead of production-grade optimization.

## MVP Scope

The current core API includes:

```kotlin
val count = signal(1)

val double = computed {
    count.value * 2
}

val stop = effect {
    println(double.value)
}

count.value = 2
stop.dispose()
```

Included in the MVP:

- `signal`
- `computed`
- `effect`
- dependency tracking
- dependency cleanup
- effect disposal
- `untracked`

The public signal interface is named `Signal<T>`:

```kotlin
interface Signal<out T> {
    val value: T
    operator fun invoke(): T = value
}

interface MutableSignal<T> : Signal<T> {
    override var value: T
}
```

## Runtime Model

Kignal's core runtime is an imperative mutable dependency graph. Signals store source values, computed signals cache synchronous derived values, and effects subscribe to the signals they read while running.

When a dependency changes:

- computed signals are marked dirty and recompute lazily on the next read
- effects rerun synchronously
- old dependencies are cleaned up before recomputation or rerun

## Threading

`kignal-core` is currently designed for single-threaded reactive graph execution.

It does not provide thread-safety guarantees. Avoid reading or mutating the same reactive graph concurrently from multiple threads unless a future version explicitly documents support for that behavior.

## Error Handling

The current error policy is simple and synchronous:

- computed errors propagate to the reader of `.value`
- effect errors propagate during initial execution
- effect errors propagate during synchronous reruns triggered by signal writes

Errors are not swallowed by the runtime.

## Non-Goals For The Current Core

The current MVP does not include:

- `batch`
- scopes
- scheduler abstraction
- asynchronous effects
- coroutine support
- Flow interop
- Reactive Streams or Reactor interop
- resources
- delegated property syntax
- Kotlin Multiplatform
- thread-safety guarantees
- JMH benchmarks
