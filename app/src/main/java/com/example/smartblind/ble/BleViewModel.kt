package com.example.smartblind.ble

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "BLE_MOCK"
private const val MAX_LOG_LINES = 200

class BleViewModel(
    private val scanner: MockBleScanner = MockBleScanner()
) : ViewModel() {

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val connector = MutableStateFlow<IBleConnector?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val isConnected: StateFlow<Boolean> = connector
        .flatMapLatest { it?.isConnected ?: flowOf(false) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var scanJob: Job? = null

    fun startScan() {
        if (_isScanning.value) return
        _isScanning.value = true
        _devices.value = emptyList()
        addLog("SYS", "Scan started")
        scanJob = viewModelScope.launch {
            try {
                val found = scanner.scan()
                _devices.value = found
                found.forEach { addLog("SYS", "Found ${it.name} (${it.address})") }
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun connect(device: BleDevice) {
        connector.value?.disconnect()
        val c = MockBleConnector(device, viewModelScope, ::addLog)
        connector.value = c
        c.connect()
    }

    fun disconnect() {
        connector.value?.disconnect()
    }

    fun send(command: BlindCommand) {
        connector.value?.sendData(command.name) ?: addLog("SYS", "No device selected")
    }

    private fun addLog(direction: String, message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "$time $direction $message"
        Log.d(TAG, line)
        _log.update { (it + line).takeLast(MAX_LOG_LINES) }
    }

    override fun onCleared() {
        connector.value?.disconnect()
        scanJob?.cancel()
    }
}
