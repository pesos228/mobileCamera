package com.android.mobilecamera.data.repository

import com.android.mobilecamera.data.database.MediaDao
import com.android.mobilecamera.data.database.MediaEntity
import com.android.mobilecamera.data.database.MediaType
import kotlinx.coroutines.flow.Flow

class MediaRepository(private val mediaDao: MediaDao) {

    val allMedia: Flow<List<MediaEntity>> = mediaDao.getAllMedia()

    suspend fun saveMedia(path: String, type: MediaType) {
        val entity = MediaEntity(
            path = path,
            type = type,
            timestamp = System.currentTimeMillis()
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