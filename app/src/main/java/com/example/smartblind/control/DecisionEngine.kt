package com.example.smartblind.control

import android.util.Log
import com.example.smartblind.BlindStatus
import com.example.smartblind.blindStatusFor

private const val TAG = "ACTUATOR"

// Освітленість (лк), починаючи з якої світло вважається яскравим: жалюзі переходять у CLOSED
// незалежно від кута. Кутові пороги (TILT_HALF_THRESHOLD, TILT_CLOSED_THRESHOLD) — у MainActivity.kt.
const val LUX_BRIGHT_THRESHOLD = 5_000f

/** Звідки прийшла команда. */
enum class CommandSource(val label: String) {
    LOCAL("Local"),    // спрацювали пороги датчиків
    REMOTE("Remote"),  // Firebase, вузол control/blind_command
    MANUAL("Manual")   // кнопки на екрані
}

/** Перехід віртуальних жалюзі з одного стану в інший. */
data class StatusChange(val from: BlindStatus, val to: BlindStatus, val source: CommandSource)

/**
 * Приймає рішення за станом жалюзі та керує актуаторами.
 * Реагує лише на ПЕРЕХІД між станами: повторний виклик з тим самим станом нічого не робить.
 *  - перехід у CLOSED: короткий сигнал вібрації + ліхтарик увімкнено (індикатор активного стану);
 *  - вихід із CLOSED (у OPEN або HALF): ліхтарик вимкнено.
 * Усі виклики мають бути з одного потоку (головного).
 */
class DecisionEngine(
    private val actuator: Actuator,
    private val log: (String) -> Unit = { Log.d(TAG, it) }
) {
    /** Поточний стан віртуальних жалюзі. */
    var current: BlindStatus = BlindStatus.OPEN
        private set

    // Останній стан, обчислений за датчиками: локальна логіка реагує лише на його зміну,
    // тож віддалена чи ручна команда не перезатирається наступним виміром датчика.
    private var lastSensorStatus: BlindStatus? = null

    /** Стан за датчиками: яскраве світло або великий кут нахилу дають CLOSED. */
    fun sensorStatus(tiltAngle: Float, lux: Float?): BlindStatus =
        if (lux != null && lux >= LUX_BRIGHT_THRESHOLD) BlindStatus.CLOSED
        else blindStatusFor(tiltAngle)

    /** Передає в engine нові показники датчиків; повертає перехід, якщо він відбувся. */
    fun onSensors(tiltAngle: Float, lux: Float?): StatusChange? {
        val status = sensorStatus(tiltAngle, lux)
        if (status == lastSensorStatus) return null
        lastSensorStatus = status
        val reason = "threshold: tilt=%.1f lux=%s".format(tiltAngle, lux?.let { "%.0f".format(it) } ?: "n/a")
        return transitionTo(status, CommandSource.LOCAL, reason)
    }

    /** Команда ззовні (Firebase або кнопки): та сама логіка актуаторів, що й для датчиків. */
    fun applyCommand(status: BlindStatus, source: CommandSource): StatusChange? {
        val reason = if (source == CommandSource.REMOTE) "remote command" else "manual control"
        return transitionTo(status, source, reason)
    }

    private fun transitionTo(to: BlindStatus, source: CommandSource, reason: String): StatusChange? {
        if (to == current) return null
        val from = current
        current = to
        log("DECISION $from -> $to source=${source.label} reason=$reason")
        if (to == BlindStatus.CLOSED) {
            actuator.triggerAlarm(reason)
            actuator.toggleFlashlight(true, reason)
        } else if (from == BlindStatus.CLOSED) {
            actuator.toggleFlashlight(false, reason)
        }
        return StatusChange(from, to, source)
    }
}
