package io.github.origincoding.kignal

import io.github.origincoding.kignal.internal.EffectNode

interface Disposable {
    fun dispose()
}

fun effect(block: () -> Unit): Disposable {
    return EffectNode(block)
}
