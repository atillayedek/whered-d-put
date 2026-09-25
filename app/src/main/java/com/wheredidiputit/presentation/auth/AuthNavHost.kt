package com.wheredidiputit.presentation.auth

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wheredidiputit.presentation.navigation.ForgotPasswordRoute
import com.wheredidiputit.presentation.navigation.SignInRoute
import com.wheredidiputit.presentation.navigation.SignUpRoute
import com.wheredidiputit.presentation.navigation.VerifyEmailRoute

@Composable
fun AuthNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = SignInRoute(),
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(150)) },
    ) {
        composable<SignInRoute> {
            SignInScreen(
                onCreateAccount = { navController.navigate(SignUpRoute) },
                onForgotPassword = { navController.navigate(ForgotPasswordRoute) },
                onVerifyEmail = { email -> navController.navigate(VerifyEmailRoute(email)) },
            )
        }
        composable<SignUpRoute> {
            SignUpScreen(
                onBack = { navController.popBackStack() },
                onVerificationRequired = { email ->
                    navController.navigate(VerifyEmailRoute(email)) {
                        popUpTo<SignInRoute> { inclusive = false }
                    }
                },
            )
        }
        composable<VerifyEmailRoute> {
            VerifyEmailScreen(
                onBackToSignIn = { email ->
                    navController.navigate(SignInRoute(email)) {
                        popUpTo<SignInRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<ForgotPasswordRoute> {
            ForgotPasswordScreen(onBack = { navController.popBackStack() })
        }
    }
}
