package io.github.mateuxlucax.healthconnectbridge

import android.app.Application

/** Owns the [AppContainer], so the activity, the screens' ViewModels and [SyncWorker] share one object graph. */
class BridgeApplication : Application() {
    /**
     * Created on first use, not in onCreate: WorkManager's androidx.startup initializer runs from a content
     * provider before Application.onCreate and can start pending work (e.g. a "sync now" or backfill enqueued
     * before the process died) right away, so [SyncWorker] may ask for the container first. The base context is
     * attached by then, which is all [AppContainer] needs. `lazy` is synchronized, so the worker thread and the
     * main thread get the same instance.
     */
    val container: AppContainer by lazy { AppContainer(this) }
}
