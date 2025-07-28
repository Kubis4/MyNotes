package sk.kubdev.selfnote.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import sk.kubdev.selfnote.R
import sk.kubdev.selfnote.Screen
import sk.kubdev.selfnote.settings.model.*
import android.os.Build
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel
) {
    BackHandler {
        if (!navController.popBackStack()) {
            navController.navigate(Screen.NoteList.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val showPasswordDialog by settingsViewModel.showPasswordDialog.collectAsStateWithLifecycle()
    val showLanguageRestartDialog by settingsViewModel.showLanguageRestartDialog.collectAsStateWithLifecycle()

    // SECURE AUTH CHANGE STATE COLLECTIONS
    val showAuthChangeDialog by settingsViewModel.showAuthChangeDialog.collectAsStateWithLifecycle()
    val authChangeError by settingsViewModel.authChangeError.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!navController.popBackStack()) {
                                navController.navigate(Screen.NoteList.route) {
                                    popUpTo(0) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Appearance Section
            item {
                SettingsSection(
                    title = stringResource(R.string.settings_appearance),
                    icon = Icons.Default.Palette
                )
            }

            item {
                ThemeSettingCard(
                    currentTheme = settings.theme,
                    onThemeChange = settingsViewModel::updateTheme
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Color Scheme Section
            item {
                ColorSchemeSettingCard(
                    currentColorScheme = settings.colorScheme,
                    onColorSchemeChange = settingsViewModel::updateColorScheme
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Language Section
            item {
                SettingsSection(
                    title = stringResource(R.string.settings_language),
                    icon = Icons.Default.Language
                )
            }

            item {
                LanguageSettingCard(
                    currentLanguage = settings.language,
                    onLanguageChange = settingsViewModel::updateLanguage
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Security Section
            item {
                SettingsSection(
                    title = stringResource(R.string.settings_security),
                    icon = Icons.Default.Security
                )
            }

            item {
                SecuritySettingsCard(
                    biometricEnabled = settings.biometricEnabled,
                    passwordEnabled = settings.passwordEnabled,
                    onBiometricToggle = settingsViewModel::updateBiometricEnabled,
                    onPasswordToggle = settingsViewModel::updatePasswordEnabled,
                    isBiometricAvailable = settingsViewModel.isBiometricAvailable()
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Note Interaction Section
            item {
                SettingsSection(
                    title = stringResource(R.string.settings_note_interactions),
                    icon = Icons.Default.SwipeLeft
                )
            }

            item {
                NoteSwipeSettingsCard(
                    noteSwipeEnabled = settings.noteSwipeEnabled,
                    swipeLeftAction = settings.swipeLeftAction,
                    swipeRightAction = settings.swipeRightAction,
                    onNoteSwipeToggle = settingsViewModel::updateNoteSwipeEnabled,
                    onSwipeLeftActionChange = settingsViewModel::updateSwipeLeftAction,
                    onSwipeRightActionChange = settingsViewModel::updateSwipeRightAction
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Password Setup Dialog
    if (showPasswordDialog) {
        PasswordSetupDialog(
            onConfirm = settingsViewModel::confirmPasswordSetup,
            onDismiss = settingsViewModel::dismissPasswordDialog
        )
    }

    // Language Restart Dialog
    if (showLanguageRestartDialog) {
        AlertDialog(
            onDismissRequest = { settingsViewModel.dismissLanguageRestartDialog() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.language_changed_title))
                }
            },
            text = {
                Text(stringResource(R.string.language_changed_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsViewModel.dismissLanguageRestartDialog()
                    }
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }

    // SECURE AUTH CHANGE DIALOGS
    showAuthChangeDialog?.let { changeType ->
        when (changeType) {
            AuthChangeType.SWITCH_TO_BIOMETRIC -> {
                var currentPassword by remember { mutableStateOf("") }
                var passwordVisible by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = {
                        settingsViewModel.dismissAuthChangeDialog()
                        currentPassword = ""
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.auth_switch_to_biometric_title))
                        }
                    },
                    text = {
                        Column {
                            Text(stringResource(R.string.auth_switch_to_biometric_message))

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = currentPassword,
                                onValueChange = {
                                    currentPassword = it
                                    settingsViewModel.clearAuthChangeError()
                                },
                                label = { Text(stringResource(R.string.auth_current_password)) },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (passwordVisible) {
                                                stringResource(R.string.auth_hide_password)
                                            } else {
                                                stringResource(R.string.auth_show_password)
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                isError = authChangeError.isNotEmpty(),
                                singleLine = true
                            )

                            if (authChangeError.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Text(
                                        text = authChangeError,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                settingsViewModel.verifyAndPerformAuthChange(
                                    AuthChangeType.SWITCH_TO_BIOMETRIC,
                                    currentPassword
                                )
                                currentPassword = ""
                            },
                            enabled = currentPassword.isNotBlank()
                        ) {
                            Text(stringResource(R.string.auth_switch_to_biometric_button))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                settingsViewModel.dismissAuthChangeDialog()
                                currentPassword = ""
                            }
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }

            AuthChangeType.SWITCH_TO_PASSWORD -> {
                AlertDialog(
                    onDismissRequest = { settingsViewModel.dismissAuthChangeDialog() },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.auth_switch_to_password_title))
                        }
                    },
                    text = {
                        Column {
                            Text(stringResource(R.string.auth_switch_to_password_message))

                            if (authChangeError.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Text(
                                        text = authChangeError,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                settingsViewModel.verifyAndPerformAuthChange(AuthChangeType.SWITCH_TO_PASSWORD)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.auth_verify_and_switch))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { settingsViewModel.dismissAuthChangeDialog() }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }

            AuthChangeType.DISABLE_BIOMETRIC -> {
                AlertDialog(
                    onDismissRequest = { settingsViewModel.dismissAuthChangeDialog() },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.auth_disable_biometric_title))
                        }
                    },
                    text = {
                        Column {
                            Text(stringResource(R.string.auth_disable_biometric_message))

                            if (authChangeError.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Text(
                                        text = authChangeError,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                settingsViewModel.verifyAndPerformAuthChange(AuthChangeType.DISABLE_BIOMETRIC)
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.auth_verify_and_disable))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { settingsViewModel.dismissAuthChangeDialog() }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }

            AuthChangeType.DISABLE_PASSWORD -> {
                var currentPassword by remember { mutableStateOf("") }
                var passwordVisible by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = {
                        settingsViewModel.dismissAuthChangeDialog()
                        currentPassword = ""
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.auth_disable_password_title))
                        }
                    },
                    text = {
                        Column {
                            Text(stringResource(R.string.auth_disable_password_message))

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = currentPassword,
                                onValueChange = {
                                    currentPassword = it
                                    settingsViewModel.clearAuthChangeError()
                                },
                                label = { Text(stringResource(R.string.auth_current_password)) },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (passwordVisible) {
                                                stringResource(R.string.auth_hide_password)
                                            } else {
                                                stringResource(R.string.auth_show_password)
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                isError = authChangeError.isNotEmpty(),
                                singleLine = true
                            )

                            if (authChangeError.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Text(
                                        text = authChangeError,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                settingsViewModel.verifyAndPerformAuthChange(
                                    AuthChangeType.DISABLE_PASSWORD,
                                    currentPassword
                                )
                                currentPassword = ""
                            },
                            enabled = currentPassword.isNotBlank(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.auth_verify_and_disable))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                settingsViewModel.dismissAuthChangeDialog()
                                currentPassword = ""
                            }
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun ThemeSettingCard(
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.theme_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Column(
                modifier = Modifier.selectableGroup()
            ) {
                AppTheme.entries.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = currentTheme == theme,
                                onClick = { onThemeChange(theme) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTheme == theme,
                            onClick = null
                        )
                        Text(
                            text = when (theme) {
                                AppTheme.LIGHT -> stringResource(R.string.theme_light)
                                AppTheme.DARK -> stringResource(R.string.theme_dark)
                                AppTheme.SYSTEM -> stringResource(R.string.theme_system)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ColorSchemeSettingCard(
    currentColorScheme: AppColorScheme,
    onColorSchemeChange: (AppColorScheme) -> Unit
) {
    var showAllColors by remember { mutableStateOf(false) }

    val mainColors = listOf(
        AppColorScheme.SKY_BLUE,
        AppColorScheme.OCEAN_BLUE,
        AppColorScheme.NAVY,
        AppColorScheme.MINT,
        AppColorScheme.FOREST,
        AppColorScheme.OLIVE
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.color_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    mainColors.take(3).forEach { colorScheme ->
                        ColorOption(
                            colorScheme = colorScheme,
                            isSelected = currentColorScheme == colorScheme,
                            onClick = { onColorSchemeChange(colorScheme) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    mainColors.drop(3).forEach { colorScheme ->
                        ColorOption(
                            colorScheme = colorScheme,
                            isSelected = currentColorScheme == colorScheme,
                            onClick = { onColorSchemeChange(colorScheme) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showAllColors = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.color_view_all))
                Spacer(modifier = Modifier.width(4.dp))
                Text("(${AppColorScheme.values().size})")
            }
        }
    }

    if (showAllColors) {
        AllColorsDialog(
            currentColorScheme = currentColorScheme,
            onColorSchemeChange = { colorScheme ->
                onColorSchemeChange(colorScheme)
                showAllColors = false
            },
            onDismiss = { showAllColors = false }
        )
    }
}

@Composable
fun AllColorsDialog(
    currentColorScheme: AppColorScheme,
    onColorSchemeChange: (AppColorScheme) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.color_choose_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(AppColorScheme.values()) { colorScheme ->
                        DetailedColorOption(
                            colorScheme = colorScheme,
                            isSelected = currentColorScheme == colorScheme,
                            onClick = { onColorSchemeChange(colorScheme) }
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                Color(currentColorScheme.primaryColor),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.color_selected, currentColorScheme.displayName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun ColorOption(
    colorScheme: AppColorScheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(colorScheme.primaryColor)
        ),
        border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 4.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = colorScheme.emoji,
                    style = MaterialTheme.typography.headlineSmall
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.selected_indicator),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DetailedColorOption(
    colorScheme: AppColorScheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(colorScheme.primaryColor)
        ),
        border = if (isSelected) BorderStroke(3.dp, Color.White) else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 12.dp else 4.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = colorScheme.emoji,
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = colorScheme.displayName.split(" ").first(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
                if (isSelected) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.selected_indicator),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LanguageSettingCard(
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.language_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Column(
                modifier = Modifier.selectableGroup()
            ) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = currentLanguage == language,
                                onClick = { onLanguageChange(language) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentLanguage == language,
                            onClick = null
                        )

                        Text(
                            text = language.flag,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 12.dp)
                        )

                        Column(
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (language) {
                                        AppLanguage.ENGLISH -> stringResource(R.string.language_english)
                                        AppLanguage.SLOVAK -> stringResource(R.string.language_slovak)
                                        AppLanguage.FRENCH -> stringResource(R.string.language_french)
                                        AppLanguage.GERMAN -> stringResource(R.string.language_german)
                                        AppLanguage.ITALIAN -> stringResource(R.string.language_italian)
                                        AppLanguage.SPANISH -> stringResource(R.string.language_spanish)
                                        AppLanguage.CZECH -> stringResource(R.string.language_czech)
                                        AppLanguage.SYSTEM -> stringResource(R.string.language_system)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )

                                // AI Translation Badge
                                if (language.isAITranslated) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        modifier = Modifier,
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = "AI",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }

                            if (language != AppLanguage.SYSTEM) {
                                Text(
                                    text = if (language.isAITranslated) {
                                        stringResource(R.string.language_ai_translated, language.displayName)
                                    } else {
                                        language.displayName
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.language_system_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // Info cards
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Icons.Default.Check
                        } else {
                            Icons.Default.Info
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            stringResource(R.string.language_instant_change)
                        } else {
                            stringResource(R.string.language_quick_restart)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            // AI Translation Notice
            if (AppLanguage.entries.any { it.isAITranslated }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.language_ai_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}



@Composable
fun SecuritySettingsCard(
    biometricEnabled: Boolean,
    passwordEnabled: Boolean,
    onBiometricToggle: (Boolean) -> Unit,
    onPasswordToggle: (Boolean) -> Unit,
    isBiometricAvailable: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.security_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // SMART BIOMETRIC TOGGLE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isBiometricAvailable) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        }
                    )
                    Column(
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.security_biometric),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isBiometricAvailable) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            }
                        )
                        if (!isBiometricAvailable) {
                            Text(
                                text = stringResource(R.string.security_biometric_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                Switch(
                    checked = biometricEnabled,
                    onCheckedChange = onBiometricToggle,
                    enabled = isBiometricAvailable
                )
            }

            // SMART PASSWORD TOGGLE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.security_password),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (!isBiometricAvailable && !passwordEnabled) {
                            Text(
                                text = stringResource(R.string.security_password_recommended),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                Switch(
                    checked = passwordEnabled,
                    onCheckedChange = onPasswordToggle
                )
            }

            // SECURITY STATUS INFO
            if (biometricEnabled || passwordEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                biometricEnabled -> stringResource(R.string.security_status_biometric)
                                passwordEnabled -> stringResource(R.string.security_status_password)
                                else -> stringResource(R.string.security_status_warning)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            } else if (!isBiometricAvailable) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.security_tip_biometric),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoteSwipeSettingsCard(
    noteSwipeEnabled: Boolean,
    swipeLeftAction: SwipeAction,
    swipeRightAction: SwipeAction,
    onNoteSwipeToggle: (Boolean) -> Unit,
    onSwipeLeftActionChange: (SwipeAction) -> Unit,
    onSwipeRightActionChange: (SwipeAction) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.swipe_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SwipeLeft,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.swipe_enable),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                Switch(
                    checked = noteSwipeEnabled,
                    onCheckedChange = onNoteSwipeToggle
                )
            }

            if (noteSwipeEnabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(R.string.swipe_left_action),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                SwipeActionSelector(
                    selectedAction = swipeLeftAction,
                    onActionChange = onSwipeLeftActionChange,
                    direction = "Left"
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.swipe_right_action),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                SwipeActionSelector(
                    selectedAction = swipeRightAction,
                    onActionChange = onSwipeRightActionChange,
                    direction = "Right"
                )
            }
        }
    }
}

@Composable
fun SwipeActionSelector(
    selectedAction: SwipeAction,
    onActionChange: (SwipeAction) -> Unit,
    direction: String
) {
    Column(
        modifier = Modifier.selectableGroup()
    ) {
        SwipeAction.entries.forEach { action ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selectedAction == action,
                        onClick = { onActionChange(action) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedAction == action,
                    onClick = null,
                    modifier = Modifier.size(20.dp)
                )

                Text(
                    text = action.icon,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )

                Column(
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = when (action) {
                            SwipeAction.DELETE -> stringResource(R.string.swipe_action_delete)
                            SwipeAction.ARCHIVE -> stringResource(R.string.swipe_action_archive)
                            SwipeAction.NONE -> stringResource(R.string.swipe_action_none)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = when (action) {
                            SwipeAction.DELETE -> stringResource(R.string.swipe_action_delete_desc)
                            SwipeAction.ARCHIVE -> stringResource(R.string.swipe_action_archive_desc)
                            SwipeAction.NONE -> stringResource(R.string.swipe_action_none_desc)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PasswordSetupDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    val isValid = password.isNotBlank() && password == confirmPassword && password.length >= 4

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.password_setup_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_hint)) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) {
                                    stringResource(R.string.auth_hide_password)
                                } else {
                                    stringResource(R.string.auth_show_password)
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(R.string.password_confirm_hint)) },
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (confirmPasswordVisible) {
                                    stringResource(R.string.auth_hide_password)
                                } else {
                                    stringResource(R.string.auth_show_password)
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isError = confirmPassword.isNotBlank() && password != confirmPassword,
                    singleLine = true
                )
                if (confirmPassword.isNotBlank() && password != confirmPassword) {
                    Text(
                        text = stringResource(R.string.password_mismatch),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = isValid
            ) {
                Text(stringResource(R.string.password_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
