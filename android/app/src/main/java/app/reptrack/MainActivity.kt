package app.reptrack

import android.os.Bundle
import android.content.ClipData
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.core.content.FileProvider

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
private enum class NavigationIcon { HOME, GYM, PLAN, PROGRESS, HISTORY, SETTINGS }
private val tabIcons = listOf(NavigationIcon.HOME, NavigationIcon.GYM, NavigationIcon.PLAN, NavigationIcon.PROGRESS, NavigationIcon.HISTORY)

class MainActivity : ComponentActivity() {
    private val incomingFile = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingFile.value = intent.exchangeUri()
        setContent { val vm: DiaryViewModel = viewModel(); RepTrackApp(vm, incomingFile.value) { incomingFile.value = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingFile.value = intent.exchangeUri()
    }
}

@Suppress("DEPRECATION")
private fun Intent.exchangeUri(): Uri? = when (action) {
    Intent.ACTION_VIEW -> data
    Intent.ACTION_SEND -> getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
    else -> null
}?.takeIf { it.scheme == "content" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepTrackApp(vm: DiaryViewModel, incomingUri: Uri?, onIncomingConsumed: () -> Unit) {
    val state by vm.snapshot.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("Главная") }
    var confirm by remember { mutableStateOf<String?>(null) }
    var historyTarget by remember { mutableStateOf<String?>(null) }
    var retryDeliverySessionId by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableLongStateOf(0L) }
    fun shareTelegramDelivery(bytes: ByteArray) {
        scope.launch(Dispatchers.IO) {
            val result = runCatching { writeExchangeFile(context, bytes, "RepTrack-${System.currentTimeMillis()}.rptdelivery", maxTelegramDeliveryBytes) }
            withContext(Dispatchers.Main) {
                result.onSuccess { shareExchangeUri(context, it, "application/vnd.reptrack.delivery") }
                    .onFailure { Toast.makeText(context, it.message ?: "Не удалось подготовить отчёт", Toast.LENGTH_LONG).show() }
            }
        }
    }
    LaunchedEffect(incomingUri) { if (incomingUri != null) tab = "Настройки" }
    LaunchedEffect(state.activeSession?.id) { while (state.activeSession != null) { delay(1000); tick++ } }

    val palette = paletteFor(state.settings.theme)
    val showBottomNavigation = tab != "Зал" || state.activeSession == null
    CompositionLocalProvider(LocalPalette provides palette) {
    MaterialTheme(colorScheme = palette.colorScheme()) {
        Scaffold(
            containerColor = LocalPalette.current.background,
            topBar = { TopAppBar(title = { Row {
                Text("Rep", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
                Text("Track", color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
            } }, actions = {
                TextButton(onClick = { tab = "Люди" }) { Text(state.activeProfile?.name ?: "Профиль", color = LocalPalette.current.secondary) }
                IconButton(onClick = { tab = "Настройки" }, modifier = Modifier.semantics { contentDescription = "Настройки" }) { NavigationGlyph(NavigationIcon.SETTINGS) }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = LocalPalette.current.background, titleContentColor = LocalPalette.current.text)) },
            bottomBar = {
                if (showBottomNavigation) NavigationBar(containerColor = LocalPalette.current.surface) {
                    tabs.forEachIndexed { index, item ->
                        NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { NavigationGlyph(tabIcons[index]) }, label = { Text(item) })
                    }
                }
            },
        ) { padding ->
            if (state.loading) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else when (tab) {
                "Зал" -> GymScreen(state, vm, Modifier.padding(padding), tick) { confirm = it }
                "План" -> PlanScreen(state, vm, Modifier.padding(padding))
                "Люди" -> CoachScreen(
                    state,
                    vm,
                    Modifier.padding(padding),
                    showDemoProfile = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0,
                    openDemoProfile = { vm.createDemoProfile(); tab = "Главная" },
                    openSettings = { tab = "Настройки" },
                )
                "История" -> HistoryScreen(state.history, state.settings.weightUnit, historyTarget, { historyTarget = null }, Modifier.padding(padding), state.activeProfile?.kind == ProfileKind.PERSONAL && state.trustedLinks.any { it.localProfileId == state.activeProfileId }) { retryDeliverySessionId = it }
                "Прогресс" -> ProgressScreen(state, Modifier.padding(padding)) { id -> historyTarget = id; tab = "История" }
                "Настройки" -> SettingsScreen(state, vm, Modifier.padding(padding), incomingUri, onIncomingConsumed)
                else -> HomeScreen(state, vm, Modifier.padding(padding)) { tab = it }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        }
    }

    confirm?.let { action ->
        val session = state.activeSession
        val resultLinks = if (action == "finish" && session != null && session.ownerId == personalProfileId) state.trustedLinks.filter { it.localProfileId == session.ownerId } else emptyList()
        var selectedResultLinkId by remember(action, session?.id) { mutableStateOf(resultLinks.firstOrNull()?.id) }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (action == "finish") "Завершить тренировку?" else "Отменить тренировку?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (action == "finish") "Сессия попадёт в историю. Повторно завершить её нельзя." else "Сессия будет помечена отменённой и не попадёт в аналитику.")
                if (resultLinks.isNotEmpty()) {
                    Text("Можно сразу передать тренеру PDF и отдельный зашифрованный файл для импорта.", color = LocalPalette.current.muted)
                    if (resultLinks.size > 1) resultLinks.forEach { link ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedResultLinkId == link.id, onClick = { selectedResultLinkId = link.id })
                            Text("${link.fingerprint} · ${link.remoteDeviceId.take(8)}", color = LocalPalette.current.text)
                        }
                    }
                    Text("PDF будет виден Telegram и боту; файл импорта остаётся зашифрованным.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            } },
            confirmButton = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { session?.let { if (action == "finish") vm.finish(it.id) else vm.cancel(it.id) }; confirm = null }, modifier = Modifier.fillMaxWidth()) { Text(if (action == "finish") "Только завершить" else "Отменить сессию") }
                if (resultLinks.isNotEmpty()) OutlinedButton(onClick = {
                    val linkId = selectedResultLinkId
                    if (linkId != null && session != null) vm.finishAndCreateTelegramDelivery(linkId, session.id) { file, error ->
                        if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        else file?.let(::shareTelegramDelivery)
                    }
                    confirm = null
                }, enabled = selectedResultLinkId != null, modifier = Modifier.fillMaxWidth()) { Text("Завершить и отправить тренеру") }
            } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Назад") } },
        )
    }
    retryDeliverySessionId?.let { sessionId ->
        val links = state.trustedLinks.filter { it.localProfileId == personalProfileId }
        var selectedLinkId by remember(sessionId) { mutableStateOf(links.firstOrNull()?.id) }
        AlertDialog(
            onDismissRequest = { retryDeliverySessionId = null },
            title = { Text("Повторно подготовить отчёт") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Будет создан новый пакет для завершённой тренировки. Повторный импорт распознает её по ID события.", color = LocalPalette.current.muted)
                Text("Читаемый PDF будет доступен Telegram и боту.", color = MaterialTheme.colorScheme.error)
                if (links.isEmpty()) Text("Нет активной доверенной связи с тренером.", color = LocalPalette.current.muted)
                links.forEach { link -> Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedLinkId == link.id, onClick = { selectedLinkId = link.id })
                    Text("${link.fingerprint} · ${link.remoteDeviceId.take(8)}", color = LocalPalette.current.text)
                } }
            } },
            confirmButton = { Button(enabled = selectedLinkId != null, onClick = {
                selectedLinkId?.let { linkId -> vm.createTelegramDelivery(linkId, sessionId) { bytes, error ->
                    if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show() else bytes?.let(::shareTelegramDelivery)
                } }
                retryDeliverySessionId = null
            }) { Text("Подготовить и передать") } },
            dismissButton = { TextButton(onClick = { retryDeliverySessionId = null }) { Text("Отмена") } },
        )
    }
    }
}

@Composable
private fun NavigationGlyph(icon: NavigationIcon) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(w * x1, h * y1),
            end = androidx.compose.ui.geometry.Offset(w * x2, h * y2),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        when (icon) {
            NavigationIcon.HOME -> {
                line(.14f, .48f, .5f, .14f); line(.5f, .14f, .86f, .48f)
                line(.25f, .44f, .25f, .86f); line(.75f, .44f, .75f, .86f); line(.25f, .86f, .75f, .86f)
                line(.45f, .86f, .45f, .62f); line(.55f, .86f, .55f, .62f)
            }
            NavigationIcon.GYM -> {
                line(.25f, .5f, .75f, .5f)
                line(.18f, .3f, .18f, .7f); line(.3f, .34f, .3f, .66f)
                line(.7f, .34f, .7f, .66f); line(.82f, .3f, .82f, .7f)
                line(.18f, .5f, .3f, .5f); line(.7f, .5f, .82f, .5f)
            }
            NavigationIcon.PLAN -> {
                line(.16f, .28f, .84f, .28f); line(.16f, .3f, .16f, .82f); line(.84f, .3f, .84f, .82f)
                line(.16f, .82f, .84f, .82f); line(.32f, .14f, .32f, .38f); line(.68f, .14f, .68f, .38f)
                line(.32f, .5f, .32f, .5f); line(.5f, .5f, .5f, .5f); line(.68f, .5f, .68f, .5f)
                line(.32f, .67f, .32f, .67f); line(.5f, .67f, .5f, .67f); line(.68f, .67f, .68f, .67f)
            }
            NavigationIcon.PROGRESS -> {
                line(.16f, .82f, .16f, .58f); line(.38f, .82f, .38f, .43f); line(.6f, .82f, .6f, .27f); line(.82f, .82f, .82f, .14f)
                line(.12f, .86f, .88f, .86f)
            }
            NavigationIcon.HISTORY -> {
                drawCircle(color, radius = w * .34f, center = androidx.compose.ui.geometry.Offset(w * .5f, h * .52f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                line(.5f, .3f, .5f, .53f); line(.5f, .53f, .66f, .63f)
            }
            NavigationIcon.SETTINGS -> {
                drawCircle(color, radius = w * .25f, center = androidx.compose.ui.geometry.Offset(w * .5f, h * .5f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                repeat(8) { index ->
                    val angle = Math.PI / 4 * index
                    line(.5f + kotlin.math.cos(angle).toFloat() * .29f, .5f + kotlin.math.sin(angle).toFloat() * .29f, .5f + kotlin.math.cos(angle).toFloat() * .43f, .5f + kotlin.math.sin(angle).toFloat() * .43f)
                }
            }
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
    val cycle = state.plans.filter { it.inCycle }
    val progressExercise = state.history.asSequence().flatMap { it.exercises.asSequence() }
        .filter { it.tracksLoad }
        .map { it.name }
        .distinct()
        .map { name -> name to calculateProgressSeries(state.history, name, System.currentTimeMillis() - 30 * 86_400_000L).size }
        .filter { it.second > 0 }
        .maxByOrNull { it.second }
        ?.first
    ScreenColumn(modifier) {
        ScreenTitle("Главная", "Твой прогресс. Твой план.")
        Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (active != null) "ТРЕНИРОВКА В ПРОЦЕССЕ" else "СЛЕДУЮЩАЯ ТРЕНИРОВКА", color = LocalPalette.current.secondary, style = MaterialTheme.typography.labelLarge)
                Text(active?.planName ?: next?.name ?: "Создай план", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = LocalPalette.current.text)
                Text(if (active != null) "${active.completedSets} из ${active.totalSets} рабочих подходов" else "Цикл: ${state.plans.filter { it.inCycle }.joinToString(" → ") { it.name }}", color = LocalPalette.current.muted)
                Button(onClick = { vm.start(active?.planId ?: next?.id); navigate("Зал") }, modifier = Modifier.fillMaxWidth(), enabled = active != null || next != null) {
                    Text(if (active != null) "Продолжить тренировку" else "▶  Начать тренировку")
                }
                if (cycle.isNotEmpty()) {
                    Text("Цикл тренировок", color = LocalPalette.current.muted, style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cycle.forEach { plan ->
                            val selected = plan.id == next?.id
                            Surface(shape = CircleShape, color = if (selected) LocalPalette.current.accent else LocalPalette.current.background) {
                                Text(
                                    plan.name + if (selected) " ✓" else "",
                                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    color = if (selected) LocalPalette.current.background else LocalPalette.current.text,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
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
                if (progressExercise != null) {
                    Text("$progressExercise · рабочий вес", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                    TrendGraph(state.history, progressExercise, System.currentTimeMillis() - 30 * 86_400_000L, ProgressMetric.MAX_WEIGHT, state.settings.weightUnit, height = 76.dp, onPointClick = { navigate("Прогресс") })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CoachScreen(state: DiarySnapshot, vm: DiaryViewModel, modifier: Modifier, showDemoProfile: Boolean, openDemoProfile: () -> Unit, openSettings: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var sortByRecent by remember { mutableStateOf(false) }
    var addProfile by remember { mutableStateOf(false) }
    var renameProfile by remember { mutableStateOf<DiaryProfile?>(null) }
    var assignToProfile by remember { mutableStateOf<DiaryProfile?>(null) }
    var selectedPlanId by remember { mutableStateOf<String?>(null) }
    val visibleProfiles = state.profiles.filter { it.name.contains(search.trim(), ignoreCase = true) }
    val orderedProfiles = if (sortByRecent) visibleProfiles.sortedByDescending { it.lastImportedAt ?: it.lastCompletedAt ?: it.createdAt }
        else visibleProfiles.sortedBy { it.name.lowercase(Locale.getDefault()) }

    ScreenColumn(modifier) {
        ScreenTitle("Подопечные", "Локальные дневники на этом устройстве")
        if (showDemoProfile) OutlinedButton(onClick = openDemoProfile, modifier = Modifier.fillMaxWidth()) {
            Text("Создать / открыть тестовый профиль")
        }
        if (showDemoProfile) OutlinedButton(onClick = { vm.createSourceReportsProfile() }, modifier = Modifier.fillMaxWidth()) {
            Text("Импортировать мои отчёты тренировок")
        }
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Поиск профиля") }, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !sortByRecent, onClick = { sortByRecent = false }, label = { Text("По имени") })
                FilterChip(selected = sortByRecent, onClick = { sortByRecent = true }, label = { Text("По активности") })
            }
            Button(onClick = { addProfile = true }) { Text("＋ Добавить") }
        }
        orderedProfiles.forEach { profile ->
            Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).background(LocalPalette.current.accent, CircleShape), contentAlignment = Alignment.Center) {
                            Text(profile.name.take(1).uppercase(Locale.getDefault()), color = LocalPalette.current.background, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LocalPalette.current.text)
                            Text(if (profile.kind == ProfileKind.PERSONAL) "Личный дневник" else "Подопечный", color = LocalPalette.current.muted)
                        }
                    }
                    Text("${profile.completedWorkouts} завершённых тренировок${profile.lastCompletedAt?.let { " · последняя ${date(it)}" } ?: ""}", color = LocalPalette.current.muted)
                    Text(profile.lastImportedAt?.let { "Последний импорт отчёта · ${date(it)}" } ?: if (profile.kind == ProfileKind.PERSONAL) "Личный профиль" else "Импортированных отчётов пока нет", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                    if (profile.kind == ProfileKind.TRAINEE) {
                        val linked = state.trustedLinks.any { it.localProfileId == profile.id }
                        Text(if (linked) "Устройство связано · файлы защищены" else "Доверенное устройство не подключено", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { vm.setActiveProfile(profile.id) { error -> if (error == null) openSettings() } }, modifier = Modifier.fillMaxWidth()) { Text("Связь и обмен файлами") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.activeProfileId == profile.id) Text("Выбран", color = LocalPalette.current.secondary, modifier = Modifier.padding(12.dp))
                        else OutlinedButton(onClick = { vm.setActiveProfile(profile.id) }) { Text("Открыть дневник") }
                        if (profile.kind == ProfileKind.TRAINEE) TextButton(onClick = { renameProfile = profile }) { Text("Переименовать") }
                    }
                    if (profile.kind == ProfileKind.TRAINEE) {
                        val assignments = state.assignments.filter { it.profileId == profile.id }
                        val unassignedPlans = state.trainerPlans.filterNot { source -> assignments.any { it.sourcePlanId == source.id } }
                        Text("Назначенные программы", fontWeight = FontWeight.SemiBold, color = LocalPalette.current.text)
                        if (assignments.isEmpty()) Text("Программ пока нет.", color = LocalPalette.current.muted)
                        assignments.forEach { assignment ->
                            Column(Modifier.fillMaxWidth().padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${assignment.planName} · версия ${assignment.version}", color = LocalPalette.current.text)
                                if (assignment.assignedPlanVersion > assignment.version) Text("Есть локальные изменения программы.", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                                if (assignment.sourceVersion > assignment.version && assignment.pendingPlan == null) {
                                    Text("Доступна версия ${assignment.sourceVersion}.", color = LocalPalette.current.secondary)
                                    if (state.activeProfileId == personalProfileId) TextButton(onClick = { vm.stageAssignmentUpdate(assignment.id) }) { Text("Подготовить обновление") }
                                    else TextButton(onClick = { vm.setActiveProfile(personalProfileId) }) { Text("Открыть личный профиль для обновления") }
                                }
                                assignment.pendingPlan?.let { incoming ->
                                    Text("Обновление до версии ${assignment.pendingVersion}", color = LocalPalette.current.secondary, fontWeight = FontWeight.SemiBold)
                                    if (assignment.pendingVersion != null && assignment.sourceVersion > assignment.pendingVersion) {
                                        Text("Конфликт версий: доступна более новая версия ${assignment.sourceVersion}.", color = MaterialTheme.colorScheme.error)
                                        if (!assignment.pendingAccepted && state.activeProfileId == personalProfileId) TextButton(onClick = { vm.stageAssignmentUpdate(assignment.id) }) { Text("Подготовить последнюю версию") }
                                        else if (!assignment.pendingAccepted) Text("Ожидается обновление от тренера.", color = LocalPalette.current.muted)
                                        else Text("Принятая версия применится после тренировки; затем можно проверить новую.", color = LocalPalette.current.muted)
                                    } else if (state.activeProfileId == profile.id) {
                                        val current = state.plans.firstOrNull { it.id == assignment.assignedPlanId }
                                        if (current != null) planDiffSummary(current, incoming).forEach { Text("• $it", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall) }
                                        if (assignment.pendingAccepted) Text("Принято. Программа применится после завершения активной тренировки.", color = LocalPalette.current.secondary)
                                        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { vm.acceptAssignmentUpdate(assignment.id) }) { Text("Принять") }
                                            TextButton(onClick = { vm.rejectAssignmentUpdate(assignment.id) }) { Text("Отклонить") }
                                        }
                                    } else TextButton(onClick = { vm.setActiveProfile(profile.id) }) { Text("Открыть для проверки") }
                                }
                            }
                        }
                        if (unassignedPlans.isEmpty()) Text("Все личные программы уже назначены этому профилю.", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { assignToProfile = profile; selectedPlanId = unassignedPlans.firstOrNull()?.id }, enabled = unassignedPlans.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Назначить программу") }
                    }
                }
            }
        }
        if (orderedProfiles.isEmpty()) Text("Профили не найдены.", color = LocalPalette.current.muted)
    }

    if (addProfile) PlanNameDialog("Новый подопечный", "", onDismiss = { addProfile = false }, onSave = { vm.createTraineeProfile(it); addProfile = false })
    renameProfile?.let { profile -> PlanNameDialog("Переименовать профиль", profile.name, onDismiss = { renameProfile = null }, onSave = { vm.renameProfile(profile.id, it); renameProfile = null }) }
    assignToProfile?.let { profile ->
        val assignedSourceIds = state.assignments.filter { it.profileId == profile.id }.mapTo(mutableSetOf()) { it.sourcePlanId }
        val availablePlans = state.trainerPlans.filterNot { it.id in assignedSourceIds }
        AlertDialog(
            onDismissRequest = { assignToProfile = null },
            title = { Text("Назначить программу · ${profile.name}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                availablePlans.forEach { plan ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedPlanId == plan.id, onClick = { selectedPlanId = plan.id })
                        Text(plan.name, color = LocalPalette.current.text)
                    }
                }
            } },
            confirmButton = { TextButton(onClick = { selectedPlanId?.let { vm.assignPlanToProfile(it, profile.id) }; assignToProfile = null }, enabled = selectedPlanId != null) { Text("Назначить") } },
            dismissButton = { TextButton(onClick = { assignToProfile = null }) { Text("Назад") } },
        )
    }
}

private fun planDiffSummary(current: TrainingPlan, incoming: TrainingPlan): List<String> = describePlanChanges(current, incoming)

@Composable
private fun GymScreen(state: DiarySnapshot, vm: DiaryViewModel, modifier: Modifier, tick: Long, confirm: (String) -> Unit) {
    val session = state.activeSession
    var replacementFor by remember { mutableStateOf<String?>(null) }
    var detailsFor by remember { mutableStateOf<String?>(null) }
    var showCatalog by remember { mutableStateOf(false) }
    var catalogQuery by remember { mutableStateOf("") }
    ScreenColumn(modifier) {
        ScreenTitle("Зал", "Отмечай фактические подходы")
        if (session == null) {
            Text("Выбери тренировку", style = MaterialTheme.typography.titleLarge, color = LocalPalette.current.text)
            OutlinedButton(onClick = { showCatalog = !showCatalog }, modifier = Modifier.fillMaxWidth()) { Text(if (showCatalog) "Скрыть каталог упражнений" else "Каталог упражнений") }
            if (showCatalog) {
                val allNames = (state.plans.flatMap { it.exercises }.map { it.name } + state.history.flatMap { it.exercises }.map { it.name }).distinct().sorted()
                val names = allNames.filter { it.contains(catalogQuery.trim(), ignoreCase = true) }
                OutlinedTextField(catalogQuery, { catalogQuery = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Поиск упражнения") }, singleLine = true)
                if (allNames.isEmpty()) Text("В выбранном профиле пока нет упражнений в планах или истории.", color = LocalPalette.current.muted)
                else if (names.isEmpty()) Text("Упражнения не найдены.", color = LocalPalette.current.muted)
                names.forEach { name -> OutlinedButton(onClick = { detailsFor = name }, modifier = Modifier.fillMaxWidth()) { Text("$name · история и рекорд") } }
            }
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
                var expanded by rememberSaveable(session.id, exercise.id) { mutableStateOf(exercise.position == 0) }
                Card(border = BorderStroke(1.dp, if (expanded) LocalPalette.current.accent else LocalPalette.current.muted.copy(alpha = 0.2f)), colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("${exercise.position + 1}. ${exercise.name} ${if (expanded) "⌃" else "⌄"}", fontWeight = FontWeight.SemiBold, color = LocalPalette.current.text) }
                            if (expanded) {
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
                        }
                        if (expanded) {
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
        }
        Spacer(Modifier.height(8.dp))
    }
    detailsFor?.let { name -> ExerciseDetailDialog(name, state, onDismiss = { detailsFor = null }) }
}

private data class ExerciseGuide(
    val equipment: String,
    val muscles: List<String>,
    val steps: List<String>,
    val sourceUrl: String,
)

private fun verifiedGuideFor(name: String): ExerciseGuide? {
    val key = name.lowercase(Locale.ROOT).replace('ё', 'е').replace(Regex("[^a-zа-я]+"), " ").trim()
    if (key !in setOf("жим ногами", "жим ногами в тренажере", "seated leg press", "leg press")) return null
    return ExerciseGuide(
        equipment = "Силовой тренажёр",
        muscles = listOf("Бёдра", "Ягодицы и таз"),
        steps = listOf(
            "Настрой сиденье так, чтобы стопы полностью стояли на платформе, а колени в исходном положении были примерно под прямым углом.",
            "Прижми спину и таз к спинке; сохраняй это положение во время движения.",
            "Плавно выжми платформу, не отрывая пятки; не переразгибай и не блокируй колени.",
            "Контролируемо верни платформу, не прижимая бёдра к корпусу.",
        ),
        sourceUrl = "https://www.acefitness.org/resources/everyone/exercise-library/154/seated-leg-press/",
    )
}

@Composable
private fun ExerciseDetailDialog(name: String, state: DiarySnapshot, onDismiss: () -> Unit) {
    var selectedTab by rememberSaveable(name) { mutableIntStateOf(0) }
    val guide = remember(name) { verifiedGuideFor(name) }
    val related = state.history.flatMap { session ->
        session.exercises.filter { it.name == name && !it.skipped && it.tracksLoad }.map { session to it }
    }.sortedByDescending { it.first.endedAt ?: it.first.startedAt }
    val workingSets = related.flatMap { it.second.sets }.filter { it.done && !it.skipped && it.kind == SetKind.WORK }
    val record = workingSets.mapNotNull { parseFiniteWeight(it.weight)?.takeIf { weight -> weight > 0.0 } }.maxOrNull()
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(12.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = LocalPalette.current.background,
            tonalElevation = 8.dp,
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LocalPalette.current.text)
                        Text(guide?.let { "${it.equipment} · проверенная справка" } ?: "История и рабочие веса", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Закрыть") }
                }
                TabRow(selectedTabIndex = selectedTab) {
                    listOf("Сводка", "Техника", "Мышцы").forEachIndexed { index, title ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                    }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (selectedTab) {
                        0 -> {
                            if (guide != null) ExerciseGuideIllustration()
                            else ExerciseMediaPlaceholder("Иллюстрация упражнения ещё не добавлена")
                            Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Личный рекорд", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = LocalPalette.current.text)
                                    Text(record?.let { formatWeight(it, state.settings.weightUnit) } ?: "Нет завершённых рабочих подходов", color = LocalPalette.current.secondary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    if (workingSets.isNotEmpty()) {
                                        HorizontalDivider()
                                        Text("Изменение рабочего веса", color = LocalPalette.current.text, fontWeight = FontWeight.SemiBold)
                                        TrendGraph(state.history, name, null, ProgressMetric.MAX_WEIGHT, state.settings.weightUnit, height = 130.dp)
                                    } else Text("График появится после завершённого рабочего подхода с весом.", color = LocalPalette.current.muted)
                                }
                            }
                            Text("Последние тренировки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = LocalPalette.current.text)
                            if (related.isEmpty()) Text("Пока нет записей по этому упражнению.", color = LocalPalette.current.muted)
                            related.take(5).forEach { (session, item) ->
                                val sets = item.sets.filter { it.done && !it.skipped && it.kind == SetKind.WORK }
                                    .joinToString(" · ") { "${displayWeight(it.weight, state.settings.weightUnit).ifBlank { "—" }} × ${it.reps.ifBlank { "—" }}" }
                                Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                        Text(date(session.endedAt ?: session.startedAt), color = LocalPalette.current.text, fontWeight = FontWeight.SemiBold)
                                        Text(sets.ifBlank { "Нет завершённых рабочих подходов" }, color = LocalPalette.current.muted)
                                    }
                                }
                            }
                        }
                        1 -> {
                            if (guide == null) {
                                ExerciseMediaPlaceholder("Техника для этого упражнения пока не проверена")
                                Text("Покажем инструкцию и видео после добавления проверенного материала в каталог.", color = LocalPalette.current.muted)
                            } else {
                                ExerciseGuideIllustration()
                                Text("Техника выполнения", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LocalPalette.current.text)
                                guide.steps.forEachIndexed { index, step ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                                        Surface(shape = CircleShape, color = LocalPalette.current.accent) {
                                            Text("${index + 1}", Modifier.padding(horizontal = 11.dp, vertical = 5.dp), color = LocalPalette.current.background, fontWeight = FontWeight.Bold)
                                        }
                                        Text(step, Modifier.weight(1f), color = LocalPalette.current.text)
                                    }
                                }
                                Text("Справка: ACE Fitness · seated leg press", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(guide.sourceUrl))) } }) { Text("Открыть источник ACE Fitness") }
                            }
                        }
                        else -> {
                            if (guide == null) {
                                ExerciseMediaPlaceholder("Карта мышц не добавлена")
                                Text("Не определяем группы мышц по одному только названию упражнения.", color = LocalPalette.current.muted)
                            } else {
                                MuscleMapIllustration()
                                Text("Основные группы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LocalPalette.current.text)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    guide.muscles.forEach { group ->
                                        Surface(shape = CircleShape, color = LocalPalette.current.surface) {
                                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box(Modifier.size(8.dp).background(LocalPalette.current.accent, CircleShape))
                                                Text(group, color = LocalPalette.current.text, style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }
                                }
                                Text("Группы указаны в справочнике ACE Fitness для seated leg press.", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(guide.sourceUrl))) } }) { Text("Открыть источник ACE Fitness") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseMediaPlaceholder(label: String) {
    val iconColor = LocalPalette.current.secondary
    Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
        Column(Modifier.fillMaxWidth().height(156.dp).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Canvas(Modifier.size(48.dp)) {
                val color = iconColor
                drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * .13f, size.height * .13f), size = androidx.compose.ui.geometry.Size(size.width * .74f, size.height * .74f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                drawCircle(color, radius = size.width * .08f, center = androidx.compose.ui.geometry.Offset(size.width * .37f, size.height * .38f))
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .24f, size.height * .72f), androidx.compose.ui.geometry.Offset(size.width * .45f, size.height * .51f), 2.dp.toPx())
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .45f, size.height * .51f), androidx.compose.ui.geometry.Offset(size.width * .73f, size.height * .71f), 2.dp.toPx())
            }
            Spacer(Modifier.height(10.dp))
            Text(label, color = LocalPalette.current.muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ExerciseGuideIllustration() {
    val accent = LocalPalette.current.accent
    val muted = LocalPalette.current.muted
    Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Схема движения · жим ногами", color = LocalPalette.current.text, fontWeight = FontWeight.SemiBold)
            Canvas(Modifier.fillMaxWidth().height(156.dp)) {
                val thick = 5.dp.toPx()
                fun line(a: Pair<Float, Float>, b: Pair<Float, Float>, color: Color = muted, width: Float = thick) {
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width * a.first, size.height * a.second), androidx.compose.ui.geometry.Offset(size.width * b.first, size.height * b.second), width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
                // Equipment outline and a simplified, non-anatomical movement path.
                line(.16f to .84f, .85f to .84f)
                line(.22f to .78f, .39f to .34f)
                line(.39f to .34f, .48f to .66f)
                line(.45f to .62f, .66f to .62f)
                line(.44f to .67f, .68f to .67f)
                line(.49f to .62f, .60f to .48f, accent, thick * 1.4f)
                line(.60f to .48f, .69f to .37f, accent, thick * 1.4f)
                line(.63f to .47f, .77f to .24f, accent, thick * 1.4f)
                line(.77f to .24f, .88f to .24f, accent, thick)
                line(.78f to .17f, .78f to .32f, accent, thick)
                line(.85f to .17f, .85f to .32f, accent, thick)
                line(.72f to .42f, .82f to .34f, accent, thick * .8f)
                drawCircle(muted, radius = 9.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * .46f, size.height * .52f))
            }
            Text("Тренажёр и направление выжимания", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MuscleMapIllustration() {
    val accent = LocalPalette.current.accent
    val muted = LocalPalette.current.muted
    Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Зоны, отмеченные в справочнике", color = LocalPalette.current.text, fontWeight = FontWeight.SemiBold)
            Canvas(Modifier.fillMaxWidth().height(188.dp)) {
                val centerX = size.width / 2f
                val scale = size.height
                val thin = 3.dp.toPx()
                drawCircle(muted.copy(alpha = .6f), radius = scale * .065f, center = androidx.compose.ui.geometry.Offset(centerX, scale * .15f), style = androidx.compose.ui.graphics.drawscope.Stroke(thin))
                fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color = muted, width: Float = thin) = drawLine(color, androidx.compose.ui.geometry.Offset(centerX + scale * x1, scale * y1), androidx.compose.ui.geometry.Offset(centerX + scale * x2, scale * y2), width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                line(-.1f, .26f, .1f, .26f); line(-.1f, .26f, -.18f, .47f); line(.1f, .26f, .18f, .47f)
                line(-.1f, .26f, -.09f, .5f); line(.1f, .26f, .09f, .5f)
                line(-.18f, .47f, .18f, .47f, accent, 9.dp.toPx())
                line(-.09f, .5f, -.11f, .76f, accent, 8.dp.toPx()); line(.09f, .5f, .11f, .76f, accent, 8.dp.toPx())
                line(-.11f, .76f, -.12f, .94f); line(.11f, .76f, .12f, .94f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(10.dp), shape = CircleShape, color = accent) {}
                Text("Бёдра · ягодицы и таз", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
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
            Checkbox(checked = set.done, onCheckedChange = { vm.toggleSet(set) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Отметить подход ${index + 1}" })
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
    val cyclePositions = state.plans.filter { it.inCycle }.mapIndexed { index, plan -> plan.id to index + 1 }.toMap()
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
                        Column(Modifier.weight(1f)) { Text(plan.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LocalPalette.current.text); Text(if (plan.inCycle) "${cyclePositions[plan.id]} · в цикле" else "вне цикла", color = LocalPalette.current.muted) }
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
private fun SettingsScreen(state: DiarySnapshot, vm: DiaryViewModel, modifier: Modifier, incomingUri: Uri?, onIncomingConsumed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var exchangePreview by remember { mutableStateOf<ExchangePreview?>(null) }
    var exchangeFile by remember { mutableStateOf<ByteArray?>(null) }
    var selectedResultsLink by remember { mutableStateOf<TrustedLinkSummary?>(null) }
    var selectedSessions by remember { mutableStateOf(setOf<String>()) }
    var selectedProgramLink by remember { mutableStateOf<TrustedLinkSummary?>(null) }
    var revokeTarget by remember { mutableStateOf<TrustedLinkSummary?>(null) }
    var exchangeError by remember { mutableStateOf<String?>(null) }
    var showCreateBackup by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var pendingPassword by remember { mutableStateOf<String?>(null) }
    var encryptedBackup by remember { mutableStateOf<ByteArray?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    var askRestorePassword by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    fun shareOutput(bytes: ByteArray, filename: String, mime: String) {
        scope.launch(Dispatchers.IO) {
            val result = runCatching { writeExchangeFile(context, bytes, filename) }
            withContext(Dispatchers.Main) {
                result.onSuccess { uri -> runCatching { shareExchangeUri(context, uri, mime) }
                    .onSuccess { status = "Файл передан в меню отправки. Это не подтверждает доставку или импорт." }
                    .onFailure { exchangeError = it.message ?: "Не удалось открыть меню отправки" } }
                    .onFailure { exchangeError = it.message ?: "Не удалось подготовить файл" }
            }
        }
    }

    fun handleExchangeFile(bytes: ByteArray) {
        val pairing = runCatching { ExchangeCrypto.decodePairing(bytes) }.getOrNull()
        when {
            pairing?.kind == "request" -> vm.respondToPairingInvite(bytes, personalProfileId) { response ->
                shareOutput(response.file, "RepTrack-${System.currentTimeMillis()}.rptinvite", "application/vnd.reptrack.invite")
                status = "Ответ создан. Сверьте отпечаток ${response.fingerprint} по независимому каналу перед подтверждением связи."
            }
            pairing?.kind == "response" -> vm.processPairingResponse(bytes) { pending ->
                status = "Отпечаток связи: ${pending.fingerprint}. Сверь его с собеседником по независимому каналу, затем подтверди связь ниже."
            }
            else -> {
                exchangeFile = bytes
                vm.previewExchange(bytes) { exchangePreview = it }
            }
        }
    }

    LaunchedEffect(incomingUri) {
        if (incomingUri != null) {
            val result = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(incomingUri)?.use { it.readLimited(maxExchangeBytes) } ?: error("Не удалось открыть файл") }
            }
            result.onSuccess(::handleExchangeFile).onFailure { exchangeError = it.message ?: "Не удалось прочитать файл" }
            onIncomingConsumed()
        }
    }

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
    val openExchange = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val result = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readLimited(maxExchangeBytes) } ?: error("Не удалось открыть файл") }
            withContext(Dispatchers.Main) { result.onSuccess(::handleExchangeFile).onFailure { exchangeError = it.message ?: "Не удалось прочитать файл" } }
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
                Text("Защищённый обмен", color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
                Text("Устройства связываются через файл приглашения. Перед подтверждением сравните отпечаток по независимому каналу.", color = LocalPalette.current.muted)
                Text("Для пересылки через бота откройте его личный чат на обоих устройствах и отправьте туда файл регистрации каждой доверенной связи.", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                if (state.activeProfile?.kind == ProfileKind.TRAINEE) {
                    Button(onClick = { vm.createPairingInvitation(state.activeProfileId) { bytes -> shareOutput(bytes, "RepTrack-${System.currentTimeMillis()}.rptinvite", "application/vnd.reptrack.invite") } }, modifier = Modifier.fillMaxWidth()) { Text("Создать приглашение для подопечного") }
                }
                OutlinedButton(onClick = { openExchange.launch(arrayOf("application/vnd.reptrack.invite", "application/vnd.reptrack.result", "application/vnd.reptrack.program", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Открыть файл обмена") }

                val links = state.trustedLinks.filter { it.localProfileId == state.activeProfileId }
                if (links.isEmpty()) Text("Для выбранного профиля доверенных связей пока нет.", color = LocalPalette.current.muted)
                links.forEach { link ->
                    HorizontalDivider()
                    Text("Связь · ${link.fingerprint}", color = LocalPalette.current.secondary, fontWeight = FontWeight.SemiBold)
                    Text("Устройство ${link.remoteDeviceId.take(8)} · ID маршрута ${link.id.take(8)} · создана ${date(link.createdAt)}", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { vm.createTelegramRouteFile(link.id) { bytes -> shareOutput(bytes, "RepTrack-${link.id.take(8)}.rptrelay", "application/vnd.reptrack.telegram-route") } }, modifier = Modifier.fillMaxWidth()) { Text("Подключить эту связь к Telegram-боту") }
                    if (state.activeProfile?.kind == ProfileKind.PERSONAL) {
                        OutlinedButton(onClick = { selectedResultsLink = link; selectedSessions = emptySet() }, enabled = state.history.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Выбрать тренировки и отправить отчёт") }
                    }
                    if (state.activeProfile?.kind == ProfileKind.TRAINEE) {
                        OutlinedButton(onClick = { selectedProgramLink = link }, enabled = state.allPlans.any { it.ownerId == link.localProfileId }, modifier = Modifier.fillMaxWidth()) { Text("Отправить программу подопечному") }
                    }
                    TextButton(onClick = { revokeTarget = link }) { Text("Отозвать связь") }
                }

                state.pendingPairings.filter { it.localProfileId == state.activeProfileId }.forEach { pending ->
                    HorizontalDivider()
                    Text(if (pending.fingerprint == null) "Приглашение отправлено · ждём ответ" else "Сверь отпечаток и подтверди связь", color = LocalPalette.current.text, fontWeight = FontWeight.SemiBold)
                    pending.fingerprint?.let { Text(it, color = LocalPalette.current.secondary, style = MaterialTheme.typography.titleMedium) }
                    Text("Код ${pending.inviteId.take(8)} · действует до ${date(pending.expiresAt)}", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                    if (pending.fingerprint != null) Button(onClick = { vm.confirmPairing(pending.inviteId) { error -> status = error ?: "Связь подтверждена." } }, modifier = Modifier.fillMaxWidth()) { Text("Отпечаток проверен · связать") }
                }
                if (status.isNotBlank()) Text(status, color = LocalPalette.current.secondary)
                exchangeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Файлы и резервные копии", color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
                Text("Дневник хранится на этом устройстве и работает без сети. Резервная копия зашифрована паролем.", color = LocalPalette.current.muted)
                Button(onClick = { showCreateBackup = true }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить зашифрованную копию") }
                OutlinedButton(onClick = { openBackup.launch(arrayOf("application/vnd.reptrack.backup", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Восстановить резервную копию") }
                OutlinedButton(onClick = { savePlans.launch("RepTrack-планы.rptplan") }, enabled = state.plans.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Экспортировать все планы") }
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
        confirmButton = { TextButton(onClick = { pendingPassword = newPassword; showCreateBackup = false; saveBackup.launch("RepTrack-${System.currentTimeMillis()}.rptbackup"); newPassword = ""; confirmPassword = "" }, enabled = newPassword.length >= 8 && newPassword == confirmPassword) { Text("Продолжить") } },
        dismissButton = { TextButton(onClick = { showCreateBackup = false; newPassword = ""; confirmPassword = "" }) { Text("Назад") } },
    )

    selectedResultsLink?.let { link -> AlertDialog(
        onDismissRequest = { selectedResultsLink = null; selectedSessions = emptySet() },
        title = { Text("Выбери тренировки") },
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("В отчёт попадут только отмеченные завершённые тренировки.", color = LocalPalette.current.muted)
            state.history.forEach { session ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = session.id in selectedSessions, onCheckedChange = { checked -> selectedSessions = if (checked) selectedSessions + session.id else selectedSessions - session.id })
                    Column {
                        Text(session.planName, color = LocalPalette.current.text)
                        Text("${date(session.endedAt ?: session.startedAt)} · ${session.completedSets} рабочих подходов", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } },
        confirmButton = { TextButton(onClick = {
            vm.createResultPackage(link.id, selectedSessions) { bytes -> shareOutput(bytes, "RepTrack-${System.currentTimeMillis()}.rptresult", "application/vnd.reptrack.result") }
            selectedResultsLink = null; selectedSessions = emptySet()
        }, enabled = selectedSessions.isNotEmpty()) { Text("Создать отчёт") } },
        dismissButton = { TextButton(onClick = { selectedResultsLink = null; selectedSessions = emptySet() }) { Text("Назад") } },
    ) }

    selectedProgramLink?.let { link -> AlertDialog(
        onDismissRequest = { selectedProgramLink = null },
        title = { Text("Выбери программу") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Получатель увидит версию и изменения и сможет принять или отклонить обновление.", color = LocalPalette.current.muted)
            state.allPlans.filter { it.ownerId == link.localProfileId }.forEach { plan -> OutlinedButton(onClick = {
                vm.createProgramPackage(link.id, plan.id) { bytes -> shareOutput(bytes, "RepTrack-${System.currentTimeMillis()}.rptprogram", "application/vnd.reptrack.program") }
                selectedProgramLink = null
            }, modifier = Modifier.fillMaxWidth()) { Text("${plan.name} · v${plan.version} · ${if (plan.id in state.assignments.map { it.assignedPlanId }) "индивидуальная копия" else "личный план тренера"}") } }
        } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { selectedProgramLink = null }) { Text("Назад") } },
    ) }

    exchangePreview?.let { preview ->
        val bytes = exchangeFile
        AlertDialog(
            onDismissRequest = { exchangePreview = null; exchangeFile = null },
            title = { Text(if (preview.type == "result") "Импорт отчёта" else "Обновление программы") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(preview.title, color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
                Text(preview.summary, color = LocalPalette.current.muted)
                preview.changes.forEach { Text("• $it", color = LocalPalette.current.text) }
                preview.currentPlanName?.let { Text("Текущая локальная программа: $it", color = LocalPalette.current.muted) }
                if (preview.hasLocalEdits) Text("Эта программа менялась локально после предыдущего принятия. Принятие заменит её актуальное содержимое.", color = MaterialTheme.colorScheme.error)
                if (preview.alreadyImported) Text("Этот пакет уже обработан.", color = MaterialTheme.colorScheme.error)
                if (preview.type == "result") Text("Дубликаты ранее импортированных тренировок будут пропущены.", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(onClick = {
                if (bytes != null) vm.acceptExchange(bytes) { result, error ->
                    status = error ?: if (preview.type == "result") "Импортировано ${result?.importedSessions ?: 0} новых тренировок." else if (result?.deferred == true) "Обновление принято и будет применено после завершения тренировки." else "Программа принята и добавлена в планы."
                }
                exchangePreview = null; exchangeFile = null
            }, enabled = bytes != null && !preview.alreadyImported) { Text(if (preview.type == "result") "Импортировать" else "Принять") } },
            dismissButton = { Row {
                if (preview.type == "program" && !preview.alreadyImported && bytes != null) TextButton(onClick = {
                    vm.rejectExchange(bytes) { error -> status = error ?: "Обновление программы отклонено." }
                    exchangePreview = null; exchangeFile = null
                }) { Text("Отклонить") }
                TextButton(onClick = { exchangePreview = null; exchangeFile = null }) { Text("Закрыть") }
            } },
        )
    }

    revokeTarget?.let { link -> AlertDialog(
        onDismissRequest = { revokeTarget = null },
        title = { Text("Отозвать связь?") },
        text = { Text("Связь будет отозвана на этом устройстве. Если для неё включён Telegram-маршрут, отдельно отправь боту /disconnect ${link.id.take(8)}. Уже переданные данные останутся у получателя.") },
        confirmButton = { TextButton(onClick = { vm.revokeLink(link.id) { error -> status = error ?: "Связь отозвана на этом устройстве." }; revokeTarget = null }) { Text("Отозвать") } },
        dismissButton = { TextButton(onClick = { revokeTarget = null }) { Text("Назад") } },
    ) }

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
        text = { Text("Планы, тренировки и настройки на этом устройстве будут заменены данными из копии. Связи с устройствами сбросятся — их нужно будет подтвердить повторно.") },
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

private fun writeExchangeFile(context: android.content.Context, bytes: ByteArray, filename: String, maxBytes: Int = maxExchangeBytes): Uri {
    require(bytes.size <= maxBytes) { "Файл слишком большой" }
    val folder = File(context.cacheDir, "exchange").apply { mkdirs() }
    val safeName = filename.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
    val file = File(folder, safeName)
    file.outputStream().use { it.write(bytes) }
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

private fun shareExchangeUri(context: android.content.Context, uri: Uri, mime: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "RepTrack exchange", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Передать файл RepTrack"))
}

@Composable
private fun HistoryScreen(history: List<TrainingSession>, unit: WeightUnit, targetId: String?, clearTarget: () -> Unit, modifier: Modifier, canSend: Boolean, resend: (String) -> Unit) {
    ScreenColumn(modifier) {
        ScreenTitle("История", "Завершённые тренировки")
        if (history.isEmpty()) Text("История появится после завершения тренировки.", color = LocalPalette.current.muted)
        targetId?.let { id ->
            history.firstOrNull { it.id == id }?.let { session ->
                Text("Тренировка из графика", color = LocalPalette.current.secondary, fontWeight = FontWeight.Bold)
                SessionHistoryCard(session, unit, canSend) { resend(session.id) }
                TextButton(onClick = clearTarget, modifier = Modifier.heightIn(min = 48.dp)) { Text("Показать всю историю") }
            }
        }
        history.filter { it.id != targetId }.forEach { session -> SessionHistoryCard(session, unit, canSend) { resend(session.id) } }
    }
}

@Composable
private fun SessionHistoryCard(session: TrainingSession, unit: WeightUnit, canSend: Boolean = false, resend: () -> Unit = {}) {
            Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(session.planName, fontWeight = FontWeight.Bold, color = LocalPalette.current.text); Text("Макс. вес ${session.maxWeightKg?.let { formatWeight(it, unit) } ?: "—"}", color = LocalPalette.current.secondary) }
                    val duration = if (session.comment.contains("время и длительность неизвестны")) "длительность не указана" else "${((session.endedAt ?: session.startedAt) - session.startedAt).coerceAtLeast(0) / 60000L} мин"
                    Text("${date(session.startedAt)} · $duration · ${session.exercises.size} упражнений · ${session.completedSets} рабочих подходов", color = LocalPalette.current.muted)
                    if (session.comment.isNotBlank()) Text(session.comment, color = LocalPalette.current.secondary)
                    if (canSend) OutlinedButton(onClick = resend, modifier = Modifier.fillMaxWidth()) { Text("Повторно подготовить PDF и пакет") }
                    session.exercises.forEach { exercise ->
                        val sets = exercise.sets.filter { it.done || it.skipped }.joinToString(" · ") {
                            when {
                                it.skipped -> "пропуск"
                                else -> "${if (it.kind == SetKind.WARMUP) "разминка " else ""}${displayWeight(it.weight, unit).ifBlank { "—" }}×${it.reps.ifBlank { "—" }}"
                            }
                        }
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
                if (exercise != null) TrendGraph(state.history, exercise, from, metric, state.settings.weightUnit, onPointClick = openSession)
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
private fun TrendGraph(
    history: List<TrainingSession>,
    exerciseName: String,
    from: Long?,
    metric: ProgressMetric,
    unit: WeightUnit,
    height: androidx.compose.ui.unit.Dp = 150.dp,
    showLabels: Boolean = true,
    onPointClick: ((String) -> Unit)? = null,
) {
    val colors = LocalPalette.current
    val points = calculateProgressSeries(history, exerciseName, from, metric = metric)
    if (points.isEmpty()) { Text("Нет завершённых рабочих подходов для графика.", color = LocalPalette.current.muted); return }
    val graphModifier = Modifier.fillMaxWidth().height(height).then(
        if (onPointClick == null) Modifier else Modifier.pointerInput(points) {
            val chartWidth = size.width.toFloat().coerceAtLeast(1f)
            detectTapGestures { offset ->
                val index = if (points.size == 1) 0 else (offset.x / chartWidth * (points.size - 1)).toInt().coerceIn(points.indices)
                onPointClick(points[index].sessionId)
            }
        },
    )
    Canvas(graphModifier.semantics { contentDescription = "График ${metric.title()} для упражнения $exerciseName, ${points.size} записей" }) {
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val low = points.minOf { it.value }
        val high = points.maxOf { it.value }
        val valuePadding = ((high - low) * .18).coerceAtLeast(high * .12).coerceAtLeast(.25)
        val chartMin = (low - valuePadding).coerceAtLeast(0.0)
        val chartMax = (high + valuePadding).coerceAtLeast(chartMin + 1.0)
        val chartHeight = (bottom - top).coerceAtLeast(1f)
        for (index in 0..4) {
            val y = top + chartHeight * index / 4
            drawLine(colors.muted.copy(alpha = .19f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
        }
        val dx = if (points.size <= 1) 0f else size.width / (points.size - 1)
        val coords = points.mapIndexed { index, point ->
            val normalized = ((point.value - chartMin) / (chartMax - chartMin)).toFloat()
            androidx.compose.ui.geometry.Offset(index * dx, bottom - normalized * chartHeight)
        }
        if (coords.size > 1) {
            val linePath = androidx.compose.ui.graphics.Path().apply {
                moveTo(coords.first().x, coords.first().y)
                coords.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val areaPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(coords.first().x, bottom)
                coords.forEach { lineTo(it.x, it.y) }
                lineTo(coords.last().x, bottom)
                close()
            }
            drawPath(areaPath, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(colors.accent.copy(alpha = .28f), colors.accent.copy(alpha = 0f)), top, bottom))
            drawPath(linePath, colors.accent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        coords.forEach { point ->
            drawCircle(colors.background, radius = 6.dp.toPx(), center = point)
            drawCircle(colors.accent, radius = 4.dp.toPx(), center = point)
        }
    }
    if (showLabels) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        points.takeLast(5).map { "${SimpleDateFormat("d.M", Locale.forLanguageTag("ru")).format(Date(it.timestamp))} · ${metric.value(it.value, unit)}" }.forEach { Text(it, color = LocalPalette.current.muted, style = MaterialTheme.typography.labelSmall) }
    } else if (points.size == 1) Text("${date(points.single().timestamp)} · ${metric.value(points.single().value, unit)}", color = LocalPalette.current.muted, style = MaterialTheme.typography.labelSmall)
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
