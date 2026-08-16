package com.nzshores.llmserver

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nzshores.llmserver.core.engine.InferenceEngine
import com.nzshores.llmserver.core.model.DevicePreference
import com.nzshores.llmserver.core.model.DownloadState
import com.nzshores.llmserver.core.model.ServerConfig
import com.nzshores.llmserver.core.repository.ModelRepository
import com.nzshores.llmserver.core.server.ApiServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Drives the real download -> load -> serve pipeline against the app process actually installed
 * on-device, bypassing UI taps entirely (this device's MIUI build blocks `adb shell input`).
 * Talks to the exact same Koin singletons the UI uses.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndSmokeTest : KoinComponent {

    private val modelRepository: ModelRepository by inject()
    private val engine: InferenceEngine by inject()
    private val apiServer: ApiServer by inject()

    private val TAG = "SmokeTest"

    @Test
    fun searchDownloadLoadAndServe() = runBlocking {
        Log.i(TAG, "Searching Hugging Face for a small GGUF model...")
        val results = modelRepository.search("smollm2 135m", ggufOnly = true).getOrThrow()
        val model = results.firstOrNull { it.selectedQuant != null }
            ?: error("No small GGUF model found in search results: $results")
        Log.i(TAG, "Selected ${model.id} quant=${model.selectedQuant?.label} size=${model.selectedQuant?.sizeBytes}")

        Log.i(TAG, "Enqueueing download...")
        modelRepository.download(model, wifiOnly = false)

        val downloaded = withTimeout(TimeUnit.MINUTES.toMillis(6)) {
            modelRepository.library().first { list ->
                list.any { it.id == model.id && it.downloadState == DownloadState.DOWNLOADED }
            }.first { it.id == model.id }
        }
        Log.i(TAG, "Downloaded to ${downloaded.localPath}")

        Log.i(TAG, "Loading model into the inference engine...")
        val loadResult = engine.load(downloaded, DevicePreference.GPU_FIRST)
        Log.i(TAG, "Load result: success=${loadResult.success} backend=${loadResult.backend} fellBackToCpu=${loadResult.fellBackToCpu} reason=${loadResult.failureReason} message=${loadResult.message}")
        assertTrue("Model failed to load: ${loadResult.message}", loadResult.success)

        Log.i(TAG, "Starting the LAN API server...")
        apiServer.start(ServerConfig(port = 8080, requireApiKey = false, restrictToLanSubnet = false, maxConcurrentRequests = 1))
        delay(1500)

        Log.i(TAG, "Calling /v1/chat/completions over localhost...")
        val client = OkHttpClient.Builder()
            .readTimeout(2, TimeUnit.MINUTES)
            .build()
        val requestBody = """
            {"model":"test","messages":[{"role":"user","content":"Say hi in exactly three words."}],"max_tokens":24,"stream":false}
        """.trimIndent()
        val request = Request.Builder()
            .url("http://127.0.0.1:8080/v1/chat/completions")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
        Log.i(TAG, "Server responded: code=${response.code} body=$responseBody")

        apiServer.stop()
        engine.unload()

        assertTrue("Server returned ${response.code}: $responseBody", response.isSuccessful)
        assertTrue("Response body was empty", !responseBody.isNullOrBlank())
    }
}
