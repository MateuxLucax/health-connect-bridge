package io.github.mateuxlucax.healthconnectbridge.ui.format

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import java.util.Locale

/** String resource lookup; an interface so formatting can be unit tested against the strings.xml files. */
interface Strings {
    val locale: Locale
    fun get(@StringRes id: Int, vararg args: Any): String
    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String
}

class ResourceStrings(private val resources: Resources) : Strings {
    override val locale: Locale get() = resources.configuration.locales[0]

    override fun get(id: Int, vararg args: Any): String =
        if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args)

    override fun plural(id: Int, count: Int, vararg args: Any): String = resources.getQuantityString(id, count, *args)
}

/** Strings in the current configuration's language. */
@Composable
fun rememberStrings(): Strings {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) { ResourceStrings(resources) }
}
