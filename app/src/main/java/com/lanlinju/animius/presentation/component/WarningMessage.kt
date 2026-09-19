package com.lanlinju.animius.presentation.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lanlinju.animius.R
import com.lanlinju.animius.util.focus.focusedOutlinedButtonColors
import com.lanlinju.animius.util.focus.rememberIsFocused

@Composable
fun WarningMessage(
    @StringRes textId: Int,
    extraText: String = "",
    onRetryClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            tint = MaterialTheme.colorScheme.onSurface,
            contentDescription = ""
        )
        Spacer(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = stringResource(id = textId),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = extraText,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )
        if (onRetryClick != null) {
            val (isFocused, focusModifier) = rememberIsFocused()
            OutlinedButton(
                onClick = onRetryClick,
                modifier = Modifier.then(focusModifier),
                colors = focusedOutlinedButtonColors(
                    isFocused,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Text(text = stringResource(id = R.string.lbl_retry))
            }
        }

    }

}