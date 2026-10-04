package com.lanlinju.animius.presentation.screen.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.lanlinju.animius.R
import com.lanlinju.animius.domain.model.Anime
import com.lanlinju.animius.domain.model.Home
import com.lanlinju.animius.presentation.component.LoadingIndicator
import com.lanlinju.animius.presentation.component.MediaSmall
import com.lanlinju.animius.presentation.component.MediaSmallRow
import com.lanlinju.animius.presentation.component.StateHandler
import com.lanlinju.animius.presentation.component.TranslucentStatusBarLayout
import com.lanlinju.animius.presentation.component.WarningMessage
import com.lanlinju.animius.util.KEY_HOME_BACKGROUND_URI
import com.lanlinju.animius.util.KEY_USE_GRID_LAYOUT
import com.lanlinju.animius.util.SettingsPreferences
import com.lanlinju.animius.util.SourceHolder
import com.lanlinju.animius.util.SourceMode
import com.lanlinju.animius.presentation.screen.week.SourceSwitchDialog
import com.lanlinju.animius.util.bannerParallax
import com.lanlinju.animius.util.isWideScreen
import com.lanlinju.animius.util.rememberPreference
import com.lanlinju.animius.util.focus.focusedIconButtonColors
import com.lanlinju.animius.util.focus.rememberIsFocused
import kotlinx.coroutines.launch
import com.lanlinju.animius.R as Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAnimeDetail: (detailUrl: String, mode: SourceMode) -> Unit,
) {
    val homeViewModel = hiltViewModel<HomeViewModel>()
    val availableDataList = homeViewModel.homeDataList.collectAsState()
    val isSourceChanged by SourceHolder.isSourceChanged.collectAsState()

    LaunchedEffect(isSourceChanged) {
        if (isSourceChanged > 0) {
            homeViewModel.refresh()
        }
    }

    Column(Modifier.fillMaxSize()) {
        StateHandler(
            state = availableDataList.value,
            onLoading = { LoadingIndicator() },
            onFailure = {
                WarningMessage(
                    textId = Res.string.txt_empty_result,
                    onRetryClick = { homeViewModel.refresh() }
                )
            }
        ) { resource ->
            val context = LocalContext.current
            val scrollState = rememberScrollState()
            var useGridLayout by rememberPreference(KEY_USE_GRID_LAYOUT, false)
            val isWideScreen = isWideScreen(context)
            val homeBackgroundColor = getBlendedBackgroundColor()

            TranslucentStatusBarLayout(
                scrollState = scrollState,
            ) {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(if (!isWideScreen) homeBackgroundColor else MaterialTheme.colorScheme.background)
                    .run { if (useGridLayout) this else verticalScroll(scrollState) }
                ) {

                    if (!isWideScreen) {
                        HomeBackground(
                            scrollState = scrollState,
                            useGridLayout = useGridLayout,
                            onSwitchLayout = { useGridLayout = it }
                        )
                    }

                    resource.data?.let { data ->
                        HomeContent(
                            data = data,
                            useGridLayout = useGridLayout,
                            isWideScreen = isWideScreen,
                            homeBackgroundColor = homeBackgroundColor,
                            onSwitchGridLayout = { useGridLayout = it },
                            onItemClick = {
                                onNavigateToAnimeDetail(
                                    it.detailUrl,
                                    SourceHolder.currentSourceMode
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
private fun HomeContent(
    data: List<Home>,
    useGridLayout: Boolean,
    isWideScreen: Boolean,
    homeBackgroundColor: Color,
    onSwitchGridLayout: (Boolean) -> Unit,
    onItemClick: (Anime) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        HomeBackgroundSpacer(isWideScreen, useGridLayout)

        val padding = if (useGridLayout) 0.dp else dimensionResource(Res.dimen.medium_padding)

        Column(
            modifier = Modifier
                .background(if (!isWideScreen) homeBackgroundColor else MaterialTheme.colorScheme.background)
                .padding(vertical = padding),
            verticalArrangement = Arrangement.spacedBy(padding),
        ) {
            if (isWideScreen) {
                HomeTile(
                    useGridLayout = useGridLayout,
                    onSwitchGridLayout = onSwitchGridLayout
                )
            }

            if (useGridLayout) {
                GridLayoutTabs(
                    data = data,
                    onItemClick = onItemClick
                )
            } else {
                data.forEach { home ->
                    HomeRow(
                        list = home.animeList,
                        title = home.title,
                        onItemClicked = onItemClick
                    )
                }
            }

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GridLayoutTabs(
    data: List<Home>,
    onItemClick: (Anime) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { data.size })
    val scope = rememberCoroutineScope()
    PrimaryScrollableTabRow(
        selectedTabIndex = pagerState.currentPage,
        containerColor = Color.Unspecified,
        edgePadding = 0.dp,
        divider = { HorizontalDivider(thickness = 0.5.dp) }
    ) {
        val tabs = remember(data) { data.map { it.title } }
        tabs.forEachIndexed { index, title ->
            Tab(
                text = { title },
                selected = pagerState.currentPage == index,
                onClick = {
                    scope.launch { pagerState.scrollToPage(index) }
                },
            )
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
    ) { page ->
        val context = LocalContext.current
        val isWideScreen = isWideScreen(context)
        // 判断使用的列数和布局宽度
        val columns = if (isWideScreen) {
            GridCells.Adaptive(minSize = dimensionResource(R.dimen.min_media_card_width))
        } else {
            GridCells.Fixed(3)
        }
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize().focusGroup(),
            columns = columns,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(data[page].animeList) { homeItem ->
                MediaSmall(
                    image = homeItem.img,
                    label = homeItem.title,
                    onClick = { onItemClick(homeItem) },
                    modifier = Modifier.width(dimensionResource(Res.dimen.media_card_width))
                )
            }
        }
    }
}

@Composable
private fun Tab(
    selected: Boolean,
    text: @Composable () -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (isFocused, focusModifier) = rememberIsFocused()
    Surface(
        onClick = onClick,
        modifier = modifier
            .padding(8.dp)
            .then(focusModifier),
        shape = RoundedCornerShape(20.dp),
        color = when {
            isFocused -> MaterialTheme.colorScheme.primary
            selected -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        },
        contentColor = when {
            isFocused -> MaterialTheme.colorScheme.onPrimary
            selected -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.onBackground
        },
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = text(),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun getBlendedBackgroundColor(): Color {
    val pureBackground by SettingsPreferences.pureBackground.collectAsState()
    if (pureBackground) return MaterialTheme.colorScheme.background
    return Color(
        ColorUtils.blendARGB(
            MaterialTheme.colorScheme.background.toArgb(),
            MaterialTheme.colorScheme.primaryContainer.toArgb(),
            0.05f
        )
    )
}

private val BackgroundOffset = 48.dp

@Composable
private fun HomeBackgroundSpacer(isWideScreen: Boolean, useGridLayout: Boolean) {
    val size by animateDpAsState(
        targetValue = if (!isWideScreen) {
            if (useGridLayout)
                dimensionResource(Res.dimen.banner_height) - BackgroundOffset
            else
                dimensionResource(Res.dimen.banner_height)
        } else 0.dp,
        animationSpec = tween(400),
        label = "background_padding"
    )
    Spacer(Modifier.size(size))
}

@Composable
fun HomeBackground(
    scrollState: ScrollState,
    useGridLayout: Boolean,
    onSwitchLayout: (Boolean) -> Unit,
) {
    Box {
        val context = LocalContext.current
        var imageUri by rememberPreference(key = KEY_HOME_BACKGROUND_URI, defaultValue = "")

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) {
            it?.let { uri ->
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                imageUri = uri.toString()
            }
        }

        AsyncImage(
            model = Uri.parse(imageUri),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(Res.dimen.banner_height))
                .bannerParallax(scrollState),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            error = painterResource(Res.drawable.background)
        )

        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    )
                )
                .fillMaxWidth()
                .height(dimensionResource(Res.dimen.banner_height))
        )

        val offsetY by animateDpAsState(
            targetValue = if (useGridLayout) -BackgroundOffset else 0.dp,
            animationSpec = tween(400),
            label = "hometile_offset"
        )
        HomeTile(
            modifier = Modifier
                .offset(y = offsetY)
                .align(Alignment.BottomStart),
            useGridLayout = useGridLayout,
            onSwitchGridLayout = onSwitchLayout,
            onClick = { launcher.launch(arrayOf("image/*")) }
        )
    }
}

@Composable
private fun HomeTile(
    modifier: Modifier = Modifier,
    useGridLayout: Boolean,
    onSwitchGridLayout: (Boolean) -> Unit,
    onClick: () -> Unit = {},
) {
    val isWideScreen = isWideScreen(LocalContext.current)
    var showSourceSwitchDialog by remember { mutableStateOf(false) }
    var currentSourceName by remember { mutableStateOf(SourceHolder.currentSourceMode.name) }
    val (isSourceFocused, sourceFocusModifier) = rememberIsFocused()
    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .padding(
                    start = dimensionResource(Res.dimen.large_padding),
                    top = if (isWideScreen) 8.dp else 64.dp
                ),
            verticalArrangement = Arrangement.spacedBy((-8).dp)
        ) {
            if (!isWideScreen) {
                val (animeFocused, animeFocusModifier) = rememberIsFocused()
                Surface(
                    onClick = onClick,
                    shape = RoundedCornerShape(20.dp),
                    color = if (animeFocused) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    modifier = animeFocusModifier
                ) {
                    Text(
                        text = stringResource(Res.string.lbl_anime),
                        color = if (animeFocused) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
            Surface(
                onClick = { showSourceSwitchDialog = true },
                shape = RoundedCornerShape(20.dp),
                color = if (isSourceFocused) MaterialTheme.colorScheme.primary
                else Color.Transparent,
                modifier = sourceFocusModifier
            ) {
                Text(
                    text = currentSourceName,
                    color = if (isSourceFocused) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        if (!isWideScreen) {
            LayoutTypeSelector(
                modifier = Modifier
                    .padding(end = 24.dp, top = 88.dp, bottom = 16.dp),
                checked = useGridLayout,
                onCheckedChange = { onSwitchGridLayout(it) }
            )
        }
    }
    if (showSourceSwitchDialog) {
        SourceSwitchDialog(
            onDismissRequest = { showSourceSwitchDialog = false },
            onSourceChanged = { mode ->
                currentSourceName = mode.name
                SourceHolder.isSourceChanged.value++
            }
        )
    }
}

@Composable
fun HomeRow(
    list: List<Anime?>,
    title: String,
    onItemClicked: (Anime) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(start = dimensionResource(Res.dimen.large_padding))
        )

        Spacer(Modifier.size(dimensionResource(Res.dimen.medium_padding)))

        MediaSmallRow(
            mediaList = list,
            content = { homeItem ->
                MediaSmall(
                    image = homeItem?.img,
                    label = homeItem?.title,
                    onClick = {
                        onItemClicked(homeItem!!)
                    },
                    modifier = Modifier.width(dimensionResource(Res.dimen.media_card_width))
                )
            }
        )
    }
}

@Composable
private fun LayoutTypeSelector(
    modifier: Modifier = Modifier,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.onBackground,
                shape = CircleShape
            )
    ) {
        val offset by animateDpAsState(
            targetValue = if (!checked) 0.dp else 40.dp,
            label = "media_switch"
        )

        Box(
            modifier = Modifier
                .padding(dimensionResource(Res.dimen.media_type_selector_padding))
                .size(dimensionResource(Res.dimen.media_type_choice_size))
                .offset { IntOffset(x = offset.roundToPx(), y = 0) }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background)
        )

        Row(
            modifier = Modifier
                .height(dimensionResource(Res.dimen.media_type_selector_height))
                .width(dimensionResource(Res.dimen.media_type_selector_width))
                .padding(dimensionResource(Res.dimen.media_type_selector_padding)),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(2) { index ->
                val selectedIndex = if (!checked) 0 else 1
                val (isFocused, focusModifier) = rememberIsFocused()
                IconButton(
                    onClick = { if (selectedIndex != index) onCheckedChange?.invoke(!checked) },
                    modifier = Modifier
                        .requiredWidth(dimensionResource(Res.dimen.media_type_choice_size))
                        .then(focusModifier),
                    colors = focusedIconButtonColors(isFocused)
                ) {
                    Icon(
                        imageVector = if (index == 0) Icons.Rounded.PlayArrow else ImageVector.vectorResource(
                            id = Res.drawable.manga
                        ),
                        tint = when {
                            isFocused -> MaterialTheme.colorScheme.onPrimary
                            selectedIndex == index -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.background
                        },
                        contentDescription = null,
                    )
                }
            }
        }
    }
}
