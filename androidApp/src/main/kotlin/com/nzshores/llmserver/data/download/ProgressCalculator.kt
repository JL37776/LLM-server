package com.nzshores.llmserver.data.download

/** Pulled out of [DownloadWorker] so the percent math is unit-testable without touching disk. */
fun computeProgressPercent(bytesWritten: Long, expectedSize: Long): Int {
    if (expectedSize <= 0) return 0
    return ((bytesWritten * 100) / expectedSize).toInt().coerceIn(0, 100)
}
