package com.lanlinju.animius.presentation.component

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lanlinju.animius.util.CROSSFADE_DURATION
import com.lanlinju.animius.util.focus.focusBorder
import com.lanlinju.animius.R as Res

/**
 * A [LazyRow] of [MediaSmall]s.
 *
 * @param mediaList A list of [HomeItem]s.
 */
@Composable
fun <T> MediaSmallRow(
    mediaList: List<T>,
    content: @Composable (T) -> Unit
) {
    LazyRow(
        modifier = Modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(Res.dimen.small_padding)),
        contentPadding = PaddingValues(
            start = dimensionResource(Res.dimen.large_padding),
            end = dimensionResource(Res.dimen.large_padding)
        )
    ) {
        items(mediaList) { media ->
            content(media)
        }
    }
}

/**
 * A [Card] to display a media image and a label.
 *
 * @param image A URL of the image to be shown in the card that this component is.
 * @param label A label for the [image], if this is `null`, the [label] is not shown.
 * @param onClick Action to happen when the card is clicked.
 * @param onLongClick Action to happen when the card is long-pressed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaSmall(
    image: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    onSuccess: (Bitmap) -> Unit = { }
) {
    val cardShape = RoundedCornerShape(dimensionResource(Res.dimen.media_card_corner_radius))

    Card(
        modifier = modifier
            .focusBorder(
                shape = cardShape,
                width = 3.dp,
                focusedColor = MaterialTheme.colorScheme.primary,
            )
            .clip(cardShape)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = cardShape,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(image)
                .crossfade(CROSSFADE_DURATION)
                .build(),
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(dimensionResource(Res.dimen.media_card_corner_radius))),
            onSuccess = {
                val bitmap = (it.result.drawable as BitmapDrawable).bitmap
                onSuccess(bitmap)
            }
        )

        if (label != null)
            Box {
                Text(
                    text = " \n ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    modifier = Modifier.padding(
                        vertical = dimensionResource(Res.dimen.media_card_text_padding_vertical)
                    )
                )

                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    // TODO: Add a custom overflow indicator:
                    //  https://proandroiddev.com/detect-text-overflow-in-jetpack-compose-56c0b83da5a5.
                    overflow = TextOverflow.Visible,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(
                            horizontal = dimensionResource(Res.dimen.media_card_text_padding_horizontal),
                            vertical = dimensionResource(Res.dimen.media_card_text_padding_vertical)
                        )
                )
            }
    }
}

@Preview
@Composable
fun PreviewMediaSmall() {
    MediaSmall(
        image =
        "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx132405-qP7FQYGmNI3d.jpg",
        label =
        "Sono Bisque Doll wa Koi wo Suru",
        onClick = { },
        modifier = Modifier.width(dimensionResource(Res.dimen.media_card_width))
    )
}
