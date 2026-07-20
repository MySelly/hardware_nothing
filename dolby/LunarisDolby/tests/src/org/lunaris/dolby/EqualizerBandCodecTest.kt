/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lunaris.dolby.data.EqualizerBandCodec
import org.lunaris.dolby.domain.models.BandGain
import org.lunaris.dolby.domain.models.BandMode

/**
 * Pure-logic serialize/deserialize roundtrip tests for [EqualizerBandCodec].
 */
class EqualizerBandCodecTest {

    @Test
    fun twentyBand_roundtrip_preservesGains() {
        val original = (0 until 20).map { BandGain(EqualizerBandCodec.BAND_FREQUENCIES_20[it], it * 10) }
        val serialized = EqualizerBandCodec.serializeGains(original, BandMode.TWENTY_BAND)
        val restored = EqualizerBandCodec.deserializeGains(serialized, BandMode.TWENTY_BAND)
        assertEquals(20, restored.size)
        original.forEachIndexed { index, band ->
            assertEquals(band.frequency, restored[index].frequency)
            assertEquals(band.gain, restored[index].gain)
        }
    }

    @Test
    fun tenBand_roundtrip_preservesIndexedGains() {
        val gains = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
        val original = EqualizerBandCodec.BAND_FREQUENCIES_10.mapIndexed { i, freq ->
            BandGain(freq, gains[i])
        }
        val serialized = EqualizerBandCodec.serializeGains(original, BandMode.TEN_BAND)
        assertEquals(20, serialized.size)
        val restored = EqualizerBandCodec.deserializeGains(serialized, BandMode.TEN_BAND)
        assertEquals(10, restored.size)
        gains.forEachIndexed { index, gain ->
            assertEquals(gain, restored[index].gain)
        }
    }

    @Test
    fun fifteenBand_deserialize_usesCorrectBandCount() {
        val flat = IntArray(20) { it * 5 }
        val bands = EqualizerBandCodec.deserializeGains(flat, BandMode.FIFTEEN_BAND)
        assertEquals(15, bands.size)
        assertEquals(EqualizerBandCodec.BAND_FREQUENCIES_15, bands.map { it.frequency })
        assertTrue(bands.all { it.gain % 5 == 0 })
    }
}
