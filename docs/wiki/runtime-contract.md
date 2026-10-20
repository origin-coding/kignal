# Runtime Contract

This page describes the current synchronous core. The [usage examples](usage.md) show the public API, and [design choices](design-choices.md) explain the implementation decisions.

## Public API

| API | Contract |
| --- | --- |
| `signal(initialValue)` | Returns a `MutableSignal<T>` with immediately readable state. |
| `Signal<T>.value` or `Signal<T>()` | Reads the current value and registers a dependency when a consumer is tracking. |
| `MutableSignal<T>.value = value` | Replaces the source value; equal assignments using Kotlin `==` do not propagate. |
| `computed { ... }` | Returns a read-only `Signal<T>` with lazy, synchronous, cached evaluation. |
| `effect { ... }` | Executes once immediately and returns a `Disposable` for future reruns. |
| `Disposable.dispose()` | Stops future executions of that effect and removes its current dependencies and pending queue entry. Repeated calls are safe. |
| `untracked { ... }` | Returns the block result while excluding reads from the surrounding consumer's dependencies. Tracking is restored even when the block throws. |

The graph executes on a single thread. There are no locks, thread-local runtime contexts, concurrent graph guarantees, suspend bodies, or asynchronous scheduling APIs.

## Dependencies and Computed Values

Dependencies come from synchronous reads performed while a computed or effect body executes. Reading a computed makes the caller depend on that computed; the computed tracks its own upstream reads separately. Nested executions restore the previous tracking context when they finish.

Before each execution, the consumer removes its old dependency links and collects a new set. Dependencies therefore follow the latest execution path. An invalidated computed retains its previous dependency set until reevaluation.

Computed values do not evaluate at creation. The first read evaluates and caches the result. Dependency changes mark the cache dirty; the next read synchronously reevaluates it. An unobserved dirty computed remains unevaluated until a read requires it.

There is no computed result equality filter. Changing a source can rerun downstream effects even if a computed returns the same result. Reentering a computed while it is already evaluating throws `IllegalStateException`.

Computed bodies are expected to derive values without signal writes, effect creation, or external side effects. This is a usage convention; the runtime does not comprehensively enforce purity. Mutations inside an object stored in a signal are not tracked; use assignments to represent changes.

## Propagation and Execution Order

For each unequal source assignment:

1. Store the new value immediately.
2. Traverse the affected downstream graph with a work queue. Mark computed nodes dirty and enqueue effects, without executing user functions.
3. After invalidation finishes, flush queued effects. Computed values reevaluate on demand during reads.

For a top-level write, flushing finishes before the setter returns normally. For a write inside an effect, invalidation happens immediately, but the running body finishes before queued effects execute. This also applies to writes during an effect's initial execution. A newly created nested effect still performs its own initial execution immediately.

An effect occupies at most one pending queue entry. It is removed from the pending set before execution, so a subsequent write can queue it again. Multiple paths from one source to an effect are coalesced while that effect is pending.

All affected computed nodes are invalidated before an effect reacts to that source write. This prevents stale-cache reads in source/computed and diamond dependency graphs. It does not provide a transaction across separate writes or across effects that write more signals. Separate top-level assignments flush separately, and an effect can observe an intermediate state before another effect performs a further write.

The pending set preserves insertion order. That order depends on graph traversal and the current dependency links, which can change between runs. Do not use effect ordering as a business dependency; express derived state through computed values. There is no public `batch` or custom scheduler.

## Lifetime

Each effect has an independent lifetime. Keep and dispose every returned handle. Nested effects are not automatically owned by their parent, and recreating one on every outer rerun creates additional subscriptions.

Disposal removes the effect's current dependencies and its pending queue entry. It does not interrupt a body already executing; perform disposal outside that body. There are no scopes or user cleanup callbacks in the current API.

Computed signals have no public disposal API and do not automatically detach from upstream producers when their last downstream effect is disposed. Producers hold strong references to their consumers, so a long-lived source can retain an evaluated computed. Reuse computed values rather than repeatedly creating temporary derived graphs attached to long-lived sources.

## Error Behavior

- A computed exception propagates to the reader. The computation remains unevaluated or dirty; the failed result is not cached as a successful value. Dependencies read before the error remain tracked, and later reads or dependency changes can retry evaluation.
- If effect construction throws, that effect's dependencies and pending entry are removed because the caller cannot receive its disposable handle.
- An effect that throws during a later rerun is not automatically disposed. Dependencies read before the error remain tracked, allowing later updates to rerun it.
- A flush attempts other queued effects after an execution failure, then throws the first error. Additional errors are added as suppressed exceptions unless they are the same object as the first error.
- Assignments and side effects are not rolled back when an error occurs.
- If one effect would execute more than 100 times in a single flush, the runtime records an `IllegalStateException`, stops that flush, and clears pending work. The guard can also reject a long feedback sequence that would eventually settle. It does not dispose existing effects; callers should fix or dispose the feedback loop.
- Flushing and tracking state are restored after failure so later independent updates can proceed.

Initial execution is separate from the queued rerun count. The limit bounds queued executions per effect per flush; it is not proof that every possible cycle or unbounded graph construction will be detected.
