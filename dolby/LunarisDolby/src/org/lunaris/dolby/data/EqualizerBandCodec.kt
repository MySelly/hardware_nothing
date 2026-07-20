/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import org.lunaris.dolby.domain.models.BandGain
import org.lunaris.dolby.domain.models.BandMode

object EqualizerBandCodec {

    val BAND_FREQUENCIES_10 = listOf(32, 64, 125, 250, 500, 1000, 2250, 5000, 10000, 19688)

    val BAND_FREQUENCIES_15 = listOf(
        32, 47, 94, 141, 234, 469, 844, 1313, 2250, 3750, 5813, 9000, 11250, 13875, 19688
    )

    val BAND_FREQUENCIES_20 = listOf(
        32, 47, 141, 234, 328, 469, 656, 844, 1031, 1313,
        1688, 2250, 3000, 3750, 4688, 5813, 7125, 9000, 11250, 19688
    )

    private val TEN_BAND_INDICES = listOf(0, 2, 4, 6, 8, 10, 12, 14, 16, 18)
    private val FIFTEEN_BAND_INDICES = listOf(0, 1, 2, 3, 4, 5, 6, 8, 11, 12, 14, 15, 17, 18, 19)

    fun deserializeGains(gains: IntArray, bandMode: BandMode): List<BandGain> {
        val frequencies = when (bandMode) {
            BandMode.TEN_BAND -> BAND_FREQUENCIES_10
            BandMode.FIFTEEN_BAND -> BAND_FREQUENCIES_15
            BandMode.TWENTY_BAND -> BAND_FREQUENCIES_20
        }

        val indices = when (bandMode) {
            BandMode.TEN_BAND -> TEN_BAND_INDICES
            BandMode.FIFTEEN_BAND -> FIFTEEN_BAND_INDICES
            BandMode.TWENTY_BAND -> (0..19).toList()
        }

        return frequencies.mapIndexed { index, freq ->
            val gainIndex = indices.getOrNull(index) ?: index
            BandGain(frequency = freq, gain = gains.getOrElse(gainIndex) { 0 })
        }
    }

    fun serializeGains(bandGains: List<BandGain>, bandMode: BandMode): IntArray {
        val result = IntArray(20) { 0 }

        when (bandMode) {
            BandMode.TEN_BAND -> {
                TEN_BAND_INDICES.forEachIndexed { index, targetIndex ->
                    if (index < bandGains.size && targetIndex < 20) {
                        result[targetIndex] = bandGains[index].gain
                    }
                }
                for (i in 0 until 19 step 2) {
                    if (i + 2 < 20) {
                        result[i + 1] = (result[i] + result[i + 2]) / 2
                    }
                }
                result[19] = result[18]
            }
            BandMode.FIFTEEN_BAND -> {
                FIFTEEN_BAND_INDICES.forEachIndexed { index, targetIndex ->
                    if (index < bandGains.size && targetIndex < 20) {
                        result[targetIndex] = bandGains[index].gain
                    }
                }
                val missing = (0..19).filter { it !in FIFTEEN_BAND_INDICES }
                missing.forEach { idx ->
                    val prev = FIFTEEN_BAND_INDICES.filter { it < idx }.maxOrNull() ?: 0
                    val next = FIFTEEN_BAND_INDICES.filter { it > idx }.minOrNull() ?: 19

                    if (prev < idx && next > idx && prev < 20 && next < 20) {
                        val prevValue = result[prev]
                        val nextValue = result[next]
                        val ratio = (idx - prev).toFloat() / (next - prev)
                        result[idx] = (prevValue + ratio * (nextValue - prevValue)).toInt()
                    } else if (prev < 20) {
                        result[idx] = result[prev]
                    }
                }
            }
            BandMode.TWENTY_BAND -> {
                bandGains.forEachIndexed { index, bandGain ->
                    if (index < 20) {
                        result[index] = bandGain.gain
                    }
                }
            }
        }

        return result
    }
}
