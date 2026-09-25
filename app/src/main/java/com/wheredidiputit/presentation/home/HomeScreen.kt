package com.wheredidiputit.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wheredidiputit.R
import com.wheredidiputit.core.designsystem.component.EmptyState
import com.wheredidiputit.core.designsystem.component.MemoryCard
import com.wheredidiputit.core.designsystem.theme.WdipiShapes
import com.wheredidiputit.core.designsystem.theme.WdipiSpacing
import com.wheredidiputit.presentation.common.AppSnackbarHost
import com.wheredidiputit.presentation.common.SectionLabel
import com.wheredidiputit.presentation.common.SyncProblemBanner
import com.wheredidiputit.presentation.common.TopLevelScreenInsets

@Composable
fun HomeScreen(
    onRemember: () -> Unit,
    onOpenItem: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = TopLevelScreenInsets,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { AppSnackbarHost() },
        floatingActionButton = {
            if (!state.isEmptyLibrary && !state.isLoading) {
                ExtendedFloatingActionButton(
                    onClick = onRemember,
                    text = { Text(stringResource(R.string.home_remember_something)) },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    shape = WdipiShapes.button,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = WdipiSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(WdipiSpacing.lg),
            ) {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .padding(top = WdipiSpacing.xl)
                        .semantics { heading() },
                )
                SearchField(
                    query = viewModel.query,
                    onQueryChange = viewModel::onQueryChange,
                    onClear = viewModel::clearQuery,
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = WdipiSpacing.screen,
                    end = WdipiSpacing.screen,
                    top = WdipiSpacing.lg,
                    bottom = 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.failedSyncCount > 0) {
                    item(key = "sync-problem") {
                        SyncProblemBanner(onRetry = viewModel::retrySync, modifier = Modifier.animateItem())
                    }
                }
                when {
                    state.isLoading -> Unit
                    state.isEmptyLibrary -> item(key = "empty") {
                        EmptyState(
                            icon = Icons.Outlined.Inventory2,
                            title = stringResource(R.string.home_empty_title),
                            message = stringResource(R.string.home_empty_message),
                            actionLabel = stringResource(R.string.home_remember_something),
                            onAction = onRemember,
                        )
                    }
                    state.isSearching && state.items.isEmpty() -> item(key = "no-results") {
                        EmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = stringResource(R.string.home_no_results_title, state.activeQuery),
                            message = stringResource(R.string.home_no_results_message),
                        )
                    }
                    else -> {
                        item(key = "label") {
                            SectionLabel(
                                text = if (state.isSearching) {
                                    pluralStringResource(R.plurals.home_results_count, state.items.size, state.items.size)
                                } else {
                                    stringResource(R.string.home_recent)
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        items(state.items, key = { it.id }) { item ->
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
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        placeholder = { Text(stringResource(R.string.home_search_hint)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.home_search_clear))
                }
            }
        },
        singleLine = true,
        shape = WdipiShapes.input,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
