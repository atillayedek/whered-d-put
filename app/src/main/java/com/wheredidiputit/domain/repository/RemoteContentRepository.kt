package com.wheredidiputit.domain.repository

import com.wheredidiputit.domain.model.Announcement
import com.wheredidiputit.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Settings and the current announcement, published from the admin panel. */
interface RemoteContentRepository {
    val settings: StateFlow<AppSettings>

    /** The active announcement, unless the person has closed it. */
    val announcement: Flow<Announcement?>

    fun refresh()

    suspend fun dismissAnnouncement(id: String)
}
