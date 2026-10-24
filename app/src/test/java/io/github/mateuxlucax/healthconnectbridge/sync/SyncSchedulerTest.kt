package io.github.mateuxlucax.healthconnectbridge.sync

import androidx.work.ExistingPeriodicWorkPolicy
import io.github.mateuxlucax.healthconnectbridge.data.local.SettingsStore
import io.github.mateuxlucax.healthconnectbridge.sync.SyncScheduler.PeriodicPlan
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncSchedulerTest {
    @Test
    fun periodIsTheChosenInterval() {
        for (minutes in SettingsStore.SYNC_INTERVALS) {
            assertEquals(minutes.toLong(), SyncScheduler.periodicPlan(minutes, intervalChanged = false).minutes)
        }
    }

    @Test
    fun periodIsAtLeastWorkManagersMinimum() {
        assertEquals(15L, SyncScheduler.periodicPlan(5, intervalChanged = false).minutes)
    }

    @Test
    fun appStart_keepsTheScheduledJob() {
        assertEquals(PeriodicPlan(15, ExistingPeriodicWorkPolicy.KEEP), SyncScheduler.periodicPlan(15, intervalChanged = false))
    }

    @Test
    fun changedInterval_updatesTheScheduledJob() {
        assertEquals(PeriodicPlan(180, ExistingPeriodicWorkPolicy.UPDATE), SyncScheduler.periodicPlan(180, intervalChanged = true))
    }
}
