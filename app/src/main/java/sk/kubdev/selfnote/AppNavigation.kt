package sk.kubdev.selfnote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import sk.kubdev.selfnote.auth.AuthenticationManager
import sk.kubdev.selfnote.settings.SettingsViewModel
import sk.kubdev.selfnote.settings.SettingsScreen
import sk.kubdev.selfnote.ui.screens.*
import sk.kubdev.selfnote.ui.components.AppDrawer

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
    object About : Screen("about")

    // ✅ NEW: Collaboration screens
    object PendingInvitations : Screen("pending_invitations")
    object FirebaseTest : Screen("firebase_test")
    object CollaborativeTodo : Screen("collaborative_todo/{collaborativeNoteId}") {
        fun createRoute(collaborativeNoteId: String) = "collaborative_todo/$collaborativeNoteId"
    }
}

@Composable
fun AppNavigation(
    viewModel: NoteViewModel,
    authManager: AuthenticationManager,
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
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel
                )
            }

            // ✅ FIXED: Bin Screen - now using your actual screen
            composable(Screen.Bin.route) {
                BinScreen(
                    navController = navController,
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel
                )
            }

            // ✅ FIXED: Backup Screen - now using your actual screen
            composable(Screen.Backup.route) {
                BackupScreen(
                    navController = navController,
                    noteViewModel = viewModel
                )
            }

            // ✅ FIXED: About Screen - now using your actual screen
            composable(Screen.About.route) {
                AboutScreen(navController = navController)
            }

            // ✅ NEW: Firebase Test Screen
            composable(Screen.FirebaseTest.route) {
                FirebaseTestScreen(navController, viewModel)
            }

            // ✅ NEW: Pending Invitations Screen
            composable(Screen.PendingInvitations.route) {
                PendingInvitationsScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            // ✅ FIXED: Collaborative Todo Screen with proper parameters
            composable(
                route = Screen.CollaborativeTodo.route,
                arguments = listOf(navArgument("collaborativeNoteId") { type = NavType.StringType })
            ) { backStackEntry ->
                val collaborativeNoteId = backStackEntry.arguments?.getString("collaborativeNoteId") ?: ""
                ToDoListScreen(
                    noteIdArg = 0, // Not used for collaborative
                    navController = navController,
                    viewModel = viewModel,
                    isCollaborative = true,
                    collaborativeNoteId = collaborativeNoteId
                )
            }

            // Note Detail Screen
            composable(
                route = "noteDetail/{noteId}",
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
                route = "todoList/{noteId}",
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

            // ✅ ADD: Support for alternative route names that might be used in NoteListScreen
            composable(
                route = "note_detail/{noteId}",
                arguments = listOf(navArgument("noteId") { type = NavType.IntType })
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getInt("noteId") ?: 0
                NoteDetailScreen(
                    noteIdArg = noteId,
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable(
                route = "todo_list/{noteId}",
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

// A simple placeholder for screens we haven't built yet
@Composable
fun PlaceholderScreen(screenName: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "$screenName Screen")
    }
}
