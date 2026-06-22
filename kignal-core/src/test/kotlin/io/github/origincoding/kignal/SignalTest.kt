package io.github.origincoding.kignal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SignalTest {
    @Test
    fun `signal exposes current value`() {
        val count = signal(1)

        assertThat(count.value).isEqualTo(1)
        assertThat(count()).isEqualTo(1)

        count.value = 2

        assertThat(count.value).isEqualTo(2)
        assertThat(count()).isEqualTo(2)
    }
}
