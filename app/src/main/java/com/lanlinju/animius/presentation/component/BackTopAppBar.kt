package com.lanlinju.animius.presentation.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.lanlinju.animius.R
import com.lanlinju.animius.util.focus.focusedIconButtonColors
import com.lanlinju.animius.util.focus.rememberIsFocused

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopAppBar(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    onBackClick: () -> Unit
) {
    val (backFocused, focusModifier) = rememberIsFocused()

    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                colors = focusedIconButtonColors(backFocused),
                modifier = focusModifier,
                onClick = onBackClick
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(id = R.string.back),
                    tint = if (backFocused) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = actions
    )
}