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
}
