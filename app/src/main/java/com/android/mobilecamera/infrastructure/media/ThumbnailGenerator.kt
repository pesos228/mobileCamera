package com.android.mobilecamera.infrastructure.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private const val TAG = "ThumbnailGenerator"

object ThumbnailGenerator {

    private const val THUMBNAIL_SIZE = 300
    private const val THUMBNAIL_QUALITY = 80

    suspend fun generateForPhoto(context: Context, photoUri: String): String? {
        return withContext(Dispatchers.IO) {
            var bitmap: Bitmap? = null
            try {
                val uri = photoUri.toUri()

                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }

                options.inSampleSize = calculateInSampleSize(options)
                options.inJustDecodeBounds = false

                bitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }

                if (bitmap == null) {
                    Log.w(TAG, "Failed to decode bitmap from stream")
                    return@withContext null
                }

                val rotatedBitmap = fixOrientation(context, uri, bitmap)

                val result = saveBitmapToCache(context, rotatedBitmap, "img_thumb")

                if (rotatedBitmap != bitmap) {
                    rotatedBitmap.recycle()
                }

                return@withContext result

            } catch (e: Exception) {
                Log.e(TAG, "Error generating photo thumbnail", e)
                return@withContext null
            } finally {
                bitmap?.recycle()
            }
        }
    }

    suspend fun generateForVideo(context: Context, videoUri: String): String? {
        return withContext(Dispatchers.IO) {
            var bitmap: Bitmap? = null
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, videoUri.toUri())

                bitmap = retriever.getFrameAtTime(
                    1_000_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: retriever.frameAtTime

                if (bitmap != null) {
                    val result = saveBitmapToCache(context, bitmap, "vid_thumb")
                    bitmap.recycle()
                    return@withContext result
                } else {
                    Log.w(TAG, "Failed to retrieve video frame for thumbnail")
                    return@withContext null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating video thumbnail", e)
                return@withContext null
            } finally {
                try {
                    retriever?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing MediaMetadataRetriever", e)
                }
                bitmap?.recycle()
            }
        }
    }

    private fun fixOrientation(context: Context, uri: android.net.Uri, bitmap: Bitmap): Bitmap {
        return try {
            val exif = context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it)
            }

            val orientation = exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL

            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                val rotated = Bitmap.createBitmap(
                    bitmap, 0, 0,
                    bitmap.width, bitmap.height,
                    matrix, true
                )
                bitmap.recycle()
                rotated
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fix orientation, using original", e)
            bitmap
        }
    }

    private fun saveBitmapToCache(context: Context, bitmap: Bitmap, prefix: String): String? {
        return try {
            val fileName = "${prefix}_${UUID.randomUUID()}.jpg"
            val file = File(context.cacheDir, "thumbnails/$fileName")
            file.parentFile?.mkdirs()

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save thumbnail to cache", e)
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > THUMBNAIL_SIZE || width > THUMBNAIL_SIZE) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= THUMBNAIL_SIZE &&
                (halfWidth / inSampleSize) >= THUMBNAIL_SIZE) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}