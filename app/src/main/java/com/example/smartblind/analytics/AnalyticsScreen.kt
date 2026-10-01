package com.example.smartblind.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartblind.R
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TILT_AXIS_MAX = 90.0   // діапазон кута нахилу: 0..90°
private const val X_LABEL_SPACING = 10   // підпис часу під кожною 10-ю точкою

/** Екран «Аналітика»: лінійний графік останніх записів із БД, оновлюється в реальному часі. */
@Composable
fun AnalyticsScreen(viewModel: SensorViewModel, modifier: Modifier = Modifier) {
    val records by viewModel.records.collectAsState()
    val modelProducer = remember { CartesianChartModelProducer() }

    // Нові записи з БД → нова серія для графіка. X — порядковий номер точки, Y — кут нахилу.
    LaunchedEffect(records) {
        if (records.isNotEmpty()) {
            modelProducer.runTransaction { lineSeries { series(records.map { it.sensorValue }) } }
        }
    }

    // Підпис осі X — час запису відповідної точки.
    val currentRecords by rememberUpdatedState(records)
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val xFormatter = remember {
        CartesianValueFormatter { _, x, _ ->
            currentRecords.getOrNull(x.toInt())?.let { timeFormat.format(Date(it.timestamp)) } ?: ""
        }
    }
    val yFormatter = remember { CartesianValueFormatter { _, y, _ -> "%.0f°".format(y) } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.analytics_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        // Легенда й підписи осей
        Text(stringResource(R.string.analytics_legend), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.analytics_axis_y), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.analytics_axis_x), style = MaterialTheme.typography.bodySmall)
        Text(
            text = stringResource(
                R.string.analytics_stats,
                records.size,
                CHART_POINTS,
                records.count { it.actionTriggered }
            ),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))

        if (records.isEmpty()) {
            Text(
                text = stringResource(R.string.analytics_empty),
                color = MaterialTheme.colorScheme.error
            )
        } else {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = TILT_AXIS_MAX)
                    ),
                    startAxis = VerticalAxis.rememberStart(valueFormatter = yFormatter),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = xFormatter,
                        itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { X_LABEL_SPACING })
                    )
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            )
        }
    }
}
