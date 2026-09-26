package com.wheredidiputit.presentation.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheredidiputit.domain.model.FreePlan
import com.wheredidiputit.domain.model.Item
import com.wheredidiputit.domain.repository.ItemRepository
import com.wheredidiputit.domain.repository.PremiumRepository
import com.wheredidiputit.domain.repository.SyncController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    /** The query the current [items] belong to (after debounce). */
    val activeQuery: String = "",
    val items: List<Item> = emptyList(),
    val failedSyncCount: Int = 0,
    /** Memories that count towards the free plan's limit. */
    val itemCount: Int = 0,
    val isPremium: Boolean = false,
) {
    val isSearching: Boolean get() = activeQuery.isNotEmpty()

    /** A new memory needs Premium or a deleted one first. */
    val limitReached: Boolean get() = !isPremium && itemCount >= FreePlan.ITEM_LIMIT

    /** Tell free users where they stand once they're close to the limit. */
    val showPlanNotice: Boolean get() = !isPremium && !isSearching && itemCount >= FreePlan.ITEM_LIMIT - 1
    val isEmptyLibrary: Boolean get() = !isLoading && !isSearching && items.isEmpty()
}

@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val syncController: SyncController,
    premiumRepository: PremiumRepository,
) : ViewModel() {

    /** Held as Compose state so typing never lags or jumps the cursor. */
    var query by mutableStateOf("")
        private set

    val uiState: StateFlow<HomeUiState> = combine(
        snapshotFlow { query.trim() }
            .distinctUntilChanged()
            .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
            .flatMapLatest { q -> itemRepository.search(q).map { q to it } },
        itemRepository.observeFailedSyncCount(),
        itemRepository.observeActiveCount(),
        premiumRepository.state,
    ) { (activeQuery, items), failed, count, premium ->
        HomeUiState(
            isLoading = false,
            activeQuery = activeQuery,
            items = items,
            failedSyncCount = failed,
            itemCount = count,
            isPremium = premium.isPremium,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onQueryChange(value: String) {
        query = value.take(MAX_QUERY_LENGTH)
    }

    fun clearQuery() {
        query = ""
    }

    fun toggleFavorite(item: Item) {
        viewModelScope.launch { itemRepository.setFavorite(item.id, !item.isFavorite) }
    }

    fun retrySync() = syncController.requestSync()

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val MAX_QUERY_LENGTH = 100
    }
}
