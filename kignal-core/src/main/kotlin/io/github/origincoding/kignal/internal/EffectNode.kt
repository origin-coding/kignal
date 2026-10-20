package io.github.origincoding.kignal.internal

import io.github.origincoding.kignal.Disposable

internal class EffectNode(private val block: () -> Unit) : Disposable, Consumer {
    override val dependencies = LinkedHashSet<Producer>()
    private var disposed = false
    private var running = false

    init {
        KignalRuntime.runInitialEffect(this)
    }

    override fun dispose() {
        if (disposed) {
            return
        }

        disposed = true
        DependencyGraph.clearDependencies(this)
        KignalRuntime.removePendingEffect(this)
    }

    fun run() {
        if (disposed) {
            return
        }

        check(!running) { "Recursive effect execution is not supported." }

        DependencyGraph.clearDependencies(this)
        running = true

        try {
            KignalRuntime.collectDependencies(this) {
                block()
            }
        } finally {
            running = false
        }
    }
}
