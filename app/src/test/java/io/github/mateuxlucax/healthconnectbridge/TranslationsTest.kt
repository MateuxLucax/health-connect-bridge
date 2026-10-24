package io.github.mateuxlucax.healthconnectbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationsTest {
    @Test
    fun everyKeyExistsInBothLanguages() {
        val english = XmlStrings.parse(XmlStrings.ENGLISH.first).keys
        val portuguese = XmlStrings.parse(XmlStrings.PORTUGUESE.first).keys
        assertEquals("missing in values-pt-rBR", emptySet<String>(), english - portuguese)
        assertEquals("missing in values (English)", emptySet<String>(), portuguese - english)
    }

    @Test
    fun everySyncErrorHasAMessageInBothLanguages() {
        for (language in listOf(XmlStrings.ENGLISH, XmlStrings.PORTUGUESE)) {
            val strings = XmlStrings.of(language)
            SyncError.Kind.entries.forEach { kind -> assertTrue(kind.name, SyncError(kind, "x").message(strings).isNotBlank()) }
        }
    }
}
