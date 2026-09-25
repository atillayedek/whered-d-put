package com.wheredidiputit.presentation.navigation

import kotlinx.serialization.Serializable

// Signed-out graph
@Serializable data class SignInRoute(val email: String? = null)
@Serializable data object SignUpRoute
@Serializable data class VerifyEmailRoute(val email: String)
@Serializable data object ForgotPasswordRoute

// Signed-in graph
@Serializable data object HomeRoute
@Serializable data object FavoritesRoute
@Serializable data object SettingsRoute
@Serializable data class RememberRoute(val itemId: String? = null)
@Serializable data class DetailRoute(val itemId: String)
@Serializable data object PrivacyRoute
