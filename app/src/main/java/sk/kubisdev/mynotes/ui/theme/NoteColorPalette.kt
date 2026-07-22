// Update your ui/theme/NoteColorPalette.kt
package sk.kubisdev.mynotes.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

object NoteColorPalette {
    // One entry per distinct hue family, evenly spread around the color wheel with
    // varied saturation, so no two picker swatches read as near-duplicates. Existing
    // notes keep their stored index; only the shade behind it changes.
    val colors = listOf(
        Color(0xFFFFF9C4), // Light Yellow (default)
        Color(0xFFFFE082), // Amber
        Color(0xFFFFCC80), // Orange
        Color(0xFFFFAB91), // Coral
        Color(0xFFEF9A9A), // Red
        Color(0xFFF48FB1), // Pink
        Color(0xFFCE93D8), // Purple
        Color(0xFFB39DDB), // Deep Purple
        Color(0xFF9FA8DA), // Indigo
        Color(0xFF90CAF9), // Blue
        Color(0xFF81D4FA), // Sky Blue
        Color(0xFF80DEEA), // Cyan
        Color(0xFF80CBC4), // Teal
        Color(0xFFA5D6A7), // Green
        Color(0xFFC5E1A5), // Light Green
        Color(0xFFE6EE9C), // Lime
        Color(0xFFBCAAA4), // Mocha
        Color(0xFFB0BEC5), // Blue Grey
        Color(0xFFEEEEEE), // Grey
        Color(0xFFF0E1D2)  // Sand
    )

    fun getColorByIndex(index: Int): Color {
        return if (index >= 0 && index < colors.size) {
            colors[index]
        } else {
            colors[0] // Default to Light Yellow instead of white
        }
    }

    // Background for a whole note card in the chosen color (Google Keep style),
    // instead of just a small dot. The palette entries are light pastels, so in the
    // light theme they're usable directly (dark onSurface text stays readable). In
    // the dark theme, blending a pastel toward gray gave washed-out, barely-colored
    // cards - so instead the hue is re-saturated and darkened (exactly Keep's dark
    // palette approach): vivid, clearly distinguishable color with luminance low
    // enough that white text keeps solid contrast.
    fun getCardBackground(index: Int, isDark: Boolean): Color {
        val base = getColorByIndex(index)
        if (!isDark) return base
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(base.toArgb(), hsv)
        if (hsv[1] > 0.05f) {
            // Pastels sit around S=0.15-0.30: triple it for a rich tone
            hsv[1] = (hsv[1] * 3f).coerceIn(0.40f, 0.65f)
        }
        hsv[2] = 0.30f
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    // Editor variant: much more blended in dark theme - a full-screen surface in a
    // strong tint would be tiring to write on, so the editor keeps just a hint of
    // the note's color while the list cards above carry the saturated version.
    fun getEditorBackground(index: Int, isDark: Boolean): Color {
        val base = getColorByIndex(index)
        return if (isDark) lerp(base, Color(0xFF161616), 0.82f) else base
    }

    fun getColorIndex(color: Color): Int {
        return colors.indexOf(color).takeIf { it >= 0 } ?: 0
    }

    // Helper function to get a random color for new notes
    fun getRandomColor(): Int {
        return (0 until colors.size).random()
    }

    // Helper function to get color name for accessibility
    fun getColorName(index: Int): String {
        val colorNames = listOf(
            "Light Yellow", "Amber", "Orange", "Coral",
            "Red", "Pink", "Purple", "Deep Purple",
            "Indigo", "Blue", "Sky Blue", "Cyan",
            "Teal", "Green", "Light Green", "Lime",
            "Mocha", "Blue Grey", "Grey", "Sand"
        )
        return if (index >= 0 && index < colorNames.size) {
            colorNames[index]
        } else {
            "Light Yellow"
        }
    }
}
