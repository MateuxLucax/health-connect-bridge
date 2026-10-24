package io.github.mateuxlucax.healthconnectbridge

import android.app.Application

/** Owns the [AppContainer], so the activity, the screens' ViewModels and [SyncWorker] share one object graph. */
class BridgeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
