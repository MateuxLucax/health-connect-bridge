package io.github.mateuxlucax.healthconnectbridge.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.healthconnectbridge.R

/** The launcher icon: its foreground on the navy background. */
@Composable
fun Logo(size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Color(0xFF1E2430))
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().scale(1.4f),
        )
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun BodyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Icon, title and optional subtitle and trailing text, at least 56dp tall. */
@Composable
fun IconRow(@DrawableRes icon: Int, title: String, subtitle: String?, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Text(trailing, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** A state line: check when [ok], error icon when not, spinner when null and [busy], plain text otherwise. */
@Composable
fun StatusLine(ok: Boolean?, text: String, busy: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            ok == true -> Icon(painterResource(R.drawable.ic_check_circle), stringResource(R.string.cd_ok), tint = MaterialTheme.colorScheme.primary)
            ok == false -> Icon(painterResource(R.drawable.ic_error), stringResource(R.string.cd_error), tint = MaterialTheme.colorScheme.error)
            busy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        }
        if (ok != null || busy) Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
