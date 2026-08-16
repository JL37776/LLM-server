package com.nzshores.llmserver

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nzshores.llmserver.core.engine.InferenceEngine
import com.nzshores.llmserver.core.model.ActiveBackend
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LoadUnloadCycleTest : KoinComponent {

    private val repository: ModelRepository by inject()
    private val engine: InferenceEngine by inject()
    private val apiServer: ApiServer by inject()
    private val TAG = "LoadUnloadTest"

    @Test
    fun loadUnloadReloadWithDifferentPreference() { runBlocking {
        val library = repository.library().first()
        val model = library.firstOrNull { it.downloadState == DownloadState.DOWNLOADED }
            ?: run {
                Log.w(TAG, "No downloaded model found, searching and downloading a small one...")
                val results = repository.search("smollm2 135m", ggufOnly = true).getOrThrow()
                val m = results.first { it.selectedQuant != null }
                repository.download(m, wifiOnly = false)
                withTimeout(TimeUnit.MINUTES.toMillis(6)) {
                    repository.library().first { list ->
                        list.any { it.id == m.id && it.downloadState == DownloadState.DOWNLOADED }
                    }.first { it.id == m.id }
                }
            }

        Log.i(TAG, "Model: ${model.id}, path: ${model.localPath}")

        // Step 1: Load with CPU_ONLY
        Log.i(TAG, "Step 1: Loading with CPU_ONLY")
        val result1 = engine.load(model, DevicePreference.CPU_ONLY)
        assertTrue("First load failed: ${result1.message}", result1.success)
        assertEquals(ActiveBackend.CPU, result1.backend)
        assertEquals(model.id, engine.status.value.loadedModelId)
        Log.i(TAG, "Step 1 OK: loaded on CPU")

        // Step 2: Unload
        Log.i(TAG, "Step 2: Unloading")
        engine.unload()
        assertNull(engine.status.value.loadedModelId)
        assertEquals(ActiveBackend.NONE, engine.status.value.backend)
        Log.i(TAG, "Step 2 OK: unloaded")

        // Step 3: Reload with GPU_FIRST (will fall back to CPU since no Vulkan)
        Log.i(TAG, "Step 3: Reloading with GPU_FIRST")
        val result2 = engine.load(model, DevicePreference.GPU_FIRST)
        assertTrue("Second load failed: ${result2.message}", result2.success)
        assertEquals(model.id, engine.status.value.loadedModelId)
        Log.i(TAG, "Step 3 OK: loaded, backend=${result2.backend}, fellBack=${result2.fellBackToCpu}")

        // Step 4: Start server and make API call while model is loaded
        Log.i(TAG, "Step 4: Starting API server")
        apiServer.start(ServerConfig(port = 8080, requireApiKey = false, restrictToLanSubnet = false, maxConcurrentRequests = 1))
        delay(1000)
        assertTrue(apiServer.runtimeInfo.value.isRunning)

        val client = OkHttpClient.Builder().readTimeout(2, TimeUnit.MINUTES).build()
        val body = """{"model":"test","messages":[{"role":"user","content":"Hi"}],"max_tokens":8,"stream":false}"""
        val resp = client.newCall(
            Request.Builder()
                .url("http://127.0.0.1:8080/v1/chat/completions")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        val respBody = resp.body?.string()
        Log.i(TAG, "Step 4: server responded ${resp.code}: $respBody")
        assertTrue("API call failed: ${resp.code}", resp.isSuccessful)

        // Step 5: Unload while server is running, then try API call (should get a proper response, not crash)
        Log.i(TAG, "Step 5: Unloading while server runs")
        engine.unload()
        assertNull(engine.status.value.loadedModelId)

        val resp2 = client.newCall(
            Request.Builder()
                .url("http://127.0.0.1:8080/v1/chat/completions")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        Log.i(TAG, "Step 5: API call after unload: ${resp2.code} ${resp2.body?.string()}")

        // Step 6: Clean up
        apiServer.stop()
        Log.i(TAG, "All steps passed")
    } }
}
