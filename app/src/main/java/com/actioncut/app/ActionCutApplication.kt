package com.actioncut.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. [HiltAndroidApp] bootstraps the DI graph, and implementing
 * [Configuration.Provider] lets WorkManager construct `@HiltWorker`s (the export worker)
 * with their injected dependencies.
 */
@HiltAndroidApp
class ActionCutApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
