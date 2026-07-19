package sk.kubdev.mynotes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import sk.kubdev.mynotes.settings.SettingsViewModel
import sk.kubdev.mynotes.settings.SettingsScreen
import sk.kubdev.mynotes.ui.screens.*
import sk.kubdev.mynotes.ui.components.AppDrawer
import sk.kubdev.mynotes.data.remote.local.entities.NoteType

// A sealed class to define all our app's screens
sealed class Screen(val route: String) {
    object NoteList : Screen("noteList")
    object NoteDetail : Screen("noteDetail/{noteId}") {
        fun createRoute(noteId: Int) = "noteDetail/$noteId"
    }

    object ToDoList : Screen("todoList/{noteId}") {
        fun createRoute(noteId: Int) = "todoList/$noteId"
    }

    object Archive : Screen("archive")
    object Bin : Screen("bin")
    object Settings : Screen("settings")
    object Backup : Screen("backup")
    object Import : Screen("import")
    object About : Screen("about")

    // ✅ NEW: Collaboration screens
    object PendingInvitations : Screen("pending_invitations")
    object SignIn : Screen("sign_in")
    object CollaborativeTodo : Screen("collaborative_todo/{collaborativeNoteId}") {
        fun createRoute(collaborativeNoteId: String) = "collaborative_todo/$collaborativeNoteId"
    }
}

@Composable
fun AppNavigation(
    viewModel: NoteViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()

    // ✅ FIXED: Add drawer state and scope
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // ✅ FIXED: Wrap everything in ModalNavigationDrawer
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                navController = navController,
                closeDrawer = {
                    scope.launch { drawerState.close() }
                },
                viewModel = viewModel // ✅ Pass the viewModel to AppDrawer
            )
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.NoteList.route
        ) {
            // ✅ FIXED: Main Notes List Screen - pass drawer control functions
            composable(Screen.NoteList.route) {
                NoteListScreen(
                    navController = navController,
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel,
                    // ✅ FIXED: Pass drawer functions instead of drawerState
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            }

            // Settings Screen
            composable(Screen.Settings.route) {
                SettingsScreen(
                    navController = navController,
                    settingsViewModel = settingsViewModel
                )
            }

            // ✅ FIXED: Archive Screen - now using your actual screen
            composable(Screen.Archive.route) {
                ArchiveScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            // ✅ FIXED: Bin Screen - now using your actual screen
            composable(Screen.Bin.route) {
                BinScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            // ✅ FIXED: Backup Screen - now using your actual screen
            composable(Screen.Backup.route) {
                BackupScreen(
                    navController = navController,
                    noteViewModel = viewModel
                )
            }

            // Import Screen - import notes from Evernote / Google Keep / plain text
            composable(Screen.Import.route) {
                ImportScreen(
                    navController = navController,
                    noteViewModel = viewModel
                )
            }

            // ✅ FIXED: About Screen - now using your actual screen
            composable(Screen.About.route) {
                AboutScreen(navController = navController)
            }

            // Sign-In Screen (Google account for collaboration & sync)
            composable(Screen.SignIn.route) {
                SignInScreen(navController, viewModel)
            }

            // ✅ NEW: Pending Invitations Screen
            composable(Screen.PendingInvitations.route) {
                PendingInvitationsScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            // Collaborative editor entry: "new"/"new_note" create a fresh shared
            // checklist/text note; an existing id first loads the note's TYPE and
            // then opens the matching editor (shared checklists and shared text
            // notes use different screens).
            composable(
                route = Screen.CollaborativeTodo.route,
                arguments = listOf(navArgument("collaborativeNoteId") { type = NavType.StringType })
            ) { backStackEntry ->
                val collaborativeNoteId = backStackEntry.arguments?.getString("collaborativeNoteId") ?: ""

                if (collaborativeNoteId == "new" || collaborativeNoteId == "new_note") {
                    val isTextNote = collaborativeNoteId == "new_note"
                    LaunchedEffect(Unit) {
                        viewModel.createCollaborativeNote(
                            title = if (isTextNote) "New Collaborative Note" else "New Collaborative Todo",
                            lines = emptyList(),
                            noteType = if (isTextNote) NoteType.TEXT else NoteType.CHECKLIST
                        ) { newId ->
                            navController.navigate("collaborative_todo/$newId") {
                                popUpTo("collaborative_todo/$collaborativeNoteId") { inclusive = true }
                            }
                        }
                    }

                    // Show loading screen while creating
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(if (isTextNote) "Creating collaborative note..." else "Creating collaborative todo...")
                        }
                    }
                } else {
                    var noteType by remember(collaborativeNoteId) { mutableStateOf<String?>(null) }

                    LaunchedEffect(collaborativeNoteId) {
                        viewModel.getCollaborativeNoteById(collaborativeNoteId) { result ->
                            noteType = result.getOrNull()?.type ?: "CHECKLIST"
                        }
                    }

                    when (noteType) {
                        null -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                        "TEXT" -> NoteDetailScreen(
                            noteIdArg = 0,
                            navController = navController,
                            viewModel = viewModel,
                            isCollaborative = true,
                            collaborativeNoteId = collaborativeNoteId
                        )
                        else -> ToDoListScreen(
                            noteIdArg = 0, // Not used for collaborative
                            navController = navController,
                            viewModel = viewModel,
                            isCollaborative = true,
                            collaborativeNoteId = collaborativeNoteId
                        )
                    }
                }
            }

            // Note Detail Screen
            composable(
                route = Screen.NoteDetail.route,
                arguments = listOf(navArgument("noteId") { type = NavType.IntType })
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getInt("noteId") ?: 0
                NoteDetailScreen(
                    noteIdArg = noteId,
                    navController = navController,
                    viewModel = viewModel
                )
            }

            // ToDoList Screen (Local)
            composable(
                route = Screen.ToDoList.route,
                arguments = listOf(navArgument("noteId") { type = NavType.IntType })
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getInt("noteId") ?: 0
                ToDoListScreen(
                    noteIdArg = noteId,
                    navController = navController,
                    viewModel = viewModel,
                    isCollaborative = false,
                    collaborativeNoteId = null
                )
            }

        }
    }
}
