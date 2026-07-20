/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.screens

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.R
import org.lunaris.dolby.data.BluetoothProfileManager
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.data.ProfileChangeHistoryManager
import org.lunaris.dolby.data.SleepTimerManager
import org.lunaris.dolby.domain.models.SleepTimerAction
import org.lunaris.dolby.data.DolbyStatusHelper
import org.lunaris.dolby.ui.theme.DolbyAppearanceState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
    var batterySaver by remember { mutableStateOf(prefs.getBoolean(DolbyConstants.PREF_BATTERY_SAVER_MODE, false)) }
    var autoCall by remember { mutableStateOf(prefs.getBoolean(DolbyConstants.PREF_AUTO_DISABLE_ON_CALL, true)) }
    var mediaDetect by remember { mutableStateOf(prefs.getBoolean(DolbyConstants.PREF_MEDIA_CONTENT_DETECTION, false)) }
    var gameLatency by remember { mutableStateOf(prefs.getBoolean(DolbyConstants.PREF_GAME_LATENCY_MODE, false)) }
    var focusMode by remember { mutableStateOf(prefs.getBoolean(DolbyConstants.PREF_FOCUS_MODE_INTEGRATION, false)) }
    var simpleUi by remember { mutableStateOf(prefs.getBoolean(DolbyConstants.PREF_SIMPLE_UI_MODE, false)) }
    var amoled by remember { mutableStateOf(prefs.getBoolean(DolbyConstants.PREF_AMOLED_THEME, false)) }
    var safeLimit by remember { mutableFloatStateOf(prefs.getInt(DolbyConstants.PREF_SAFE_LISTENING_LIMIT, 0).toFloat()) }
    var sleepMinutes by remember { mutableIntStateOf(30) }
    val timerManager = remember { SleepTimerManager(context) }
    var activeTimer by remember { mutableStateOf(timerManager.getActiveTimer()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.automation_settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SectionTitle(stringResource(R.string.sleep_timer_title)) }
            item {
                Text(stringResource(R.string.sleep_timer_desc))
                Slider(
                    value = sleepMinutes.toFloat(),
                    onValueChange = { sleepMinutes = it.toInt() },
                    valueRange = 5f..120f,
                    steps = 22
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val repo = DolbyRepository(context)
                        val current = repo.getCurrentProfile()
                        repo.close()
                        timerManager.startTimer(sleepMinutes, SleepTimerAction.RESTORE_PROFILE, 0, current)
                        activeTimer = timerManager.getActiveTimer()
                    }) { Text(stringResource(R.string.sleep_timer_start)) }
                    if (activeTimer != null) {
                        OutlinedButton(onClick = {
                            timerManager.clearTimer()
                            activeTimer = null
                        }) { Text(stringResource(R.string.sleep_timer_cancel)) }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.automation_features_title)) }
            item { PrefSwitch(stringResource(R.string.battery_saver_mode), batterySaver) {
                batterySaver = it
                prefs.edit().putBoolean(DolbyConstants.PREF_BATTERY_SAVER_MODE, it).apply()
            }}
            item { PrefSwitch(stringResource(R.string.auto_disable_on_call), autoCall) {
                autoCall = it
                prefs.edit().putBoolean(DolbyConstants.PREF_AUTO_DISABLE_ON_CALL, it).apply()
            }}
            item { PrefSwitch(stringResource(R.string.media_content_detection), mediaDetect) {
                mediaDetect = it
                prefs.edit().putBoolean(DolbyConstants.PREF_MEDIA_CONTENT_DETECTION, it).apply()
            }}
            item { PrefSwitch(stringResource(R.string.game_latency_mode), gameLatency) {
                gameLatency = it
                prefs.edit().putBoolean(DolbyConstants.PREF_GAME_LATENCY_MODE, it).apply()
            }}
            item { PrefSwitch(stringResource(R.string.focus_mode_integration), focusMode) {
                focusMode = it
                prefs.edit().putBoolean(DolbyConstants.PREF_FOCUS_MODE_INTEGRATION, it).apply()
            }}
            item { SectionTitle(stringResource(R.string.ui_settings_title)) }
            item { PrefSwitch(stringResource(R.string.simple_ui_mode), simpleUi) {
                simpleUi = it
                prefs.edit().putBoolean(DolbyConstants.PREF_SIMPLE_UI_MODE, it).apply()
                DolbyAppearanceState.notifyChanged()
            }}
            item { PrefSwitch(stringResource(R.string.amoled_theme), amoled) {
                amoled = it
                prefs.edit().putBoolean(DolbyConstants.PREF_AMOLED_THEME, it).apply()
                DolbyAppearanceState.notifyChanged()
            }}
            item {
                Text(stringResource(R.string.safe_listening_limit))
                Slider(
                    value = safeLimit,
                    onValueChange = {
                        safeLimit = it
                        prefs.edit().putInt(DolbyConstants.PREF_SAFE_LISTENING_LIMIT, it.toInt()).apply()
                    },
                    valueRange = 0f..100f,
                    steps = 20
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun PrefSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothRulesScreen(navController: NavController) {
    val context = LocalContext.current
    val btManager = remember { BluetoothProfileManager(context) }
    var rules by remember { mutableStateOf(btManager.getRules()) }
    val audioManager = context.getSystemService(AudioManager::class.java)
    val btDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bt_rules_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            if (btDevice != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        btManager.addRule(btDevice, 2)
                        rules = btManager.getRules()
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.bt_rules_add_current)) }
                )
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            if (rules.isEmpty()) {
                item { Text(stringResource(R.string.bt_rules_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(rules, key = { it.deviceKey }) { rule ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(rule.displayName, fontWeight = FontWeight.SemiBold)
                                Text("Profile ${rule.profileId}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = {
                                btManager.deleteRule(rule.deviceKey)
                                rules = btManager.getRules()
                            }) { Icon(Icons.Default.Delete, contentDescription = null) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileHistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val historyManager = remember { ProfileChangeHistoryManager(context) }
    var history by remember { mutableStateOf(historyManager.getHistory()) }
    val profiles = stringArrayResource(R.array.dolby_profile_entries)
    val values = stringArrayResource(R.array.dolby_profile_values)
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_history_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        historyManager.clearHistory()
                        history = emptyList()
                    }) { Icon(Icons.Default.DeleteSweep, contentDescription = null) }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            if (history.isEmpty()) {
                item { Text(stringResource(R.string.profile_history_empty)) }
            } else {
                items(history, key = { it.timestamp }) { entry ->
                    val idx = values.indexOf(entry.profileId.toString())
                    val profileName = profiles.getOrElse(idx.coerceAtLeast(0)) { "?" }
                    ListItem(
                        headlineContent = { Text(profileName) },
                        supportingContent = { Text("${entry.source.key}: ${entry.detail}") },
                        trailingContent = { Text(fmt.format(Date(entry.timestamp)), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(navController: NavController) {
    val context = LocalContext.current
    val helper = remember { DolbyStatusHelper(context) }
    val diagnostics = remember { helper.getDiagnostics() }
    DisposableEffect(Unit) { onDispose { helper.close() } }
    val profiles = stringArrayResource(R.array.dolby_profile_entries)
    val values = stringArrayResource(R.array.dolby_profile_values)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item { DiagRow(stringResource(R.string.dolby_enable), if (diagnostics.dolbyEnabled) "ON" else "OFF") }
            item {
                val idx = values.indexOf(diagnostics.currentProfile.toString())
                DiagRow(stringResource(R.string.dolby_profile_title), profiles.getOrElse(idx.coerceAtLeast(0)) { "?" })
            }
            item { DiagRow(stringResource(R.string.diagnostics_effect), if (diagnostics.effectHasControl) "OK" else "FAIL") }
            item { DiagRow(stringResource(R.string.audio_output_active_device), diagnostics.activeDevice.name) }
            item { DiagRow(stringResource(R.string.status_active_source), diagnostics.automationStatus.activeSource?.key ?: "-") }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
