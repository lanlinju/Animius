package com.sakura.anime.presentation.screen.week

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sakura.anime.R
import com.sakura.anime.data.remote.dto.AnimeBean
import com.sakura.anime.presentation.component.LoadingIndicator
import com.sakura.anime.presentation.component.MediaSmall
import com.sakura.anime.presentation.component.StateHandler
import com.sakura.anime.presentation.component.WarningMessage
import com.sakura.anime.util.GITHUB_ADDRESS
import com.sakura.anime.util.GITHUB_RELEASE_ADDRESS
import com.sakura.anime.util.KEY_AUTO_CONTINUE_PLAY_ENABLED
import com.sakura.anime.util.KEY_AUTO_ORIENTATION_ENABLED
import com.sakura.anime.util.KEY_SOURCE_MODE
import com.sakura.anime.util.SourceHolder
import com.sakura.anime.util.SourceHolder.DEFAULT_ANIME_SOURCE
import com.sakura.anime.util.SourceMode
import com.sakura.anime.util.TABS
import com.sakura.anime.util.isAndroidTV
import com.sakura.anime.util.isWideScreen
import com.sakura.anime.util.rememberPreference
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(
    onNavigateToAnimeDetail: (detailUrl: String, mode: SourceMode) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDownload: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToDanmakuSettings: () -> Unit,
) {
    val viewModel = hiltViewModel<WeekViewModel>()
    val weekDataState by viewModel.weeKDataMap.collectAsState()
    val isUpdateVersion by viewModel.isUpdateVersion.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dayOfWeek = remember {
        val calendar = Calendar.getInstance()
        var day = calendar.get(Calendar.DAY_OF_WEEK) - 2
        if (day == -1) {
            day = 6
        }
        day
    }

    val pagerState = rememberPagerState(initialPage = dayOfWeek, pageCount = { TABS.size })

    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(
                top = if (isAndroidTV) {
                    8.dp
                } else 0.dp
            )
    ) {
        val openSwitchSourceDialog = remember { mutableStateOf(false) }
        val openSettingsDialog = remember { mutableStateOf(false) }
        val openChangeDomainDialog = remember { mutableStateOf(false) }

        Column {
            var expanded by remember { mutableStateOf(false) }
            val uriHandler = LocalUriHandler.current
            TopAppBar(
                title = {
                    Column(modifier = Modifier.clickable {
                        openSwitchSourceDialog.value = true
                    }) {
                        Text(
                            text = stringResource(id = R.string.lbl_schedule),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = SourceHolder.currentSourceMode.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_history),
                            contentDescription = stringResource(id = R.string.history)
                        )
                    }

                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(id = R.string.search)
                        )
                    }

                    IconButton(onClick = onNavigateToDownload) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            modifier = Modifier.rotate(90f),
                            contentDescription = stringResource(id = R.string.download_list)
                        )
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
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.switch_source)) },
                                onClick = {
                                    expanded = false
                                    openSwitchSourceDialog.value = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        modifier = Modifier.rotate(90f),
                                        contentDescription = stringResource(id = R.string.switch_source)
                                    )
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.modifier_domain)) },
                                onClick = {
                                    expanded = false
                                    openChangeDomainDialog.value = true
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_domain),
                                        contentDescription = stringResource(id = R.string.modifier_domain)
                                    )
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.default_settings)) },
                                onClick = {
                                    expanded = false
                                    openSettingsDialog.value = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = stringResource(id = R.string.default_settings)
                                    )
                                }
                            )

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = R.string.appearance_settings)) },
                                    onClick = {
                                        expanded = false
                                        onNavigateToAppearance()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Palette,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }


                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.danmaku_settings)) },
                                onClick = {
                                    expanded = false
                                    onNavigateToDanmakuSettings()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Subtitles,
                                        contentDescription = stringResource(R.string.danmaku_settings)
                                    )
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.check_update)) },
                                onClick = {
                                    expanded = false
                                    viewModel.checkUpdate(context)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        modifier = Modifier.rotate(-90f),
                                        contentDescription = stringResource(id = R.string.check_update)
                                    )
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.github_repo)) },
                                onClick = {
                                    expanded = false
                                    uriHandler.openUri(GITHUB_ADDRESS)
                                },
                                leadingIcon = {
                                    Icon(
                                        modifier = Modifier.size(24.dp),
                                        painter = painterResource(id = R.drawable.ic_github),
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                })

            TabRow(
                selectedTabIndex = pagerState.currentPage,
            ) {
                TABS.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title, style = MaterialTheme.typography.bodyMedium) },
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.scrollToPage(index) } },
                    )
                }
            }
            val isAndroidTV = isAndroidTV(LocalContext.current)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize(),
//                    .run {
//                        if (isAndroidTV) {
//                            disableHorizontalPointerInputScroll()
//                        } else this
//                    },
                beyondViewportPageCount = 1,
//                userScrollEnabled = !isAndroidTV
            ) { page ->
                StateHandler(
                    state = weekDataState,
                    onLoading = { LoadingIndicator() },
                    onFailure = {
                        WarningMessage(
                            textId = R.string.txt_empty_result,
                            onRetryClick = { viewModel.refresh() }
                        )
                    }
                ) { resource ->
                    resource.data?.let { weekDataMap ->
                        weekDataMap[page]?.let { list ->
                            WeekList(
                                list = list,
                                onItemClicked = {
                                    onNavigateToAnimeDetail(it.url, SourceHolder.currentSourceMode)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (openSwitchSourceDialog.value) {
            SwitchSourceDialog(
                onDismissRequest = { isRefresh ->
                    openSwitchSourceDialog.value = false
                    if (isRefresh) {
                        viewModel.refresh()
                    }
                })
        }

        if (openSettingsDialog.value) {
            SettingsDialog(onDismissRequest = { openSettingsDialog.value = false })
        }

        if (isUpdateVersion) {
            UpdateVersionDialog(viewModel, context)
        }

        if (isCheckingUpdate) {
            LoadingIndicationDialog(onDismissRequest = { viewModel.closeLoadingIndicationDialog() })
        }

        if (openChangeDomainDialog.value) {
            ChangeDomainDialog { isRefresh ->
                openChangeDomainDialog.value = false
                if (isRefresh) {
                    viewModel.refresh()
                }
            }
        }
    }

}

@Composable
private fun UpdateVersionDialog(
    viewModel: WeekViewModel,
    context: Context,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AlertDialog(
        onDismissRequest = { viewModel.closeUpdateDialog() },
        title = {
            Text(text = stringResource(id = R.string.software_updates))
        },
        text = {
            val uriHandler = LocalUriHandler.current
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = viewModel.versionName + "\n" + viewModel.updateMessage)
                Text(
                    text = stringResource(R.string.github_release_address),
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .padding(top = dimensionResource(id = R.dimen.small_padding))
                        .clickable { uriHandler.openUri(GITHUB_RELEASE_ADDRESS) }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isAndroidTV,
                onClick = { viewModel.downloadUpdate(context, lifecycleOwner) }) {
                Text(text = stringResource(id = R.string.download_software))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.closeUpdateDialog() }) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
private fun LoadingIndicationDialog(
    onDismissRequest: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(id = R.string.checking_update))
        },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimensionResource(id = R.dimen.small_padding)),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun SwitchSourceDialog(
    onDismissRequest: (Boolean) -> Unit,
) {
    var currentSourceMode by rememberPreference(KEY_SOURCE_MODE, DEFAULT_ANIME_SOURCE)

    val radioOptions = SourceMode.entries.map { it.name }
    val (selectedOption, onOptionSelected) = remember { mutableStateOf(currentSourceMode.name) }
    Dialog(onDismissRequest = {
        val isRefresh = if (selectedOption != currentSourceMode.name) {
            val mode = SourceMode.valueOf(selectedOption)
            SourceHolder.isSourceChanged = true
            SourceHolder.switchSource(mode)
            currentSourceMode = mode
            true
        } else {
            false
        }
        onDismissRequest(isRefresh)
    }) {
        Card(shape = RoundedCornerShape(dimensionResource(id = R.dimen.lager_corner_radius))) {
            Column(
                Modifier
                    .padding(vertical = dimensionResource(id = R.dimen.large_padding))
                    .selectableGroup()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    modifier = Modifier.padding(start = dimensionResource(id = R.dimen.large_padding)),
                    text = stringResource(id = R.string.switch_source),
                    style = MaterialTheme.typography.titleLarge
                )
                radioOptions.forEach { text ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(dimensionResource(id = R.dimen.radio_button_height))
                            .selectable(
                                selected = (text == selectedOption),
                                onClick = { onOptionSelected(text) },
                                role = Role.RadioButton
                            )
                            .padding(start = dimensionResource(id = R.dimen.large_padding)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (text == selectedOption),
                            onClick = null
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = dimensionResource(id = R.dimen.medium_padding))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    onDismissRequest: () -> Unit,
) {
    var isAutoOrientation by rememberPreference(KEY_AUTO_ORIENTATION_ENABLED, true)
    var isAutoContinuePlayEnabled by rememberPreference(KEY_AUTO_CONTINUE_PLAY_ENABLED, true)

    Dialog(onDismissRequest = onDismissRequest) {
        Card(shape = RoundedCornerShape(dimensionResource(id = R.dimen.lager_corner_radius))) {
            Column(
                modifier = Modifier.padding(dimensionResource(id = R.dimen.large_padding))
            ) {
                Text(
                    text = stringResource(id = R.string.default_settings),
                    style = MaterialTheme.typography.titleLarge
                )

                SettingsItem(
                    text = stringResource(id = R.string.enable_auto_rotate_orientation),
                    checked = isAutoOrientation,
                    onCheckedChange = { isAutoOrientation = it }
                )

                SettingsItem(
                    text = stringResource(id = R.string.auto_continue_play),
                    checked = isAutoContinuePlayEnabled,
                    onCheckedChange = { isAutoContinuePlayEnabled = it }
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(dimensionResource(id = R.dimen.radio_button_height)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ChangeDomainDialog(
    onDismissRequest: (Boolean) -> Unit = { s -> },
) {
    var currentDomain by rememberPreference(
        SourceHolder.currentSource.KEY_SOURCE_DOMAIN,
        SourceHolder.currentSource.DEFAULT_DOMAIN
    )
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onDismissRequest(false) },
        icon = {
            Icon(painterResource(id = R.drawable.ic_domain), contentDescription = null)
        },
        title = {
            Text(text = stringResource(id = R.string.modifier_domain))
        },
        text = {
            Column {

                val focusRequester = remember { FocusRequester() }
                val clipboardManager = LocalClipboardManager.current

                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                    },
                    placeholder = {
                        Text(
                            text = stringResource(
                                id = R.string.default_domain,
                                SourceHolder.currentSource.DEFAULT_DOMAIN
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1
                        )
                    },
                    label = {
                        Text(text = stringResource(id = R.string.anime_source_domain))
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            text = clipboardManager.getText()?.text.orEmpty()
                        }) {
                            Icon(
                                painterResource(id = R.drawable.ic_content_paste),
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.focusRequester(focusRequester)
                )

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            }

        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotEmpty()) {
                    currentDomain = text
                    SourceHolder.isSourceChanged = true
                    SourceHolder.currentSource.baseUrl = currentDomain
                    onDismissRequest(true)
                } else {
                    onDismissRequest(false)
                }
            }) {
                Text(text = stringResource(id = R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismissRequest(false) }) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
fun WeekList(
    list: List<AnimeBean>,
    onItemClicked: (AnimeBean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isWideScreen = isWideScreen(context)

    val useWeekItem = list.isEmpty() || list.first().img.isEmpty()

    // 判断使用的列数和布局宽度
    val columns = if (useWeekItem) {
        GridCells.Adaptive(minSize = dimensionResource(id = R.dimen.week_item_width))
    } else {
        if (isWideScreen) {
            GridCells.Adaptive(minSize = dimensionResource(R.dimen.min_media_card_width))
        } else {
            GridCells.Fixed(3)
        }
    }

    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        columns = columns,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(8.dp)
    ) {
        if (useWeekItem) {
            items(list) { anime ->
                WeekItem(
                    title = anime.title,
                    subtitle = anime.episodeName,
                    onClick = { onItemClicked(anime) }
                )
            }
        } else {
            items(list) { anime ->
                MediaSmall(
                    image = anime.img,
                    label = anime.title,
                    onClick = { onItemClicked(anime) },
                )
            }
        }
    }
}

@Composable
fun WeekItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {

    ElevatedCard(
        modifier = modifier.height(80.dp),
        onClick = onClick,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Text(
                text = title,
                modifier = Modifier.align(Alignment.TopStart),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = subtitle,
                modifier = Modifier.align(Alignment.BottomEnd),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
