package com.myra.assistant.ui.settings

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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

private val MyraBg = Color(0xFF050508)
private val MyraSurface = Color(0xFF111118)
private val MyraSurfaceAlt = Color(0xFF181822)
private val MyraCrimson = Color(0xFFFF1744)
private val MyraViolet = Color(0xFFD500F9)
private val MyraTextPrimary = Color(0xFFF3F3F7)
private val MyraTextSecondary = Color(0xFF9E9EAE)
private val MyraSuccess = Color(0xFF00E676)

private val MyraDarkScheme = darkColorScheme(
    primary = MyraCrimson,
    secondary = MyraViolet,
    background = MyraBg,
    surface = MyraSurface,
    onPrimary = Color.White,
    onBackground = MyraTextPrimary,
    onSurface = MyraTextPrimary
)

@Composable
fun MyraSettingsScreen(
    state: SettingsUiState,
    onApiKeyChange: (String) -> Unit,
    onUserNameChange: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onVoiceSelected: (String) -> Unit,
    onPersonalitySelected: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onAddPrimeContact: (String, String) -> Unit,
    onDeletePrimeContact: (Int) -> Unit,
    onTestApiClick: () -> Unit,
    onOpenAccessibilityClick: () -> Unit,
    onCheckUpdatesClick: () -> Unit,
    onSaveClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }

    BackHandler(enabled = selectedCategory != null) {
        selectedCategory = null
    }

    MaterialTheme(colorScheme = MyraDarkScheme) {
        Scaffold(
            containerColor = MyraBg,
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
                            .background(MyraBg)
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
                                onVoiceSelected = onVoiceSelected
                            )
                            SettingsCategory.LANGUAGE_PERSONALITY -> LanguageAndPersonalityCategory(
                                state = state,
                                onLanguageSelected = onLanguageSelected,
                                onPersonalitySelected = onPersonalitySelected
                            )
                            SettingsCategory.PROFILE_CONTACTS -> ProfileAndContactsCategory(
                                state = state,
                                onUserNameChange = onUserNameChange,
                                onAddPrimeContact = onAddPrimeContact,
                                onDeletePrimeContact = onDeletePrimeContact
                            )
                            SettingsCategory.SYSTEM_PERMISSIONS -> SystemPermissionsCategory(
                                state = state,
                                onOpenAccessibilityClick = onOpenAccessibilityClick
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
            .padding(horizontal = 12.dp, vertical = 12.dp),
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
                tint = MyraCrimson
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            color = MyraCrimson,
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
                accentColor = if (state.apiKey.isNotBlank()) MyraSuccess else MyraCrimson,
                testTag = "category_api",
                onClick = { onSelectCategory(SettingsCategory.API_CONNECTION) }
            )
        }
        item {
            CategoryTile(
                icon = Icons.Rounded.GraphicEq,
                title = SettingsCategory.VOICE_MODEL.title,
                subtitle = "$activeModelLabel • Voice: ${state.selectedVoice}",
                testTag = "category_voice_model",
                onClick = { onSelectCategory(SettingsCategory.VOICE_MODEL) }
            )
        }
        item {
            CategoryTile(
                icon = Icons.Rounded.Translate,
                title = SettingsCategory.LANGUAGE_PERSONALITY.title,
                subtitle = "${state.languageMode} • ${state.personalityMode} Mode",
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
                subtitle = if (state.isAccessibilityEnabled) "Accessibility: Active" else "Accessibility: Disabled • Tap to enable",
                accentColor = if (state.isAccessibilityEnabled) MyraSuccess else MyraCrimson,
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
    accentColor: Color = MyraCrimson,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        color = MyraSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0x22FF1744))
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
                    color = MyraTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = MyraTextSecondary,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Open $title",
                tint = MyraTextSecondary
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
                    placeholder = { Text("Enter Gemini API Key", color = Color(0xFF666677)) },
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
                                tint = MyraTextSecondary
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
                    colors = ButtonDefaults.buttonColors(containerColor = MyraCrimson),
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
                    color = MyraTextSecondary,
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
    onVoiceSelected: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
    onPersonalitySelected: (String) -> Unit
) {
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
    }
}

@Composable
private fun ProfileAndContactsCategory(
    state: SettingsUiState,
    onUserNameChange: (String) -> Unit,
    onAddPrimeContact: (String, String) -> Unit,
    onDeletePrimeContact: (Int) -> Unit
) {
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
                    placeholder = { Text("Your Name (e.g. Alex)", color = Color(0xFF666677)) },
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
                        color = MyraTextSecondary,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("add_prime_contact_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MyraCrimson),
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
                        color = MyraTextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    state.primeContacts.forEachIndexed { index, contact ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            color = MyraSurfaceAlt,
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
                                        color = MyraTextPrimary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = contact.number,
                                        color = MyraTextSecondary,
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
                                        tint = MyraCrimson
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
    onOpenAccessibilityClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsSectionCard(title = "ACCESSIBILITY SERVICE") {
                Text(
                    text = if (state.isAccessibilityEnabled) "Status: ACTIVE" else "Status: DISABLED",
                    color = if (state.isAccessibilityEnabled) MyraSuccess else MyraCrimson,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enable MYRA Accessibility Helper in Android Settings to perform hands-free voice actions across apps.",
                    color = MyraTextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onOpenAccessibilityClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_accessibility_button"),
                    border = BorderStroke(1.dp, MyraCrimson),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "OPEN ACCESSIBILITY SETTINGS",
                        color = MyraCrimson,
                        fontWeight = FontWeight.Bold
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
                        color = MyraTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "v${state.appVersion}",
                        color = MyraCrimson,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.updateServiceStatus,
                    color = MyraTextSecondary,
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
                    colors = ButtonDefaults.buttonColors(containerColor = MyraSurfaceAlt),
                    border = BorderStroke(1.dp, MyraCrimson),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = state.updateButtonLabel,
                        color = MyraCrimson,
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MyraSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0x1FFF1744))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = title,
                color = MyraCrimson,
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
                selectedColor = MyraCrimson,
                unselectedColor = MyraTextSecondary
            )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = if (selected) MyraTextPrimary else MyraTextSecondary,
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
                    colors = listOf(MyraCrimson, MyraViolet)
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
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MyraSurface,
        title = { Text("Add Prime Contact", color = MyraTextPrimary) },
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
                Text("ADD", color = MyraCrimson, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = MyraTextSecondary)
            }
        }
    )
}

@Composable
private fun myraFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MyraCrimson,
    unfocusedBorderColor = Color(0xFF2B2B3A),
    focusedTextColor = MyraTextPrimary,
    unfocusedTextColor = MyraTextPrimary,
    cursorColor = MyraCrimson,
    focusedLabelColor = MyraCrimson,
    unfocusedLabelColor = MyraTextSecondary
)
