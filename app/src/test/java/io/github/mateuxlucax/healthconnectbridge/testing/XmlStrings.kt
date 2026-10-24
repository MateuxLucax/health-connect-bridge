package io.github.mateuxlucax.healthconnectbridge.testing

import io.github.mateuxlucax.healthconnectbridge.R
import io.github.mateuxlucax.healthconnectbridge.ui.format.Strings
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * [Strings] backed by one of the module's strings.xml files, resolving R ids by reflection, so JVM tests
 * see the real translations. Plurals use the English rule (one for 1, other otherwise).
 */
class XmlStrings(folder: String, override val locale: Locale) : Strings {
    private val resources = parse(folder)

    override fun get(id: Int, vararg args: Any): String =
        format(resources.getValue("string/" + nameOf(R.string::class.java, id)).getValue(""), args)

    override fun plural(id: Int, count: Int, vararg args: Any): String {
        val items = resources.getValue("plurals/" + nameOf(R.plurals::class.java, id))
        return format(items[if (count == 1) "one" else "other"] ?: items.getValue("other"), args)
    }

    private fun format(text: String, args: Array<out Any>) = if (args.isEmpty()) text else String.format(locale, text, *args)

    companion object {
        val ENGLISH = "values" to Locale.US
        val PORTUGUESE = "values-pt-rBR" to Locale.forLanguageTag("pt-BR")

        fun of(language: Pair<String, Locale>) = XmlStrings(language.first, language.second)

        /** "type/name" (string, plurals, string-array) to its text, or to its items by quantity / index. */
        fun parse(folder: String): Map<String, Map<String, String>> {
            // Unit tests run in the module directory.
            val file = File("src/main/res/$folder/strings.xml")
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            return root.children().associate { e ->
                val key = e.tagName + "/" + e.getAttribute("name")
                key to when (e.tagName) {
                    "string" -> mapOf("" to unescape(e.textContent))
                    "plurals" -> e.children().associate { it.getAttribute("quantity") to unescape(it.textContent) }
                    else -> e.children().withIndex().associate { (i, item) -> i.toString() to unescape(item.textContent) }
                }
            }
        }

        private fun Element.children(): List<Element> =
            (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()

        private fun unescape(text: String) = text.replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"")

        private fun nameOf(rClass: Class<*>, id: Int): String = rClass.fields.first { it.getInt(null) == id }.name
    }
}
