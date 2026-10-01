package com.example.smartblind.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorDao {

    @Insert
    suspend fun insert(record: SensorRecord): Long

    /** Останні n записів у хронологічному порядку (старіші зліва); Flow оновлюється після кожної вставки. */
    @Query(
        "SELECT * FROM (SELECT * FROM sensor_records ORDER BY timestamp DESC, id DESC LIMIT :n) " +
            "ORDER BY timestamp ASC, id ASC"
    )
    fun getLastN(n: Int): Flow<List<SensorRecord>>

    /** Лишає в таблиці тільки keep найновіших записів. */
    @Query(
        "DELETE FROM sensor_records WHERE id NOT IN " +
            "(SELECT id FROM sensor_records ORDER BY timestamp DESC, id DESC LIMIT :keep)"
    )
    suspend fun deleteOld(keep: Int): Int
}
