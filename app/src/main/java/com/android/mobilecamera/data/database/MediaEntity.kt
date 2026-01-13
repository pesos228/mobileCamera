package com.android.mobilecamera.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_table")
data class MediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val path: String,
    val type: MediaType,
    val timestamp: Long,
    val duration: Long? = null,
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null
)