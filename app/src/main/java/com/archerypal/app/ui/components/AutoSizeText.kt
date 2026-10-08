package com.archerypal.app.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Single-line label that shrinks its font (down to [minScale]) instead of wrapping
 * mid-word when the available width is too narrow, e.g. small phones or large
 * system font sizes. Ellipsizes only if it still doesn't fit at [minScale].
 */
@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    minScale: Float = 0.7f
) {
    val baseSize = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    val fontScale = LocalDensity.current.fontScale
    var scale by remember(text, baseSize, fontScale) { mutableFloatStateOf(1f) }
    var ready by remember(text, baseSize, fontScale) { mutableStateOf(false) }
    val atMin = scale <= minScale

    Text(
        text = text,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        style = style.copy(fontSize = baseSize * scale),
        maxLines = 1,
        softWrap = false,
        overflow = if (atMin) TextOverflow.Ellipsis else TextOverflow.Clip,
        onTextLayout = { result ->
            if (result.didOverflowWidth && !atMin) {
                scale = (scale * 0.9f).coerceAtLeast(minScale)
            } else {
                ready = true
            }
        }
    )
}
