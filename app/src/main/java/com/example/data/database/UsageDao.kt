package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {

    @Query("SELECT * FROM usage_history ORDER BY date DESC")
    fun getAllUsageHistory(): Flow<List<UsageHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageHistory(history: UsageHistoryEntity)

    @Query("SELECT * FROM battery_logs WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun getBatteryLogs(since: Long): Flow<List<BatteryLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatteryLog(log: BatteryLogEntity)

    @Query("DELETE FROM battery_logs WHERE timestamp < :before")
    suspend fun deleteOldBatteryLogs(before: Long)

    @Insert
    suspend fun insertBackgroundMediaLog(log: BackgroundMediaLogEntity): Long

    @Query("UPDATE background_media_logs SET endTime = :endTime WHERE id = :id")
    suspend fun closeBackgroundMediaLog(id: Long, endTime: Long)

    // Closes any session left dangling (endTime still null) from a previous process —
    // e.g. the app/service was killed mid-playback. Called once on service start so a
    // stale "still open" row from days ago never inflates totals going forward.
    @Query("UPDATE background_media_logs SET endTime = :now WHERE endTime IS NULL AND id NOT IN (:exceptIds)")
    suspend fun closeDanglingBackgroundMediaLogs(now: Long, exceptIds: List<Long> = emptyList())

    // Retention cleanup — mirrors deleteOldBatteryLogs. Without this, background_media_logs
    // grows forever since every play/pause transition adds a row.
    @Query("DELETE FROM background_media_logs WHERE startTime < :before")
    suspend fun deleteOldBackgroundMediaLogs(before: Long)

    // Total ms per package overlapping [start, end]: for each session, only the portion
    // that falls within the requested window counts (min(endTime,end) - max(startTime,start)).
    @Query(
        """
        SELECT packageName, SUM(
            MIN(COALESCE(endTime, :end), :end) - MAX(startTime, :start)
        ) as totalMs
        FROM background_media_logs
        WHERE startTime < :end AND COALESCE(endTime, :end) > :start
        GROUP BY packageName
        ORDER BY totalMs DESC
        """
    )
    suspend fun getBackgroundMediaTotals(start: Long, end: Long): List<BackgroundMediaTotal>
}

data class BackgroundMediaTotal(
    val packageName: String,
    val totalMs: Long
)
