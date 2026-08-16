package com.nzshores.llmserver.data.download

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nzshores.llmserver.core.model.ModelInfo

class DownloadManager(
    private val workManager: WorkManager,
) {
    fun enqueue(model: ModelInfo, wifiOnly: Boolean) {
        val quant = model.selectedQuant ?: return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_MODEL_ID to model.id,
                    DownloadWorker.KEY_URL to quant.downloadUrl,
                    DownloadWorker.KEY_FILE_NAME to quant.fileName,
                    DownloadWorker.KEY_EXPECTED_SIZE to quant.sizeBytes,
                ),
            )
            .build()

        workManager.enqueueUniqueWork(uniqueName(model.id), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(modelId: String) {
        workManager.cancelUniqueWork(uniqueName(modelId))
    }

    private fun uniqueName(modelId: String) = "download-$modelId"
}
