package io.github.origincoding.kignal.internal

import io.github.origincoding.kignal.MutableSignal

internal class SignalNode<T>(
    initialValue: T,
) : MutableSignal<T>, DependencySource {
    private val subscribers = LinkedHashSet<ReactiveObserver>()
    private var currentValue = initialValue

    override var value: T
        get() {
            KignalRuntime.track(this)
            return currentValue
        }
        set(newValue) {
            if (currentValue == newValue) {
                return
            }

            currentValue = newValue
            notifySubscribers()
        }

    override fun addSubscriber(observer: ReactiveObserver) {
        subscribers.add(observer)
    }

    override fun removeSubscriber(observer: ReactiveObserver) {
        subscribers.remove(observer)
    }

    private fun notifySubscribers() {
        subscribers.toList().forEach { subscriber ->
            subscriber.onDependencyChanged()
        }
    }
}
