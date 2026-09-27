package com.wheredidiputit.presentation.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheredidiputit.domain.model.Announcement
import com.wheredidiputit.domain.model.FreePlan
import com.wheredidiputit.domain.model.Item
import com.wheredidiputit.domain.repository.ItemRepository
import com.wheredidiputit.domain.repository.PremiumRepository
import com.wheredidiputit.domain.repository.RemoteContentRepository
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
    /** Free-plan limit currently set by the admin. */
    val itemLimit: Int = FreePlan.DEFAULT_ITEM_LIMIT,
    val announcement: Announcement? = null,
) {
    val isSearching: Boolean get() = activeQuery.isNotEmpty()

    /** A new memory needs Premium or a deleted one first. */
    val limitReached: Boolean get() = !isPremium && itemCount >= itemLimit

    /** Tell free users where they stand once they're close to the limit. */
    val showPlanNotice: Boolean get() = !isPremium && !isSearching && itemCount >= itemLimit - 1
    val isEmptyLibrary: Boolean get() = !isLoading && !isSearching && items.isEmpty()
}

@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val syncController: SyncController,
    premiumRepository: PremiumRepository,
    private val remoteContent: RemoteContentRepository,
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
        combine(premiumRepository.state, remoteContent.settings, remoteContent.announcement) { premium, settings, announcement ->
            Triple(premium.isPremium, settings.freeItemLimit, announcement)
        },
    ) { (activeQuery, items), failed, count, (isPremium, limit, announcement) ->
        HomeUiState(
            isLoading = false,
            activeQuery = activeQuery,
            items = items,
            failedSyncCount = failed,
            itemCount = count,
            isPremium = isPremium,
            itemLimit = limit,
            announcement = announcement,
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

    fun dismissAnnouncement(id: String) {
        viewModelScope.launch { remoteContent.dismissAnnouncement(id) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val MAX_QUERY_LENGTH = 100
    }
}
