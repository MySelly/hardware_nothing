/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nothing.thirdparty

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log

class IGlyphServiceImpl(private val context: Context) : IGlyphService.Stub() {
    private var glyphService: IGlyphService? = null

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                glyphService = IGlyphService.Stub.asInterface(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                glyphService = null
            }
        }

    init {
        bindGlyphService()
    }

    private fun bindGlyphService() {
        val component = if (Build.DEVICE == "Spacewar") {
            ComponentName(
                "org.aspends.nglyphs",
                "org.aspends.nglyphs.services.ThirdPartyGlyphService"
            )
        } else {
            ComponentName(
                "co.aospa.glyph",
                "co.aospa.glyph.Services.ThirdPartyService"
            )
        }
        val intent = Intent("com.nothing.thirdparty.IGlyphService").apply {
            this.component = component
        }
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun setFrameColors(iArray: IntArray?) {
        if (iArray != null) {
            glyphService?.setFrameColors(iArray)
        }
    }

    override fun openSession() {
        glyphService?.setFrameColors(intArrayOf(0, 0, 0, 0, 0))
    }

    override fun closeSession() {
        glyphService?.setFrameColors(intArrayOf(0, 0, 0, 0, 0))
    }

    override fun register(str: String) = true

    override fun registerSDK(str1: String, str2: String) = true
}
