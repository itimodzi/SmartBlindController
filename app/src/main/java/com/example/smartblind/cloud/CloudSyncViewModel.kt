package com.example.smartblind.cloud

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartblind.R
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "CLOUD_SYNC"

const val SYNC_INTERVAL_MS = 5_000L          // періодична відправка
const val DELTA_THRESHOLD = 1.0f             // м/с²: зміна x/y/z, після якої шлемо одразу
const val LUX_DELTA_THRESHOLD = 50f          // лк: те саме для датчика світла
const val MIN_SEND_INTERVAL_MS = 1_000L      // не частіше ніж раз на секунду, навіть при Δ
const val MAX_PENDING = 200                  // ліміт черги непідтверджених записів

/** Один знімок показників датчиків. */
data class SensorSample(val x: Float, val y: Float, val z: Float, val lux: Float? = null)

enum class SyncStatus { IDLE, SYNCED, PENDING, ERROR }

data class CloudSyncState(
    val status: SyncStatus = SyncStatus.IDLE,
    val lastSyncMillis: Long? = null,
    val pendingCount: Int = 0
)

/** true, якщо показники змінилися відносно останніх відправлених на величину ≥ Δ. */
fun exceedsDelta(prev: SensorSample, cur: SensorSample): Boolean {
    val accel = maxOf(abs(cur.x - prev.x), abs(cur.y - prev.y), abs(cur.z - prev.z))
    if (accel >= DELTA_THRESHOLD) return true
    val p = prev.lux
    val c = cur.lux
    return p != null && c != null && abs(c - p) >= LUX_DELTA_THRESHOLD
}

class CloudSyncViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CloudRepository()
    private val network = NetworkMonitor(application)

    private val _state = MutableStateFlow(CloudSyncState())
    val state: StateFlow<CloudSyncState> = _state.asStateFlow()

    // Разові повідомлення для Snackbar.
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var latest: SensorSample? = null
    private var lastSent: SensorSample? = null
    private var lastEnqueueAt = 0L
    private var hasNewData = false
    private var offlineNotified = false

    // Записи, ще не підтверджені сервером: без інтернету вони не губляться.
    private val queue = ArrayDeque<SensorPayload>()
    private val mutex = Mutex()

    init {
        // Періодична відправка: кожні 5 с (мережа виконується в Dispatchers.IO всередині репозиторію).
        viewModelScope.launch {
            while (true) {
                delay(SYNC_INTERVAL_MS)
                trySync()
            }
        }
    }

    /** Викликається з Activity при кожному новому вимірі; швидко повертається. */
    fun onSensorData(sample: SensorSample) {
        latest = sample
        hasNewData = true
        val prev = lastSent
        val sinceLast = SystemClock.elapsedRealtime() - lastEnqueueAt
        if (prev == null || (exceedsDelta(prev, sample) && sinceLast >= MIN_SEND_INTERVAL_MS)) {
            viewModelScope.launch { trySync() }
        }
    }

    private suspend fun trySync() = mutex.withLock {
        val sample = latest
        if (hasNewData && sample != null) enqueue(sample)
        if (queue.isEmpty()) return@withLock

        if (!network.isOnline()) {
            Log.w(TAG, "SKIP no internet, pending=${queue.size}")
            _state.update { it.copy(status = SyncStatus.PENDING, pendingCount = queue.size) }
            if (!offlineNotified) {
                offlineNotified = true
                _messages.tryEmit(getApplication<Application>().getString(R.string.cloud_no_internet))
            }
            return@withLock
        }
        offlineNotified = false

        while (queue.isNotEmpty()) {
            val result = repository.sendPayload(queue.first())
            if (result.isSuccess) {
                queue.removeFirst()
                _state.value = CloudSyncState(SyncStatus.SYNCED, System.currentTimeMillis(), queue.size)
            } else {
                _state.update { it.copy(status = SyncStatus.ERROR, pendingCount = queue.size) }
                return@withLock
            }
        }
    }

    private fun enqueue(sample: SensorSample) {
        if (queue.size >= MAX_PENDING) {
            Log.w(TAG, "QUEUE full, dropping oldest payload")
            queue.removeFirst()
        }
        queue.addLast(
            SensorPayload(
                sensorType = "accelerometer",
                x = sample.x, y = sample.y, z = sample.z,
                lux = sample.lux
            )
        )
        lastSent = sample
        lastEnqueueAt = SystemClock.elapsedRealtime()
        hasNewData = false
    }
}
