package com.nzshores.llmserver.di

import androidx.room.Room
import androidx.work.WorkManager
import com.nzshores.llmserver.core.engine.InferenceEngine
import com.nzshores.llmserver.core.metrics.MetricsCollector
import com.nzshores.llmserver.core.repository.ModelRepository
import com.nzshores.llmserver.core.server.ApiServer
import com.nzshores.llmserver.data.download.DownloadManager
import com.nzshores.llmserver.data.local.db.AppDatabase
import com.nzshores.llmserver.data.remote.HuggingFaceApi
import com.nzshores.llmserver.data.repository.AndroidModelRepository
import com.nzshores.llmserver.data.settings.SettingsRepository
import com.nzshores.llmserver.engine.llama.LlamaCppInferenceEngine
import com.nzshores.llmserver.metrics.AndroidMetricsCollector
import com.nzshores.llmserver.server.KtorApiServer
import com.nzshores.llmserver.ui.library.LibraryViewModel
import com.nzshores.llmserver.ui.monitor.MonitorViewModel
import com.nzshores.llmserver.ui.search.SearchViewModel
import com.nzshores.llmserver.ui.server.ServerViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    single {
        Room.databaseBuilder(get(), AppDatabase::class.java, "llm-manager.db")
            .fallbackToDestructiveMigration()
            .build()
    }
    single { get<AppDatabase>().modelDao() }

    single { HuggingFaceApi(get()) }
    single { WorkManager.getInstance(get()) }
    single { DownloadManager(get()) }
    single { SettingsRepository(get()) }

    single<ModelRepository> { AndroidModelRepository(get(), get(), get(), get()) }
    single<InferenceEngine> { LlamaCppInferenceEngine() }
    single<ApiServer> { KtorApiServer(get(), get()) }
    single<MetricsCollector> { AndroidMetricsCollector(get()) }

    viewModel { SearchViewModel(get()) }
    viewModel { LibraryViewModel(get(), get(), get()) }
    viewModel { ServerViewModel(get(), get()) }
    viewModel { MonitorViewModel(get(), get()) }
}
