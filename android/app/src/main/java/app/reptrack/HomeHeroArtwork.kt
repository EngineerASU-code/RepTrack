package app.reptrack

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/** Compact, original dumbbell illustration for the next-workout card. */
@Composable
fun HomeHeroArtwork(
    modifier: Modifier = Modifier.size(88.dp),
    primaryColor: Color = Color(0xFF8957F6),
    secondaryColor: Color = Color(0xFFB4A0FF),
) {
    Canvas(modifier.semantics { contentDescription = "Фиолетовая иллюстрация гантели" }) {
        val unit = size.minDimension
        val center = Offset(size.width * .52f, size.height * .5f)
        val accent = primaryColor
        val light = secondaryColor

        drawCircle(
            Brush.radialGradient(
                listOf(accent.copy(alpha = .28f), accent.copy(alpha = .11f), Color.Transparent),
                center,
                unit * .48f,
            ),
            radius = unit * .48f,
            center = center,
        )
        drawCircle(accent.copy(alpha = .22f), unit * .42f, center, style = Stroke(unit * .012f))
        drawCircle(light.copy(alpha = .12f), unit * .35f, center, style = Stroke(unit * .008f))
        drawOval(
            Brush.radialGradient(listOf(accent.copy(alpha = .24f), Color.Transparent), center + Offset(0f, unit * .31f), unit * .28f),
            topLeft = Offset(center.x - unit * .29f, center.y + unit * .25f),
            size = Size(unit * .58f, unit * .14f),
        )

        rotate(-32f, center) {
            val plateBrush = Brush.horizontalGradient(
                listOf(accent.copy(alpha = .58f), accent, light.copy(alpha = .96f), accent.copy(alpha = .72f)),
                center.x - unit * .42f,
                center.x + unit * .42f,
            )
            val darkBrush = Brush.horizontalGradient(
                listOf(Color(0xFF211A32), accent.copy(alpha = .78f), light.copy(alpha = .85f), Color(0xFF34244F)),
                center.x - unit * .42f,
                center.x + unit * .42f,
            )

            fun rounded(x: Float, y: Float, width: Float, height: Float, brush: Brush, alpha: Float = 1f) {
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(x, y),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(width * .42f),
                    alpha = alpha,
                )
            }

            val y = center.y
            val h = unit
            // Soft cast shadow keeps the metal form separate from the card surface.
            rounded(center.x - h * .39f, y - h * .13f + h * .045f, h * .78f, h * .26f, darkBrush, .45f)

            // Weight plates, with beveled highlights and narrow grooves.
            rounded(center.x - h * .40f, y - h * .22f, h * .065f, h * .44f, darkBrush)
            rounded(center.x - h * .355f, y - h * .19f, h * .052f, h * .38f, plateBrush)
            rounded(center.x - h * .294f, y - h * .165f, h * .047f, h * .33f, darkBrush)
            rounded(center.x + h * .247f, y - h * .165f, h * .047f, h * .33f, darkBrush)
            rounded(center.x + h * .303f, y - h * .19f, h * .052f, h * .38f, plateBrush)
            rounded(center.x + h * .35f, y - h * .22f, h * .065f, h * .44f, darkBrush)
            drawLine(light.copy(alpha = .68f), Offset(center.x - h * .326f, y - h * .15f), Offset(center.x - h * .326f, y + h * .15f), h * .009f)
            drawLine(light.copy(alpha = .68f), Offset(center.x + h * .33f, y - h * .15f), Offset(center.x + h * .33f, y + h * .15f), h * .009f)

            // Shaft and textured center grip.
            rounded(center.x - h * .29f, y - h * .058f, h * .58f, h * .116f, darkBrush)
            rounded(center.x - h * .17f, y - h * .073f, h * .34f, h * .146f, plateBrush)
            for (groove in -2..2) {
                val x = center.x + groove * h * .047f
                drawLine(
                    Color(0xFF302344).copy(alpha = .72f),
                    Offset(x, y - h * .045f),
                    Offset(x, y + h * .045f),
                    h * .009f,
                )
            }
            drawLine(Color.White.copy(alpha = .56f), Offset(center.x - h * .13f, y - h * .056f), Offset(center.x + h * .13f, y - h * .056f), h * .008f)
        }
    }
}
