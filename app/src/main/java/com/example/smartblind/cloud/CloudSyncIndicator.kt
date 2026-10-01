package com.example.smartblind.cloud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartblind.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Індикатор «Хмарна синхронізація»: зелений — Synced, червоний — Error/Pending. */
@Composable
fun CloudSyncIndicator(state: CloudSyncState, modifier: Modifier = Modifier) {
    val (label, color) = when (state.status) {
        SyncStatus.IDLE -> R.string.cloud_status_idle to Color(0xFF757575)
        SyncStatus.SYNCED -> R.string.cloud_status_synced to Color(0xFF2E7D32)
        SyncStatus.PENDING -> R.string.cloud_status_pending to Color(0xFFC62828)
        SyncStatus.ERROR -> R.string.cloud_status_error to Color(0xFFC62828)
    }
    val lastSync = state.lastSyncMillis?.let {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
    } ?: "—"

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = stringResource(R.string.cloud_sync_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(label), color = color, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.cloud_last_sync, lastSync),
                style = MaterialTheme.typography.bodySmall
            )
            if (state.pendingCount > 0) {
                Text(
                    text = stringResource(R.string.cloud_pending_count, state.pendingCount),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
