package com.android.mobilecamera.data.repository

import com.android.mobilecamera.data.database.MediaDao
import com.android.mobilecamera.data.database.MediaEntity
import com.android.mobilecamera.data.database.MediaType
import com.android.mobilecamera.infrastructure.media.DeleteResult
import com.android.mobilecamera.infrastructure.media.MediaManager
import kotlinx.coroutines.flow.Flow

class MediaRepository(
    private val mediaDao: MediaDao,
    private val mediaManager: MediaManager
) {

    val allMedia: Flow<List<MediaEntity>> = mediaDao.getAllMedia()

    suspend fun saveMedia(
        path: String,
        type: MediaType,
        duration: Long? = null,
        timestamp: Long = System.currentTimeMillis(),
        thumbnailPath: String? = null
    ) {
        val entity = MediaEntity(
            path = path,
            type = type,
            timestamp = timestamp,
            duration = duration,
            thumbnailPath = thumbnailPath
        )
        mediaDao.insert(entity)
    }

    suspend fun deleteMultipleMedia(mediaList: List<MediaEntity>): DeleteResult {
        val (mockFiles, realFiles) = mediaList.partition { it.path.startsWith("mock_") }

        mockFiles.forEach { mediaDao.delete(it) }

        if (realFiles.isEmpty()) {
            return DeleteResult.Success(mockFiles.size)
        }

        val uris = realFiles.map { it.path }

        val result = mediaManager.deleteMultipleFiles(uris)

        if (result is DeleteResult.Success) {
            realFiles.forEach { mediaDao.delete(it) }
            return DeleteResult.Success(mockFiles.size + result.deletedCount)
        }

        return result
    }

    suspend fun clearAll() {
        mediaDao.deleteAll()
    }

    suspend fun isEmpty(): Boolean {
        return mediaDao.getCount() == 0
    }
}