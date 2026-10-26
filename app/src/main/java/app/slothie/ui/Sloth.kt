package app.slothie.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import app.slothie.R
import kotlin.math.PI
import kotlin.math.sin

/** How the sloth feels about your phone right now. */
enum class Mood(val description: String, @DrawableRes val art: Int) {
    Sleeping("fast asleep, you are barely on the phone", R.drawable.sloth_sleeping),
    Chill("relaxed, you are about on your usual pace", R.drawable.sloth_chill),
    Peeking("one eye open, a risky hour is close", R.drawable.sloth_peeking),
    Alarmed("wide awake, you are well past your usual pace", R.drawable.sloth_alarmed),
}

/**
 * The sloth. One illustration per [mood], with a little life added in code: the sleeper
 * breathes, the hammock sways, the peeker tilts its head, and the alarmed one trembles.
 */
@Composable
fun Sloth(mood: Mood, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "sloth")
    val sway by t.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (mood == Mood.Alarmed) 160 else 2600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "sway",
    )
    val breath by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "breath",
    )
    val breathe = 1f + 0.012f * sin(breath * 2f * PI).toFloat()

    Image(
        painter = painterResource(mood.art),
        contentDescription = "Slothie is ${mood.description}",
        modifier = modifier.aspectRatio(1f).graphicsLayer {
            when (mood) {
                Mood.Sleeping -> {
                    scaleX = breathe; scaleY = breathe
                    transformOrigin = TransformOrigin(0.5f, 0.8f)
                }
                Mood.Chill -> {
                    rotationZ = sway * 3f
                    transformOrigin = TransformOrigin(0.5f, 0.1f)
                }
                Mood.Peeking -> {
                    rotationZ = sway * 1.5f
                    transformOrigin = TransformOrigin(0.5f, 0.9f)
                }
                Mood.Alarmed -> {
                    translationX = sway * 2.5f * density
                }
            }
        },
    )
}
