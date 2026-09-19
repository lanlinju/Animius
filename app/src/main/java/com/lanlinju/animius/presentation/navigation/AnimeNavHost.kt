package com.lanlinju.animius.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.lanlinju.animius.presentation.screen.detail.AnimeDetailScreen
import com.lanlinju.animius.presentation.screen.download.DownloadScreen
import com.lanlinju.animius.presentation.screen.downloaddetail.DownloadDetailScreen
import com.lanlinju.animius.presentation.screen.history.HistoryScreen
import com.lanlinju.animius.presentation.screen.main.MainScreen
import com.lanlinju.animius.presentation.screen.search.SearchScreen
import com.lanlinju.animius.presentation.screen.settings.AppearanceScreen
import com.lanlinju.animius.presentation.screen.settings.DanmakuSettingsScreen
import com.lanlinju.animius.presentation.screen.videoplayer.VideoPlayScreen
import com.lanlinju.animius.util.SourceMode

@Composable
fun AnimeNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    onNavigateToAnimeDetail: (detailUrl: String, mode: SourceMode) -> Unit,
    onNavigateToVideoPlay: (parameters: String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDownload: () -> Unit,
    onNavigateToDownloadDetail: (detailUrl: String, title: String, sourceMode: SourceMode) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToDanmakuSettings: () -> Unit,
    onBackClick: () -> Unit,
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = Screen.Main,
    ) {
        composable<Screen.Main> {
            MainScreen(
                onNavigateToAnimeDetail = onNavigateToAnimeDetail,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToDownload = onNavigateToDownload,
                onNavigateToAppearance = onNavigateToAppearance,
                onNavigateToDanmakuSettings = onNavigateToDanmakuSettings,
            )
        }
        composable<Screen.AnimeDetail> {
            AnimeDetailScreen(
                onRelatedAnimeClick = onNavigateToAnimeDetail,
                onNavigateToVideoPlay = onNavigateToVideoPlay,
                onBackClick = onBackClick
            )
        }
        composable<Screen.VideoPlayer>(
            enterTransition = { fadeIn() },
            popExitTransition = { fadeOut(animationSpec = tween(durationMillis = 35)) }
        ) {
            VideoPlayScreen(onBackClick = onBackClick)
        }
        composable<Screen.Search> {
            SearchScreen(
                onNavigateToAnimeDetail = onNavigateToAnimeDetail,
                onBackClick = onBackClick
            )
        }
        composable<Screen.HistoryScreen> {
            HistoryScreen(
                onBackClick = onBackClick,
                onNavigateToAnimeDetail = onNavigateToAnimeDetail,
            )
        }
        composable<Screen.Download> {
            DownloadScreen(
                onBackClick = onBackClick,
                onNavigateToDownloadDetail = onNavigateToDownloadDetail,
                onNavigateToAnimeDetail = onNavigateToAnimeDetail
            )
        }
        composable<Screen.DownloadDetail> {
            DownloadDetailScreen(
                onBackClick = onBackClick,
                onNavigateToVideoPlay = onNavigateToVideoPlay,
                onNavigateToAnimeDetail = onNavigateToAnimeDetail
            )
        }
        composable<Screen.Appearance> {
            AppearanceScreen(
                onBackClick = onBackClick
            )
        }
        composable<Screen.DanmakuSettings> {
            DanmakuSettingsScreen(
                onBackClick = onBackClick
            )
        }
    }
}