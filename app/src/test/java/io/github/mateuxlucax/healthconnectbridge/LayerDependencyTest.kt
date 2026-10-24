package io.github.mateuxlucax.healthconnectbridge

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The package rules of docs/adr/0013-code-structure.md: model <- data <- sync <- ui, only ui uses resources
 * (`R`, `BuildConfig`), and no layer refers to the root package (entry points and the AppContainer). Checks
 * imports and fully qualified names written in the code.
 */
class LayerDependencyTest {
    private val base = "io.github.mateuxlucax.healthconnectbridge"

    /** Layer packages (relative to [base]) that each layer must not use. */
    private val forbiddenLayers = mapOf(
        "model" to listOf("data", "sync", "ui"),
        "data" to listOf("sync", "ui"),
        "sync" to listOf("ui"),
        "ui" to emptyList(),
    )

    /** Root package names each layer may still use: the generated resource classes, in `ui` only. */
    private val allowedRoot = mapOf("ui" to setOf("R", "BuildConfig"))

    /** A fully qualified reference to something in the app, e.g. `io.github...healthconnectbridge.data.Foo`. */
    private val reference = Regex("""\b${Regex.escape(base)}\.([A-Za-z_][\w.]*|\*)""")

    @Test
    fun layersOnlyUseLayersBelowThem() {
        val violations = sources().flatMap { (layer, path, lines) ->
            references(lines).filter { violates(layer, it) }.map { "$path uses $base.$it" }
        }
        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun ruleCatchesRootImportsAndFullyQualifiedNames() {
        val lines = listOf(
            "package $base.data.local",
            "import $base.AppContainer",
            "import $base.model.Sample",
            "val x = $base.SyncWorker::class.java",
            "import $base.*",
        )
        val found = references(lines).filter { violates("data", it) }
        assertEquals(listOf("AppContainer", "SyncWorker", "*"), found)
        assertEquals(listOf("R"), references(listOf("import $base.R")).filter { !violates("ui", it) })
        assertEquals(listOf("R"), references(listOf("import $base.R")).filter { violates("sync", it) })
    }

    /** (layer, path, lines) of every Kotlin file inside a layer package; root files may use anything. */
    private fun sources(): List<Triple<String, String, List<String>>> {
        // Unit tests run in the module directory.
        val root = File("src/main/java/" + base.replace('.', '/'))
        return root.walk().filter { it.extension == "kt" }.mapNotNull { file ->
            val relative = file.relativeTo(root).path
            val layer = relative.substringBefore(File.separatorChar, missingDelimiterValue = "")
            if (layer.isEmpty()) return@mapNotNull null
            Triple(layer, relative, file.readLines())
        }.toList().also { check(it.isNotEmpty()) { "no sources found under $root" } }
    }

    /** What each line refers to below [base] (e.g. `data.local.SettingsStore`), ignoring the package line. */
    private fun references(lines: List<String>): List<String> = lines
        .filterNot { it.startsWith("package ") }
        .flatMap { line -> reference.findAll(line).map { it.groupValues[1] }.toList() }

    private fun violates(layer: String, referenced: String): Boolean {
        val first = referenced.substringBefore('.')
        // Packages are lowercase; anything else directly below base (a class, `*`) is in the root package.
        val inRoot = first == "*" || first.first().isUpperCase()
        if (inRoot) return first !in allowedRoot[layer].orEmpty()
        val forbidden = forbiddenLayers[layer] ?: error("unknown layer package '$layer'; add it to the rules")
        return first in forbidden
    }
}
