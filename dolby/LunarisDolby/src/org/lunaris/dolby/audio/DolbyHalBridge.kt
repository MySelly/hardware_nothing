/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.audio

import android.content.Context
import android.media.AudioManager
import org.lunaris.dolby.DolbyConstants

/**
 * Vendor HAL parameter bridge for DAX settings not exposed via AudioEffect DsParam.
 */
object DolbyHalBridge {

    private fun audioManager(context: Context): AudioManager? =
        context.getSystemService(AudioManager::class.java)

    fun applyVolmaxBoost(context: Context, enabled: Boolean, value: Int) {
        val boost = if (enabled) value.coerceIn(0, 96) else 0
        setParameters(context, listOf(
            "dolby_volmax_boost=$boost",
            "volmax_boost=$boost",
            "volmax-boost=$boost",
            "DolbyVolMaxBoost=$boost"
        ))
        DolbyConstants.dlog(TAG, "volmax_boost=$boost enabled=$enabled")
    }

    fun applySurroundBoost(context: Context, enabled: Boolean, value: Int) {
        val boost = if (enabled) value.coerceIn(0, 64) else 0
        setParameters(context, listOf(
            "dolby_surround_boost=$boost",
            "surround_boost=$boost",
            "surround-boost=$boost",
            "DolbySurroundBoost=$boost"
        ))
        DolbyConstants.dlog(TAG, "surround_boost=$boost enabled=$enabled")
    }

    fun applyHearingProtection(
        context: Context,
        enabled: Boolean,
        rmsTargetRaw: Int = DolbyConstants.HP_RMS_TARGET_STOCK_RAW,
        attackMs: Int = DolbyConstants.HP_ATTACK_STOCK_MS,
        releaseMs: Int = DolbyConstants.HP_RELEASE_STOCK_MS
    ) {
        val flag = if (enabled) 1 else 0
        val target = rmsTargetRaw.coerceIn(DolbyConstants.HP_RMS_TARGET_MIN_RAW, DolbyConstants.HP_RMS_TARGET_MAX_RAW)
        val attack = attackMs.coerceIn(DolbyConstants.HP_ATTACK_MIN_MS, DolbyConstants.HP_ATTACK_MAX_MS)
        val release = releaseMs.coerceIn(DolbyConstants.HP_RELEASE_MIN_MS, DolbyConstants.HP_RELEASE_MAX_MS)
        setParameters(context, listOf(
            "dolby_hearing_protection=$flag",
            "hearing_protection_enable=$flag",
            "hearing-protection-enable=$flag",
            "hearing_protection_rms_attenuation_target=$target",
            "hearing-protection-rms-attenuation-target=$target",
            "hearing_protection_attenuation_attack_time=$attack",
            "hearing-protection-attenuation-attack-time=$attack",
            "hearing_protection_attenuation_release_time=$release",
            "hearing-protection-attenuation-release-time=$release"
        ))
        DolbyConstants.dlog(TAG, "hearing_protection=$flag target=$target attack=$attack release=$release")
    }

    fun applyVirtualBassHal(
        context: Context,
        enabled: Boolean,
        mode: Int = DolbyConstants.VB_MODE_STOCK,
        overallGain: Int = DolbyConstants.VB_OVERALL_GAIN_STOCK,
        slopeGain: Int = DolbyConstants.VB_SLOPE_GAIN_STOCK
    ) {
        val flag = if (enabled) 1 else 0
        val vbMode = mode.coerceIn(0, DolbyConstants.VB_MODE_MAX)
        val overall = overallGain.coerceIn(DolbyConstants.VB_OVERALL_GAIN_MIN, DolbyConstants.VB_OVERALL_GAIN_MAX)
        val slope = slopeGain.coerceIn(DolbyConstants.VB_SLOPE_GAIN_MIN, DolbyConstants.VB_SLOPE_GAIN_MAX)
        setParameters(context, listOf(
            "dolby_virtual_bass=$flag",
            "virtual_bass_process_enable=$flag",
            "virtual-bass-process-enable=$flag",
            "virtual_bass_process=$flag",
            "virtual_bass_mode=$vbMode",
            "virtual-bass-mode=$vbMode",
            "virtual_bass_overall_gain=$overall",
            "virtual-bass-overall-gain=$overall",
            "virtual_bass_slope_gain=$slope",
            "virtual-bass-slope-gain=$slope"
        ))
        DolbyConstants.dlog(TAG, "virtual_bass=$flag mode=$vbMode overall=$overall slope=$slope")
    }

    fun applyReverbSuppression(context: Context, enabled: Boolean, amount: Int) {
        val flag = if (enabled) 1 else 0
        val amt = if (enabled) amount.coerceIn(0, 16) else 0
        setParameters(context, listOf(
            "reverb_suppression_enable=$flag",
            "reverb-suppression-enable=$flag",
            "reverb_suppression_amount=$amt",
            "reverb-suppression-amount=$amt"
        ))
        DolbyConstants.dlog(TAG, "reverb_suppression enable=$flag amount=$amt")
    }

    fun applyAdvancedBass(
        context: Context,
        enabled: Boolean,
        boostPercent: Int,
        cutoffHz: Int,
        width: Int = DolbyConstants.ADV_BASS_WIDTH_STOCK
    ) {
        val flag = if (enabled) 1 else 0
        val rawBoost = if (enabled) {
            boostPercent.coerceIn(0, 100) * DolbyConstants.ADV_BASS_BOOST_RAW_MAX / 100
        } else {
            DolbyConstants.ADV_BASS_BOOST_RAW_STOCK
        }
        val cutoff = if (enabled) {
            cutoffHz.coerceIn(DolbyConstants.ADV_BASS_CUTOFF_MIN_HZ, DolbyConstants.ADV_BASS_CUTOFF_MAX_HZ)
        } else {
            DolbyConstants.ADV_BASS_CUTOFF_STOCK_HZ
        }
        val widthVal = if (enabled) {
            width.coerceIn(DolbyConstants.ADV_BASS_WIDTH_MIN, DolbyConstants.ADV_BASS_WIDTH_MAX)
        } else {
            DolbyConstants.ADV_BASS_WIDTH_STOCK
        }
        setParameters(context, listOf(
            "bass_enhancer_enable=$flag",
            "bass-enhancer-enable=$flag",
            "bass_enhancer_boost=$rawBoost",
            "bass-enhancer-boost=$rawBoost",
            "bass_enhancer_cutoff_frequency=$cutoff",
            "bass-enhancer-cutoff-frequency=$cutoff",
            "bass_enhancer_width=$widthVal",
            "bass-enhancer-width=$widthVal"
        ))
        DolbyConstants.dlog(TAG, "adv_bass enabled=$enabled boost=$rawBoost cutoff=$cutoff width=$widthVal")
    }

    fun applyLevelerTarget(context: Context, enabled: Boolean, targetDb: Int) {
        val db = if (enabled) {
            targetDb.coerceIn(DolbyConstants.LEVELER_TARGET_MIN_DB, DolbyConstants.LEVELER_TARGET_MAX_DB)
        } else {
            DolbyConstants.LEVELER_TARGET_STOCK_DB
        }
        val raw = db * 16
        setParameters(context, listOf(
            "volume_leveler_in_target=$raw",
            "volume-leveler-in-target=$raw",
            "volume_leveler_out_target=$raw",
            "volume-leveler-out-target=$raw"
        ))
        DolbyConstants.dlog(TAG, "leveler_target enabled=$enabled db=$db raw=$raw")
    }

    fun applySurroundDecoder(context: Context, enabled: Boolean, diffuseAmount: Int = 0) {
        val flag = if (enabled) 1 else 0
        val diffuse = if (enabled) {
            diffuseAmount.coerceIn(0, DolbyConstants.SURROUND_DIFFUSE_MAX)
        } else {
            0
        }
        setParameters(context, listOf(
            "surround_decoder_enable=$flag",
            "surround-decoder-enable=$flag",
            "dolby_surround_decoder=$flag",
            "surround_decoder_diffuse_relocating_to_front_amount=$diffuse",
            "surround-decoder-diffuse-relocating-to-front-amount=$diffuse"
        ))
        DolbyConstants.dlog(TAG, "surround_decoder=$flag diffuse=$diffuse")
    }

    fun applyRegulator(
        context: Context,
        enabled: Boolean,
        overdriveDb: Int,
        timbrePreservation: Boolean = true,
        sibilancePercent: Int = 50,
        stressPercent: Int = 50
    ) {
        val flag = if (enabled) 1 else 0
        val rawOverdrive = if (enabled) {
            overdriveDb.coerceIn(0, DolbyConstants.REGULATOR_OVERDRIVE_MAX_DB) * 16
        } else {
            0
        }
        val timbre = if (enabled && timbrePreservation) DolbyConstants.REGULATOR_TIMBRE_STOCK else 0
        val sibilanceEnable = if (enabled && sibilancePercent > 0) 1 else 0
        // Map 0-100 UI percent onto a representative raw band amount (stock ~ -400)
        val sibilanceRaw = if (sibilanceEnable == 1) {
            -(sibilancePercent.coerceIn(0, 100) * 432 / 100)
        } else {
            0
        }
        val stressRaw = if (enabled) {
            (stressPercent.coerceIn(0, 100) * 240 / 100)
        } else {
            0
        }
        setParameters(context, listOf(
            "regulator_enable=$flag",
            "regulator-enable=$flag",
            "regulator_overdrive=$rawOverdrive",
            "regulator-overdrive=$rawOverdrive",
            "regulator_timbre_preservation=$timbre",
            "regulator-timbre-preservation=$timbre",
            "regulator_sibilance_suppress_enable=$sibilanceEnable",
            "regulator-sibilance-suppress-enable=$sibilanceEnable",
            "regulator_sibilance_suppress_amount=$sibilanceRaw",
            "regulator-sibilance-suppress-amount=$sibilanceRaw",
            "regulator_stress_amount=$stressRaw",
            "regulator-stress-amount=$stressRaw"
        ))
        DolbyConstants.dlog(
            TAG,
            "regulator enable=$flag overdrive=$rawOverdrive timbre=$timbre sibilance=$sibilanceRaw stress=$stressRaw"
        )
    }

    fun applyVolumeModeler(context: Context, enabled: Boolean) {
        val flag = if (enabled) 1 else 0
        setParameters(context, listOf(
            "volume_modeler_enable=$flag",
            "volume-modeler-enable=$flag",
            "dolby_volume_modeler=$flag"
        ))
        DolbyConstants.dlog(TAG, "volume_modeler=$flag")
    }

    fun applyHeadphoneVirtualizerTuning(
        context: Context,
        mode: Int,
        lrAngle: Int,
        startBand: Int
    ) {
        val virtMode = mode.coerceIn(0, 1)
        val angle = lrAngle.coerceIn(0, DolbyConstants.HP_VIRT_LR_ANGLE_MAX)
        val band = startBand.coerceIn(0, DolbyConstants.HP_VIRT_START_BAND_MAX)
        setParameters(context, listOf(
            "headphone_virtualizer_mode=$virtMode",
            "headphone-virtualizer-mode=$virtMode",
            "advanced_headphone_virtualizer_lr_angle=$angle",
            "advanced-headphone-virtualizer-lr-angle=$angle",
            "virtualizer_start_band=$band",
            "virtualizer-start-band=$band"
        ))
        DolbyConstants.dlog(TAG, "hp_virt_tuning mode=$virtMode angle=$angle startBand=$band")
    }

    fun applyCalibrationBoost(context: Context, boost: Int) {
        val value = boost.coerceIn(0, DolbyConstants.CALIBRATION_BOOST_MAX)
        setParameters(context, listOf(
            "calibration_boost=$value",
            "calibration-boost=$value",
            "dolby_calibration_boost=$value"
        ))
        DolbyConstants.dlog(TAG, "calibration_boost=$value")
    }

    fun applyGameLatencyHal(context: Context, enabled: Boolean) {
        val flag = if (enabled) 1 else 0
        setParameters(context, listOf(
            "game_latency_mode=$flag",
            "game-latency-mode=$flag",
            "dolby_low_latency=$flag",
            "dolby-low-latency=$flag"
        ))
        DolbyConstants.dlog(TAG, "game_latency=$flag")
    }

    fun applyMiSteering(context: Context, enabled: Boolean) {
        val flag = if (enabled) 1 else 0
        setParameters(context, listOf(
            "mi_dv_leveler_steering_enable=$flag",
            "mi-dv-leveler-steering-enable=$flag",
            "mi_dv_dialog_steering_enable=$flag",
            "mi-dv-dialog-steering-enable=$flag",
            "mi_ieq_steering_enable=$flag",
            "mi-ieq-steering-enable=$flag"
        ))
        DolbyConstants.dlog(TAG, "mi_steering=$flag")
    }

    fun applySpatialAudio(
        context: Context,
        balanceEnabled: Boolean,
        balance: Int,
        monoEnabled: Boolean,
        crossfeedEnabled: Boolean,
        crossfeedStrength: Int
    ) {
        val balanceVal = if (balanceEnabled) balance.coerceIn(-100, 100) else 0
        val mono = if (monoEnabled) 1 else 0
        val crossfeed = if (crossfeedEnabled) crossfeedStrength.coerceIn(0, 100) else 0
        setParameters(context, listOf(
            "stereo_balance=$balanceVal",
            "dolby_stereo_balance=$balanceVal",
            "mono_mix=$mono",
            "dolby_mono_mix=$mono",
            "crossfeed=$crossfeed",
            "dolby_crossfeed=$crossfeed"
        ))
        DolbyConstants.dlog(TAG, "spatial balance=$balanceVal mono=$mono crossfeed=$crossfeed")
    }

    fun syncDspVolume(context: Context, volumeStep: Int, boostEnabled: Boolean, boostStrength: Int) {
        val am = audioManager(context) ?: return
        val strength = boostStrength.coerceIn(0, 100)
        if (!boostEnabled || strength == 0) {
            am.setParameters("volume_change=$volumeStep;flags=8")
            return
        }
        val maxSteps = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val fraction = volumeStep.toFloat() / maxSteps.toFloat()
        val extra = (fraction * strength / 100f * maxSteps * 0.35f).toInt()
        val scaled = (volumeStep + extra).coerceIn(0, maxSteps)
        am.setParameters("volume_change=$scaled;flags=8")
        am.setParameters("dsp_loudness_boost=$strength")
        DolbyConstants.dlog(TAG, "dsp volume step=$volumeStep scaled=$scaled boost=$strength")
    }

    private fun setParameters(context: Context, params: List<String>) {
        val am = audioManager(context) ?: return
        params.forEach { param ->
            try {
                am.setParameters(param)
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "setParameters failed for $param: ${e.message}")
            }
        }
    }

    private const val TAG = "DolbyHalBridge"
}
