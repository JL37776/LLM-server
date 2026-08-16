package com.nzshores.llmserver.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressCalculatorTest {

    @Test
    fun `zero bytes written is zero percent`() {
        assertEquals(0, computeProgressPercent(0L, 1_000L))
    }

    @Test
    fun `half of expected size is fifty percent`() {
        assertEquals(50, computeProgressPercent(500L, 1_000L))
    }

    @Test
    fun `bytes written beyond expected size clamps to one hundred`() {
        assertEquals(100, computeProgressPercent(1_500L, 1_000L))
    }

    @Test
    fun `unknown expected size of zero or less is reported as zero percent`() {
        assertEquals(0, computeProgressPercent(500L, 0L))
        assertEquals(0, computeProgressPercent(500L, -1L))
    }
}
