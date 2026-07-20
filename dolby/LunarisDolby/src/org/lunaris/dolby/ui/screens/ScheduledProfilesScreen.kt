/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.lunaris.dolby.R
import org.lunaris.dolby.domain.models.ScheduledProfileRule
import org.lunaris.dolby.domain.models.ScheduledProfileUiState
import org.lunaris.dolby.ui.components.ExactAlarmPermissionCard
import org.lunaris.dolby.ui.components.ModernConfirmDialog
import org.lunaris.dolby.ui.viewmodel.ScheduledProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledProfilesScreen(
    viewModel: ScheduledProfileViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var ruleToDelete by remember { mutableStateOf<ScheduledProfileRule?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.scheduled_profiles_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.scheduled_profiles_add)) }
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { padding ->
        when (val state = uiState) {
            is ScheduledProfileUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ScheduledProfileUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is ScheduledProfileUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        ExactAlarmPermissionCard()
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.scheduled_profiles_enable),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    stringResource(R.string.scheduled_profiles_enable_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.scheduled_profiles_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.rules.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.scheduled_profiles_empty),
                                modifier = Modifier.padding(vertical = 24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(state.rules, key = { it.id }) { rule ->
                            ScheduledRuleCard(
                                rule = rule,
                                onToggle = { viewModel.toggleRule(rule.id, it) },
                                onDelete = { ruleToDelete = rule }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddScheduledRuleDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, profile, start, end, days, priority ->
                viewModel.addRule(name, profile, start, end, days, priority)
                showAddDialog = false
            }
        )
    }

    ruleToDelete?.let { rule ->
        ModernConfirmDialog(
            title = stringResource(R.string.scheduled_profiles_delete_title),
            message = stringResource(R.string.scheduled_profiles_delete_message, rule.name),
            icon = Icons.Default.Delete,
            onConfirm = {
                viewModel.deleteRule(rule.id)
                ruleToDelete = null
            },
            onDismiss = { ruleToDelete = null }
        )
    }
}

@Composable
private fun ScheduledRuleCard(
    rule: ScheduledProfileRule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val profiles = stringArrayResource(R.array.dolby_profile_entries)
    val values = stringArrayResource(R.array.dolby_profile_values)
    val profileName = profiles.getOrElse(values.indexOf(rule.profileId.toString()).coerceAtLeast(0)) { "" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(rule.name, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        R.string.scheduled_profiles_rule_summary,
                        profileName,
                        rule.startHour,
                        rule.endHour
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (rule.daysOfWeek.isNotEmpty()) {
                    Text(
                        stringResource(R.string.scheduled_profiles_days, rule.daysOfWeek.sorted().joinToString(",")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (rule.priority > 0) {
                    Text(
                        stringResource(R.string.scheduled_profiles_priority, rule.priority),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddScheduledRuleDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, profileId: Int, startHour: Int, endHour: Int, days: Set<Int>, priority: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var profileIndex by remember { mutableIntStateOf(0) }
    var startHour by remember { mutableIntStateOf(22) }
    var endHour by remember { mutableIntStateOf(7) }
    var priority by remember { mutableIntStateOf(0) }
    val selectedDays = remember { mutableStateListOf<Int>() }
    val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
    val dayValues = listOf(1, 2, 3, 4, 5, 6, 7)
    val profiles = stringArrayResource(R.array.dolby_profile_entries)
    val values = stringArrayResource(R.array.dolby_profile_values)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scheduled_profiles_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.scheduled_profiles_rule_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.scheduled_profiles_days_label))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayLabels.forEachIndexed { index, label ->
                        val day = dayValues[index]
                        FilterChip(
                            selected = day in selectedDays,
                            onClick = {
                                if (day in selectedDays) selectedDays.remove(day) else selectedDays.add(day)
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Text(stringResource(R.string.scheduled_profiles_profile_label))
                profiles.forEachIndexed { index, label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = profileIndex == index,
                            onClick = { profileIndex = index }
                        )
                        Text(label)
                    }
                }
                Text(stringResource(R.string.scheduled_profiles_start_hour, startHour))
                Slider(
                    value = startHour.toFloat(),
                    onValueChange = { startHour = it.toInt() },
                    valueRange = 0f..23f,
                    steps = 22
                )
                Text(stringResource(R.string.scheduled_profiles_end_hour, endHour))
                Slider(
                    value = endHour.toFloat(),
                    onValueChange = { endHour = it.toInt() },
                    valueRange = 0f..23f,
                    steps = 22
                )
                Text(stringResource(R.string.scheduled_profiles_priority, priority))
                Slider(
                    value = priority.toFloat(),
                    onValueChange = { priority = it.toInt() },
                    valueRange = 0f..10f,
                    steps = 10
                )
            }
        },
        confirmButton = {
            val defaultName = stringResource(R.string.scheduled_profiles_unnamed)
            Button(
                onClick = {
                    onAdd(
                        name.ifBlank { defaultName },
                        values[profileIndex].toInt(),
                        startHour,
                        endHour,
                        selectedDays.toSet(),
                        priority
                    )
                },
                enabled = true
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
