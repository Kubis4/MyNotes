package sk.kubisdev.mynotes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import sk.kubisdev.mynotes.settings.model.AppColorScheme
import sk.kubisdev.mynotes.settings.model.AppTheme
import sk.kubisdev.mynotes.ui.screens.getTextColorForBackground

// The user's EXACT picked color (plus its hue-shifted partner) for branded surfaces
// like the top-bar gradient. colorScheme.primary can't be used for those in dark
// mode: it gets lightened there so small accents (cursor, links) stay readable on
// near-black, which made e.g. Wine's header a washed pink in dark while light kept
// the true wine red. Branded surfaces are large with white text on top, so they can
// safely use the raw color in both themes - and now match across them.
data class BrandGradientColors(val start: Color, val end: Color)

val LocalBrandGradient = staticCompositionLocalOf {
    BrandGradientColors(Color(0xFF1976D2), Color(0xFF1565C0))
}

@Composable
fun MyNotesDynamicTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    colorScheme: AppColorScheme = AppColorScheme.OCEAN_BLUE,
    content: @Composable () -> Unit
) {
    val isDarkTheme = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val baseColor = Color(colorScheme.primaryColor)
    val dynamicColorScheme = createColorScheme(
        baseColor = baseColor,
        isDark = isDarkTheme
    )

    MaterialTheme(
        colorScheme = dynamicColorScheme,
        typography = Typography,
        shapes = MyNotesShapes
    ) {
        CompositionLocalProvider(
            LocalBrandGradient provides BrandGradientColors(
                start = baseColor,
                end = adjustHue(baseColor, -30f)
            )
        ) {
            content()
        }
    }
}

// Approximates how `foreground` (which may be semi-transparent) actually looks once drawn
// on top of `backdrop`, so contrast can be judged from the *visible* color, not the raw one.
private fun compositeOver(foreground: Color, backdrop: Color): Color {
    val a = foreground.alpha
    return Color(
        red = foreground.red * a + backdrop.red * (1 - a),
        green = foreground.green * a + backdrop.green * (1 - a),
        blue = foreground.blue * a + backdrop.blue * (1 - a),
        alpha = 1f
    )
}

private fun contentColorFor(foreground: Color, backdrop: Color): Color {
    return getTextColorForBackground(compositeOver(foreground, backdrop))
}

// Dark accents (Navy, Coffee, Plum, Wine...) are nearly invisible against the
// near-black dark-theme surfaces - the text cursor, checked checkboxes, section
// headers and text-selection highlights all disappear into the background. Standard
// Material dark themes solve this by using a LIGHTER TONE of the same hue as the
// accent, so do the same - but lighten via HSV "value" (brightness), not by blending
// toward white: blending toward white also drags saturation down, and a desaturated
// dark red reads as pastel pink rather than "a brighter wine red". Raising V alone
// keeps the hue and saturation intact, so Wine gets brighter/more vivid instead of
// pinker, and stays recognizably the same color as its (unlightened) header/FAB.
// Light theme keeps the user's exact pick.
private fun ensureReadableOnDark(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        ), hsv
    )

    var c = color
    var guard = 0
    while (c.luminance() < 0.22f && guard < 12 && hsv[2] < 1f) {
        hsv[2] = (hsv[2] + 0.08f).coerceAtMost(1f)
        c = Color(android.graphics.Color.HSVToColor((color.alpha * 255).toInt(), hsv))
        guard++
    }
    return c
}

private fun createColorScheme(baseColor: Color, isDark: Boolean): ColorScheme {
    // Create harmonious colors based on the primary color
    val secondaryColor = adjustHue(baseColor, 30f) // Slightly different hue
    val tertiaryColor = adjustHue(baseColor, -30f) // Another variation

    // Some AppColorScheme entries are light/pastel accents (e.g. Sky Blue, Peach, Sand). A
    // hardcoded white "on*" text/icon color is unreadable against those, so contrast is
    // computed per-swatch against the backdrop it actually renders on (see contentColorFor).
    return if (isDark) {
        val darkBackdrop = Color(0xFF1E1E1E)
        val base = ensureReadableOnDark(baseColor)
        val secondaryBase = ensureReadableOnDark(secondaryColor).copy(alpha = 0.8f)
        val tertiaryBase = ensureReadableOnDark(tertiaryColor).copy(alpha = 0.7f)
        val primaryContainer = base.copy(alpha = 0.3f)
        val secondaryContainer = ensureReadableOnDark(secondaryColor).copy(alpha = 0.2f)
        val tertiaryContainer = ensureReadableOnDark(tertiaryColor).copy(alpha = 0.2f)

        darkColorScheme(
            primary = base,
            onPrimary = contentColorFor(base, darkBackdrop),
            primaryContainer = primaryContainer,
            onPrimaryContainer = contentColorFor(primaryContainer, darkBackdrop),

            secondary = secondaryBase,
            onSecondary = contentColorFor(secondaryBase, darkBackdrop),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = contentColorFor(secondaryContainer, darkBackdrop),

            tertiary = tertiaryBase,
            onTertiary = contentColorFor(tertiaryBase, darkBackdrop),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = contentColorFor(tertiaryContainer, darkBackdrop),

            background = Color(0xFF121212),
            onBackground = Color.White,
            surface = darkBackdrop,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF2A2A2A),
            onSurfaceVariant = Color(0xFFE0E0E0),

            // 🎨 SNACKBAR USES SELECTED COLOR
            inverseSurface = base.copy(alpha = 0.9f), // User's selected color
            inverseOnSurface = getTextColorForBackground(base),
            inversePrimary = Color.White, // Always white action button text

            outline = base.copy(alpha = 0.5f),
            outlineVariant = base.copy(alpha = 0.3f),

            // Error colors that harmonize with the theme
            error = Color(0xFFFF6B6B),
            onError = Color.White,
            errorContainer = Color(0xFF4D1F1F),
            onErrorContainer = Color(0xFFFFDAD6)
        )
    } else {
        val lightBackdrop = Color(0xFFFAFAFA)
        val secondaryBase = secondaryColor.copy(alpha = 0.8f)
        val tertiaryBase = tertiaryColor.copy(alpha = 0.7f)
        val primaryContainer = baseColor.copy(alpha = 0.1f)
        val secondaryContainer = secondaryColor.copy(alpha = 0.05f)
        val tertiaryContainer = tertiaryColor.copy(alpha = 0.05f)

        lightColorScheme(
            primary = baseColor,
            onPrimary = contentColorFor(baseColor, lightBackdrop),
            primaryContainer = primaryContainer,
            onPrimaryContainer = contentColorFor(primaryContainer, lightBackdrop),

            secondary = secondaryBase,
            onSecondary = contentColorFor(secondaryBase, lightBackdrop),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = contentColorFor(secondaryContainer, lightBackdrop),

            tertiary = tertiaryBase,
            onTertiary = contentColorFor(tertiaryBase, lightBackdrop),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = contentColorFor(tertiaryContainer, lightBackdrop),

            background = Color.White,
            onBackground = Color.Black,
            surface = lightBackdrop,
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFF5F5F5),
            onSurfaceVariant = Color(0xFF666666),

            // 🎨 SNACKBAR USES SELECTED COLOR
            inverseSurface = baseColor.copy(alpha = 0.9f), // User's selected color
            inverseOnSurface = getTextColorForBackground(baseColor),
            inversePrimary = Color.White, // Always white action button text

            outline = baseColor.copy(alpha = 0.3f),
            outlineVariant = baseColor.copy(alpha = 0.1f),

            // Error colors that harmonize with the theme
            error = Color(0xFFD32F2F),
            onError = Color.White,
            errorContainer = Color(0xFFFFF2F2),
            onErrorContainer = Color(0xFF5F1A1A)
        )
    }
}

// Helper function to adjust hue for harmonious colors
private fun adjustHue(color: Color, hueDelta: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        ), hsv
    )

    hsv[0] = (hsv[0] + hueDelta) % 360f
    if (hsv[0] < 0) hsv[0] += 360f

    val adjustedColor = android.graphics.Color.HSVToColor(hsv)
    return Color(adjustedColor)
}
