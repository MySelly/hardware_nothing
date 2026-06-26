/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.lunaris.dolby.R
import org.lunaris.dolby.domain.models.AutomationStatus
import org.lunaris.dolby.domain.models.ProfileChangeSource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActiveRuleStatusCard(
    status: AutomationStatus,
    currentProfileName: String,
    onUndoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.status_panel_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            StatusRow(stringResource(R.string.status_current_profile), currentProfileName)
            status.activeSource?.let { source ->
                StatusRow(stringResource(R.string.status_active_source), sourceLabel(source))
            }
            if (status.activeDetail.isNotBlank()) {
                StatusRow(stringResource(R.string.status_detail), status.activeDetail)
            }
            status.scheduledRuleName?.let {
                StatusRow(stringResource(R.string.status_schedule), it)
            }
            status.appPackage?.let {
                StatusRow(stringResource(R.string.status_app), it)
            }
            status.bluetoothRuleName?.let {
                StatusRow(stringResource(R.string.status_bluetooth), it)
            }
            status.mediaContentType?.let {
                StatusRow(stringResource(R.string.status_media), it)
            }
            if (status.atmosContentActive) {
                StatusRow(stringResource(R.string.status_atmos), stringResource(R.string.dolby_on))
            }
            if (onUndoClick != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onUndoClick) {
                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.undo_profile_change))
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun sourceLabel(source: ProfileChangeSource): String = when (source) {
    ProfileChangeSource.MANUAL -> stringResource(R.string.source_manual)
    ProfileChangeSource.SCHEDULE -> stringResource(R.string.source_schedule)
    ProfileChangeSource.APP -> stringResource(R.string.source_app)
    ProfileChangeSource.DEVICE -> stringResource(R.string.source_device)
    ProfileChangeSource.BLUETOOTH -> stringResource(R.string.source_bluetooth)
    ProfileChangeSource.MEDIA -> stringResource(R.string.source_media)
    ProfileChangeSource.AUTOMATION -> stringResource(R.string.source_automation)
    ProfileChangeSource.CALL -> stringResource(R.string.source_call)
    ProfileChangeSource.SLEEP_TIMER -> stringResource(R.string.source_sleep_timer)
    ProfileChangeSource.FOCUS -> stringResource(R.string.source_focus)
    ProfileChangeSource.WIDGET -> stringResource(R.string.source_widget)
    ProfileChangeSource.QS_TILE -> stringResource(R.string.source_qs_tile)
}
