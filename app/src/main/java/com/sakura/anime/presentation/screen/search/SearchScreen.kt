package com.sakura.anime.presentation.screen.search


import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.sakura.anime.R
import com.sakura.anime.presentation.component.MediaSmall
import com.sakura.anime.presentation.component.PaginationStateHandler
import com.sakura.anime.presentation.component.WarningMessage
import com.sakura.anime.util.SourceMode
import com.sakura.anime.util.isAndroidTV
import com.sakura.anime.util.isWideScreen
import com.sakura.anime.util.onDCenterKeyPress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToAnimeDetail: (detailUrl: String, mode: SourceMode) -> Unit,
    onBackClick: () -> Unit
) {

    val viewModel = hiltViewModel<SearchViewModel>()
    val animesState = viewModel.animesState.collectAsLazyPagingItems()
    val searchQuery by viewModel.query.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    SearchBar(
        query = searchQuery,
        onQueryChange = viewModel::onQuery,
        onSearch = {
            viewModel.onSearch(it, viewModel.currentSourceMode)
            keyboardController?.hide()
        },
        active = true,
        onActiveChange = { if (!it) onBackClick() },
        leadingIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(id = R.string.back)
                )
            }
        },
        placeholder = {
            Text(stringResource(id = R.string.lbl_search_placeholder))
        },
        trailingIcon = {
            Row {
                IconButton(onClick = viewModel::clearSearchQuery) {
                    Icon(imageVector = Icons.Rounded.Clear, contentDescription = "")
                }
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(id = R.string.more)
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {

                        SourceMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.name,
                                        color = if (viewModel.currentSourceMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    expanded = false
                                    viewModel.currentSourceMode = mode
                                    viewModel.getSearchData(searchQuery, mode)
                                },
                            )
                        }
                    }
                }
            }

        },
        modifier = Modifier.focusRequester(focusRequester)
    ) {
        val context = LocalContext.current
        val isAndroidTV = isAndroidTV(context)

        LazyVerticalGrid(
            columns = if (isWideScreen(context)) GridCells.Adaptive(dimensionResource(R.dimen.media_card_width)) else GridCells.Fixed(
                3
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp),
        ) {
            items(count = animesState.itemCount) { index ->
                val mediaFocusRequester = remember { FocusRequester() }
                var isFocused by remember { mutableStateOf(false) }
                val item = animesState[index]!!
                MediaSmall(
                    image = item.img,
                    label = item.title,
                    onClick = {
                        onNavigateToAnimeDetail(item.detailUrl, viewModel.currentSourceMode)
                    },
                    modifier = Modifier
                        .onFocusChanged(onFocusChanged = { isFocused = it.isFocused })
                        .run {
                            if (isFocused && isAndroidTV) {
                                border(
                                    4.dp, MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(dimensionResource(R.dimen.media_card_corner_radius))
                                )
                            } else this
                        }
//                        .scale(if (isFocused && isAndroidTV) 1.1f else 1f)
                        .focusRequester(mediaFocusRequester) // 设置焦点请求者
                        .focusable()
                        .onDCenterKeyPress {
                            onNavigateToAnimeDetail(item.detailUrl, viewModel.currentSourceMode)
                        }
                )

                LaunchedEffect(item.detailUrl) {
                    if (index == 0 && isAndroidTV) {
                        mediaFocusRequester.requestFocus() // 将焦点移动到 MediaSmall
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

    LaunchedEffect(Unit) {
        if (!viewModel.hasFocusRequest) {
            viewModel.hasFocusRequest = true
            focusRequester.requestFocus()
        }

    }
}

