package app.reptrack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ProgressPeriod(val label: String, val days: Int?) {
    WEEK("Неделя", 7), MONTH("1 мес.", 30), THREE_MONTHS("3 мес.", 90), ALL("Всё время", null)
}

@Composable
fun ProgressScreen(
    state: DiarySnapshot,
    modifier: Modifier = Modifier,
    openSession: (String) -> Unit,
) {
    var period by rememberSaveable { mutableStateOf(ProgressPeriod.MONTH) }
    var metric by rememberSaveable { mutableStateOf(ProgressMetric.MAX_WEIGHT) }
    var chosenExercise by rememberSaveable { mutableStateOf<String?>(null) }
    var showExercises by remember { mutableStateOf(false) }
    var showMetricInfo by remember { mutableStateOf(false) }
    var exerciseQuery by rememberSaveable { mutableStateOf("") }
    val palette = LocalPalette.current
    val now = System.currentTimeMillis()
    val exerciseNames = remember(state.history) {
        state.history.asSequence().filter { it.status == SessionStatus.COMPLETED }
            .flatMap { it.exercises.asSequence() }.filter { it.tracksLoad && !it.skipped }
            .map { it.name }.distinct().sorted().toList()
    }
    val exercise = chosenExercise?.takeIf { it in exerciseNames } ?: exerciseNames.firstOrNull()
    val from = period.days?.let { now - it * dayMillis }
    val metrics = remember(state.history, exercise, from) {
        if (exercise == null) ProgressMetrics(0, 0, null, null, null) else calculateProgress(state.history, exercise, from)
    }
    val points = remember(state.history, exercise, from, metric) {
        exercise?.let { calculateProgressSeries(state.history, it, from, metric = metric) }.orEmpty()
    }
    var selectedSession by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = points.firstOrNull { it.sessionId == selectedSession } ?: points.lastOrNull()
    val previousPoint = selected?.let { current -> points.lastOrNull { it.timestamp < current.timestamp } }
    val selectedSets = selected?.let { point ->
        state.history.firstOrNull { it.id == point.sessionId }?.exercises?.firstOrNull { it.name == exercise }?.sets
            ?.filter { it.done && !it.skipped && it.kind == SetKind.WORK }.orEmpty()
    }.orEmpty()
    val recentSessions = remember(state.history, exercise) {
        state.history.asSequence().filter { session ->
            session.status == SessionStatus.COMPLETED && session.exercises.any { e ->
                e.name == exercise && e.tracksLoad && !e.skipped && e.sets.any { it.done && !it.skipped && it.kind == SetKind.WORK }
            }
        }.sortedByDescending { it.endedAt ?: it.startedAt }.toList()
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Прогресс", color = palette.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Динамика по упражнениям", color = palette.muted, style = MaterialTheme.typography.bodyLarge)
        }

        Card(
                modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable(enabled = exerciseNames.isNotEmpty()) { showExercises = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.muted.copy(alpha = .22f)),
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (exercise != null) ExerciseArtwork(exercise, Modifier.size(56.dp))
                    Column(Modifier.weight(1f)) {
                        Text(exercise ?: "Упражнение", color = palette.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(if (exercise != null) "История завершённых подходов" else "Нет записей с нагрузкой", color = palette.muted, style = MaterialTheme.typography.bodyMedium)
                        exercise?.let { name ->
                            val muscles = verifiedGuideFor(name)?.primaryMuscles.orEmpty()
                            Text(
                                muscles.takeIf { it.isNotEmpty() }?.joinToString(" · ") { "Основная группа · ${it.compactLabel}" }
                                    ?: "Основная группа не подтверждена",
                                color = palette.secondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    DownChevron(Modifier.padding(horizontal = 6.dp))
                }
            }
        if (showExercises) Dialog(onDismissRequest = { showExercises = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = palette.background) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 600.dp).fillMaxHeight().fillMaxWidth().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showExercises = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Назад", color = palette.secondary) }
                        Text("Упражнение", color = palette.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(exerciseQuery, { exerciseQuery = it }, Modifier.fillMaxWidth(), label = { Text("Поиск упражнения") }, singleLine = true)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val filtered = exerciseNames.filter { it.contains(exerciseQuery.trim(), ignoreCase = true) }
                        if (filtered.isEmpty()) item { Text("Ничего не найдено", Modifier.padding(16.dp), color = palette.muted) }
                        items(filtered, key = { it }) { name ->
                            Card(Modifier.fillMaxWidth().clickable { chosenExercise = name; selectedSession = null; showExercises = false; exerciseQuery = "" }, colors = CardDefaults.cardColors(containerColor = palette.surface)) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ExerciseArtwork(name, Modifier.size(52.dp))
                                    Text(name, Modifier.weight(1f), color = palette.text, fontWeight = FontWeight.SemiBold)
                                    if (name == exercise) Text("Выбрано", color = palette.secondary, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProgressMetric.entries.forEach { option ->
                FilterChip(selected = metric == option, onClick = { metric = option; selectedSession = null }, label = { Text(option.label()) }, modifier = Modifier.weight(1f).then(if (option == ProgressMetric.MAX_WEIGHT) Modifier.testTag("metric-max-weight") else Modifier))
            }
        }

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = palette.surface), border = androidx.compose.foundation.BorderStroke(1.dp, palette.muted.copy(alpha = .2f))) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(metric.cardTitle(), color = palette.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { showMetricInfo = true }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Информация о показателе ${metric.cardTitle()}" }) { InfoGlyph() }
                        }
                        Text("${metrics.sessions} ${trainingCountWord(metrics.sessions)} · ${metrics.completedWorkingSets} рабочих подходов", color = palette.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(selected?.let { it.value.display(metric, state.settings.weightUnit) } ?: "—", color = palette.accent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        selected?.let { point ->
                            Text(previousPoint?.let { changeLabel(point.value - it.value, it.value, metric, state.settings.weightUnit) } ?: "${dateShort(point.timestamp)}", color = palette.secondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (points.isNotEmpty()) {
                    ProgressChart(points, selected?.sessionId, metric, state.settings.weightUnit, Modifier.fillMaxWidth().height(184.dp), showAxes = true, tag = "progress-chart") { selectedSession = it }
                    selected?.let { point ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${dateLong(point.timestamp)} · ${point.value.display(metric, state.settings.weightUnit)}", color = palette.muted, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { openSession(point.sessionId) }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Тренировка", color = palette.secondary)
                                Spacer(Modifier.width(4.dp)); RightChevron()
                            }
                        }
                        if (selectedSets.isNotEmpty()) Text(
                            "Подходы: ${selectedSets.joinToString(" · ") { "${displayLoad(it.weight, state.settings.weightUnit)}×${it.reps}" }}",
                            color = palette.muted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                        )
                    }
                } else {
                    Column(Modifier.fillMaxWidth().heightIn(min = 132.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(if (exercise == null) "Выберите упражнение" else "Нет завершённых рабочих подходов за период", color = palette.muted)
                        if (exercise != null && period != ProgressPeriod.ALL) TextButton(onClick = { period = ProgressPeriod.ALL; selectedSession = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Показать всё время") }
                    }
                }
                ProgressPeriodSelector(period, onSelect = { period = it; selectedSession = null })
            }
        }

        val lifetimeWeight = remember(state.history, exercise) { exercise?.let { calculateProgressSeries(state.history, it, metric = ProgressMetric.MAX_WEIGHT).maxByOrNull { point -> point.value } } }
        val lifetimeReps = remember(state.history, exercise) { exercise?.let { calculateProgressSeries(state.history, it, metric = ProgressMetric.MAX_REPS).maxByOrNull { point -> point.value } } }
        val lifetimeE1rm = remember(state.history, exercise) { exercise?.let { calculateProgressSeries(state.history, it, metric = ProgressMetric.ESTIMATED_ONE_REP_MAX).maxByOrNull { point -> point.value } } }
        Text("Личные рекорды", color = palette.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProgressStat("Макс. вес", lifetimeWeight?.value?.display(ProgressMetric.MAX_WEIGHT, state.settings.weightUnit) ?: "—", lifetimeWeight?.timestamp?.let(::dateLong) ?: "Нет рекорда", ProgressStatIcon.TROPHY, Modifier.weight(1f))
                ProgressStat("Повторы", lifetimeReps?.value?.display(ProgressMetric.MAX_REPS, state.settings.weightUnit) ?: "—", lifetimeReps?.timestamp?.let(::dateLong) ?: "Нет рекорда", ProgressStatIcon.REPS, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProgressStat("Рабочие подходы", metrics.completedWorkingSets.toString(), if (period == ProgressPeriod.ALL) "За всё время" else "${period.label} · за период", ProgressStatIcon.SETS, Modifier.weight(1f))
                ProgressStat("e1RM · Эпли", lifetimeE1rm?.value?.display(ProgressMetric.ESTIMATED_ONE_REP_MAX, state.settings.weightUnit) ?: "—", lifetimeE1rm?.timestamp?.let(::dateLong) ?: "Нет рекорда", ProgressStatIcon.LIGHTNING, Modifier.weight(1f))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("История тренировок", color = palette.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${recentSessions.size}", color = palette.muted, style = MaterialTheme.typography.labelLarge)
        }
        if (recentSessions.isEmpty()) {
            Text("Завершённые тренировки с этим упражнением появятся здесь.", color = palette.muted)
        } else recentSessions.forEach { session ->
            ProgressHistoryCard(session, exercise.orEmpty(), metric, state.settings.weightUnit, openSession)
        }
        Text("Учитываются только завершённые рабочие подходы. Разминка, пропуски и незавершённые сессии исключены.", color = palette.muted, style = MaterialTheme.typography.bodySmall)
    }
    if (showMetricInfo) AlertDialog(
        onDismissRequest = { showMetricInfo = false },
        title = { Text(metric.cardTitle()) },
        text = { Text(metric.explanation()) },
        confirmButton = { TextButton(onClick = { showMetricInfo = false }) { Text("Понятно") } },
    )
}

@Composable
private fun ProgressPeriodSelector(selected: ProgressPeriod, onSelect: (ProgressPeriod) -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier.fillMaxWidth()
            .background(palette.background.copy(alpha = .7f), RoundedCornerShape(14.dp))
            .padding(3.dp)
            .testTag("progress-periods"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ProgressPeriod.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { option ->
                    val active = option == selected
                    Box(
                        Modifier.weight(1f).heightIn(min = 48.dp)
                            .background(if (active) palette.accent else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
                            .selectable(selected = active, role = Role.RadioButton) { onSelect(option) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(option.label, Modifier.padding(horizontal = 8.dp, vertical = 8.dp), color = if (active) MaterialTheme.colorScheme.onPrimary else palette.muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressChart(points: List<ProgressPoint>, selectedId: String?, metric: ProgressMetric, unit: WeightUnit, modifier: Modifier, showAxes: Boolean, tag: String? = null, onSelect: (String) -> Unit) {
    val palette = LocalPalette.current
    val low = points.minOf { it.value }
    val high = points.maxOf { it.value }
    val padding = ((high - low) * .16).coerceAtLeast(high * .10).coerceAtLeast(.5)
    val minValue = (low - padding).coerceAtLeast(0.0)
    val maxValue = (high + padding).coerceAtLeast(minValue + 1.0)
    val ticks = (0..4).map { maxValue - (maxValue - minValue) * it / 4 }
    val selectedIndex = points.indexOfFirst { it.sessionId == selectedId }.let { if (it < 0) points.lastIndex else it }
    val selectedPoint = points[selectedIndex]
    val chartActions = buildList {
        if (selectedIndex > 0) {
            val previous = points[selectedIndex - 1]
            add(CustomAccessibilityAction("Предыдущая точка: ${dateLong(previous.timestamp)}, ${previous.value.display(metric, unit)}") {
                onSelect(previous.sessionId)
                true
            })
        }
        if (selectedIndex < points.lastIndex) {
            val next = points[selectedIndex + 1]
            add(CustomAccessibilityAction("Следующая точка: ${dateLong(next.timestamp)}, ${next.value.display(metric, unit)}") {
                onSelect(next.sessionId)
                true
            })
        }
    }
    val coordinates = points.mapIndexed { index, point ->
        val xFraction = if (points.size == 1 || points.last().timestamp == points.first().timestamp) .5f else ((point.timestamp - points.first().timestamp).toDouble() / (points.last().timestamp - points.first().timestamp)).toFloat()
        Offset(xFraction, (1.0 - (point.value - minValue) / (maxValue - minValue)).toFloat())
    }
    Column(modifier) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showAxes) Column(Modifier.width(58.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                ticks.forEach { Text(it.display(metric, unit), color = palette.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().then(tag?.let { Modifier.testTag(it) } ?: Modifier).pointerInput(points, coordinates) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun selectByX(x: Float) {
                        val index = coordinates.indices.minByOrNull { i -> kotlin.math.abs(coordinates[i].x * size.width - x) } ?: return
                        onSelect(points[index].sessionId)
                    }
                    selectByX(down.position.x)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                selectByX(change.position.x)
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }.semantics {
                contentDescription = chartDescription(points, selectedIndex, selectedPoint, metric, unit)
                customActions = chartActions
            }) {
                val top = 6.dp.toPx(); val bottom = size.height - 6.dp.toPx()
                val chartHeight = (bottom - top).coerceAtLeast(1f)
                if (showAxes) for (i in 0..4) {
                    val y = top + chartHeight * i / 4
                    drawLine(palette.muted.copy(alpha = .2f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                val coords = coordinates.map { Offset(it.x * size.width, top + it.y * chartHeight) }
                if (coords.size > 1) {
                    val line = Path().apply { moveTo(coords.first().x, coords.first().y); coords.drop(1).forEach { lineTo(it.x, it.y) } }
                    val fill = Path().apply { moveTo(coords.first().x, bottom); coords.forEach { lineTo(it.x, it.y) }; lineTo(coords.last().x, bottom); close() }
                    drawPath(fill, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(palette.accent.copy(alpha = .32f), palette.accent.copy(alpha = .015f)), top, bottom))
                    drawPath(line, palette.accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                coords.forEachIndexed { index, p ->
                    val radius = if (index == selectedIndex) 5.dp.toPx() else 3.5.dp.toPx()
                    drawCircle(palette.background, radius + 2.dp.toPx(), p)
                    drawCircle(if (index == selectedIndex) palette.text else palette.accent, radius, p)
                }
            }
        }
        if (showAxes) Row(Modifier.fillMaxWidth().padding(start = 66.dp, top = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val labels = listOf(points.first(), points[(points.lastIndex / 2)], points.last()).distinctBy { it.timestamp }
            labels.forEach { Text(dateShort(it.timestamp), color = palette.muted, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
internal fun TrendGraph(
    history: List<TrainingSession>,
    exerciseName: String,
    from: Long?,
    metric: ProgressMetric,
    unit: WeightUnit,
    height: androidx.compose.ui.unit.Dp = 150.dp,
    showLabels: Boolean = true,
    onPointClick: ((String) -> Unit)? = null,
) {
    val points = calculateProgressSeries(history, exerciseName, from, metric = metric)
    if (points.isEmpty()) {
        Text("Нет завершённых рабочих подходов для графика.", color = LocalPalette.current.muted)
        return
    }
    var selected by remember(points, metric) { mutableStateOf(points.last().sessionId) }
    ProgressChart(points, selected, metric, unit, Modifier.fillMaxWidth().height(height), showAxes = showLabels) { id ->
        selected = id
        onPointClick?.invoke(id)
    }
}

private enum class ProgressStatIcon { TROPHY, REPS, SETS, LIGHTNING }

@Composable
private fun ProgressStat(title: String, value: String, detail: String, icon: ProgressStatIcon, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = palette.surface), border = androidx.compose.foundation.BorderStroke(1.dp, palette.muted.copy(alpha = .2f))) {
        Row(Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressStatGlyph(icon, Modifier.size(36.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = palette.muted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Text(value, color = palette.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(detail, color = palette.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ProgressStatGlyph(icon: ProgressStatIcon, modifier: Modifier = Modifier) {
    val accent = LocalPalette.current.accent
    Canvas(modifier) {
        drawCircle(accent.copy(alpha = .14f))
        val stroke = 2.dp.toPx()
        val centerX = size.width / 2; val centerY = size.height / 2
        when (icon) {
            ProgressStatIcon.TROPHY -> {
                val cup = Path().apply {
                    moveTo(size.width * .28f, size.height * .2f); lineTo(size.width * .72f, size.height * .2f)
                    lineTo(size.width * .66f, size.height * .57f); lineTo(size.width * .56f, size.height * .7f)
                    lineTo(size.width * .44f, size.height * .7f); lineTo(size.width * .34f, size.height * .57f); close()
                }
                drawPath(cup, accent, style = Stroke(stroke, join = StrokeJoin.Round))
                drawLine(accent, Offset(centerX, size.height * .7f), Offset(centerX, size.height * .82f), stroke, cap = StrokeCap.Round)
                drawLine(accent, Offset(size.width * .36f, size.height * .84f), Offset(size.width * .64f, size.height * .84f), stroke, cap = StrokeCap.Round)
                drawLine(accent, Offset(size.width * .28f, size.height * .29f), Offset(size.width * .16f, size.height * .29f), stroke, cap = StrokeCap.Round)
                drawLine(accent, Offset(size.width * .16f, size.height * .29f), Offset(size.width * .2f, size.height * .48f), stroke, cap = StrokeCap.Round)
                drawLine(accent, Offset(size.width * .72f, size.height * .29f), Offset(size.width * .84f, size.height * .29f), stroke, cap = StrokeCap.Round)
                drawLine(accent, Offset(size.width * .84f, size.height * .29f), Offset(size.width * .8f, size.height * .48f), stroke, cap = StrokeCap.Round)
            }
            ProgressStatIcon.REPS -> {
                for (y in listOf(.32f, .5f, .68f)) drawLine(accent, Offset(size.width * .28f, size.height * y), Offset(size.width * .72f, size.height * y), stroke, cap = StrokeCap.Round)
                drawLine(accent, Offset(size.width * .2f, size.height * .4f), Offset(size.width * .2f, size.height * .6f), stroke, cap = StrokeCap.Round)
                drawLine(accent, Offset(size.width * .8f, size.height * .4f), Offset(size.width * .8f, size.height * .6f), stroke, cap = StrokeCap.Round)
            }
            ProgressStatIcon.SETS -> {
                for (offset in listOf(0f, .16f, .32f)) {
                    val path = Path().apply { moveTo(size.width * .24f, size.height * (.32f + offset)); lineTo(centerX, size.height * (.2f + offset)); lineTo(size.width * .76f, size.height * (.32f + offset)); lineTo(centerX, size.height * (.44f + offset)); close() }
                    drawPath(path, accent, style = Stroke(stroke, join = StrokeJoin.Round))
                }
            }
            ProgressStatIcon.LIGHTNING -> {
                val bolt = Path().apply { moveTo(size.width * .56f, size.height * .15f); lineTo(size.width * .32f, size.height * .54f); lineTo(size.width * .49f, size.height * .54f); lineTo(size.width * .42f, size.height * .85f); lineTo(size.width * .7f, size.height * .43f); lineTo(size.width * .52f, size.height * .43f); close() }
                drawPath(bolt, accent, style = Stroke(stroke, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun ProgressHistoryCard(session: TrainingSession, exercise: String, metric: ProgressMetric, unit: WeightUnit, openSession: (String) -> Unit) {
    val palette = LocalPalette.current
    val entry = session.exercises.firstOrNull { it.name == exercise }
    val sets = entry?.sets.orEmpty().filter { it.done && !it.skipped && it.kind == SetKind.WORK }
    val sessionValue = calculateProgressSeries(listOf(session), exercise, metric = metric).firstOrNull()?.value
    Card(modifier = Modifier.fillMaxWidth().clickable { openSession(session.id) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = palette.surface), border = androidx.compose.foundation.BorderStroke(1.dp, palette.muted.copy(alpha = .2f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(dateLong(session.endedAt ?: session.startedAt), color = palette.text, fontWeight = FontWeight.Bold)
                Text(
                    sets.joinToString("   ") { "${displayLoad(it.weight, unit)}×${it.reps}" }
                        .takeIf { it.isNotBlank() }?.let { "Подходы · $it" } ?: "Нет рабочих подходов",
                    color = palette.muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(metric.label(), color = palette.muted, style = MaterialTheme.typography.labelSmall)
                Text(sessionValue?.display(metric, unit) ?: "—", color = palette.text, fontWeight = FontWeight.Bold)
                Text(session.planName, color = palette.muted, style = MaterialTheme.typography.labelSmall, maxLines = 2)
            }
            RightChevron()
        }
    }
}

private const val dayMillis = 86_400_000L
private fun ProgressMetric.label() = when (this) {
    ProgressMetric.MAX_WEIGHT -> "Макс. вес"
    ProgressMetric.MAX_REPS -> "Повторы"
    ProgressMetric.ESTIMATED_ONE_REP_MAX -> "e1RM"
}
private fun ProgressMetric.cardTitle() = when (this) {
    ProgressMetric.MAX_WEIGHT -> "Максимальный вес"
    ProgressMetric.MAX_REPS -> "Максимум повторений"
    ProgressMetric.ESTIMATED_ONE_REP_MAX -> "Расчётный 1ПМ"
}
private fun ProgressMetric.explanation() = when (this) {
    ProgressMetric.MAX_WEIGHT -> "Точка показывает самый тяжёлый завершённый рабочий подход этого упражнения за тренировку. Разминка, пропуски и незавершённые сессии не учитываются."
    ProgressMetric.MAX_REPS -> "Точка показывает наибольшее число повторений в завершённом рабочем подходе этого упражнения за тренировку."
    ProgressMetric.ESTIMATED_ONE_REP_MAX -> "Расчёт по Эпли: вес × (1 + повторы / 30). Оценка считается только для завершённых рабочих подходов на 1–10 повторений; это не измеренный максимум."
}
private fun Double.display(metric: ProgressMetric, unit: WeightUnit, withUnit: Boolean = true): String = when (metric) {
    ProgressMetric.MAX_WEIGHT, ProgressMetric.ESTIMATED_ONE_REP_MAX -> {
        val value = fromKilograms(this, unit)
        val number = compactNumber(value)
        if (withUnit) "$number ${if (unit == WeightUnit.KG) "кг" else "lb"}" else number
    }
    ProgressMetric.MAX_REPS -> "${compactNumber(this)}${if (withUnit) " повт." else ""}"
}
private fun compactNumber(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
private fun dateShort(timestamp: Long) = SimpleDateFormat("d.M", Locale.forLanguageTag("ru")).format(Date(timestamp))
private fun dateLong(timestamp: Long) = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru")).format(Date(timestamp))
private fun displayLoad(storedKg: String, unit: WeightUnit): String {
    val value = parseFiniteWeight(storedKg) ?: return storedKg
    return compactNumber(fromKilograms(value, unit))
}
private fun changeLabel(change: Double, base: Double, metric: ProgressMetric, unit: WeightUnit): String {
    val percent = if (base != 0.0) change / base * 100 else 0.0
    val sign = if (change > 0) "+" else ""
    return "$sign${change.display(metric, unit)} · ${if (percent > 0) "+" else ""}${String.format(Locale.US, "%.1f%%", percent)}"
}
private fun chartDescription(points: List<ProgressPoint>, selectedIndex: Int, selected: ProgressPoint, metric: ProgressMetric, unit: WeightUnit) = buildString {
    append("График ${metric.cardTitle()}. ${points.size} ${trainingCountWord(points.size)}. ")
    append("Выбрана точка ${selectedIndex + 1} из ${points.size}: ${dateLong(selected.timestamp)}, ${selected.value.display(metric, unit)}. Значения: ")
    points.forEachIndexed { index, point ->
        append("${index + 1}: ${dateLong(point.timestamp)}, ${point.value.display(metric, unit)}. ")
    }
    if (points.size > 1) append("Для перехода по значениям используйте действия «Предыдущая точка» и «Следующая точка».")
}

private fun trainingCountWord(count: Int): String = when {
    count % 100 in 11..14 -> "тренировок"
    count % 10 == 1 -> "тренировка"
    count % 10 in 2..4 -> "тренировки"
    else -> "тренировок"
}

@Composable
private fun DownChevron(modifier: Modifier = Modifier) {
    val color = LocalPalette.current.secondary
    Canvas(modifier.size(24.dp)) {
        val p = Path().apply { moveTo(size.width * .25f, size.height * .4f); lineTo(size.width * .5f, size.height * .65f); lineTo(size.width * .75f, size.height * .4f) }
        drawPath(p, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun RightChevron() {
    val color = LocalPalette.current.secondary
    Canvas(Modifier.size(20.dp)) {
        val p = Path().apply { moveTo(size.width * .4f, size.height * .25f); lineTo(size.width * .65f, size.height * .5f); lineTo(size.width * .4f, size.height * .75f) }
        drawPath(p, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun InfoGlyph() {
    val color = LocalPalette.current.secondary
    Canvas(Modifier.size(20.dp)) {
        val stroke = 1.8.dp.toPx()
        drawCircle(color, radius = size.minDimension * .42f, style = Stroke(stroke))
        drawCircle(color, radius = stroke * 1.1f, center = Offset(size.width * .5f, size.height * .32f))
        drawLine(color, Offset(size.width * .5f, size.height * .46f), Offset(size.width * .5f, size.height * .72f), stroke, cap = StrokeCap.Round)
    }
}
