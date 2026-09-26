package com.wheredidiputit.core.config

import com.wheredidiputit.BuildConfig

object AppConfig {
    val supabaseUrl: String = BuildConfig.SUPABASE_URL
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY

    /** False when the build was made without Supabase credentials. */
    val isBackendConfigured: Boolean =
        supabaseUrl.startsWith("https://") && supabaseAnonKey.isNotBlank()

    /** Deep link the app itself handles (see AndroidManifest.xml). */
    const val AUTH_SCHEME = "wheredidiputit"
    const val AUTH_HOST = "auth-callback"

    /**
     * Email links first land on this web page. On a phone with the app it
     * hands the one-time code to [AUTH_SCHEME]://[AUTH_HOST]; anywhere else it
     * shows a friendly "email confirmed" page instead of a dead link.
     */
    val AUTH_REDIRECT_URL: String = "${BuildConfig.AUTH_WEB_URL.trimEnd('/')}/auth/callback"
    val PASSWORD_RECOVERY_REDIRECT_URL: String = "$AUTH_REDIRECT_URL?type=recovery"

    const val PHOTO_BUCKET = "item-photos"
    const val DELETE_ACCOUNT_FUNCTION = "delete-account"

    /**
     * Premium subscription as created in Play Console (Monetize → Subscriptions):
     * product ID [PREMIUM_PRODUCT_ID] with an auto-renewing monthly base plan
     * [PREMIUM_BASE_PLAN]. The price (34.99 TRY) is set there, not in the app.
     */
    const val PREMIUM_PRODUCT_ID = "wdipi_premium"
    const val PREMIUM_BASE_PLAN = "monthly"

    fun manageSubscriptionUrl(packageName: String): String =
        "https://play.google.com/store/account/subscriptions?sku=$PREMIUM_PRODUCT_ID&package=$packageName"
}
