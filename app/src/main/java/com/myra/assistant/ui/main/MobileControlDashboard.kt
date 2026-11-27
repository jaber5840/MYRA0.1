package com.myra.assistant.ui.main

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.StopScreenShare
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myra.assistant.model.AppCommand
import com.myra.assistant.model.CommandHistoryItem
import com.myra.assistant.model.PendingConfirmation
import com.myra.assistant.ui.theme.JetBrainsMonoFontFamily
import com.myra.assistant.ui.theme.LocalMyraPalette
import com.myra.assistant.ui.theme.MyraColors
import com.myra.assistant.ui.theme.PlusJakartaFontFamily
import com.myra.assistant.ui.theme.SpaceGroteskFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun AudioWaveformStrip(
    amplitude: Float,
    orbState: OrbAnimationView.State,
    isSpeaking: Boolean,
    isMuted: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalMyraPalette.current
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val thinkingPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinkingDots"
    )

    if (orbState == OrbAnimationView.State.THINKING) {
        Row(
            modifier = modifier.height(26.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (dotIdx in 0 until 3) {
                val active = abs(thinkingPulse - dotIdx) < 0.7f
                Box(
                    modifier = Modifier
                        .size(if (active) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MyraColors.Cyan else MyraColors.Cyan.copy(alpha = 0.35f)
                        )
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "THINKING",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                color = MyraColors.Cyan
            )
        }
        return
    }

    val barCount = 15
    val activeAmp = when {
        isMuted -> 0.10f
        isSpeaking -> (0.45f + amplitude * 0.9f).coerceIn(0.35f, 1.0f)
        else -> (0.22f + amplitude * 0.85f).coerceIn(0.18f, 1.0f)
    }

    Row(
        modifier = modifier.height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val distFromCenter = abs(i - barCount / 2) / (barCount / 2f)
            val envelope = 1f - distFromCenter * 0.55f
            val wave = abs(sin(phase + i * 0.55f))
            val fraction = (0.18f + wave * activeAmp * envelope).coerceIn(0.16f, 1.0f)
            val barColor = if (isMuted) palette.textMuted else accentColor.copy(alpha = 0.72f + 0.28f * fraction)

            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height((24.dp * fraction).coerceAtLeast(4.dp))
                    .clip(RoundedCornerShape(50))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun ActionConfirmationDialog(
    pending: PendingConfirmation,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val palette = LocalMyraPalette.current
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = palette.surfaceCard,
        title = {
            Text(
                text = pending.title,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
        },
        text = {
            Text(
                text = pending.description,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 14.sp,
                color = palette.textSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MyraColors.Crimson),
                modifier = Modifier.testTag("confirm_pending_action_button")
            ) {
                Text(
                    text = pending.confirmButtonLabel,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("cancel_pending_action_button")
            ) {
                Text("CANCEL", color = palette.textSecondary)
            }
        }
    )
}

private data class ControlTileSpec(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val isHighlighted: Boolean = false,
    val command: AppCommand
)

@Composable
fun MobileControlDashboardContent(
    telemetry: HomeTelemetryState,
    isRunning: Boolean,
    isScreenSharing: Boolean,
    isFlashlightOn: Boolean,
    accentColor: Color,
    commandHistory: List<CommandHistoryItem>,
    onStartStopClick: () -> Unit,
    onScreenShareClick: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenPermissionsSettings: () -> Unit,
    onExecuteQuickCommand: (AppCommand) -> Unit,
    onClearHistory: () -> Unit
) {
    val palette = LocalMyraPalette.current

    val controlTiles = listOf(
        ControlTileSpec(
            icon = if (isFlashlightOn) Icons.Rounded.FlashlightOff else Icons.Rounded.FlashlightOn,
            title = if (isFlashlightOn) "Torch Off" else "Torch On",
            subtitle = if (isFlashlightOn) "Flashlight active" else "Turn flashlight on",
            isHighlighted = isFlashlightOn,
            command = AppCommand(if (isFlashlightOn) AppCommand.TYPE_FLASHLIGHT_OFF else AppCommand.TYPE_FLASHLIGHT_ON)
        ),
        ControlTileSpec(
            icon = Icons.Rounded.VolumeUp,
            title = "Volume +",
            subtitle = "Increase media sound",
            command = AppCommand(AppCommand.TYPE_VOLUME_UP)
        ),
        ControlTileSpec(
            icon = Icons.Rounded.VolumeDown,
            title = "Volume -",
            subtitle = "Decrease media sound",
            command = AppCommand(AppCommand.TYPE_VOLUME_DOWN)
        ),
        ControlTileSpec(
            icon = Icons.Rounded.BrightnessHigh,
            title = "Bright +",
            subtitle = "Raise screen brightness",
            command = AppCommand(AppCommand.TYPE_BRIGHTNESS_UP)
        ),
        ControlTileSpec(
            icon = Icons.Rounded.BrightnessLow,
            title = "Bright -",
            subtitle = "Lower screen brightness",
            command = AppCommand(AppCommand.TYPE_BRIGHTNESS_DOWN)
        ),
        ControlTileSpec(
            icon = Icons.Rounded.CameraAlt,
            title = "Back Cam",
            subtitle = "Open rear camera",
            command = AppCommand(AppCommand.TYPE_OPEN_CAMERA_BACK)
        ),
        ControlTileSpec(
            icon = Icons.Rounded.CameraFront,
            title = "Front Cam",
            subtitle = "Open selfie camera",
            command = AppCommand(AppCommand.TYPE_OPEN_CAMERA_FRONT)
        ),
        ControlTileSpec(
            icon = Icons.Rounded.PhotoCamera,
            title = "Take Photo",
            subtitle = "Capture with camera",
            command = AppCommand(AppCommand.TYPE_TAKE_PHOTO)
        ),
        ControlTileSpec(
            icon = Icons.Rounded.Screenshot,
            title = "Screenshot",
            subtitle = "Capture phone screen",
            command = AppCommand(AppCommand.TYPE_TAKE_SCREENSHOT)
        ),
        ControlTileSpec(
            icon = Icons.Rounded.MusicNote,
            title = "Music",
            subtitle = "Play / Pause media",
            command = AppCommand(AppCommand.TYPE_MUSIC_PLAY_PAUSE)
        ),
        ControlTileSpec(
            icon = Icons.Rounded.WbSunny,
            title = "Weather",
            subtitle = "Live local forecast",
            command = AppCommand(AppCommand.TYPE_WEATHER)
        ),
        ControlTileSpec(
            icon = Icons.Rounded.BatteryChargingFull,
            title = "Battery",
            subtitle = "${telemetry.batteryPercent}% charge info",
            command = AppCommand(AppCommand.TYPE_BATTERY_INFO)
        ),
        ControlTileSpec(
            icon = Icons.Rounded.PhoneAndroid,
            title = "Device Info",
            subtitle = "Model, Android & RAM",
            command = AppCommand(AppCommand.TYPE_DEVICE_INFO)
        ),
        ControlTileSpec(
            icon = Icons.Rounded.Dialpad,
            title = "Dialer",
            subtitle = "Open phone keypad",
            command = AppCommand(AppCommand.TYPE_OPEN_DIALER)
        ),
        ControlTileSpec(
            icon = Icons.Rounded.Contacts,
            title = "Contacts",
            subtitle = "Browse phonebook",
            command = AppCommand(AppCommand.TYPE_OPEN_CONTACTS)
        ),
        ControlTileSpec(
            icon = Icons.Rounded.Alarm,
            title = "7 AM Alarm",
            subtitle = "Set morning alarm",
            command = AppCommand(AppCommand.TYPE_SET_ALARM, mapOf("hour" to "7", "minute" to "0"))
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Mobile Control & System",
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = palette.textPrimary
            )
            Text(
                text = "Voice session, screen vision, permissions, device controls & command history",
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.5.sp,
                color = palette.textSecondary
            )
        }

        item {
            DashboardStatusRowCard(
                icon = Icons.Rounded.GraphicEq,
                title = "Background AI Session",
                subtitle = if (isRunning) "Running live in background" else "Inactive — tap to wake MYRA",
                statusLabel = if (isRunning) "ACTIVE" else "START",
                isActive = isRunning,
                accentColor = accentColor,
                onClick = onStartStopClick
            )
        }

        item {
            DashboardStatusRowCard(
                icon = if (isScreenSharing) Icons.Rounded.StopScreenShare else Icons.Rounded.ScreenShare,
                title = "Live Screen Share",
                subtitle = if (isScreenSharing) "MYRA can see your phone screen now" else "Say \"screen share on koro\" or tap to share",
                statusLabel = if (isScreenSharing) "LIVE" else "SHARE",
                isActive = isScreenSharing,
                accentColor = accentColor,
                onClick = onScreenShareClick
            )
        }

        item {
            DashboardStatusRowCard(
                icon = Icons.Rounded.AutoAwesome,
                title = "Floating Siri Orb",
                subtitle = "Shows glowing orb on screen when you leave MYRA",
                statusLabel = if (telemetry.isOverlayEnabled) "ON" else "ENABLE",
                isActive = telemetry.isOverlayEnabled,
                accentColor = accentColor,
                onClick = onOpenOverlaySettings
            )
        }

        item {
            DashboardStatusRowCard(
                icon = Icons.Rounded.AccessibilityNew,
                title = "Accessibility Helper",
                subtitle = "Allows voice control to close apps & take screenshots",
                statusLabel = if (telemetry.isAccessibilityEnabled) "ON" else "ENABLE",
                isActive = telemetry.isAccessibilityEnabled,
                accentColor = accentColor,
                onClick = onOpenAccessibilitySettings
            )
        }

        item {
            DashboardStatusRowCard(
                icon = Icons.Rounded.Security,
                title = "Permission Manager",
                subtitle = "Manage Mic, Camera, Contacts, Location, Brightness & Notifications",
                statusLabel = "MANAGE",
                isActive = telemetry.isOverlayEnabled && telemetry.isAccessibilityEnabled,
                accentColor = accentColor,
                onClick = onOpenPermissionsSettings
            )
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Device Controls",
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp,
                color = palette.textPrimary
            )
        }

        items(controlTiles.chunked(2)) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { tile ->
                    DashboardActionCard(
                        icon = tile.icon,
                        title = tile.title,
                        subtitle = tile.subtitle,
                        isHighlighted = tile.isHighlighted,
                        onClick = { onExecuteQuickCommand(tile.command) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        item {
            DashboardActionCard(
                icon = Icons.Rounded.Call,
                title = "Prime Contact Call",
                subtitle = "Prompts confirmation before dialing your primary contact",
                isHighlighted = false,
                onClick = {
                    onExecuteQuickCommand(
                        AppCommand(AppCommand.TYPE_PRIME_CALL, mapOf("index" to "0"))
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MyraColors.Crimson,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = "Command History",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        color = palette.textPrimary
                    )
                }
                if (commandHistory.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onClearHistory)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("clear_history_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Clear history",
                            tint = MyraColors.Crimson,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Clear",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MyraColors.Crimson
                        )
                    }
                }
            }
        }

        if (commandHistory.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = palette.surfaceCard,
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No commands executed yet. Try asking MYRA for the weather, battery status, or tapping a control above.",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.5.sp,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(commandHistory, key = { "${it.id}_${it.timestamp}" }) { item ->
                CommandHistoryRowCard(item = item)
            }
        }
    }
}

@Composable
private fun CommandHistoryRowCard(item: CommandHistoryItem) {
    val palette = LocalMyraPalette.current
    val timeText = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(item.timestamp))

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.surfaceCard,
        border = BorderStroke(1.dp, palette.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (item.succeeded) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = if (item.succeeded) "Succeeded" else "Attention needed",
                tint = if (item.succeeded) MyraColors.Emerald else MyraColors.Amber,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.inputSummary,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeText,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        color = palette.textMuted
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.resultSummary,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DashboardStatusRowCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    statusLabel: String,
    isActive: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val palette = LocalMyraPalette.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = palette.surfaceCard,
        border = BorderStroke(
            1.dp,
            if (isActive) accentColor.copy(alpha = 0.35f) else palette.borderSubtle
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MyraColors.Crimson.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MyraColors.Crimson,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = palette.textPrimary
                )
                Text(
                    text = subtitle,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = if (isActive) MyraColors.Emerald.copy(alpha = 0.15f) else MyraColors.Crimson.copy(alpha = 0.16f)
            ) {
                Text(
                    text = statusLabel,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    color = if (isActive) MyraColors.Emerald else MyraColors.Crimson,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun DashboardActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMyraPalette.current
    Surface(
        onClick = onClick,
        modifier = modifier.height(94.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (isHighlighted) palette.surfaceHighlight else palette.surfaceCard,
        border = BorderStroke(
            width = 1.dp,
            color = if (isHighlighted) MyraColors.Crimson.copy(alpha = 0.50f) else palette.borderSubtle
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(13.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MyraColors.Crimson.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MyraColors.Crimson,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
