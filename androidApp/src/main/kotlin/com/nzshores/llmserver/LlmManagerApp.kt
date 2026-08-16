package com.nzshores.llmserver

import android.app.Application
import com.nzshores.llmserver.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LlmManagerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LlmManagerApp)
            modules(appModule)
        }
    }
}
