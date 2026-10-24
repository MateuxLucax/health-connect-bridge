package io.github.mateuxlucax.healthconnectbridge.ui.onboarding

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.healthconnectbridge.R
import kotlinx.coroutines.delay

private class FlowNode(@DrawableRes val icon: Int, @StringRes val label: Int)

private val NODES = listOf(
    FlowNode(R.drawable.ic_watch, R.string.flow_watch),
    FlowNode(R.drawable.ic_smartphone, R.string.flow_phone),
    FlowNode(R.drawable.ic_cloud, R.string.flow_server),
    FlowNode(R.drawable.ic_home, R.string.flow_grafana),
)

private val CIRCLE = 44.dp

/**
 * Watch → phone → server → Grafana, with a highlight and a dot hopping left to right in a loop.
 * Static when the system turns animations off.
 */
@Composable
fun DataFlow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    var active by remember { mutableIntStateOf(-1) }
    var hop by remember { mutableIntStateOf(-1) }
    val dot = remember { Animatable(0f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        while (true) {
            for (i in NODES.indices) {
                active = i
                delay(600)
                if (i < NODES.lastIndex) {
                    dot.snapTo(0f)
                    hop = i
                    dot.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
                    hop = -1
                }
            }
            delay(1000)
        }
    }

    val line = MaterialTheme.colorScheme.outlineVariant
    val dotColor = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.flow_description)
    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
            .drawBehind {
                val column = size.width / NODES.size
                val radius = CIRCLE.toPx() / 2
                val y = radius
                val gap = 6.dp.toPx()
                val stroke = 2.dp.toPx()
                val head = 4.dp.toPx()
                for (i in 0 until NODES.lastIndex) {
                    val start = column * (i + 0.5f) + radius + gap
                    val end = column * (i + 1.5f) - radius - gap
                    if (end <= start) continue
                    drawLine(line, Offset(start, y), Offset(end, y), stroke, StrokeCap.Round)
                    drawLine(line, Offset(end - head, y - head), Offset(end, y), stroke, StrokeCap.Round)
                    drawLine(line, Offset(end - head, y + head), Offset(end, y), stroke, StrokeCap.Round)
                    if (i == hop) {
                        drawCircle(dotColor, 3.dp.toPx(), Offset(start + (end - start) * dot.value, y))
                    }
                }
            },
    ) {
        NODES.forEachIndexed { i, node ->
            val highlight by animateFloatAsState(if (i == active) 1f else 0f, tween(400), label = "highlight")
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(CIRCLE)
                        .scale(1f + 0.08f * highlight)
                        .background(
                            lerp(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.primaryContainer, highlight),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(node.icon),
                        contentDescription = null,
                        tint = lerp(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onPrimaryContainer, highlight),
                    )
                }
                Text(
                    stringResource(node.label),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
