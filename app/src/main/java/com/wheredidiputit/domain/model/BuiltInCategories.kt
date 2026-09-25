package com.wheredidiputit.domain.model

/**
 * The built-in taxonomy. IDs match the rows created by the
 * `20260925000001_initial_schema.sql` migration so references are consistent
 * between the device and the cloud.
 */
object BuiltInCategories {
    val all: List<Category> = listOf(
        Category("00000000-0000-4000-8000-000000000001", "Documents", isBuiltIn = true),
        Category("00000000-0000-4000-8000-000000000002", "Keys", isBuiltIn = true),
        Category("00000000-0000-4000-8000-000000000003", "Electronics", isBuiltIn = true),
        Category("00000000-0000-4000-8000-000000000004", "Clothes", isBuiltIn = true),
        Category("00000000-0000-4000-8000-000000000005", "Tools", isBuiltIn = true),
        Category("00000000-0000-4000-8000-000000000006", "Other", isBuiltIn = true),
    )

    private val ids = all.mapTo(HashSet()) { it.id }

    fun isBuiltIn(id: String): Boolean = id in ids
}
