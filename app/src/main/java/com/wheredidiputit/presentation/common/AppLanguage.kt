package com.wheredidiputit.presentation.common

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.LocaleListCompat
import com.wheredidiputit.core.designsystem.theme.WdipiSpacing

/** Languages the app ships. Labels are shown in their own language. */
enum class AppLanguage(val tag: String, val label: String) {
    ENGLISH("en", "English"),
    TURKISH("tr", "Türkçe"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { tag?.startsWith(it.tag) == true } ?: ENGLISH
    }
}

/**
 * Applies the chosen language app-wide. AppCompat persists the choice and
 * recreates the activity, so every screen switches immediately.
 */
fun setAppLanguage(language: AppLanguage) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
}

/** The language the UI is currently shown in. */
@Composable
fun currentAppLanguage(): AppLanguage =
    AppLanguage.fromTag(LocalConfiguration.current.locales[0]?.language)

@Composable
fun LanguageSelector(modifier: Modifier = Modifier) {
    val current = currentAppLanguage()
    val options = AppLanguage.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, language ->
            SegmentedButton(
                selected = current == language,
                onClick = { if (current != language) setAppLanguage(language) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                modifier = Modifier.heightIn(min = WdipiSpacing.minTouchTarget),
            ) {
                Text(language.label)
            }
        }
    }
}
