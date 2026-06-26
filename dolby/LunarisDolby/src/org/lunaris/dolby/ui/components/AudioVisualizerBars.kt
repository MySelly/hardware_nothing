/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.components

import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import org.lunaris.dolby.DolbyConstants
import kotlin.math.max

@Composable
fun AudioVisualizerBars(
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    enabled: Boolean = true,
    useFft: Boolean = false
) {
    var levels by remember { mutableStateOf(FloatArray(barCount) { 0.1f }) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(enabled, useFft) {
        if (!enabled) {
            return@DisposableEffect onDispose { }
        }
        var visualizer: Visualizer? = null
        try {
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (useFft || waveform == null) return
                            updateLevels(waveform, barCount, mainHandler) { levels = it }
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (!useFft || fft == null) return
                            val next = FloatArray(barCount) { 0.05f }
                            val usable = minOf(fft.size / 2, barCount)
                            for (i in 0 until usable) {
                                val real = fft[2 * i].toInt()
                                val imag = fft[2 * i + 1].toInt()
                                val magnitude = kotlin.math.sqrt((real * real + imag * imag).toFloat())
                                next[i] = (magnitude / 128f).coerceIn(0.05f, 1f)
                            }
                            mainHandler.post { levels = next }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    !useFft,
                    useFft
                )
                this.enabled = true
            }
        } catch (e: Exception) {
            DolbyConstants.dlog("AudioVisualizerBars", "Visualizer unavailable: ${e.message}")
        }
        onDispose {
            try {
                visualizer?.enabled = false
                visualizer?.release()
            } catch (_: Exception) {
            }
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        if (!enabled) return@Canvas
        val barWidth = size.width / (barCount * 1.6f)
        val gap = barWidth * 0.6f
        levels.forEachIndexed { index, level ->
            val x = index * (barWidth + gap)
            drawRoundRect(
                color = track,
                topLeft = Offset(x, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
            val barHeight = size.height * level
            drawRoundRect(
                color = primary,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}

private fun updateLevels(
    waveform: ByteArray,
    barCount: Int,
    handler: Handler,
    onUpdate: (FloatArray) -> Unit
) {
    val chunk = max(1, waveform.size / barCount)
    val next = FloatArray(barCount)
    for (i in 0 until barCount) {
        var peak = 0
        val start = i * chunk
        val end = minOf(start + chunk, waveform.size)
        for (j in start until end) {
            peak = max(peak, kotlin.math.abs(waveform[j].toInt()))
        }
        next[i] = (peak / 128f).coerceIn(0.05f, 1f)
    }
    handler.post { onUpdate(next) }
}
