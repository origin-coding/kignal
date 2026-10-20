# Usage Examples

These examples use the synchronous `kignal-core` API. Each Kotlin block is an independent example, with its own imports. See the [runtime contract](runtime-contract.md) for the precise behavior.

## Signal Values and Equality

```kotlin
import io.github.origincoding.kignal.effect
import io.github.origincoding.kignal.signal

val name = signal("Ada")
val seen = mutableListOf<String>()
val stop = effect { seen += name.value }

name.value = "Ada" // Equal assignment: no rerun.
name.value = "Grace"
check(name() == "Grace") // invoke() is a read, just like .value.
check(seen == listOf("Ada", "Grace"))
stop.dispose()
```

Signal assignments use Kotlin `==`. Changes inside a mutable object are not tracked. For collection state, assigning a new immutable value makes the change explicit, for example `items.value = items.value + "new item"`.

## Lazy Computed Values

```kotlin
import io.github.origincoding.kignal.computed
import io.github.origincoding.kignal.signal

val count = signal(1)
var calculations = 0 // Instrumentation to demonstrate caching.
val double = computed {
    calculations++
    count.value * 2
}

check(calculations == 0)
check(double.value == 2)
check(double.value == 2)
check(calculations == 1)

count.value = 2
check(calculations == 1) // Invalidated, but not read yet.
check(double.value == 4)
check(calculations == 2)
```

Keep real computed bodies limited to synchronous derivation. Use effects for external side effects. Upstream invalidation can rerun an effect even when the newly computed result equals the previous result; computed result equality is not used to filter effects.

## Dynamic Dependencies

```kotlin
import io.github.origincoding.kignal.computed
import io.github.origincoding.kignal.effect
import io.github.origincoding.kignal.signal

val useFirst = signal(true)
val first = signal(1)
val second = signal(10)
val selected = computed {
    if (useFirst.value) first.value else second.value
}
val seen = mutableListOf<Int>()
val stop = effect { seen += selected.value }

useFirst.value = false
first.value = 2 // No longer a dependency of selected after recomputation.
second.value = 20

check(seen == listOf(1, 10, 20))
stop.dispose()
```

Only reads from the latest execution are retained as dependencies. A dirty computed updates its dependency set when it is next evaluated.

## Untracked Reads

```kotlin
import io.github.origincoding.kignal.effect
import io.github.origincoding.kignal.signal
import io.github.origincoding.kignal.untracked

val count = signal(1)
val label = signal("initial")
val seen = mutableListOf<String>()
val stop = effect {
    val value = count.value
    val context = untracked { label.value }
    seen += "$context: $value"
}

label.value = "latest" // Does not trigger the effect.
count.value = 2 // The rerun reads the latest label.

check(seen == listOf("initial: 1", "latest: 2"))
stop.dispose()
```

`untracked` excludes reads from the surrounding consumer's dependencies. It does not freeze values, delay writes, or prevent a computed from tracking its own dependencies when it evaluates inside the block.

## Effect Lifetime

```kotlin
import io.github.origincoding.kignal.effect
import io.github.origincoding.kignal.signal

val count = signal(0)
val seen = mutableListOf<Int>()
val stop = effect { seen += count.value } // Runs immediately.

try {
    count.value = 1
} finally {
    stop.dispose()
}

stop.dispose() // Repeated disposal is safe.
count.value = 2
check(seen == listOf(0, 1))
```

Keep the returned `Disposable` and dispose it when the owning component or operation ends. Effects created inside another effect have independent lifetimes; dispose each explicitly. Disposing an outer effect does not dispose nested effects.

## Synchronous Errors and Recovery

```kotlin
import io.github.origincoding.kignal.computed
import io.github.origincoding.kignal.effect
import io.github.origincoding.kignal.signal

val count = signal(1)
val positiveDouble = computed {
    check(count.value > 0) { "count must be positive" }
    count.value * 2
}
val seen = mutableListOf<Int>()
val stop = effect { seen += positiveDouble.value }

val failure = runCatching { count.value = -1 }.exceptionOrNull()
check(failure is IllegalStateException)
check(count.value == -1) // Writes are not rolled back on failure.

count.value = 2 // The failed computed can be evaluated again.
check(seen == listOf(2, 4))
stop.dispose()
```

An effect that fails during a rerun remains subscribed to the dependencies it read before the failure. Other queued effects are attempted before the first error is rethrown. See [error behavior](runtime-contract.md#error-behavior) for construction failures and cycle limits.
