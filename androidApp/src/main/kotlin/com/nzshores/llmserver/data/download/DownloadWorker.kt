package com.nzshores.llmserver.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nzshores.llmserver.core.model.DownloadState
import com.nzshores.llmserver.data.local.db.ModelDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.RandomAccessFile

class DownloadWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params),
    KoinComponent {

    private val modelDao: ModelDao by inject()
    private val client = OkHttpClient()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return@withContext Result.failure()
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return@withContext Result.failure()
        val expectedSize = inputData.getLong(KEY_EXPECTED_SIZE, 0L)

        val destDir = File(applicationContext.filesDir, "models/$modelId").apply { mkdirs() }
        val destFile = File(destDir, fileName)

        try {
            val startingBytes = if (destFile.exists()) destFile.length() else 0L
            modelDao.updateProgress(modelId, percent = computeProgressPercent(startingBytes, expectedSize), state = DownloadState.DOWNLOADING)

            val startByte = if (destFile.exists()) destFile.length() else 0L
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=$startByte-")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    modelDao.updateState(modelId, DownloadState.FAILED)
                    return@withContext Result.failure()
                }

                val body = response.body ?: run {
                    modelDao.updateState(modelId, DownloadState.FAILED)
                    return@withContext Result.failure()
                }

                RandomAccessFile(destFile, "rw").use { raf ->
                    raf.seek(startByte)
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var totalRead = startByte
                        var lastReportedPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            raf.write(buffer, 0, read)
                            totalRead += read

                            val percent = computeProgressPercent(totalRead, expectedSize)
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                modelDao.updateProgress(modelId, percent, DownloadState.DOWNLOADING)
                                setProgress(androidx.work.workDataOf(KEY_PROGRESS_PERCENT to percent))
                            }
                        }
                    }
                }
            }

            modelDao.markDownloaded(modelId, destFile.absolutePath)
            Result.success()
        } catch (e: java.io.IOException) {
            modelDao.updateState(modelId, DownloadState.FAILED)
            Result.failure()
        }
    }

    companion object {
        const val KEY_MODEL_ID = "modelId"
        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_EXPECTED_SIZE = "expectedSize"
        const val KEY_PROGRESS_PERCENT = "progressPercent"
    }
}
