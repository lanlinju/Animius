package com.lanlinju.animius.presentation.screen.search


import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.lanlinju.animius.R
import com.lanlinju.animius.presentation.component.MediaSmall
import com.lanlinju.animius.presentation.component.PaginationStateHandler
import com.lanlinju.animius.presentation.component.WarningMessage
import com.lanlinju.animius.presentation.screen.captcha.CaptchaWebViewActivity
import com.lanlinju.animius.util.SourceMode
import com.lanlinju.animius.util.isWideScreen
import com.lanlinju.animius.util.focus.focusedIconButtonColors
import com.lanlinju.animius.util.focus.focusedTextButtonColors
import com.lanlinju.animius.util.focus.rememberIsFocused

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToAnimeDetail: (detailUrl: String, mode: SourceMode) -> Unit,
    onBackClick: () -> Unit
) {

    val viewModel = hiltViewModel<SearchViewModel>()
    val animesState = viewModel.animesState.collectAsLazyPagingItems()
    val searchQuery by viewModel.query.collectAsState()
    val needCaptchaUrl by viewModel.needCaptchaUrl.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isEditing by remember { mutableStateOf(true) }
    var searchBarHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val navFocusRequester = remember { FocusRequester() }

    // PagingSource 加载完成后检查是否需要验证码
    // 不能在 collect 中检查，因为 PagingData 在加载前就 emit 了
    LaunchedEffect(animesState.loadState.refresh) {
        if (animesState.loadState.refresh is androidx.paging.LoadState.NotLoading) {
            viewModel.checkNeedCaptcha()
        }
    }

    // Request focus on the appropriate element when isEditing changes:
    // - Enter editing → focus the InputField so the keyboard and cursor work
    // - Exit editing  → focus the non-editing search bar so D-Pad can navigate buttons
    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        } else {
            navFocusRequester.requestFocus()
        }
    }

    val context = LocalContext.current
    val captchaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 验证码验证完成，重新搜索
            viewModel.clearNeedCaptcha()
            viewModel.getSearchData(searchQuery, viewModel.currentSourceMode)
        }
    }

    // 显示验证码提示对话框
    needCaptchaUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { viewModel.clearNeedCaptcha() },
            title = { Text("需要验证码验证") },
            text = { Text("搜索时遇到验证码，请完成验证后重试") },
            confirmButton = {
                val (isFocused, focusModifier) = rememberIsFocused()
                TextButton(
                    onClick = {
                        viewModel.clearNeedCaptcha()
                        captchaLauncher.launch(
                            CaptchaWebViewActivity.createIntent(context, url)
                        )
                    },
                    modifier = Modifier.then(focusModifier),
                    colors = focusedTextButtonColors(isFocused)
                ) {
                    Text("去验证")
                }
            },
            dismissButton = {
                val (isFocused, focusModifier) = rememberIsFocused()
                TextButton(
                    onClick = { viewModel.clearNeedCaptcha() },
                    modifier = Modifier.then(focusModifier),
                    colors = focusedTextButtonColors(isFocused)
                ) {
                    Text("取消")
                }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        // 返回键优先级: 菜单展开时关闭菜单 > 编辑时退出编辑 > 返回上一页
        BackHandler(enabled = menuExpanded) {
            menuExpanded = false
        }
        BackHandler(enabled = isEditing) {
            keyboardController?.hide()
            isEditing = false
        }
        BackHandler(enabled = !isEditing && !menuExpanded) {
            onBackClick()
        }

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // 搜索栏 - InputField 测量高度 → 非编辑 Row 精确匹配，零跳动
        if (isEditing) {
            SearchBarDefaults.InputField(
                query = searchQuery,
                onQueryChange = viewModel::onQuery,
                onSearch = {
                    viewModel.onSearch(it, viewModel.currentSourceMode)
                    keyboardController?.hide()
                    isEditing = false
                },
                expanded = false,
                onExpandedChange = { },
                placeholder = {
                    Text(stringResource(id = R.string.lbl_search_placeholder))
                },
                leadingIcon = {
                    val (backFocused, backModifier) = rememberIsFocused()
                    IconButton(
                        onClick = onBackClick,
                        colors = focusedIconButtonColors(backFocused),
                        modifier = backModifier
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                            tint = if (backFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                trailingIcon = {
                    Row {
                        val (clearFocused, clearModifier) = rememberIsFocused()
                        IconButton(
                            onClick = viewModel::clearSearchQuery,
                            colors = focusedIconButtonColors(clearFocused),
                            modifier = clearModifier
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = stringResource(id = R.string.clear),
                                tint = if (clearFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        SourceModeMenu(
                            expanded = menuExpanded,
                            currentMode = viewModel.currentSourceMode,
                            onExpandedChange = { menuExpanded = it },
                            onModeSelected = { mode ->
                                menuExpanded = false
                                viewModel.currentSourceMode = mode
                                viewModel.getSearchData(searchQuery, mode)
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .focusRequester(focusRequester)
                    .onGloballyPositioned { coordinates ->
                        searchBarHeight = with(density) { coordinates.size.height.toDp() }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionUp, Key.DirectionDown -> true
                                Key.DirectionCenter -> {
                                    if (searchQuery.isNotEmpty()) {
                                        viewModel.onSearch(searchQuery, viewModel.currentSourceMode)
                                    }
                                    keyboardController?.hide()
                                    isEditing = false
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
            )
        } else {
            val (isNavFocused, navPillModifier) = rememberIsFocused()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(searchBarHeight)
                    .padding(start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val (backFocused, backModifier) = rememberIsFocused()
                IconButton(
                    onClick = onBackClick,
                    colors = focusedIconButtonColors(backFocused),
                    modifier = backModifier
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(id = R.string.back),
                        tint = if (backFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(navFocusRequester)
                        .then(navPillModifier)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            keyboardController?.show()
                            isEditing = true
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionCenter
                            ) {
                                keyboardController?.show()
                                isEditing = true
                                true
                            } else {
                                false
                            }
                        }
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            if (isNavFocused) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = searchQuery.ifEmpty { stringResource(id = R.string.lbl_search_placeholder) },
                        color = when {
                            isNavFocused -> MaterialTheme.colorScheme.onPrimary
                            searchQuery.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val (clearFocused, clearModifier) = rememberIsFocused()
                IconButton(
                    onClick = viewModel::clearSearchQuery,
                    colors = focusedIconButtonColors(clearFocused),
                    modifier = clearModifier
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = stringResource(id = R.string.clear),
                        tint = if (clearFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }

                SourceModeMenu(
                    expanded = menuExpanded,
                    currentMode = viewModel.currentSourceMode,
                    onExpandedChange = { menuExpanded = it },
                    onModeSelected = { mode ->
                        menuExpanded = false
                        viewModel.currentSourceMode = mode
                        viewModel.getSearchData(searchQuery, mode)
                    }
                )
            }
        }

            // 搜索结果 - 始终在搜索栏下方显示
            val context = LocalContext.current

            LazyVerticalGrid(
                modifier = Modifier
                    .weight(1f)
                    .focusGroup(),
            columns = if (isWideScreen(context)) GridCells.Adaptive(dimensionResource(R.dimen.media_card_width)) else GridCells.Fixed(
                3
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp),
        ) {
            items(count = animesState.itemCount) { index ->
                val mediaFocusRequester = remember { FocusRequester() }
                val item = animesState[index]!!
                MediaSmall(
                    image = item.img,
                    label = item.title,
                    onClick = {
                        onNavigateToAnimeDetail(item.detailUrl, viewModel.currentSourceMode)
                    },
                    modifier = Modifier
                        .focusRequester(mediaFocusRequester)
                )

                LaunchedEffect(item.detailUrl) {
                    if (index == 0) {
                        mediaFocusRequester.requestFocus()
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                PaginationStateHandler(
                    paginationState = animesState,
                    loadingComponent = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    vertical = dimensionResource(
                                        id = R.dimen.medium_padding
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    },
                    errorComponent = {
                        WarningMessage(
                            textId = R.string.txt_empty_result,
                            onRetryClick = {
                                animesState.retry()
                            }
                        )
                    }
                )
            }
        }
        }
    }
}

/**
 * "More" button + source-mode dropdown, shared by both search-bar states.
 */
@Composable
private fun SourceModeMenu(
    expanded: Boolean,
    currentMode: SourceMode,
    onExpandedChange: (Boolean) -> Unit,
    onModeSelected: (SourceMode) -> Unit,
) {
    val (moreFocused, moreModifier) = rememberIsFocused()
    Box {
        IconButton(
            onClick = { onExpandedChange(true) },
            colors = focusedIconButtonColors(moreFocused),
            modifier = moreModifier
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(id = R.string.more),
                tint = if (moreFocused) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            SourceMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = mode.name,
                            color = if (currentMode == mode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = { onModeSelected(mode) },
                )
            }
        }
    }
}

