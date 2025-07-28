// Update your ui/theme/NoteColorPalette.kt
package sk.kubdev.selfnote.ui.theme

import androidx.compose.ui.graphics.Color

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
