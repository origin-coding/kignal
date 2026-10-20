package io.github.origincoding.kignal.internal

internal object KignalRuntime {
    private const val MAX_EFFECT_RUNS_PER_FLUSH = 100

    private var activeConsumer: Consumer? = null
    private val pendingEffects = LinkedHashSet<EffectNode>()
    private var flushing = false
    private var initialEffectDepth = 0

    fun trackRead(producer: Producer) {
        activeConsumer?.let { consumer -> DependencyGraph.link(producer, consumer) }
    }

    fun invalidate(producer: Producer) {
        val pendingProducers = ArrayDeque<Producer>()
        val visited = HashSet<Producer>()
        pendingProducers.addLast(producer)

        // No user code runs here: finish invalidating every branch before flushing.
        while (pendingProducers.isNotEmpty()) {
            val current = pendingProducers.removeFirst()
            if (!visited.add(current)) continue

            for (consumer in current.consumers) {
                when (consumer) {
                    is ComputedNode<*> -> {
                        consumer.markDirty()
                        // Traverse even an already-dirty computed, so a failed
                        // evaluation can notify its consumers on the next write.
                        pendingProducers.addLast(consumer)
                    }
                    is EffectNode -> pendingEffects.add(consumer)
                }
            }
        }

        flushEffects()
    }

    fun runInitialEffect(effect: EffectNode) {
        var failure: Throwable? = null
        initialEffectDepth++
        try {
            effect.run()
        } catch (error: Throwable) {
            // Construction failed, so callers cannot receive a disposable handle.
            effect.dispose()
            failure = error
        } finally {
            initialEffectDepth--
        }

        try {
            flushEffects()
        } catch (error: Throwable) {
            failure = combineFailures(failure, error)
        }
        failure?.let {
            effect.dispose()
            throw it
        }
    }

    fun removePendingEffect(effect: EffectNode) {
        pendingEffects.remove(effect)
    }

    private fun flushEffects() {
        if (flushing || initialEffectDepth > 0) return

        flushing = true
        val runCounts = HashMap<EffectNode, Int>()
        var failure: Throwable? = null
        try {
            while (pendingEffects.isNotEmpty()) {
                val effect = pendingEffects.first()
                pendingEffects.remove(effect)
                val runs = runCounts.getOrDefault(effect, 0) + 1
                runCounts[effect] = runs
                if (runs > MAX_EFFECT_RUNS_PER_FLUSH) {
                    failure = combineFailures(
                        failure,
                        IllegalStateException("Effect update cycle exceeded $MAX_EFFECT_RUNS_PER_FLUSH runs per flush."),
                    )
                    break
                }

                try {
                    effect.run()
                } catch (error: Throwable) {
                    failure = combineFailures(failure, error)
                }
            }
        } finally {
            pendingEffects.clear()
            flushing = false
        }
        failure?.let { throw it }
    }

    private fun combineFailures(first: Throwable?, next: Throwable): Throwable {
        if (first == null) return next
        if (first !== next) first.addSuppressed(next)
        return first
    }

    fun <T> collectDependencies(consumer: Consumer, block: () -> T): T {
        val previousConsumer = activeConsumer
        activeConsumer = consumer

        return try {
            block()
        } finally {
            activeConsumer = previousConsumer
        }
    }

    fun <T> untracked(block: () -> T): T {
        val previousConsumer = activeConsumer
        activeConsumer = null

        return try {
            block()
        } finally {
            activeConsumer = previousConsumer
        }
    }
}
