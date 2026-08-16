package com.nzshores.llmserver.engine.llama

import com.nzshores.llmserver.core.model.LoadFailureReason

/** Pulled out of [LlamaCppInferenceEngine] so the GPU-failure heuristics are unit-testable on the JVM. */
fun classifyLoadFailure(error: String, useGpu: Boolean): LoadFailureReason {
    if (!useGpu) return LoadFailureReason.UNKNOWN
    val lower = error.lowercase()
    return when {
        "vram" in lower || "memory" in lower || "alloc" in lower -> LoadFailureReason.OUT_OF_VRAM
        "driver" in lower -> LoadFailureReason.DRIVER_UNSUPPORTED
        "unsupported" in lower || "op" in lower -> LoadFailureReason.UNSUPPORTED_OP
        else -> LoadFailureReason.UNKNOWN
    }
}
