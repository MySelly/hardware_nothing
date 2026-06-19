/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.utils

import android.util.Base64
import org.json.JSONObject
import org.lunaris.dolby.domain.models.BandGain
import org.lunaris.dolby.domain.models.BandMode
import org.lunaris.dolby.domain.models.EqualizerPreset

object PresetShareCodec {

    private const val PREFIX = "LDOLBY1:"

    fun encode(preset: EqualizerPreset): String {
        val json = JSONObject().apply {
            put("name", preset.name)
            put("bandMode", preset.bandMode.value)
            val gains = org.json.JSONArray()
            preset.bandGains.forEach { band ->
                gains.put(JSONObject().apply {
                    put("frequency", band.frequency)
                    put("gain", band.gain)
                })
            }
            put("bandGains", gains)
        }
        return PREFIX + Base64.encodeToString(
            json.toString().toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP or Base64.URL_SAFE
        )
    }

    fun decode(payload: String): EqualizerPreset {
        val trimmed = payload.trim()
        val encoded = if (trimmed.startsWith(PREFIX)) {
            trimmed.removePrefix(PREFIX)
        } else {
            trimmed
        }
        val jsonString = String(
            Base64.decode(encoded, Base64.NO_WRAP or Base64.URL_SAFE),
            Charsets.UTF_8
        )
        val json = JSONObject(jsonString)
        val bandMode = BandMode.fromValue(json.getString("bandMode"))
        val gainsArray = json.getJSONArray("bandGains")
        val bandGains = buildList {
            for (i in 0 until gainsArray.length()) {
                val item = gainsArray.getJSONObject(i)
                add(BandGain(item.getInt("frequency"), item.getInt("gain")))
            }
        }
        return EqualizerPreset(
            name = json.getString("name"),
            bandGains = bandGains,
            isUserDefined = true,
            bandMode = bandMode
        )
    }

    fun isShareCode(text: String): Boolean = text.trim().startsWith(PREFIX)
}
