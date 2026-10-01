package com.example.smartblind.ble

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartblind.R

@Composable
fun BluetoothScreen(
    viewModel: BleViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isScanning by viewModel.isScanning.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val logLines by viewModel.log.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(
                text = stringResource(R.string.bluetooth_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Індикатор з'єднання
        val stateColor = if (isConnected) Color(0xFF2E7D32) else Color(0xFFC62828)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(stateColor))
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(if (isConnected) R.string.connected else R.string.disconnected),
                color = stateColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            if (isConnected) {
                OutlinedButton(onClick = viewModel::disconnect) {
                    Text(stringResource(R.string.disconnect))
                }
            }
        }

        // Сканування
        Button(
            onClick = viewModel::startScan,
            enabled = !isScanning,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.scan_devices)) }
        if (isScanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        // Знайдені пристрої
        if (devices.isNotEmpty()) {
            Text(stringResource(R.string.found_devices), style = MaterialTheme.typography.titleSmall)
            devices.forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { viewModel.connect(device) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(device.name, fontWeight = FontWeight.Bold)
                        Text(device.address, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(stringResource(R.string.tap_to_connect), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Команди
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BlindCommand.entries.forEach { command ->
                Button(
                    onClick = { viewModel.send(command) },
                    enabled = isConnected,
                    modifier = Modifier.weight(1f)
                ) { Text(command.name) }
            }
        }

        // Термінал
        Text(stringResource(R.string.terminal), style = MaterialTheme.typography.titleSmall)
        val listState = rememberLazyListState()
        LaunchedEffect(logLines.size) {
            if (logLines.isNotEmpty()) listState.animateScrollToItem(logLines.lastIndex)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF101010))
                .padding(8.dp)
        ) {
            items(logLines) { line ->
                Text(
                    text = line,
                    color = when {
                        " TX " in line -> Color(0xFF80CBC4)
                        " RX " in line -> Color(0xFFFFCC80)
                        else -> Color(0xFFB0B0B0)
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
    }
}
