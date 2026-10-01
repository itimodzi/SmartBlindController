package com.example.smartblind.ble

import kotlinx.coroutines.flow.StateFlow

/** Знайдений BLE-пристрій (у ЛР №2 — віртуальний). */
data class BleDevice(val name: String, val address: String)

/** Абстракція BLE-з'єднання: реальну реалізацію (BluetoothGatt) можна підставити замість Mock. */
interface IBleConnector {
    /** true, поки з'єднання з пристроєм встановлене. */
    val isConnected: StateFlow<Boolean>

    fun connect()
    fun disconnect()

    /** Записує рядок у характеристику команд. Ігнорується, якщо з'єднання немає. */
    fun sendData(data: String)
}
