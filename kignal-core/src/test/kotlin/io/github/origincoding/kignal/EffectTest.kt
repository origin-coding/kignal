package io.github.origincoding.kignal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EffectTest {
    @Test
    fun `effect runs immediately`() {
        val count = signal(1)
        val values = mutableListOf<Int>()

        effect {
            values.add(count.value)
        }

        assertThat(values).containsExactly(1)
    }

    @Test
    fun `effect reruns when dependency changes`() {
        val count = signal(1)
        val values = mutableListOf<Int>()

        effect {
            values.add(count.value)
        }

        count.value = 2

        assertThat(values).containsExactly(1, 2)
    }

    @Test
    fun `effect does not rerun when unrelated signal changes`() {
        val count = signal(1)
        val unrelated = signal("initial")
        var runs = 0

        effect {
            runs += 1
            count.value
        }

        unrelated.value = "changed"

        assertThat(runs).isEqualTo(1)
    }

    @Test
    fun `effect does not rerun when signal is set to equal value`() {
        val count = signal(1)
        var runs = 0

        effect {
            runs += 1
            count.value
        }

        count.value = 1

        assertThat(runs).isEqualTo(1)
    }

    @Test
    fun `effect cleans up old dependencies when dependencies change`() {
        val enabled = signal(true)
        val a = signal(1)
        val b = signal(10)
        val values = mutableListOf<Int>()

        effect {
            values.add(if (enabled.value) a.value else b.value)
        }

        enabled.value = false
        a.value = 2
        b.value = 20

        assertThat(values).containsExactly(1, 10, 20)
    }

    @Test
    fun `disposed effect stops rerunning`() {
        val count = signal(1)
        val values = mutableListOf<Int>()

        val stop = effect {
            values.add(count.value)
        }

        stop.dispose()
        count.value = 2

        assertThat(values).containsExactly(1)
    }

    @Test
    fun `effect can depend on computed`() {
        val count = signal(1)
        val double = computed {
            count.value * 2
        }
        val values = mutableListOf<Int>()

        effect {
            values.add(double.value)
        }

        count.value = 2

        assertThat(values).containsExactly(2, 4)
    }
}
