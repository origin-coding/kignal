package io.github.origincoding.kignal

import io.github.origincoding.kignal.internal.KignalRuntime

fun <T> untracked(block: () -> T): T {
    return KignalRuntime.untracked(block)
}
