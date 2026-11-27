package com.myra.assistant.ui.settings

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myra.assistant.ui.theme.LocalMyraPalette
import com.myra.assistant.ui.theme.MyraColors

@Composable
fun MyraSettingsScreen(
    state: SettingsUiState,
    initialCategory: SettingsCategory? = null,
    onApiKeyChange: (String) -> Unit,
    onUserNameChange: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onVoiceSelected: (String) -> Unit,
    onResponseStyleSelected: (String) -> Unit,
    onWakeWordToggle: (Boolean) -> Unit,
    onPersonalitySelected: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onThemeModeSelected: (String) -> Unit,
    onMemoryConsentToggle: (Boolean) -> Unit,
    onClearMemoryClick: () -> Unit,
    onAddPrimeContact: (String, String) -> Unit,
    onDeletePrimeContact: (Int) -> Unit,
    onTestApiClick: () -> Unit,
    onRequestRuntimePermission: (String) -> Unit,
    onOpenWriteSettingsClick: () -> Unit,
    onOpenAccessibilityClick: () -> Unit,
    onOpenOverlayClick: () -> Unit,
    onOpenNotificationAccessClick: () -> Unit,
    onCheckUpdatesClick: () -> Unit,
    onSaveClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    val palette = LocalMyraPalette.current
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }

    BackHandler(enabled = selectedCategory != null) {
        selectedCategory = null
    }

    Scaffold(
        containerColor = palette.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            SettingsTopBar(
                title = selectedCategory?.title ?: "SETTINGS",
                isSubScreen = selectedCategory != null,
                onBack = {
                    if (selectedCategory != null) {
                        selectedCategory = null
                    } else {
                        onCloseClick()
                    }
                }
            )
        },
        bottomBar = {
            if (selectedCategory != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.background)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GradientPrimaryButton(
                        text = "SAVE & APPLY",
                        onClick = {
                            onSaveClick()
                            selectedCategory = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .testTag("save_category_button")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedContent(
                targetState = selectedCategory,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp),
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 4 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 4 } + fadeOut())
                    }
                },
                label = "settings_category_transition"
            ) { category ->
                if (category == null) {
                    SettingsCategoryHub(
                        state = state,
                        onSelectCategory = { selectedCategory = it },
                        onSaveAndExit = onSaveClick
                    )
                } else {
                    when (category) {
                        SettingsCategory.API_CONNECTION -> ApiConnectionCategory(
                            state = state,
                            onApiKeyChange = onApiKeyChange,
                            onTestApiClick = onTestApiClick
                        )
                        SettingsCategory.VOICE_MODEL -> VoiceAndModelCategory(
                            state = state,
                            onModelSelected = onModelSelected,
                            onVoiceSelected = onVoiceSelected,
                            onResponseStyleSelected = onResponseStyleSelected,
                            onWakeWordToggle = onWakeWordToggle
                        )
                        SettingsCategory.LANGUAGE_PERSONALITY -> LanguageAndPersonalityCategory(
                            state = state,
                            onLanguageSelected = onLanguageSelected,
                            onPersonalitySelected = onPersonalitySelected,
                            onThemeModeSelected = onThemeModeSelected,
                            onMemoryConsentToggle = onMemoryConsentToggle,
                            onClearMemoryClick = onClearMemoryClick
                        )
                        SettingsCategory.PROFILE_CONTACTS -> ProfileAndContactsCategory(
                            state = state,
                            onUserNameChange = onUserNameChange,
                            onAddPrimeContact = onAddPrimeContact,
                            onDeletePrimeContact = onDeletePrimeContact
                        )
                        SettingsCategory.SYSTEM_PERMISSIONS -> SystemPermissionsCategory(
                            state = state,
                            onRequestRuntimePermission = onRequestRuntimePermission,
                            onOpenWriteSettingsClick = onOpenWriteSettingsClick,
                            onOpenAccessibilityClick = onOpenAccessibilityClick,
                            onOpenOverlayClick = onOpenOverlayClick,
                            onOpenNotificationAccessClick = onOpenNotificationAccessClick
                        )
                        SettingsCategory.APP_UPDATES -> AppUpdatesCategory(
                            state = state,
                            onCheckUpdatesClick = onCheckUpdatesClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTopBar(
    title: String,
    isSubScreen: Boolean,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .testTag("settings_back_button")
        ) {
            Icon(
                imageVector = if (isSubScreen) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close,
                contentDescription = if (isSubScreen) "Back to categories" else "Close settings",
                tint = MyraColors.Crimson
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            color = MyraColors.Crimson,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsCategoryHub(
    state: SettingsUiState,
    onSelectCategory: (SettingsCategory) -> Unit,
    onSaveAndExit: () -> Unit
) {
    val activeModelLabel = state.modelOptions
        .firstOrNull { it.second == state.selectedModel }
        ?.first
        ?.substringBefore(" (")
        ?: "Gemini Live"

    val grantedCount = listOf(
        state.isMicGranted,
        state.isCameraGranted,
        state.isContactsGranted,
        state.isCallPhoneGranted,
        state.isLocationGranted,
        state.isWriteSettingsGranted,
        state.isOverlayEnabled,
        state.isAccessibilityEnabled,
        state.isNotificationAccessEnabled
    ).count { it }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CategoryTile(
                icon = Icons.Rounded.Key,
                title = SettingsCategory.API_CONNECTION.title,
                subtitle = if (state.apiKey.isNotBlank()) "API key configured • Tap to edit or test" else "API key missing • Tap to add",
                accentColor = if (state.apiKey.isNotBlank()) MyraColors.Emerald else MyraColors.Crimson,
                testTag = "category_api",
                onClick = { onSelectCategory(SettingsCategory.API_CONNECTION) }
            )
        }
        item {
            val wakeLabel = if (state.wakeWordEnabled) "Hey MYRA On" else "Wake Word Off"
            CategoryTile(
                icon = Icons.Rounded.GraphicEq,
                title = SettingsCategory.VOICE_MODEL.title,
                subtitle = "$activeModelLabel • ${state.selectedVoice} • $wakeLabel",
                testTag = "category_voice_model",
                onClick = { onSelectCategory(SettingsCategory.VOICE_MODEL) }
            )
        }
        item {
            CategoryTile(
                icon = Icons.Rounded.Translate,
                title = SettingsCategory.LANGUAGE_PERSONALITY.title,
                subtitle = "${state.languageMode} • ${state.personalityMode} • Theme: ${state.themeMode}",
                testTag = "category_language_personality",
                onClick = { onSelectCategory(SettingsCategory.LANGUAGE_PERSONALITY) }
            )
        }
        item {
            val count = state.primeContacts.size
            CategoryTile(
                icon = Icons.Rounded.People,
                title = SettingsCategory.PROFILE_CONTACTS.title,
                subtitle = "${state.userName} • $count Prime Contact${if (count == 1) "" else "s"}",
                testTag = "category_profile_contacts",
                onClick = { onSelectCategory(SettingsCategory.PROFILE_CONTACTS) }
            )
        }
        item {
            CategoryTile(
                icon = Icons.Rounded.AccessibilityNew,
                title = SettingsCategory.SYSTEM_PERMISSIONS.title,
                subtitle = "$grantedCount of 9 permissions enabled • Manage access",
                accentColor = if (state.isMicGranted && state.isAccessibilityEnabled) MyraColors.Emerald else MyraColors.Crimson,
                testTag = "category_permissions",
                onClick = { onSelectCategory(SettingsCategory.SYSTEM_PERMISSIONS) }
            )
        }
        item {
            CategoryTile(
                icon = Icons.Rounded.SystemUpdate,
                title = SettingsCategory.APP_UPDATES.title,
                subtitle = "Version v${state.appVersion} • Tap to check updates",
                testTag = "category_updates",
                onClick = { onSelectCategory(SettingsCategory.APP_UPDATES) }
            )
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            GradientPrimaryButton(
                text = "SAVE & CLOSE",
                onClick = onSaveAndExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_settings_button")
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CategoryTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color = MyraColors.Crimson,
    testTag: String,
    onClick: () -> Unit
) {
    val palette = LocalMyraPalette.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        color = palette.surfaceCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MyraColors.Crimson.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = palette.textSecondary,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Open $title",
                tint = palette.textSecondary
            )
        }
    }
}

@Composable
private fun ApiConnectionCategory(
    state: SettingsUiState,
    onApiKeyChange: (String) -> Unit,
    onTestApiClick: () -> Unit
) {
    val palette = LocalMyraPalette.current
    var showKey by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsSectionCard(title = "GEMINI API KEY") {
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input"),
                    placeholder = { Text("Enter Gemini API Key", color = palette.textMuted) },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showKey = !showKey },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showKey) "Hide API Key" else "Show API Key",
                                tint = palette.textSecondary
                            )
                        }
                    },
                    colors = myraFieldColors(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onTestApiClick,
                    enabled = !state.isTestingApi,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("test_api_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MyraColors.Crimson),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = state.apiTestButtonLabel,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Only verified Gemini API keys allow MYRA voice conversations to start.",
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun VoiceAndModelCategory(
    state: SettingsUiState,
    onModelSelected: (String) -> Unit,
    onVoiceSelected: (String) -> Unit,
    onResponseStyleSelected: (String) -> Unit,
    onWakeWordToggle: (Boolean) -> Unit
) {
    val palette = LocalMyraPalette.current
    val responseStyles = listOf(
        "Concise" to "Concise (Fast 1–2 sentence answers)",
        "Detailed" to "Detailed (Thorough explanations)"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsSectionCard(title = "HEY MYRA WAKE WORD") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Hands-Free \"Hey MYRA\" Detection",
                            color = palette.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Listens while app is open and idle so saying \"Hey MYRA\" wakes voice mode automatically.",
                            color = palette.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = state.wakeWordEnabled,
                        onCheckedChange = onWakeWordToggle,
                        modifier = Modifier.testTag("wake_word_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MyraColors.Crimson
                        )
                    )
                }
            }
        }
        item {
            SettingsSectionCard(title = "VOICE RESPONSE STYLE") {
                responseStyles.forEach { (key, label) ->
                    RadioOptionRow(
                        label = label,
                        selected = state.voiceResponseStyle == key,
                        onClick = { onResponseStyleSelected(key) }
                    )
                }
            }
        }
        item {
            SettingsSectionCard(title = "AI LIVE MODEL") {
                state.modelOptions.forEach { (label, id) ->
                    RadioOptionRow(
                        label = label,
                        selected = state.selectedModel == id,
                        onClick = { onModelSelected(id) }
                    )
                }
            }
        }
        item {
            SettingsSectionCard(title = "MYRA VOICE") {
                state.voiceOptions.forEach { (label, voiceId) ->
                    RadioOptionRow(
                        label = label,
                        selected = state.selectedVoice == voiceId,
                        onClick = { onVoiceSelected(voiceId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageAndPersonalityCategory(
    state: SettingsUiState,
    onLanguageSelected: (String) -> Unit,
    onPersonalitySelected: (String) -> Unit,
    onThemeModeSelected: (String) -> Unit,
    onMemoryConsentToggle: (Boolean) -> Unit,
    onClearMemoryClick: () -> Unit
) {
    val palette = LocalMyraPalette.current
    val languages = listOf(
        "Banglish" to "Banglish (বাংলা + English)",
        "Bangla" to "Bangla (বাংলা)",
        "English" to "English",
        "Hindi" to "Hindi (हिन्दी)"
    )
    val personalities = listOf(
        "GF" to "GF Mode (Caring & Warm)",
        "Professional" to "Professional Mode (Formal)",
        "Assistant" to "Assistant Mode (Friendly & Balanced)"
    )
    val themes = listOf(
        "DARK" to "Futuristic Dark (Obsidian + Neon Crimson)",
        "LIGHT" to "Clean Light (Pearl + Neon Crimson)",
        "SYSTEM" to "Follow Android System Theme"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsSectionCard(title = "APP THEME") {
                themes.forEach { (key, label) ->
                    RadioOptionRow(
                        label = label,
                        selected = state.themeMode.equals(key, ignoreCase = true),
                        onClick = { onThemeModeSelected(key) }
                    )
                }
            }
        }
        item {
            SettingsSectionCard(title = "LANGUAGE / ভাষা") {
                languages.forEach { (key, label) ->
                    RadioOptionRow(
                        label = label,
                        selected = state.languageMode == key,
                        onClick = { onLanguageSelected(key) }
                    )
                }
            }
        }
        item {
            SettingsSectionCard(title = "PERSONALITY MODE") {
                personalities.forEach { (key, label) ->
                    RadioOptionRow(
                        label = label,
                        selected = state.personalityMode == key,
                        onClick = { onPersonalitySelected(key) }
                    )
                }
            }
        }
        item {
            SettingsSectionCard(title = "USER PREFERENCE MEMORY (CONSENT)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Remember My Preferences",
                            color = palette.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Allows MYRA to save preferences locally when you say \"Remember that...\".",
                            color = palette.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = state.memoryConsentEnabled,
                        onCheckedChange = onMemoryConsentToggle,
                        modifier = Modifier.testTag("memory_consent_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MyraColors.Crimson
                        )
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onClearMemoryClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("clear_memory_button"),
                    border = BorderStroke(1.dp, MyraColors.Crimson),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "CLEAR REMEMBERED PREFERENCES",
                        color = MyraColors.Crimson,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileAndContactsCategory(
    state: SettingsUiState,
    onUserNameChange: (String) -> Unit,
    onAddPrimeContact: (String, String) -> Unit,
    onDeletePrimeContact: (Int) -> Unit
) {
    val palette = LocalMyraPalette.current
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddPrimeContactDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, number ->
                onAddPrimeContact(name, number)
                showAddDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsSectionCard(title = "YOUR NAME") {
                OutlinedTextField(
                    value = state.userName,
                    onValueChange = onUserNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user_name_input"),
                    placeholder = { Text("Your Name (e.g. Alex)", color = palette.textMuted) },
                    singleLine = true,
                    colors = myraFieldColors(),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }
        item {
            SettingsSectionCard(title = "PRIME CONTACTS") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.primeContacts.size} saved contact(s)",
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("add_prime_contact_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MyraColors.Crimson),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ADD", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (state.primeContacts.isEmpty()) {
                    Text(
                        text = "No Prime Contacts added yet.",
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    state.primeContacts.forEachIndexed { index, contact ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            color = palette.surfaceAlt,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.name.ifBlank { "Prime Contact" },
                                        color = palette.textPrimary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = contact.number,
                                        color = palette.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(
                                    onClick = { onDeletePrimeContact(index) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Remove contact",
                                        tint = MyraColors.Crimson
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemPermissionsCategory(
    state: SettingsUiState,
    onRequestRuntimePermission: (String) -> Unit,
    onOpenWriteSettingsClick: () -> Unit,
    onOpenAccessibilityClick: () -> Unit,
    onOpenOverlayClick: () -> Unit,
    onOpenNotificationAccessClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PermissionStatusCard(
                title = "MICROPHONE ACCESS",
                description = "Required for live AI voice conversations and optional \"Hey MYRA\" wake word.",
                isGranted = state.isMicGranted,
                buttonLabel = if (state.isMicGranted) "MANAGE IN SETTINGS" else "GRANT MICROPHONE",
                testTag = "perm_mic_button",
                onClick = { onRequestRuntimePermission(Manifest.permission.RECORD_AUDIO) }
            )
        }
        item {
            PermissionStatusCard(
                title = "CAMERA ACCESS",
                description = "Allows MYRA to open your front/back camera or take photos on voice command.",
                isGranted = state.isCameraGranted,
                buttonLabel = if (state.isCameraGranted) "MANAGE IN SETTINGS" else "GRANT CAMERA",
                testTag = "perm_camera_button",
                onClick = { onRequestRuntimePermission(Manifest.permission.CAMERA) }
            )
        }
        item {
            PermissionStatusCard(
                title = "CONTACTS ACCESS",
                description = "Lets MYRA look up contact numbers for confirmed calls, SMS, and WhatsApp messages.",
                isGranted = state.isContactsGranted,
                buttonLabel = if (state.isContactsGranted) "MANAGE IN SETTINGS" else "GRANT CONTACTS",
                testTag = "perm_contacts_button",
                onClick = { onRequestRuntimePermission(Manifest.permission.READ_CONTACTS) }
            )
        }
        item {
            PermissionStatusCard(
                title = "PHONE CALLS",
                description = "Allows placing calls after you explicitly confirm the recipient.",
                isGranted = state.isCallPhoneGranted,
                buttonLabel = if (state.isCallPhoneGranted) "MANAGE IN SETTINGS" else "GRANT CALL PHONE",
                testTag = "perm_call_button",
                onClick = { onRequestRuntimePermission(Manifest.permission.CALL_PHONE) }
            )
        }
        item {
            PermissionStatusCard(
                title = "LOCATION (COARSE)",
                description = "Provides city-level weather updates and current location info when requested.",
                isGranted = state.isLocationGranted,
                buttonLabel = if (state.isLocationGranted) "MANAGE IN SETTINGS" else "GRANT LOCATION",
                testTag = "perm_location_button",
                onClick = { onRequestRuntimePermission(Manifest.permission.ACCESS_COARSE_LOCATION) }
            )
        }
        item {
            PermissionStatusCard(
                title = "SCREEN BRIGHTNESS (WRITE SETTINGS)",
                description = "Lets MYRA increase or decrease screen brightness on your command.",
                isGranted = state.isWriteSettingsGranted,
                buttonLabel = if (state.isWriteSettingsGranted) "MANAGE BRIGHTNESS ACCESS" else "ALLOW MODIFY SETTINGS",
                testTag = "perm_write_settings_button",
                onClick = onOpenWriteSettingsClick
            )
        }
        item {
            PermissionStatusCard(
                title = "FLOATING SIRI ORB (DISPLAY OVER APPS)",
                description = "Displays MYRA's glowing orb on screen when you exit the app during an active session.",
                isGranted = state.isOverlayEnabled,
                buttonLabel = if (state.isOverlayEnabled) "MANAGE OVERLAY PERMISSION" else "ENABLE FLOATING ORB",
                testTag = "open_overlay_permission_button",
                onClick = onOpenOverlayClick
            )
        }
        item {
            PermissionStatusCard(
                title = "ACCESSIBILITY SERVICE (OPTIONAL)",
                description = "Enables hands-free closing of apps and system screenshots (API 28+) with explicit disclosure.",
                isGranted = state.isAccessibilityEnabled,
                buttonLabel = if (state.isAccessibilityEnabled) "MANAGE ACCESSIBILITY" else "ENABLE ACCESSIBILITY SERVICE",
                testTag = "open_accessibility_button",
                onClick = onOpenAccessibilityClick
            )
        }
        item {
            PermissionStatusCard(
                title = "NOTIFICATION READER (EXPLICIT CONSENT)",
                description = "Allows MYRA to read your recent notifications aloud only when you ask.",
                isGranted = state.isNotificationAccessEnabled,
                buttonLabel = if (state.isNotificationAccessEnabled) "MANAGE NOTIFICATION ACCESS" else "ENABLE NOTIFICATION ACCESS",
                testTag = "open_notification_access_button",
                onClick = onOpenNotificationAccessClick
            )
        }
    }
}

@Composable
private fun PermissionStatusCard(
    title: String,
    description: String,
    isGranted: Boolean,
    buttonLabel: String,
    testTag: String,
    onClick: () -> Unit
) {
    val palette = LocalMyraPalette.current
    SettingsSectionCard(title = title) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isGranted) "Status: ALLOWED ✓" else "Status: NOT GRANTED",
                color = if (isGranted) MyraColors.Emerald else MyraColors.Crimson,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            color = palette.textSecondary,
            fontSize = 12.5.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        if (isGranted) {
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(testTag),
                border = BorderStroke(1.dp, MyraColors.Emerald.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MyraColors.Emerald.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "ALLOWED (TAP TO MANAGE)",
                    color = MyraColors.Emerald,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        } else {
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(testTag),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(MyraColors.Crimson, Color(0xFFD5004C))
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = buttonLabel,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        letterSpacing = 1.1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AppUpdatesCategory(
    state: SettingsUiState,
    onCheckUpdatesClick: () -> Unit
) {
    val palette = LocalMyraPalette.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsSectionCard(title = "APP UPDATES") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Installed Version",
                        color = palette.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "v${state.appVersion}",
                        color = MyraColors.Crimson,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.updateServiceStatus,
                    color = palette.textSecondary,
                    fontSize = 12.5.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onCheckUpdatesClick,
                    enabled = !state.isCheckingUpdates,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("check_updates_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.surfaceAlt),
                    border = BorderStroke(1.dp, MyraColors.Crimson),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = state.updateButtonLabel,
                        color = MyraColors.Crimson,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalMyraPalette.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = palette.surfaceCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MyraColors.Crimson.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = title,
                color = MyraColors.Crimson,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun RadioOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalMyraPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MyraColors.Crimson,
                unselectedColor = palette.textSecondary
            )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = if (selected) palette.textPrimary else palette.textSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun GradientPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(MyraColors.Crimson, MyraColors.Violet)
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
    }
}

@Composable
private fun AddPrimeContactDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    val palette = LocalMyraPalette.current
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceCard,
        title = { Text("Add Prime Contact", color = palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = myraFieldColors()
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    colors = myraFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() || number.isNotBlank()) {
                        onAdd(name.trim(), number.trim())
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text("ADD", color = MyraColors.Crimson, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = palette.textSecondary)
            }
        }
    )
}

@Composable
private fun myraFieldColors(): androidx.compose.material3.TextFieldColors {
    val palette = LocalMyraPalette.current
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MyraColors.Crimson,
        unfocusedBorderColor = palette.borderSubtle,
        focusedTextColor = palette.textPrimary,
        unfocusedTextColor = palette.textPrimary,
        cursorColor = MyraColors.Crimson,
        focusedLabelColor = MyraColors.Crimson,
        unfocusedLabelColor = palette.textSecondary
    )
}
