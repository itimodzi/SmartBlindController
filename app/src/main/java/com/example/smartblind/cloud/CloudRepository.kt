package com.example.smartblind.cloud

import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "CLOUD_SYNC"
private const val NODE_SENSOR_DATA = "sensor_data"

// Realtime Database без зв'язку не завершує Task, тож чекаємо не довше цього часу.
const val SEND_TIMEOUT_MS = 10_000L

/** Мережева логіка: запис показників датчиків у Firebase Realtime Database. */
class CloudRepository {

    // Лінива ініціалізація: Firebase запитується лише при першій відправці.
    private val root: DatabaseReference by lazy { FirebaseDatabase.getInstance().reference }

    /** Додає новий запис у sensor_data (push) і чекає підтвердження сервера. */
    suspend fun sendPayload(payload: SensorPayload): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            withTimeout(SEND_TIMEOUT_MS) {
                root.child(NODE_SENSOR_DATA).push().setValue(payload).await()
            }
            Log.d(TAG, "SEND payload=$payload -> SUCCESS")
            Result.success(Unit)
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "SEND payload=$payload -> FAIL timeout ${SEND_TIMEOUT_MS}ms")
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "SEND payload=$payload -> FAIL ${e.message}", e)
            Result.failure(e)
        }
    }
}
