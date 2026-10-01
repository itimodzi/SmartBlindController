package com.example.smartblind.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Рядок таблиці sensor_records: один вимір основної величини системи (кут нахилу, °). */
@Entity(tableName = "sensor_records", indices = [Index("timestamp")])
data class SensorRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,            // мс від епохи
    val sensorValue: Float,         // кут нахилу, градуси
    val actionTriggered: Boolean    // true, якщо в цей момент спрацював актуатор (ЛР №4)
)
