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

Each signal write has two synchronous phases:

1. The runtime traverses all affected downstream nodes using a work queue. Computed signals are marked dirty and effects are added to an ordered, deduplicated pending set. No computed or effect body runs during this phase.
2. Once invalidation finishes, the runtime flushes pending effects. Computed signals recompute lazily when read, including reads made by effects. A top-level write returns after the pending effects finish, or throws if execution fails.

Effects run once immediately when created. Writes inside an effect invalidate downstream nodes immediately, but queued effects wait until the current effect body returns. A pending effect is queued only once; an effect that has already run can be queued again by a later write. This does not make several separate top-level writes a public `batch`.

Internally, signals are `Producer` nodes, effects are `Consumer` nodes, and computed signals implement both roles. Dependency links and cleanup are maintained in one place. Old dependencies are removed before recomputation or rerun. Computed bodies should only derive values, without writing signals or creating effects.

## Threading

`kignal-core` is currently designed for single-threaded reactive graph execution.

It does not provide thread-safety guarantees. Avoid reading or mutating the same reactive graph concurrently from multiple threads unless a future version explicitly documents support for that behavior.

## Error Handling

The current error policy is simple and synchronous:

- computed errors propagate to the reader of `.value`
- effect errors propagate during initial execution
- effect errors propagate during synchronous reruns triggered by signal writes

During an effect flush, the runtime continues running other pending effects after an effect fails, then throws the first error. Additional errors are attached as suppressed exceptions. Failed initial effect construction releases its subscriptions. A computed that throws stays dirty and can be retried on a later read or dependency change.

To stop unbounded feedback updates, a flush throws if one effect would run more than 100 times. Pending work is cleared after this limit is reached, and runtime flushing state is restored. The limit also applies to long feedback sequences that would eventually settle.

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
