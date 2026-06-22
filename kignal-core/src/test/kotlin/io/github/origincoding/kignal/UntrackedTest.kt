package io.github.origincoding.kignal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UntrackedTest {
    @Test
    fun `untracked returns block value`() {
        val value = untracked {
            "result"
        }

        assertThat(value).isEqualTo("result")
    }

    @Test
    fun `untracked read does not become computed dependency`() {
        val tracked = signal(1)
        val ignored = signal(10)
        var runs = 0

        val result = computed {
            runs += 1
            tracked.value + untracked { ignored.value }
        }

        assertThat(result.value).isEqualTo(11)
        assertThat(runs).isEqualTo(1)

        ignored.value = 20

        assertThat(result.value).isEqualTo(11)
        assertThat(runs).isEqualTo(1)

        tracked.value = 2

        assertThat(result.value).isEqualTo(22)
        assertThat(runs).isEqualTo(2)
    }

    @Test
    fun `untracked read does not become effect dependency`() {
        val tracked = signal(1)
        val ignored = signal(10)
        val values = mutableListOf<Int>()

        effect {
            values.add(tracked.value + untracked { ignored.value })
        }

        ignored.value = 20

        assertThat(values).containsExactly(11)

        tracked.value = 2

        assertThat(values).containsExactly(11, 22)
    }

    @Test
    fun `tracking resumes after untracked block`() {
        val first = signal(1)
        val ignored = signal(10)
        val second = signal(100)
        var runs = 0

        val result = computed {
            runs += 1
            first.value + untracked { ignored.value } + second.value
        }

        assertThat(result.value).isEqualTo(111)
        assertThat(runs).isEqualTo(1)

        second.value = 200

        assertThat(result.value).isEqualTo(211)
        assertThat(runs).isEqualTo(2)
    }
}
