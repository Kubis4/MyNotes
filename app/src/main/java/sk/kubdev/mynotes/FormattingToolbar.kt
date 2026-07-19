package sk.kubdev.mynotes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubdev.mynotes.ui.components.LocalGradientHeaders
import sk.kubdev.mynotes.ui.components.gradientHeaderBrush

data class HeadingLevel(val label: String, val fontSize: Int)

@Composable
fun FormattingToolbar(
    toggledStyles: Set<String>,
    activeColor: Color?,
    activeFontSize: Int,
    onStyleToggle: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    onFontSizeChange: (Int?) -> Unit,
    onAddSeparator: () -> Unit,
    showSeparatorButton: Boolean = true,
    // Font size doesn't make sense on checklist items (each item is one short line, not
    // prose worth a heading), so the to-do screen hides this button and offers only color.
    showFontSizeButton: Boolean = true,
    // False when hosted inside GradientTopAppBar's bottomContent slot, which already
    // paints one continuous gradient - drawing our own here would restart the
    // gradient and create a visible seam between the bar and the toolbar.
    drawOwnBackground: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showColorPicker by remember { mutableStateOf(false) }
    var showFontSizePicker by remember { mutableStateOf(false) }

    // Predefined colors for the color picker
    val colors = listOf(
        Color.Black, Color.White, Color.Gray, Color(0xFF424242), Color(0xFF9E9E9E),
        Color.Red, Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7), Color.Blue,
        Color(0xFF2196F3), Color(0xFF00BCD4), Color(0xFF009688), Color.Green, Color(0xFF8BC34A),
        Color(0xFFCDDC39), Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722),
        Color(0xFF795548), Color(0xFF8D6E63), Color(0xFF6D4C41), Color(0xFF5D4037), Color(0xFF3E2723)
    )

    // Heading levels (Google Keep-style) instead of a free-form point-size picker.
    // Letting any of 11 arbitrary sizes (10-48sp) sit next to each other in one
    // flowing paragraph is exactly what broke Compose's text layout for mixed-size
    // lines - oversized/mismatched cursor rendering, and neighboring characters
    // visibly flickering while typing at a different size. A small fixed set of
    // named levels means far fewer distinct sizes ever coexist in one paragraph,
    // which sidesteps both bugs at the root, and reads better as a note-taking
    // feature besides ("heading" vs "32sp").
    val headingLevels = listOf(
        HeadingLevel("H1", 28),
        HeadingLevel("H2", 22),
        HeadingLevel("H3", 18),
        HeadingLevel("Normal", 16)
    )

    val gradientEnabled = LocalGradientHeaders.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (drawOwnBackground && gradientEnabled) Modifier.background(gradientHeaderBrush()) else Modifier),
        color = when {
            !drawOwnBackground -> Color.Transparent
            gradientEnabled -> Color.Transparent
            else -> MaterialTheme.colorScheme.primary
        },
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bold Button
            IconButton(
                onClick = { onStyleToggle("BOLD") },
                modifier = Modifier
                    .background(
                        if (toggledStyles.contains("BOLD"))
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        else
                            Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    Icons.Default.FormatBold,
                    contentDescription = "Bold",
                    tint = if (toggledStyles.contains("BOLD"))
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            }

            // Italic Button
            IconButton(
                onClick = { onStyleToggle("ITALIC") },
                modifier = Modifier
                    .background(
                        if (toggledStyles.contains("ITALIC"))
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        else
                            Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    Icons.Default.FormatItalic,
                    contentDescription = "Italic",
                    tint = if (toggledStyles.contains("ITALIC"))
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            }

            // Underline Button
            IconButton(
                onClick = { onStyleToggle("UNDERLINE") },
                modifier = Modifier
                    .background(
                        if (toggledStyles.contains("UNDERLINE"))
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        else
                            Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    Icons.Default.FormatUnderlined,
                    contentDescription = "Underline",
                    tint = if (toggledStyles.contains("UNDERLINE"))
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            }

            // Font Size Picker
            if (showFontSizeButton) Box {
                IconButton(
                    onClick = { showFontSizePicker = !showFontSizePicker },
                    modifier = Modifier
                        .background(
                            if (activeFontSize != 16)
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                            else
                                Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Icon(
                        Icons.Default.FormatSize,
                        contentDescription = "Font Size",
                        tint = if (activeFontSize != 16)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                }

                DropdownMenu(
                    expanded = showFontSizePicker,
                    onDismissRequest = { showFontSizePicker = false }
                ) {
                    headingLevels.forEach { level ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = level.label,
                                    fontSize = level.fontSize.sp,
                                    color = if (activeFontSize == level.fontSize)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        LocalContentColor.current,
                                    fontWeight = if (level.label != "Normal")
                                        FontWeight.Bold
                                    else
                                        FontWeight.Normal
                                )
                            },
                            onClick = {
                                onFontSizeChange(if (level.fontSize == 16) null else level.fontSize)
                                showFontSizePicker = false
                            }
                        )
                    }
                }
            }

            // Color Picker
            Box {
                IconButton(
                    onClick = { showColorPicker = !showColorPicker },
                    modifier = Modifier
                        .background(
                            if (activeColor != null)
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                            else
                                Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Icon(
                        Icons.Default.FormatColorText,
                        contentDescription = "Text Color",
                        tint = activeColor ?: MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                }

                DropdownMenu(
                    expanded = showColorPicker,
                    onDismissRequest = { showColorPicker = false }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // Color grid
                        for (rowIndex in 0 until (colors.size + 4) / 5) {
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (colIndex in 0 until 5) {
                                    val colorIndex = rowIndex * 5 + colIndex
                                    if (colorIndex < colors.size) {
                                        val color = colors[colorIndex]
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .clickable {
                                                    onColorChange(color)
                                                    showColorPicker = false
                                                }
                                                .border(
                                                    width = 2.dp,
                                                    color = if (activeColor == color)
                                                        MaterialTheme.colorScheme.primary
                                                    else if (color == Color.White)
                                                        Color.Gray
                                                    else
                                                        Color.Transparent,
                                                    shape = CircleShape
                                                )
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Clear color button
                        TextButton(
                            onClick = {
                                onColorChange(Color.Unspecified)
                                showColorPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.format_clear_color))
                        }
                    }
                }
            }

            // Add Separator Button - hidden where typing "__" does the same thing
            if (showSeparatorButton) {
                IconButton(
                    onClick = onAddSeparator
                ) {
                    Icon(
                        Icons.Default.HorizontalRule,
                        contentDescription = "Add Separator",
                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
