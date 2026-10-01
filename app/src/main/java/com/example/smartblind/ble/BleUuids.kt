package com.example.smartblind.ble

import java.util.UUID

/** UUID GATT-профілю Smart Blind Hub (власні 128-бітні UUID, не зі списку Bluetooth SIG). */
object BleUuids {
    /** Сервіс керування жалюзі: об'єднує дві характеристики нижче. */
    val SERVICE: UUID = UUID.fromString("0000b110-0000-1000-8000-00805f9b34fb")

    /**
     * Характеристика статусу (READ + NOTIFY): пристрій повідомляє поточне положення жалюзі
     * рядком "OPEN" / "HALF" / "CLOSE".
     */
    val STATUS_CHARACTERISTIC: UUID = UUID.fromString("0000b111-0000-1000-8000-00805f9b34fb")

    /**
     * Характеристика команд (WRITE): застосунок записує сюди команду
     * "OPEN" / "HALF" / "CLOSE", щоб змінити положення жалюзі.
     */
    val COMMAND_CHARACTERISTIC: UUID = UUID.fromString("0000b112-0000-1000-8000-00805f9b34fb")
}

/** Команди, які приймає характеристика команд. */
enum class BlindCommand { OPEN, HALF, CLOSE }
