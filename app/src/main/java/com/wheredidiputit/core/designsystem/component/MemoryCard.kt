package com.wheredidiputit.core.designsystem.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wheredidiputit.R
import com.wheredidiputit.core.designsystem.theme.WdipiShapes
import com.wheredidiputit.core.designsystem.theme.WdipiSpacing
import com.wheredidiputit.core.util.relativeTime
import com.wheredidiputit.core.util.rememberHaptics
import com.wheredidiputit.domain.model.Item
import java.io.File

/**
 * One memory in a list. The answer to "where?" is the second line and is
 * always visible, so search results are useful without opening them.
 */
@Composable
fun MemoryCard(
    item: Item,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val time = relativeTime(item.createdAt)
    val meta = listOfNotNull(item.category?.name, time).joinToString(" · ")
    val cardDescription = stringResource(R.string.a11y_memory_card, item.title, item.location, meta)

    Surface(
        onClick = onClick,
        shape = WdipiShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = WdipiSpacing.lg, top = WdipiSpacing.md, bottom = WdipiSpacing.md, end = WdipiSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.localImagePath != null || item.hasPendingImageDownload) {
                Thumbnail(path = item.localImagePath)
                Box(Modifier.size(WdipiSpacing.md))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = cardDescription },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Box(Modifier.size(WdipiSpacing.xs))
                    Text(
                        text = item.location,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FavoriteToggle(isFavorite = item.isFavorite, onToggle = onToggleFavorite)
        }
    }
}

@Composable
private fun Thumbnail(path: String?) {
    val shapeModifier = Modifier
        .size(56.dp)
        .clip(WdipiShapes.thumbnail)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    if (path != null) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = shapeModifier,
        )
    } else {
        Box(shapeModifier, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Heart toggle with a small, quick scale — no bounce loops. */
@Composable
fun FavoriteToggle(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val scale by animateFloatAsState(
        targetValue = if (isFavorite) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "favoriteScale",
    )
    IconToggleButton(
        checked = isFavorite,
        onCheckedChange = {
            haptics.tick()
            onToggle()
        },
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(if (isFavorite) R.string.a11y_remove_favorite else R.string.a11y_add_favorite),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.scale(scale),
        )
    }
}
