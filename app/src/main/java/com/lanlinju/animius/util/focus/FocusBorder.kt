package com.lanlinju.animius.util.focus

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * Draws a border around the composable that responds to focus state changes
 * **without triggering recomposition**.
 *
 * Focus state is stored in a [mutableStateOf] but is only read inside
 * [drawWithContent]'s draw lambda. Because the state read happens in the draw
 * phase (not in composition), a focus change invalidates only the draw layer —
 * no recomposition, no relayout.
 *
 * Uses [Modifier.composed] so this can be called in a regular modifier chain
 * without requiring a @Composable call site.
 *
 * @param shape the shape of the border outline
 * @param width border stroke width
 * @param focusedColor border color when this composable has focus
 * @param unfocusedColor border color when no focus; [Color.Transparent] by default
 */
fun Modifier.focusBorder(
    shape: Shape,
    width: Dp,
    focusedColor: Color,
    unfocusedColor: Color = Color.Transparent,
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { isFocused = it.isFocused }
        .drawWithContent {
            drawContent()
            val color = if (isFocused) focusedColor else unfocusedColor
            if (color != Color.Transparent) {
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(outline, color, style = Stroke(width.toPx()))
            }
        }
}
