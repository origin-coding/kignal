package io.github.origincoding.kignal

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ComputedTest {
    @Test
    fun `computed is lazy and caches its value`() {
        val count = signal(1)
        var runs = 0

        val double = computed {
            runs += 1
            count.value * 2
        }

        assertThat(runs).isEqualTo(0)

        assertThat(double.value).isEqualTo(2)
        assertThat(runs).isEqualTo(1)

        assertThat(double.value).isEqualTo(2)
        assertThat(runs).isEqualTo(1)
    }

    @Test
    fun `computed recomputes after dependency changes`() {
        val count = signal(1)
        var runs = 0

        val double = computed {
            runs += 1
            count.value * 2
        }

        assertThat(double.value).isEqualTo(2)
        assertThat(runs).isEqualTo(1)

        count.value = 2

        assertThat(runs).isEqualTo(1)
        assertThat(double.value).isEqualTo(4)
        assertThat(runs).isEqualTo(2)
    }

    @Test
    fun `computed is not invalidated when signal is set to equal value`() {
        val count = signal(1)
        var runs = 0

        val double = computed {
            runs += 1
            count.value * 2
        }

        assertThat(double.value).isEqualTo(2)
        assertThat(runs).isEqualTo(1)

        count.value = 1

        assertThat(double.value).isEqualTo(2)
        assertThat(runs).isEqualTo(1)
    }

    @Test
    fun `computed cleans up old dependencies when dependencies change`() {
        val enabled = signal(true)
        val a = signal(1)
        val b = signal(10)
        var runs = 0

        val result = computed {
            runs += 1
            if (enabled.value) a.value else b.value
        }

        assertThat(result.value).isEqualTo(1)
        assertThat(runs).isEqualTo(1)

        enabled.value = false

        assertThat(result.value).isEqualTo(10)
        assertThat(runs).isEqualTo(2)

        a.value = 2

        assertThat(result.value).isEqualTo(10)
        assertThat(runs).isEqualTo(2)

        b.value = 20

        assertThat(result.value).isEqualTo(20)
        assertThat(runs).isEqualTo(3)
    }

    @Test
    fun `computed can depend on another computed`() {
        val count = signal(1)

        val double = computed {
            count.value * 2
        }
        val quadruple = computed {
            double.value * 2
        }

        assertThat(quadruple.value).isEqualTo(4)

        count.value = 2

        assertThat(quadruple.value).isEqualTo(8)
    }

    @Test
    fun `computed error propagates to reader`() {
        val result = computed<Int> {
            error("boom")
        }

        assertThatThrownBy {
            result.value
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("boom")
    }
}
