package io.github.origincoding.kignal.internal

import io.github.origincoding.kignal.MutableSignal

internal class SignalNode<T>(
    initialValue: T,
) : MutableSignal<T>, Producer {
    override val consumers = LinkedHashSet<Consumer>()
    private var currentValue = initialValue

    override var value: T
        get() {
            KignalRuntime.trackRead(this)
            return currentValue
        }
        set(newValue) {
            if (currentValue == newValue) {
                return
            }

            currentValue = newValue
            KignalRuntime.invalidate(this)
        }
}
