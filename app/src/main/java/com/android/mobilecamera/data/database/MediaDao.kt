package com.android.mobilecamera.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query("SELECT * FROM media_table ORDER BY timestamp DESC")
    fun getAllMedia(): Flow<List<MediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MediaEntity)

    @Delete
    suspend fun delete(media: MediaEntity)

    @Query("DELETE FROM media_table")
    suspend fun deleteAll()

    @Query("SELECT * FROM media_table WHERE id = :mediaId")
    suspend fun getMediaById(mediaId: Int): MediaEntity?

    @Query("SELECT COUNT(*) FROM media_table")
    suspend fun getCount(): Int
}