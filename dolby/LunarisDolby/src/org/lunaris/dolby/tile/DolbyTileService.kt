/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import org.lunaris.dolby.R
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.service.DolbyEffectService

class DolbyTileService : TileService() {

    private val repository by lazy { DolbyRepository(applicationContext) }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        unlockAndRun {
            val enabled = repository.getDolbyEnabled()
            val newState = !enabled
            repository.setDolbyEnabled(newState)
            if (newState) {
                DolbyEffectService.start(applicationContext)
            } else {
                DolbyEffectService.stop(applicationContext)
            }
            updateTile()
        }
    }

    override fun onLongClick() {
        unlockAndRun {
            val nextProfile = repository.cycleToNextProfile()
            if (!repository.getDolbyEnabled()) {
                repository.setDolbyEnabled(true)
                DolbyEffectService.start(applicationContext)
            }
            val profileName = repository.getProfileDisplayName(nextProfile)
            Toast.makeText(
                applicationContext,
                getString(R.string.qs_tile_profile_switched, profileName),
                Toast.LENGTH_SHORT
            ).show()
            updateTile()
        }
    }

    private fun updateTile() {
        qsTile?.apply {
            val enabled = repository.getDolbyEnabled()
            val profileName = getProfileName()
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.dolby_title)
            subtitle = if (enabled) {
                profileName
            } else {
                getString(R.string.qs_tile_tap_to_enable)
            }
            stateDescription = if (enabled) {
                getString(R.string.qs_tile_state_on, profileName)
            } else {
                getString(R.string.qs_tile_state_off)
            }
            updateTile()
        }
    }

    private fun getProfileName(): String {
        return repository.getProfileDisplayName(repository.getCurrentProfile())
    }
}
