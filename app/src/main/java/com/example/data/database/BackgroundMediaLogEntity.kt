package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single continuous "this app was actively playing media" session, detected via the
 * system's MediaSession API (requires Notification Access permission). endTime is null
 * while the session is still ongoing; the listener service fills it in when playback
 * pauses/stops.
 *
 * This can only ever contain data from the point the person granted Notification Access
 * onward — Android doesn't retroactively expose past media session history, so there's
 * no way to backfill "yesterday's background playback".
 */
@Entity(tableName = "background_media_logs")
data class BackgroundMediaLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startTime: Long,
    val endTime: Long?
)
