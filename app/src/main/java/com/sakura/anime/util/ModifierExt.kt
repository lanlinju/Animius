package com.sakura.anime.util

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

fun Modifier.bannerParallax(scrollState: ScrollState) = graphicsLayer {
    translationY = 0.7f * scrollState.value
}

/**
 * @see https://github.com/google/accompanist/issues/756#issuecomment-985723235
 */
val VerticalScrollConsumer = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource) = available.copy(x = 0f)
}

val HorizontalScrollConsumer = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource) = available.copy(y = 0f)
}

fun Modifier.disableVerticalPointerInputScroll() = this.nestedScroll(VerticalScrollConsumer)

fun Modifier.disableHorizontalPointerInputScroll() = this.nestedScroll(HorizontalScrollConsumer)