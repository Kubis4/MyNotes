package sk.kubdev.selfnote

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun AppDrawer(
    navController: NavController,
    closeDrawer: () -> Unit
) {
    // Get the current route to highlight the selected item
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    ModalDrawerSheet {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.EditNote,
                contentDescription = "App Icon",
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = "SelfNote",
                style = MaterialTheme.typography.titleLarge
            )
        }
        Divider()
        Spacer(Modifier.height(12.dp))

        // Menu Items
        NavigationDrawerItem(
            label = { Text("Notes") },
            icon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
            selected = currentRoute == Screen.NoteList.route,
            onClick = {
                navController.navigate(Screen.NoteList.route) { launchSingleTop = true }
                closeDrawer()
            }
        )
        NavigationDrawerItem(
            label = { Text("Archive") },
            icon = { Icon(Icons.Default.Archive, null) },
            selected = currentRoute == Screen.Archive.route,
            onClick = {
                navController.navigate(Screen.Archive.route) { launchSingleTop = true }
                closeDrawer()
            }
        )
        NavigationDrawerItem(
            label = { Text("Bin") },
            icon = { Icon(Icons.Default.DeleteOutline, null) },
            selected = currentRoute == Screen.Bin.route,
            onClick = {
                navController.navigate(Screen.Bin.route) { launchSingleTop = true }
                closeDrawer()
            }
        )
        Divider(modifier = Modifier.padding(vertical = 12.dp))
        NavigationDrawerItem(
            label = { Text("Settings") },
            icon = { Icon(Icons.Default.Settings, null) },
            selected = currentRoute == Screen.Settings.route,
            onClick = {
                navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                closeDrawer()
            }
        )
        NavigationDrawerItem(
            label = { Text("Feedback") },
            icon = { Icon(Icons.Default.Feedback, null) },
            selected = currentRoute == Screen.Feedback.route,
            onClick = {
                navController.navigate(Screen.Feedback.route) { launchSingleTop = true }
                closeDrawer()
            }
        )
        NavigationDrawerItem(
            label = { Text("About") },
            icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, null) },
            selected = currentRoute == Screen.About.route,
            onClick = {
                navController.navigate(Screen.About.route) { launchSingleTop = true }
                closeDrawer()
            }
        )
    }
}