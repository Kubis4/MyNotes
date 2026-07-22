package sk.kubisdev.mynotes.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Softer, slightly larger corner radii than the Material3 defaults (4/8/12/16/28dp)
// for a friendlier, more modern feel across cards, dialogs, buttons and sheets.
val MyNotesShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)
