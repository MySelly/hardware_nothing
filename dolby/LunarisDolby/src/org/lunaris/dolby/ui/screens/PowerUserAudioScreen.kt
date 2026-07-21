/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.R
import org.lunaris.dolby.data.AudioEnginePreferences
import org.lunaris.dolby.data.DeviceStateManager
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.data.LoudnessPreset
import org.lunaris.dolby.ui.components.HeadroomWarningCard
import org.lunaris.dolby.ui.components.LoudnessStackBar
import org.lunaris.dolby.ui.viewmodel.DolbyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerUserAudioScreen(
    navController: NavController,
    dolbyViewModel: DolbyViewModel
) {
    val context = LocalContext.current
    val prefs = remember { AudioEnginePreferences(context) }
    val repository = remember { DolbyRepository(context) }
    val uiState by dolbyViewModel.uiState.collectAsState()
    val profile = (uiState as? org.lunaris.dolby.domain.models.DolbyUiState.Success)?.settings?.currentProfile ?: 0
    val profileSettings = (uiState as? org.lunaris.dolby.domain.models.DolbyUiState.Success)?.profileSettings

    var extendedBoost by remember { mutableStateOf(prefs.isExtendedOutputBoostEnabled()) }
    var boostMax by remember { mutableFloatStateOf(prefs.getOutputBoostMaxTenths().toFloat()) }
    var headroomWarn by remember { mutableStateOf(prefs.isHeadroomWarningEnabled()) }
    var softClip by remember { mutableStateOf(prefs.isSoftClipEnabled()) }
    var loudnessPresets by remember { mutableStateOf(prefs.isLoudnessPresetsEnabled()) }
    var autoLoudness by remember { mutableStateOf(prefs.isAutoLoudnessEnabled()) }
    var perDeviceGain by remember { mutableStateOf(prefs.isPerDeviceGainEnabled()) }
    var deviceGain by remember { mutableFloatStateOf(0f) }
    var stereoBalanceOn by remember { mutableStateOf(prefs.isStereoBalanceEnabled()) }
    var stereoBalance by remember { mutableFloatStateOf(prefs.getStereoBalance().toFloat()) }
    var monoMix by remember { mutableStateOf(prefs.isMonoMixEnabled()) }
    var crossfeedOn by remember { mutableStateOf(prefs.isCrossfeedEnabled()) }
    var crossfeed by remember { mutableFloatStateOf(prefs.getCrossfeedStrength().toFloat()) }
    var highPassOn by remember { mutableStateOf(prefs.isHighPassEnabled()) }
    var highPassHz by remember { mutableFloatStateOf(prefs.getHighPassHz().toFloat()) }
    var spectrumOverlay by remember { mutableStateOf(prefs.isEqSpectrumOverlayEnabled()) }
    var coloredLabels by remember { mutableStateOf(prefs.isEqColoredDbLabelsEnabled()) }
    var stackVisual by remember { mutableStateOf(prefs.isLoudnessStackVisualEnabled()) }

    val extendedEqEnabled = prefs.isExtendedEqEnabled()
    val headroom = remember(profile, profileSettings, extendedEqEnabled, extendedBoost) {
        repository.getHeadroomInfo(profile)
    }

    LaunchedEffect(perDeviceGain) {
        if (perDeviceGain) {
            val am = context.getSystemService(android.media.AudioManager::class.java)
            val device = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).firstOrNull()
            if (device != null) {
                val key = DeviceStateManager(context).deviceKey(device)
                deviceGain = prefs.getDeviceGainOffsetTenths(key).toFloat()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { repository.close() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.power_user_audio_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.power_user_audio_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (headroomWarn) {
                item { HeadroomWarningCard(headroom) }
            }

            if (stackVisual && profileSettings != null) {
                item {
                    LoudnessStackBar(
                        outputBoostDb = if (profileSettings.outputBoostEnabled)
                            profileSettings.outputBoostTenthsDb / 10f else 0f,
                        volmaxContribution = if (profileSettings.volmaxBoostEnabled)
                            profileSettings.volmaxBoost / 16f else 0f,
                        dspStrength = if (profileSettings.dspVolumeBoostEnabled)
                            profileSettings.dspVolumeBoostStrength.toFloat() else 0f,
                        maxDb = prefs.maxGainDb() + prefs.getOutputBoostMaxTenths() / 10f
                    )
                }
            }

            item { AudioSectionTitle(stringResource(R.string.output_boost_section)) }
            item {
                EngineSwitch(stringResource(R.string.extended_output_boost_enabled), extendedBoost) {
                    extendedBoost = it
                    prefs.setExtendedOutputBoostEnabled(it)
                }
            }
            if (extendedBoost) {
                item {
                    Text(stringResource(R.string.output_boost_max_label, boostMax / 10f))
                    Slider(
                        value = boostMax,
                        onValueChange = {
                            boostMax = it
                            prefs.setOutputBoostMaxTenths(it.toInt())
                        },
                        valueRange = 60f..150f,
                        steps = 8
                    )
                }
            }

            item { AudioSectionTitle(stringResource(R.string.processing_section)) }
            item {
                EngineSwitch(stringResource(R.string.headroom_warning_enabled), headroomWarn) {
                    headroomWarn = it
                    prefs.setHeadroomWarningEnabled(it)
                }
            }
            item {
                EngineSwitch(stringResource(R.string.soft_clip_enabled), softClip) {
                    softClip = it
                    prefs.setSoftClipEnabled(it)
                    repository.refreshSpatialAndGeq(profile)
                }
            }
            item {
                EngineSwitch(stringResource(R.string.high_pass_enabled), highPassOn) {
                    highPassOn = it
                    prefs.setBoolean(DolbyConstants.PREF_HIGH_PASS_ENABLED, it)
                    repository.refreshSpatialAndGeq(profile)
                }
            }
            if (highPassOn) {
                item {
                    Text(stringResource(R.string.high_pass_hz_label, highPassHz.toInt()))
                    Slider(
                        value = highPassHz,
                        onValueChange = {
                            highPassHz = it
                            context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
                                .edit().putInt(DolbyConstants.PREF_HIGH_PASS_HZ, it.toInt()).apply()
                            repository.refreshSpatialAndGeq(profile)
                        },
                        valueRange = 20f..120f,
                        steps = 10
                    )
                }
            }

            item { AudioSectionTitle(stringResource(R.string.loudness_presets_section)) }
            item {
                EngineSwitch(stringResource(R.string.loudness_presets_enabled), loudnessPresets) {
                    loudnessPresets = it
                    prefs.setBoolean(DolbyConstants.PREF_LOUDNESS_PRESETS_ENABLED, it)
                }
            }
            if (loudnessPresets) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        LoudnessPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    repository.applyLoudnessPreset(preset)
                                    dolbyViewModel.loadSettings()
                                },
                                label = {
                                    Text(when (preset) {
                                        LoudnessPreset.FLAT -> stringResource(R.string.loudness_flat)
                                        LoudnessPreset.PUNCH -> stringResource(R.string.loudness_punch)
                                        LoudnessPreset.WARM -> stringResource(R.string.loudness_warm)
                                        LoudnessPreset.MAX -> stringResource(R.string.loudness_max)
                                    })
                                }
                            )
                        }
                    }
                }
            }
            item {
                EngineSwitch(stringResource(R.string.auto_loudness_enabled), autoLoudness) {
                    autoLoudness = it
                    prefs.setAutoLoudnessEnabled(it)
                }
            }

            item { AudioSectionTitle(stringResource(R.string.spatial_section)) }
            item {
                var spatialMaster by remember {
                    mutableStateOf(repository.isSpatialAudioEnabled())
                }
                EngineSwitch(stringResource(R.string.spatial_audio_master), spatialMaster) {
                    spatialMaster = it
                    repository.setSpatialAudioEnabled(it)
                    repository.refreshSpatialAndGeq(profile)
                }
            }
            item {
                EngineSwitch(stringResource(R.string.stereo_balance_enabled), stereoBalanceOn) {
                    stereoBalanceOn = it
                    prefs.setBoolean(DolbyConstants.PREF_STEREO_BALANCE_ENABLED, it)
                    repository.refreshSpatialAndGeq(profile)
                }
            }
            if (stereoBalanceOn) {
                item {
                    Slider(
                        value = stereoBalance,
                        onValueChange = {
                            stereoBalance = it
                            context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
                                .edit().putInt(DolbyConstants.PREF_STEREO_BALANCE_VALUE, it.toInt()).apply()
                            repository.refreshSpatialAndGeq(profile)
                        },
                        valueRange = -100f..100f
                    )
                }
            }
            item {
                EngineSwitch(stringResource(R.string.mono_mix_enabled), monoMix) {
                    monoMix = it
                    prefs.setBoolean(DolbyConstants.PREF_MONO_MIX_ENABLED, it)
                    repository.refreshSpatialAndGeq(profile)
                }
            }
            item {
                EngineSwitch(stringResource(R.string.crossfeed_enabled), crossfeedOn) {
                    crossfeedOn = it
                    prefs.setBoolean(DolbyConstants.PREF_CROSSFEED_ENABLED, it)
                    repository.refreshSpatialAndGeq(profile)
                }
            }
            if (crossfeedOn) {
                item {
                    Slider(value = crossfeed, onValueChange = {
                        crossfeed = it
                        context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
                            .edit().putInt(DolbyConstants.PREF_CROSSFEED_STRENGTH, it.toInt()).apply()
                        repository.refreshSpatialAndGeq(profile)
                    }, valueRange = 0f..100f)
                }
            }

            item { AudioSectionTitle(stringResource(R.string.per_device_gain_section)) }
            item {
                EngineSwitch(stringResource(R.string.per_device_gain_enabled), perDeviceGain) {
                    perDeviceGain = it
                    prefs.setBoolean(DolbyConstants.PREF_PER_DEVICE_GAIN_ENABLED, it)
                    repository.refreshSpatialAndGeq(profile)
                }
            }
            if (perDeviceGain) {
                item {
                    Text(stringResource(R.string.per_device_gain_slider, deviceGain / 10f))
                    Slider(
                        value = deviceGain,
                        onValueChange = {
                            deviceGain = it
                            val am = context.getSystemService(android.media.AudioManager::class.java)
                            val device = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).firstOrNull()
                            if (device != null) {
                                prefs.setDeviceGainOffsetTenths(
                                    DeviceStateManager(context).deviceKey(device),
                                    it.toInt()
                                )
                                repository.refreshSpatialAndGeq(profile)
                            }
                        },
                        valueRange = -60f..60f
                    )
                }
            }

            item { AudioSectionTitle(stringResource(R.string.calibration_boost_section)) }
            item {
                var calSpeaker by remember {
                    mutableFloatStateOf(repository.getCalibrationBoostSpeaker().toFloat())
                }
                var calHp by remember {
                    mutableFloatStateOf(repository.getCalibrationBoostHeadphone().toFloat())
                }
                var calBt by remember {
                    mutableFloatStateOf(repository.getCalibrationBoostBluetooth().toFloat())
                }
                Text(stringResource(R.string.calibration_boost_speaker, calSpeaker.toInt()))
                Slider(
                    value = calSpeaker,
                    onValueChange = {
                        calSpeaker = it
                        repository.setCalibrationBoostSpeaker(it.toInt())
                    },
                    valueRange = 0f..192f,
                    steps = 23
                )
                Text(stringResource(R.string.calibration_boost_headphone, calHp.toInt()))
                Slider(
                    value = calHp,
                    onValueChange = {
                        calHp = it
                        repository.setCalibrationBoostHeadphone(it.toInt())
                    },
                    valueRange = 0f..192f,
                    steps = 23
                )
                Text(stringResource(R.string.calibration_boost_bt, calBt.toInt()))
                Slider(
                    value = calBt,
                    onValueChange = {
                        calBt = it
                        repository.setCalibrationBoostBluetooth(it.toInt())
                    },
                    valueRange = 0f..192f,
                    steps = 23
                )
            }

            item { AudioSectionTitle(stringResource(R.string.eq_ui_section)) }
            item {
                EngineSwitch(stringResource(R.string.eq_spectrum_overlay), spectrumOverlay) {
                    spectrumOverlay = it
                    prefs.setBoolean(DolbyConstants.PREF_EQ_SPECTRUM_OVERLAY, it)
                }
            }
            item {
                EngineSwitch(stringResource(R.string.eq_colored_db_labels), coloredLabels) {
                    coloredLabels = it
                    prefs.setBoolean(DolbyConstants.PREF_EQ_COLORED_DB_LABELS, it)
                }
            }
            item {
                EngineSwitch(stringResource(R.string.loudness_stack_visual), stackVisual) {
                    stackVisual = it
                    prefs.setBoolean(DolbyConstants.PREF_LOUDNESS_STACK_VISUAL, it)
                }
            }
        }
    }
}

@Composable
private fun AudioSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun EngineSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
