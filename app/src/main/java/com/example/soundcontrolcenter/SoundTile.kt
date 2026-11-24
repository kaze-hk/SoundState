package com.example.soundcontrolcenter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.ContextCompat

class SoundTile : TileService() {
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val changes = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            confirmMode()
        }
    }
    private var listening = false
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Control? = null
    private var waitingForService = false
    private var queuedClicks = 0
    private val verify = Runnable { confirmMode(finalCheck = true) }

    override fun onTileAdded() {
        super.onTileAdded()
        tilePreferences().edit().putBoolean(TILE_ADDED, true).apply()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        tilePreferences().edit().putBoolean(TILE_ADDED, false).apply()
    }

    override fun onStartListening() {
        super.onStartListening()
        tilePreferences().edit().putBoolean(TILE_ADDED, true).apply()
        if (!listening) {
            ContextCompat.registerReceiver(this, changes, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),
                ContextCompat.RECEIVER_EXPORTED)
            listening = true
        }
        refresh()
    }

    override fun onStopListening() {
        handler.removeCallbacks(verify)
        pending = null
        queuedClicks = 0
        if (listening) unregisterReceiver(changes)
        listening = false
        super.onStopListening()
    }

    private fun refresh() {
        val current = modes.firstOrNull { it.id == audio.ringerMode } ?: return
        val name = getString(current.label)
        qsTile?.apply {
            state = if (current.id == AudioManager.RINGER_MODE_SILENT) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            icon = Icon.createWithResource(this@SoundTile, current.icon)
            label = if (Build.VERSION.SDK_INT >= 29) getString(R.string.app_name) else name
            if (Build.VERSION.SDK_INT >= 29) subtitle = name
            if (Build.VERSION.SDK_INT >= 30) stateDescription = name
            contentDescription = getString(R.string.app_name) + ", " + name
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        queuedClicks++
        if (pending != null || waitingForService) return
        if (Build.VERSION.SDK_INT >= 37 && !TileControlService.running &&
            tilePreferences().getBoolean(TileControlService.ENABLED, false)) {
            waitingForService = true
            val ready = object : android.os.ResultReceiver(handler) {
                override fun onReceiveResult(resultCode: Int, resultData: android.os.Bundle?) {
                    waitingForService = false
                    if (queuedClicks > 0 && pending == null) requestNextMode()
                }
            }
            try {
                startForegroundService(Intent(this, TileControlService::class.java)
                    .putExtra(TileControlService.READY, ready))
                return
            } catch (_: android.app.ForegroundServiceStartNotAllowedException) {
                waitingForService = false
            }
        }
        requestNextMode()
    }

    private fun requestNextMode() {
        queuedClicks--
        val current = audio.ringerMode
        val next = modes[(modes.indexOfFirst { it.id == current } + 1) % modes.size]
        pending = next
        try {
            audio.ringerMode = next.id
        } catch (_: SecurityException) {
            rejectChange(getString(R.string.permission_body))
            return
        }
        handler.postDelayed(verify, 750)
        confirmMode()
    }

    private fun confirmMode(finalCheck: Boolean = false) {
        refresh()
        val target = pending ?: return
        if (audio.ringerMode == target.id) {
            handler.removeCallbacks(verify)
            pending = null
            if (queuedClicks > 0) requestNextMode()
        } else if (finalCheck) {
            rejectChange(getString(R.string.mode_unavailable, getString(target.label)))
        }
    }

    private fun rejectChange(message: String) {
        pending = null
        queuedClicks = 0
        refresh()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}

internal const val TILE_ADDED = "added"
internal fun Context.tilePreferences() = getSharedPreferences("quick_settings", Context.MODE_PRIVATE)
