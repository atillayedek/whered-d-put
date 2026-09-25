package com.wheredidiputit.data.image

import android.net.Uri
import com.wheredidiputit.domain.model.AppResult
import com.wheredidiputit.domain.repository.PhotoRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepositoryImpl @Inject constructor(
    private val processor: ImageProcessor,
    private val store: ImageStore,
) : PhotoRepository {
    override suspend fun prepare(uri: String): AppResult<String> = processor.prepare(Uri.parse(uri))

    override fun discard(stagedPath: String) = store.delete(stagedPath)
}
