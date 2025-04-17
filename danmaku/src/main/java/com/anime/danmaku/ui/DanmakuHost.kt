package com.anime.danmaku.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import com.anime.danmaku.api.Danmaku
import com.anime.danmaku.api.DanmakuLocation
import com.anime.danmaku.api.DanmakuPresentation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.lang.System.currentTimeMillis
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun DanmakuHost(
    state: DanmakuHostState,
    modifier: Modifier = Modifier,
) {

    BoxWithConstraints(modifier) {

        state.hostWidth = constraints.maxWidth
        state.hostHeight = constraints.maxHeight

        DanmakuCanvas {
            for (danmaku in state.presentFloatingDanmaku) {
                with(danmaku.danmaku) {
                    draw(
                        screenPosX = { danmaku.screenPosX },
                        screenPosY = { danmaku.screenPosY },
                    )
                }
            }
            for (danmaku in state.presentFixedDanmaku) {
                with(danmaku.danmaku) {
                    draw(
                        screenPosX = { danmaku.screenPosX },
                        screenPosY = { danmaku.screenPosY },
                    )
                }
            }
        }
    }

    // set the number of tracks
    LaunchedEffect(state.hostHeight) { state.setTrackCount() }
    // calculate current play time on every frame
    LaunchedEffect(state.paused) { if (!state.paused) state.interpolateFrameLoop() }
    // logical tick for removal of danmaku
    LaunchedEffect(state.paused) {
        if (!state.paused) {
            while (true) {
                state.tick()
                delay(1000)
            }
        }
    }

    if (state.isDebug) {
        DanmakuDebug(state)
    }
}

@Composable
fun DanmakuCanvas(modifier: Modifier = Modifier, onDraw: DrawScope.() -> Unit) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        onDraw()
    }
}

@Composable
private fun DanmakuDebug(state: DanmakuHostState) {
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodySmall) {
        Column(
            modifier = Modifier
                .padding(4.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text("DanmakuHost state: ")
            Text("  hostSize: ${state.hostWidth}x${state.hostHeight}, trackHeight: ${state.trackHeight}")
            Text("  paused: ${state.paused}, elapsedFrameTimeMillis: ${state.elapsedFrameTimeNanos / 1_000_000}")
            Text("  presentDanmakuCount: ${state.presentFixedDanmaku.size + state.presentFloatingDanmaku.size}")
            HorizontalDivider()
            Text("  floating tracks: ")
            for (track in state.floatingTracks) {
                Text("    $track")
            }
            Text("  top tracks: ")
            for (track in state.topTracks) {
                Text("    $track")
            }
            Text("  bottom tracks: ")
            for (track in state.bottomTracks) {
                Text("    $track")
            }
        }
    }
}


@Composable
@Preview(showBackground = true)
@Preview(showBackground = true, device = Devices.TABLET)
internal fun DanmakuHostPreview() {
    var emitted by remember { mutableIntStateOf(0) }
    val config = remember {
        mutableStateOf(
            DanmakuConfig(
                displayArea = 1.0f,
                isDebug = true,
                enableTop = false,
                enableBottom = false
            )
        )
    }

    val data = remember {
        flow {
            var counter = 0
            val startTime = currentTimeMillis()

            fun danmaku() =
                Danmaku(
                    counter++.toString(),
                    "dummy",
                    currentTimeMillis() - startTime,
                    "1",
                    DanmakuLocation.entries.random(),
                    text = LoremIpsum(Random.nextInt(1..5)).values.first(),
                    0xffffff,
                )

            emit(danmaku())
            emit(danmaku())
            emit(danmaku())
            while (true) {
                emit(danmaku())
                emitted++
                delay(Random.nextLong(5, 10).milliseconds)
            }
        }
    }

    val state = rememberDanmakuHostState(config.value)

    LaunchedEffect(true) {
        data.collect {
            state.trySend(
                DanmakuPresentation(
                    it,
                    isSelf = Random.nextBoolean(),
                ),
            )
        }
    }

    if (isInLandscapeMode()) {
        DanmakuHost(
            state,
            Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        )

    } else {
        Box() {
            Column {
                DanmakuHost(
                    state,
                    Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .background(Color.Transparent)
                )
                HorizontalDivider()
            }

            // test send
            Row(modifier = Modifier.align(Alignment.BottomCenter)) {
                val scope = rememberCoroutineScope()
                Button(onClick = {
                    val d = Danmaku(
                        "111",
                        "dummy",
                        5500,
                        "1",
                        DanmakuLocation.NORMAL,
                        text = "Send Danmaku Test!!!",
                        Color.Green.toArgb(),
                    )
                    scope.launch {
                        state.send(DanmakuPresentation(d, isSelf = true))
                    }
                }) {
                    Text(text = "Send")
                }
                Spacer(modifier = Modifier.size(8.dp))
                Button(onClick = {
                    state.paused = !state.paused
                }) {
                    Text(text = "Pause")
                }
            }
        }
    }
}