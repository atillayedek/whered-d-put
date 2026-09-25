package com.wheredidiputit.data.image

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.wheredidiputit.core.di.IoDispatcher
import com.wheredidiputit.domain.model.AppError
import com.wheredidiputit.domain.model.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Turns a photo from the system Photo Picker into a small, upload-ready JPEG.
 *
 * - Only image MIME types are accepted, and originals are capped in size.
 * - The image is rotated upright and scaled so its longest edge is at most
 *   [MAX_EDGE_PX].
 * - Re-encoding through [Bitmap.compress] writes no EXIF block, so location,
 *   device and time metadata from the original are never stored or uploaded.
 */
@Singleton
class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageStore: ImageStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun prepare(uri: Uri): AppResult<String> = withContext(ioDispatcher) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.lowercase()
        if (mime == null || mime !in ACCEPTED_MIME_TYPES) {
            return@withContext AppResult.Failure(AppError.PHOTO_UNSUPPORTED)
        }
        val size = resolver.querySize(uri)
        if (size != null && size > MAX_INPUT_BYTES) {
            return@withContext AppResult.Failure(AppError.PHOTO_TOO_LARGE)
        }

        val output = imageStore.newStagingFile()
        try {
            val bitmap = decode(resolver, uri) ?: return@withContext AppResult.Failure(AppError.PHOTO_UNSUPPORTED)
            try {
                output.outputStream().use { stream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                        return@withContext AppResult.Failure(AppError.PHOTO_UNSUPPORTED)
                    }
                }
            } finally {
                bitmap.recycle()
            }
            if (output.length() > MAX_OUTPUT_BYTES) {
                output.delete()
                return@withContext AppResult.Failure(AppError.PHOTO_TOO_LARGE)
            }
            AppResult.Success(output.absolutePath)
        } catch (e: CancellationException) {
            output.delete()
            throw e
        } catch (e: Exception) {
            output.delete()
            AppResult.Failure(AppError.PHOTO_UNSUPPORTED)
        } catch (e: OutOfMemoryError) {
            output.delete()
            AppResult.Failure(AppError.PHOTO_TOO_LARGE)
        }
    }

    private fun decode(resolver: ContentResolver, uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            // ImageDecoder applies EXIF orientation itself.
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val (w, h) = info.size.width to info.size.height
                val scale = MAX_EDGE_PX.toFloat() / max(w, h)
                if (scale < 1f) {
                    decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
                }
            }
        } else {
            decodeLegacy(resolver, uri)
        }

    private fun decodeLegacy(resolver: ContentResolver, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_EDGE_PX) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null

        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val scale = MAX_EDGE_PX.toFloat() / max(decoded.width, decoded.height)
        if (rotation == 0f && scale >= 1f) return decoded

        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        val transformed = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (transformed != decoded) decoded.recycle()
        return transformed
    }

    private fun ContentResolver.querySize(uri: Uri): Long? =
        query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }

    companion object {
        const val MAX_EDGE_PX = 1600
        const val JPEG_QUALITY = 82
        const val MAX_INPUT_BYTES = 30L * 1024 * 1024
        const val MAX_OUTPUT_BYTES = 5L * 1024 * 1024

        private val ACCEPTED_MIME_TYPES = setOf(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif",
        )
    }
}
