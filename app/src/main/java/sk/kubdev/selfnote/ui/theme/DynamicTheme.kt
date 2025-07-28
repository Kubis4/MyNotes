package sk.kubdev.selfnote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import sk.kubdev.selfnote.settings.model.AppColorScheme
import sk.kubdev.selfnote.settings.model.AppTheme

@Composable
fun SelfNoteDynamicTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    colorScheme: AppColorScheme = AppColorScheme.OCEAN_BLUE,
    content: @Composable () -> Unit
) {
    val isDarkTheme = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val dynamicColorScheme = createColorScheme(
        baseColor = Color(colorScheme.primaryColor),
        isDark = isDarkTheme
    )

    MaterialTheme(
        colorScheme = dynamicColorScheme,
        typography = Typography,
        content = content
    )
}

private fun createColorScheme(baseColor: Color, isDark: Boolean): ColorScheme {
    // Create harmonious colors based on the primary color
    val primaryVariant = baseColor.copy(alpha = 0.8f)
    val secondaryColor = adjustHue(baseColor, 30f) // Slightly different hue
    val tertiaryColor = adjustHue(baseColor, -30f) // Another variation

    return if (isDark) {
        darkColorScheme(
            primary = baseColor,
            onPrimary = Color.White,
            primaryContainer = baseColor.copy(alpha = 0.3f),
            onPrimaryContainer = baseColor.copy(alpha = 0.9f),

            secondary = secondaryColor.copy(alpha = 0.8f),
            onSecondary = Color.White,
            secondaryContainer = secondaryColor.copy(alpha = 0.2f),
            onSecondaryContainer = secondaryColor.copy(alpha = 0.9f),

            tertiary = tertiaryColor.copy(alpha = 0.7f),
            onTertiary = Color.White,
            tertiaryContainer = tertiaryColor.copy(alpha = 0.2f),
            onTertiaryContainer = tertiaryColor.copy(alpha = 0.8f),

            background = Color(0xFF121212),
            onBackground = Color.White,
            surface = Color(0xFF1E1E1E),
            onSurface = Color.White,
            surfaceVariant = Color(0xFF2A2A2A),
            onSurfaceVariant = Color(0xFFE0E0E0),

            // 🎨 SNACKBAR USES SELECTED COLOR WITH WHITE TEXT
            inverseSurface = baseColor.copy(alpha = 0.9f), // User's selected color
            inverseOnSurface = Color.White, // Always white text for visibility
            inversePrimary = Color.White, // Always white action button text

            outline = baseColor.copy(alpha = 0.5f),
            outlineVariant = baseColor.copy(alpha = 0.3f),

            // Error colors that harmonize with the theme
            error = Color(0xFFFF6B6B),
            onError = Color.White,
            errorContainer = Color(0xFF4D1F1F),
            onErrorContainer = Color(0xFFFFDAD6)
        )
    } else {
        lightColorScheme(
            primary = baseColor,
            onPrimary = Color.White,
            primaryContainer = baseColor.copy(alpha = 0.1f),
            onPrimaryContainer = baseColor.copy(alpha = 0.9f),

            secondary = secondaryColor.copy(alpha = 0.8f),
            onSecondary = Color.White,
            secondaryContainer = secondaryColor.copy(alpha = 0.05f),
            onSecondaryContainer = secondaryColor.copy(alpha = 0.9f),

            tertiary = tertiaryColor.copy(alpha = 0.7f),
            onTertiary = Color.White,
            tertiaryContainer = tertiaryColor.copy(alpha = 0.05f),
            onTertiaryContainer = tertiaryColor.copy(alpha = 0.8f),

            background = Color.White,
            onBackground = Color.Black,
            surface = Color(0xFFFAFAFA),
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFF5F5F5),
            onSurfaceVariant = Color(0xFF666666),

            // 🎨 SNACKBAR USES SELECTED COLOR WITH WHITE TEXT
            inverseSurface = baseColor.copy(alpha = 0.9f), // User's selected color
            inverseOnSurface = Color.White, // Always white text for visibility
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
