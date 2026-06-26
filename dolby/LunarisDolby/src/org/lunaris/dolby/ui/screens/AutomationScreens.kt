/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.R
import org.lunaris.dolby.ui.components.FloatingNavToolbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationHubScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
    val currentRoute by navController.currentBackStackEntryFlow.collectAsState(null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.automation_hub_title),
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AutomationNavButton(
                        title = stringResource(R.string.power_user_audio_title),
                        subtitle = stringResource(R.string.power_user_audio_desc),
                        icon = Icons.Default.Tune,
                        onClick = { navController.navigate(Screen.PowerUserAudio.route) }
                    )
                }
                item {
                    AutomationNavButton(
                        title = stringResource(R.string.scheduled_profiles_title),
                        subtitle = stringResource(R.string.scheduled_profiles_home_desc),
                        icon = Icons.Default.Schedule,
                        onClick = { navController.navigate(Screen.ScheduledProfiles.route) }
                    )
                }
                item {
                    AutomationNavButton(
                        title = stringResource(R.string.custom_presets_title),
                        subtitle = stringResource(R.string.custom_presets_desc),
                        icon = Icons.Default.Bookmark,
                        onClick = { navController.navigate(Screen.CustomPresets.route) }
                    )
                }
                item {
                    AutomationNavButton(
                        title = stringResource(R.string.app_profiles_title),
                        subtitle = stringResource(R.string.app_profiles_desc),
                        icon = Icons.Default.Apps,
                        onClick = { navController.navigate(Screen.AppProfiles.route) }
                    )
                }
                item {
                    AutomationNavButton(
                        title = stringResource(R.string.device_memory_manage_title),
                        subtitle = stringResource(R.string.device_memory_manage_desc),
                        icon = Icons.Default.DevicesOther,
                        onClick = { navController.navigate(Screen.DeviceMemory.route) }
                    )
                }
                item {
                    AutomationNavButton(
                        title = stringResource(R.string.bt_rules_title),
                        subtitle = stringResource(R.string.bt_rules_desc),
                        icon = Icons.Default.Bluetooth,
                        onClick = { navController.navigate(Screen.BluetoothRules.route) }
                    )
                }
                item {
                    AutomationNavButton(
                        title = stringResource(R.string.automation_settings_title),
                        subtitle = stringResource(R.string.automation_settings_desc),
                        icon = Icons.Default.Tune,
                        onClick = { navController.navigate(Screen.AutomationSettings.route) }
                    )
                }
                item {
                    AutomationNavButton(
                        title = stringResource(R.string.profile_history_title),
                        subtitle = stringResource(R.string.profile_history_desc),
                        icon = Icons.Default.History,
                        onClick = { navController.navigate(Screen.ProfileHistory.route) }
                    )
                }
                item {
                    AutomationNavButton(
                        title = stringResource(R.string.diagnostics_title),
                        subtitle = stringResource(R.string.diagnostics_desc),
                        icon = Icons.Default.MedicalServices,
                        onClick = { navController.navigate(Screen.Diagnostics.route) }
                    )
                }
                item {
                    AutomationNavButton(
                        title = stringResource(R.string.calibration_title),
                        subtitle = stringResource(R.string.calibration_desc),
                        icon = Icons.Default.GraphicEq,
                        onClick = { navController.navigate(Screen.Calibration.route) }
                    )
                }
                item {
                    AutomationNavButton(
                        title = stringResource(R.string.preset_import_export),
                        subtitle = stringResource(R.string.full_backup_description),
                        icon = Icons.Default.ImportExport,
                        onClick = { navController.navigate(Screen.ImportExport.route) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
            Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth()) {
                FloatingNavToolbar(
                    currentRoute = currentRoute?.destination?.route ?: Screen.Automation.route,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Settings.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AutomationNavButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf(
        stringResource(R.string.onboarding_welcome),
        stringResource(R.string.onboarding_permissions),
        stringResource(R.string.onboarding_qs_tile),
        stringResource(R.string.onboarding_done)
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.onboarding_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(steps[step], style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                when (step) {
                    1 -> {
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.enable_notification_access))
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.app_profiles_grant_permission))
                        }
                    }
                    2 -> {
                        Text(
                            stringResource(R.string.onboarding_qs_tile_desc),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (step > 0) {
                    TextButton(onClick = { step-- }) { Text(stringResource(R.string.back)) }
                } else Spacer(Modifier.width(1.dp))
                Button(onClick = {
                    if (step < steps.lastIndex) step++ else {
                        context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean(DolbyConstants.PREF_ONBOARDING_COMPLETE, true).apply()
                        onComplete()
                    }
                }) {
                    Text(if (step < steps.lastIndex) stringResource(R.string.next) else stringResource(R.string.get_started))
                }
            }
        }
    }
}
