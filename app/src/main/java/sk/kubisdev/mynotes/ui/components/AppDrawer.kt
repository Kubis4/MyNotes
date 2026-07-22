package sk.kubisdev.mynotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.navigation.compose.currentBackStackEntryAsState
import sk.kubisdev.mynotes.BuildConfig
import sk.kubisdev.mynotes.NoteViewModel
import sk.kubisdev.mynotes.R
import sk.kubisdev.mynotes.Screen

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

    fun navigate(route: String) {
        if (currentRoute != route) {
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = true
            }
        }
        closeDrawer()
    }

    // Built on a plain Surface instead of ModalDrawerSheet: the latter reserves a
    // flat-colored gap for the status bar before its content starts (regardless of
    // its windowInsets param), leaving an ugly strip above the gradient header. A
    // bare Surface has no such built-in inset handling, so the header's gradient can
    // paint all the way to the top edge; the header applies statusBarsPadding()
    // itself so its content stays clear of the system icons.
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp),
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        DrawerHeader()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
        Spacer(Modifier.height(8.dp))

        DrawerSectionLabel(stringResource(R.string.nav_notes))
        DrawerItem(
            label = stringResource(R.string.nav_notes),
            icon = Icons.AutoMirrored.Filled.Notes,
            selected = currentRoute == Screen.NoteList.route,
            onClick = { navigate(Screen.NoteList.route) }
        )
        DrawerItem(
            label = stringResource(R.string.nav_archive),
            icon = Icons.Default.Archive,
            selected = currentRoute == Screen.Archive.route,
            onClick = { navigate(Screen.Archive.route) }
        )
        DrawerItem(
            label = stringResource(R.string.nav_bin),
            icon = Icons.Default.DeleteOutline,
            selected = currentRoute == Screen.Bin.route,
            onClick = { navigate(Screen.Bin.route) }
        )

        if (isUserSignedIn) {
            Spacer(Modifier.height(8.dp))
            DrawerSectionLabel(stringResource(R.string.drawer_section_collaboration))
            DrawerItem(
                label = stringResource(R.string.nav_invitations),
                icon = Icons.Default.Mail,
                selected = currentRoute == Screen.PendingInvitations.route,
                badgeCount = pendingInvites.size,
                onClick = { navigate(Screen.PendingInvitations.route) }
            )
        }

        Spacer(Modifier.height(8.dp))
        DrawerSectionLabel(stringResource(R.string.drawer_section_more))
        DrawerItem(
            label = stringResource(R.string.nav_settings),
            icon = Icons.Default.Settings,
            selected = currentRoute == Screen.Settings.route,
            onClick = { navigate(Screen.Settings.route) }
        )
        DrawerItem(
            label = stringResource(R.string.nav_backup),
            icon = Icons.Default.Backup,
            selected = currentRoute == Screen.Backup.route,
            onClick = { navigate(Screen.Backup.route) }
        )
        DrawerItem(
            label = stringResource(R.string.nav_import),
            icon = Icons.Default.FileDownload,
            selected = currentRoute == Screen.Import.route,
            onClick = { navigate(Screen.Import.route) }
        )
        DrawerItem(
            label = stringResource(R.string.nav_about),
            icon = Icons.AutoMirrored.Filled.HelpOutline,
            selected = currentRoute == Screen.About.route,
            onClick = { navigate(Screen.About.route) }
        )

        Spacer(Modifier.height(8.dp))
        }

        Text(
            text = "MyNotes " + BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp)
                .navigationBarsPadding()
        )
        }
    }
}

@Composable
private fun DrawerHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradientHeaderBrush())
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = "App Icon",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    badgeCount: Int = 0
) {
    NavigationDrawerItem(
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                if (badgeCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text(
                            text = badgeCount.toString(),
                            color = MaterialTheme.colorScheme.onError,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        },
        icon = {
            if (badgeCount > 0) {
                BadgedBox(
                    badge = {
                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                            Text(
                                text = badgeCount.toString(),
                                color = MaterialTheme.colorScheme.onError,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                ) {
                    Icon(icon, contentDescription = null)
                }
            } else {
                Icon(icon, contentDescription = null)
            }
        },
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
