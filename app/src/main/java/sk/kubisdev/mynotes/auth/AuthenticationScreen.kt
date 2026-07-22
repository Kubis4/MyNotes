package sk.kubisdev.mynotes.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.kubisdev.mynotes.R

@Composable
fun AuthenticationScreen(
    authManager: AuthenticationManager,
    onAuthenticated: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val isAuthenticated by authManager.isAuthenticated.collectAsStateWithLifecycle()

    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val isBiometricEnabled = authManager.isBiometricEnabled()
    val isPasswordEnabled = authManager.isPasswordEnabled()

    // Pre-load all string resources in composable scope
    val authAuthenticationFailedMessage = stringResource(R.string.auth_authentication_failed)
    val authInvalidPasswordMessage = stringResource(R.string.auth_invalid_password)

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            onAuthenticated()
        }
    }

    // 🆕 AUTO-TRIGGER BIOMETRIC AUTHENTICATION
    LaunchedEffect(isBiometricEnabled) {
        if (isBiometricEnabled && !isAuthenticated) {
            authManager.authenticateWithBiometric(
                activity = activity,
                onSuccess = onAuthenticated,
                onError = { error -> errorMessage = error },
                onFailed = { errorMessage = authAuthenticationFailedMessage } // Use pre-loaded string
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                sk.kubisdev.mynotes.ui.components.SectionIconCircle(
                    icon = Icons.Default.Lock,
                    size = 72.dp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.auth_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.auth_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Password Authentication (if enabled)
                if (isPasswordEnabled) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = ""
                        },
                        label = { Text(stringResource(R.string.auth_password_label)) },
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
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (password.isNotBlank()) {
                                    isLoading = true
                                    if (authManager.verifyPassword(password)) {
                                        onAuthenticated()
                                    } else {
                                        errorMessage = authInvalidPasswordMessage // Use pre-loaded string
                                        password = ""
                                    }
                                    isLoading = false
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (password.isNotBlank()) {
                                isLoading = true
                                if (authManager.verifyPassword(password)) {
                                    onAuthenticated()
                                } else {
                                    errorMessage = authInvalidPasswordMessage // Use pre-loaded string
                                    password = ""
                                }
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = password.isNotBlank() && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(R.string.auth_unlock))
                        }
                    }
                }

                // Biometric Button (if enabled and password is also enabled)
                if (isBiometricEnabled && isPasswordEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.auth_or),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (isBiometricEnabled) {
                    OutlinedButton(
                        onClick = {
                            authManager.authenticateWithBiometric(
                                activity = activity,
                                onSuccess = onAuthenticated,
                                onError = { error -> errorMessage = error },
                                onFailed = { errorMessage = authAuthenticationFailedMessage } // Use pre-loaded string
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.auth_use_biometric))
                    }

                    // Fallback when fingerprint/face repeatedly isn't recognized and no
                    // app password is set up: hand off to the device's own PIN/pattern/password.
                    if (!isPasswordEnabled && authManager.canAuthenticateWithDeviceCredential()) {
                        TextButton(
                            onClick = {
                                authManager.authenticateWithDeviceCredential(
                                    activity = activity,
                                    onSuccess = onAuthenticated,
                                    onError = { error -> errorMessage = error }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.auth_use_device_credential))
                        }
                    }
                }

                // Error Message
                if (errorMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
