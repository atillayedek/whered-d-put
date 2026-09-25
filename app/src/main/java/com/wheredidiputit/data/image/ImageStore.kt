package com.wheredidiputit.data.image

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns every photo file the app keeps. Photos live in app-private storage,
 * are excluded from backups and are only ever touched inside these folders.
 */
@Singleton
class ImageStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val imagesDir = File(context.filesDir, "images")
    val stagingDir = File(context.cacheDir, "photo-staging")

    fun newStagingFile(): File {
        stagingDir.mkdirs()
        return File(stagingDir, "${UUID.randomUUID()}.jpg")
    }

    /** Moves a processed photo from staging into permanent storage for [itemId]. */
    fun adoptStaged(stagedPath: String, itemId: String): String? {
        val staged = File(stagedPath)
        if (!staged.isInside(stagingDir) || !staged.isFile) return null
        imagesDir.mkdirs()
        val target = File(imagesDir, "$itemId-${UUID.randomUUID()}.jpg")
        if (!staged.renameTo(target)) {
            staged.copyTo(target, overwrite = true)
            staged.delete()
        }
        return target.absolutePath
    }

    fun saveDownloaded(itemId: String, bytes: ByteArray): String {
        imagesDir.mkdirs()
        val target = File(imagesDir, "$itemId-${UUID.randomUUID()}.jpg")
        target.writeBytes(bytes)
        return target.absolutePath
    }

    fun read(path: String): ByteArray? {
        val file = File(path)
        return if (file.isInside(imagesDir) && file.isFile) file.readBytes() else null
    }

    fun delete(path: String?) {
        if (path == null) return
        val file = File(path)
        if (file.isInside(imagesDir) || file.isInside(stagingDir)) file.delete()
    }

    /** Removes staged photos left behind when a Remember screen was abandoned. */
    fun pruneStaging(maxAgeMillis: Long = STAGING_MAX_AGE_MILLIS) {
        val now = System.currentTimeMillis()
        stagingDir.listFiles()?.filter { now - it.lastModified() > maxAgeMillis }?.forEach { it.delete() }
    }

    fun clearAll() {
        imagesDir.deleteRecursively()
        stagingDir.deleteRecursively()
    }

    private fun File.isInside(dir: File): Boolean =
        canonicalFile.parentFile?.canonicalPath == dir.canonicalPath

    private companion object {
        const val STAGING_MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
    }
}
