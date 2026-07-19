package sk.kubdev.mynotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

// Provided from MainActivity based on SettingsData.gradientHeadersEnabled, so any
// screen's top bar can pick it up without threading the setting through every
// screen's parameter list.
val LocalGradientHeaders = compositionLocalOf { true }

// Uses the RAW user-picked color (LocalBrandGradient), not colorScheme.primary:
// primary gets lightened in dark mode so small accents stay readable, which made
// header gradients look completely different between light and dark (e.g. Wine
// turned washed pink in dark). Large branded surfaces with white content on top can
// safely carry the true color in both themes - so now they match.
@Composable
fun gradientHeaderBrush(): Brush {
    val brand = sk.kubdev.mynotes.ui.theme.LocalBrandGradient.current
    return Brush.linearGradient(colors = listOf(brand.start, brand.end))
}

// FABs matching the header's branding: same raw user color (gradient or flat,
// following the Gradient Headers toggle) instead of colorScheme.primary, which in
// dark mode is the lightened accent and made buttons look washed-out next to the
// vividly colored top bar.
@Composable
fun BrandFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val brand = sk.kubdev.mynotes.ui.theme.LocalBrandGradient.current
    androidx.compose.material3.FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = brand.start,
        contentColor = sk.kubdev.mynotes.ui.screens.getTextColorForBackground(brand.start),
        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 8.dp
        ),
        content = content
    )
}

@Composable
fun BrandSmallFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val brand = sk.kubdev.mynotes.ui.theme.LocalBrandGradient.current
    androidx.compose.material3.SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = brand.start,
        contentColor = sk.kubdev.mynotes.ui.screens.getTextColorForBackground(brand.start),
        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 8.dp
        ),
        content = content
    )
}

// Drop-in replacement for Material3's TopAppBar used across the app's screens - but
// built from scratch instead of wrapping TopAppBar. TopAppBar's own containerColor
// paints behind the status bar correctly, but a background Modifier passed in via its
// `modifier` param does not (Material3 applies the caller's modifier to a layer that
// sits *inside* the inset-consuming wrapper, so the gradient only painted the content
// row and the status bar strip above it stayed an unthemed flat gray - the same class
// of bug fixed earlier in the drawer). Building the bar directly - background first,
// statusBarsPadding() second - guarantees the gradient actually extends under the
// status bar, exactly like the drawer header.
@Composable
fun GradientTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    // Extra content (e.g. a formatting toolbar) rendered INSIDE the same gradient
    // surface, below the title row. Screens used to stack their toolbar as a separate
    // gradient-painted composable under the bar, which restarted the diagonal
    // gradient and produced a visible seam; hosting it here keeps one continuous
    // gradient from the status bar down to the toolbar's bottom edge.
    bottomContent: (@Composable () -> Unit)? = null
) {
    val gradientEnabled = LocalGradientHeaders.current
    val background = if (gradientEnabled) {
        Modifier.background(gradientHeaderBrush())
    } else {
        // Same reasoning as the gradient: flat mode also uses the raw brand color so
        // the bar looks identical in light and dark themes.
        Modifier.background(sk.kubdev.mynotes.ui.theme.LocalBrandGradient.current.start)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(background)
            .statusBarsPadding()
    ) {
        // One LocalContentColor for the WHOLE bar (nav icon + title + actions), not
        // just the title: icons without an explicit tint were falling back to the
        // theme's default onSurface, which differs from onPrimary and made icon
        // colors visibly inconsistent across the bar.
        CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onPrimary
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center) { navigationIcon() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    title()
                }
                Row(verticalAlignment = Alignment.CenterVertically) { actions() }
            }

            bottomContent?.invoke()
        }
    }
}
