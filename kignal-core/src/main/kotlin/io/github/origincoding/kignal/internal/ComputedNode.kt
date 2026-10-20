package io.github.origincoding.kignal.internal

import io.github.origincoding.kignal.Signal

internal class ComputedNode<T>(
    private val block: () -> T,
) : Signal<T>, Producer, Consumer {
    override val dependencies = LinkedHashSet<Producer>()
    override val consumers = LinkedHashSet<Consumer>()
    private var initialized = false
    private var dirty = true
    private var computing = false
    private var cachedValue: T? = null

    override val value: T
        get() {
            KignalRuntime.trackRead(this)

            if (!initialized || dirty) {
                recompute()
            }

            @Suppress("UNCHECKED_CAST")
            return cachedValue as T
        }

    fun markDirty() {
        dirty = true
    }

    private fun recompute() {
        check(!computing) { "Recursive computed evaluation is not supported." }

        DependencyGraph.clearDependencies(this)
        computing = true

        try {
            cachedValue = KignalRuntime.collectDependencies(this) {
                block()
            }
            initialized = true
            dirty = false
        } finally {
            computing = false
        }
    }
}
