/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.R

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
