package com.example.soundcontrolcenter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.ContextCompat

class SoundTile : TileService() {
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val changes = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }
    private var listening = false

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
        val initial = audio.ringerMode
        val index = modes.indexOfFirst { it.id == initial }
        var denied = false
        for (step in 1 until modes.size) {
            val next = modes[(index + step) % modes.size]
            try {
                audio.ringerMode = next.id
            } catch (_: SecurityException) {
                denied = true
            }
            if (audio.ringerMode != initial) {
                refresh()
                return
            }
        }
        refresh()
        val message = if (denied) getString(R.string.permission_body)
            else getString(R.string.mode_unavailable, getString(modes[(index + 1) % modes.size].label))
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}

internal const val TILE_ADDED = "added"
internal fun Context.tilePreferences() = getSharedPreferences("quick_settings", Context.MODE_PRIVATE)
