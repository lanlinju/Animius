package com.lanlinju.animius.presentation.screen.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lanlinju.animius.R
import com.lanlinju.animius.domain.model.History
import com.lanlinju.animius.presentation.component.BackTopAppBar
import com.lanlinju.animius.presentation.component.LoadingIndicator
import com.lanlinju.animius.presentation.component.PopupMenuListItem
import com.lanlinju.animius.presentation.component.SourceBadge
import com.lanlinju.animius.presentation.component.StateHandler
import com.lanlinju.animius.util.CROSSFADE_DURATION
import com.lanlinju.animius.util.LOW_CONTENT_ALPHA
import com.lanlinju.animius.util.SourceMode
import com.lanlinju.animius.util.VIDEO_ASPECT_RATIO
import com.lanlinju.animius.util.focus.focusedIconButtonColors
import com.lanlinju.animius.util.focus.focusedTextButtonColors
import com.lanlinju.animius.util.focus.rememberIsFocused

@Composable
fun HistoryScreen(
    onBackClick: () -> Unit,
    onNavigateToAnimeDetail: (detailUrl: String, mode: SourceMode) -> Unit,
) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val historyListState by viewModel.historyList.collectAsState()
    var showDeleteAllHistoriesDialog by remember { mutableStateOf(false) }

    StateHandler(state = historyListState,
        onLoading = { LoadingIndicator() },
        onFailure = {}
    ) { resource ->
        resource.data?.let { histories ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
            ) {
                BackTopAppBar(
                    title = stringResource(id = R.string.play_history),
                    actions = {
                        DeleteHistoryButton(onClick = { showDeleteAllHistoriesDialog = true })
                    },
                    onBackClick = onBackClick
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.small_padding))
                ) {
                    items(histories) { history ->
                        PopupMenuListItem(
                            menuText = stringResource(id = R.string.delete),
                            onClick = {
                                onNavigateToAnimeDetail(history.detailUrl, history.sourceMode)
                            },
                            onMenuItemClick = { viewModel.deleteHistory(history.detailUrl) }
                        ) {
                            HistoryItem(history = history)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteAllHistoriesDialog) {
        DeleteAllHistoriesDialog(
            onDismissRequest = { showDeleteAllHistoriesDialog = false },
            onDeleteAllHistories = viewModel::deleteAllHistories
        )
    }
}

@Composable
fun HistoryItem(
    modifier: Modifier = Modifier,
    history: History,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensionResource(id = R.dimen.history_item_height)),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = dimensionResource(id = R.dimen.small_padding),
                vertical = 4.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.small_padding))
        ) {
            SourceBadge(
                text = history.sourceMode.name,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(history.imgUrl)
                        .crossfade(CROSSFADE_DURATION)
                        .build(),
                    contentDescription = stringResource(id = R.string.lbl_anime_img),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(VIDEO_ASPECT_RATIO)
                        .clip(RoundedCornerShape(dimensionResource(id = R.dimen.history_cover_radius)))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = history.title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = history.lastEpisodeName,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = LOW_CONTENT_ALPHA),
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Text(
                        text = history.time,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = LOW_CONTENT_ALPHA),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

            }
        }
    }

}

@Composable
fun DeleteHistoryButton(
    onClick: () -> Unit,
) {
    val (isFocused, focusModifier) = rememberIsFocused()
    IconButton(
        onClick = onClick,
        colors = focusedIconButtonColors(isFocused),
        modifier = focusModifier
    ) {
        Icon(
            Icons.Outlined.DeleteOutline,
            contentDescription = "Delete",
            tint = if (isFocused) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun DeleteAllHistoriesDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    onDeleteAllHistories: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.clear_all_histories)) },
        text = { Text(text = stringResource(R.string.are_you_sure_you_want_to_clear_all_histories)) },
        confirmButton = {
            val (isFocused, focusModifier) = rememberIsFocused()
            TextButton(
                onClick = {
                    onDismissRequest()
                    onDeleteAllHistories()
                },
                modifier = Modifier.then(focusModifier),
                colors = focusedTextButtonColors(isFocused)
            ) {
                Text(stringResource(id = R.string.confirm))
            }
        },
        dismissButton = {
            val (isFocused, focusModifier) = rememberIsFocused()
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.then(focusModifier),
                colors = focusedTextButtonColors(isFocused)
            ) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}
