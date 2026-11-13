package app.reptrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.net.Uri
import java.io.ByteArrayOutputStream

private data class AppPalette(val background: Color, val surface: Color, val accent: Color, val secondary: Color, val muted: Color, val text: Color, val light: Boolean = false) {
    fun colorScheme() = if (light) lightColorScheme(background = background, surface = surface, primary = accent, secondary = secondary, onBackground = text, onSurface = text)
    else darkColorScheme(background = background, surface = surface, primary = accent, secondary = secondary, onBackground = text, onSurface = text)
}

private val purplePro = AppPalette(Color(0xFF12101C), Color(0xFF211C31), Color(0xFF8957F6), Color(0xFFB4A0FF), Color(0xFFA9A3B9), Color(0xFFF7F5FC))
private val LocalPalette = staticCompositionLocalOf { purplePro }
private fun paletteFor(theme: DiaryTheme) = when (theme) {
    DiaryTheme.PURPLE_PRO -> purplePro
    DiaryTheme.LIME_ENERGY -> AppPalette(Color(0xFF10150E), Color(0xFF1B2517), Color(0xFFB2F36B), Color(0xFFD6FFA0), Color(0xFFAAB59F), Color(0xFFF5F8F1))
    DiaryTheme.BLUE_TECH -> AppPalette(Color(0xFF0E121C), Color(0xFF1A2333), Color(0xFF448AFF), Color(0xFFA9C6FF), Color(0xFFA4ADBF), Color(0xFFF5F7FC))
    DiaryTheme.ORANGE_POWER -> AppPalette(Color(0xFF17120F), Color(0xFF2A1E18), Color(0xFFFF8A34), Color(0xFFFFC18F), Color(0xFFB9A89B), Color(0xFFFFF7F1))
    DiaryTheme.LIGHT_CLEAN -> AppPalette(Color(0xFFF4F2F8), Color(0xFFFFFFFF), Color(0xFF6843D6), Color(0xFF6843D6), Color(0xFF655E73), Color(0xFF1E1928), light = true)
    DiaryTheme.GRAPHITE_MINIMAL -> AppPalette(Color(0xFF111315), Color(0xFF202326), Color(0xFFB7C0C8), Color(0xFFD8DEE3), Color(0xFF9AA2A9), Color(0xFFF3F5F6))
}
private fun DiaryTheme.title() = when (this) {
    DiaryTheme.PURPLE_PRO -> "Purple Pro"
    DiaryTheme.LIME_ENERGY -> "Lime Energy"
    DiaryTheme.BLUE_TECH -> "Blue Tech"
    DiaryTheme.ORANGE_POWER -> "Orange Power"
    DiaryTheme.LIGHT_CLEAN -> "Light Clean"
    DiaryTheme.GRAPHITE_MINIMAL -> "Graphite Minimal"
}
private val tabs = listOf("Главная", "Зал", "План", "Прогресс", "История")
private val tabMarks = listOf("⌂", "♧", "▦", "↗", "◷")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { val vm: DiaryViewModel = viewModel(); RepTrackApp(vm) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepTrackApp(vm: DiaryViewModel) {
    val state by vm.snapshot.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf("Главная") }
    var confirm by remember { mutableStateOf<String?>(null) }
    var historyTarget by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.activeSession?.id) { while (state.activeSession != null) { delay(1000); tick++ } }

    val palette = paletteFor(state.settings.theme)
    CompositionLocalProvider(LocalPalette provides palette) {
    MaterialTheme(colorScheme = palette.colorScheme()) {
        Scaffold(
            containerColor = LocalPalette.current.background,
            topBar = { TopAppBar(title = { Text("✦ RepTrack", fontWeight = FontWeight.Bold) }, actions = { IconButton(onClick = { tab = "Настройки" }) { Text("⚙", color = LocalPalette.current.secondary) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = LocalPalette.current.background, titleContentColor = LocalPalette.current.text)) },
            bottomBar = {
                NavigationBar(containerColor = LocalPalette.current.surface) {
                    tabs.forEachIndexed { index, item ->
                        NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Text(tabMarks[index], style = MaterialTheme.typography.titleMedium) }, label = { Text(item) })
                    }
                }
            },
        ) { padding ->
            if (state.loading) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else when (tab) {
                "Зал" -> GymScreen(state, vm, Modifier.padding(padding), tick) { confirm = it }
                "План" -> PlanScreen(state, vm, Modifier.padding(padding))
                "История" -> HistoryScreen(state.history, state.settings.weightUnit, historyTarget, { historyTarget = null }, Modifier.padding(padding))
                "Прогресс" -> ProgressScreen(state, Modifier.padding(padding)) { id -> historyTarget = id; tab = "История" }
                "Настройки" -> SettingsScreen(state, vm, Modifier.padding(padding))
                else -> HomeScreen(state, vm, Modifier.padding(padding)) { tab = it }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        }
    }

    confirm?.let { action ->
        val session = state.activeSession
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (action == "finish") "Завершить тренировку?" else "Отменить тренировку?") },
            text = { Text(if (action == "finish") "Сессия попадёт в историю. Повторно завершить её нельзя." else "Сессия будет помечена отменённой и не попадёт в аналитику.") },
            confirmButton = { TextButton(onClick = { session?.let { if (action == "finish") vm.finish(it.id) else vm.cancel(it.id) }; confirm = null }) { Text(if (action == "finish") "Завершить" else "Отменить сессию") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Назад") } },
        )
    }
    }
}

@Composable
private fun ScreenColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = LocalPalette.current.text)
        Text(subtitle, color = LocalPalette.current.muted)
    }
}

@Composable
private fun HomeScreen(state: DiarySnapshot, vm: DiaryViewModel, modifier: Modifier, navigate: (String) -> Unit) {
    val active = state.activeSession
    val next = state.nextPlan
    ScreenColumn(modifier) {
        ScreenTitle("Твой прогресс. Твой план.", "Тренировки сохраняются на этом устройстве")
        Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (active != null) "ТРЕНИРОВКА В ПРОЦЕССЕ" else "СЛЕДУЮЩАЯ ТРЕНИРОВКА", color = LocalPalette.current.secondary, style = MaterialTheme.typography.labelLarge)
                Text(active?.planName ?: next?.name ?: "Создай план", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = LocalPalette.current.text)
                Text(if (active != null) "${active.completedSets} из ${active.totalSets} рабочих подходов" else "Цикл: ${state.plans.filter { it.inCycle }.joinToString(" → ") { it.name }}", color = LocalPalette.current.muted)
                Button(onClick = { vm.start(active?.planId ?: next?.id); navigate("Зал") }, modifier = Modifier.fillMaxWidth(), enabled = active != null || next != null) {
                    Text(if (active != null) "Продолжить тренировку" else "▶  Начать тренировку")
                }
            }
        }
        if (active == null && next == null) {
            TextButton(onClick = { navigate("План") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Создать план тренировок") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Последняя сессия", fontWeight = FontWeight.SemiBold, color = LocalPalette.current.text)
                state.history.firstOrNull()?.let { session ->
                    Text("${session.planName} · ${date(session.endedAt ?: session.startedAt)}", color = LocalPalette.current.secondary)
                    Text("${session.exercises.size} упражнений · ${session.completedSets} рабочих подходов · макс. вес ${session.maxWeightKg?.let { formatWeight(it, state.settings.weightUnit) } ?: "—"}", color = LocalPalette.current.muted)
                } ?: Text("История пока пуста. Заверши первую тренировку — она появится здесь.", color = LocalPalette.current.muted)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Прогресс", fontWeight = FontWeight.SemiBold, color = LocalPalette.current.text); TextButton(onClick = { navigate("Прогресс") }) { Text("Подробнее") } }
                Text("${state.workouts} завершённых тренировок · лучший вес ${state.bestWeightKg?.let { formatWeight(it, state.settings.weightUnit) } ?: "—"}", color = LocalPalette.current.muted)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GymScreen(state: DiarySnapshot, vm: DiaryViewModel, modifier: Modifier, tick: Long, confirm: (String) -> Unit) {
    val session = state.activeSession
    var replacementFor by remember { mutableStateOf<String?>(null) }
    var detailsFor by remember { mutableStateOf<String?>(null) }
    ScreenColumn(modifier) {
        ScreenTitle("Зал", "Отмечай фактические подходы")
        if (session == null) {
            Text("Выбери тренировку", style = MaterialTheme.typography.titleLarge, color = LocalPalette.current.text)
            state.plans.forEach { plan ->
                Card(onClick = { vm.start(plan.id) }, colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text(plan.name, fontWeight = FontWeight.Bold, color = LocalPalette.current.text); Text("${plan.exercises.size} упражнений", color = LocalPalette.current.muted) }
                        Text("Начать →", color = LocalPalette.current.secondary)
                    }
                }
            }
        } else {
            val elapsed = ((System.currentTimeMillis() - session.startedAt) / 60000L).coerceAtLeast(0)
            Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(session.planName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LocalPalette.current.text)
                    Text("${elapsed} мин · ${session.completedSets}/${session.totalSets} рабочих подходов", color = LocalPalette.current.muted)
                    OutlinedTextField(value = session.comment, onValueChange = { vm.updateComment(session.id, it) }, modifier = Modifier.fillMaxWidth(), label = { Text("Комментарий к тренировке") }, minLines = 2, maxLines = 4)
                    LinearProgressIndicator(progress = { if (session.totalSets == 0) 0f else session.completedSets.toFloat() / session.totalSets }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { confirm("finish") }, modifier = Modifier.weight(1f)) { Text("Завершить") }
                        OutlinedButton(onClick = { confirm("cancel") }, modifier = Modifier.weight(1f)) { Text("Отменить") }
                    }
                }
            }
            session.exercises.forEach { exercise ->
                Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${exercise.position + 1}. ${exercise.name}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = LocalPalette.current.text)
                            TextButton(onClick = { vm.skipExercise(exercise.id, !exercise.skipped) }) { Text(if (exercise.skipped) "Вернуть" else "Пропустить") }
                            Box {
                                TextButton(onClick = { replacementFor = exercise.id }) { Text("Замена") }
                                DropdownMenu(expanded = replacementFor == exercise.id, onDismissRequest = { replacementFor = null }) {
                                    state.plans.flatMap { it.exercises }.distinctBy { it.name }.forEach { option ->
                                        DropdownMenuItem(text = { Text(option.name) }, onClick = { vm.replaceExercise(exercise.id, option); replacementFor = null })
                                    }
                                }
                            }
                        }
                        state.history.asSequence().flatMap { it.exercises.asSequence() }.firstOrNull { it.name == exercise.name }?.let { previous ->
                            val prev = previous.sets.filter { it.done && !it.skipped && it.kind == SetKind.WORK }.joinToString(" · ") { "${displayWeight(it.weight, state.settings.weightUnit).ifBlank { "—" }}×${it.reps.ifBlank { "—" }}" }
                            if (prev.isNotBlank()) Text("Прошлый раз · $prev", style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted)
                        }
                        TextButton(onClick = { detailsFor = exercise.name }, modifier = Modifier.heightIn(min = 48.dp)) { Text("История и детали упражнения") }
                        if (exercise.skipped) Text("Упражнение пропущено", color = LocalPalette.current.muted)
                        else exercise.sets.forEachIndexed { index, set ->
                            SetEditor(index, set, vm, tick, exercise.tracksLoad, state.settings.weightUnit)
                        }
                        if (!exercise.skipped) OutlinedButton(onClick = { vm.addSet(exercise.id) }, modifier = Modifier.fillMaxWidth()) { Text("＋ Добавить подход") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    detailsFor?.let { name ->
        val related = state.history.mapNotNull { session -> session.exercises.firstOrNull { it.name == name && !it.skipped }?.let { session to it } }
        val record = related.flatMap { it.second.sets }.filter { it.done && !it.skipped && it.kind == SetKind.WORK }.mapNotNull { it.weight.toDoubleOrNull() }.maxOrNull()
        AlertDialog(
            onDismissRequest = { detailsFor = null },
            title = { Text(name) },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Проверенное описание техники и данные об оборудовании/мышцах пока не добавлены.", color = LocalPalette.current.muted)
                Text("Личный рекорд: ${record?.let { formatWeight(it, state.settings.weightUnit) } ?: "нет завершённых подходов"}", color = LocalPalette.current.secondary)
                related.take(5).forEach { (past, item) ->
                    val sets = item.sets.filter { it.done && !it.skipped && it.kind == SetKind.WORK }.joinToString(" · ") { "${displayWeight(it.weight, state.settings.weightUnit).ifBlank { "—" }}×${it.reps.ifBlank { "—" }}" }
                    Text("${date(past.endedAt ?: past.startedAt)} · ${sets.ifBlank { "нет выполненных рабочих подходов" }}", color = LocalPalette.current.text)
                }
            } },
            confirmButton = { TextButton(onClick = { detailsFor = null }) { Text("Закрыть") } },
        )
    }
}

@Composable
private fun SetEditor(index: Int, set: TrainingSet, vm: DiaryViewModel, tick: Long, tracksLoad: Boolean, unit: WeightUnit) {
    var weight by remember(set.id, set.weight, unit) { mutableStateOf(displayWeight(set.weight, unit)) }
    var reps by remember(set.id) { mutableStateOf(set.reps) }
    val seconds = set.restUntil?.let { ((it - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0) } ?: (set.restRemaining / 1000).toInt()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${index + 1}", color = LocalPalette.current.muted, modifier = Modifier.width(20.dp))
            if (tracksLoad) {
                OutlinedTextField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(8); vm.updateSet(set.id, weight = canonicalWeight(weight, unit)) }, Modifier.weight(1f), label = { Text("Вес, ${unit.label()}") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(reps, { reps = it.filter(Char::isDigit).take(4); vm.updateSet(set.id, reps = reps) }, Modifier.weight(1f), label = { Text("Повторы") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            } else Text("Без силовой нагрузки", Modifier.weight(2f), color = LocalPalette.current.muted)
            Checkbox(checked = set.done, onCheckedChange = { vm.toggleSet(set) }, modifier = Modifier.size(48.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = set.kind == SetKind.WORK, onClick = { vm.setKind(set.id, if (set.kind == SetKind.WORK) SetKind.WARMUP else SetKind.WORK) }, label = { Text(if (set.kind == SetKind.WORK) "Рабочий" else "Разминочный") })
            if (seconds > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Отдых ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", color = LocalPalette.current.secondary)
                TextButton(onClick = { if (set.restUntil != null) vm.pauseRest(set.id) else vm.resumeRest(set.id) }) { Text(if (set.restUntil != null) "Пауза" else "Продолжить") }
                TextButton(onClick = { vm.skipRest(set.id) }) { Text("Пропустить") }
            }
            TextButton(onClick = { vm.removeSet(set.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Удалить") }
        }
    }
    tick.hashCode()
}

@Composable
private fun PlanScreen(state: DiarySnapshot, vm: DiaryViewModel, modifier: Modifier) {
    var addToPlan by remember { mutableStateOf<TrainingPlan?>(null) }
    var exerciseName by remember { mutableStateOf("") }
    var createPlan by remember { mutableStateOf(false) }
    var renamePlan by remember { mutableStateOf<TrainingPlan?>(null) }
    var deletePlan by remember { mutableStateOf<TrainingPlan?>(null) }
    var editExercise by remember { mutableStateOf<Pair<TrainingPlan, PlanExercise>?>(null) }
    var removeExercise by remember { mutableStateOf<Pair<TrainingPlan, PlanExercise>?>(null) }
    ScreenColumn(modifier) {
        ScreenTitle("План", "Создавай шаблоны и настраивай тренировочный цикл")
        Button(onClick = { createPlan = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Создать тренировку") }
        if (state.plans.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Пока нет планов", color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
                    Text("Можно начать с пустого плана или добавить предложенный цикл. Это не создаст историю тренировок.", color = LocalPalette.current.muted)
                    OutlinedButton(onClick = { vm.createStarterPlans() }, modifier = Modifier.fillMaxWidth()) { Text("Добавить стартовый цикл Push · Legs · Pull · Up") }
                }
            }
        }
        state.plans.forEach { plan ->
            Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(plan.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LocalPalette.current.text); Text(if (plan.inCycle) "${plan.position + 1} · в цикле" else "вне цикла", color = LocalPalette.current.muted) }
                        TextButton(onClick = { vm.movePlan(plan.id, -1) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("↑") }
                        TextButton(onClick = { vm.movePlan(plan.id, 1) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("↓") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        TextButton(onClick = { renamePlan = plan }) { Text("Переименовать") }
                        TextButton(onClick = { vm.duplicatePlan(plan.id) }) { Text("Дублировать") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        TextButton(onClick = { vm.setPlanInCycle(plan.id, !plan.inCycle) }) { Text(if (plan.inCycle) "Убрать из цикла" else "В цикл") }
                        TextButton(onClick = { deletePlan = plan }, enabled = state.activeSession?.planId != plan.id) { Text("Удалить") }
                    }
                    plan.exercises.forEachIndexed { index, exercise ->
                        Column {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}. ${exercise.name}", Modifier.weight(1f), color = LocalPalette.current.text)
                                TextButton(onClick = { editExercise = plan to exercise }) { Text("Изм.") }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { vm.moveExercise(exercise.id, plan.id, -1) }) { Text("↑ Выше") }
                                TextButton(onClick = { vm.moveExercise(exercise.id, plan.id, 1) }) { Text("↓ Ниже") }
                                TextButton(onClick = { removeExercise = plan to exercise }) { Text("Удалить") }
                            }
                        }
                        Text("${exercise.plannedSets} подхода · ${displayWeight(exercise.targetWeight, state.settings.weightUnit).ifBlank { "вес не задан" }}${if (exercise.targetReps.isBlank()) "" else " × ${exercise.targetReps}"} · отдых ${exercise.restSeconds} с", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { addToPlan = plan; exerciseName = "" }, modifier = Modifier.fillMaxWidth()) { Text("＋ Добавить упражнение") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    addToPlan?.let { plan ->
        AlertDialog(onDismissRequest = { addToPlan = null }, title = { Text("Новое упражнение · ${plan.name}") }, text = {
            OutlinedTextField(value = exerciseName, onValueChange = { exerciseName = it }, label = { Text("Название") }, singleLine = true)
        }, confirmButton = { TextButton(onClick = { vm.addExercise(plan.id, exerciseName); addToPlan = null }) { Text("Добавить") } }, dismissButton = { TextButton(onClick = { addToPlan = null }) { Text("Назад") } })
    }
    if (createPlan) PlanNameDialog("Новый план", "", onDismiss = { createPlan = false }, onSave = { vm.createPlan(it); createPlan = false })
    renamePlan?.let { plan -> PlanNameDialog("Переименовать план", plan.name, onDismiss = { renamePlan = null }, onSave = { vm.renamePlan(plan.id, it); renamePlan = null }) }
    deletePlan?.let { plan ->
        AlertDialog(onDismissRequest = { deletePlan = null }, title = { Text("Удалить «${plan.name}»?") }, text = { Text("Завершённая история останется без изменений.") }, confirmButton = { TextButton(onClick = { vm.deletePlan(plan.id); deletePlan = null }) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { deletePlan = null }) { Text("Назад") } })
    }
    editExercise?.let { (_, exercise) -> ExercisePlanDialog(exercise, state.settings.weightUnit, onDismiss = { editExercise = null }, onSave = { vm.updateExercise(it); editExercise = null }) }
    removeExercise?.let { (plan, exercise) ->
        AlertDialog(onDismissRequest = { removeExercise = null }, title = { Text("Убрать упражнение?") }, text = { Text("«${exercise.name}» будет удалено из плана «${plan.name}». Завершённая история не изменится.") }, confirmButton = { TextButton(onClick = { vm.deleteExercise(exercise.id); removeExercise = null }) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { removeExercise = null }) { Text("Назад") } })
    }
}

@Composable
private fun PlanNameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(title, initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true) }, confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Сохранить") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Назад") } })
}

@Composable
private fun ExercisePlanDialog(exercise: PlanExercise, unit: WeightUnit, onDismiss: () -> Unit, onSave: (PlanExercise) -> Unit) {
    var name by remember(exercise.id) { mutableStateOf(exercise.name) }
    var sets by remember(exercise.id) { mutableStateOf(exercise.plannedSets.toString()) }
    var weight by remember(exercise.id, exercise.targetWeight, unit) { mutableStateOf(displayWeight(exercise.targetWeight, unit)) }
    var reps by remember(exercise.id) { mutableStateOf(exercise.targetReps) }
    var rest by remember(exercise.id) { mutableStateOf(exercise.restSeconds.toString()) }
    var tracksLoad by remember(exercise.id) { mutableStateOf(exercise.tracksLoad) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Параметры упражнения") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Упражнение") }, singleLine = true)
            OutlinedTextField(sets, { sets = it.filter(Char::isDigit).take(2) }, label = { Text("Подходов в плане") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(weight, { weight = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(8) }, label = { Text("Целевой вес, ${unit.label()}") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(reps, { reps = it.filter(Char::isDigit).take(4) }, label = { Text("Целевые повторы") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(rest, { rest = it.filter(Char::isDigit).take(3) }, label = { Text("Отдых, секунд") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(tracksLoad, { tracksLoad = it })
                Text("Учитывать вес в силовой аналитике")
            }
        }
    }, confirmButton = { TextButton(onClick = { onSave(exercise.copy(name = name, plannedSets = sets.toIntOrNull() ?: 3, targetWeight = canonicalWeight(weight, unit), targetReps = reps, restSeconds = rest.toIntOrNull() ?: 90, tracksLoad = tracksLoad)) }, enabled = name.isNotBlank()) { Text("Сохранить") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Назад") } })
}

@Composable
private fun SettingsScreen(state: DiarySnapshot, vm: DiaryViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var showCreateBackup by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var pendingPassword by remember { mutableStateOf<String?>(null) }
    var encryptedBackup by remember { mutableStateOf<ByteArray?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    var askRestorePassword by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.reptrack.backup")) { uri ->
        val password = pendingPassword
        pendingPassword = null
        if (uri != null && password != null) vm.createEncryptedBackup(password) { bytes ->
            scope.launch(Dispatchers.IO) {
                val result = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Не удалось открыть файл для записи") }
                withContext(Dispatchers.Main) { status = if (result.isSuccess) "Зашифрованная копия сохранена." else result.exceptionOrNull()?.message ?: "Не удалось сохранить копию" }
            }
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val result = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readLimited(maxBackupBytes + 64) } ?: error("Не удалось открыть файл") }
            withContext(Dispatchers.Main) { result.onSuccess { encryptedBackup = it; askRestorePassword = true }.onFailure { status = it.message ?: "Не удалось прочитать копию" } }
        }
    }
    val savePlans = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportPlans { bytes ->
            scope.launch(Dispatchers.IO) {
                val result = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Не удалось открыть файл для записи") }
                withContext(Dispatchers.Main) { status = if (result.isSuccess) "Планы экспортированы." else result.exceptionOrNull()?.message ?: "Не удалось сохранить планы" }
            }
        }
    }
    val openPlans = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val result = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readLimited(maxBackupBytes) } ?: error("Не удалось открыть файл") }
            withContext(Dispatchers.Main) { result.onSuccess { vm.importPlans(it) { error -> status = error ?: "Планы импортированы как новые шаблоны." } }.onFailure { status = it.message ?: "Не удалось прочитать планы" } }
        }
    }

    ScreenColumn(modifier) {
        ScreenTitle("Настройки", "Локальные параметры приложения")
        Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Единицы веса", color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.settings.weightUnit == WeightUnit.KG, onClick = { vm.setWeightUnit(WeightUnit.KG) }, label = { Text("Килограммы") })
                    FilterChip(selected = state.settings.weightUnit == WeightUnit.LB, onClick = { vm.setWeightUnit(WeightUnit.LB) }, label = { Text("Фунты") })
                }
                Text("Вес в истории хранится в единой базе и только пересчитывается при отображении.", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Цветовая схема", color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
                DiaryTheme.entries.forEach { theme ->
                    FilterChip(selected = state.settings.theme == theme, onClick = { vm.setTheme(theme) }, label = { Text(theme.title()) })
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Таймер отдыха", color = LocalPalette.current.text, fontWeight = FontWeight.Bold); Text("После отметки подхода", color = LocalPalette.current.muted) }
                Switch(checked = state.settings.restTimerEnabled, onCheckedChange = { vm.setRestTimerEnabled(it) })
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Файлы и резервные копии", color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
                Text("Дневник хранится на этом устройстве и работает без сети. Резервная копия зашифрована паролем.", color = LocalPalette.current.muted)
                Button(onClick = { showCreateBackup = true }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить зашифрованную копию") }
                OutlinedButton(onClick = { openBackup.launch(arrayOf("application/vnd.reptrack.backup", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Восстановить резервную копию") }
                OutlinedButton(onClick = { savePlans.launch("RepTrack-планы.json") }, enabled = state.plans.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Экспортировать все планы") }
                OutlinedButton(onClick = { openPlans.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Импортировать планы") }
                if (status.isNotBlank()) Text(status, color = LocalPalette.current.secondary)
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showCreateBackup) AlertDialog(
        onDismissRequest = { showCreateBackup = false; newPassword = ""; confirmPassword = "" },
        title = { Text("Пароль резервной копии") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Пароль не сохраняется в файле. Запомни его: восстановить копию без него нельзя.", color = LocalPalette.current.muted)
            OutlinedTextField(newPassword, { newPassword = it }, label = { Text("Пароль, минимум 8 символов") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            OutlinedTextField(confirmPassword, { confirmPassword = it }, label = { Text("Повторить пароль") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { pendingPassword = newPassword; showCreateBackup = false; saveBackup.launch("RepTrack-${System.currentTimeMillis()}.rpbk"); newPassword = ""; confirmPassword = "" }, enabled = newPassword.length >= 8 && newPassword == confirmPassword) { Text("Продолжить") } },
        dismissButton = { TextButton(onClick = { showCreateBackup = false; newPassword = ""; confirmPassword = "" }) { Text("Назад") } },
    )

    if (askRestorePassword) AlertDialog(
        onDismissRequest = { askRestorePassword = false; restorePassword = ""; encryptedBackup = null },
        title = { Text("Восстановление копии") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Введите пароль, заданный при создании копии.", color = LocalPalette.current.muted)
            OutlinedTextField(restorePassword, { restorePassword = it }, label = { Text("Пароль") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { askRestorePassword = false; confirmRestore = true }, enabled = restorePassword.isNotEmpty()) { Text("Далее") } },
        dismissButton = { TextButton(onClick = { askRestorePassword = false; restorePassword = ""; encryptedBackup = null }) { Text("Назад") } },
    )

    if (confirmRestore) AlertDialog(
        onDismissRequest = { confirmRestore = false; restorePassword = ""; encryptedBackup = null },
        title = { Text("Заменить текущие данные?") },
        text = { Text("Планы, тренировки и настройки на этом устройстве будут заменены данными из копии.") },
        confirmButton = { TextButton(onClick = {
            val bytes = encryptedBackup
            if (bytes != null) vm.restoreEncryptedBackup(bytes, restorePassword) { error -> status = error ?: "Резервная копия восстановлена." }
            confirmRestore = false; restorePassword = ""; encryptedBackup = null
        }) { Text("Восстановить") } },
        dismissButton = { TextButton(onClick = { confirmRestore = false; restorePassword = ""; encryptedBackup = null }) { Text("Отмена") } },
    )
}

private fun java.io.InputStream.readLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= limit) { "Файл слишком большой" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

@Composable
private fun HistoryScreen(history: List<TrainingSession>, unit: WeightUnit, targetId: String?, clearTarget: () -> Unit, modifier: Modifier) {
    ScreenColumn(modifier) {
        ScreenTitle("История", "Завершённые тренировки")
        if (history.isEmpty()) Text("История появится после завершения тренировки.", color = LocalPalette.current.muted)
        targetId?.let { id ->
            history.firstOrNull { it.id == id }?.let { session ->
                Text("Тренировка из графика", color = LocalPalette.current.secondary, fontWeight = FontWeight.Bold)
                SessionHistoryCard(session, unit)
                TextButton(onClick = clearTarget, modifier = Modifier.heightIn(min = 48.dp)) { Text("Показать всю историю") }
            }
        }
        history.filter { it.id != targetId }.forEach { session -> SessionHistoryCard(session, unit) }
    }
}

@Composable
private fun SessionHistoryCard(session: TrainingSession, unit: WeightUnit) {
            Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(session.planName, fontWeight = FontWeight.Bold, color = LocalPalette.current.text); Text("Макс. вес ${session.maxWeightKg?.let { formatWeight(it, unit) } ?: "—"}", color = LocalPalette.current.secondary) }
                    val duration = ((session.endedAt ?: session.startedAt) - session.startedAt).coerceAtLeast(0) / 60000L
                    Text("${date(session.startedAt)} · ${duration} мин · ${session.exercises.size} упражнений · ${session.completedSets} рабочих подходов", color = LocalPalette.current.muted)
                    if (session.comment.isNotBlank()) Text(session.comment, color = LocalPalette.current.secondary)
                    session.exercises.forEach { exercise ->
                        val sets = exercise.sets.filter { it.done || it.skipped }.joinToString(" · ") { if (it.skipped) "пропуск" else "${displayWeight(it.weight, unit).ifBlank { "—" }}×${it.reps.ifBlank { "—" }}" }
                        Text("${exercise.name}: ${sets.ifBlank { "нет выполненных подходов" }}", color = LocalPalette.current.text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
}

@Composable
private fun ProgressScreen(state: DiarySnapshot, modifier: Modifier, openSession: (String) -> Unit) {
    var period by remember { mutableStateOf("Месяц") }
    var metric by remember { mutableStateOf(ProgressMetric.MAX_WEIGHT) }
    var chosenExercise by remember { mutableStateOf<String?>(null) }
    var showExercises by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val days = when (period) { "7 дней" -> 7; "Месяц" -> 30; "3 месяца" -> 90; else -> null }
    val from = days?.let { now - it * 86_400_000L }
    val exerciseNames = state.history.flatMap { it.exercises }.filter { it.tracksLoad }.map { it.name }.distinct().sorted()
    val exercise = chosenExercise?.takeIf { it in exerciseNames } ?: exerciseNames.firstOrNull()
    val metrics = calculateProgress(state.history, exercise, from)
    val points = exercise?.let { calculateProgressSeries(state.history, it, from, metric = metric) }.orEmpty()
    val previous = if (days == null) null else calculateProgress(state.history, exercise, now - 2 * days * 86_400_000L, now - days * 86_400_000L)
    ScreenColumn(modifier) {
        ScreenTitle("Прогресс", "Динамика по завершённым рабочим подходам")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("7 дней", "Месяц", "3 месяца", "Всё время").forEach { value ->
                FilterChip(selected = period == value, onClick = { period = value }, label = { Text(value) })
            }
        }
        Box {
            OutlinedButton(onClick = { showExercises = true }, enabled = exerciseNames.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(exercise ?: "Упражнение") }
            DropdownMenu(expanded = showExercises, onDismissRequest = { showExercises = false }) {
                exerciseNames.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { chosenExercise = name; showExercises = false }) }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${metrics.sessions} тренировок · ${metrics.completedWorkingSets} рабочих подходов", color = LocalPalette.current.muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Макс. вес", metrics.maxWeightKg?.let { formatWeight(it, state.settings.weightUnit) } ?: "—")
                    Stat("Повторы", metrics.maxReps?.toString() ?: "—")
                    Stat("Рабочие подходы", metrics.completedWorkingSets.toString())
                }
                Text("e1RM по Эпли · ${metrics.estimatedOneRepMaxKg?.let { formatWeight(it, state.settings.weightUnit) } ?: "—"}", color = LocalPalette.current.secondary, fontWeight = FontWeight.SemiBold)
                Text("Оценка только для рабочих подходов на 1–10 повторений; не измеренный максимум.", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(ProgressMetric.MAX_WEIGHT, ProgressMetric.MAX_REPS, ProgressMetric.ESTIMATED_ONE_REP_MAX).forEach { option ->
                        FilterChip(selected = metric == option, onClick = { metric = option }, label = { Text(option.title()) })
                    }
                }
                if (exercise != null) TrendGraph(state.history, exercise, from, metric, state.settings.weightUnit)
                points.takeLast(5).forEach { point ->
                    OutlinedButton(onClick = { openSession(point.sessionId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Открыть тренировку · ${date(point.timestamp)} · ${metric.value(point.value, state.settings.weightUnit)}")
                    }
                }
                if (previous != null) {
                    val before = previous.maxWeightKg
                    val current = metrics.maxWeightKg
                    val change = if (before == null || before <= 0.0 || current == null) null else (current - before) / before * 100
                    Text("Максимальный вес к предыдущему периоду: ${change?.let { (if (it > 0) "+" else "") + String.format(Locale.US, "%.1f%%", it) } ?: "нет базы для сравнения"}", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Личные рекорды · вес", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LocalPalette.current.text)
                if (state.bestByExercise.isEmpty()) Text("Пока нет завершённых рабочих подходов с весом.", color = LocalPalette.current.muted)
                else state.bestByExercise.toList().sortedByDescending { it.second }.forEach { (name, weight) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(name, color = LocalPalette.current.text); Text(formatWeight(weight, state.settings.weightUnit), color = LocalPalette.current.secondary, fontWeight = FontWeight.Bold) } }
            }
        }
        Text("Вес считается по завершённым рабочим подходам. Разминка, пропуски и незавершённые сессии не учитываются.", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TrendGraph(history: List<TrainingSession>, exerciseName: String, from: Long?, metric: ProgressMetric, unit: WeightUnit) {
    val colors = LocalPalette.current
    val points = calculateProgressSeries(history, exerciseName, from, metric = metric)
    if (points.isEmpty()) { Text("Нет завершённых рабочих подходов для графика.", color = LocalPalette.current.muted); return }
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val max = (points.maxOf { it.value } * 1.1).coerceAtLeast(1.0)
        val dx = if (points.size <= 1) 0f else size.width / (points.size - 1)
        val coords = points.mapIndexed { index, point -> androidx.compose.ui.geometry.Offset(index * dx, size.height - (point.value / max * size.height).toFloat()) }
        coords.zipWithNext().forEach { (a, b) -> drawLine(colors.secondary, a, b, strokeWidth = 4f) }
        coords.forEach { drawCircle(colors.accent, radius = 6f, center = it) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        points.takeLast(5).map { "${SimpleDateFormat("d.M", Locale.forLanguageTag("ru")).format(Date(it.timestamp))} · ${metric.value(it.value, unit)}" }.forEach { Text(it, color = LocalPalette.current.muted, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun Stat(label: String, value: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(label, color = LocalPalette.current.muted); Text(value, color = LocalPalette.current.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) } }

private fun date(epoch: Long): String = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru")).format(Date(epoch))
private fun kg(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
private fun WeightUnit.label() = if (this == WeightUnit.KG) "кг" else "lb"
private fun ProgressMetric.title() = when (this) {
    ProgressMetric.MAX_WEIGHT -> "Вес"
    ProgressMetric.MAX_REPS -> "Повторы"
    ProgressMetric.ESTIMATED_ONE_REP_MAX -> "e1RM"
}
private fun ProgressMetric.value(value: Double, unit: WeightUnit) = when (this) {
    ProgressMetric.MAX_WEIGHT, ProgressMetric.ESTIMATED_ONE_REP_MAX -> formatWeight(value, unit)
    ProgressMetric.MAX_REPS -> "${kg(value)} повт."
}
private fun displayWeight(storedKg: String, unit: WeightUnit): String {
    val value = storedKg.replace(',', '.').toDoubleOrNull() ?: return storedKg
    return kg(fromKilograms(value, unit))
}
private fun formatWeight(storedKg: Double, unit: WeightUnit) = "${kg(fromKilograms(storedKg, unit))} ${unit.label()}"
private fun canonicalWeight(displayValue: String, unit: WeightUnit) = parseWeightToKilograms(displayValue, unit)
