package com.qingal.dizzystop

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class MotionCueTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { handleClick() }
        } else {
            handleClick()
        }
    }

    private fun handleClick() {
        if (MotionCueService.isRunning) {
            startService(MotionCueService.stopIntent(this))
            setTileState(Tile.STATE_INACTIVE)
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            setTileState(Tile.STATE_INACTIVE)
            openPermissionScreen()
            return
        }

        runCatching {
            ContextCompat.startForegroundService(this, MotionCueService.startIntent(this))
            setTileState(Tile.STATE_ACTIVE)
        }.onFailure {
            setTileState(Tile.STATE_INACTIVE)
            openPermissionScreen()
        }
    }

    private fun updateTile() {
        val state = if (MotionCueService.isRunning) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }
        setTileState(state)
    }

    private fun setTileState(state: Int) {
        qsTile?.apply {
            this.state = state
            label = getString(R.string.quick_settings_tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(
                    when {
                        state == Tile.STATE_ACTIVE -> R.string.quick_settings_tile_on
                        !Settings.canDrawOverlays(this@MotionCueTileService) ->
                            R.string.quick_settings_tile_permission
                        else -> R.string.quick_settings_tile_off
                    }
                )
            }
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openPermissionScreen() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_OVERLAY_PERMISSION, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                2,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            requestListeningState(
                context,
                ComponentName(context, MotionCueTileService::class.java)
            )
        }
    }
}
