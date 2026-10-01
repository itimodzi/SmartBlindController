package com.example.smartblind.control

import com.example.smartblind.BlindStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecisionEngineTest {

    private class FakeActuator : Actuator {
        val calls = mutableListOf<String>()
        override fun triggerAlarm(reason: String) { calls += "alarm" }
        override fun toggleFlashlight(status: Boolean, reason: String) { calls += "torch=$status" }
    }

    private val actuator = FakeActuator()
    private val engine = DecisionEngine(actuator) {}

    @Test
    fun closed_triggersAlarmAndTorchOn() {
        engine.onSensors(80f, null)
        assertEquals(listOf("alarm", "torch=true"), actuator.calls)
    }

    @Test
    fun sameState_doesNotRepeat() {
        engine.onSensors(80f, null)
        engine.onSensors(85f, null)
        engine.onSensors(70f, null)
        assertEquals(listOf("alarm", "torch=true"), actuator.calls)
    }

    @Test
    fun leavingClosed_turnsTorchOff() {
        engine.onSensors(80f, null)
        engine.onSensors(45f, null) // HALF
        assertEquals(listOf("alarm", "torch=true", "torch=false"), actuator.calls)
        assertEquals(BlindStatus.HALF, engine.current)
    }

    @Test
    fun openToHalf_doesNothingWithActuators() {
        engine.onSensors(10f, null)
        engine.onSensors(45f, null)
        assertEquals(emptyList<String>(), actuator.calls)
    }

    @Test
    fun brightLight_forcesClosed() {
        engine.onSensors(10f, LUX_BRIGHT_THRESHOLD)
        assertEquals(BlindStatus.CLOSED, engine.current)
        assertEquals(listOf("alarm", "torch=true"), actuator.calls)
    }

    @Test
    fun remoteCommand_behavesLikeLocal() {
        val change = engine.applyCommand(BlindStatus.CLOSED, CommandSource.REMOTE)
        assertEquals(CommandSource.REMOTE, change?.source)
        assertEquals(listOf("alarm", "torch=true"), actuator.calls)
        assertNull(engine.applyCommand(BlindStatus.CLOSED, CommandSource.REMOTE))
    }

    @Test
    fun manualCommand_isNotOverwrittenBySameSensorState() {
        engine.onSensors(10f, null) // OPEN, baseline
        engine.applyCommand(BlindStatus.CLOSED, CommandSource.MANUAL)
        assertNull(engine.onSensors(11f, null)) // датчик досі OPEN: не змінився
        assertEquals(BlindStatus.CLOSED, engine.current)
    }
}
