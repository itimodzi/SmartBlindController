package com.example.smartblind.cloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExceedsDeltaTest {

    private val base = SensorSample(0f, 0f, 9.8f)

    @Test
    fun smallChange_isIgnored() {
        assertFalse(exceedsDelta(base, SensorSample(0.3f, -0.2f, 9.9f)))
    }

    @Test
    fun accelChangeAtThreshold_triggers() {
        assertTrue(exceedsDelta(base, SensorSample(DELTA_THRESHOLD, 0f, 9.8f)))
    }

    @Test
    fun luxChange_triggersOnlyWhenBothPresent() {
        val dark = base.copy(lux = 10f)
        assertTrue(exceedsDelta(dark, base.copy(lux = 10f + LUX_DELTA_THRESHOLD)))
        assertFalse(exceedsDelta(base, base.copy(lux = 500f)))
    }
}
