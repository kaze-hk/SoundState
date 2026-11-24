package com.example.soundcontrolcenter

import android.app.NotificationManager
import android.app.StatusBarManager
import android.content.ActivityNotFoundException
import android.provider.Settings
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.soundcontrolcenter.ui.theme.SoundControlCenterTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import android.service.quicksettings.TileService

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (Build.VERSION.SDK_INT >= 37) {
            if (granted) startForegroundService(Intent(this, TileControlService::class.java))
            else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private val policyAccess = mutableStateOf(false)

    private val tileOpenCount = mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == TileService.ACTION_QS_TILE_PREFERENCES) tileOpenCount.intValue++
    }

    override fun onResume() {
        super.onResume()
        policyAccess.value = getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
    }

    private val controlsEnabled = mutableStateOf(false)
    private val tileAdded = mutableStateOf(false)
    private val tileListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == TileControlService.ENABLED) controlsEnabled.value = prefs.getBoolean(key, false)
        if (key == TILE_ADDED) tileAdded.value = prefs.getBoolean(TILE_ADDED, false)
    }

    override fun onStart() {
        super.onStart()
        val prefs = tilePreferences()
        prefs.registerOnSharedPreferenceChangeListener(tileListener)
        tileAdded.value = prefs.getBoolean(TILE_ADDED, false)
        controlsEnabled.value = prefs.getBoolean(TileControlService.ENABLED, false)
    }

    override fun onStop() {
        tilePreferences().unregisterOnSharedPreferenceChangeListener(tileListener)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SoundControlCenterTheme {
                Surface(Modifier.fillMaxSize()) {
                    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                        val landscape = maxWidth > maxHeight
                        val prefs = remember { getSharedPreferences("layout", MODE_PRIVATE) }
                        val modesOnRight = remember { mutableStateOf(prefs.getBoolean("modes_on_right", false)) }
                        val scroll = rememberScrollState()
                        LaunchedEffect(tileOpenCount.intValue) {
                            if (tileOpenCount.intValue > 0) scroll.scrollTo(0)
                        }
                        Column(Modifier.widthIn(max = if (landscape) 960.dp else 560.dp).fillMaxWidth().verticalScroll(scroll)
                            .padding(horizontal = 24.dp, vertical = if (landscape) 8.dp else 24.dp),
                            verticalArrangement = Arrangement.spacedBy(if (landscape) 8.dp else 18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(painterResource(R.drawable.ic_wave), null, tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(R.string.app_name), Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
                                FilledTonalIconButton(onClick = { finish() }) {
                                    Icon(painterResource(R.drawable.ic_done), stringResource(R.string.done))
                                }
                            }
                            SoundPanel(landscape, modesOnRight.value)
                            Text(stringResource(R.string.quick_settings), style = MaterialTheme.typography.titleLarge)
                            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Row(Modifier.fillMaxWidth().toggleable(value = modesOnRight.value, role = Role.Switch) { right ->
                                        modesOnRight.value = right
                                        prefs.edit().putBoolean("modes_on_right", right).apply()
                                    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(stringResource(R.string.landscape_modes_right), Modifier.weight(1f),
                                            style = MaterialTheme.typography.titleMedium)
                                        Switch(checked = modesOnRight.value, onCheckedChange = null)
                                    }
                                    HorizontalDivider()
                                    SettingRow(stringResource(R.string.tile_title), stringResource(R.string.tile_summary)) {
                                        if (!tileAdded.value) {
                                            FilledTonalButton(onClick = { addTile() }) { Text(stringResource(R.string.add_tile)) }
                                        }
                                    }
                                    HorizontalDivider()
                                    SettingRow(stringResource(R.string.dnd_title), stringResource(R.string.silent_dnd_note)) {
                                        if (!policyAccess.value) {
                                            FilledTonalButton(onClick = {
                                                try {
                                                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                                                } catch (_: ActivityNotFoundException) {
                                                    Toast.makeText(this@MainActivity, R.string.permission_hint, Toast.LENGTH_LONG).show()
                                                }
                                            }) { Text(stringResource(R.string.open_settings_short)) }
                                        }
                                    }
                                    if (Build.VERSION.SDK_INT >= 37) {
                                        HorizontalDivider()
                                        val enabled = controlsEnabled.value
                                        SettingRow(
                                            title = stringResource(R.string.background_controls),
                                            summary = stringResource(R.string.tile_background_summary),
                                            modifier = Modifier.toggleable(value = enabled, role = Role.Switch) { turnOn ->
                                                val service = Intent(this@MainActivity, TileControlService::class.java)
                                                if (!turnOn) {
                                                    startService(service.setAction(TileControlService.STOP))
                                                } else if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                                                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                    startForegroundService(service)
                                                } else {
                                                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                                }
                                            }
                                        ) {
                                            Switch(checked = enabled, onCheckedChange = null)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, SoundTile::class.java), getString(R.string.app_name),
                Icon.createWithResource(this, R.drawable.ic_wave), mainExecutor
            ) { result ->
                if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                    tilePreferences().edit().putBoolean(TILE_ADDED, true).apply()
                }
                val message = when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.tile_added
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.tile_exists
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> R.string.tile_cancelled
                    else -> R.string.manual_tile
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        } else Toast.makeText(this, R.string.manual_tile, Toast.LENGTH_LONG).show()
    }

}

@Composable
private fun SettingRow(title: String, summary: String, modifier: Modifier = Modifier, action: @Composable () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            action()
        }
        Text(summary, style = MaterialTheme.typography.bodyMedium)
    }
}
