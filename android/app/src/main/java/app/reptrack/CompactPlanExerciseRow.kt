package app.reptrack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Compact S07 plan row. Group and summary are supplied by the caller from its current app state. */
@Composable
fun CompactPlanExerciseRow(
    exercise: PlanExercise,
    position: Int,
    groupLabel: String,
    summary: String,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PlanRowAction(
                description = "Изменить порядок упражнения «${exercise.name}»",
                onClick = onMove,
                dragHandle = true,
            )
            ExerciseArtwork(
                exercise.name,
                Modifier.size(48.dp).clickable(
                    onClickLabel = "Открыть упражнение ${exercise.name}",
                    onClick = onOpen,
                ),
            )
            Column(
                modifier = Modifier.weight(1f).clickable(
                    onClickLabel = "Открыть упражнение ${exercise.name}",
                    onClick = onOpen,
                ).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "${position.coerceAtLeast(1)}. ${exercise.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = .16f),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = groupLabel.ifBlank { "Группа не указана" },
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = summary,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            PlanRowAction(
                description = "Действия для упражнения «${exercise.name}»",
                onClick = onMenu,
                dragHandle = false,
            )
        }
    }
}

@Composable
private fun PlanRowAction(
    description: String,
    onClick: () -> Unit,
    dragHandle: Boolean,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).semantics { contentDescription = description },
    ) {
        val iconColor = MaterialTheme.colorScheme.onSurfaceVariant
        Canvas(Modifier.size(20.dp)) {
            val spacing = 5.dp.toPx()
            val radius = 1.6.dp.toPx()
            if (dragHandle) {
                repeat(3) { row ->
                    repeat(2) { column ->
                        drawCircle(
                            color = iconColor,
                            radius = radius,
                            center = Offset(center.x + (column - .5f) * spacing, center.y + (row - 1) * spacing),
                        )
                    }
                }
            } else {
                repeat(3) { dot ->
                    drawCircle(
                        color = iconColor,
                        radius = radius,
                        center = Offset(center.x, center.y + (dot - 1) * spacing),
                    )
                }
            }
        }
    }
}
