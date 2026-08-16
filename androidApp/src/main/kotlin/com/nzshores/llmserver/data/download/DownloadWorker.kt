package com.nzshores.llmserver.data.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nzshores.llmserver.core.model.DownloadState
import com.nzshores.llmserver.data.local.db.ModelDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class DownloadWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params),
    KoinComponent {

    private val modelDao: ModelDao by inject()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return@withContext Result.failure()
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return@withContext Result.failure()
        val expectedSize = inputData.getLong(KEY_EXPECTED_SIZE, 0L)

        val destDir = File(applicationContext.filesDir, "models/$modelId").apply { mkdirs() }
        val destFile = File(destDir, fileName)

        try {
            if (expectedSize > 0 && supportsRangeRequests(url)) {
                parallelDownload(modelId, url, destFile, expectedSize)
            } else {
                singleStreamDownload(modelId, url, destFile, expectedSize)
            }

            modelDao.markDownloaded(modelId, destFile.absolutePath)
            Result.success()
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Download failed for $modelId", e)
            modelDao.updateState(modelId, DownloadState.FAILED)
            Result.failure()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        }
    }

    private fun supportsRangeRequests(url: String): Boolean {
        return try {
            val headReq = Request.Builder().url(url).head().build()
            client.newCall(headReq).execute().use { resp ->
                val acceptRanges = resp.header("Accept-Ranges")
                val supports = acceptRanges != null && acceptRanges != "none"
                Log.d(TAG, "HEAD $url -> Accept-Ranges=$acceptRanges, supports=$supports")
                supports
            }
        } catch (e: Exception) {
            Log.w(TAG, "HEAD request failed, falling back to single stream", e)
            false
        }
    }

    private suspend fun parallelDownload(
        modelId: String,
        url: String,
        destFile: File,
        totalSize: Long,
    ) = coroutineScope {
        val chunkCount = PARALLEL_CONNECTIONS
        val chunkSize = totalSize / chunkCount
        val totalDownloaded = AtomicLong(0L)

        Log.i(TAG, "Starting parallel download: $chunkCount connections, totalSize=$totalSize")
        modelDao.updateProgress(modelId, 0, DownloadState.DOWNLOADING)

        RandomAccessFile(destFile, "rw").use { raf ->
            raf.setLength(totalSize)
        }

        val chunkFiles = (0 until chunkCount).map { i ->
            val start = i * chunkSize
            val end = if (i == chunkCount - 1) totalSize - 1 else (start + chunkSize - 1)
            ChunkSpec(i, start, end)
        }

        val jobs = chunkFiles.map { chunk ->
            async(Dispatchers.IO) {
                downloadChunk(modelId, url, destFile, chunk, totalSize, totalDownloaded)
            }
        }

        jobs.forEach { it.await() }

        assembleChunks(destFile, chunkFiles)
    }

    private fun assembleChunks(destFile: File, chunks: List<ChunkSpec>) {
        RandomAccessFile(destFile, "rw").use { output ->
            for (chunk in chunks) {
                val partFile = File(destFile.parent, "${destFile.name}.part${chunk.index}")
                partFile.inputStream().use { input ->
                    output.seek(chunk.start)
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                    }
                }
                partFile.delete()
            }
        }
    }

    private suspend fun downloadChunk(
        modelId: String,
        url: String,
        destFile: File,
        chunk: ChunkSpec,
        totalSize: Long,
        totalDownloaded: AtomicLong,
    ) {
        val partFile = File(destFile.parent, "${destFile.name}.part${chunk.index}")
        val alreadyDownloaded = if (partFile.exists()) partFile.length() else 0L
        val resumeStart = chunk.start + alreadyDownloaded

        if (alreadyDownloaded >= (chunk.end - chunk.start + 1)) {
            totalDownloaded.addAndGet(alreadyDownloaded)
            return
        }

        totalDownloaded.addAndGet(alreadyDownloaded)

        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$resumeStart-${chunk.end}")
            .build()

        Log.d(TAG, "Chunk ${chunk.index}: Range bytes=$resumeStart-${chunk.end}")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                throw java.io.IOException("Chunk ${chunk.index} failed: HTTP ${response.code}")
            }
            Log.d(TAG, "Chunk ${chunk.index}: HTTP ${response.code}, Content-Length=${response.header("Content-Length")}")

            val body = response.body ?: throw java.io.IOException("Empty body for chunk ${chunk.index}")

            val chunkStart = System.currentTimeMillis()
            var chunkBytes = 0L
            RandomAccessFile(partFile, "rw").use { raf ->
                raf.seek(alreadyDownloaded)
                body.byteStream().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    var lastReportedPercent = -1
                    var lastSpeedLog = chunkStart
                    while (true) {
                        if (isStopped) break
                        val read = input.read(buffer)
                        if (read == -1) break
                        raf.write(buffer, 0, read)
                        chunkBytes += read

                        val downloaded = totalDownloaded.addAndGet(read.toLong())
                        val percent = computeProgressPercent(downloaded, totalSize)
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            modelDao.updateProgress(modelId, percent, DownloadState.DOWNLOADING)
                            setProgress(androidx.work.workDataOf(KEY_PROGRESS_PERCENT to percent))
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastSpeedLog > 5000) {
                            val elapsed = (now - chunkStart) / 1000.0
                            val speedMBps = (chunkBytes / 1024.0 / 1024.0) / elapsed
                            Log.d(TAG, "Chunk ${chunk.index}: %.1f MB downloaded, %.2f MB/s".format(chunkBytes / 1024.0 / 1024.0, speedMBps))
                            lastSpeedLog = now
                        }
                    }
                }
            }
            val elapsed = (System.currentTimeMillis() - chunkStart) / 1000.0
            Log.i(TAG, "Chunk ${chunk.index} done: %.1f MB in %.1fs (%.2f MB/s)".format(chunkBytes / 1024.0 / 1024.0, elapsed, (chunkBytes / 1024.0 / 1024.0) / elapsed))
        }
    }

    private suspend fun singleStreamDownload(
        modelId: String,
        url: String,
        destFile: File,
        expectedSize: Long,
    ) {
        val startByte = if (destFile.exists()) destFile.length() else 0L
        modelDao.updateProgress(modelId, computeProgressPercent(startByte, expectedSize), DownloadState.DOWNLOADING)

        val request = Request.Builder()
            .url(url)
            .apply { if (startByte > 0) header("Range", "bytes=$startByte-") }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                modelDao.updateState(modelId, DownloadState.FAILED)
                throw java.io.IOException("HTTP ${response.code}")
            }

            val body = response.body ?: throw java.io.IOException("Empty response body")

            RandomAccessFile(destFile, "rw").use { raf ->
                raf.seek(startByte)
                body.byteStream().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    var totalRead = startByte
                    var lastReportedPercent = -1
                    while (true) {
                        if (isStopped) break
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
    }

    companion object {
        private const val TAG = "DownloadWorker"
        const val PARALLEL_CONNECTIONS = 6
        const val KEY_MODEL_ID = "modelId"
        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_EXPECTED_SIZE = "expectedSize"
        const val KEY_PROGRESS_PERCENT = "progressPercent"
    }

    private data class ChunkSpec(val index: Int, val start: Long, val end: Long)
}
