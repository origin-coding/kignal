package io.github.origincoding.kignal

import io.github.origincoding.kignal.internal.ComputedNode

fun <T> computed(block: () -> T): Signal<T> {
    return ComputedNode(block)
}
