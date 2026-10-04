package com.lanlinju.animius.util.focus

import androidx.compose.foundation.background
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Replaces the repetitive `var isFocused by remember { mutableStateOf(false) }`
 * + `Modifier.onFocusChanged { isFocused = it.isFocused }` boilerplate.
 *
 * Returns a [Pair] of `(isFocused: Boolean, focusModifier: Modifier)`.
 * Attach the modifier to your composable; read `isFocused` for colors.
 *
 * Usage:
 * ```
 * val (isFocused, focusModifier) = rememberIsFocused()
 * IconButton(
 *     modifier = Modifier.then(focusModifier),
 *     colors = focusedIconButtonColors(isFocused)
 * ) {
 *     Icon(tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else Color.White)
 * }
 * ```
 */
@Composable
fun rememberIsFocused(): Pair<Boolean, Modifier> {
    var isFocused by remember { mutableStateOf(false) }
    return isFocused to Modifier.onFocusChanged { isFocused = it.isFocused }
}

/**
 * The single definition of the TV focus palette: primary container + onPrimary
 * content while focused, the caller's colors otherwise. Every `focused*Colors`
 * helper below and [FocusedDropdownMenuItem] resolve through this, so the
 * highlight only has to change in one place.
 *
 * Returns `(containerColor, contentColor)`.
 */
@Composable
private fun focusHighlightColors(
    isFocused: Boolean,
    unfocusedContainerColor: Color,
    unfocusedContentColor: Color,
): Pair<Color, Color> = if (isFocused) {
    MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
} else {
    unfocusedContainerColor to unfocusedContentColor
}

/**
 * IconButton colors with the shared TV focus highlight: primary container +
 * onPrimary content while focused, transparent container otherwise.
 */
@Composable
fun focusedIconButtonColors(
    isFocused: Boolean,
    unfocusedContentColor: Color = MaterialTheme.colorScheme.onSurface,
    unfocusedContainerColor: Color = Color.Transparent,
): IconButtonColors {
    val (container, content) =
        focusHighlightColors(isFocused, unfocusedContainerColor, unfocusedContentColor)
    return IconButtonDefaults.iconButtonColors(containerColor = container, contentColor = content)
}

/**
 * TextButton colors with the shared TV focus highlight.
 */
@Composable
fun focusedTextButtonColors(
    isFocused: Boolean,
    unfocusedContentColor: Color = MaterialTheme.colorScheme.primary,
): ButtonColors {
    val (container, content) =
        focusHighlightColors(isFocused, Color.Transparent, unfocusedContentColor)
    return ButtonDefaults.textButtonColors(containerColor = container, contentColor = content)
}

/**
 * OutlinedButton colors with the shared TV focus highlight.
 */
@Composable
fun focusedOutlinedButtonColors(
    isFocused: Boolean,
    unfocusedContainerColor: Color = Color.Transparent,
    unfocusedContentColor: Color = MaterialTheme.colorScheme.primary,
): ButtonColors {
    val (container, content) =
        focusHighlightColors(isFocused, unfocusedContainerColor, unfocusedContentColor)
    return ButtonDefaults.outlinedButtonColors(containerColor = container, contentColor = content)
}

/**
 * DropdownMenuItem with the shared TV focus highlight (primary container +
 * onPrimary text while focused).
 */
@Composable
fun FocusedDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val (focused, focusModifier) = rememberIsFocused()
    val (container, content) = focusHighlightColors(
        isFocused = focused,
        unfocusedContainerColor = Color.Transparent,
        unfocusedContentColor = MaterialTheme.colorScheme.onSurface,
    )
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = style,
                color = content
            )
        },
        onClick = onClick,
        modifier = focusModifier
            .then(if (focused) Modifier.background(container) else Modifier)
            .then(modifier)
    )
}

