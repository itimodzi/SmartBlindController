package com.example.smartblind.analytics

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartblind.data.AppDatabase
import com.example.smartblind.data.SensorRecord
import com.example.smartblind.data.SensorRepository
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val CHART_POINTS = 50                 // скільки останніх точок показуємо на графіку
const val RECORD_INTERVAL_MS = 2_000L       // запис за таймером
const val RECORD_DELTA = 5f                 // °: зміна кута, після якої пишемо одразу
const val MIN_RECORD_GAP_MS = 250L          // не частіше, ніж раз на 250 мс, навіть при Δ

/** Посередник між сенсором і БД: вирішує, коли робити запис, і віддає в UI останні записи. */
class SensorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SensorRepository(AppDatabase.getInstance(application).sensorDao())

    /** Останні CHART_POINTS записів для графіка (оновлюється при кожному новому записі). */
    val records: StateFlow<List<SensorRecord>> = repository.lastRecords(CHART_POINTS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var latest: Float? = null
    private var lastRecorded: Float? = null
    private var lastRecordAt = 0L
    private var hasNewData = false

    init {
        // Запис за таймером: кожні 2 с, якщо з'явилися нові вимірювання.
        viewModelScope.launch {
            while (true) {
                delay(RECORD_INTERVAL_MS)
                val value = latest
                if (hasNewData && value != null) record(value, actionTriggered = false)
            }
        }
    }

    /** Новий вимір (кут нахилу, °): пишемо одразу, якщо він суттєво відрізняється від попереднього запису. */
    fun onSensorValue(value: Float) {
        latest = value
        hasNewData = true
        val last = lastRecorded
        val sinceLast = SystemClock.elapsedRealtime() - lastRecordAt
        if (last == null || (abs(value - last) >= RECORD_DELTA && sinceLast >= MIN_RECORD_GAP_MS)) {
            record(value, actionTriggered = false)
        }
    }

    /** Спрацював актуатор (ЛР №4): фіксуємо поточне значення з прапорцем actionTriggered = true. */
    fun onActuatorTriggered() {
        latest?.let { record(it, actionTriggered = true) }
    }

    private fun record(value: Float, actionTriggered: Boolean) {
        lastRecorded = value
        lastRecordAt = SystemClock.elapsedRealtime()
        hasNewData = false
        val record = SensorRecord(
            timestamp = System.currentTimeMillis(),
            sensorValue = value,
            actionTriggered = actionTriggered
        )
        viewModelScope.launch { repository.add(record) }
    }
}
