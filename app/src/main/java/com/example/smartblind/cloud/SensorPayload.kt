package com.example.smartblind.cloud

/**
 * Запис у Firebase Realtime Database (вузол sensor_data).
 * Значення за замовчуванням потрібні Firebase: йому потрібен конструктор без аргументів.
 * lux заповнюється, лише якщо на пристрої є датчик світла.
 */
data class SensorPayload(
    val sensorType: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val lux: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)
