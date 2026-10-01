package com.example.smartblind.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

private const val TAG = "DB"

const val MAX_STORED_RECORDS = 1_000   // більше в таблиці не тримаємо
private const val TRIM_EVERY = 100     // як часто чистимо старі записи (кожні N вставок)

/** Обгортка над DAO: усі операції запису виконуються поза UI-потоком. */
class SensorRepository(private val dao: SensorDao) {

    private var insertsSinceTrim = 0

    /** Останні n записів; Flow оновлюється автоматично при зміні таблиці. */
    fun lastRecords(n: Int): Flow<List<SensorRecord>> = dao.getLastN(n)

    suspend fun add(record: SensorRecord) = withContext(Dispatchers.IO) {
        val id = dao.insert(record)
        Log.d(
            TAG,
            "INSERT id=$id timestamp=${record.timestamp} value=%.1f action=${record.actionTriggered}"
                .format(record.sensorValue)
        )
        if (++insertsSinceTrim >= TRIM_EVERY) {
            insertsSinceTrim = 0
            val removed = dao.deleteOld(MAX_STORED_RECORDS)
            if (removed > 0) Log.d(TAG, "TRIM removed $removed old records")
        }
    }
}
