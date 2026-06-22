package io.github.origincoding.kignal.internal

import io.github.origincoding.kignal.Disposable

internal class EffectNode(private val block: () -> Unit) : Disposable, ReactiveObserver {
    private val dependencies = LinkedHashSet<DependencySource>()
    private var disposed = false
    private var running = false

    init {
        run()
    }

    override fun addDependency(source: DependencySource) {
        if (dependencies.add(source)) {
            source.addSubscriber(this)
        }
    }

    override fun onDependencyChanged() {
        if (!disposed) {
            run()
        }
    }

    override fun dispose() {
        if (disposed) {
            return
        }

        disposed = true
        cleanupDependencies()
    }

    private fun run() {
        if (disposed) {
            return
        }

        check(!running) { "Recursive effect execution is not supported." }

        cleanupDependencies()
        running = true

        try {
            KignalRuntime.collectDependencies(this) {
                block()
            }
        } finally {
            running = false
        }
    }

    private fun cleanupDependencies() {
        dependencies.forEach { dependency ->
            dependency.removeSubscriber(this)
        }
        dependencies.clear()
    }
}
