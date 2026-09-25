package com.wheredidiputit.domain.repository

import com.wheredidiputit.domain.model.AppResult

interface PhotoRepository {
    /** Optimises a picked photo and returns the path of the staged, metadata-free copy. */
    suspend fun prepare(uri: String): AppResult<String>

    /** Deletes a staged photo that was not saved. */
    fun discard(stagedPath: String)
}
