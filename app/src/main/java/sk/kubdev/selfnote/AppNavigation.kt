package sk.kubdev.selfnote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

// A sealed class to define all our app's screens
sealed class Screen(val route: String) {
    object NoteList : Screen("noteList")
    object NoteDetail : Screen("noteDetail/{noteId}") {
        fun createRoute(noteId: Int) = "noteDetail/$noteId"
    }

    // --- UPDATED: ToDoList now takes an ID, Drawing is removed ---
    object ToDoList : Screen("todoList/{noteId}") {
        fun createRoute(noteId: Int) = "todoList/$noteId"
    }

    object Archive : Screen("archive")
    object Bin : Screen("bin")
    object Settings : Screen("settings")
    object Feedback : Screen("feedback")
    object About : Screen("about")
}

@Composable
fun AppNavigation(viewModel: NoteViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.NoteList.route
    ) {
        // Main Notes List Screen
        composable(Screen.NoteList.route) {
            NoteListScreen(navController = navController, viewModel = viewModel)
        }

        // Note Detail Screen
        composable(
            route = Screen.NoteDetail.route,
            arguments = listOf(navArgument("noteId") { type = NavType.IntType })
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getInt("noteId") ?: 0
            NoteDetailScreen(
                // --- FIX: Change 'noteId' to 'noteIdArg' ---
                noteIdArg = noteId,
                navController = navController,
                viewModel = viewModel
            )
        }

        // --- UPDATED: Use the real ToDoListScreen instead of a placeholder ---
        composable(
            route = Screen.ToDoList.route,
            arguments = listOf(navArgument("noteId") { type = NavType.IntType })
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getInt("noteId") ?: 0
            ToDoListScreen(
                // --- FIX: Change 'noteId' to 'noteIdArg' ---
                noteIdArg = noteId,
                navController = navController,
                viewModel = viewModel
            )
        }

        // Placeholder screens for our drawer destinations
        composable(Screen.Archive.route) { PlaceholderScreen("Archive") }
        composable(Screen.Bin.route) { PlaceholderScreen("Bin") }
        composable(Screen.Settings.route) { PlaceholderScreen("Settings") }
        composable(Screen.Feedback.route) { PlaceholderScreen("Feedback") }
        composable(Screen.About.route) { PlaceholderScreen("About") }
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