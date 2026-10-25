package app.slothie.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.sin

/** How the sloth feels about your phone right now. */
enum class Mood(val description: String) {
    Sleeping("fast asleep, you are barely on the phone"),
    Chill("relaxed, you are about on your usual pace"),
    Peeking("one eye open, a risky hour is close"),
    Alarmed("wide awake, you are well past your usual pace"),
}

/**
 * The sloth, drawn with plain shapes so there is no asset pipeline to fall over. It hangs from a
 * branch, sways a little, breathes, and its face follows [mood].
 */
@Composable
fun Sloth(mood: Mood, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "sloth")
    val sway by t.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (mood == Mood.Alarmed) 900 else 2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway",
    )
    val breath by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "breath",
    )
    val zzz by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "zzz",
    )

    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = "Slothie is ${mood.description}" }) {
        val w = size.width
        val u = w / 100f
        val swayDeg = sway * (if (mood == Mood.Alarmed) 4f else 2.2f)
        val breathe = 1f + 0.015f * sin(breath * 2f * PI).toFloat()

        // branch
        drawRoundRect(Palette.bark, Offset(6 * u, 14 * u), Size(88 * u, 7 * u), androidx.compose.ui.geometry.CornerRadius(4 * u))
        drawCircle(Color(0xFF8FB996), 4 * u, Offset(18 * u, 13 * u))
        drawCircle(Color(0xFF6F9A77), 3 * u, Offset(84 * u, 13 * u))

        rotate(swayDeg, pivot = Offset(50 * u, 17 * u)) {
            // arms reaching up to the branch
            arm(u, 36f, 24f, 42f, 46f)
            arm(u, 64f, 24f, 58f, 46f)
            // claws
            for (x in listOf(32f, 36f, 40f, 60f, 64f, 68f)) {
                drawLine(Palette.furDark, Offset(x * u, 18 * u), Offset(x * u, 23 * u), 1.6f * u, StrokeCap.Round)
            }
            translate(0f, (1f - breathe) * 40 * u) {
                // body
                drawOval(Palette.fur, Offset(28 * u, 40 * u), Size(44 * u * breathe, 44 * u * breathe))
                drawOval(Color(0xFFC7AE8F), Offset(36 * u, 52 * u), Size(28 * u, 28 * u))
                // head
                drawOval(Palette.fur, Offset(31 * u, 36 * u), Size(38 * u, 34 * u))
                // face
                drawOval(Palette.furLight, Offset(37 * u, 42 * u), Size(26 * u, 24 * u))
                // eye patches
                patch(u, 41f, 50f, -18f)
                patch(u, 59f, 50f, 18f)
                // eyes
                when (mood) {
                    Mood.Sleeping -> { closedEye(u, 45f, 52f); closedEye(u, 55f, 52f) }
                    Mood.Chill -> { openEye(u, 45f, 52f, 1.6f); openEye(u, 55f, 52f, 1.6f) }
                    Mood.Peeking -> { closedEye(u, 45f, 52f); openEye(u, 55f, 52f, 1.9f) }
                    Mood.Alarmed -> { openEye(u, 45f, 51f, 2.6f); openEye(u, 55f, 51f, 2.6f) }
                }
                // nose
                drawOval(Palette.furDark, Offset(47.5f * u, 56 * u), Size(5 * u, 3.6f * u))
                // mouth
                val mouth = Path()
                when (mood) {
                    Mood.Alarmed -> { mouth.moveTo(46 * u, 62 * u); mouth.quadraticTo(50 * u, 59 * u, 54 * u, 62 * u) }
                    Mood.Peeking -> { mouth.moveTo(46 * u, 61 * u); mouth.lineTo(54 * u, 61.5f * u) }
                    else -> { mouth.moveTo(45 * u, 60 * u); mouth.quadraticTo(50 * u, 65 * u, 55 * u, 60 * u) }
                }
                drawPath(mouth, Palette.furDark, style = Stroke(1.5f * u, cap = StrokeCap.Round))
                // cheeks
                if (mood == Mood.Sleeping || mood == Mood.Chill) {
                    drawCircle(Color(0x55E97B77), 2.6f * u, Offset(41 * u, 58 * u))
                    drawCircle(Color(0x55E97B77), 2.6f * u, Offset(59 * u, 58 * u))
                }
                // legs tucked
                drawOval(Palette.fur, Offset(33 * u, 70 * u), Size(14 * u, 12 * u))
                drawOval(Palette.fur, Offset(53 * u, 70 * u), Size(14 * u, 12 * u))
            }
        }

        if (mood == Mood.Sleeping) {
            // floating z's
            for (i in 0 until 3) {
                val p = (zzz + i * 0.33f) % 1f
                val alpha = (1f - p).coerceIn(0f, 1f)
                drawCircle(Palette.sky.copy(alpha = alpha * 0.9f), (2.2f + i) * u * (0.6f + p), Offset((72 + 10 * p + i * 3) * u, (40 - 22 * p) * u))
            }
        }
        if (mood == Mood.Alarmed) {
            // a little sweat drop
            drawOval(Palette.sky, Offset(66 * u, 42 * u), Size(3 * u, 5 * u))
        }
    }
}

private fun DrawScope.arm(u: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
    drawLine(Palette.fur, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), 9f * u, StrokeCap.Round)
}

private fun DrawScope.patch(u: Float, cx: Float, cy: Float, deg: Float) {
    rotate(deg, pivot = Offset(cx * u, cy * u)) {
        drawOval(Palette.furDark, Offset((cx - 6) * u, (cy - 3.2f) * u), Size(12 * u, 6.4f * u))
    }
}

private fun DrawScope.openEye(u: Float, cx: Float, cy: Float, r: Float) {
    drawCircle(Color.White, r * u, Offset(cx * u, cy * u))
    drawCircle(Color(0xFF1B1A17), r * 0.6f * u, Offset((cx + 0.3f) * u, (cy + 0.2f) * u))
    drawCircle(Color.White, r * 0.22f * u, Offset((cx - 0.5f) * u, (cy - 0.6f) * u))
}

private fun DrawScope.closedEye(u: Float, cx: Float, cy: Float) {
    val p = Path()
    p.moveTo((cx - 2.4f) * u, cy * u)
    p.quadraticTo(cx * u, (cy + 2.2f) * u, (cx + 2.4f) * u, cy * u)
    drawPath(p, Color(0xFF1B1A17), style = Stroke(1.3f * u, cap = StrokeCap.Round))
}
