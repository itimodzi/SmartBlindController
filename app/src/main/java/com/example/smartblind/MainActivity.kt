package com.example.smartblind

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.smartblind.ble.BleViewModel
import com.example.smartblind.ble.BluetoothScreen
import com.example.smartblind.cloud.CloudSyncIndicator
import com.example.smartblind.cloud.CloudSyncState
import com.example.smartblind.cloud.CloudSyncViewModel
import com.example.smartblind.cloud.SensorSample
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartblind.ui.theme.SmartBlindTheme
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.sqrt

// Пороги кута нахилу (у градусах), за якими визначається стан жалюзі.
const val TILT_HALF_THRESHOLD = 30f    // нижче — "Відкрито"
const val TILT_CLOSED_THRESHOLD = 60f  // вище — "Закрито", між порогами — "Наполовину"

// Максимальний кут для масштабування ProgressBar (0..90°).
const val TILT_MAX_ANGLE = 90f

/** Стан жалюзі, що залежить від кута нахилу пристрою. */
enum class BlindStatus { OPEN, HALF, CLOSED }

/** Перетворює кут нахилу в статус жалюзі за порогами. */
fun blindStatusFor(angle: Float): BlindStatus = when {
    angle < TILT_HALF_THRESHOLD -> BlindStatus.OPEN
    angle <= TILT_CLOSED_THRESHOLD -> BlindStatus.HALF
    else -> BlindStatus.CLOSED
}

/**
 * Кут нахилу — це кут між нормаллю екрана (вісь Z) та вертикаллю:
 * 0° — телефон лежить екраном угору, 90° — стоїть вертикально.
 * Береться |z|, тому положення екраном донизу дає той самий кут (діапазон 0..90°).
 */
fun tiltAngleDegrees(x: Float, y: Float, z: Float): Float {
    val g = sqrt(x * x + y * y + z * z)
    if (g == 0f) return 0f
    val cos = (abs(z) / g).coerceIn(0f, 1f)
    return Math.toDegrees(acos(cos).toDouble()).toFloat()
}

class MainActivity : ComponentActivity(), SensorEventListener {

    private val bleViewModel: BleViewModel by viewModels()
    private val cloudViewModel: CloudSyncViewModel by viewModels()

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lightSensor: Sensor? = null

    // Стан UI: Compose автоматично перемальовує екран при зміні цих значень.
    private var x by mutableFloatStateOf(0f)
    private var y by mutableFloatStateOf(0f)
    private var z by mutableFloatStateOf(0f)
    private var tiltAngle by mutableFloatStateOf(0f)
    private var hasAccelerometer by mutableStateOf(true)
    private var lux by mutableStateOf<Float?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        // Якщо акселерометра немає, getDefaultSensor повертає null — покажемо повідомлення.
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        hasAccelerometer = accelerometer != null
        // Датчик світла необов'язковий: якщо його немає, lux лишається null.
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        setContent {
            SmartBlindTheme {
                // Проста навігація між екраном ЛР №1 та екраном Bluetooth.
                var showBluetooth by rememberSaveable { mutableStateOf(false) }
                val cloudState by cloudViewModel.state.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    cloudViewModel.messages.collect { snackbarHostState.showSnackbar(it) }
                }
                BackHandler(enabled = showBluetooth) { showBluetooth = false }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    if (showBluetooth) {
                        BluetoothScreen(
                            viewModel = bleViewModel,
                            onBack = { showBluetooth = false },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        BlindControllerScreen(
                            hasAccelerometer = hasAccelerometer,
                            x = x,
                            y = y,
                            z = z,
                            tiltAngle = tiltAngle,
                            lux = lux,
                            cloudState = cloudState,
                            onOpenBluetooth = { showBluetooth = true },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    // Підписуємось на датчик, коли екран стає активним.
    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    // Відписуємось, коли екран ховається, щоб не витрачати батарею.
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // Викликається при кожному новому вимірі акселерометра.
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                x = event.values[0]
                y = event.values[1]
                z = event.values[2]
                tiltAngle = tiltAngleDegrees(x, y, z)
            }
            Sensor.TYPE_LIGHT -> lux = event.values[0]
            else -> return
        }
        // Хмарна синхронізація: ViewModel сама вирішує, коли відправляти (5 с або Δ).
        if (hasAccelerometer) cloudViewModel.onSensorData(SensorSample(x, y, z, lux))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

@Composable
fun BlindControllerScreen(
    hasAccelerometer: Boolean,
    x: Float,
    y: Float,
    z: Float,
    tiltAngle: Float,
    modifier: Modifier = Modifier,
    lux: Float? = null,
    cloudState: CloudSyncState? = null,
    onOpenBluetooth: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(32.dp))

        if (onOpenBluetooth != null) {
            Button(onClick = onOpenBluetooth) { Text(stringResource(R.string.open_bluetooth)) }
            Spacer(Modifier.height(24.dp))
        }

        if (cloudState != null) {
            CloudSyncIndicator(cloudState)
            Spacer(Modifier.height(24.dp))
        }

        if (!hasAccelerometer) {
            Text(
                text = stringResource(R.string.no_accelerometer),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
            return@Column
        }

        Text(stringResource(R.string.accel_x, x), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.accel_y, y), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.accel_z, z), style = MaterialTheme.typography.bodyLarge)
        if (lux != null) {
            Text(stringResource(R.string.light_lux, lux), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(24.dp))

        // Індикатор кута: прогрес = кут / 90°.
        Text(stringResource(R.string.tilt_angle, tiltAngle), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (tiltAngle / TILT_MAX_ANGLE).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
        )
        Spacer(Modifier.height(32.dp))

        // Статус жалюзі за порогами кута.
        val status = blindStatusFor(tiltAngle)
        val (label, color) = when (status) {
            BlindStatus.OPEN -> R.string.status_open to Color(0xFF2E7D32)
            BlindStatus.HALF -> R.string.status_half to Color(0xFFEF6C00)
            BlindStatus.CLOSED -> R.string.status_closed to Color(0xFFC62828)
        }
        Text(stringResource(R.string.blind_status_label), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(label),
            color = color,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BlindControllerScreenPreview() {
    SmartBlindTheme {
        BlindControllerScreen(
            hasAccelerometer = true,
            x = 0.5f, y = 4.9f, z = 8.4f,
            tiltAngle = 45f
        )
    }
}
