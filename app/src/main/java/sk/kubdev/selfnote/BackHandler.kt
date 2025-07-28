package sk.kubdev.selfnote

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.navigation.NavController

@Composable
fun HandleBackNavigation(
    navController: NavController,
    onBack: (() -> Unit)? = null
) {
    BackHandler {
        if (onBack != null) {
            onBack()
        } else {
            // 🔧 SAFE BACK NAVIGATION
            if (!navController.popBackStack()) {
                navController.navigate(Screen.NoteList.route) {
                    popUpTo(Screen.NoteList.route) {
                        inclusive = false
                    }
                    launchSingleTop = true
                }
            }
        }
    }
}