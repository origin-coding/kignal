# AGENTS.md

## Project Overview

This project is a Kotlin/JVM learning project for implementing a small fine-grained reactivity system inspired by `alien-signals`.

Tentative project name:

```text
kignal
```

The goal is not to build a production-grade, ultra-optimized reactivity runtime at the beginning. The goal is to learn and implement a clear, usable signals library with understandable internals, good tests, and room for future extension.

Primary goals:

* Implement a Kotlin/JVM signals core.
* Use a multi-module Gradle project structure.
* Start with `core` and synchronous scheduling.
* Keep `computed` strictly synchronous.
* Allow `effect` to support pluggable scheduling.
* Leave coroutine, Flow, Reactive Streams, Reactor, and resource support as later extension modules.
* Use `alien-signals` as a design reference, but do not blindly port every optimization.

---

## Current Development Scope

The first stage should focus on a synchronous core.

Required first-stage features:

```text
signal
computed
effect
dependency tracking
dependency cleanup
synchronous scheduling
dispose
scope
batch
untracked
```

Do not implement coroutine resources, Flow interop, Reactor interop, or Reactive Streams interop in the first stage.

---

## Technology Stack

Use:

```text
Kotlin/JVM
Gradle Kotlin DSL
JUnit 5 or Kotest
JMH for benchmarks, later
```

The initial target is JVM only. Do not design the project as Kotlin Multiplatform in the first version.

Kotlin Multiplatform may be considered only after the JVM core is stable.

---

## Suggested Module Structure

Initial modules:

```text
kignal/
  settings.gradle.kts
  build.gradle.kts

  kignal-core/
  kignal-test/
  kignal-benchmark/
```

Future modules may include:

```text
kignal-coroutines/
kignal-flow/
kignal-reactive-streams/
kignal-reactor/
```

Dependency direction must always be:

```text
kignal-core
   ↑
kignal-coroutines
   ↑
kignal-flow
   ↑
kignal-reactive-streams
   ↑
kignal-reactor
```

The core module must not depend on coroutine, Flow, Reactor, or Reactive Streams libraries.

---

## Core Design Philosophy

The internal implementation should be object-oriented and imperative.

This is intentional.

A signals runtime is fundamentally a mutable dependency graph. It needs nodes, links, dirty flags, subscription lists, cleanup, and disposal. These concepts are easier to implement clearly with mutable objects.

The public API can use Kotlin-friendly functional construction helpers and light DSL syntax.

Recommended split:

```text
Internal implementation:
OOP / mutable graph

Public API:
Kotlin-style factory functions + simple DSL
```

Do not force a purely functional design for the core runtime.

---

## Public API Style

Prefer clear APIs first.

Recommended initial API:

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

The `.value` style should be the default because it is explicit and easy to understand.

Optional syntax sugar can be added later:

```kotlin
val double = computed {
    count() * 2
}
```

This may be implemented via:

```kotlin
operator fun invoke(): T
```

Do not start with delegated property syntax as the primary API.

Avoid making this the first-class API:

```kotlin
var count by signal(1)
```

Delegated properties may be added later as syntax sugar, but they hide the signal object and are not ideal for learning the core model.

---

## Core Interfaces

A reasonable public model may look like this:

```kotlin
interface ReadSignal<out T> {
    val value: T
    operator fun invoke(): T = value
}

interface MutableSignal<T> : ReadSignal<T> {
    override var value: T
}

interface Disposable {
    fun dispose()
}
```

Possible factory functions:

```kotlin
fun <T> signal(initial: T): MutableSignal<T>

fun <T> computed(block: () -> T): ReadSignal<T>

fun effect(block: () -> Unit): Disposable
```

Later:

```kotlin
fun effect(
    scheduler: Scheduler,
    block: () -> Unit
): Disposable
```

---

## Internal Runtime Model

The implementation may be inspired by `alien-signals`.

Useful internal concepts:

```text
ReactiveNode
Link
SignalNode
ComputedNode
EffectNode
Scope
Scheduler
```

The core runtime may use:

```text
dependency links
subscriber links
version numbers
dirty flags
pending flags
tracking context
cleanup before rerun
non-recursive propagation where practical
```

The first implementation does not need to reproduce all low-level performance optimizations.

Do not optimize too early.

Prioritize:

```text
correct behavior
clear dependency tracking
clear cleanup logic
testability
readable implementation
```

Performance optimization can come later.

---

## Computed Design Rules

`computed` must remain synchronous.

A computed value represents:

```text
current value
cached value
synchronous derivation
dependency tracking
lazy recomputation
dirty checking
```

Allowed:

```kotlin
val fullName = computed {
    "${firstName.value} ${lastName.value}"
}
```

Do not support this in core:

```kotlin
val user = computed {
    api.getUser(userId.value) // suspend or async call
}
```

Reasons:

* Computed should have a synchronous current value.
* Async computed introduces loading, error, cancellation, stale result, and race semantics.
* Async derived values should be modeled later as `resource`, not as `computed`.

---

## Effect Design Rules

`effect` may be synchronous at first.

Initial behavior:

```text
effect runs once immediately
effect tracks signals read during execution
effect reruns when dependencies change
effect cleans up old dependencies before rerun
effect can be disposed
```

Initial API:

```kotlin
val stop = effect {
    println(count.value)
}

stop.dispose()
```

Later, effect may support scheduling:

```kotlin
effect(scheduler = scheduler) {
    println(count.value)
}
```

Important distinction:

```text
Allowed later:
asynchronously scheduled effect rerun

Avoid initially:
suspend effect body
```

That means this is acceptable later:

```kotlin
effect(scheduler = CoroutineScheduler(dispatcher = Dispatchers.IO)) {
    println(count.value)
}
```

But this should not be part of the core API:

```kotlin
effectAsync {
    delay(100)
    println(count.value)
}
```

Reason: signal reads after suspension create difficult dependency tracking semantics.

---

## Scheduler Design

The core module should define a minimal scheduler abstraction.

Example:

```kotlin
fun interface Scheduler {
    fun schedule(task: () -> Unit)
}
```

Initial synchronous scheduler:

```kotlin
object ImmediateScheduler : Scheduler {
    override fun schedule(task: () -> Unit) {
        task()
    }
}
```

Future schedulers may include:

```text
QueueScheduler
BatchScheduler
CoroutineScheduler
```

`CoroutineScheduler` must belong to `kignal-coroutines`, not `kignal-core`.

---

## Batch Design

`batch` should group multiple signal writes and flush effects once at the end.

Example:

```kotlin
batch {
    firstName.value = "John"
    lastName.value = "Smith"
}
```

Expected behavior:

```text
signals are updated immediately
computed nodes are marked dirty
effects are queued
effects flush after the batch ends
duplicate effect reruns should be coalesced where practical
```

Batch belongs in `kignal-core`.

---

## Untracked Design

`untracked` allows reading a signal without registering it as a dependency.

Example:

```kotlin
effect {
    println(a.value)

    untracked {
        println(b.value)
    }
}
```

In this example, the effect should depend on `a`, but not on `b`.

`untracked` belongs in `kignal-core`.

---

## Scope Design

Scopes manage lifetime.

A scope can own effects and future resources.

Example:

```kotlin
val scope = ReactiveScope()

scope.effect {
    println(count.value)
}

scope.dispose()
```

When a scope is disposed:

```text
all owned effects are disposed
all owned subscriptions are cleaned up
future coroutine jobs should be cancelled in coroutine modules
```

The first version may implement only synchronous scope disposal.

Coroutine-aware scope belongs to a later extension module.

---

## Threading Model

The first version should assume single-threaded usage unless explicitly documented otherwise.

Do not attempt to make the core fully thread-safe in the first stage.

Avoid adding locks, atomics, or complex concurrent data structures unless there is a clear test case and design decision.

Document the initial assumption:

```text
The core runtime is designed for single-threaded reactive graph execution.
```

Thread-safety may be considered later.

---

## Resources

`resource` is not part of the initial core.

A resource is an async reactive primitive. It can be understood as:

```text
resource = async derived value + loading/error/success state + cancellation/reload behavior
```

It is useful when a value depends on signals but must be loaded asynchronously.

Example future API:

```kotlin
val userId = signal(1)

val user = resource {
    api.getUser(userId.value)
}
```

The return type should not be `ReadSignal<User>` directly. It should be something like:

```kotlin
ReadSignal<ResourceState<User>>
```

Possible state model:

```kotlin
sealed interface ResourceState<out T> {
    data object Idle : ResourceState<Nothing>
    data object Loading : ResourceState<Nothing>
    data class Success<T>(val value: T) : ResourceState<T>
    data class Error(val throwable: Throwable) : ResourceState<Nothing>
}
```

A more practical version may preserve previous values:

```kotlin
sealed interface ResourceState<out T> {
    data object Idle : ResourceState<Nothing>

    data class Loading<T>(
        val previous: T? = null
    ) : ResourceState<T>

    data class Success<T>(
        val value: T
    ) : ResourceState<T>

    data class Error<T>(
        val throwable: Throwable,
        val previous: T? = null
    ) : ResourceState<T>
}
```

Resource behavior should include:

```text
track signal reads before starting async work
reload when dependencies change
cancel stale async work
avoid stale result overwriting newer result
expose loading/error/success state
dispose with scope
```

Resources should be implemented in a future coroutine module:

```text
kignal-coroutines
```

Do not add resources to `kignal-core`.

---

## Coroutine Extension Plan

Future module:

```text
kignal-coroutines
```

Possible features:

```text
CoroutineScheduler
CoroutineReactiveScope
resource
```

This module may depend on:

```text
kignal-core
kotlinx-coroutines-core
```

The core must not depend on this module.

---

## Flow Extension Plan

Future module:

```text
kignal-flow
```

Possible APIs:

```kotlin
fun <T> ReadSignal<T>.asFlow(): Flow<T>

fun <T> Flow<T>.toSignal(
    initial: T,
    scope: CoroutineScope
): ReadSignal<T>
```

Signals and Flow are different models:

```text
Signal:
current value, synchronous read, dependency tracking

Flow:
asynchronous stream, sequential emissions, completion, failure
```

Do not treat Flow as the core abstraction.

Flow interop should remain optional.

---

## Reactive Streams and Reactor Extension Plan

Future modules:

```text
kignal-reactive-streams
kignal-reactor
```

Possible APIs:

```kotlin
fun <T> ReadSignal<T>.asPublisher(): Publisher<T>

fun <T> Publisher<T>.toSignal(
    initial: T,
    scope: CoroutineScope
): ReadSignal<T>
```

For Reactor:

```kotlin
fun <T> ReadSignal<T>.asFlux(): Flux<T>

fun <T> Flux<T>.toSignal(
    initial: T,
    scope: CoroutineScope
): ReadSignal<T>
```

These are not first-stage goals.

Prefer implementing Flow interop first, then bridge Reactive Streams/Reactor through Flow if needed.

---

## Testing Strategy

Testing is more important than early performance tuning.

Recommended testing stack:

```text
JUnit 5 + AssertJ
```

Kotest may be added later, especially for property-based testing.

Important behaviors to test:

```text
signal read and write
computed caching
computed invalidation
effect initial run
effect rerun on dependency change
effect does not rerun when unrelated signal changes
dynamic dependency switching
old dependency cleanup
dispose stops future reruns
batch coalesces reruns
untracked avoids dependency collection
nested computed
nested effect
error handling policy
```

Example dynamic dependency test:

```kotlin
val enabled = signal(true)
val a = signal(1)
val b = signal(10)

val result = computed {
    if (enabled.value) a.value else b.value
}

assertThat(result.value).isEqualTo(1)

enabled.value = false
assertThat(result.value).isEqualTo(10)

a.value = 2
assertThat(result.value).isEqualTo(10)

b.value = 20
assertThat(result.value).isEqualTo(20)
```

This verifies that the old dependency `a` is cleaned up when the computed switches to depend on `b`.

---

## Property-Based Testing

Later, consider property-based testing.

Possible tools:

```text
Kotest Property
jqwik
```

Useful properties:

```text
computed should not recompute if dependencies did not change
disposed effects should never rerun
dynamic dependency cleanup should match a simple reference model
batch should not change final values compared with sequential updates
untracked reads should not add dependencies
```

Property-based tests are especially useful because dependency graph bugs often appear only under complex update sequences.

---

## Benchmarking Strategy

Benchmark later, after correctness is stable.

Use JMH.

Benchmark scenarios:

```text
read plain signal
write signal with no subscribers
write signal with one effect
computed cache hit
computed recomputation
dynamic dependency switch
many subscribers
deep computed chain
batch update
```

Do not use benchmarks to drive the first implementation.

Correctness comes first.

---

## Performance Policy

This project does not need to match `alien-signals` performance initially.

Avoid premature optimizations such as:

```text
object pooling
manual array-based graph storage
unsafe-like tricks
overly complex intrusive lists
lock-free algorithms
```

Acceptable first implementation choices:

```text
classes
nullable references
simple linked structures
clear flags
simple scheduler
simple cleanup
```

Optimization can be done after behavior is well tested.

---

## Error Handling Policy

Define error behavior explicitly.

Questions to answer during implementation:

```text
What happens if computed throws?
What happens if effect throws?
Should effect errors stop the effect?
Should errors propagate to caller?
Should scheduler catch errors?
```

Initial simple policy may be:

```text
computed errors propagate to the reader
effect errors propagate during synchronous execution
scheduler does not swallow exceptions unless explicitly documented
```

Do not silently ignore errors.

---

## Naming Guidelines

Use clear names.

Preferred project name:

```text
kignal
```

Recommended package style:

```kotlin
io.github.origincoding.kignal
```

Possible modules:

```text
kignal-core
kignal-test
kignal-benchmark
kignal-coroutines
kignal-flow
kignal-reactive-streams
kignal-reactor
```

Avoid overly broad names like:

```text
reactive-kt
```

unless the project intentionally grows beyond signals.

---

## What Not To Do

Do not:

```text
do not start with Kotlin Multiplatform
do not put coroutine dependencies in core
do not make computed suspend
do not add resource to core
do not build Flow/Reactor interop in the first stage
do not over-optimize before tests
do not hide the signal object behind delegated properties as the primary API
do not implement a full Rx/Flow replacement
do not make thread-safety claims without tests
```

---

## Recommended Implementation Order

### Stage 0: Project Setup

```text
create Gradle multi-module project
add kignal-core
add kignal-test
configure Kotlin/JVM
configure JUnit 5
```

### Stage 1: Minimal Core

```text
signal
computed
effect
dependency tracking
dependency cleanup
dispose
```

### Stage 2: Correctness Features

```text
dynamic dependency switching
nested computed
nested effect
untracked
batch
```

### Stage 3: Scheduling and Scope

```text
Scheduler
ImmediateScheduler
QueueScheduler if needed
ReactiveScope
scope-owned effects
```

### Stage 4: Tests and Benchmarks

```text
behavior tests
edge case tests
property-based tests
JMH benchmarks
```

### Stage 5: Coroutine Extension

```text
kignal-coroutines
CoroutineScheduler
CoroutineReactiveScope
resource
```

### Stage 6: Flow and Reactive Interop

```text
kignal-flow
kignal-reactive-streams
kignal-reactor
```

---

## Design Summary

The core design should remain small and synchronous.

Recommended mental model:

```text
signal:
mutable source value

computed:
synchronous cached derived value

effect:
side-effecting subscriber with disposable lifetime

scheduler:
controls when effect reruns are executed

scope:
owns disposable reactive resources

resource:
future async extension, not part of core
```

The project should first become a clear and correct Kotlin/JVM implementation of a fine-grained synchronous reactivity system.

Async support should be layered on top later.
