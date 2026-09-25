package com.wheredidiputit.presentation.remember

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.wheredidiputit.R
import com.wheredidiputit.core.designsystem.component.EmptyState
import com.wheredidiputit.core.designsystem.component.NavIcon
import com.wheredidiputit.core.designsystem.component.PrimaryButton
import com.wheredidiputit.core.designsystem.component.QuietButton
import com.wheredidiputit.core.designsystem.component.WdipiTextField
import com.wheredidiputit.core.designsystem.component.WdipiTopBar
import com.wheredidiputit.core.designsystem.theme.WdipiShapes
import com.wheredidiputit.core.designsystem.theme.WdipiSpacing
import com.wheredidiputit.core.util.rememberHaptics
import com.wheredidiputit.domain.model.Category
import com.wheredidiputit.domain.model.ItemLimits
import com.wheredidiputit.presentation.common.AppSnackbarHost
import com.wheredidiputit.presentation.common.LocalSnackbarHostState
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun RememberScreen(
    onClose: () -> Unit,
    onSaved: (isNew: Boolean) -> Unit,
    viewModel: RememberViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    fun showMessage(textRes: Int) {
        scope.launch { snackbarHostState.showSnackbar(context.getString(textRes)) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RememberEvent.Saved -> {
                    haptics.confirm()
                    onSaved(event.isNew)
                }
                is RememberEvent.Message -> showMessage(event.textRes)
            }
        }
    }

    val speech = rememberSpeechCapture(
        onResult = viewModel::onSpeechResult,
        onError = { error ->
            showMessage(
                when (error) {
                    SpeechError.NO_MATCH -> R.string.speech_no_match
                    SpeechError.NO_PERMISSION -> R.string.speech_permission_denied
                    SpeechError.UNAVAILABLE -> R.string.speech_unavailable
                },
            )
        },
    )
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.start() else showMessage(R.string.speech_permission_denied)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onPhotoPicked(it.toString()) }
    }

    fun startSpeech() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) speech.start() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun pickPhoto() {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { AppSnackbarHost() },
        topBar = {
            WdipiTopBar(
                title = stringResource(if (state.isEditing) R.string.remember_edit_title else R.string.remember_title),
                onNavigate = onClose,
                navIcon = NavIcon.CLOSE,
            )
        },
        bottomBar = {
            if (!state.notFound && !state.isLoading) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PrimaryButton(
                        text = stringResource(if (state.isEditing) R.string.remember_save_changes else R.string.remember_save),
                        onClick = viewModel::save,
                        loading = state.isSaving,
                        enabled = !state.isProcessingPhoto && !speech.isListening,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = WdipiSpacing.screen, vertical = WdipiSpacing.md),
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize())
            state.notFound -> EmptyState(
                icon = Icons.Outlined.Close,
                title = stringResource(R.string.detail_not_found_title),
                message = stringResource(R.string.detail_not_found_message),
                modifier = Modifier.padding(padding),
            )
            else -> RememberForm(
                state = state,
                viewModel = viewModel,
                speech = speech,
                onSpeak = ::startSpeech,
                onPickPhoto = ::pickPhoto,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (state.showNewCategoryDialog) {
        NewCategoryDialog(
            onCreate = viewModel::createCategory,
            onDismiss = { viewModel.showNewCategoryDialog(false) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RememberForm(
    state: RememberUiState,
    viewModel: RememberViewModel,
    speech: SpeechCapture,
    onSpeak: () -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = WdipiSpacing.screen)
            .padding(bottom = WdipiSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(WdipiSpacing.lg),
    ) {
        if (!state.isEditing) {
            Text(
                text = stringResource(R.string.remember_prompt),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = WdipiSpacing.sm),
            )
        }

        CaptureActions(
            speechAvailable = speech.isAvailable,
            isListening = speech.isListening,
            showPhotoAction = state.photo == PhotoUi.None && !state.isProcessingPhoto,
            onSpeak = onSpeak,
            onPickPhoto = onPickPhoto,
        )

        AnimatedVisibility(visible = speech.isListening, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            ListeningPanel(partialText = speech.partialText, onStop = speech::stop)
        }

        state.heard?.let { heard ->
            HeardNotice(text = heard, onDismiss = viewModel::dismissHeard)
        }

        WdipiTextField(
            value = state.title,
            onValueChange = viewModel::onTitleChange,
            label = stringResource(R.string.remember_item_label),
            hint = stringResource(R.string.remember_item_hint),
            error = if (state.showTitleError) stringResource(R.string.remember_item_required) else null,
            maxLength = ItemLimits.TITLE_MAX,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        )

        WdipiTextField(
            value = state.location,
            onValueChange = viewModel::onLocationChange,
            label = stringResource(R.string.remember_location_label),
            hint = stringResource(R.string.remember_location_hint),
            error = if (state.showLocationError) stringResource(R.string.remember_location_required) else null,
            singleLine = false,
            maxLength = ItemLimits.LOCATION_MAX,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
        )

        PhotoSection(
            photo = state.photo,
            isProcessing = state.isProcessingPhoto,
            onChange = onPickPhoto,
            onRemove = viewModel::removePhoto,
        )

        TextButton(
            onClick = viewModel::toggleDetails,
            modifier = Modifier.heightIn(min = WdipiSpacing.minTouchTarget),
        ) {
            Text(stringResource(if (state.showDetails) R.string.remember_fewer_details else R.string.remember_more_details))
            Spacer(Modifier.width(WdipiSpacing.xs))
            Icon(if (state.showDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        }

        AnimatedVisibility(visible = state.showDetails, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(WdipiSpacing.lg)) {
                Text(stringResource(R.string.remember_category_label), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(WdipiSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(WdipiSpacing.xs),
                ) {
                    state.categories.forEach { category ->
                        CategoryChip(
                            category = category,
                            selected = state.categoryId == category.id,
                            onClick = { viewModel.onCategorySelected(category.id) },
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.showNewCategoryDialog(true) },
                        label = { Text(stringResource(R.string.category_new)) },
                        leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.heightIn(min = WdipiSpacing.minTouchTarget),
                    )
                }
                WdipiTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = stringResource(R.string.remember_note_label),
                    hint = stringResource(R.string.remember_note_hint),
                    singleLine = false,
                    minLines = 3,
                    maxLength = ItemLimits.DESCRIPTION_MAX,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        }
    }
}

@Composable
private fun CaptureActions(
    speechAvailable: Boolean,
    isListening: Boolean,
    showPhotoAction: Boolean,
    onSpeak: () -> Unit,
    onPickPhoto: () -> Unit,
) {
    if (!speechAvailable && !showPhotoAction) return
    Row(horizontalArrangement = Arrangement.spacedBy(WdipiSpacing.sm)) {
        if (speechAvailable) {
            FilledTonalButton(
                onClick = onSpeak,
                enabled = !isListening,
                shape = WdipiShapes.button,
                modifier = Modifier.heightIn(min = WdipiSpacing.minTouchTarget),
            ) {
                Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(WdipiSpacing.sm))
                Text(stringResource(R.string.remember_speak))
            }
        }
        if (showPhotoAction) {
            OutlinedButton(
                onClick = onPickPhoto,
                shape = WdipiShapes.button,
                modifier = Modifier.heightIn(min = WdipiSpacing.minTouchTarget),
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(WdipiSpacing.sm))
                Text(stringResource(R.string.remember_add_photo))
            }
        }
    }
}

@Composable
private fun ListeningPanel(partialText: String, onStop: () -> Unit) {
    Surface(
        shape = WdipiShapes.card,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = WdipiSpacing.lg, end = WdipiSpacing.sm, top = WdipiSpacing.md, bottom = WdipiSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(WdipiSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.speech_listening),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = partialText.ifEmpty { stringResource(R.string.speech_listening_hint) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            TextButton(onClick = onStop, modifier = Modifier.heightIn(min = WdipiSpacing.minTouchTarget)) {
                Text(stringResource(R.string.speech_done))
            }
        }
    }
}

@Composable
private fun HeardNotice(text: String, onDismiss: () -> Unit) {
    Surface(
        shape = WdipiShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = WdipiSpacing.lg, top = WdipiSpacing.sm, bottom = WdipiSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.speech_heard, text),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.speech_check),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_dismiss))
            }
        }
    }
}

@Composable
private fun PhotoSection(
    photo: PhotoUi,
    isProcessing: Boolean,
    onChange: () -> Unit,
    onRemove: () -> Unit,
) {
    val path = when (photo) {
        is PhotoUi.Staged -> photo.path
        is PhotoUi.Existing -> photo.path
        PhotoUi.None -> null
    }
    when {
        isProcessing -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(WdipiShapes.photo)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
        photo != PhotoUi.None -> Column(verticalArrangement = Arrangement.spacedBy(WdipiSpacing.xs)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(WdipiShapes.photo)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (path != null) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = stringResource(R.string.a11y_item_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Outlined.Image, contentDescription = stringResource(R.string.detail_photo_pending))
                }
            }
            Row {
                QuietButton(text = stringResource(R.string.remember_change_photo), onClick = onChange)
                QuietButton(text = stringResource(R.string.remember_remove_photo), onClick = onRemove, destructive = true)
            }
        }
    }
}

@Composable
private fun CategoryChip(category: Category, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(category.name) },
        modifier = Modifier.heightIn(min = WdipiSpacing.minTouchTarget),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun NewCategoryDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category_new_title)) },
        text = {
            WdipiTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.category_name_label),
                maxLength = ItemLimits.CATEGORY_NAME_MAX,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            QuietButton(
                text = stringResource(R.string.category_create),
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            )
        },
        dismissButton = { QuietButton(text = stringResource(R.string.action_cancel), onClick = onDismiss) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
}
