package com.myra.assistant.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.StopScreenShare
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.myra.assistant.model.AppCommand
import com.myra.assistant.ui.theme.JetBrainsMonoFontFamily
import com.myra.assistant.ui.theme.MyraColors
import com.myra.assistant.ui.theme.PlusJakartaFontFamily
import com.myra.assistant.ui.theme.SpaceGroteskFontFamily
import kotlin.math.PI
import kotlin.math.sin

enum class HomeTab {
    HOME, CHAT, TRIGGERS
}

data class HomeTelemetryState(
    val userName: String = "Alex",
    val batteryPercent: Int = 100,
    val ramPercent: Int = 0,
    val timeText: String = "",
    val personalityLabel: String = "GF",
    val languageLabel: String = "Banglish",
    val voiceLabel: String = "Aoede",
    val isOverlayEnabled: Boolean = false,
    val isAccessibilityEnabled: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isRunning: Boolean,
    isMuted: Boolean,
    isSpeaking: Boolean,
    isScreenSharing: Boolean,
    orbState: OrbAnimationView.State,
    statusText: String,
    amplitude: Float,
    messages: List<ChatMessage>,
    telemetry: HomeTelemetryState,
    onStartStopClick: () -> Unit,
    onScreenShareClick: () -> Unit,
    onMicClick: () -> Unit,
    onMicLongPress: () -> Unit,
    onOrbClick: () -> Unit,
    onSendMessage: (String) -> Unit,
    onExecuteQuickCommand: (AppCommand) -> Unit,
    onCheckUpdatesClick: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onSettingsClick: () -> Unit
) {
    var activeTab by rememberSaveable { mutableStateOf(HomeTab.HOME) }
    var messageDraft by rememberSaveable { mutableStateOf("") }
    var showQuickActionsSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = activeTab != HomeTab.HOME) {
        activeTab = HomeTab.HOME
    }

    val accentColor by animateColorAsState(
        targetValue = when {
            !isRunning -> MyraColors.Crimson
            orbState == OrbAnimationView.State.THINKING -> MyraColors.Cyan
            orbState == OrbAnimationView.State.SPEAKING -> Color(0xFFFF2E63)
            isMuted -> MyraColors.Amber
            else -> MyraColors.Crimson
        },
        animationSpec = tween(380),
        label = "accentColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06060B))
    ) {
        // Subtle dark ambient vignette across entire screen
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B0A11),
                        Color(0xFF06060A),
                        Color(0xFF08070D)
                    )
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
        ) {
            // 1. TOP HEADER BAR (MYRA AI ASSISTANT + Bell + Settings)
            DarkTopBrandHeader(
                isRunning = isRunning,
                isScreenSharing = isScreenSharing,
                accentColor = accentColor,
                onCheckUpdatesClick = onCheckUpdatesClick,
                onSettingsClick = onSettingsClick
            )

            // 2. MAIN CONTENT AREA BY TAB
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (activeTab) {
                    HomeTab.HOME -> {
                        HomeDashboardContent(
                            isRunning = isRunning,
                            isMuted = isMuted,
                            isSpeaking = isSpeaking,
                            isScreenSharing = isScreenSharing,
                            orbState = orbState,
                            statusText = statusText,
                            amplitude = amplitude,
                            accentColor = accentColor,
                            telemetry = telemetry,
                            messageDraft = messageDraft,
                            onMessageDraftChange = { messageDraft = it },
                            onSendMessage = {
                                val text = messageDraft.trim()
                                if (text.isNotEmpty()) {
                                    messageDraft = ""
                                    onSendMessage(text)
                                }
                            },
                            onOpenQuickActions = { showQuickActionsSheet = true },
                            onOrbClick = onOrbClick,
                            onVoiceModeClick = onStartStopClick,
                            onScreenShareClick = onScreenShareClick
                        )
                    }
                    HomeTab.CHAT -> {
                        ChatTabContent(
                            messages = messages,
                            accentColor = accentColor,
                            isRunning = isRunning,
                            statusText = statusText,
                            messageDraft = messageDraft,
                            onMessageDraftChange = { messageDraft = it },
                            onSendMessage = {
                                val text = messageDraft.trim()
                                if (text.isNotEmpty()) {
                                    messageDraft = ""
                                    onSendMessage(text)
                                }
                            },
                            onOpenQuickActions = { showQuickActionsSheet = true }
                        )
                    }
                    HomeTab.TRIGGERS -> {
                        TriggersTabContent(
                            telemetry = telemetry,
                            isRunning = isRunning,
                            isScreenSharing = isScreenSharing,
                            accentColor = accentColor,
                            onStartStopClick = onStartStopClick,
                            onScreenShareClick = onScreenShareClick,
                            onOpenOverlaySettings = onOpenOverlaySettings,
                            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                            onExecuteQuickCommand = onExecuteQuickCommand
                        )
                    }
                }
            }

            // 3. BOTTOM NAVIGATION DOCK WITH RAISED CENTER MIC ORB
            BottomNavigationDock(
                activeTab = activeTab,
                isRunning = isRunning,
                isMuted = isMuted,
                isSpeaking = isSpeaking,
                accentColor = accentColor,
                onSelectTab = { activeTab = it },
                onMicClick = onMicClick,
                onMicLongPress = onMicLongPress,
                onSettingsClick = onSettingsClick
            )
        }
    }

    if (showQuickActionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showQuickActionsSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF101018),
            contentColor = MyraColors.TextPrimary
        ) {
            QuickActionsSheetContent(
                onSelectPrompt = { prompt ->
                    showQuickActionsSheet = false
                    onSendMessage(prompt)
                },
                onRunCommand = { cmd ->
                    showQuickActionsSheet = false
                    onExecuteQuickCommand(cmd)
                }
            )
        }
    }
}

@Composable
private fun DarkTopBrandHeader(
    isRunning: Boolean,
    isScreenSharing: Boolean,
    accentColor: Color,
    onCheckUpdatesClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Brand Wordmark: MYRA / AI ASSISTANT
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold)) {
                            append("MY")
                        }
                        withStyle(SpanStyle(color = MyraColors.Crimson, fontWeight = FontWeight.ExtraBold)) {
                            append("RA")
                        }
                    },
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 22.sp,
                    letterSpacing = 1.8.sp
                )
                if (isRunning) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MyraColors.Emerald)
                    )
                }
                if (isScreenSharing) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MyraColors.Crimson.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, MyraColors.Crimson.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "SCREEN LIVE",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.5.sp,
                            color = Color(0xFFFFB3C1),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = when {
                    isScreenSharing -> "SCREEN VISION ACTIVE"
                    isRunning -> "LIVE ASSISTANT"
                    else -> "AI ASSISTANT"
                },
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 9.sp,
                letterSpacing = 2.sp,
                color = if (isRunning || isScreenSharing) accentColor else Color(0xFF7C7C92)
            )
        }

        // Right circular dark glass icon buttons (Notifications/Updates + Settings)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onCheckUpdatesClick,
                shape = CircleShape,
                color = Color(0xFF13131D),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(48.dp)
                    .testTag("notifications_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsNone,
                        contentDescription = "Check Updates",
                        tint = Color(0xFFE0E0EA),
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Surface(
                onClick = onSettingsClick,
                shape = CircleShape,
                color = Color(0xFF13131D),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(48.dp)
                    .testTag("settings_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFFE0E0EA),
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDashboardContent(
    isRunning: Boolean,
    isMuted: Boolean,
    isSpeaking: Boolean,
    isScreenSharing: Boolean,
    orbState: OrbAnimationView.State,
    statusText: String,
    amplitude: Float,
    accentColor: Color,
    telemetry: HomeTelemetryState,
    messageDraft: String,
    onMessageDraftChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onOpenQuickActions: () -> Unit,
    onOrbClick: () -> Unit,
    onVoiceModeClick: () -> Unit,
    onScreenShareClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // HERO SECTION WITH HORIZONTAL SILK CRIMSON WAVES + CENTRAL ORB
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 235.dp, max = 285.dp),
            contentAlignment = Alignment.Center
        ) {
            val orbSize = if (maxHeight < 250.dp) 186.dp else 218.dp

            // Horizontal flowing silk ribbon backdrop matching reference image in dark crimson
            CrimsonSilkWaveBackdrop(
                accentColor = accentColor,
                isRunning = isRunning,
                isSpeaking = isSpeaking,
                amplitude = amplitude,
                modifier = Modifier.fillMaxSize()
            )

            // Central Orb
            Box(
                modifier = Modifier
                    .size(orbSize)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
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

                // Iconic dark-crimson 'M / MYRA AI ASSISTANT' core emblem when idle
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isRunning,
                    enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.9f),
                    exit = fadeOut(tween(220)) + scaleOut(targetScale = 0.85f)
                ) {
                    MyraCoreEmblem(modifier = Modifier.size(orbSize * 0.50f))
                }
            }
        }

        // GREETING + SUBTITLE
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val headline = when {
                !isRunning -> "Hello!"
                isSpeaking -> "Speaking..."
                orbState == OrbAnimationView.State.THINKING -> "Thinking..."
                isMuted -> "Muted"
                else -> "Listening..."
            }
            Text(
                text = headline,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isRunning) statusText else "How can I help you today?",
                fontFamily = PlusJakartaFontFamily,
                fontSize = 14.5.sp,
                color = Color(0xFF9E9EB4),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (isRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    onClick = onVoiceModeClick,
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF280B16),
                    border = BorderStroke(1.dp, MyraColors.Crimson.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Stop Voice Mode",
                            tint = MyraColors.Crimson,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Stop MYRA",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            color = Color(0xFFFFD6DF)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // MESSAGE INPUT PILL ("Type a message...")
        DarkMessageInputPill(
            value = messageDraft,
            onValueChange = onMessageDraftChange,
            onSend = onSendMessage,
            onLeftActionClick = onOpenQuickActions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(22.dp))

        // "More" SECTION + TWO SIDE-BY-SIDE CARDS
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "More",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    text = "${telemetry.personalityLabel} • ${telemetry.languageLabel}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.5.sp,
                    color = Color(0xFF7B7B90)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureActionCard(
                    icon = Icons.Rounded.GraphicEq,
                    title = if (isRunning) "Voice Active" else "Voice Mode",
                    subtitle = if (isRunning) "Tap to stop session" else "Holographic Orb",
                    isHighlighted = isRunning,
                    onClick = onVoiceModeClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("voice_mode_card")
                )

                FeatureActionCard(
                    icon = if (isScreenSharing) Icons.Rounded.StopScreenShare else Icons.Rounded.ScreenShare,
                    title = if (isScreenSharing) "Screen Live" else "Screen Share",
                    subtitle = if (isScreenSharing) "Seeing screen • Tap stop" else "Share mobile screen",
                    isHighlighted = isScreenSharing,
                    onClick = onScreenShareClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("screen_share_card")
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))
    }
}

@Composable
private fun CrimsonSilkWaveBackdrop(
    accentColor: Color,
    isRunning: Boolean,
    isSpeaking: Boolean,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "silkWave")
    val shift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "silkShift"
    )

    val energy = if (isSpeaking) 1.35f + amplitude * 0.9f else if (isRunning) 1.15f else 0.9f

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cy = h * 0.50f

        // Center crimson glow pool behind orb
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = if (isRunning) 0.24f else 0.16f),
                    Color(0xFF880E4F).copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, cy),
                radius = minOf(w, h) * 0.58f
            ),
            radius = minOf(w, h) * 0.58f,
            center = Offset(w * 0.5f, cy)
        )

        // 3 sweeping translucent crimson-pink silk ribbons left -> right
        val ribbonConfigs = listOf(
            Triple(0.0f, 34.dp.toPx() * energy, 0.16f),
            Triple(1.4f, 22.dp.toPx() * energy, 0.22f),
            Triple(2.8f, 14.dp.toPx() * energy, 0.12f)
        )

        ribbonConfigs.forEachIndexed { idx, (phaseOffset, ampPx, alpha) ->
            val path = Path()
            val topY = cy - (18.dp.toPx() + idx * 6.dp.toPx())
            val botY = cy + (18.dp.toPx() + idx * 6.dp.toPx())

            val steps = 36
            // Top edge left to right
            for (s in 0..steps) {
                val t = s.toFloat() / steps
                val x = w * t
                val yOff = sin(t * PI * 2.2 + shift + phaseOffset).toFloat() * ampPx
                val y = topY + yOff
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            // Bottom edge right to left
            for (s in steps downTo 0) {
                val t = s.toFloat() / steps
                val x = w * t
                val yOff = sin(t * PI * 2.2 + shift + phaseOffset + 0.9).toFloat() * ampPx
                val y = botY + yOff
                path.lineTo(x, y)
            }
            path.close()

            drawPath(
                path = path,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = alpha * 0.7f),
                        Color(0xFFFF4081).copy(alpha = alpha),
                        accentColor.copy(alpha = alpha * 0.7f)
                    )
                )
            )
        }
    }
}

@Composable
private fun MyraCoreEmblem(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Stylized 3D Crimson "M" vector mark matching reference aesthetic
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .height(40.dp)
        ) {
            val w = size.width
            val h = size.height
            val mPath = Path().apply {
                moveTo(w * 0.08f, h * 0.92f)
                lineTo(w * 0.08f, h * 0.14f)
                lineTo(w * 0.50f, h * 0.60f)
                lineTo(w * 0.92f, h * 0.14f)
                lineTo(w * 0.92f, h * 0.92f)
            }
            // Glow stroke
            drawPath(
                path = mPath,
                color = MyraColors.Crimson.copy(alpha = 0.38f),
                style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // Main crimson gradient stroke
            drawPath(
                path = mPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFF4D6D), MyraColors.Crimson, Color(0xFFB71C1C))
                ),
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                    append("MY")
                }
                withStyle(SpanStyle(color = MyraColors.Crimson, fontWeight = FontWeight.Bold)) {
                    append("RA")
                }
            },
            fontFamily = SpaceGroteskFontFamily,
            fontSize = 15.sp,
            letterSpacing = 2.4.sp
        )
        Text(
            text = "AI ASSISTANT",
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 6.5.sp,
            letterSpacing = 1.6.sp,
            color = Color(0xFFABAAC0)
        )
    }
}

@Composable
private fun DarkMessageInputPill(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onLeftActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(27.dp),
        color = Color(0xFF12121B),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onLeftActionClick)
                    .testTag("quick_prompt_attach_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AttachFile,
                    contentDescription = "Quick prompts and actions",
                    tint = Color(0xFF8C8CA4),
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Type a message...",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 14.sp,
                        color = Color(0xFF727288)
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 14.sp,
                        color = Color.White
                    ),
                    cursorBrush = SolidColor(MyraColors.Crimson),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            keyboardController?.hide()
                            onSend()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("message_input_field")
                )
            }

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(MyraColors.Crimson, Color(0xFFD5004C))
                        )
                    )
                    .clickable {
                        keyboardController?.hide()
                        onSend()
                    }
                    .testTag("send_message_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Send message",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun FeatureActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(104.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (isHighlighted) Color(0xFF1A0D16) else Color(0xFF11111A),
        border = BorderStroke(
            width = 1.dp,
            color = if (isHighlighted) MyraColors.Crimson.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.07f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MyraColors.Crimson.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MyraColors.Crimson,
                    modifier = Modifier.size(19.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.5.sp,
                    color = Color(0xFF8A8AA0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BottomNavigationDock(
    activeTab: HomeTab,
    isRunning: Boolean,
    isMuted: Boolean,
    isSpeaking: Boolean,
    accentColor: Color,
    onSelectTab: (HomeTab) -> Unit,
    onMicClick: () -> Unit,
    onMicLongPress: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val micActive = isRunning && !isMuted
    val micScale by animateFloatAsState(
        targetValue = if (isSpeaking) 1.07f else if (micActive) 1.03f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "micOrbScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B0B12))
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        // Top hairline divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.07f))
                .align(Alignment.TopCenter)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            BottomNavItem(
                icon = Icons.Rounded.Home,
                label = "Home",
                selected = activeTab == HomeTab.HOME,
                onClick = { onSelectTab(HomeTab.HOME) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_home")
            )
            BottomNavItem(
                icon = Icons.Rounded.ChatBubbleOutline,
                label = "Chat",
                selected = activeTab == HomeTab.CHAT,
                onClick = { onSelectTab(HomeTab.CHAT) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_chat")
            )

            // Center spacer under floating mic button
            Spacer(modifier = Modifier.width(72.dp))

            BottomNavItem(
                icon = Icons.Rounded.Bolt,
                label = "Triggers",
                selected = activeTab == HomeTab.TRIGGERS,
                onClick = { onSelectTab(HomeTab.TRIGGERS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_triggers")
            )
            BottomNavItem(
                icon = Icons.Rounded.Settings,
                label = "Settings",
                selected = false,
                onClick = onSettingsClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_settings")
            )
        }

        // Raised Dark-Crimson Center Mic Sphere overlapping bottom bar
        Box(
            modifier = Modifier
                .offset(y = (-20).dp)
                .scale(micScale)
                .minimumInteractiveComponentSize()
                .size(62.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = MyraColors.Crimson,
                    spotColor = MyraColors.Crimson
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = if (micActive) {
                            listOf(Color(0xFFFF1744), Color(0xFF7A0928), Color(0xFF1F040C))
                        } else {
                            listOf(Color(0xFF420A19), Color(0xFF1E060E), Color(0xFF0E0408))
                        }
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            if (micActive) accentColor else MyraColors.Crimson.copy(alpha = 0.7f),
                            Color(0xFF6A0B28)
                        )
                    ),
                    shape = CircleShape
                )
                .combinedClickable(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMicClick()
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMicLongPress()
                    }
                )
                .testTag("mic_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isRunning && isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                contentDescription = if (micActive) "Mute or long press to interrupt" else "Start or unmute MYRA",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MyraColors.Crimson.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MyraColors.Crimson else Color(0xFF7C7C92),
                modifier = Modifier.size(21.dp)
            )
            Text(
                text = label,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 10.5.sp,
                color = if (selected) MyraColors.Crimson else Color(0xFF7C7C92)
            )
        }
    }
}

@Composable
private fun ChatTabContent(
    messages: List<ChatMessage>,
    accentColor: Color,
    isRunning: Boolean,
    statusText: String,
    messageDraft: String,
    onMessageDraftChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onOpenQuickActions: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Live session status banner
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF11111A),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) accentColor else Color(0xFF555568))
                )
                Text(
                    text = if (isRunning) statusText else "Send a message or tap the mic orb to start talking",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.5.sp,
                    color = Color(0xFFC2C2D4),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MyraColors.Crimson,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "No conversation yet",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Type a message below or tap the center microphone to speak with MYRA.",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 13.sp,
                        color = Color(0xFF8C8CA4),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("chat_list"),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { "${it.timestamp}_${it.isUser}_${it.text.hashCode()}" }) { msg ->
                    ChatBubbleItem(message = msg, accentColor = accentColor)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        DarkMessageInputPill(
            value = messageDraft,
            onValueChange = onMessageDraftChange,
            onSend = onSendMessage,
            onLeftActionClick = onOpenQuickActions,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
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
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(MyraColors.Crimson, Color(0xFF9C0B3F))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "MYRA",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
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
            color = if (message.isUser) Color(0xFF280D18) else Color(0xFF14141F),
            border = BorderStroke(
                width = 1.dp,
                color = if (message.isUser) MyraColors.Crimson.copy(alpha = 0.45f) else accentColor.copy(alpha = 0.16f)
            ),
            modifier = Modifier.widthIn(max = 285.dp)
        ) {
            Text(
                text = message.text,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = MyraColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
private fun TriggersTabContent(
    telemetry: HomeTelemetryState,
    isRunning: Boolean,
    isScreenSharing: Boolean,
    accentColor: Color,
    onStartStopClick: () -> Unit,
    onScreenShareClick: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onExecuteQuickCommand: (AppCommand) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "System & Triggers",
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
            Text(
                text = "Background voice, screen vision, floating Siri orb, and quick device actions",
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.5.sp,
                color = Color(0xFF8C8CA4)
            )
        }

        item {
            TriggerStatusRowCard(
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
            TriggerStatusRowCard(
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
            TriggerStatusRowCard(
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
            TriggerStatusRowCard(
                icon = Icons.Rounded.AccessibilityNew,
                title = "Accessibility Helper",
                subtitle = "Allows voice control to open & close apps",
                statusLabel = if (telemetry.isAccessibilityEnabled) "ON" else "ENABLE",
                isActive = telemetry.isAccessibilityEnabled,
                accentColor = accentColor,
                onClick = onOpenAccessibilitySettings
            )
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Instant Controls",
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeatureActionCard(
                    icon = Icons.Rounded.FlashlightOn,
                    title = "Torch On",
                    subtitle = "Toggle flashlight",
                    isHighlighted = false,
                    onClick = { onExecuteQuickCommand(AppCommand(AppCommand.TYPE_FLASHLIGHT_ON)) },
                    modifier = Modifier.weight(1f)
                )
                FeatureActionCard(
                    icon = Icons.Rounded.Call,
                    title = "Prime Call",
                    subtitle = "Call primary contact",
                    isHighlighted = false,
                    onClick = {
                        onExecuteQuickCommand(
                            AppCommand(AppCommand.TYPE_PRIME_CALL, mapOf("index" to "0"))
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TriggerStatusRowCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    statusLabel: String,
    isActive: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF11111A),
        border = BorderStroke(
            1.dp,
            if (isActive) accentColor.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.07f)
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
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = Color(0xFF8C8CA4)
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
private fun QuickActionsSheetContent(
    onSelectPrompt: (String) -> Unit,
    onRunCommand: (AppCommand) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Quick Actions & Prompts",
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickChipButton(
                icon = Icons.Rounded.FlashlightOn,
                label = "Torch On",
                onClick = { onRunCommand(AppCommand(AppCommand.TYPE_FLASHLIGHT_ON)) },
                modifier = Modifier.weight(1f)
            )
            QuickChipButton(
                icon = Icons.Rounded.VolumeUp,
                label = "Volume +",
                onClick = { onRunCommand(AppCommand(AppCommand.TYPE_VOLUME_UP)) },
                modifier = Modifier.weight(1f)
            )
            QuickChipButton(
                icon = Icons.Rounded.Wifi,
                label = "Wi-Fi",
                onClick = { onRunCommand(AppCommand(AppCommand.TYPE_WIFI_ON)) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickChipButton(
                icon = Icons.Rounded.OpenInNew,
                label = "YouTube",
                onClick = {
                    onRunCommand(AppCommand(AppCommand.TYPE_OPEN_APP, mapOf("app_name" to "YouTube")))
                },
                modifier = Modifier.weight(1f)
            )
            QuickChipButton(
                icon = Icons.Rounded.OpenInNew,
                label = "WhatsApp",
                onClick = {
                    onRunCommand(AppCommand(AppCommand.TYPE_OPEN_APP, mapOf("app_name" to "WhatsApp")))
                },
                modifier = Modifier.weight(1f)
            )
            QuickChipButton(
                icon = Icons.Rounded.Call,
                label = "Prime Call",
                onClick = {
                    onRunCommand(AppCommand(AppCommand.TYPE_PRIME_CALL, mapOf("index" to "0")))
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        val prompts = listOf(
            "Ask MYRA: Ajker weather ba din ta kemon?",
            "Ask MYRA: Ekta mojaar golpo bolo",
            "Ask MYRA: Amar din ta plan korte help koro"
        )
        prompts.forEach { item ->
            val cleanPrompt = item.removePrefix("Ask MYRA: ")
            Surface(
                onClick = { onSelectPrompt(cleanPrompt) },
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF171723),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MyraColors.Crimson,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = cleanPrompt,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 13.5.sp,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun QuickChipButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF171723),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier.height(52.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MyraColors.Crimson,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}
