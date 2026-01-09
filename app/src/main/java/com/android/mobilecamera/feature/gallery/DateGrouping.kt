package com.android.mobilecamera.feature.gallery

import android.content.Context
import com.android.mobilecamera.R
import com.android.mobilecamera.data.database.MediaEntity
import java.text.SimpleDateFormat
import java.util.*

data class MediaGroup(
    val date: String,
    val items: List<MediaEntity>
)

fun List<MediaEntity>.groupByDate(context: Context): List<MediaGroup> {
    val calendar = Calendar.getInstance()
    val today = calendar.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val yesterday = calendar.apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }.timeInMillis

    return this
        .groupBy { media ->
            val mediaCalendar = Calendar.getInstance().apply {
                timeInMillis = media.timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            mediaCalendar.timeInMillis
        }
        .map { (timestamp, items) ->
            val dateLabel = when {
                timestamp >= today -> context.getString(R.string.date_today)
                timestamp >= yesterday -> context.getString(R.string.date_yesterday)
                else -> {
                    val pattern = context.getString(R.string.date_format_pattern)
                    val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
                    dateFormat.format(Date(timestamp))
                }
            }
            MediaGroup(dateLabel, items)
        }
        .sortedByDescending { it.items.firstOrNull()?.timestamp ?: 0 }
}