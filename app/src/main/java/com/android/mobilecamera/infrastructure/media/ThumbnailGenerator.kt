package com.android.mobilecamera.infrastructure.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ThumbnailGenerator {

    private const val THUMBNAIL_SIZE = 300
    private const val THUMBNAIL_QUALITY = 80

    suspend fun generateForVideo(context: Context, videoUri: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, videoUri.toUri())

                val bitmap = retriever.getFrameAtTime(
                    1_000_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: retriever.frameAtTime

                retriever.release()

                bitmap?.let { saveBitmapToCache(context, it, "vid_thumb") }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun generateForPhoto(context: Context, photoUri: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val uri = photoUri.toUri()

                // Читаем размеры
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }

                // Уменьшаем
                options.inSampleSize = calculateInSampleSize(options)
                options.inJustDecodeBounds = false

                val bitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: return@withContext null

                // Исправляем ориентацию по EXIF
                val rotatedBitmap = fixOrientation(context, uri, bitmap)

                saveBitmapToCache(context, rotatedBitmap, "img_thumb")
            } catch (e: Exception) {
                e.printStackTrace()
                null
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
            e.printStackTrace()
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
            bitmap.recycle()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Упрощенная версия - всегда THUMBNAIL_SIZE
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