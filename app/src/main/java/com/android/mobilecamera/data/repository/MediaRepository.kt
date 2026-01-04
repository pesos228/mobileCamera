package com.android.mobilecamera.data.repository

import com.android.mobilecamera.data.database.MediaDao
import com.android.mobilecamera.data.database.MediaEntity
import com.android.mobilecamera.data.database.MediaType
import kotlinx.coroutines.flow.Flow

class MediaRepository(private val mediaDao: MediaDao) {

    val allMedia: Flow<List<MediaEntity>> = mediaDao.getAllMedia()

    suspend fun saveMedia(
        path: String,
        type: MediaType,
        duration: Long? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val entity = MediaEntity(
            path = path,
            type = type,
            timestamp = timestamp,
            duration = duration
        )
        mediaDao.insert(entity)
    }

    suspend fun deleteMedia(media: MediaEntity) {
        mediaDao.delete(media)
    }

    suspend fun clearAll() {
        mediaDao.deleteAll()
    }
}