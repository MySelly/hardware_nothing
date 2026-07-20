/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.lunaris.dolby.R
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import org.lunaris.dolby.data.AutomationStatusResolver
import org.lunaris.dolby.data.ProfileChangeHistoryManager
import org.lunaris.dolby.data.UndoOfferNotifier
import org.lunaris.dolby.domain.models.DolbyUiState
import org.lunaris.dolby.ui.components.*
import org.lunaris.dolby.ui.viewmodel.DolbyViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModernDolbySettingsScreen(
    viewModel: DolbyViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }
    val currentRoute by navController.currentBackStackEntryFlow.collectAsState(null)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(Unit) {
        viewModel.userMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        UndoOfferNotifier.offers.collect { offer ->
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.undo_snackbar_message, offer.detail),
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                ProfileChangeHistoryManager(context).consumeUndoProfile()?.let { profile ->
                    viewModel.setProfile(profile)
                } ?: viewModel.setProfile(offer.previousProfileId)
            }
        }
    }
    
    val layoutDirection = LocalLayoutDirection.current
    val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.dolby_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ) 
                },
                actions = {
                    IconButton(onClick = { showCreditsDialog = true }) {
                        Icon(
                            Icons.Default.Info, 
                            contentDescription = "Credits",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(
                            Icons.Default.RestartAlt, 
                            contentDescription = "Reset",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is DolbyUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is DolbyUiState.Success -> {
                ModernDolbySettingsContent(
                    state = state,
                    viewModel = viewModel,
                    navController = navController,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is DolbyUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = { viewModel.loadSettings() },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(stringResource(R.string.app_profiles_retry))
                        }
                    }
                }
            }
        }
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f)
                            )
                        )
                    )
            )
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = cutoutInsets.calculateStartPadding(layoutDirection),
                        end = cutoutInsets.calculateEndPadding(layoutDirection),
                        bottom = paddingValues.calculateBottomPadding()
                    ),
                contentAlignment = Alignment.Center
            ) {
                FloatingNavToolbar(
                    currentRoute = currentRoute?.destination?.route ?: "settings",
                    onNavigate = { route ->
                        if (currentRoute?.destination?.route != route) {
                            navController.navigate(route) {
                                popUpTo("settings") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }

    if (showResetDialog) {
        ModernConfirmDialog(
            title = stringResource(R.string.dolby_reset_all),
            message = stringResource(R.string.dolby_reset_all_message),
            icon = Icons.Default.RestartAlt,
            onConfirm = {
                viewModel.resetAllProfiles()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false }
        )
    }
    
    if (showCreditsDialog) {
        CreditsDialog(
            onDismiss = { showCreditsDialog = false }
        )
    }
}

@Composable
private fun ModernDolbySettingsContent(
    state: DolbyUiState.Success,
    viewModel: DolbyViewModel,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val statusResolver = remember { AutomationStatusResolver(context) }
    val historyManager = remember { ProfileChangeHistoryManager(context) }
    val automationStatus = remember(state) { statusResolver.resolve() }
    val profiles = stringArrayResource(R.array.dolby_profile_entries)
    val profileValues = stringArrayResource(R.array.dolby_profile_values)
    val profileIdx = profileValues.indexOf(state.settings.currentProfile.toString())
    val profileName = profiles.getOrElse(profileIdx.coerceAtLeast(0)) { "?" }

    DisposableEffect(Unit) {
        onDispose { statusResolver.close() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DolbyMainCard(
                enabled = state.settings.enabled,
                onEnabledChange = { viewModel.setDolbyEnabled(it) },
                onBypassChange = { viewModel.setDolbyBypass(it) }
            )
        }

        item {
            ActiveAudioDeviceCard(device = state.activeAudioDevice)
        }

        item {
            ActiveRuleStatusCard(
                status = automationStatus,
                currentProfileName = profileName,
                onUndoClick = {
                    historyManager.consumeUndoProfile()?.let { profile ->
                        viewModel.setProfile(profile)
                    }
                }
            )
        }

        item {
            NotificationListenerPermissionCard()
        }

        item {
            AnimatedVisibility(
                visible = state.settings.enabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ModernProfileSelector(
                    currentProfile = state.settings.currentProfile,
                    onProfileChange = { viewModel.setProfile(it) }
                )
            }
        }

        item {
            AnimatedVisibility(
                visible = state.settings.enabled && state.settings.currentProfile != 0,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ModernSettingsCard(
                    title = stringResource(R.string.dolby_ieq),
                    icon = Icons.Default.GraphicEq
                ) {
                    ModernIeqSelector(
                        currentPreset = state.profileSettings.ieqPreset,
                        onPresetChange = { viewModel.setIeqPreset(it) }
                    )
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = state.settings.enabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                AppProfileSettingsCard(
                    onManageClick = { navController.navigate("app_profiles") },
                    onManageDeviceMemoryClick = { navController.navigate("device_memory") }
                )
            }
        }

        item {
            AnimatedVisibility(
                visible = state.settings.enabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ModernSettingsCard(
                    title = stringResource(R.string.hub_tools_title),
                    icon = Icons.Default.Apps
                ) {
                    Text(
                        text = stringResource(R.string.hub_tools_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    HubDestinationRow(
                        title = stringResource(R.string.custom_presets_title),
                        icon = Icons.Default.Bookmark,
                        onClick = { navController.navigate("custom_presets") }
                    )
                    HubDestinationRow(
                        title = stringResource(R.string.scheduled_profiles_title),
                        icon = Icons.Default.Schedule,
                        onClick = { navController.navigate("scheduled_profiles") }
                    )
                    HubDestinationRow(
                        title = stringResource(R.string.bt_rules_title),
                        icon = Icons.Default.Bluetooth,
                        onClick = { navController.navigate(Screen.BluetoothRules.route) }
                    )
                    HubDestinationRow(
                        title = stringResource(R.string.automation_settings_title),
                        icon = Icons.Default.Tune,
                        onClick = { navController.navigate(Screen.AutomationSettings.route) }
                    )
                    HubDestinationRow(
                        title = stringResource(R.string.profile_history_title),
                        icon = Icons.Default.History,
                        onClick = { navController.navigate(Screen.ProfileHistory.route) }
                    )
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(70.dp))
        }
    }
}

@Composable
private fun HubDestinationRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
