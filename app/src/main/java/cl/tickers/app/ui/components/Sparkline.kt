package cl.tickers.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.tickers.app.R
import cl.tickers.app.domain.model.UfValue

/**
 * A minimal line chart drawn on a Canvas.
 *
 * Deliberately not a charting library: the app needs one line with a fill and
 * a scrub cursor, and a dependency for that would cost more than it saves.
 *
 * [onScrub] reports the index the finger is over, or null when it lifts.
 */
@Composable
fun Sparkline(
    values: List<UfValue>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    selectedIndex: Int? = null,
    onScrub: (Int?) -> Unit = {},
) {
    if (values.size < 2) {
        // A blank rectangle reads as breakage; the condor reads as "nothing to
        // draw yet".
        Box(
            modifier.fillMaxWidth().height(height),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_condor),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(72.dp),
            )
        }
        return
    }

    val min = values.minOf { it.value }.toDouble()
    val max = values.maxOf { it.value }.toDouble()
    val span = (max - min).takeIf { it > 0.0 } ?: 1.0
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            // Horizontal only. detectDragGestures claimed vertical drags too,
            // so a finger that started on the chart could not scroll the page
            // it sits in.
            .pointerInput(values) {
                detectHorizontalDragGestures(
                    onDragEnd = { onScrub(null) },
                    onDragCancel = { onScrub(null) },
                ) { change, _ ->
                    val i = ((change.position.x / size.width) * (values.size - 1))
                        .toInt().coerceIn(0, values.size - 1)
                    onScrub(i)
                }
            }
            .pointerInput(values) {
                detectTapGestures { pos ->
                    val i = ((pos.x / size.width) * (values.size - 1))
                        .toInt().coerceIn(0, values.size - 1)
                    onScrub(i)
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val padV = h * 0.10f

        fun xAt(i: Int) = w * i / (values.size - 1).toFloat()
        fun yAt(v: Double) = (h - padV) - ((v - min) / span).toFloat() * (h - 2 * padV)

        // Horizontal guides at the extremes only; a full grid is visual noise.
        listOf(padV, h - padV).forEach { y ->
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        val line = Path().apply {
            moveTo(xAt(0), yAt(values[0].value.toDouble()))
            for (i in 1 until values.size) lineTo(xAt(i), yAt(values[i].value.toDouble()))
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(xAt(values.size - 1), h)
            lineTo(xAt(0), h)
            close()
        }

        drawPath(
            fill,
            Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.18f), lineColor.copy(alpha = 0f))
            ),
        )
        drawPath(line, lineColor, style = Stroke(width = 2.5f))

        selectedIndex?.let { i ->
            if (i in values.indices) {
                val x = xAt(i)
                val y = yAt(values[i].value.toDouble())
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1.5f)
                drawCircle(lineColor, radius = 5f, center = Offset(x, y))
            }
        }
    }
}
