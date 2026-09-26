package com.wheredidiputit.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wheredidiputit.R
import com.wheredidiputit.domain.model.Category

private val builtInLabels = mapOf(
    "00000000-0000-4000-8000-000000000001" to R.string.category_documents,
    "00000000-0000-4000-8000-000000000002" to R.string.category_keys,
    "00000000-0000-4000-8000-000000000003" to R.string.category_electronics,
    "00000000-0000-4000-8000-000000000004" to R.string.category_clothes,
    "00000000-0000-4000-8000-000000000005" to R.string.category_tools,
    "00000000-0000-4000-8000-000000000006" to R.string.category_other,
)

/** Built-in categories are translated; custom ones show the name the person typed. */
@Composable
fun categoryLabel(category: Category): String =
    builtInLabels[category.id]?.let { stringResource(it) } ?: category.name
