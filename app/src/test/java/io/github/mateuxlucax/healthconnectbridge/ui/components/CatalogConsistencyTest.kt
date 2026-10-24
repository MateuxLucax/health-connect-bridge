package io.github.mateuxlucax.healthconnectbridge.ui.components

import io.github.mateuxlucax.healthconnectbridge.data.healthconnect.METRICS_BY_RECORD_TYPE
import io.github.mateuxlucax.healthconnectbridge.data.healthconnect.RECORD_TYPES
import io.github.mateuxlucax.healthconnectbridge.testing.XmlStrings
import io.github.mateuxlucax.healthconnectbridge.ui.format.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The UI catalog and formatting must cover exactly the record types and metrics the sync exports. */
class CatalogConsistencyTest {
    @Test
    fun catalogHasOneEntryPerSyncedRecordType() {
        assertEquals(RECORD_TYPES.toSet(), DATA_TYPES.map { it.record }.toSet())
        assertEquals(RECORD_TYPES.size, DATA_TYPES.size)
        DATA_TYPES.forEach { assertTrue(it.record.simpleName, it.metrics.isNotEmpty()) }
    }

    @Test
    fun everyCategoryIsUsed() {
        assertEquals(Category.entries.toSet(), DATA_TYPES.map { it.category }.toSet())
    }

    @Test
    fun everyMetricIsShownWithAUnit() {
        val format = Format(XmlStrings.of(XmlStrings.ENGLISH))
        METRICS_BY_RECORD_TYPE.values.flatten().forEach { metric ->
            assertNotEquals(metric, format.number(2.0), format.value(metric, 2.0))
        }
    }
}
