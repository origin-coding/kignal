package io.github.origincoding.kignal.internal

import io.github.origincoding.kignal.Signal

internal class ComputedNode<T>(
    private val block: () -> T,
) : Signal<T>, DependencySource, ReactiveObserver {
    private val dependencies = LinkedHashSet<DependencySource>()
    private val subscribers = LinkedHashSet<ReactiveObserver>()
    private var initialized = false
    private var dirty = true
    private var computing = false
    private var cachedValue: T? = null

    override val value: T
        get() {
            KignalRuntime.track(this)

            if (!initialized || dirty) {
                recompute()
            }

            @Suppress("UNCHECKED_CAST")
            return cachedValue as T
        }

    override fun addDependency(source: DependencySource) {
        if (dependencies.add(source)) {
            source.addSubscriber(this)
        }
    }

    override fun onDependencyChanged() {
        if (dirty) {
            return
        }

        dirty = true
        notifySubscribers()
    }

    override fun addSubscriber(observer: ReactiveObserver) {
        subscribers.add(observer)
    }

    override fun removeSubscriber(observer: ReactiveObserver) {
        subscribers.remove(observer)
    }

    private fun recompute() {
        check(!computing) { "Recursive computed evaluation is not supported." }

        cleanupDependencies()
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

    private fun cleanupDependencies() {
        dependencies.forEach { dependency ->
            dependency.removeSubscriber(this)
        }
        dependencies.clear()
    }

    private fun notifySubscribers() {
        subscribers.toList().forEach { subscriber ->
            subscriber.onDependencyChanged()
        }
    }
}
