package com.example.soundcontrolcenter

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal data class Control(val label: Int, val icon: Int, val id: Int)

private val channels = listOf(
    Control(R.string.media_volume, R.drawable.ic_music, AudioManager.STREAM_MUSIC),
    Control(R.string.ring_volume, R.drawable.ic_bell, AudioManager.STREAM_RING),
    Control(R.string.alarm_volume, R.drawable.ic_alarm, AudioManager.STREAM_ALARM)
)
internal val modes = listOf(
    Control(R.string.ring, R.drawable.ic_bell, AudioManager.RINGER_MODE_NORMAL),
    Control(R.string.vibrate, R.drawable.ic_vibrate, AudioManager.RINGER_MODE_VIBRATE),
    Control(R.string.silent, R.drawable.ic_silent, AudioManager.RINGER_MODE_SILENT)
)

@Composable
internal fun SoundPanel(landscape: Boolean, modesOnRight: Boolean) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val permissionHint = stringResource(R.string.permission_hint)
    val owner = LocalLifecycleOwner.current
    val values = remember { mutableStateMapOf<Int, Int>() }
    var mode by remember { mutableIntStateOf(audio.ringerMode) }
    var permissionNeeded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    fun refresh() {
        mode = audio.ringerMode
        channels.forEach { (_, _, id) -> values[id] = audio.getStreamVolume(id) }
    }
    fun change(action: () -> Unit) {
        message = ""
        try {
            action()
        } catch (_: SecurityException) {
            permissionNeeded = true
        }
        refresh()
    }
    LaunchedEffect(owner, audio) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                refresh()
                delay(500)
            }
        }
    }
    val selectMode: (Int, String) -> Unit = { value, unavailable ->
        change { audio.ringerMode = value }
        if (!permissionNeeded && audio.ringerMode != value) message = unavailable
    }
    val setVolume: (Int, Int) -> Unit = { id, index -> change { audio.setStreamVolume(id, index, 0) } }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(if (landscape) 12.dp else 20.dp)) {
            if (landscape) {
                val rail: @Composable () -> Unit = {
                    Column(Modifier.width(72.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        modes.forEach { ModeButton(it, mode == it.id, Modifier.fillMaxWidth(), selectMode) }
                    }
                }
                val railFirst = modesOnRight == (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (railFirst) rail()
                    VolumeControls(audio, values, true, Modifier.weight(1f), setVolume)
                    if (!railFirst) rail()
                }
            } else {
                Text(stringResource(R.string.panel), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    modes.forEach { ModeButton(it, mode == it.id, Modifier.weight(1f), selectMode) }
                }
                Spacer(Modifier.height(16.dp))
                VolumeControls(audio, values, false, onVolume = setVolume)
            }
            if (message.isNotEmpty()) Text(message)
        }
    }
    if (permissionNeeded) AlertDialog(onDismissRequest = { permissionNeeded = false },
        title = { Text(stringResource(R.string.permission_title)) },
        text = { Text(stringResource(R.string.permission_body)) },
        confirmButton = { TextButton(onClick = {
            permissionNeeded = false
            try {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                message = permissionHint
            }
        }) { Text(stringResource(R.string.open_settings)) } },
        dismissButton = { TextButton(onClick = { permissionNeeded = false }) { Text(stringResource(android.R.string.cancel)) } })
}

@Composable
private fun ModeButton(control: Control, selected: Boolean, modifier: Modifier, onSelect: (Int, String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = stringResource(control.label)
    val unavailable = stringResource(R.string.mode_unavailable, name)
    Box(modifier.clip(CircleShape)
        .background(if (selected) colors.primary else colors.secondaryContainer)
        .selectable(selected, role = Role.RadioButton, onClick = { onSelect(control.id, unavailable) })
        .heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        Icon(painterResource(control.icon), name, tint = if (selected) colors.onPrimary else colors.onSecondaryContainer)
    }
}

@Composable
private fun VolumeControls(audio: AudioManager, values: Map<Int, Int>, compact: Boolean,
    modifier: Modifier = Modifier, onVolume: (Int, Int) -> Unit) {
    Column(modifier) {
        channels.forEach { (label, icon, id) ->
            val name = stringResource(label)
            val min = if (Build.VERSION.SDK_INT >= 28) audio.getStreamMinVolume(id) else 0
            val max = audio.getStreamMaxVolume(id)
            val level = values[id] ?: audio.getStreamVolume(id)
            if (!compact) Text(name, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (compact) Text(name, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Slider(value = level.toFloat(), onValueChange = { position ->
                    val index = position.roundToInt()
                    if (index != level) onVolume(id, index)
                }, modifier = Modifier.weight(3f).semantics { contentDescription = name },
                    valueRange = min.toFloat()..max.coerceAtLeast(min + 1).toFloat(),
                    enabled = !audio.isVolumeFixed && max > min)
            }
        }
    }
}
