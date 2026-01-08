package com.android.mobilecamera.infrastructure.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.android.mobilecamera.data.database.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class MediaSyncManager(
    private val context: Context,
) {
    private val contentResolver = context.contentResolver
    private val folderName = "MobileCamera"

    suspend fun syncMedia(
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
        onSave: suspend (uri: String, type: MediaType, duration: Long?, timestamp: Long, thumbPath: String?) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        try {
            val photos = scanMediaStore(MediaType.PHOTO)
            val videos = scanMediaStore(MediaType.VIDEO)
            val allMedia = (photos + videos).sortedByDescending { it.timestamp }

            if (allMedia.isEmpty()) return@withContext 0

            Log.d("MediaSyncManager", "Found ${allMedia.size} files. Starting parallel sync...")

            val cores = Runtime.getRuntime().availableProcessors()
            val parallelism = cores.coerceIn(3, 5)
            val semaphore = Semaphore(parallelism)
            val progressCounter = AtomicInteger(0)

            allMedia.map { mediaInfo ->
                async {
                    semaphore.withPermit {
                        try {
                            val thumbnailPath = generateThumbnail(mediaInfo)

                            onSave(
                                mediaInfo.uri.toString(),
                                mediaInfo.type,
                                mediaInfo.duration,
                                mediaInfo.timestamp,
                                thumbnailPath
                            )
                        } catch (e: Exception) {
                            Log.e("MediaSyncManager", "Error processing ${mediaInfo.displayName}", e)
                        } finally {
                            val current = progressCounter.incrementAndGet()
                            onProgress?.invoke(current, allMedia.size)
                        }
                    }
                }
            }.awaitAll()

            Log.d("MediaSyncManager", "Sync completed")
            return@withContext allMedia.size

        } catch (e: Exception) {
            Log.e("MediaSyncManager", "Sync global failed", e)
            return@withContext 0
        }
    }

    private fun scanMediaStore(type: MediaType): List<MediaInfo> {
        val mediaList = mutableListOf<MediaInfo>()
        val isQ = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val collectionUri: Uri
        val durationColumn: String?

        if (type == MediaType.VIDEO) {
            collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            durationColumn = MediaStore.Video.Media.DURATION
        } else {
            collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            durationColumn = null
        }

        val idCol = MediaStore.MediaColumns._ID
        val nameCol = MediaStore.MediaColumns.DISPLAY_NAME
        val dateCol = MediaStore.MediaColumns.DATE_ADDED

        // Путь (разный для версий Android)
        val pathCol = if (isQ) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA

        val projection = mutableListOf(idCol, nameCol, dateCol, pathCol).apply {
            if (durationColumn != null) add(durationColumn)
        }.toTypedArray()

        // WHERE условие
        val selection = "$pathCol LIKE ?"

        // Аргументы поиска
        val selectionArgs = arrayOf("%$folderName%")

        val sortOrder = "$dateCol DESC"

        try {
            contentResolver.query(
                collectionUri,
                projection, selection, selectionArgs, sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(idCol)
                val nameIdx = cursor.getColumnIndexOrThrow(nameCol)
                val dateIdx = cursor.getColumnIndexOrThrow(dateCol)
                val durationIdx = durationColumn?.let { cursor.getColumnIndex(it) } ?: -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx)
                    val dateAdded = cursor.getLong(dateIdx) * 1000

                    // Безопасно читаем длительность только для видео
                    val duration = if (durationIdx != -1) cursor.getLong(durationIdx) else null

                    val contentUri = ContentUris.withAppendedId(collectionUri, id)

                    mediaList.add(
                        MediaInfo(
                            uri = contentUri,
                            displayName = name,
                            type = type,
                            timestamp = dateAdded,
                            duration = duration
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MediaSyncManager", "Scan failed for $type", e)
        }

        return mediaList
    }

    private suspend fun generateThumbnail(mediaInfo: MediaInfo): String? {
        return try {
            when (mediaInfo.type) {
                MediaType.PHOTO -> ThumbnailGenerator.generateForPhoto(context, mediaInfo.uri.toString())
                MediaType.VIDEO -> ThumbnailGenerator.generateForVideo(context, mediaInfo.uri.toString())
            }
        } catch (e: Exception) {
            null
        }
    }

    private data class MediaInfo(
        val uri: Uri,
        val displayName: String,
        val type: MediaType,
        val timestamp: Long,
        val duration: Long?
    )
}