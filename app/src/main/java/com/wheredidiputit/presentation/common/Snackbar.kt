package com.wheredidiputit.presentation.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** One snackbar queue for the whole app so messages survive navigation. */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun AppSnackbarHost() {
    SnackbarHost(LocalSnackbarHostState.current)
}

/** Insets for screens shown above the bottom navigation bar, which handles the bottom edge. */
val TopLevelScreenInsets: WindowInsets
    @Composable get() = WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
