package io.github.mateuxlucax.healthconnectbridge

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The package rules of docs/adr/0013-code-structure.md: model <- data <- sync <- ui, only ui uses resources,
 * and nothing imports the root package (entry points and the AppContainer).
 */
class LayerDependencyTest {
    private val base = "io.github.mateuxlucax.healthconnectbridge"

    /** Package prefixes (relative to [base]) that each layer must not import. */
    private val forbidden = mapOf(
        "model" to listOf("data", "sync", "ui", "R", "BuildConfig"),
        "data" to listOf("sync", "ui", "R", "BuildConfig"),
        "sync" to listOf("ui", "R", "BuildConfig"),
        "ui" to listOf("MainActivity", "PrivacyActivity", "SyncWorker", "BridgeApplication", "AppContainer"),
    )

    @Test
    fun layersOnlyImportDownwards() {
        // Unit tests run in the module directory.
        val root = File("src/main/java/" + base.replace('.', '/'))
        val violations = root.walk().filter { it.extension == "kt" }.flatMap { file ->
            val layer = file.relativeTo(root).path.substringBefore(File.separatorChar)
            val rules = forbidden[layer].orEmpty()
            file.readLines()
                .filter { it.startsWith("import $base.") }
                .map { it.removePrefix("import $base.") }
                .filter { imported -> rules.any { imported == it || imported.startsWith("$it.") } }
                .map { "${file.relativeTo(root)} imports $it" }
        }.toList()
        assertEquals(emptyList<String>(), violations)
    }
}
