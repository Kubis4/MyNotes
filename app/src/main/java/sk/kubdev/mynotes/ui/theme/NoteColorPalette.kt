// Update your ui/theme/NoteColorPalette.kt
package sk.kubdev.mynotes.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

object NoteColorPalette {
    val colors = listOf(
        Color(0xFFFFF9C4), // Light Yellow (new default)
        Color(0xFFFFE0B2), // Light Orange
        Color(0xFFFFCDD2), // Light Pink
        Color(0xFFF8BBD9), // Light Rose
        Color(0xFFE1BEE7), // Light Purple
        Color(0xFFD1C4E9), // Light Indigo
        Color(0xFFBBDEFB), // Light Blue
        Color(0xFFB3E5FC), // Light Cyan
        Color(0xFFB2DFDB), // Light Teal
        Color(0xFFC8E6C9), // Light Green
        Color(0xFFDCEDC8), // Light Light Green
        Color(0xFFF0F4C3), // Light Lime
        Color(0xFFFFE082), // Amber
        Color(0xFFFFCC80), // Deep Orange Light
        Color(0xFFD7CCC8), // Brown Light
        Color(0xFFF5F5F5), // Grey Light
        Color(0xFFE8F5E8), // Mint Green
        Color(0xFFFFE5CC), // Peach
        Color(0xFFE5E5FF), // Lavender
        Color(0xFFFFE5F1)  // Blush Pink
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
            "Light Yellow", "Light Orange", "Light Pink", "Light Rose",
            "Light Purple", "Light Indigo", "Light Blue", "Light Cyan",
            "Light Teal", "Light Green", "Light Lime Green", "Lime",
            "Amber", "Deep Orange", "Brown", "Grey",
            "Mint Green", "Peach", "Lavender", "Blush Pink"
        )
        return if (index >= 0 && index < colorNames.size) {
            colorNames[index]
        } else {
            "Light Yellow"
        }
    }
}
