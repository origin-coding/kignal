package io.github.origincoding.kignal.internal

internal object KignalRuntime {
    private var currentObserver: ReactiveObserver? = null

    fun track(source: DependencySource) {
        currentObserver?.addDependency(source)
    }

    fun <T> collectDependencies(
        observer: ReactiveObserver,
        block: () -> T,
    ): T {
        val previousObserver = currentObserver
        currentObserver = observer

        return try {
            block()
        } finally {
            currentObserver = previousObserver
        }
    }

    fun <T> untracked(block: () -> T): T {
        val previousObserver = currentObserver
        currentObserver = null

        return try {
            block()
        } finally {
            currentObserver = previousObserver
        }
    }
}

internal interface DependencySource {
    fun addSubscriber(observer: ReactiveObserver)

    fun removeSubscriber(observer: ReactiveObserver)
}

internal interface ReactiveObserver {
    fun addDependency(source: DependencySource)

    fun onDependencyChanged()
}
