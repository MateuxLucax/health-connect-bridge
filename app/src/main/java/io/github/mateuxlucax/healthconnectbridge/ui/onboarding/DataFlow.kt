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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * Watch → phone → server → Grafana. Each node lights up in turn and the arrow to the next one fills
 * with color like a progress bar; after the last node everything fades back and the loop restarts.
 * Static (all arrows filled) when the system turns animations off.
 */
@Composable
fun DataFlow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    var active by remember { mutableIntStateOf(-1) }
    val fills = remember { List(NODES.lastIndex) { Animatable(if (animate) 0f else 1f) } }
    val fillAlpha = remember { Animatable(1f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        while (true) {
            for (i in NODES.indices) {
                active = i
                delay(600)
                if (i < NODES.lastIndex) {
                    fills[i].animateTo(1f, tween(700, easing = FastOutSlowInEasing))
                }
            }
            delay(1000)
            active = -1
            fillAlpha.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
            fills.forEach { it.snapTo(0f) }
            fillAlpha.snapTo(1f)
            delay(200)
        }
    }

    val neutral = MaterialTheme.colorScheme.outlineVariant
    val filled = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.flow_description)
    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
            .drawBehind {
                val column = size.width / NODES.size
                val radius = CIRCLE.toPx() / 2
                // Circles are top-aligned in the row, so their centers sit at y = radius.
                val y = radius
                val gap = 6.dp.toPx()
                val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val head = 4.dp.toPx()
                for (i in 0 until NODES.lastIndex) {
                    val start = column * (i + 0.5f) + radius + gap
                    val end = column * (i + 1.5f) - radius - gap
                    if (end <= start) continue
                    val arrow = Path().apply {
                        moveTo(start, y)
                        lineTo(end, y)
                        moveTo(end - head, y - head)
                        lineTo(end, y)
                        lineTo(end - head, y + head)
                    }
                    drawPath(arrow, neutral, style = stroke)
                    val progress = fills[i].value
                    if (progress <= 0f) continue
                    val alpha = fillAlpha.value
                    if (progress >= 1f) {
                        drawPath(arrow, filled, alpha = alpha, style = stroke)
                    } else {
                        // The colored part grows along the shaft and reaches the tip as progress hits 1.
                        val x = start + (end - start) * progress
                        val bar = Path().apply {
                            moveTo(start, y)
                            lineTo(x, y)
                        }
                        drawPath(bar, filled, alpha = alpha, style = stroke)
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
