package sk.kubdev.selfnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import sk.kubdev.selfnote.R
import sk.kubdev.selfnote.ui.theme.NoteColorPalette

@Composable
fun ColorPickerDialog(
    currentColorIndex: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
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
                        .heightIn(max = 400.dp) // Increased max height to show more colors
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
                tint = if (color == Color.White || color == Color.Yellow) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
