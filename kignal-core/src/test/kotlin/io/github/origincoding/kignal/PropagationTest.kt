package io.github.origincoding.kignal

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PropagationTest {
    @Test
    fun `effect sees fresh computed values regardless of read order`() {
        for (sourceFirst in listOf(true, false)) {
            val count = signal(1)
            val double = computed { count.value * 2 }
            val values = mutableListOf<Pair<Int, Int>>()
            val stop = effect {
                values += if (sourceFirst) {
                    count.value to double.value
                } else {
                    val derived = double.value
                    count.value to derived
                }
            }

            count.value = 2
            count.value = 3

            assertThat(values).containsExactly(1 to 2, 2 to 4, 3 to 6)
            stop.dispose()
        }
    }

    @Test
    fun `diamond dependencies are invalidated before effect runs`() {
        val count = signal(1)
        val left = computed { count.value * 2 }
        val right = computed { count.value * 3 }
        val total = computed { left.value + right.value }
        val values = mutableListOf<Int>()
        val stop = effect { values += total.value }

        count.value = 2
        count.value = 3

        assertThat(values).containsExactly(5, 10, 15)
        stop.dispose()
    }

    @Test
    fun `dynamic dependency switch reads freshly invalidated branch`() {
        val count = signal(1)
        val branch = computed { count.value * 10 }
        val values = mutableListOf<Int>()
        val stop = effect {
            values += if (count.value == 1) 0 else branch.value
        }
        // Cache the branch after the effect has subscribed to count.
        assertThat(branch.value).isEqualTo(10)

        count.value = 2
        count.value = 1
        count.value = 3

        assertThat(values).containsExactly(0, 20, 0, 30)
        stop.dispose()
    }

    @Test
    fun `unobserved computed stays lazy during propagation`() {
        val count = signal(1)
        var runs = 0
        val double = computed {
            runs++
            count.value * 2
        }
        assertThat(double.value).isEqualTo(2)

        count.value = 2
        count.value = 3

        assertThat(runs).isEqualTo(1)
        assertThat(double.value).isEqualTo(6)
        assertThat(runs).isEqualTo(2)
    }

    @Test
    fun `effect writes defer other effects until current body returns`() {
        val trigger = signal(0)
        val target = signal(0)
        val events = mutableListOf<String>()
        val reader = effect { events += "read ${target.value}" }
        val writer = effect {
            val value = trigger.value
            events += "start $value"
            target.value = value
            events += "end $value"
        }
        events.clear()

        trigger.value = 1

        assertThat(events).containsExactly("start 1", "end 1", "read 1")
        writer.dispose()
        reader.dispose()
    }

    @Test
    fun `initial effect writes flush after the body returns`() {
        val count = signal(0)
        val events = mutableListOf<String>()
        val reader = effect { events += "read ${count.value}" }
        events.clear()

        val writer = effect {
            events += "start"
            count.value = 1
            events += "end"
        }

        assertThat(events).containsExactly("start", "end", "read 1")
        writer.dispose()
        reader.dispose()
    }

    @Test
    fun `effect already waiting is coalesced when another effect writes`() {
        val trigger = signal(0)
        val target = signal(0)
        val writer = effect { target.value = trigger.value * 10 }
        val values = mutableListOf<Pair<Int, Int>>()
        val reader = effect { values += trigger.value to target.value }

        trigger.value = 1

        assertThat(values).containsExactly(0 to 0, 1 to 10)
        reader.dispose()
        writer.dispose()
    }

    @Test
    fun `effect can be queued again after it has run`() {
        val trigger = signal(0)
        val target = signal(0)
        val values = mutableListOf<Pair<Int, Int>>()
        val reader = effect { values += trigger.value to target.value }
        val writer = effect { target.value = trigger.value * 10 }

        trigger.value = 1

        assertThat(values).containsExactly(0 to 0, 1 to 0, 1 to 10)
        reader.dispose()
        writer.dispose()
    }

    @Test
    fun `disposed effect is skipped even if already queued`() {
        val count = signal(0)
        var stopReader: Disposable? = null
        val disposer = effect {
            if (count.value > 0) stopReader?.dispose()
        }
        val values = mutableListOf<Int>()
        stopReader = effect { values += count.value }

        count.value = 1

        assertThat(values).containsExactly(0)
        disposer.dispose()
    }

    @Test
    fun `effect failure does not interrupt invalidation or other queued effects`() {
        val count = signal(0)
        val failing = effect {
            if (count.value == 1) error("boom")
        }
        val double = computed { count.value * 2 }
        val values = mutableListOf<Int>()
        val reader = effect { values += double.value }

        assertThatThrownBy { count.value = 1 }.hasMessage("boom")
        assertThat(values).containsExactly(0, 2)

        count.value = 2

        assertThat(values).containsExactly(0, 2, 4)
        reader.dispose()
        failing.dispose()
    }

    @Test
    fun `computed failure can recover on a later dependency change`() {
        val count = signal(1)
        val positive = computed {
            check(count.value > 0) { "positive values only" }
            count.value * 2
        }
        val values = mutableListOf<Int>()
        val stop = effect { values += positive.value }

        assertThatThrownBy { count.value = -1 }.hasMessage("positive values only")
        count.value = 2

        assertThat(values).containsExactly(2, 4)
        stop.dispose()
    }

    @Test
    fun `failed initial effect releases dependencies`() {
        val count = signal(0)
        var runs = 0
        assertThatThrownBy {
            effect {
                count.value
                runs++
                error("boom")
            }
        }.hasMessage("boom")

        count.value = 1

        assertThat(runs).isEqualTo(1)
    }

    @Test
    fun `failure while flushing initial effect updates releases its dependencies`() {
        val count = signal(0)
        var runs = 0
        assertThatThrownBy {
            effect {
                runs++
                if (count.value == 0) count.value = 1 else error("boom")
            }
        }.hasMessage("boom")

        count.value = 2

        assertThat(runs).isEqualTo(2)
    }

    @Test
    fun `multiple effect failures are reported and later writes still work`() {
        val count = signal(0)
        val firstError = IllegalStateException("first")
        val secondError = IllegalStateException("second")
        val first = effect { if (count.value == 1) throw firstError }
        val second = effect { if (count.value == 1) throw secondError }
        val values = mutableListOf<Int>()
        val reader = effect { values += count.value }

        assertThatThrownBy { count.value = 1 }
            .isSameAs(firstError)
        assertThat(firstError.suppressed).containsExactly(secondError)
        count.value = 2

        assertThat(values).containsExactly(0, 1, 2)
        reader.dispose()
        first.dispose()
        second.dispose()
    }

    @Test
    fun `deep unobserved computed chain invalidates without recursive propagation`() {
        val count = signal(0)
        var runs = 0
        var tail: Signal<Int> = count
        repeat(10_000) {
            val parent = tail
            tail = computed {
                runs++
                parent.value + 1
            }
            // Initialize each node while its parent is still cached.
            assertThat(tail.value).isEqualTo(it + 1)
        }

        count.value = 1

        assertThat(runs).isEqualTo(10_000)
    }

    @Test
    fun `nested initial effect restores outer dependency tracking`() {
        val first = signal(0)
        val innerSource = signal(0)
        val last = signal(0)
        var outerRuns = 0
        var innerRuns = 0
        var inner: Disposable? = null
        val outer = effect {
            outerRuns++
            first.value
            if (inner == null) {
                inner = effect {
                    innerRuns++
                    innerSource.value
                }
            }
            last.value
        }

        innerSource.value = 1
        assertThat(outerRuns).isEqualTo(1)
        assertThat(innerRuns).isEqualTo(2)
        last.value = 1
        first.value = 1

        assertThat(outerRuns).isEqualTo(3)
        assertThat(innerRuns).isEqualTo(2)
        outer.dispose()
        inner?.dispose()
    }

    @Test
    fun `self updating effect can settle without recursive execution`() {
        val count = signal(0)
        val values = mutableListOf<Int>()
        val stop = effect {
            val value = count.value
            values += value
            if (value < 3) count.value = value + 1
        }

        assertThat(values).containsExactly(0, 1, 2, 3)
        assertThat(count.value).isEqualTo(3)
        stop.dispose()
    }

    @Test
    fun `unbounded updates fail and runtime can be used afterwards`() {
        val enabled = signal(false)
        val count = signal(0)
        val stop = effect {
            if (enabled.value) count.value = count.value + 1
        }

        assertThatThrownBy { enabled.value = true }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Effect update cycle")
        stop.dispose()

        val other = signal(0)
        val values = mutableListOf<Int>()
        val reader = effect { values += other.value }
        other.value = 1

        assertThat(values).containsExactly(0, 1)
        reader.dispose()
    }
}
