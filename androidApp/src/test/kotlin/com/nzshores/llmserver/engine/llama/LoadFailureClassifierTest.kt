package com.nzshores.llmserver.engine.llama

import com.nzshores.llmserver.core.model.LoadFailureReason
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadFailureClassifierTest {

    @Test
    fun `cpu load failures are never classified as gpu-specific reasons`() {
        assertEquals(LoadFailureReason.UNKNOWN, classifyLoadFailure("out of VRAM", useGpu = false))
    }

    @Test
    fun `vram or memory wording maps to out of vram`() {
        assertEquals(LoadFailureReason.OUT_OF_VRAM, classifyLoadFailure("failed to allocate VRAM for buffer", useGpu = true))
        assertEquals(LoadFailureReason.OUT_OF_VRAM, classifyLoadFailure("out of memory", useGpu = true))
    }

    @Test
    fun `driver wording maps to driver unsupported`() {
        assertEquals(LoadFailureReason.DRIVER_UNSUPPORTED, classifyLoadFailure("Vulkan driver does not support this extension", useGpu = true))
    }

    @Test
    fun `unsupported op wording maps to unsupported op`() {
        assertEquals(LoadFailureReason.UNSUPPORTED_OP, classifyLoadFailure("unsupported operation for this tensor type", useGpu = true))
    }

    @Test
    fun `unrecognized wording falls back to unknown`() {
        assertEquals(LoadFailureReason.UNKNOWN, classifyLoadFailure("something unexpected happened", useGpu = true))
    }
}
