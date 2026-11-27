package com.myra.assistant.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.myra.assistant.ui.theme.JetBrainsMonoFontFamily
import com.myra.assistant.ui.theme.MyraColors
import com.myra.assistant.ui.theme.PlusJakartaFontFamily
import com.myra.assistant.ui.theme.SpaceGroteskFontFamily
import kotlin.math.PI
import kotlin.math.sin

data class HomeTelemetryState(
    val batteryPercent: Int = 100,
    val ramPercent: Int = 0,
    val timeText: String = "",
    val personalityLabel: String = "GF",
    val languageLabel: String = "Banglish",
    val voiceLabel: String = "Aoede"
)

@Composable
fun HomeScreen(
    isRunning: Boolean,
    isMuted: Boolean,
    isSpeaking: Boolean,
    orbState: OrbAnimationView.State,
    statusText: String,
    amplitude: Float,
    messages: List<ChatMessage>,
    telemetry: HomeTelemetryState,
    onStartStopClick: () -> Unit,
    onMicClick: () -> Unit,
    onMicLongPress: () -> Unit,
    onOrbClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val accentColor by animateColorAsState(
        targetValue = when {
            !isRunning -> MyraColors.Crimson
            orbState == OrbAnimationView.State.THINKING -> MyraColors.Cyan
            orbState == OrbAnimationView.State.SPEAKING -> MyraColors.Violet
            isMuted -> MyraColors.Amber
            else -> MyraColors.Crimson
        },
        animationSpec = tween(420),
        label = "accentColor"
    )

    val secondaryAccent by animateColorAsState(
        targetValue = when {
            !isRunning -> Color(0xFF5A0A26)
            orbState == OrbAnimationView.State.THINKING -> Color(0xFF0077C2)
            orbState == OrbAnimationView.State.SPEAKING -> MyraColors.Crimson
            else -> MyraColors.Violet
        },
        animationSpec = tween(420),
        label = "secondaryAccent"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MyraColors.BgObsidian)
    ) {
        // Atmospheric ambient background canvas
        AtmosphericBackdrop(
            accentColor = accentColor,
            secondaryColor = secondaryAccent,
            isRunning = isRunning,
            isSpeaking = isSpeaking,
            amplitude = amplitude
        )

        // Centered responsive column for phones, foldables, and tablets
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .widthIn(max = 600.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // 1. TOP LUXURY TELEMETRY BAR
            TopTelemetryHeader(
                telemetry = telemetry,
                isRunning = isRunning,
                accentColor = accentColor,
                onSettingsClick = onSettingsClick
            )

            // 2. HERO ORB + PERSONA CHIP + LIVE WAVEFORM + STATUS
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val compactHeight = maxHeight < 340.dp
                val orbSize = when {
                    maxHeight < 290.dp -> 170.dp
                    maxHeight < 360.dp -> 210.dp
                    else -> 244.dp
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Animated Orb centerpiece
                    Box(
                        modifier = Modifier
                            .size(orbSize)
                            .clickable(
                                interactionSource = androidx.compose.runtime.remember {
                                    androidx.compose.foundation.interaction.MutableInteractionSource()
                                },
                                indication = null,
                                onClick = onOrbClick
                            )
                            .testTag("orb_container"),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                OrbAnimationView(ctx).apply {
                                    setState(orbState)
                                    this.amplitude = amplitude
                                }
                            },
                            update = { view ->
                                view.setState(orbState)
                                view.amplitude = amplitude
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(if (compactHeight) 6.dp else 10.dp))

                    // Active Persona / Language / Voice quick badge
                    Surface(
                        onClick = onSettingsClick,
                        shape = RoundedCornerShape(50),
                        color = MyraColors.SurfaceGlass,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${telemetry.personalityLabel}  •  ${telemetry.languageLabel}  •  ${telemetry.voiceLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MyraColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (compactHeight) 8.dp else 12.dp))

                    // Smooth Compose Gradient Audio Visualizer
                    PremiumWaveformBar(
                        amplitude = amplitude,
                        isRunning = isRunning,
                        isMuted = isMuted,
                        primaryColor = accentColor,
                        secondaryColor = secondaryAccent,
                        modifier = Modifier
                            .width(210.dp)
                            .height(if (compactHeight) 28.dp else 36.dp)
                    )

                    Spacer(modifier = Modifier.height(if (compactHeight) 6.dp else 10.dp))

                    // Live Status Pill
                    LiveStatusBadge(
                        statusText = statusText,
                        isRunning = isRunning,
                        accentColor = accentColor
                    )
                }
            }

            // 3. GLASSMORPHIC CONVERSATION STREAM
            ConversationGlassPanel(
                messages = messages,
                accentColor = accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 124.dp, max = 180.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. FLOATING LUXURY CONTROL DOCK
            FloatingControlDock(
                isRunning = isRunning,
                isMuted = isMuted,
                accentColor = accentColor,
                onStartStopClick = onStartStopClick,
                onMicClick = onMicClick,
                onMicLongPress = onMicLongPress
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isRunning) {
                    "Long press mic to interrupt  •  Active in background"
                } else {
                    "Tap START or the orb to wake MYRA"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MyraColors.TextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun AtmosphericBackdrop(
    accentColor: Color,
    secondaryColor: Color,
    isRunning: Boolean,
    isSpeaking: Boolean,
    amplitude: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "backdropPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringSpin"
    )

    val speakingBoost = if (isSpeaking) 0.22f + amplitude * 0.14f else if (isRunning) 0.16f else 0.10f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val orbCenter = Offset(w * 0.5f, h * 0.36f)

        // Primary radial aura behind orb
        val mainRadius = minOf(w, h) * 0.68f * pulse
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = speakingBoost),
                    secondaryColor.copy(alpha = speakingBoost * 0.45f),
                    Color.Transparent
                ),
                center = orbCenter,
                radius = mainRadius
            ),
            radius = mainRadius,
            center = orbCenter
        )

        // Subtle secondary bottom corner aura
        val bottomCorner = Offset(w * 0.85f, h * 0.92f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondaryColor.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                center = bottomCorner,
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = bottomCorner
        )
    }
}

@Composable
private fun TopTelemetryHeader(
    telemetry: HomeTelemetryState,
    isRunning: Boolean,
    accentColor: Color,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Battery + RAM glass telemetry capsule
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MyraColors.SurfaceGlass,
            border = BorderStroke(1.dp, MyraColors.BorderSubtle)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BatteryChargingFull,
                        contentDescription = "Battery",
                        tint = MyraColors.Crimson,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${telemetry.batteryPercent}%",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MyraColors.TextPrimary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = "Memory",
                        tint = MyraColors.TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "RAM ${telemetry.ramPercent}%",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        color = MyraColors.TextSecondary
                    )
                }
            }
        }

        // Center: Luxury MYRA wordmark + live pulse dot
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) MyraColors.Emerald else MyraColors.Crimson.copy(alpha = 0.6f))
                )
                Text(
                    text = "MYRA",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                    letterSpacing = 5.sp,
                    color = MyraColors.TextPrimary
                )
            }
            Text(
                text = if (isRunning) "LIVE NEURAL VOICE" else "AI COMPANION",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 9.sp,
                letterSpacing = 1.6.sp,
                color = if (isRunning) accentColor else MyraColors.TextMuted
            )
        }

        // Right: Live Clock + Settings Glass Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (telemetry.timeText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MyraColors.SurfaceGlass,
                    border = BorderStroke(1.dp, MyraColors.BorderSubtle)
                ) {
                    Text(
                        text = telemetry.timeText,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = MyraColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
                    )
                }
            }

            Surface(
                onClick = onSettingsClick,
                shape = CircleShape,
                color = MyraColors.SurfaceGlass,
                border = BorderStroke(1.dp, MyraColors.BorderSubtle),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(48.dp)
                    .testTag("settings_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = MyraColors.TextPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumWaveformBar(
    amplitude: Float,
    isRunning: Boolean,
    isMuted: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveAnim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val smoothedAmp by animateFloatAsState(
        targetValue = when {
            !isRunning -> 0.14f
            isMuted -> 0.08f
            else -> amplitude.coerceIn(0.12f, 1.0f)
        },
        animationSpec = tween(durationMillis = 90),
        label = "ampSmooth"
    )

    Canvas(modifier = modifier) {
        val barCount = 24
        val spacing = 4.dp.toPx()
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(2.dp.toPx())
        val centerY = size.height / 2f

        val barBrush = Brush.verticalGradient(
            colors = listOf(primaryColor, secondaryColor, primaryColor)
        )

        for (i in 0 until barCount) {
            // Symmetric envelope stronger toward center
            val distFromCenter = kotlin.math.abs(i - (barCount - 1) / 2f) / (barCount / 2f)
            val envelope = 1f - distFromCenter * 0.55f
            val wave = ((sin(phase + i * 0.55f) + 1f) / 2f)
            val normalizedHeight = (0.14f + smoothedAmp * envelope * (0.35f + 0.65f * wave))
                .coerceIn(0.12f, 1f)

            val barHeight = (size.height * normalizedHeight).coerceAtLeast(4.dp.toPx())
            val left = i * (barWidth + spacing)
            val top = centerY - barHeight / 2f

            drawRoundRect(
                brush = barBrush,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                alpha = if (!isRunning || isMuted) 0.45f else 0.92f
            )
        }
    }
}

@Composable
private fun LiveStatusBadge(
    statusText: String,
    isRunning: Boolean,
    accentColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "statusDot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = MyraColors.SurfaceGlass,
        border = BorderStroke(
            width = 1.dp,
            color = if (isRunning) accentColor.copy(alpha = 0.35f) else MyraColors.BorderSubtle
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = if (isRunning) dotAlpha else 0.45f))
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MyraColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConversationGlassPanel(
    messages: List<ChatMessage>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MyraColors.SurfaceCard.copy(alpha = 0.78f),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.12f),
                    accentColor.copy(alpha = 0.18f),
                    Color.White.copy(alpha = 0.05f)
                )
            )
        )
    ) {
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.14f))
                            .border(1.dp, accentColor.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "MYRA is ready to converse",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = MyraColors.TextPrimary
                    )
                    Text(
                        text = "Speak naturally in Bangla, Banglish, English, or Hindi — or ask MYRA to call, text, or open apps.",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = MyraColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("chat_list"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { "${it.timestamp}_${it.isUser}_${it.text.hashCode()}" }) { msg ->
                    ChatBubbleItem(message = msg, accentColor = accentColor)
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: ChatMessage,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!message.isUser) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp, end = 8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(MyraColors.Crimson, MyraColors.Violet)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "MYRA",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = if (message.isUser) 18.dp else 6.dp,
                topEnd = if (message.isUser) 6.dp else 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            ),
            color = if (message.isUser) {
                MyraColors.Crimson.copy(alpha = 0.18f)
            } else {
                MyraColors.SurfaceElevated.copy(alpha = 0.92f)
            },
            border = BorderStroke(
                width = 1.dp,
                color = if (message.isUser) {
                    MyraColors.Crimson.copy(alpha = 0.40f)
                } else {
                    accentColor.copy(alpha = 0.18f)
                }
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MyraColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FloatingControlDock(
    isRunning: Boolean,
    isMuted: Boolean,
    accentColor: Color,
    onStartStopClick: () -> Unit,
    onMicClick: () -> Unit,
    onMicLongPress: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val buttonScale by animateFloatAsState(
        targetValue = if (isRunning) 1.0f else 1.01f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dockScale"
    )

    Surface(
        shape = RoundedCornerShape(34.dp),
        color = MyraColors.SurfaceCard.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        shadowElevation = 12.dp,
        modifier = Modifier.scale(buttonScale)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Primary START / STOP gradient pill button
            val primaryGradient = if (isRunning) {
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF3E0B1E), Color(0xFF6A1030))
                )
            } else {
                Brush.horizontalGradient(
                    colors = listOf(MyraColors.Crimson, Color(0xFFE91E63), MyraColors.Violet)
                )
            }

            Box(
                modifier = Modifier
                    .height(54.dp)
                    .widthIn(min = 176.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(primaryGradient)
                    .then(
                        if (isRunning) {
                            Modifier.border(
                                1.dp,
                                MyraColors.Crimson.copy(alpha = 0.55f),
                                RoundedCornerShape(27.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartStopClick()
                    }
                    .padding(horizontal = 24.dp)
                    .testTag("start_stop_button"),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isRunning,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                    label = "startStopContent"
                ) { running ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = if (running) "Stop MYRA" else "Start MYRA",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = if (running) "STOP MYRA" else "START MYRA",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.6.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // Mic / Mute / Interrupt circular control
            val micActive = isRunning && !isMuted
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        if (micActive) {
                            Brush.linearGradient(
                                colors = listOf(accentColor.copy(alpha = 0.28f), MyraColors.Violet.copy(alpha = 0.22f))
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(MyraColors.SurfaceElevated, MyraColors.SurfaceElevated)
                            )
                        }
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (micActive) accentColor else Color.White.copy(alpha = 0.14f),
                        shape = CircleShape
                    )
                    .combinedClickable(
                        onClick = onMicClick,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onMicLongPress()
                        }
                    )
                    .testTag("mic_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (micActive) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                    contentDescription = if (micActive) "Mute microphone or long press to interrupt" else "Unmute or start microphone",
                    tint = if (micActive) Color.White else MyraColors.TextSecondary,
                    modifier = Modifier.size(23.dp)
                )
            }
        }
    }
}
