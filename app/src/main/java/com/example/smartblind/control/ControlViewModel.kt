package com.example.smartblind.control

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartblind.BlindStatus
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private const val TAG = "ACTUATOR"
private const val REMOTE_PATH = "control/blind_command"

/** Остання виконана команда: що саме й звідки. */
data class LastCommand(val label: String, val source: CommandSource)

data class ControlUiState(
    val blindStatus: BlindStatus = BlindStatus.OPEN,
    val torchOn: Boolean = false,
    val vibrationActive: Boolean = false,
    val lastCommand: LastCommand? = null
)

class ControlViewModel(application: Application) : AndroidViewModel(application) {

    private val actuator = SmartActuator(application)
    private val engine = DecisionEngine(actuator)

    private val blind = MutableStateFlow(BlindStatus.OPEN)
    private val lastCommand = MutableStateFlow<LastCommand?>(null)

    val uiState: StateFlow<ControlUiState> = combine(
        blind, lastCommand, actuator.torchOn, actuator.vibrationActive
    ) { b, last, torch, vibration -> ControlUiState(b, torch, vibration, last) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ControlUiState())

    // Подія «спрацював актуатор» (перехід стану або ручне керування): її записує Аналітика (ЛР №5).
    private val _actuatorEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val actuatorEvents: SharedFlow<Unit> = _actuatorEvents.asSharedFlow()

    // --- Віддалене керування (Firebase) ---
    private var remoteRef: DatabaseReference? = null
    private var remoteListener: ValueEventListener? = null
    private var remoteBaselineSeen = false
    private var lastRemoteValue: String? = null

    /** Нові показники датчиків (викликається при кожному виміру). */
    fun onSensors(tiltAngle: Float, lux: Float?) {
        engine.onSensors(tiltAngle, lux)?.let(::publish)
    }

    fun manualBlind(status: BlindStatus) {
        engine.applyCommand(status, CommandSource.MANUAL)?.let(::publish)
    }

    fun manualVibration(on: Boolean) {
        actuator.setVibration(on, "manual control")
        lastCommand.value = LastCommand("Vibration ${if (on) "ON" else "OFF"}", CommandSource.MANUAL)
        _actuatorEvents.tryEmit(Unit)
    }

    fun manualTorch(on: Boolean) {
        actuator.toggleFlashlight(on, "manual control")
        lastCommand.value = LastCommand("Torch ${if (on) "ON" else "OFF"}", CommandSource.MANUAL)
        _actuatorEvents.tryEmit(Unit)
    }

    private fun publish(change: StatusChange) {
        blind.value = change.to
        lastCommand.value = LastCommand(change.to.name, change.source)
        _actuatorEvents.tryEmit(Unit)
    }

    /** Підключає слухач control/blind_command (викликається з onStart). */
    fun startRemoteListening() {
        if (remoteListener != null) return
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = onRemoteValue(snapshot)
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "REMOTE listener cancelled: ${error.message}")
            }
        }
        try {
            val ref = FirebaseDatabase.getInstance().getReference(REMOTE_PATH)
            ref.addValueEventListener(listener)
            remoteRef = ref
            remoteListener = listener
            Log.i(TAG, "REMOTE listener attached to $REMOTE_PATH")
        } catch (e: Exception) {
            Log.e(TAG, "REMOTE listener not attached: ${e.message}", e)
        }
    }

    /** Відключає слухач (викликається з onStop), щоб не було витоків. */
    fun stopRemoteListening() {
        val ref = remoteRef
        val listener = remoteListener
        if (ref != null && listener != null) {
            ref.removeEventListener(listener)
            Log.i(TAG, "REMOTE listener removed")
        }
        remoteRef = null
        remoteListener = null
    }

    private fun onRemoteValue(snapshot: DataSnapshot) {
        val raw = snapshot.getValue(String::class.java)?.trim()?.uppercase()
        // Перше значення — лише відправна точка: старе значення в базі не має спрацьовувати як нова команда.
        // Після повторного підключення (onStart) виконуємо команду, лише якщо значення змінилося, поки нас не було.
        if (!remoteBaselineSeen) {
            remoteBaselineSeen = true
            lastRemoteValue = raw
            Log.i(TAG, "REMOTE baseline value=$raw (not applied)")
            return
        }
        if (raw == lastRemoteValue) return
        lastRemoteValue = raw

        val status = when (raw) {
            "OPEN" -> BlindStatus.OPEN
            "HALF" -> BlindStatus.HALF
            "CLOSE", "CLOSED" -> BlindStatus.CLOSED
            else -> {
                Log.w(TAG, "REMOTE unknown command value=$raw")
                return
            }
        }
        Log.i(TAG, "REMOTE command=$raw")
        engine.applyCommand(status, CommandSource.REMOTE)?.let(::publish)
    }

    override fun onCleared() {
        stopRemoteListening()
        actuator.release()
    }
}
