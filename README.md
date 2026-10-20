# Kignal

Kignal is a small, synchronous Kotlin/JVM signals library and personal learning project inspired by `alien-signals`.

The current core provides mutable signals, lazy computed values, effects, dynamic dependency tracking and cleanup, effect disposal, and `untracked` reads. The project focuses on understandable internals and tested behavior. This synchronous core is the current project scope; additional features will be considered when there is a concrete use case.

## Quick Start

The public API lives in `io.github.origincoding.kignal`:

```kotlin
import io.github.origincoding.kignal.computed
import io.github.origincoding.kignal.effect
import io.github.origincoding.kignal.signal

val count = signal(1)
val double = computed {
    count.value * 2
}

val stop = effect {
    println("${count.value}, ${double.value}")
}
// Prints: 1, 2

count.value = 2
// Prints: 2, 4

stop.dispose()
count.value = 3
// The disposed effect does not run.
check(double.value == 6)
```

Use `.value` for explicit reads and writes. `count()` is also available as shorthand for reading. `computed` returns a read-only `Signal<T>`; `signal` returns a `MutableSignal<T>`.

## Documentation

- [Usage examples](docs/wiki/usage.md): caching, dynamic dependencies, untracked reads, disposal, and errors.
- [Runtime contract](docs/wiki/runtime-contract.md): equality, propagation, execution order, lifetime, and error behavior.
- [Design choices](docs/wiki/design-choices.md): the graph model, two-phase propagation, lazy computation, and scope decisions.
- [Original MVP plan](docs/dev/2026-06-22-1.0-mvp.md): the historical implementation target.

## Execution Model

The core runtime is designed for single-threaded reactive graph execution. Signal reads, computed functions, and effect bodies are synchronous. Computed bodies should only derive values, without writing signals or creating effects.

Each changed signal write has two phases:

1. Traverse all affected downstream nodes, mark computed values dirty, and queue effects. No computed or effect body runs during invalidation.
2. Run pending effects. Dirty computed values recalculate when read. A top-level write returns when the pending effects finish, or throws if execution fails.

An effect runs immediately when created. Writes inside an effect invalidate downstream nodes immediately, while queued effects wait until the current effect body returns. An effect is queued once while pending, and can be queued again after it has run. Separate top-level writes flush separately.

Errors propagate synchronously. A flush attempts other pending effects after a failure, then throws the first error with additional errors attached as suppressed exceptions. A flush stops if one effect would run more than 100 times. See the [runtime contract](docs/wiki/runtime-contract.md) for recovery and lifetime details.

## Build and Verify

The current build uses a JDK 25 toolchain and the checked-in Gradle Wrapper. From the repository root, run:

```shell
# Windows PowerShell
.\gradlew.bat check
.\gradlew.bat build
```

On macOS or Linux, use `./gradlew check` and `./gradlew build`. To use the core from another module in this checkout, add `implementation(project(":kignal-core"))` to that module's dependencies. The commands above build and verify the local project; they do not publish an artifact.

## Project Scope

The current project does not include `batch`, scopes, custom schedulers, coroutines, Flow, Reactive Streams, Reactor, async resources, delegated properties, Kotlin Multiplatform, or benchmarks. These are optional future directions rather than a committed roadmap. The core has no thread-safety guarantees.

## Feedback and Future Development

Issues are welcome! Please [open an Issue](https://github.com/origin-coding/kignal/issues) for bugs, questions, or feature requests. For a bug, include a small reproducer and the expected and actual behavior. For a feature request, describe the use case you want to support.

I may use Issues to guide future development, depending on the use case, project fit, and available time. Submitting an Issue does not guarantee implementation or a release date.
