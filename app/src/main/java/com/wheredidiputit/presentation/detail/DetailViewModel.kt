package com.wheredidiputit.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.wheredidiputit.domain.model.Item
import com.wheredidiputit.domain.repository.ItemRepository
import com.wheredidiputit.presentation.navigation.DetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState
    data class Loaded(val item: Item) : DetailUiState
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val itemRepository: ItemRepository,
) : ViewModel() {

    private val itemId = savedStateHandle.toRoute<DetailRoute>().itemId
    private val deleting = MutableStateFlow(false)

    val uiState: StateFlow<DetailUiState> = combine(itemRepository.observeItem(itemId), deleting) { item, isDeleting ->
        when {
            isDeleting -> DetailUiState.Loading
            item == null -> DetailUiState.NotFound
            else -> DetailUiState.Loaded(item)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)

    private val deletedChannel = Channel<Unit>(Channel.CONFLATED)
    val deleted: Flow<Unit> = deletedChannel.receiveAsFlow()

    fun toggleFavorite() {
        val item = (uiState.value as? DetailUiState.Loaded)?.item ?: return
        viewModelScope.launch { itemRepository.setFavorite(item.id, !item.isFavorite) }
    }

    fun delete() {
        if (deleting.value) return
        deleting.value = true
        viewModelScope.launch {
            itemRepository.delete(itemId)
            deletedChannel.send(Unit)
        }
    }
}
