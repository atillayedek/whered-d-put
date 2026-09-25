package com.wheredidiputit.presentation.remember

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.wheredidiputit.R
import com.wheredidiputit.domain.model.AppError
import com.wheredidiputit.domain.model.AppResult
import com.wheredidiputit.domain.model.Category
import com.wheredidiputit.domain.model.ItemDraft
import com.wheredidiputit.domain.model.ItemLimits
import com.wheredidiputit.domain.model.PhotoChange
import com.wheredidiputit.domain.repository.CategoryRepository
import com.wheredidiputit.domain.repository.ItemRepository
import com.wheredidiputit.domain.repository.PhotoRepository
import com.wheredidiputit.domain.util.SpeechParser
import com.wheredidiputit.presentation.common.messageRes
import com.wheredidiputit.presentation.navigation.RememberRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface PhotoUi {
    data object None : PhotoUi

    /** Photo already saved with this memory; [path] is null until it is downloaded. */
    data class Existing(val path: String?) : PhotoUi

    /** Newly picked, optimised photo not saved yet. */
    data class Staged(val path: String) : PhotoUi
}

data class RememberUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val title: String = "",
    val location: String = "",
    val description: String = "",
    val categoryId: String? = null,
    val categories: List<Category> = emptyList(),
    val photo: PhotoUi = PhotoUi.None,
    val isProcessingPhoto: Boolean = false,
    val showDetails: Boolean = false,
    val showTitleError: Boolean = false,
    val showLocationError: Boolean = false,
    val heard: String? = null,
    val isSaving: Boolean = false,
    val showNewCategoryDialog: Boolean = false,
)

sealed interface RememberEvent {
    data class Saved(val isNew: Boolean) : RememberEvent
    data class Message(@param:StringRes val textRes: Int) : RememberEvent
}

@HiltViewModel
class RememberViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val itemRepository: ItemRepository,
    private val categoryRepository: CategoryRepository,
    private val photoRepository: PhotoRepository,
) : ViewModel() {

    private val itemId: String? = savedStateHandle.toRoute<RememberRoute>().itemId
    private var hadPhotoOriginally = false

    var state by mutableStateOf(RememberUiState(isEditing = itemId != null, isLoading = itemId != null))
        private set

    private val eventChannel = Channel<RememberEvent>(Channel.BUFFERED)
    val events: Flow<RememberEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            categoryRepository.observeCategories().collect { state = state.copy(categories = it) }
        }
        if (itemId != null) {
            viewModelScope.launch {
                val item = itemRepository.observeItem(itemId).first()
                state = if (item == null) {
                    state.copy(isLoading = false, notFound = true)
                } else {
                    hadPhotoOriginally = item.localImagePath != null || item.hasPendingImageDownload
                    state.copy(
                        isLoading = false,
                        title = item.title,
                        location = item.location,
                        description = item.description.orEmpty(),
                        categoryId = item.category?.id,
                        photo = if (hadPhotoOriginally) PhotoUi.Existing(item.localImagePath) else PhotoUi.None,
                        showDetails = item.description != null || item.category != null,
                    )
                }
            }
        }
    }

    fun onTitleChange(value: String) {
        state = state.copy(title = value.take(ItemLimits.TITLE_MAX), showTitleError = false)
    }

    fun onLocationChange(value: String) {
        state = state.copy(location = value.take(ItemLimits.LOCATION_MAX), showLocationError = false)
    }

    fun onDescriptionChange(value: String) {
        state = state.copy(description = value.take(ItemLimits.DESCRIPTION_MAX))
    }

    fun onCategorySelected(id: String?) {
        state = state.copy(categoryId = if (state.categoryId == id) null else id)
    }

    fun toggleDetails() {
        state = state.copy(showDetails = !state.showDetails)
    }

    fun showNewCategoryDialog(show: Boolean) {
        state = state.copy(showNewCategoryDialog = show)
    }

    fun createCategory(name: String) {
        viewModelScope.launch {
            when (val result = categoryRepository.createCategory(name)) {
                is AppResult.Success -> state = state.copy(
                    categoryId = result.value.id,
                    showNewCategoryDialog = false,
                )
                is AppResult.Failure -> eventChannel.send(RememberEvent.Message(R.string.category_create_failed))
            }
        }
    }

    /** Fills the form from speech. Fields stay fully editable afterwards. */
    fun onSpeechResult(transcript: String) {
        val parsed = SpeechParser.parse(transcript)
        state = state.copy(
            title = parsed.item?.take(ItemLimits.TITLE_MAX) ?: state.title,
            location = parsed.location.take(ItemLimits.LOCATION_MAX).ifEmpty { state.location },
            heard = transcript,
            showTitleError = false,
            showLocationError = false,
        )
    }

    fun dismissHeard() {
        state = state.copy(heard = null)
    }

    fun onPhotoPicked(uri: String) {
        state = state.copy(isProcessingPhoto = true)
        viewModelScope.launch {
            when (val result = photoRepository.prepare(uri)) {
                is AppResult.Success -> {
                    (state.photo as? PhotoUi.Staged)?.let { photoRepository.discard(it.path) }
                    state = state.copy(photo = PhotoUi.Staged(result.value), isProcessingPhoto = false)
                }
                is AppResult.Failure -> {
                    state = state.copy(isProcessingPhoto = false)
                    eventChannel.send(
                        RememberEvent.Message(
                            if (result.error == AppError.PHOTO_TOO_LARGE) R.string.photo_too_large else R.string.photo_unsupported,
                        ),
                    )
                }
            }
        }
    }

    fun removePhoto() {
        (state.photo as? PhotoUi.Staged)?.let { photoRepository.discard(it.path) }
        state = state.copy(photo = PhotoUi.None)
    }

    fun save() {
        val current = state
        if (current.isSaving || current.isProcessingPhoto) return
        val titleMissing = current.title.isBlank()
        val locationMissing = current.location.isBlank()
        if (titleMissing || locationMissing) {
            state = current.copy(showTitleError = titleMissing, showLocationError = locationMissing)
            return
        }
        state = current.copy(isSaving = true)

        val photoChange = when (val photo = current.photo) {
            is PhotoUi.Staged -> PhotoChange.Replace(photo.path)
            is PhotoUi.Existing -> PhotoChange.Keep
            PhotoUi.None -> if (hadPhotoOriginally) PhotoChange.Remove else PhotoChange.Keep
        }
        val draft = ItemDraft(
            title = current.title,
            location = current.location,
            description = current.description.ifBlank { null },
            categoryId = current.categoryId,
            photo = photoChange,
        )

        viewModelScope.launch {
            val result = if (itemId == null) {
                itemRepository.create(draft)
            } else {
                itemRepository.update(itemId, draft)
            }
            when (result) {
                is AppResult.Success -> {
                    // The staged file now belongs to the saved memory.
                    state = state.copy(photo = PhotoUi.None, isSaving = false)
                    eventChannel.send(RememberEvent.Saved(isNew = itemId == null))
                }
                is AppResult.Failure -> {
                    state = state.copy(isSaving = false)
                    eventChannel.send(RememberEvent.Message(result.error.messageRes()))
                }
            }
        }
    }

    override fun onCleared() {
        (state.photo as? PhotoUi.Staged)?.let { photoRepository.discard(it.path) }
        super.onCleared()
    }
}
