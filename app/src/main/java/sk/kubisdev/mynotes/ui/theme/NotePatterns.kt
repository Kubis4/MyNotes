package sk.kubisdev.mynotes.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

// Subtle procedural background patterns drawn over a note's color tint. Deliberately
// very low alpha so text stays clearly readable on top - they read as texture, not
// decoration competing with content. Index 0 = none.
object NotePatterns {

    val names = listOf("None", "Dots", "Grid", "Stripes", "Waves", "Rings", "Zigzag", "Diamonds", "Crosses")
    val count = names.size

    fun getName(index: Int): String = names.getOrElse(index) { names[0] }
}

// Draws the selected pattern behind the content. `tint` should be the theme's
// onSurface so the pattern works on both light pastels and dark tints.
fun Modifier.notePattern(patternIndex: Int, tint: Color): Modifier {
    if (patternIndex <= 0 || patternIndex >= NotePatterns.count) return this
    return drawBehind {
        val color = tint.copy(alpha = 0.07f)
        when (patternIndex) {
            1 -> drawDots(color)
            2 -> drawGrid(color)
            3 -> drawStripes(color)
            4 -> drawWaves(color)
            5 -> drawRings(color)
            6 -> drawZigzag(color)
            7 -> drawDiamonds(color)
            8 -> drawCrosses(color)
        }
    }
}

private fun DrawScope.drawDots(color: Color) {
    val step = 28.dp.toPx()
    val radius = 2.dp.toPx()
    var y = step / 2
    var rowIndex = 0
    while (y < size.height + step) {
        // Offset every other row for a honeycomb-ish rhythm
        var x = if (rowIndex % 2 == 0) step / 2 else step
        while (x < size.width + step) {
            drawCircle(color, radius = radius, center = Offset(x, y))
            x += step
        }
        y += step
        rowIndex++
    }
}

private fun DrawScope.drawGrid(color: Color) {
    val step = 32.dp.toPx()
    val stroke = 1.dp.toPx()
    var x = step
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
        x += step
    }
    var y = step
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
        y += step
    }
}

private fun DrawScope.drawStripes(color: Color) {
    val step = 36.dp.toPx()
    val stroke = 1.5.dp.toPx()
    // Diagonal lines covering the whole area (start off-canvas to the left)
    var startX = -size.height
    while (startX < size.width) {
        drawLine(
            color,
            Offset(startX, size.height),
            Offset(startX + size.height, 0f),
            strokeWidth = stroke
        )
        startX += step
    }
}

private fun DrawScope.drawWaves(color: Color) {
    val amplitude = 6.dp.toPx()
    val wavelength = 64.dp.toPx()
    val rowStep = 36.dp.toPx()
    val stroke = 1.5.dp.toPx()
    var y = rowStep / 2
    while (y < size.height + amplitude) {
        val path = Path()
        path.moveTo(0f, y)
        var x = 0f
        while (x <= size.width) {
            path.lineTo(x, y + (amplitude * sin((x / wavelength) * 2 * Math.PI)).toFloat())
            x += 8f
        }
        drawPath(path, color, style = Stroke(width = stroke))
        y += rowStep
    }
}

private fun DrawScope.drawRings(color: Color) {
    val step = 56.dp.toPx()
    val radius = 16.dp.toPx()
    val stroke = 1.5.dp.toPx()
    var y = step / 2
    var rowIndex = 0
    while (y < size.height + radius) {
        var x = if (rowIndex % 2 == 0) step / 2 else step
        while (x < size.width + radius) {
            drawCircle(color, radius = radius, center = Offset(x, y), style = Stroke(width = stroke))
            x += step
        }
        y += step
        rowIndex++
    }
}

private fun DrawScope.drawDiamonds(color: Color) {
    val step = 48.dp.toPx()
    val half = 10.dp.toPx()
    val stroke = 1.5.dp.toPx()
    var y = step / 2
    var rowIndex = 0
    while (y < size.height + half) {
        var x = if (rowIndex % 2 == 0) step / 2 else step
        while (x < size.width + half) {
            val path = Path()
            path.moveTo(x, y - half)
            path.lineTo(x + half, y)
            path.lineTo(x, y + half)
            path.lineTo(x - half, y)
            path.close()
            drawPath(path, color, style = Stroke(width = stroke))
            x += step
        }
        y += step
        rowIndex++
    }
}

private fun DrawScope.drawCrosses(color: Color) {
    val step = 40.dp.toPx()
    val arm = 5.dp.toPx()
    val stroke = 1.5.dp.toPx()
    var y = step / 2
    var rowIndex = 0
    while (y < size.height + arm) {
        var x = if (rowIndex % 2 == 0) step / 2 else step
        while (x < size.width + arm) {
            drawLine(color, Offset(x - arm, y), Offset(x + arm, y), strokeWidth = stroke)
            drawLine(color, Offset(x, y - arm), Offset(x, y + arm), strokeWidth = stroke)
            x += step
        }
        y += step
        rowIndex++
    }
}

private fun DrawScope.drawZigzag(color: Color) {
    val segment = 24.dp.toPx()
    val height = 10.dp.toPx()
    val rowStep = 40.dp.toPx()
    val stroke = 1.5.dp.toPx()
    var y = rowStep / 2
    while (y < size.height + height) {
        val path = Path()
        path.moveTo(0f, y)
        var x = 0f
        var up = true
        while (x <= size.width + segment) {
            x += segment
            path.lineTo(x, if (up) y - height else y + height)
            up = !up
        }
        drawPath(path, color, style = Stroke(width = stroke))
        y += rowStep
    }
}
