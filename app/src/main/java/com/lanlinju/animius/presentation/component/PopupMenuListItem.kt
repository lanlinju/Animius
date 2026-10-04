package com.lanlinju.animius.presentation.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.lanlinju.animius.R
import com.lanlinju.animius.util.VIDEO_ASPECT_RATIO
import com.lanlinju.animius.util.focus.FocusedDropdownMenuItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PopupMenuListItem(
    menuText: String,
    onClick: () -> Unit,
    onMenuItemClick: () -> Unit,
    content: @Composable () -> Unit,
) {

    var expanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .combinedClickable(
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    expanded = true
                },
                onClick = onClick
            )
            .onPreviewKeyEvent { event ->
                when {
                    event.key == Key.Menu && event.type == KeyEventType.KeyDown -> {
                        if (expanded) expanded = false else expanded = true
                        true
                    }
                    else -> false
                }
            }
    ) {

        content()

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(
                x = dimensionResource(id = R.dimen.image_cover_height) * VIDEO_ASPECT_RATIO + dimensionResource(
                    id = R.dimen.small_padding
                ),
                y = 0.dp
            ),
        ) {

            FocusedDropdownMenuItem(
                text = menuText,
                onClick = {
                    expanded = false
                    onMenuItemClick()
                }
            )

        }
    }
}