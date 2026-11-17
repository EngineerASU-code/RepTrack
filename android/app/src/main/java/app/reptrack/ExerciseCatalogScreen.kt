package app.reptrack

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ExerciseCatalogContent(
    entries: List<ExerciseCatalogEntry>,
    selectForPlan: Boolean,
    selectedMuscle: ExerciseMuscleGroup?,
    onMuscleSelected: (ExerciseMuscleGroup?) -> Unit,
    query: String,
    onQueryChanged: (String) -> Unit,
    selectedNames: Set<String>,
    onToggleSelected: (String, Boolean) -> Unit,
    onOpenExercise: (String) -> Unit,
    onAddSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val search = query.trim()
    val filteredEntries = entries.filter { entry ->
        (
            search.isEmpty() ||
                entry.name.contains(search, ignoreCase = true) ||
                entry.aliases.any {
                    it.contains(search, ignoreCase = true)
                }
            ) &&
            (selectedMuscle == null || selectedMuscle in entry.primaryMuscles || selectedMuscle in entry.secondaryMuscles)
    }

    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth().testTag("catalog-search"),
            label = { Text("Поиск упражнения") },
            singleLine = true,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selectedMuscle == null, { onMuscleSelected(null) }, label = { Text("Все мышцы") })
            ExerciseMuscleGroup.entries.forEach { muscle ->
                FilterChip(
                    selected = selectedMuscle == muscle,
                    onClick = { onMuscleSelected(if (selectedMuscle == muscle) null else muscle) },
                    label = { Text(muscle.compactLabel) },
                    modifier = Modifier.testTag("catalog-muscle-${muscle.name}"),
                )
            }
        }
        Text("${filteredEntries.size} упражнений", color = palette.muted, style = MaterialTheme.typography.labelMedium)
        LazyColumn(
            Modifier.weight(1f).testTag("catalog-list"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (filteredEntries.isEmpty()) {
                item {
                    Text(
                        if (entries.isEmpty()) "Каталог упражнений пока пуст." else "Поиск и фильтры ничего не нашли.",
                        color = palette.muted,
                    )
                    if (search.isNotEmpty() || selectedMuscle != null) {
                        TextButton(onClick = {
                            onQueryChanged("")
                            onMuscleSelected(null)
                        }) { Text("Сбросить поиск и фильтр") }
                    }
                }
            }
            items(filteredEntries, key = ExerciseCatalogEntry::id) { entry ->
                val selected = entry.name in selectedNames
                Card(
                    onClick = { if (selectForPlan) onToggleSelected(entry.name, !selected) else onOpenExercise(entry.name) },
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    border = BorderStroke(
                        if (selected) 1.5.dp else 1.dp,
                        if (selected) palette.accent else palette.muted.copy(alpha = .18f),
                    ),
                    modifier = Modifier.testTag("catalog-${entry.id}"),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.name,
                                    color = palette.text,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(entry.equipment, color = palette.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            if (selectForPlan) Checkbox(checked = selected, onCheckedChange = { onToggleSelected(entry.name, it) })
                            TextButton(onClick = {
                                onOpenExercise(entry.name)
                            }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Детали") }
                        }
                        ExerciseArtwork(entry, Modifier.fillMaxWidth().height(176.dp))
                        Text(
                            "Основная нагрузка · ${entry.primaryMuscles.joinToString {
                                it.compactLabel
                            }}",
                            color = palette.secondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (entry.secondaryMuscles.isNotEmpty()) {
                            Text(
                                "Дополнительно · ${entry.secondaryMuscles.joinToString {
                                    it.compactLabel
                                }}",
                                color = palette.muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
        if (selectForPlan) {
            Button(
                onClick = onAddSelected,
                enabled = selectedNames.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Добавить выбранные · ${selectedNames.size}")
            }
        }
    }
}
