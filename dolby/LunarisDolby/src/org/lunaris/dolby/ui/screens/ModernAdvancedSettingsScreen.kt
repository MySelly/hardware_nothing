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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.lunaris.dolby.R
import org.lunaris.dolby.domain.models.DolbyUiState
import org.lunaris.dolby.ui.components.*
import org.lunaris.dolby.ui.viewmodel.DolbyViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModernAdvancedSettingsScreen(
    viewModel: DolbyViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentRoute by navController.currentBackStackEntryFlow.collectAsState(null)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.userMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
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
                        stringResource(R.string.dolby_category_adv_settings),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
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
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is DolbyUiState.Success -> {
                ModernAdvancedSettingsContent(
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
}

@Composable
private fun ModernAdvancedSettingsContent(
    state: DolbyUiState.Success,
    viewModel: DolbyViewModel,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.settings.enabled) {
            item {
                val headroom = viewModel.getHeadroomInfo()
                val engine = org.lunaris.dolby.data.AudioEnginePreferences(
                    androidx.compose.ui.platform.LocalContext.current
                )
                if (engine.isHeadroomWarningEnabled() && headroom != null) {
                    org.lunaris.dolby.ui.components.HeadroomWarningCard(headroom)
                }
                OutlinedButton(
                    onClick = { navController.navigate(Screen.PowerUserAudio.route) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.power_user_audio_title))
                }
            }
            item {
                ModernSettingsCard(
                    title = stringResource(R.string.dolby_category_settings),
                    icon = Icons.Default.Tune
                ) {
                    ModernSettingSwitch(
                        title = stringResource(R.string.graphic_eq_enable_title),
                        subtitle = stringResource(R.string.graphic_eq_enable_summary),
                        checked = state.profileSettings.graphicEqEnabled,
                        onCheckedChange = { viewModel.setGraphicEqEnabled(it) },
                        icon = Icons.Default.Equalizer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column {
                        ModernSettingSwitch(
                            title = stringResource(R.string.dolby_bass_enhancer),
                            subtitle = stringResource(R.string.dolby_bass_enhancer_summary),
                            checked = state.profileSettings.bassLevel > 0,
                            onCheckedChange = { enabled ->
                                if (enabled && state.profileSettings.bassLevel == 0) {
                                    viewModel.setBassLevel(50)
                                } else if (!enabled) {
                                    viewModel.setBassLevel(0)
                                }
                            },
                            icon = Icons.Default.MusicNote
                        )
                        
                        AnimatedVisibility(visible = state.profileSettings.bassLevel > 0) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                ModernSettingSelector(
                                    title = stringResource(R.string.dolby_bass_curve),
                                    currentValue = state.profileSettings.bassCurve,
                                    entries = R.array.dolby_bass_curve_entries,
                                    values = R.array.dolby_bass_curve_values,
                                    onValueChange = { viewModel.setBassCurve(it) },
                                    icon = Icons.Default.Equalizer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                ModernSettingSlider(
                                    title = stringResource(R.string.dolby_bass_level),
                                    value = state.profileSettings.bassLevel,
                                    onValueChange = { viewModel.setBassLevel(it.toInt()) },
                                    valueRange = 0f..100f,
                                    steps = 19,
                                    valueLabel = { "$it%" }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Column {
                        ModernSettingSwitch(
                            title = stringResource(R.string.dolby_mid_enhancer),
                            subtitle = stringResource(R.string.dolby_mid_enhancer_summary),
                            checked = state.profileSettings.midLevel > 0,
                            onCheckedChange = { enabled ->
                                if (enabled && state.profileSettings.midLevel == 0) {
                                    viewModel.setMidLevel(40)
                                } else if (!enabled) {
                                    viewModel.setMidLevel(0)
                                }
                            },
                            icon = Icons.Default.VolumeUp
                        )

                        AnimatedVisibility(visible = state.profileSettings.midLevel > 0) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                ModernSettingSlider(
                                    title = stringResource(R.string.dolby_mid_level),
                                    value = state.profileSettings.midLevel,
                                    onValueChange = { viewModel.setMidLevel(it.toInt()) },
                                    valueRange = 0f..100f,
                                    steps = 19,
                                    valueLabel = { "$it%" }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Column {
                        ModernSettingSwitch(
                            title = stringResource(R.string.dolby_treble_enhancer),
                            subtitle = stringResource(R.string.dolby_treble_enhancer_summary),
                            checked = state.profileSettings.trebleLevel > 0,
                            onCheckedChange = { enabled ->
                                if (enabled && state.profileSettings.trebleLevel == 0) {
                                    viewModel.setTrebleLevel(30)
                                } else if (!enabled) {
                                    viewModel.setTrebleLevel(0)
                                }
                            },
                            icon = Icons.Default.GraphicEq
                        )

                        AnimatedVisibility(visible = state.profileSettings.trebleLevel > 0) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                ModernSettingSlider(
                                    title = stringResource(R.string.dolby_treble_level),
                                    value = state.profileSettings.trebleLevel,
                                    onValueChange = { viewModel.setTrebleLevel(it.toInt()) },
                                    valueRange = 0f..100f,
                                    steps = 19,
                                    valueLabel = { "$it%" }
                                )
                            }
                        }
                    }
                }
            }

            item {
                AudioTuningSettingsCard(
                    profileSettings = state.profileSettings,
                    volumeLevelerEnabled = state.settings.volumeLevelerEnabled,
                    showIeqAmount = state.settings.currentProfile != 0,
                    viewModel = viewModel
                )
            }
            
            item {
                ModernSettingsCard(
                    title = stringResource(R.string.dolby_volume_leveler),
                    icon = Icons.Default.VolumeDown
                ) {
                    ModernSettingSwitch(
                        title = stringResource(R.string.dolby_volume_leveler),
                        subtitle = stringResource(R.string.dolby_volume_leveler_summary),
                        checked = state.settings.volumeLevelerEnabled,
                        onCheckedChange = { viewModel.setVolumeLeveler(it) },
                        icon = Icons.Default.BarChart
                    )
                    AnimatedVisibility(visible = state.settings.volumeLevelerEnabled) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            ModernSettingSlider(
                                title = stringResource(R.string.volume_leveler_amount_title),
                                value = state.profileSettings.volumeLevelerAmount,
                                onValueChange = { viewModel.setVolumeLevelerAmount(it.toInt()) },
                                valueRange = 0f..10f,
                                steps = 9,
                                valueLabel = { "$it" }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            ModernSettingSwitch(
                                title = stringResource(R.string.leveler_target_title),
                                subtitle = stringResource(R.string.leveler_target_summary),
                                checked = state.profileSettings.levelerTargetEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.setLevelerTarget(
                                        enabled,
                                        state.profileSettings.levelerTargetDb
                                    )
                                },
                                icon = Icons.Default.Tune
                            )
                            AnimatedVisibility(visible = state.profileSettings.levelerTargetEnabled) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ModernSettingSlider(
                                        title = stringResource(R.string.leveler_target_slider_title),
                                        value = state.profileSettings.levelerTargetDb,
                                        onValueChange = { viewModel.setLevelerTarget(true, it.toInt()) },
                                        valueRange = -40f..0f,
                                        steps = 39,
                                        valueLabel = { "$it dB" }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            if (state.settings.currentProfile != 0) {
                item {
                    ModernSettingsCard(
                        title = stringResource(R.string.dolby_spk_virtualizer),
                        icon = Icons.Default.Headphones
                    ) {
                        if (state.isOnSpeaker) {
                            ModernSettingSwitch(
                                title = stringResource(R.string.dolby_spk_virtualizer),
                                subtitle = stringResource(R.string.dolby_spk_virtualizer_summary),
                                checked = state.profileSettings.speakerVirtualizerEnabled,
                                onCheckedChange = { viewModel.setSpeakerVirtualizer(it) },
                                icon = Icons.Default.Speaker
                            )
                        } else {
                            ModernSettingSwitch(
                                title = stringResource(R.string.dolby_hp_virtualizer),
                                subtitle = stringResource(R.string.dolby_hp_virtualizer_summary),
                                checked = state.profileSettings.headphoneVirtualizerEnabled,
                                onCheckedChange = { viewModel.setHeadphoneVirtualizer(it) },
                                icon = Icons.Default.Headphones
                            )
                            
                            AnimatedVisibility(visible = state.profileSettings.headphoneVirtualizerEnabled) {
                                Column {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    ModernSettingSlider(
                                        title = stringResource(R.string.dolby_hp_virtualizer_dolby_strength),
                                        value = state.profileSettings.stereoWideningAmount,
                                        onValueChange = { viewModel.setStereoWidening(it.toInt()) },
                                        valueRange = 4f..64f,
                                        steps = 59
                                    )
                                }
                            }
                        }
                    }
                }
                
                item {
                    ModernSettingsCard(
                        title = stringResource(R.string.dolby_dialogue_enhancer),
                        icon = Icons.Default.RecordVoiceOver
                    ) {
                        ModernSettingSwitch(
                            title = stringResource(R.string.dolby_dialogue_enhancer),
                            subtitle = stringResource(R.string.dolby_dialogue_enhancer_summary),
                            checked = state.profileSettings.dialogueEnhancerEnabled,
                            onCheckedChange = { viewModel.setDialogueEnhancer(it) },
                            icon = Icons.Default.RecordVoiceOver
                        )
                        
                        AnimatedVisibility(visible = state.profileSettings.dialogueEnhancerEnabled) {
                            Column {
                                Spacer(modifier = Modifier.height(16.dp))
                                ModernSettingSlider(
                                    title = stringResource(R.string.dolby_dialogue_enhancer_dolby_strength),
                                    value = state.profileSettings.dialogueEnhancerAmount,
                                    onValueChange = { viewModel.setDialogueEnhancerAmount(it.toInt()) },
                                    valueRange = 1f..12f,
                                    steps = 10
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        ModernSettingSwitch(
                            title = stringResource(R.string.dialogue_ducking_title),
                            subtitle = stringResource(R.string.dialogue_ducking_summary),
                            checked = state.profileSettings.dialogueDuckingEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setDialogueDucking(
                                    enabled,
                                    state.profileSettings.dialogueDuckingAmount
                                )
                            },
                            icon = Icons.Default.VoiceOverOff
                        )
                        AnimatedVisibility(visible = state.profileSettings.dialogueDuckingEnabled) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                ModernSettingSlider(
                                    title = stringResource(R.string.dialogue_ducking_amount_title),
                                    value = state.profileSettings.dialogueDuckingAmount,
                                    onValueChange = { viewModel.setDialogueDucking(true, it.toInt()) },
                                    valueRange = 0f..16f,
                                    steps = 15,
                                    valueLabel = { "$it" }
                                )
                            }
                        }
                    }
                }
            }
        } else if (state.settings.currentProfile == 0 && state.settings.enabled) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.dolby_adv_settings_footer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        } else if (!state.settings.enabled) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.dolby_adv_settings_footer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
        
        item {
            ModernSettingsCard(
                title = stringResource(R.string.diagnostics_title),
                icon = Icons.Default.MedicalServices
            ) {
                Text(
                    text = stringResource(R.string.diagnostics_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedButton(
                    onClick = { navController.navigate(Screen.Diagnostics.route) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.MedicalServices, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.diagnostics_title))
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(70.dp))
        }
    }
}

@Composable
private fun AudioTuningSettingsCard(
    profileSettings: org.lunaris.dolby.domain.models.ProfileSettings,
    volumeLevelerEnabled: Boolean,
    showIeqAmount: Boolean,
    viewModel: DolbyViewModel
) {
    val context = LocalContext.current
    ModernSettingsCard(
        title = stringResource(R.string.audio_tuning_title),
        icon = Icons.Default.VolumeUp
    ) {
        ModernSettingSwitch(
            title = stringResource(R.string.output_boost_title),
            subtitle = stringResource(R.string.output_boost_summary),
            checked = profileSettings.outputBoostEnabled,
            onCheckedChange = { enabled ->
                if (enabled) {
                    val tenths = if (profileSettings.outputBoostTenthsDb == 0) 30 else profileSettings.outputBoostTenthsDb
                    viewModel.setOutputBoost(true, tenths)
                } else {
                    viewModel.setOutputBoost(false, profileSettings.outputBoostTenthsDb)
                }
            },
            icon = Icons.Default.TrendingUp
        )
        AnimatedVisibility(visible = profileSettings.outputBoostEnabled) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                ModernSettingSlider(
                    title = stringResource(R.string.output_boost_title),
                    value = profileSettings.outputBoostTenthsDb,
                    onValueChange = { viewModel.setOutputBoost(true, it.toInt()) },
                    valueRange = viewModel.getOutputBoostMinTenths().toFloat()..
                        viewModel.getOutputBoostMaxTenths().toFloat(),
                    steps = (viewModel.getOutputBoostMaxTenths() - viewModel.getOutputBoostMinTenths() - 1).coerceAtLeast(0),
                    valueLabel = { tenths ->
                        context.getString(R.string.output_boost_value, tenths / 10f)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ModernSettingSwitch(
            title = stringResource(R.string.volmax_boost_title),
            subtitle = stringResource(R.string.volmax_boost_summary),
            checked = profileSettings.volmaxBoostEnabled,
            onCheckedChange = { enabled ->
                if (enabled) {
                    val value = if (profileSettings.volmaxBoost == 0) 64 else profileSettings.volmaxBoost
                    viewModel.setVolmaxBoost(true, value)
                } else {
                    viewModel.setVolmaxBoost(false, profileSettings.volmaxBoost)
                }
            },
            icon = Icons.Default.Speaker
        )
        AnimatedVisibility(visible = profileSettings.volmaxBoostEnabled) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                ModernSettingSlider(
                    title = stringResource(R.string.volmax_boost_title),
                    value = profileSettings.volmaxBoost,
                    onValueChange = { viewModel.setVolmaxBoost(true, it.toInt()) },
                    valueRange = 0f..96f,
                    steps = 95,
                    valueLabel = { value ->
                        context.getString(R.string.volmax_boost_value, value)
                    }
                )
            }
        }

        if (showIeqAmount) {
            Spacer(modifier = Modifier.height(12.dp))
            ModernSettingSlider(
                title = stringResource(R.string.ieq_amount_title),
                value = profileSettings.ieqAmount,
                onValueChange = { viewModel.setIeqAmount(it.toInt()) },
                valueRange = 0f..10f,
                steps = 9,
                valueLabel = { "$it" }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        ModernSettingSwitch(
            title = stringResource(R.string.surround_boost_title),
            subtitle = stringResource(R.string.surround_boost_summary),
            checked = profileSettings.surroundBoostEnabled,
            onCheckedChange = { enabled ->
                if (enabled) {
                    val value = if (profileSettings.surroundBoost == 0) 16 else profileSettings.surroundBoost
                    viewModel.setSurroundBoost(true, value)
                } else {
                    viewModel.setSurroundBoost(false, profileSettings.surroundBoost)
                }
            },
            icon = Icons.Default.SurroundSound
        )
        AnimatedVisibility(visible = profileSettings.surroundBoostEnabled) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                ModernSettingSlider(
                    title = stringResource(R.string.surround_boost_title),
                    value = profileSettings.surroundBoost,
                    onValueChange = { viewModel.setSurroundBoost(true, it.toInt()) },
                    valueRange = 0f..64f,
                    steps = 63,
                    valueLabel = { "$it" }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ModernSettingSwitch(
            title = stringResource(R.string.surround_decoder_title),
            subtitle = stringResource(R.string.surround_decoder_summary),
            checked = profileSettings.surroundDecoderEnabled,
            onCheckedChange = { viewModel.setSurroundDecoder(it) },
            icon = Icons.Default.SurroundSound
        )

        Spacer(modifier = Modifier.height(12.dp))
        ModernSettingSwitch(
            title = stringResource(R.string.virtual_bass_speaker_title),
            subtitle = stringResource(R.string.virtual_bass_speaker_summary),
            checked = profileSettings.virtualBassSpeakerEnabled,
            onCheckedChange = { viewModel.setVirtualBassSpeaker(it) },
            icon = Icons.Default.GraphicEq
        )

        Spacer(modifier = Modifier.height(12.dp))
        ModernSettingSwitch(
            title = stringResource(R.string.virtual_bass_bluetooth_title),
            subtitle = stringResource(R.string.virtual_bass_bluetooth_summary),
            checked = profileSettings.virtualBassBluetoothEnabled,
            onCheckedChange = { viewModel.setVirtualBassBluetooth(it) },
            icon = Icons.Default.GraphicEq
        )

        Spacer(modifier = Modifier.height(12.dp))
        ModernSettingSwitch(
            title = stringResource(R.string.adv_bass_title),
            subtitle = stringResource(R.string.adv_bass_summary),
            checked = profileSettings.advancedBassEnabled,
            onCheckedChange = { enabled ->
                viewModel.setAdvancedBass(
                    enabled,
                    profileSettings.advancedBassBoost,
                    profileSettings.advancedBassCutoff
                )
            },
            icon = Icons.Default.Speaker
        )
        AnimatedVisibility(visible = profileSettings.advancedBassEnabled) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                ModernSettingSlider(
                    title = stringResource(R.string.adv_bass_boost_title),
                    value = profileSettings.advancedBassBoost,
                    onValueChange = {
                        viewModel.setAdvancedBass(true, it.toInt(), profileSettings.advancedBassCutoff)
                    },
                    valueRange = 0f..100f,
                    steps = 19,
                    valueLabel = { "$it%" }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ModernSettingSlider(
                    title = stringResource(R.string.adv_bass_cutoff_title),
                    value = profileSettings.advancedBassCutoff,
                    onValueChange = {
                        viewModel.setAdvancedBass(true, profileSettings.advancedBassBoost, it.toInt())
                    },
                    valueRange = 50f..1000f,
                    steps = 18,
                    valueLabel = { "$it Hz" }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ModernSettingSwitch(
            title = stringResource(R.string.reverb_suppression_title),
            subtitle = stringResource(R.string.reverb_suppression_summary),
            checked = profileSettings.reverbSuppressionEnabled,
            onCheckedChange = { enabled ->
                viewModel.setReverbSuppression(enabled, profileSettings.reverbSuppressionAmount)
            },
            icon = Icons.Default.Podcasts
        )
        AnimatedVisibility(visible = profileSettings.reverbSuppressionEnabled) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                ModernSettingSlider(
                    title = stringResource(R.string.reverb_suppression_amount_title),
                    value = profileSettings.reverbSuppressionAmount,
                    onValueChange = { viewModel.setReverbSuppression(true, it.toInt()) },
                    valueRange = 0f..16f,
                    steps = 15,
                    valueLabel = { "$it" }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ModernSettingSwitch(
            title = stringResource(R.string.regulator_title),
            subtitle = stringResource(R.string.regulator_summary),
            checked = profileSettings.regulatorEnabled,
            onCheckedChange = { enabled ->
                viewModel.setRegulator(enabled, profileSettings.regulatorOverdriveDb)
            },
            icon = Icons.Default.Shield
        )
        AnimatedVisibility(visible = profileSettings.regulatorEnabled) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                ModernSettingSlider(
                    title = stringResource(R.string.regulator_overdrive_title),
                    value = profileSettings.regulatorOverdriveDb,
                    onValueChange = { viewModel.setRegulator(true, it.toInt()) },
                    valueRange = 0f..12f,
                    steps = 11,
                    valueLabel = { "+$it dB" }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ModernSettingSwitch(
            title = stringResource(R.string.hearing_protection_title),
            subtitle = stringResource(R.string.hearing_protection_summary),
            checked = profileSettings.hearingProtectionEnabled,
            onCheckedChange = { viewModel.setHearingProtection(it) },
            icon = Icons.Default.HealthAndSafety
        )

        Spacer(modifier = Modifier.height(12.dp))
        ModernSettingSwitch(
            title = stringResource(R.string.dsp_volume_boost_title),
            subtitle = stringResource(R.string.dsp_volume_boost_summary),
            checked = profileSettings.dspVolumeBoostEnabled,
            onCheckedChange = { enabled ->
                if (enabled) {
                    val strength = if (profileSettings.dspVolumeBoostStrength == 0) 50 else profileSettings.dspVolumeBoostStrength
                    viewModel.setDspVolumeBoost(true, strength)
                } else {
                    viewModel.setDspVolumeBoost(false, profileSettings.dspVolumeBoostStrength)
                }
            },
            icon = Icons.Default.VolumeUp
        )
        AnimatedVisibility(visible = profileSettings.dspVolumeBoostEnabled) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                ModernSettingSlider(
                    title = stringResource(R.string.dsp_volume_boost_strength),
                    value = profileSettings.dspVolumeBoostStrength,
                    onValueChange = { viewModel.setDspVolumeBoost(true, it.toInt()) },
                    valueRange = 0f..100f,
                    steps = 19,
                    valueLabel = { "$it%" }
                )
            }
        }
    }
}
