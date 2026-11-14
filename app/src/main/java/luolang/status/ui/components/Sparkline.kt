package luolang.status.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * MIUIX 风格柔滑折线：Catmull-Rom 平滑曲线 + 渐变填充 + 最右端呼吸灯。
 */
@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    maxValue: Float = 100f,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas

        val max = maxValue.coerceAtLeast(1f)
        val stepX = if (values.size == 1) 0f else size.width / (values.size - 1).toFloat()
        val points = values.mapIndexed { i, v ->
            val y = (1f - (v / max).coerceIn(0f, 1f)) * size.height
            Offset(i * stepX, y)
        }

        if (points.size >= 2) {
            val line = smoothPath(points)

            // 渐变填充
            val fill = Path().apply {
                addPath(line)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            }
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.26f), color.copy(alpha = 0f)),
                    startY = 0f,
                    endY = size.height,
                ),
            )

            drawPath(
                path = line,
                color = color,
                style = Stroke(
                    width = 4f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        // 呼吸灯
        val last = points.last()
        drawCircle(
            color = color.copy(alpha = 0.25f * pulse),
            radius = 12f,
            center = last,
        )
        drawCircle(color = color, radius = 4.5f, center = last)
    }
}

/** Catmull-Rom 转三次贝塞尔，得到柔滑曲线。 */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }
    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.lastIndex)]

        val c1x = p1.x + (p2.x - p0.x) / 6f
        val c1y = p1.y + (p2.y - p0.y) / 6f
        val c2x = p2.x - (p3.x - p1.x) / 6f
        val c2y = p2.y - (p3.y - p1.y) / 6f
        path.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
    }
    return path
}
