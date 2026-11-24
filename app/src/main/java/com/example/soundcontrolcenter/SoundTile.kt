package com.example.soundcontrolcenter

import android.app.PendingIntent
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

    override fun onStartListening() {
        super.onStartListening()
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
        val index = modes.indexOfFirst { it.id == audio.ringerMode }
        val next = modes[(index + 1) % modes.size]
        try {
            audio.ringerMode = next.id
            if (audio.ringerMode != next.id) {
                Toast.makeText(this, getString(R.string.mode_unavailable, getString(next.label)),
                    Toast.LENGTH_SHORT).show()
            }
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.permission_body, Toast.LENGTH_LONG).show()
            openPanel()
        }
        refresh()
    }

    @Suppress("DEPRECATION")
    private fun openPanel() {
        val panel = Intent(this, PanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, panel, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(panel)
        }
    }
}
