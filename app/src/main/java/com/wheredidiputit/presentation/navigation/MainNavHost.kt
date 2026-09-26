package com.wheredidiputit.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wheredidiputit.R
import com.wheredidiputit.presentation.common.LocalAdsController
import com.wheredidiputit.presentation.common.LocalSnackbarHostState
import com.wheredidiputit.presentation.detail.DetailScreen
import com.wheredidiputit.presentation.favorites.FavoritesScreen
import com.wheredidiputit.presentation.home.HomeScreen
import com.wheredidiputit.presentation.premium.PremiumScreen
import com.wheredidiputit.presentation.remember.RememberScreen
import com.wheredidiputit.presentation.settings.PrivacyScreen
import com.wheredidiputit.presentation.settings.SettingsScreen
import kotlinx.coroutines.launch

private enum class TopLevel(
    val route: Any,
    val matches: (NavDestination) -> Boolean,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME(HomeRoute, { it.hasRoute<HomeRoute>() }, R.string.nav_home, Icons.Outlined.Home, Icons.Rounded.Home),
    FAVORITES(FavoritesRoute, { it.hasRoute<FavoritesRoute>() }, R.string.nav_favorites, Icons.Outlined.FavoriteBorder, Icons.Rounded.Favorite),
    SETTINGS(SettingsRoute, { it.hasRoute<SettingsRoute>() }, R.string.nav_settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
}

@Composable
fun MainNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTopLevel = TopLevel.entries.firstOrNull { tab ->
        destination?.hierarchy?.any(tab.matches) == true
    }
    val snackbarHostState = LocalSnackbarHostState.current
    val ads = LocalAdsController.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun announce(textRes: Int) {
        scope.launch { snackbarHostState.showSnackbar(context.getString(textRes)) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentTopLevel != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    TopLevel.entries.forEach { tab ->
                        val selected = tab == currentTopLevel
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(150)) },
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onRemember = { navController.navigate(RememberRoute()) },
                    onOpenItem = { id -> navController.navigate(DetailRoute(id)) },
                    onOpenPremium = { navController.navigate(PremiumRoute) },
                )
            }
            composable<FavoritesRoute> {
                FavoritesScreen(onOpenItem = { id -> navController.navigate(DetailRoute(id)) })
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onOpenPrivacy = { navController.navigate(PrivacyRoute) },
                    onOpenPremium = { navController.navigate(PremiumRoute) },
                )
            }
            composable<RememberRoute> {
                RememberScreen(
                    onClose = { navController.popBackStack() },
                    onSaved = { isNew ->
                        navController.popBackStack()
                        announce(if (isNew) R.string.remember_saved else R.string.remember_updated)
                        ads.onNaturalBreak()
                    },
                )
            }
            composable<DetailRoute> {
                DetailScreen(
                    onBack = {
                        navController.popBackStack()
                        ads.onNaturalBreak()
                    },
                    onEdit = { id -> navController.navigate(RememberRoute(id)) },
                    onDeleted = {
                        navController.popBackStack()
                        announce(R.string.detail_deleted)
                    },
                )
            }
            composable<PremiumRoute> {
                PremiumScreen(
                    onClose = { navController.popBackStack() },
                    onPurchased = {
                        navController.popBackStack()
                        announce(R.string.premium_welcome)
                    },
                )
            }
            composable<PrivacyRoute> {
                PrivacyScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
