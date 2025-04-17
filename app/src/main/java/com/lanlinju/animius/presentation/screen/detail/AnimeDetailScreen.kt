package com.lanlinju.animius.presentation.screen.detail

import android.content.res.Configuration
import android.graphics.Bitmap
import android.text.Html
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lanlinju.animius.R
import com.lanlinju.animius.domain.model.Anime
import com.lanlinju.animius.domain.model.AnimeDetail
import com.lanlinju.animius.domain.model.Download
import com.lanlinju.animius.domain.model.DownloadDetail
import com.lanlinju.animius.domain.model.Episode
import com.lanlinju.animius.domain.model.Favourite
import com.lanlinju.animius.domain.model.History
import com.lanlinju.animius.presentation.component.LoadingIndicator
import com.lanlinju.animius.presentation.component.MediaSmall
import com.lanlinju.animius.presentation.component.MediaSmallRow
import com.lanlinju.animius.presentation.component.ScrollableText
import com.lanlinju.animius.presentation.component.StateHandler
import com.lanlinju.animius.presentation.component.TranslucentStatusBarLayout
import com.lanlinju.animius.presentation.component.WarningMessage
import com.lanlinju.animius.presentation.navigation.PlayerParameters
import com.lanlinju.animius.util.CROSSFADE_DURATION
import com.lanlinju.animius.util.KEY_DYNAMIC_IMAGE_COLOR
import com.lanlinju.animius.util.SettingsPreferences
import com.lanlinju.animius.util.SourceHolder
import com.lanlinju.animius.util.SourceMode
import com.lanlinju.animius.util.bannerParallax
import com.lanlinju.animius.util.dynamicColorOf
import com.lanlinju.animius.util.isAndroidTV
import com.lanlinju.animius.util.isWideScreen
import com.lanlinju.animius.util.log
import com.lanlinju.animius.util.rememberPreference
import kotlinx.coroutines.launch
import java.io.File
import com.lanlinju.animius.R as Res

@Composable
fun AnimeDetailScreen(
    viewModel: AnimeDetailViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onRelatedAnimeClick: (detailUrl: String, mode: SourceMode) -> Unit,
    onNavigateToVideoPlay: (parameters: String) -> Unit
) {
    val scrollState = rememberScrollState()
    val bannerHeight = dimensionResource(Res.dimen.banner_height)

    val animeDetailState by viewModel.animeDetailState.collectAsStateWithLifecycle()
    val isFavourite by viewModel.isFavourite.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    StateHandler(
        state = animeDetailState,
        onLoading = { LoadingIndicator() },
        onFailure = {
            WarningMessage(
                textId = Res.string.txt_empty_result,
                extraText = it.error?.message ?: stringResource(id = R.string.unknown_error),
                onRetryClick = { viewModel.retry() }
            )
        }
    ) { resource ->
        resource.data?.let { animeDetail ->
            TranslucentStatusBarLayout(
                scrollState = scrollState,
                distanceUntilAnimated = bannerHeight
            ) {
                var reverseList by rememberSaveable { mutableStateOf(false) }
                var showBottomSheet by remember { mutableStateOf(false) }
                var showChannelSelectorDialog by remember { mutableStateOf(false) }
                var showDownloadBottomSheet by remember { mutableStateOf(false) }

                val background = Color(
                    ColorUtils.blendARGB(
                        MaterialTheme.colorScheme.background.toArgb(),
                        MaterialTheme.colorScheme.primaryContainer.toArgb(),
                        0.05f
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(background)
                        .verticalScroll(scrollState)
                ) {

                    val isDynamicImageColor by rememberPreference(
                        key = KEY_DYNAMIC_IMAGE_COLOR,
                        defaultValue = true
                    )

                    AnimeBanner(
                        imageUrl = animeDetail.img,
                        tintColor = { Color(0).copy(alpha = 0.25f) },
                        modifier = Modifier
                            .height(bannerHeight)
                            .fillMaxWidth()
                            .bannerParallax(scrollState)
                    )

                    TopAppBar(
                        detailUrl = viewModel.detailUrl,
                        onBackClick = onBackClick,
                        onDownloadClick = { showDownloadBottomSheet = true }
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = bannerHeight)
                            .background(background),
                        verticalArrangement = Arrangement.spacedBy(dimensionResource(Res.dimen.large_padding))
                    ) {
                        AnimeDetails(
                            title = animeDetail.title,
                            description = Html
                                .fromHtml(animeDetail.desc, Html.FROM_HTML_MODE_COMPACT)
                                .toString(),
                            modifier = Modifier
                                .padding(
                                    start = dimensionResource(Res.dimen.large_padding)
                                            + dimensionResource(Res.dimen.media_card_width)
                                            + dimensionResource(Res.dimen.large_padding),
                                    top = dimensionResource(Res.dimen.medium_padding),
                                    end = dimensionResource(Res.dimen.large_padding)
                                )
                                .height(
                                    WindowInsets.statusBars
                                        .asPaddingValues()
                                        .calculateTopPadding()
                                            + dimensionResource(Res.dimen.media_card_top_padding)
                                            + dimensionResource(Res.dimen.media_card_height)
                                            - dimensionResource(Res.dimen.banner_height)
                                            - dimensionResource(Res.dimen.medium_padding)
                                )
                        )

                        AnimeGenres(
                            genres = animeDetail.tags,
                            contentPadding = PaddingValues(
                                start = dimensionResource(Res.dimen.large_padding) + if (
                                    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                                ) {
                                    WindowInsets.displayCutout.asPaddingValues()
                                        .calculateLeftPadding(LayoutDirection.Ltr)
                                } else 0.dp,
                                end = dimensionResource(Res.dimen.large_padding)
                            ),
                            color = MaterialTheme.colorScheme.inversePrimary
                        )

                        Box {
                            AnimeEpisodes(
                                episodes = animeDetail.episodes,
                                lastPosition = animeDetail.lastPosition,
                                contentPadding = PaddingValues(
                                    start = dimensionResource(Res.dimen.large_padding) + if (
                                        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                                    ) {
                                        WindowInsets.displayCutout.asPaddingValues()
                                            .calculateLeftPadding(LayoutDirection.Ltr)
                                    } else 0.dp,
                                    end = dimensionResource(Res.dimen.large_padding)
                                ),
                                reverseList = reverseList,
                                onEpisodeClick = { episodeIndex ->
                                    // TODO: 优化
                                    with(animeDetail) {
                                        val history =
                                            History(
                                                title = title,
                                                imgUrl = img,
                                                detailUrl = viewModel.detailUrl,
                                                episodes = listOf(episodes[episodeIndex]),
                                                sourceMode = viewModel.mode
                                            )
                                        viewModel.addHistory(history)
                                        scope.launch {
                                            PlayerParameters.serialize(
                                                title = title,
                                                episodeIndex = episodeIndex,
                                                episodes = if (reverseList) episodes.reversed() else episodes,
                                                mode = viewModel.mode
                                            ).let { onNavigateToVideoPlay(it) }
                                        }
                                    }
                                }
                            )

                            EpisodeListControl(
                                modifier = Modifier.align(Alignment.BottomEnd),
                                channelIndex = animeDetail.channelIndex,
                                isShowChannel = animeDetail.channels.size > 1,
                                onReverseClick = { reverseList = !reverseList },
                                onMoreClick = { showBottomSheet = true },
                                onChannelClick = { showChannelSelectorDialog = true }
                            )
                        }

                        AnimeRelated(
                            animes = animeDetail.relatedAnimes,
                            contentPadding = PaddingValues(horizontal = dimensionResource(Res.dimen.large_padding)),
                            onRelatedAnimeClick = { onRelatedAnimeClick(it, viewModel.mode) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(
                                top = dimensionResource(Res.dimen.media_card_top_padding),
                                start = dimensionResource(Res.dimen.large_padding),
                                end = dimensionResource(Res.dimen.large_padding)
                            )
                    ) {
                        MediaSmall(
                            image = animeDetail.img,
                            label = null,
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.width(dimensionResource(Res.dimen.media_card_width)),
                            onSuccess = { bitmap ->
                                if (isDynamicImageColor) {
                                    scope.launch {
                                        val newBitmap =
                                            bitmap.copy(Bitmap.Config.ARGB_8888, true)
                                        dynamicColorOf(newBitmap)?.let {
                                            SettingsPreferences.applyImageColor(it.toArgb())
                                        }
                                    }
                                }
                            }
                        )

                        FavouriteIcon(isFavourite, animeDetail, viewModel)
                    }

                    if (showBottomSheet) {
                        EpisodeBottomSheet(
                            episodes = animeDetail.episodes,
                            reverseList = reverseList,
                            lastPosition = animeDetail.lastPosition,
                            onDismissRequest = { showBottomSheet = false },
                            onEpisodeClick = { episodeIndex ->
                                // TODO: 优化
                                with(animeDetail) {
                                    val history =
                                        History(
                                            title = title,
                                            imgUrl = img,
                                            detailUrl = viewModel.detailUrl,
                                            episodes = listOf(episodes[episodeIndex]),
                                            sourceMode = viewModel.mode
                                        )
                                    viewModel.addHistory(history)
                                    scope.launch {
                                        PlayerParameters.serialize(
                                            title = title,
                                            episodeIndex = episodeIndex,
                                            episodes = if (reverseList) episodes.reversed() else episodes,
                                            mode = viewModel.mode
                                        ).let { onNavigateToVideoPlay(it) }
                                    }

                                }
                            }
                        )
                    }

                    if (showDownloadBottomSheet) {
                        val episodes =
                            viewModel.handleDownloadedEpisode(animeDetail.episodes)
                                .collectAsState(emptyList())
                        DownloadBottomSheet(
                            episodes = episodes.value,
                            reverseList = reverseList,
                            lastPosition = animeDetail.lastPosition,
                            onDismissRequest = { showDownloadBottomSheet = false },
                            onDownloadClick = { index, episode ->
                                val path =
                                    context.getExternalFilesDir("download/${viewModel.mode}/${animeDetail.title}")!!.path + "/${episode.name}.mp4"
                                val downloadDetail = DownloadDetail(
                                    title = episode.name,
                                    imgUrl = animeDetail.img,
                                    dramaNumber = index,
                                    path = path,
                                    downloadUrl = "" // 在viewModel中获取视频下载地址
                                )
                                val download = Download(
                                    title = animeDetail.title,
                                    detailUrl = viewModel.detailUrl,
                                    imgUrl = animeDetail.img,
                                    downloadDetails = listOf(downloadDetail),
                                    sourceMode = viewModel.mode
                                )
                                viewModel.addDownload(download, episode.url, File(path))
                            }
                        )
                    }

                    if (showChannelSelectorDialog) {
                        ChannelSelectorDialog(
                            onDismissRequest = { showChannelSelectorDialog = false },
                            onChannelClick = { i, l ->
                                showChannelSelectorDialog = false
                                viewModel.onChannelClick(i, l)
                            },
                            channels = animeDetail.channels,
                            channelIndex = animeDetail.channelIndex,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBar(
    detailUrl: String = "",
    onBackClick: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = { },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(id = R.string.back),
                    tint = Color.White.copy(alpha = 0.85f)
                )
            }
        },
        actions = {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(id = R.string.more),
                        tint = Color.White.copy(alpha = 0.85f)
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.download)) },
                        onClick = {
                            expanded = false
                            onDownloadClick()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                modifier = Modifier
                                    .size(24.dp)
                                    .rotate(90f),
                                contentDescription = stringResource(id = R.string.download)
                            )
                        }
                    )
                    val uriHandler = LocalUriHandler.current
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.website_address)) },
                        onClick = {
                            expanded = false
                            // gogoanime 的detailUrl包含域名地址，其他的不包含，所以需要判断一下
                            val url =
                                if (detailUrl.contains("http")) detailUrl else "${SourceHolder.currentSource.baseUrl}$detailUrl"
                            uriHandler.openUri(url)
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_domain),
                                contentDescription = stringResource(id = R.string.website_address)
                            )
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

@Composable
private fun FavouriteIcon(
    isFavourite: Boolean,
    animeDetail: AnimeDetail,
    viewModel: AnimeDetailViewModel,
) {
    val context = LocalContext.current
    val msg = stringResource(
        id = if (!isFavourite) Res.string.add_favourite else Res.string.remove_favourite
    )

    IconButton(
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.45f
            )
        ),
        onClick = {
            val favourite = Favourite(
                animeDetail.title,
                viewModel.detailUrl,
                animeDetail.img,
                sourceMode = viewModel.mode
            )
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.favourite(favourite)
        }) {
        Icon(
            if (isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = stringResource(id = Res.string.favourite),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun AnimeBanner(
    imageUrl: String?,
    tintColor: () -> Color,
    modifier: Modifier = Modifier
) {
    if (!imageUrl.isNullOrEmpty()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(CROSSFADE_DURATION)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .blur(if (isWideScreen(LocalContext.current)) 24.dp else 0.dp),
            alignment = Alignment.Center,
            colorFilter = ColorFilter.tint(
                color = tintColor(),
                blendMode = BlendMode.SrcAtop
            )
        )
    } else {
        Image(
            painter = painterResource(Res.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier,
            alignment = Alignment.TopCenter,
            colorFilter = ColorFilter.tint(
                color = tintColor(),
                blendMode = BlendMode.SrcAtop
            )
        )
    }
}

@Composable
fun AnimeDetails(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )

        ScrollableText(text = description)
    }
}


@Composable
fun AnimeGenres(
    genres: List<String>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    color: Color = MaterialTheme.colorScheme.primaryContainer
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(
            dimensionResource(Res.dimen.medium_padding)
        ),
        contentPadding = contentPadding,
        modifier = modifier
    ) {
        items(genres) { genre ->
            SuggestionChip(
                label = {
                    Text(
                        text = genre.uppercase(),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(
                            vertical = dimensionResource(Res.dimen.small_padding)
                        )
                    )
                },
                onClick = { },
                shape = CircleShape,
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = color.copy(alpha = 0.25f)
                ),
                border = SuggestionChipDefaults.suggestionChipBorder(
                    enabled = true,
                    borderColor = Color.Transparent
                ),
            )
        }
    }
}

@Composable
fun AnimeEpisodes(
    episodes: List<Episode>,
    lastPosition: Int,
    modifier: Modifier = Modifier,
    reverseList: Boolean,
    contentPadding: PaddingValues,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    onEpisodeClick: (episodeIndex: Int) -> Unit
) {
    val scrollState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (lastPosition < 3) 0 else lastPosition,
        initialFirstVisibleItemScrollOffset = if (lastPosition < 3) 0 else -200
    )

    val isAndroidTV = isAndroidTV(LocalContext.current)

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(Res.dimen.medium_padding)),
        contentPadding = contentPadding,
        modifier = modifier,
        state = scrollState
    ) {
        itemsIndexed(if (!reverseList) episodes else episodes.reversed()) { index, episode ->
            val focusRequester = remember { FocusRequester() }
            val interactionSource = remember { MutableInteractionSource() }
//            var focusIndex by rememberSaveable { mutableStateOf(lastPosition) } // 保存焦点位置
            FilledTonalButton(
                onClick = { onEpisodeClick(index) },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = color.copy(0.5f)),
                modifier = Modifier.run {
                    if (isAndroidTV) {
//                        onFocusChanged { if (it.isFocused) focusIndex = index }
                        clip(CircleShape)
                            .indication(interactionSource, LocalIndication.current)
                            .hoverable(interactionSource)
                            .focusRequester(focusRequester)
                            .focusable(interactionSource = interactionSource)
                    } else this
                }
            ) {
                Text(
                    text = episode.name,
                    color = if (episode.isPlayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(
                        vertical = dimensionResource(Res.dimen.small_padding)
                    )
                )
            }

            LaunchedEffect(Unit) {
                if (index == lastPosition && isAndroidTV) {
                    "focusRequester: ${lastPosition + 1}".log("AnimeDetailScreen")
                    focusRequester.requestFocus()
                }
            }
        }
    }
}

@Composable
private fun EpisodeListControl(
    modifier: Modifier = Modifier,
    channelIndex: Int,
    isShowChannel: Boolean,
    onReverseClick: () -> Unit,
    onMoreClick: () -> Unit,
    onChannelClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = dimensionResource(id = Res.dimen.large_padding) + 6.dp)
            .padding(
                top = dimensionResource(id = Res.dimen.small_padding),
                end = dimensionResource(id = Res.dimen.small_padding)
            ),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isShowChannel) {
            Text(
                modifier = Modifier.clickable(onClick = onChannelClick),
                text = stringResource(Res.string.channel_number, channelIndex + 1),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium
            )

            Spacer(modifier = Modifier.size(12.dp))
        }
        Text(
            modifier = Modifier.clickable(onClick = onReverseClick),
            text = stringResource(id = Res.string.reverse_list),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(modifier = Modifier.size(12.dp))

        Row(
            modifier = Modifier.clickable(onClick = onMoreClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = Res.string.more_episodes),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(id = Res.string.more_episodes),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AnimeRelated(
    animes: List<Anime>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onRelatedAnimeClick: (detailUrl: String) -> Unit
) {
    Column(modifier) {
        Text(
            text = stringResource(Res.string.lbl_related_anime),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(contentPadding)
        )

        Spacer(Modifier.size(dimensionResource(Res.dimen.medium_padding)))

        MediaSmallRow(
            mediaList = animes
        ) { anime ->
            MediaSmall(
                image = anime.img,
                label = anime.title,
                onClick = { onRelatedAnimeClick(anime.detailUrl) },
                modifier = Modifier.width(dimensionResource(Res.dimen.media_card_width))
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EpisodeBottomSheet(
    episodes: List<Episode>,
    reverseList: Boolean,
    lastPosition: Int,
    onDismissRequest: () -> Unit,
    onEpisodeClick: (episodeIndex: Int) -> Unit,
    isDownload: Boolean = false,
    onDownloadClick: (index: Int, episode: Episode) -> Unit = { _, _ -> },
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = Res.dimen.small_padding)),
            contentPadding = PaddingValues(horizontal = dimensionResource(id = Res.dimen.small_padding)),
            state = rememberLazyGridState(initialFirstVisibleItemIndex = lastPosition, -100)
        ) {
            if (isDownload) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(id = R.string.download_episode))
                }
            }
            itemsIndexed(
                items = if (!reverseList) episodes else episodes.reversed(),
                key = { _, e -> e.url }) { index, episode ->
                SuggestionChip(
                    onClick = {
                        when {
                            isDownload -> onDownloadClick(index, episode)
                            else -> onEpisodeClick(index)
                        }
                    },
                    label = {

                        val isPrimaryColor =
                            if (isDownload) episode.isDownloaded else episode.isPlayed

                        Text(
                            modifier = Modifier
                                .padding(end = dimensionResource(id = Res.dimen.small_padding))
                                .fillMaxWidth(),
                            text = episode.name,
                            color = if (isPrimaryColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )

                    }
                )
            }
        }
    }
}

@Composable
private fun DownloadBottomSheet(
    episodes: List<Episode>,
    reverseList: Boolean,
    lastPosition: Int,
    onDownloadClick: (index: Int, episode: Episode) -> Unit,
    onDismissRequest: () -> Unit,
) {
    EpisodeBottomSheet(
        episodes = episodes,
        reverseList = reverseList,
        lastPosition = lastPosition,
        isDownload = true,
        onDownloadClick = onDownloadClick,
        onDismissRequest = onDismissRequest,
        onEpisodeClick = { }
    )
}

@Composable
fun ChannelSelectorDialog(
    onDismissRequest: () -> Unit,
    channelIndex: Int,
    channels: Map<Int, List<Episode>>,
    onChannelClick: (index: Int, episodes: List<Episode>) -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(Res.string.channel_selection))
        },
        text = {
            // 显示所有剧集线路
            LazyColumn(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(channels.size) { index ->
                    Text(
                        text = stringResource(Res.string.channel_number, index + 1),
                        color = if (index == channelIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                onChannelClick(index, channels[index]!!)
                            }
                            .padding(16.dp)
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = R.string.confirm))
            }
        }
    )
}