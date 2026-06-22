package io.github.origincoding.kignal

import io.github.origincoding.kignal.internal.SignalNode

interface Signal<out T> {
    val value: T

    operator fun invoke(): T = value
}

interface MutableSignal<T> : Signal<T> {
    override var value: T
}

fun <T> signal(initialValue: T): MutableSignal<T> {
    return SignalNode(initialValue)
}
