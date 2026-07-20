/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lunaris.dolby.domain.models.BandGain
import org.lunaris.dolby.domain.models.BandMode
import org.lunaris.dolby.domain.models.EqualizerPreset
import org.lunaris.dolby.utils.PresetShareCodec

class PresetShareCodecTest {

    @Test
    fun encodeDecode_roundtrip_preservesPreset() {
        val original = EqualizerPreset(
            name = "Share Test",
            bandGains = listOf(
                BandGain(31, 10),
                BandGain(62, -20),
                BandGain(125, 0)
            ),
            isUserDefined = true,
            bandMode = BandMode.TEN_BAND
        )
        val code = PresetShareCodec.encode(original)
        assertTrue(PresetShareCodec.isShareCode(code))
        val restored = PresetShareCodec.decode(code)
        assertEquals(original.name, restored.name)
        assertEquals(original.bandMode, restored.bandMode)
        assertEquals(original.bandGains, restored.bandGains)
        assertTrue(restored.isUserDefined)
    }

    @Test
    fun isShareCode_detectsPrefix() {
        assertTrue(PresetShareCodec.isShareCode("LDOLBY1:abc"))
        assertTrue(PresetShareCodec.isShareCode("  LDOLBY1:xyz  "))
        assertFalse(PresetShareCodec.isShareCode("not-a-share-code"))
        assertFalse(PresetShareCodec.isShareCode(""))
    }

    @Test
    fun decode_acceptsPayloadWithoutPrefix() {
        val preset = EqualizerPreset(
            name = "Bare",
            bandGains = listOf(BandGain(1000, 5)),
            bandMode = BandMode.FIFTEEN_BAND
        )
        val encoded = PresetShareCodec.encode(preset).removePrefix("LDOLBY1:")
        val restored = PresetShareCodec.decode(encoded)
        assertEquals("Bare", restored.name)
        assertEquals(BandMode.FIFTEEN_BAND, restored.bandMode)
        assertEquals(5, restored.bandGains.single().gain)
    }
}
