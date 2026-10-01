package com.example.smartblind.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

const val MOCK_CONNECT_DELAY_MS = 800L
const val MOCK_RESPONSE_DELAY_MS = 300L

/**
 * Імітація з'єднання: без реального Bluetooth. Кожну прийняту команду «пристрій»
 * підтверджує повідомленням зі статусом (як notify характеристики статусу).
 *
 * @param log приймає напрям ("TX"/"RX"/"SYS") і текст повідомлення.
 */
class MockBleConnector(
    private val device: BleDevice,
    private val scope: CoroutineScope,
    private val log: (direction: String, message: String) -> Unit
) : IBleConnector {

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected

    override fun connect() {
        if (_isConnected.value) return
        log("SYS", "Connecting to ${device.name} (${device.address})...")
        scope.launch {
            delay(MOCK_CONNECT_DELAY_MS)
            _isConnected.value = true
            log("SYS", "Connected, service ${BleUuids.SERVICE}")
        }
    }

    override fun disconnect() {
        if (!_isConnected.value) return
        _isConnected.value = false
        log("SYS", "Disconnected")
    }

    override fun sendData(data: String) {
        if (!_isConnected.value) {
            log("SYS", "Not connected, \"$data\" dropped")
            return
        }
        log("TX", "-> $data")
        scope.launch {
            delay(MOCK_RESPONSE_DELAY_MS)
            if (_isConnected.value) log("RX", "<- STATUS:$data")
        }
    }
}
