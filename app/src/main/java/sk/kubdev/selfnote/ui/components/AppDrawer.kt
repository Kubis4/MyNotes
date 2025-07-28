package sk.kubdev.selfnote.ui.components

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.navigation.compose.currentBackStackEntryAsState
import sk.kubdev.selfnote.NoteViewModel
import sk.kubdev.selfnote.R
import sk.kubdev.selfnote.Screen

@Composable
fun AppDrawer(
    navController: NavController,
    closeDrawer: () -> Unit,
    viewModel: NoteViewModel? = null
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Get collaboration states if viewModel is provided
    val pendingInvites = if (viewModel != null) {
        val invites by viewModel.pendingInvites.collectAsStateWithLifecycle()
        invites
    } else {
        emptyList()
    }

    val isUserSignedIn = viewModel?.isUserSignedIn() ?: false

    // Start sync when drawer opens and user is signed in
    LaunchedEffect(isUserSignedIn) {
        if (isUserSignedIn && viewModel != null) {
            viewModel.startCollaborativeSync()
        }
    }

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
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ✅ MAIN SECTION
        // Notes
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_notes)) },
            icon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
            selected = currentRoute == Screen.NoteList.route,
            onClick = {
                if (currentRoute != Screen.NoteList.route) {
                    navController.navigate(Screen.NoteList.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                closeDrawer()
            },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        // Archive
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_archive)) },
            icon = { Icon(Icons.Default.Archive, null) },
            selected = currentRoute == Screen.Archive.route,
            onClick = {
                if (currentRoute != Screen.Archive.route) {
                    navController.navigate(Screen.Archive.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                closeDrawer()
            },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        // Bin
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_bin)) },
            icon = { Icon(Icons.Default.DeleteOutline, null) },
            selected = currentRoute == Screen.Bin.route,
            onClick = {
                if (currentRoute != Screen.Bin.route) {
                    navController.navigate(Screen.Bin.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                closeDrawer()
            },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        // ✅ COLLABORATION SECTION (only if signed in)
        if (isUserSignedIn) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Section header
            Text(
                text = "Collaboration",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Pending Invitations
            NavigationDrawerItem(
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Invitations")
                        if (pendingInvites.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error
                            ) {
                                Text(
                                    text = pendingInvites.size.toString(),
                                    color = MaterialTheme.colorScheme.onError,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                },
                icon = {
                    BadgedBox(
                        badge = {
                            if (pendingInvites.isNotEmpty()) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error
                                ) {
                                    Text(
                                        text = pendingInvites.size.toString(),
                                        color = MaterialTheme.colorScheme.onError,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Mail, null)
                    }
                },
                selected = currentRoute == Screen.PendingInvitations.route,
                onClick = {
                    if (currentRoute != Screen.PendingInvitations.route) {
                        navController.navigate(Screen.PendingInvitations.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    closeDrawer()
                },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // Firebase Test
            NavigationDrawerItem(
                label = { Text("Firebase Test") },
                icon = { Icon(Icons.Default.CloudSync, null) },
                selected = currentRoute == Screen.FirebaseTest.route,
                onClick = {
                    if (currentRoute != Screen.FirebaseTest.route) {
                        navController.navigate(Screen.FirebaseTest.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    closeDrawer()
                },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        // ✅ SETTINGS SECTION
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Settings
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_settings)) },
            icon = { Icon(Icons.Default.Settings, null) },
            selected = currentRoute == Screen.Settings.route,
            onClick = {
                if (currentRoute != Screen.Settings.route) {
                    navController.navigate(Screen.Settings.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                closeDrawer()
            },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        // Backup
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_backup)) },
            icon = { Icon(Icons.Default.Backup, null) },
            selected = currentRoute == Screen.Backup.route,
            onClick = {
                if (currentRoute != Screen.Backup.route) {
                    navController.navigate(Screen.Backup.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                closeDrawer()
            },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        // About
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_about)) },
            icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, null) },
            selected = currentRoute == Screen.About.route,
            onClick = {
                if (currentRoute != Screen.About.route) {
                    navController.navigate(Screen.About.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                closeDrawer()
            },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
