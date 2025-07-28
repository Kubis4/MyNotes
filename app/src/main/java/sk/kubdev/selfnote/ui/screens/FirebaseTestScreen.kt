package sk.kubdev.selfnote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import sk.kubdev.selfnote.NoteViewModel
import sk.kubdev.selfnote.NoteLine
import sk.kubdev.selfnote.data.remote.local.entities.NoteType
import sk.kubdev.selfnote.LineType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirebaseTestScreen(
    navController: NavController,
    viewModel: NoteViewModel = hiltViewModel()
) {
    var testResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val collaborativeNotes by viewModel.collaborativeNotes.collectAsStateWithLifecycle()
    val pendingInvites by viewModel.pendingInvites.collectAsStateWithLifecycle()
    val collaborationError by viewModel.collaborationError.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startCollaborativeSync()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Firebase Test") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Firebase Connection Tests
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Firebase Connection Tests",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Test buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    isLoading = true
                                    testResults = testResults + "Testing Firebase connection..."
                                    viewModel.testFirebaseConnection()
                                },
                                enabled = !isLoading
                            ) {
                                Text("Test Connection")
                            }

                            Button(
                                onClick = {
                                    testResults = testResults + "User signed in: ${viewModel.isUserSignedIn()}"
                                    testResults = testResults + "User email: ${viewModel.getCurrentUserEmail() ?: "Not signed in"}"
                                }
                            ) {
                                Text("Check Auth")
                            }
                        }
                    }
                }
            }

            // Google Authentication
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Google Authentication",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    testResults = testResults + "Starting Google Sign-In..."
                                    viewModel.signInWithGoogle(context) { success, error ->
                                        testResults = testResults + if (success) {
                                            "✅ Google Sign-In successful!"
                                        } else {
                                            "❌ Google Sign-In failed: $error"
                                        }
                                        isLoading = false
                                    }
                                    isLoading = true
                                },
                                enabled = !isLoading
                            ) {
                                Text("🔐 Sign In with Google")
                            }

                            Button(
                                onClick = {
                                    viewModel.signOut()
                                    testResults = testResults + "Signed out"
                                }
                            ) {
                                Text("Sign Out")
                            }
                        }
                    }
                }
            }

            // Email/Password Authentication (for testing)
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Email/Password (Testing Only)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        var email by remember { mutableStateOf("test@example.com") }
                        var password by remember { mutableStateOf("password123") }
                        var displayName by remember { mutableStateOf("Test User") }

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Display Name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    testResults = testResults + "Attempting email sign up..."
                                    viewModel.signUp(email, password, displayName) { success, error ->
                                        testResults = testResults + if (success) {
                                            "✅ Email sign up successful!"
                                        } else {
                                            "❌ Email sign up failed: $error"
                                        }
                                    }
                                }
                            ) {
                                Text("Email Sign Up")
                            }

                            Button(
                                onClick = {
                                    testResults = testResults + "Attempting email sign in..."
                                    viewModel.signIn(email, password) { success, error ->
                                        testResults = testResults + if (success) {
                                            "✅ Email sign in successful!"
                                        } else {
                                            "❌ Email sign in failed: $error"
                                        }
                                    }
                                }
                            ) {
                                Text("Email Sign In")
                            }
                        }
                    }
                }
            }

            // Collaborative Notes Tests
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Collaborative Notes Tests",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                testResults = testResults + "Creating test collaborative note..."
                                viewModel.createCollaborativeNote(
                                    title = "Test Collaborative Note",
                                    lines = listOf(
                                        NoteLine(type = LineType.CHECKLIST, content = "First test item"),
                                        NoteLine(type = LineType.CHECKLIST, content = "Second test item")
                                    ),
                                    noteType = NoteType.CHECKLIST
                                ) { noteId ->
                                    testResults = testResults + "✅ Created collaborative note: $noteId"
                                }
                            },
                            enabled = viewModel.isUserSignedIn()
                        ) {
                            Text("Create Test Note")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Your Collaborative Notes: ${collaborativeNotes.size}")
                        collaborativeNotes.take(3).forEach { note ->
                            Text(
                                text = "• ${note.title} (${note.lastEditedByEmail})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

// Invitation Tests
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Invitation Tests",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        var inviteEmail by remember { mutableStateOf("friend@example.com") }

                        OutlinedTextField(
                            value = inviteEmail,
                            onValueChange = { inviteEmail = it },
                            label = { Text("Email to Invite") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (collaborativeNotes.isNotEmpty()) {
                                    val firstNote = collaborativeNotes.first()
                                    testResults = testResults + "Sending invitation..."
                                    viewModel.sendCollaborationInvite(firstNote.id, inviteEmail) { success ->
                                        testResults = testResults + if (success) {
                                            "✅ Invitation sent to $inviteEmail"
                                        } else {
                                            "❌ Failed to send invitation"
                                        }
                                    }
                                } else {
                                    testResults = testResults + "❌ No collaborative notes to invite to"
                                }
                            },
                            enabled = viewModel.isUserSignedIn() && collaborativeNotes.isNotEmpty()
                        ) {
                            Text("Send Invitation")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Pending Invites: ${pendingInvites.size}")
                        pendingInvites.take(3).forEach { invite ->
                            Text(
                                // ✅ FIXED: Use correct field names from your NoteInvite data class
                                text = "• ${invite.noteTitle} from ${invite.senderDisplayName ?: invite.senderEmail}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Error Display
            collaborationError?.let { error ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Test Results
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Test Results",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            TextButton(
                                onClick = { testResults = emptyList() }
                            ) {
                                Text("Clear")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (testResults.isEmpty()) {
                            Text(
                                text = "No test results yet. Run some tests above!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        } else {
                            testResults.forEach { result ->
                                Text(
                                    text = "• $result",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Loading indicator
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
