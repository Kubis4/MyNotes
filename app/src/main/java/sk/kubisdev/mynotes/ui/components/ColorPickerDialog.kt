package sk.kubisdev.mynotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import sk.kubisdev.mynotes.R
import androidx.compose.ui.graphics.luminance
import sk.kubisdev.mynotes.ui.screens.getTextColorForBackground
import sk.kubisdev.mynotes.ui.theme.NoteColorPalette
import sk.kubisdev.mynotes.ui.theme.NotePatterns
import sk.kubisdev.mynotes.ui.theme.notePattern

@Composable
fun ColorPickerDialog(
    currentColorIndex: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    // Optional background-pattern picker (see NotePatterns): shown only when the
    // caller provides a selection callback - the plain color-only dialog is still
    // used from list/bin/archive long-press.
    currentPatternIndex: Int = 0,
    onPatternSelected: ((Int) -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.color_picker_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Show current color name
                Text(
                    text = stringResource(R.string.color_picker_current, NoteColorPalette.getColorName(currentColorIndex)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4), // 4 columns for better layout
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .heightIn(max = if (onPatternSelected != null) 260.dp else 400.dp)
                ) {
                    itemsIndexed(NoteColorPalette.colors) { index, color ->
                        ColorCircle(
                            color = color,
                            isSelected = index == currentColorIndex,
                            colorName = NoteColorPalette.getColorName(index),
                            onClick = { onColorSelected(index) }
                        )
                    }
                }

                if (onPatternSelected != null) {
                    Text(
                        text = stringResource(R.string.pattern_label),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        repeat(NotePatterns.count) { index ->
                            PatternSwatch(
                                patternIndex = index,
                                baseColor = NoteColorPalette.getCardBackground(
                                    currentColorIndex,
                                    isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                                ),
                                isSelected = index == currentPatternIndex,
                                onClick = { onPatternSelected(index) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            // Random color button
                            val randomIndex = NoteColorPalette.getRandomColor()
                            onColorSelected(randomIndex)
                        }
                    ) {
                        Text(stringResource(R.string.color_picker_random))
                    }

                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }
    }
}

// NotePatterns.names is index-ordered ("None", "Dots", ...); mirror that order here
// so the swatch labels localize without NotePatterns needing a Context.
@androidx.annotation.StringRes
private fun patternNameRes(index: Int): Int = when (index) {
    1 -> R.string.pattern_dots
    2 -> R.string.pattern_grid
    3 -> R.string.pattern_stripes
    4 -> R.string.pattern_waves
    5 -> R.string.pattern_rings
    6 -> R.string.pattern_zigzag
    7 -> R.string.pattern_diamonds
    8 -> R.string.pattern_crosses
    else -> R.string.pattern_none
}

@Composable
private fun PatternSwatch(
    patternIndex: Int,
    baseColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(baseColor)
                .notePattern(patternIndex, MaterialTheme.colorScheme.onSurface)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable { onClick() }
        )
        Text(
            text = stringResource(patternNameRes(patternIndex)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    colorName: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp) // Slightly smaller to fit more colors
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.color_picker_selected_desc, colorName),
                tint = getTextColorForBackground(color),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
