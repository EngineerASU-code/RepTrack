package app.reptrack

import android.content.Context
import android.util.Xml
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.ArrayDeque
import org.xmlpull.v1.XmlPullParser

internal enum class ExerciseMuscleGroup(
    val label: String,
    val compactLabel: String,
    val frontRegions: List<String> = emptyList(),
    val backRegions: List<String> = emptyList(),
) {
    CHEST("Большая грудная мышца", "Грудь", frontRegions = listOf("upper-pectoralis", "mid-lower-pectoralis")),
    SHOULDERS(
        "Дельтовидные мышцы",
        "Плечи",
        frontRegions = listOf("anterior-deltoid", "lateral-deltoid"),
        backRegions = listOf("posterior-deltoid"),
    ),
    TRAPS(
        "Верхняя часть трапециевидной мышцы",
        "Трапеции",
        frontRegions = listOf("upper-trapezius"),
        backRegions = listOf("upper-trapezius"),
    ),
    TRICEPS("Трицепс", "Трицепс", backRegions = listOf("long-head-triceps", "lateral-head-triceps", "medial-head-triceps")),
    LATS("Широчайшие мышцы спины", "Широчайшие", backRegions = listOf("lats")),
    BICEPS("Бицепс", "Бицепс", frontRegions = listOf("long-head-bicep", "short-head-bicep")),
    MIDDLE_BACK("Мышцы средней части спины", "Средняя спина", backRegions = listOf("traps-middle", "lower-trapezius")),
    QUADRICEPS("Квадрицепсы", "Квадрицепсы", frontRegions = listOf("outer-quadricep", "rectus-femoris", "inner-quadricep")),
    CALVES("Икроножные мышцы", "Икры", frontRegions = listOf("gastrocnemius", "soleus"), backRegions = listOf("gastrocnemius", "soleus")),
    GLUTES("Ягодичные мышцы", "Ягодицы", backRegions = listOf("gluteus-maximus", "gluteus-medius")),
    HAMSTRINGS("Задняя поверхность бедра", "Бицепс бедра", backRegions = listOf("lateral-hamstrings", "medial-hamstrings")),
    LOWER_BACK("Поясница", "Поясница", backRegions = listOf("lowerback")),
    FOREARMS(
        "Предплечья",
        "Предплечья",
        frontRegions = listOf("wrist-flexors", "wrist-extensors"),
        backRegions = listOf("wrist-flexors", "wrist-extensors"),
    ),
    ABS("Мышцы живота", "Пресс", frontRegions = listOf("upper-abdominals", "lower-abdominals", "obliques")),
}

private data class AnatomyShape(val groupId: String, val path: Path, val filled: Boolean, val stroked: Boolean, val strokeWidth: Float)

private data class AnatomyDiagram(val width: Float, val height: Float, val shapes: List<AnatomyShape>)

@Composable
internal fun ExerciseMuscleMap(
    primary: List<ExerciseMuscleGroup>,
    secondary: List<ExerciseMuscleGroup>,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val description = buildString {
        append("Карта мышц, вид спереди и сзади. Основная нагрузка: ")
        append(primary.joinToString { it.label })
        if (secondary.isNotEmpty()) append(". Дополнительно: ${secondary.joinToString { it.label }}")
    }
    val front = remember(context) { context.readAnatomyDiagram("exercise-artwork/anatomy/male_front_muscle_anatomy.svg") }
    val back = remember(context) { context.readAnatomyDiagram("exercise-artwork/anatomy/male_back_muscle_anatomy.svg") }

    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Text("Спереди", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                Text("Сзади", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val mapHeight = (maxWidth * .9f).coerceIn(240.dp, 360.dp)
                Row(Modifier.fillMaxWidth().height(mapHeight)) {
                    MuscleFigure(
                        diagram = front,
                        primaryRegions = primary.flatMap { it.frontRegions }.toSet(),
                        secondaryRegions = secondary.flatMap { it.frontRegions }.toSet(),
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        description = "$description. Вид спереди.",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    MuscleFigure(
                        diagram = back,
                        primaryRegions = primary.flatMap { it.backRegions }.toSet(),
                        secondaryRegions = secondary.flatMap { it.backRegions }.toSet(),
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        description = "$description. Вид сзади.",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MuscleFigure(
    diagram: AnatomyDiagram,
    primaryRegions: Set<String>,
    secondaryRegions: Set<String>,
    primaryColor: Color,
    secondaryColor: Color,
    description: String,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.semantics { contentDescription = description }) {
        drawDiagram(diagram, primaryRegions, secondaryRegions, primaryColor, secondaryColor)
    }
}

private fun DrawScope.drawDiagram(
    diagram: AnatomyDiagram,
    primaryRegions: Set<String>,
    secondaryRegions: Set<String>,
    primaryColor: Color,
    secondaryColor: Color,
) {
    val scale = minOf(size.width / diagram.width, size.height / diagram.height)
    val left = (size.width - diagram.width * scale) / 2f
    val top = (size.height - diagram.height * scale) / 2f
    val neutralFill = Color(0xFF393342)
    val neutralStroke = Color(0xFF81778F)

    translate(left, top) {
        scale(scale, pivot = Offset.Zero) {
            diagram.shapes.forEach { shape ->
                val primary = shape.groupId in primaryRegions
                val secondary = !primary && shape.groupId in secondaryRegions
                val fill = when {
                    primary -> primaryColor
                    secondary -> secondaryColor.copy(alpha = .72f)
                    else -> neutralFill
                }
                val outline = when {
                    primary -> primaryColor.copy(alpha = .95f)
                    secondary -> secondaryColor.copy(alpha = .88f)
                    else -> neutralStroke
                }
                if (shape.filled) drawPath(shape.path, fill, style = Fill)
                if (shape.stroked || shape.filled) drawPath(shape.path, outline, style = Stroke(shape.strokeWidth.coerceAtLeast(1.4f)))
            }
        }
    }
}

private fun Context.readAnatomyDiagram(assetPath: String): AnatomyDiagram {
    val parser = Xml.newPullParser()
    val groups = ArrayDeque<String>()
    val shapes = ArrayList<AnatomyShape>()
    var viewBoxWidth = 0f
    var viewBoxHeight = 0f

    assets.open(assetPath).use { input ->
        parser.setInput(input, null)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "svg" -> {
                        val viewBox = parser.getAttributeValue(null, "viewBox").orEmpty().split(Regex("[\\s,]+"))
                        viewBoxWidth = viewBox.getOrNull(2)?.toFloatOrNull() ?: 0f
                        viewBoxHeight = viewBox.getOrNull(3)?.toFloatOrNull() ?: 0f
                    }
                    "g" -> groups.addLast(parser.getAttributeValue(null, "id").orEmpty())
                    "path" -> {
                        parser.getAttributeValue(null, "d")?.let { pathData ->
                            val path = PathParser().parsePathString(pathData).toPath()
                            if (parser.getAttributeValue(null, "fill-rule") == "evenodd") path.fillType = PathFillType.EvenOdd
                            val group = groups.lastOrNull().orEmpty()
                            val fill = group != "body" && parser.getAttributeValue(null, "fill") != "none"
                            val stroke = parser.getAttributeValue(null, "stroke")?.let { it != "none" } ?: (group == "body")
                            val strokeWidth = parser.getAttributeValue(null, "stroke-width")?.toFloatOrNull() ?: 3.2f
                            shapes += AnatomyShape(group, path, fill, stroke, strokeWidth)
                        }
                    }
                    "line" -> {
                        val path = Path().apply {
                            moveTo(parser.number("x1"), parser.number("y1"))
                            lineTo(parser.number("x2"), parser.number("y2"))
                        }
                        val stroke = parser.getAttributeValue(null, "stroke")?.let { it != "none" } ?: true
                        val strokeWidth = parser.getAttributeValue(null, "stroke-width")?.toFloatOrNull() ?: 3.2f
                        shapes +=
                            AnatomyShape(groups.lastOrNull().orEmpty(), path, filled = false, stroked = stroke, strokeWidth = strokeWidth)
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "g" && groups.isNotEmpty()) groups.removeLast()
            }
        }
    }
    require(viewBoxWidth > 0f && viewBoxHeight > 0f && shapes.isNotEmpty()) { "Invalid anatomy asset: $assetPath" }
    return AnatomyDiagram(viewBoxWidth, viewBoxHeight, shapes)
}

private fun XmlPullParser.number(name: String): Float = getAttributeValue(null, name)?.toFloatOrNull() ?: 0f
