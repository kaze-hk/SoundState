package com.example.soundcontrolcenter

import android.app.NotificationManager
import android.app.StatusBarManager
import android.content.ActivityNotFoundException
import android.provider.Settings
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.accessibility.AccessibilityManager
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import android.content.SharedPreferences
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.soundcontrolcenter.ui.theme.SoundControlCenterTheme
import kotlin.math.roundToInt
import kotlin.math.sin

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
                    Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Icon(painterResource(R.drawable.ic_wave), null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayLarge)
                            Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.titleMedium)
                            Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                                    Waveform()
                                    Spacer(Modifier.height(20.dp))
                                    Text(stringResource(R.string.panel), style = MaterialTheme.typography.headlineMedium)
                                    Text(stringResource(R.string.panel_summary), modifier = Modifier.padding(vertical = 8.dp))
                                    Button(onClick = { startActivity(Intent(this@MainActivity, PanelActivity::class.java)) }) {
                                        Text(stringResource(R.string.open_panel))
                                    }
                                }
                            }
                            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                                    Text(stringResource(R.string.quick_settings), style = MaterialTheme.typography.titleLarge)
                                    Text(stringResource(R.string.tile_summary), Modifier.padding(vertical = 8.dp))
                                    Text(stringResource(R.string.silent_dnd_note))
                                    if (!policyAccess.value) {
                                        Text(stringResource(R.string.permission_body))
                                        TextButton(onClick = {
                                            try {
                                                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                                            } catch (_: ActivityNotFoundException) {
                                                Toast.makeText(this@MainActivity, R.string.permission_hint, Toast.LENGTH_LONG).show()
                                            }
                                        }) { Text(stringResource(R.string.open_settings)) }
                                    }
                                    if (Build.VERSION.SDK_INT >= 37) {
                                        val enabled = controlsEnabled.value
                                        Text(stringResource(if (enabled) R.string.controls_active else R.string.controls_hint),
                                            Modifier.padding(top = 8.dp))
                                        OutlinedButton(onClick = {
                                            val service = Intent(this@MainActivity, TileControlService::class.java)
                                            if (enabled) {
                                                tilePreferences().edit().putBoolean(TileControlService.ENABLED, false).apply()
                                                stopService(service)
                                            }
                                            else if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                                                android.content.pm.PackageManager.PERMISSION_GRANTED) startForegroundService(service)
                                            else notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        }) {
                                            Text(stringResource(if (enabled) R.string.stop_controls else R.string.start_controls))
                                        }
                                    }
                                    if (!tileAdded.value) {
                                        OutlinedButton(onClick = { addTile() }) { Text(stringResource(R.string.add_tile)) }
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
private fun Waveform() {
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    Canvas(Modifier.fillMaxWidth().height(72.dp)) {
        val spacing = size.width / 48
        repeat(48) { i ->
            val x = (i + 0.5f) * spacing
            val envelope = sin(Math.PI * (i + 1) / 49).toFloat()
            val amplitude = size.height * (.04f + .42f * envelope * (0.57f + .43f * sin(i * .49f)))
            drawLine(ink, Offset(x, center.y - amplitude), Offset(x, center.y + amplitude),
                spacing * .52f, StrokeCap.Round)
        }
    }
}

class PanelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setGravity(Gravity.CENTER)
        val metrics = resources.displayMetrics
        window.setLayout(minOf(metrics.widthPixels - (24 * metrics.density).toInt(),
            (440 * metrics.density).toInt()), WindowManager.LayoutParams.WRAP_CONTENT)
        setFinishOnTouchOutside(true)
        val decor = window.decorView
        decor.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (applyHeight()) return false
                decor.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        decor.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (decor.isLaidOut && positionLoaded && bottom - top != oldBottom - oldTop) applyHeight()
        }
        setContent {
            SoundControlCenterTheme {
                SoundPanel(::scheduleDismiss, ::movePanel, ::saveHeight)
            }
        }
    }

    private val placement by lazy { getSharedPreferences("panel_position", MODE_PRIVATE) }
    private var heightFraction = 0f
    private var positionLoaded = false

    private fun travel(): Int {
        val frame = Rect()
        window.decorView.getWindowVisibleDisplayFrame(frame)
        return ((frame.height() - window.decorView.height) / 2).coerceAtLeast(0)
    }

    private fun applyHeight(): Boolean {
        if (!positionLoaded) {
            heightFraction = placement.getFloat("height_fraction", 0f).coerceIn(-1f, 1f)
            positionLoaded = true
        }
        val offset = (heightFraction * travel()).roundToInt()
        val params = window.attributes
        if (params.y != offset) {
            params.y = offset
            window.attributes = params
            return true
        }
        return false
    }

    private fun movePanel(delta: Float) {
        val range = travel()
        if (range == 0) return
        heightFraction = (heightFraction + delta / range).coerceIn(-1f, 1f)
        applyHeight()
    }

    private fun saveHeight() {
        placement.edit().putFloat("height_fraction", heightFraction).apply()
        scheduleDismiss()
    }

    private val dismiss = Runnable { finish() }
    private var touching = false

    private fun scheduleDismiss() {
        val view = window.decorView
        view.removeCallbacks(dismiss)
        if (!view.hasWindowFocus() || touching || isFinishing) return
        val accessibility = getSystemService(AccessibilityManager::class.java)
        val timeout = if (Build.VERSION.SDK_INT >= 29) {
            accessibility.getRecommendedTimeoutMillis(6000,
                AccessibilityManager.FLAG_CONTENT_ICONS or AccessibilityManager.FLAG_CONTENT_CONTROLS)
        } else if (accessibility.isTouchExplorationEnabled) {
            return
        } else 6000
        view.postDelayed(dismiss, timeout.toLong())
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> touching = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touching = false
        }
        scheduleDismiss()
        return super.dispatchTouchEvent(event)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        scheduleDismiss()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        touching = false
        scheduleDismiss()
    }

    override fun onDestroy() {
        window.decorView.removeCallbacks(dismiss)
        super.onDestroy()
    }
}
