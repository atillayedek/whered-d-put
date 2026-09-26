package com.wheredidiputit.presentation.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wheredidiputit.R
import com.wheredidiputit.core.designsystem.component.ConfirmDialog
import com.wheredidiputit.core.designsystem.component.EmptyState
import com.wheredidiputit.core.designsystem.component.FavoriteToggle
import com.wheredidiputit.core.designsystem.component.QuietButton
import com.wheredidiputit.core.designsystem.component.WdipiTopBar
import com.wheredidiputit.core.designsystem.theme.WdipiShapes
import com.wheredidiputit.core.designsystem.theme.WdipiSpacing
import com.wheredidiputit.core.util.relativeTime
import com.wheredidiputit.core.util.rememberHaptics
import com.wheredidiputit.domain.model.Item
import com.wheredidiputit.domain.model.SyncState
import com.wheredidiputit.presentation.common.AppSnackbarHost
import com.wheredidiputit.presentation.common.categoryLabel
import java.io.File

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()

    LaunchedEffect(viewModel) {
        viewModel.deleted.collect { onDeleted() }
    }
    // System back behaves exactly like the toolbar back arrow.
    BackHandler(onBack = onBack)

    val item = (state as? DetailUiState.Loaded)?.item

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { AppSnackbarHost() },
        topBar = {
            WdipiTopBar(
                title = "",
                onNavigate = onBack,
                actions = {
                    if (item != null) FavoriteToggle(isFavorite = item.isFavorite, onToggle = viewModel::toggleFavorite)
                },
            )
        },
        bottomBar = {
            if (item != null) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = WdipiSpacing.screen, vertical = WdipiSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QuietButton(
                            text = stringResource(R.string.action_delete),
                            onClick = { confirmDelete = true },
                            destructive = true,
                        )
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { onEdit(item.id) },
                            shape = WdipiShapes.button,
                            modifier = Modifier.heightIn(min = 52.dp),
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(WdipiSpacing.sm))
                            Text(stringResource(R.string.action_edit), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        },
    ) { padding ->
        when (val current = state) {
            DetailUiState.Loading -> Box(Modifier.fillMaxSize())
            DetailUiState.NotFound -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.detail_not_found_title),
                message = stringResource(R.string.detail_not_found_message),
                modifier = Modifier.padding(padding),
            )
            is DetailUiState.Loaded -> DetailContent(current.item, Modifier.padding(padding))
        }
    }

    if (confirmDelete && item != null) {
        ConfirmDialog(
            title = stringResource(R.string.detail_delete_title),
            message = stringResource(R.string.detail_delete_message, item.title),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                haptics.confirm()
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun DetailContent(item: Item, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = WdipiSpacing.screen)
            .padding(bottom = WdipiSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(WdipiSpacing.lg),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )

        Surface(
            shape = WdipiShapes.card,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(WdipiSpacing.lg),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Outlined.Place,
                    contentDescription = stringResource(R.string.a11y_location),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(WdipiSpacing.md))
                Text(
                    text = item.location,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        when {
            item.localImagePath != null -> AsyncImage(
                model = File(item.localImagePath),
                contentDescription = stringResource(R.string.a11y_item_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(WdipiShapes.photo),
            )
            item.hasPendingImageDownload -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(WdipiShapes.photo)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(WdipiSpacing.sm))
                    Text(
                        stringResource(R.string.detail_photo_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item.description?.let { note ->
            Column(verticalArrangement = Arrangement.spacedBy(WdipiSpacing.xs)) {
                Text(
                    stringResource(R.string.detail_note),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(note, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item.category?.let {
                Text(categoryLabel(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.detail_saved, relativeTime(item.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.updatedAt.toEpochMilli() - item.createdAt.toEpochMilli() > EDITED_THRESHOLD_MS) {
                Text(
                    stringResource(R.string.detail_updated, relativeTime(item.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SyncNotice(item.syncState)
    }
}

@Composable
private fun SyncNotice(syncState: SyncState) {
    val (icon, text) = when (syncState) {
        SyncState.SYNCED -> return
        SyncState.FAILED -> Icons.Outlined.CloudOff to stringResource(R.string.sync_item_failed)
        else -> Icons.Outlined.CloudUpload to stringResource(R.string.sync_item_pending)
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(WdipiSpacing.sm))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val EDITED_THRESHOLD_MS = 60_000L
