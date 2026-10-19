package com.romanianguy.klover.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset

// changed digits roll like a counter; digits share one width so nothing around them shifts
@Composable
fun RollingText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
) {
    val motion = MaterialTheme.motionScheme
    val numberStyle = style.copy(fontFeatureSettings = "tnum")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // Runs of digits and runs of anything else, in order. Digits are keyed by their place counted from the right
        // of
        // their number, so a number that gains or loses a digit keeps the ones in the ones place.
        var i = 0
        var run = 0
        while (i < text.length) {
            val digits = text[i].isDigit()
            var end = i
            while (end < text.length && text[end].isDigit() == digits) end++
            val part = text.substring(i, end)
            key(run) {
                if (digits) {
                    Row {
                        part.forEachIndexed { index, ch ->
                            key(part.length - index) {
                                AnimatedContent(
                                    targetState = ch,
                                    modifier = Modifier.clipToBounds(),
                                    transitionSpec = {
                                        (slideInVertically(motion.defaultSpatialSpec<IntOffset>()) { -it } +
                                            fadeIn(motion.defaultEffectsSpec())) togetherWith
                                            (slideOutVertically(motion.defaultSpatialSpec<IntOffset>()) { it } +
                                                fadeOut(motion.fastEffectsSpec())) using
                                            SizeTransform(clip = false)
                                    },
                                    label = "rolling-digit",
                                ) { digit ->
                                    Text(
                                        digit.toString(),
                                        color = color,
                                        style = numberStyle,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(part, color = color, style = style, maxLines = 1, softWrap = false)
                }
            }
            run++
            i = end
        }
    }
}
