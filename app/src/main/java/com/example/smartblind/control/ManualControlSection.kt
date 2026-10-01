package com.example.smartblind.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartblind.BlindStatus
import com.example.smartblind.R

/** Обробники кнопок ручного керування. */
class ControlActions(
    val onBlind: (BlindStatus) -> Unit,
    val onVibration: (Boolean) -> Unit,
    val onTorch: (Boolean) -> Unit
)

/** Секція «Manual Control»: індикатори актуаторів, джерело останньої команди й кнопки. */
@Composable
fun ManualControlSection(
    state: ControlUiState,
    actions: ControlActions,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.manual_control_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Індикатори активних актуаторів
        Text(stringResource(R.string.torch_state, if (state.torchOn) "ON" else "OFF"))
        Text(
            stringResource(
                R.string.vibration_state,
                stringResource(if (state.vibrationActive) R.string.vibration_active else R.string.vibration_inactive)
            )
        )
        val last = state.lastCommand
        Text(
            text = if (last == null) {
                stringResource(R.string.last_command_none)
            } else {
                stringResource(R.string.last_command, last.label, last.source.label)
            },
            style = MaterialTheme.typography.bodySmall
        )

        // Віртуальні жалюзі
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { actions.onBlind(BlindStatus.OPEN) }, modifier = Modifier.weight(1f)) {
                Text("OPEN")
            }
            Button(onClick = { actions.onBlind(BlindStatus.HALF) }, modifier = Modifier.weight(1f)) {
                Text("HALF")
            }
            Button(onClick = { actions.onBlind(BlindStatus.CLOSED) }, modifier = Modifier.weight(1f)) {
                Text("CLOSE")
            }
        }

        // Примусове керування актуаторами
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { actions.onVibration(true) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.vibration_on))
            }
            OutlinedButton(onClick = { actions.onVibration(false) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.vibration_off))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { actions.onTorch(true) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.torch_on))
            }
            OutlinedButton(onClick = { actions.onTorch(false) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.torch_off))
            }
        }
    }
}
