package io.github.mateuxlucax.healthconnectbridge.ui

import androidx.health.connect.client.HealthConnectClient
import io.github.mateuxlucax.healthconnectbridge.model.DeviceStatus
import io.github.mateuxlucax.healthconnectbridge.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeviceStatusViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    /** DeviceStatus has identity equality; the tests compare the instances they hand out. */
    private fun status(granted: Int) = DeviceStatus(
        healthConnect = HealthConnectClient.SDK_AVAILABLE,
        granted = granted,
        requested = 17,
        grantedReads = granted,
        batteryExempt = false,
    )

    /** Answers each load with the next entry: a status, or an exception to throw. */
    private val answers = ArrayDeque<Any>()

    private val vm by lazy {
        DeviceStatusViewModel {
            when (val next = answers.removeFirst()) {
                is Exception -> throw next
                is CompletableDeferred<*> -> next.await() as DeviceStatus
                else -> next as DeviceStatus
            }
        }
    }

    @Test
    fun nothingUntilTheFirstLoad() {
        assertEquals(DeviceStatusUiState(), vm.uiState.value)
    }

    @Test
    fun refreshLoadsTheStatus() {
        val loaded = status(3)
        answers += loaded
        vm.refresh()
        assertEquals(DeviceStatusUiState(loaded, failed = false), vm.uiState.value)
    }

    @Test
    fun failedLoad_keepsTheLastStatusAndReportsTheFailure() {
        val first = status(3)
        val later = status(5)
        answers += first
        answers += IllegalStateException("binder died")
        answers += later
        vm.refresh()
        vm.refresh()
        assertEquals(DeviceStatusUiState(first, failed = true), vm.uiState.value)
        vm.refresh()
        assertEquals(DeviceStatusUiState(later, failed = false), vm.uiState.value)
    }

    @Test
    fun newerRefreshReplacesARunningLoad() {
        val slow = CompletableDeferred<DeviceStatus>()
        val newer = status(5)
        answers += slow
        answers += newer
        vm.refresh()
        assertNull(vm.uiState.value.status)
        vm.refresh()
        slow.complete(status(1)) // the superseded load's answer is dropped
        assertEquals(newer, vm.uiState.value.status)
        assertFalse(vm.uiState.value.failed)
        assertTrue(answers.isEmpty())
    }
}
