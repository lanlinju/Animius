package com.lanlinju.animius.presentation.screen.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lanlinju.animius.R
import com.lanlinju.animius.domain.model.Download
import com.lanlinju.animius.presentation.component.BackTopAppBar
import com.lanlinju.animius.presentation.component.LoadingIndicator
import com.lanlinju.animius.presentation.component.PopupMenuListItem
import com.lanlinju.animius.presentation.component.SourceBadge
import com.lanlinju.animius.presentation.component.StateHandler
import com.lanlinju.animius.util.CROSSFADE_DURATION
import com.lanlinju.animius.util.LOW_CONTENT_ALPHA
import com.lanlinju.animius.util.SourceMode
import com.lanlinju.animius.util.VIDEO_ASPECT_RATIO
import com.lanlinju.download.utils.formatSize

@Composable
fun DownloadScreen(
    onNavigateToAnimeDetail: (detailUrl: String, mode: SourceMode) -> Unit,
    onNavigateToDownloadDetail: (detailUrl: String, title: String, sourceMode: SourceMode) -> Unit,
    onBackClick: () -> Unit
) {
    val viewModel: DownloadViewModel = hiltViewModel()
    val downloadState = viewModel.downloadList.collectAsState()
    val context = LocalContext.current

    StateHandler(
        state = downloadState.value,
        onLoading = { LoadingIndicator() }, onFailure = {}) { resource ->
        Scaffold(
            topBar = {
                BackTopAppBar(
                    title = stringResource(id = R.string.download_list),
                    onBackClick = onBackClick
                )
            }
        ) { paddingValues ->
            resource.data?.let { downloads ->
                LazyColumn(modifier = Modifier.padding(paddingValues)) {
                    items(downloads, key = { item -> item.detailUrl }) { download ->

                        SideEffect {
                            if (download.downloadDetails.isEmpty()) {
                                viewModel.deleteDownload(
                                    download.detailUrl, download.title, context
                                )
                            }
                        }

                        PopupMenuListItem(
                            content = {
                                DownloadItem(download = download)
                            },
                            menuText = stringResource(id = R.string.anime_detail),
                            onClick = {
                                onNavigateToDownloadDetail(download.detailUrl, download.title, download.sourceMode)
                            },
                            onMenuItemClick = {
                                onNavigateToAnimeDetail(
                                    download.detailUrl,
                                    download.sourceMode
                                )
                            }
                        )

                    }
                }
            }
        }
    }
}

@Composable
fun DownloadItem(
    modifier: Modifier = Modifier,
    download: Download,
) {
    Surface(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(
                    horizontal = dimensionResource(id = R.dimen.small_padding),
                    vertical = dimensionResource(id = R.dimen.small_padding)
                ),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.small_padding))
        ) {
            SourceBadge(text = download.sourceMode.name) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(download.imgUrl)
                        .crossfade(CROSSFADE_DURATION)
                        .build(),
                    contentDescription = stringResource(id = R.string.lbl_anime_img),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(dimensionResource(id = R.dimen.image_cover_height))
                        .aspectRatio(VIDEO_ASPECT_RATIO)
                        .clip(RoundedCornerShape(dimensionResource(id = R.dimen.small_padding)))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = download.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "共${download.downloadDetails.size}个",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = LOW_CONTENT_ALPHA),
                        style = MaterialTheme.typography.bodySmall,
                    )

                    val sizeStr =
                        stringResource(id = R.string.space_usage) + ": " + download.totalSize.formatSize()

                    Text(
                        text = sizeStr,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = LOW_CONTENT_ALPHA),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

            }

        }
    }

}