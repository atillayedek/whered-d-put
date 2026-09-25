package com.wheredidiputit.presentation.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wheredidiputit.R
import com.wheredidiputit.core.designsystem.component.EmptyState
import com.wheredidiputit.core.designsystem.component.MemoryCard
import com.wheredidiputit.core.designsystem.theme.WdipiSpacing
import com.wheredidiputit.domain.model.Item
import com.wheredidiputit.domain.repository.ItemRepository
import com.wheredidiputit.presentation.common.AppSnackbarHost
import com.wheredidiputit.presentation.common.TopLevelScreenInsets
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
) : ViewModel() {

    /** Null while the first result is loading. */
    val favorites: StateFlow<List<Item>?> = itemRepository.observeFavorites()
        .map<List<Item>, List<Item>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleFavorite(item: Item) {
        viewModelScope.launch { itemRepository.setFavorite(item.id, !item.isFavorite) }
    }
}

@Composable
fun FavoritesScreen(
    onOpenItem: (String) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = TopLevelScreenInsets,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { AppSnackbarHost() },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = WdipiSpacing.screen, vertical = WdipiSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.favorites_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .padding(bottom = WdipiSpacing.sm)
                        .semantics { heading() },
                )
            }
            val list = favorites
            when {
                list == null -> Unit
                list.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.FavoriteBorder,
                        title = stringResource(R.string.favorites_empty_title),
                        message = stringResource(R.string.favorites_empty_message),
                    )
                }
                else -> items(list, key = { it.id }) { item ->
                    MemoryCard(
                        item = item,
                        onClick = { onOpenItem(item.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(item) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}
