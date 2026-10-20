# Design Choices

Kignal is a personal learning project with a usable synchronous signals core. The current scope is complete enough to study dependency tracking, caching, propagation, cleanup, and error recovery. Extensions will be considered when Issues describe concrete needs; there is no committed feature roadmap.

## Mutable Graph Internals

The runtime models dependencies with objects and mutable sets. Public factory functions provide the Kotlin API, while internal nodes hold values, caches, dependencies, and execution state.

| Node | Producer: provides a readable value | Consumer: tracks upstream dependencies |
| --- | --- | --- |
| `SignalNode` | Yes | No |
| `ComputedNode` | Yes | Yes |
| `EffectNode` | No | Yes |

`DependencyGraph` maintains both sides of each link and clears old dependencies. `KignalRuntime` manages tracking context, invalidation traversal, and effect flushing. Nodes handle their own value or execution state. This keeps the propagation path in one place and removes duplicated subscription cleanup.

Sets and ordinary objects make the graph easy to inspect. They allocate more than specialized intrusive links or pooled storage might. The project accepts that cost while prioritizing correctness and readability; no performance parity with `alien-signals` is claimed.

## Two-Phase Propagation

Executing effects while recursively notifying nodes allows an effect to run before a different branch has been invalidated. It can then read a new source value alongside an old computed cache, or run multiple times for one source update.

Kignal completes invalidation before executing effects:

```text
source assignment
  -> traverse downstream graph, mark computed nodes, queue effects
  -> flush effects, recompute dirty values when read
```

An explicit work queue avoids recursive invalidation. A visited set limits each producer to one traversal per invalidation pass, and an ordered pending set coalesces effects. Already-dirty computed nodes are still traversed so an earlier failed computation does not prevent downstream consumers from being notified on the next write.

The traversal allocates queue and visited-set storage per changed write. It favors a direct algorithm over persistent graph metadata and additional state flags. Computed evaluation still uses normal nested calls, so the nonrecursive propagation guarantee does not extend to arbitrarily deep computed evaluation.

## Lazy Synchronous Computation

Computed values evaluate on reads. This preserves cached reads and avoids calculating derived values that no caller currently needs. When an effect reads a dirty computed, nested reads naturally obtain upstream values before the dependent calculation finishes; no separate global topological execution order is maintained.

Eagerly recalculating every dirty computed would add work for unused values and require another execution policy. The current model instead requires computed bodies to perform synchronous derivation and keeps side effects in effects.

Source assignments use Kotlin `==` to avoid equal-value notifications. Computed results are not compared to suppress effects: propagating invalidation is simpler than tracking value versions and validating whether each downstream consumer's inputs really changed. The tradeoff is that an effect may rerun even when the derived result is equal.

## Explicit Execution and Lifetime

Effects are synchronous and run immediately at creation. Subsequent executions use the pending queue, including updates caused by effect writes, so an update can settle without recursive effect calls. Effects that write signals can cause more executions in the same flush; deduplication only applies while an effect is waiting.

The queue does not turn a sequence of writes into a transaction. A public batch API, scopes, and pluggable schedulers could be added later if real use cases justify their semantics and maintenance cost.

Lifetime is explicit through `Disposable`. Automatic ownership of nested effects, computed detachment, and cleanup callbacks would introduce more lifetime rules. The current implementation leaves these out; the [runtime contract](runtime-contract.md#lifetime) describes the retention tradeoff and recommended usage.

## Error Recovery

Synchronous errors reach the caller. A failing effect does not prevent independent pending effects from being attempted, and additional errors are preserved as suppressed exceptions. Initial construction failures release the inaccessible effect's dependencies.

A per-effect limit of 100 queued executions bounds repeated feedback in one flush. This is a practical guard rather than a full cycle analysis. It can reject long terminating update sequences, and aborting a flush leaves pending effects unexecuted. State changes are not transactional and are not rolled back.

## Deliberate Scope

The current API focuses on `signal`, `computed`, `effect`, `untracked`, and effect disposal on a single JVM thread. `.value` makes reads and writes explicit while preserving access to the signal object; `invoke()` offers optional read shorthand.

Coroutine resources, Flow and Reactive Streams interop, Reactor, Kotlin Multiplatform, thread safety, and benchmarks remain optional directions. Async derived values would require loading, cancellation, failure, and stale-result semantics beyond a computed's synchronous current value. Such support belongs in separate extension modules if it is ever needed.

Bug reports, questions, and use cases are welcome through [GitHub Issues](https://github.com/origin-coding/kignal/issues). Future work may follow that feedback, depending on project fit and available time.
