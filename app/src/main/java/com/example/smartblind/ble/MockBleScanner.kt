package com.example.smartblind.ble

import kotlinx.coroutines.delay

const val MOCK_SCAN_DURATION_MS = 2000L

/** Імітація сканування: через 2 секунди «знаходить» віртуальний Smart Blind Hub. */
class MockBleScanner {
    suspend fun scan(): List<BleDevice> {
        delay(MOCK_SCAN_DURATION_MS)
        return listOf(BleDevice(name = "Smart Blind Hub", address = "AA:BB:CC:DD:EE:01"))
    }
}
