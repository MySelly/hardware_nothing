/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.tile

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import org.lunaris.dolby.R
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.data.ProfileChangeHistoryManager
import org.lunaris.dolby.service.DolbyEffectService

class DolbyTileService : TileService() {

    private val repository by lazy { DolbyRepository(applicationContext) }
    private val handler = Handler(Looper.getMainLooper())
    private var profileCycleRunnable: Runnable? = null
    private var waitingForDisableConfirm = false

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        unlockAndRun {
            val enabled = repository.getDolbyEnabled()

            if (!enabled) {
                cancelPendingProfileCycle()
                repository.setDolbyEnabled(true)
                DolbyEffectService.start(applicationContext)
                updateTile()
                return@unlockAndRun
            }

            if (waitingForDisableConfirm) {
                cancelPendingProfileCycle()
                repository.setDolbyEnabled(false)
                DolbyEffectService.stop(applicationContext)
                updateTile()
                return@unlockAndRun
            }

            waitingForDisableConfirm = true
            Toast.makeText(
                applicationContext,
                getString(R.string.qs_tile_tap_again_to_disable),
                Toast.LENGTH_SHORT
            ).show()
            val cycleRunnable = Runnable {
                waitingForDisableConfirm = false
                profileCycleRunnable = null
                val nextProfile = repository.cycleToNextProfile()
                ProfileChangeHistoryManager(applicationContext).recordChange(
                    nextProfile,
                    org.lunaris.dolby.domain.models.ProfileChangeSource.QS_TILE,
                    repository.getProfileDisplayName(nextProfile)
                )
                Toast.makeText(
                    applicationContext,
                    getString(R.string.qs_tile_profile_switched, repository.getProfileDisplayName(nextProfile)),
                    Toast.LENGTH_SHORT
                ).show()
                updateTile()
            }
            profileCycleRunnable = cycleRunnable
            handler.postDelayed(cycleRunnable, DOUBLE_TAP_WINDOW_MS)
        }
    }

    private fun cancelPendingProfileCycle() {
        waitingForDisableConfirm = false
        profileCycleRunnable?.let { handler.removeCallbacks(it) }
        profileCycleRunnable = null
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

    companion object {
        private const val DOUBLE_TAP_WINDOW_MS = 350L
    }
}
