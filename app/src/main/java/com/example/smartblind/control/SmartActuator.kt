package com.example.smartblind.control

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "ACTUATOR"

// Тривалість короткої вібрації-тривоги, мс.
const val ALARM_VIBRATION_MS = 500L

// Патерн безперервної вібрації для ручного керування: пауза, вібрація, пауза (повторюється).
private val CONTINUOUS_PATTERN = longArrayOf(0, 400, 300)

/** Виконавчі пристрої, якими керує DecisionEngine. Інтерфейс дозволяє підставити заглушку в тестах. */
interface Actuator {
    fun triggerAlarm(reason: String = "unspecified")
    fun toggleFlashlight(status: Boolean, reason: String = "unspecified")
}

/** Вібрація (VibratorManager, API 31+) та ліхтарик (CameraManager.setTorchMode). */
class SmartActuator(context: Context) : Actuator {

    private val appContext = context.applicationContext
    private val hasFlash =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    private val cameraManager = appContext.getSystemService(CameraManager::class.java)
    private val vibrator: Vibrator? =
        appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    private val handler = Handler(Looper.getMainLooper())

    private val _torchOn = MutableStateFlow(false)
    val torchOn: StateFlow<Boolean> = _torchOn.asStateFlow()

    private val _vibrationActive = MutableStateFlow(false)
    val vibrationActive: StateFlow<Boolean> = _vibrationActive.asStateFlow()

    private var continuousVibration = false
    private val clearAlarmIndicator = Runnable {
        if (!continuousVibration) _vibrationActive.value = false
    }

    // Камера зі спалахом: шукаємо один раз, бажано задню.
    private val torchCameraId: String? by lazy { findFlashCameraId() }

    // Відстежуємо реальний стан ліхтарика: його можуть вимкнути система чи інший застосунок.
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) _torchOn.value = enabled
        }
    }

    init {
        if (hasFlash) {
            try {
                cameraManager.registerTorchCallback(appContext.mainExecutor, torchCallback)
            } catch (e: RuntimeException) {
                Log.e(TAG, "INIT torch callback registration failed: ${e.message}", e)
            }
        } else {
            Log.w(TAG, "INIT no camera flash on this device, torch actions will be skipped")
        }
    }

    /** Коротка вібрація (тривога). */
    override fun triggerAlarm(reason: String) {
        val v = vibrator
        if (v == null || !v.hasVibrator()) {
            Log.w(TAG, "ALARM skipped: no vibrator, reason=$reason")
            return
        }
        if (continuousVibration) {
            Log.d(TAG, "ALARM skipped: manual vibration already active, reason=$reason")
            return
        }
        try {
            v.vibrate(
                VibrationEffect.createOneShot(ALARM_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            )
            _vibrationActive.value = true
            handler.removeCallbacks(clearAlarmIndicator)
            handler.postDelayed(clearAlarmIndicator, ALARM_VIBRATION_MS)
            Log.i(TAG, "ALARM vibrate ${ALARM_VIBRATION_MS}ms, reason=$reason")
        } catch (e: SecurityException) {
            Log.e(TAG, "ALARM vibrate denied: ${e.message}, reason=$reason", e)
        }
    }

    /** Ручне керування: безперервна вібрація до вимкнення. */
    fun setVibration(on: Boolean, reason: String = "unspecified") {
        val v = vibrator
        if (v == null || !v.hasVibrator()) {
            Log.w(TAG, "VIBRATION ${if (on) "ON" else "OFF"} skipped: no vibrator, reason=$reason")
            return
        }
        try {
            handler.removeCallbacks(clearAlarmIndicator)
            if (on) {
                v.vibrate(VibrationEffect.createWaveform(CONTINUOUS_PATTERN, 0))
            } else {
                v.cancel()
            }
            continuousVibration = on
            _vibrationActive.value = on
            Log.i(TAG, "VIBRATION ${if (on) "ON" else "OFF"}, reason=$reason")
        } catch (e: SecurityException) {
            Log.e(TAG, "VIBRATION denied: ${e.message}, reason=$reason", e)
        }
    }

    override fun toggleFlashlight(status: Boolean, reason: String) {
        if (!hasFlash) {
            Log.w(TAG, "TORCH ${if (status) "ON" else "OFF"} skipped: no camera flash, reason=$reason")
            return
        }
        val id = torchCameraId
        if (id == null) {
            Log.w(TAG, "TORCH skipped: flash camera not found, reason=$reason")
            return
        }
        try {
            cameraManager.setTorchMode(id, status)
            _torchOn.value = status
            Log.i(TAG, "TORCH ${if (status) "ON" else "OFF"}, reason=$reason")
        } catch (e: CameraAccessException) {
            Log.e(TAG, "TORCH failed: ${e.message}, reason=$reason", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "TORCH invalid camera id $id: ${e.message}, reason=$reason", e)
        }
    }

    /** Вимикає все й відписується від камери (викликається при знищенні ViewModel). */
    fun release() {
        handler.removeCallbacks(clearAlarmIndicator)
        if (continuousVibration) setVibration(false, "release")
        if (_torchOn.value) toggleFlashlight(false, "release")
        if (hasFlash) {
            try {
                cameraManager.unregisterTorchCallback(torchCallback)
            } catch (e: RuntimeException) {
                Log.w(TAG, "RELEASE unregister failed: ${e.message}")
            }
        }
    }

    private fun findFlashCameraId(): String? = try {
        val withFlash = cameraManager.cameraIdList.filter {
            cameraManager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        withFlash.firstOrNull {
            cameraManager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: withFlash.firstOrNull()
    } catch (e: CameraAccessException) {
        Log.e(TAG, "INIT camera list failed: ${e.message}", e)
        null
    }
}
