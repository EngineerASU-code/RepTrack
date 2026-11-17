package app.reptrack

import android.os.Bundle
import android.content.ClipData
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.core.content.FileProvider

internal data class AppPalette(val background: Color, val surface: Color, val accent: Color, val secondary: Color, val muted: Color, val text: Color, val light: Boolean = false) {
    fun colorScheme() = if (light) lightColorScheme(background = background, surface = surface, primary = accent, secondary = secondary, onBackground = text, onSurface = text)
    else darkColorScheme(background = background, surface = surface, primary = accent, onPrimary = Color.Black, secondary = secondary, onBackground = text, onSurface = text)
}

private val purplePro = AppPalette(Color(0xFF12101C), Color(0xFF211C31), Color(0xFF9B75FF), Color(0xFFB4A0FF), Color(0xFFA9A3B9), Color(0xFFF7F5FC))
internal val LocalPalette = staticCompositionLocalOf { purplePro }
internal fun paletteFor(theme: DiaryTheme) = when (theme) {
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
private enum class NavigationIcon { HOME, GYM, PLAN, PROGRESS, HISTORY, SETTINGS, MORE, PAUSE, PLAY, SKIP, BACK }
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
    var tab by rememberSaveable { mutableStateOf("Главная") }
    var confirm by remember { mutableStateOf<String?>(null) }
    var historyTarget by remember { mutableStateOf<String?>(null) }
    var exerciseScreen by rememberSaveable { mutableStateOf<String?>(null) }
    var catalogScreen by rememberSaveable { mutableStateOf(false) }
    var catalogTargetPlanId by rememberSaveable { mutableStateOf<String?>(null) }
    var catalogSelectedNames by rememberSaveable { mutableStateOf(emptySet<String>()) }
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
    val workoutOpen = tab == "Зал" && state.activeSession != null && exerciseScreen == null && !catalogScreen
    val showBottomNavigation = !workoutOpen && exerciseScreen == null && !catalogScreen
    BackHandler(enabled = exerciseScreen != null) { exerciseScreen = null }
    BackHandler(enabled = catalogScreen) { catalogScreen = false }
    BackHandler(enabled = workoutOpen) { tab = "Главная" }
    CompositionLocalProvider(LocalPalette provides palette) {
    MaterialTheme(colorScheme = palette.colorScheme(), shapes = MaterialTheme.shapes.copy(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(16.dp))) {
        Scaffold(
            containerColor = LocalPalette.current.background,
            topBar = { TopAppBar(
                title = {
                    if (exerciseScreen != null) Text(
                        if (LocalDensity.current.fontScale >= 1.5f) "Упражнение" else exerciseScreen.orEmpty(),
                        color = LocalPalette.current.text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    else if (catalogScreen) Text("Каталог упражнений", color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
                    else if (workoutOpen) Column {
                        Text(state.activeSession?.planName?.removePrefix("Демо · ").orEmpty(), color = LocalPalette.current.text, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${((System.currentTimeMillis() - (state.activeSession?.startedAt ?: 0L)) / 60000L).coerceAtLeast(0)} мин · тренировка", color = LocalPalette.current.muted, style = MaterialTheme.typography.labelSmall)
                    } else Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(30.dp), shape = CircleShape, color = LocalPalette.current.accent.copy(alpha = .18f)) { Box(contentAlignment = Alignment.Center) { NavigationGlyph(NavigationIcon.GYM) } }
                        Spacer(Modifier.width(8.dp))
                        Text("Rep", color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
                        Text("Track", color = LocalPalette.current.secondary, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    if (exerciseScreen != null) IconButton(onClick = { exerciseScreen = null }, modifier = Modifier.semantics { contentDescription = "Назад" }.testTag("exercise-back")) { NavigationGlyph(NavigationIcon.BACK) }
                    else if (catalogScreen) IconButton(onClick = { catalogScreen = false }, modifier = Modifier.semantics { contentDescription = "Назад" }.testTag("catalog-back")) { NavigationGlyph(NavigationIcon.BACK) }
                    else if (workoutOpen) IconButton(onClick = { tab = "Главная" }, modifier = Modifier.semantics { contentDescription = "Назад" }) { NavigationGlyph(NavigationIcon.BACK) }
                },
                actions = {
                    if (!workoutOpen && exerciseScreen == null && !catalogScreen) {
                        TextButton(onClick = { tab = "Люди" }, modifier = Modifier.testTag("open-profiles")) {
                            Text(
                                if (state.activeProfile?.kind == ProfileKind.TRAINEE) "Демо" else state.activeProfile?.name ?: "Профиль",
                                color = LocalPalette.current.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { tab = "Настройки" }, modifier = Modifier.semantics { contentDescription = "Настройки" }.testTag("open-settings")) { NavigationGlyph(NavigationIcon.SETTINGS) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LocalPalette.current.background, titleContentColor = LocalPalette.current.text),
            ) },
            bottomBar = {
                if (showBottomNavigation) {
                    val navigationModifier = Modifier.fillMaxWidth().background(LocalPalette.current.background).navigationBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp)
                    val largeFont = LocalDensity.current.fontScale >= 1.5f
                    val navigationItem: @Composable (Int, Modifier) -> Unit = { index, itemModifier ->
                        val item = tabs[index]
                        val selected = tab == item
                        val tag = listOf("home", "gym", "plan", "progress", "history")[index]
                        Column(
                            itemModifier.heightIn(min = 56.dp)
                                .semantics { contentDescription = item; this.selected = selected }
                                .clickable(interactionSource = null, indication = null) { tab = item }
                                .testTag("nav-$tag")
                                .padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(Modifier.size(30.dp).background(if (selected) LocalPalette.current.accent.copy(alpha = .18f) else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                                CompositionLocalProvider(LocalContentColor provides if (selected) LocalPalette.current.secondary else LocalPalette.current.muted) { NavigationGlyph(tabIcons[index]) }
                            }
                            Text(item, color = if (selected) LocalPalette.current.secondary else LocalPalette.current.muted, style = MaterialTheme.typography.labelSmall, maxLines = 2, modifier = Modifier.testTag("nav-label-$tag"))
                        }
                    }
                    if (largeFont) Column(navigationModifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth()) { (0..2).forEach { navigationItem(it, Modifier.weight(1f)) } }
                        Row(Modifier.fillMaxWidth()) { (3..4).forEach { navigationItem(it, Modifier.weight(1f)) } }
                    } else Row(navigationModifier, horizontalArrangement = Arrangement.SpaceEvenly) {
                        tabs.indices.forEach { navigationItem(it, Modifier.weight(1f)) }
                    }
                } else state.activeSession?.let { WorkoutBottomBar(it, tick, pause = vm::pauseRest, resume = vm::resumeRest, skip = vm::skipRest) { confirm = "finish" } }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
                    if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else if (exerciseScreen != null) ExerciseDetailScreen(exerciseScreen!!, state, vm, tick, Modifier.fillMaxSize(), onBack = { exerciseScreen = null }) { id -> historyTarget = id; tab = "История"; exerciseScreen = null; catalogScreen = false }
                    else if (catalogScreen) ExerciseCatalogScreen(
                        state,
                        Modifier.fillMaxSize(),
                        selectForPlan = catalogTargetPlanId != null,
                        selectedNames = catalogSelectedNames,
                        onSelectedNamesChange = { catalogSelectedNames = it },
                        openExercise = { exerciseScreen = it },
                        addSelected = { names -> names.forEach { vm.addExercise(catalogTargetPlanId!!, it) }; catalogSelectedNames = emptySet(); catalogTargetPlanId = null; catalogScreen = false },
                    )
                    else when (tab) {
                        "Зал" -> GymScreen(state, vm, Modifier.fillMaxSize(), { confirm = it }, { catalogSelectedNames = emptySet(); catalogTargetPlanId = null; catalogScreen = true }) { exerciseScreen = it }
                        "План" -> PlanScreen(state, vm, Modifier.fillMaxSize(), { id -> catalogSelectedNames = emptySet(); catalogTargetPlanId = id; catalogScreen = true }, { exerciseScreen = it })
                        "Люди" -> CoachScreen(
                            state,
                            vm,
                            Modifier.fillMaxSize(),
                            showDemoProfile = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0,
                            openDemoProfile = { vm.createDemoProfile(); tab = "Главная" },
                            openSettings = { tab = "Настройки" },
                        )
                        "История" -> HistoryScreen(state.history, state.settings.weightUnit, historyTarget, { historyTarget = null }, Modifier.fillMaxSize(), state.activeProfile?.kind == ProfileKind.PERSONAL && state.trustedLinks.any { it.localProfileId == state.activeProfileId }, { retryDeliverySessionId = it }) { exerciseScreen = it }
                        "Прогресс" -> ProgressScreen(state, Modifier.fillMaxSize()) { id -> historyTarget = id; tab = "История" }
                        "Настройки" -> SettingsScreen(state, vm, Modifier.fillMaxSize(), incomingUri, onIncomingConsumed)
                        else -> HomeScreen(state, vm, Modifier.fillMaxSize()) { tab = it }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
                }
            }
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
                Button(onClick = { session?.let { if (action == "finish") vm.finish(it.id) else vm.cancel(it.id) }; confirm = null }, modifier = Modifier.fillMaxWidth().testTag("confirm-finish-workout")) { Text(if (action == "finish") "Только завершить" else "Отменить сессию") }
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
private fun WorkoutBottomBar(
    session: TrainingSession,
    tick: Long,
    pause: (String) -> Unit,
    resume: (String) -> Unit,
    skip: (String) -> Unit,
    finish: () -> Unit,
) {
    val now = remember(tick) { System.currentTimeMillis() }
    val sets = session.exercises.asSequence().flatMap { it.sets.asSequence() }.toList()
    val resting = sets.firstOrNull { set -> (set.restUntil ?: 0L) > now || set.restRemaining > 0L }
    val restJustFinished = resting == null && sets.mapNotNull { it.restUntil }
        .filter { it <= now && now - it <= 5_000L }.maxOrNull() != null
    Column(Modifier.fillMaxWidth().background(LocalPalette.current.surface).navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (resting != null) {
            val seconds = resting.restUntil?.let { ((it - now) / 1000L).coerceAtLeast(0L) } ?: (resting.restRemaining / 1000L)
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Отдых  %02d:%02d".format(seconds / 60, seconds % 60), color = LocalPalette.current.secondary, fontWeight = FontWeight.Bold)
                Row {
                    IconButton(onClick = { if (resting.restUntil != null) pause(resting.id) else resume(resting.id) }, modifier = Modifier.size(48.dp).semantics { contentDescription = if (resting.restUntil != null) "Приостановить отдых" else "Продолжить отдых" }) { NavigationGlyph(if (resting.restUntil != null) NavigationIcon.PAUSE else NavigationIcon.PLAY) }
                    IconButton(onClick = { skip(resting.id) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Пропустить отдых" }) { NavigationGlyph(NavigationIcon.SKIP) }
                }
            }
        } else if (restJustFinished) Text("Отдых завершён", color = LocalPalette.current.secondary, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("rest-complete"))
        Button(onClick = finish, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("finish-workout")) { Text("Завершить тренировку") }
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
            NavigationIcon.MORE -> repeat(3) { index -> drawCircle(color, radius = w * .065f, center = androidx.compose.ui.geometry.Offset(w * .5f, h * (.25f + index * .25f))) }
            NavigationIcon.PAUSE -> { line(.34f, .22f, .34f, .78f); line(.66f, .22f, .66f, .78f) }
            NavigationIcon.PLAY -> { drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * .32f, h * .2f); lineTo(w * .78f, h * .5f); lineTo(w * .32f, h * .8f); close() }, color) }
            NavigationIcon.SKIP -> { line(.2f, .2f, .58f, .5f); line(.58f, .5f, .2f, .8f); line(.2f, .5f, .58f, .5f); line(.72f, .2f, .72f, .8f) }
            NavigationIcon.BACK -> { line(.76f, .5f, .24f, .5f); line(.24f, .5f, .48f, .26f); line(.24f, .5f, .48f, .74f) }
        }
    }
}

@Composable
private fun ScreenColumn(
    modifier: Modifier,
    scrollState: ScrollState = rememberScrollState(),
    onViewportPositioned: (LayoutCoordinates) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxSize().onGloballyPositioned(onViewportPositioned).verticalScroll(scrollState).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = LocalPalette.current.text)
        Text(subtitle, color = LocalPalette.current.muted)
    }
}

@Composable
private fun PrimaryActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tag: String? = null, enabled: Boolean = true) {
    val brush = Brush.horizontalGradient(listOf(Color(0xFF8957F6), Color(0xFFB05AFF)))
    val taggedModifier = tag?.let { modifier.testTag(it) } ?: modifier
    Button(onClick = onClick, modifier = taggedModifier.fillMaxWidth().heightIn(min = 50.dp), contentPadding = PaddingValues(0.dp), enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.Black)) {
        Row(Modifier.fillMaxWidth().height(50.dp).background(brush, RoundedCornerShape(14.dp)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            NavigationGlyph(NavigationIcon.PLAY)
            Spacer(Modifier.width(8.dp))
            Text(label, color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HomeScreen(state: DiarySnapshot, vm: DiaryViewModel, modifier: Modifier, navigate: (String) -> Unit) {
    val active = state.activeSession
    val next = state.nextPlan
    val cycle = state.plans.filter { it.inCycle }
    val lastPlanId = state.history.firstOrNull()?.planId
    val targetGroups = (active?.exercises?.map { it.name } ?: next?.exercises?.map { it.name }.orEmpty())
        .flatMap { verifiedGuideFor(it)?.primaryMuscles.orEmpty() }
        .distinct()
        .joinToString(" · ") { it.compactLabel }
    var selectedExercise by rememberSaveable { mutableStateOf<String?>(null) }
    val exerciseNames = state.history.asSequence().flatMap { it.exercises.asSequence() }
        .filter { it.tracksLoad }
        .map { it.name }
        .distinct()
        .filter { name -> calculateProgressSeries(state.history, name, System.currentTimeMillis() - 30 * 86_400_000L).isNotEmpty() }
        .toList()
    val progressExercise = selectedExercise?.takeIf { it in exerciseNames } ?: exerciseNames
        .maxByOrNull { name -> calculateProgressSeries(state.history, name, System.currentTimeMillis() - 30 * 86_400_000L).size }
    val progressPoints = progressExercise?.let { calculateProgressSeries(state.history, it, System.currentTimeMillis() - 30 * 86_400_000L) }.orEmpty()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface), border = BorderStroke(1.dp, LocalPalette.current.accent.copy(alpha = .28f))) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (active != null) "ТРЕНИРОВКА В ПРОЦЕССЕ" else "СЛЕДУЮЩАЯ ТРЕНИРОВКА", color = LocalPalette.current.secondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(active?.planName?.removePrefix("Демо · ") ?: next?.name?.removePrefix("Демо · ") ?: "Создай план", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = LocalPalette.current.text)
                    Text(targetGroups.ifBlank { "Группы мышц не подтверждены" }, color = LocalPalette.current.muted, style = MaterialTheme.typography.bodyMedium)
                }
                HomeHeroArtwork(
                    modifier = Modifier.size(74.dp),
                    primaryColor = LocalPalette.current.accent,
                    secondaryColor = LocalPalette.current.secondary,
                )
            }
        }
        PrimaryActionButton(if (active != null) "Продолжить тренировку" else "Начать тренировку", { if (active == null) vm.start(next?.id); navigate("Зал") }, Modifier, "start-workout", enabled = active != null || next != null)
        if (active == null && next == null) TextButton(onClick = { navigate("План") }, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)) { Text("Создать план тренировок") }
        if (cycle.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Цикл", color = LocalPalette.current.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(russianCount(cycle.size, "тренировка", "тренировки", "тренировок"), color = LocalPalette.current.muted, style = MaterialTheme.typography.labelMedium)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cycle.forEach { plan ->
                        val selected = plan.id == next?.id
                        val completed = plan.id == lastPlanId
                        Surface(shape = MaterialTheme.shapes.medium, color = if (selected) LocalPalette.current.accent.copy(alpha = .18f) else LocalPalette.current.surface, border = BorderStroke(1.dp, if (selected) LocalPalette.current.accent else LocalPalette.current.muted.copy(alpha = .2f))) {
                        Text(plan.name.removePrefix("Демо · ") + when { selected -> " · текущая"; completed -> " · готово"; else -> "" }, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), color = if (selected) LocalPalette.current.secondary else LocalPalette.current.text, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface), onClick = { navigate("История") }) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Последняя тренировка", color = LocalPalette.current.text, fontWeight = FontWeight.SemiBold)
                    state.history.firstOrNull()?.let { session ->
                        Text(session.planName.removePrefix("Демо · "), color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
                        Text("${date(session.endedAt ?: session.startedAt)} · ${russianCount(session.exercises.size, "упражнение", "упражнения", "упражнений")} · ${russianCount(session.completedSets, "рабочий подход", "рабочих подхода", "рабочих подходов")} · макс. ${session.maxWeightKg?.let { formatWeight(it, state.settings.weightUnit) } ?: "—"}", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                    } ?: Text("История появится после первой тренировки", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val compactWidth = LocalConfiguration.current.screenWidthDp < 390
                val heading: @Composable () -> Unit = {
                    Column(Modifier.weight(1f)) {
                        Text("Прогресс", color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
                        Text(progressExercise ?: "Пока нет записей", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                val actions: @Composable () -> Unit = {
                    if (exerciseNames.size > 1) TextButton(onClick = { selectedExercise = exerciseNames[(exerciseNames.indexOf(progressExercise) + 1).mod(exerciseNames.size)] }) { Text("Сменить") }
                    TextButton(onClick = { navigate("Прогресс") }, modifier = Modifier.testTag("home-progress-all")) { Text("Все") }
                }
                if (compactWidth) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { heading() }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        heading()
                        actions()
                    }
                }
                val latest = progressPoints.lastOrNull()
                val previous = progressPoints.dropLast(1).lastOrNull()
                if (latest != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatWeight(latest.value, state.settings.weightUnit), color = LocalPalette.current.secondary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    previous?.let {
                        val change = latest.value - it.value
                        val prefix = if (change >= 0) "+" else "−"
                        Text("$prefix${formatWeight(kotlin.math.abs(change), state.settings.weightUnit)} к прошлой", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (progressExercise != null) TrendGraph(state.history, progressExercise, System.currentTimeMillis() - 30 * 86_400_000L, ProgressMetric.MAX_WEIGHT, state.settings.weightUnit, height = 112.dp, showLabels = false, onPointClick = { navigate("Прогресс") })
                else Text("Заверши силовой подход — здесь появится график.", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
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
        if (showDemoProfile) OutlinedButton(onClick = { vm.createDemoReportsProfile() }, modifier = Modifier.fillMaxWidth()) {
            Text("Загрузить демонстрационные тренировки")
        }
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Поиск профиля") }, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !sortByRecent, onClick = { sortByRecent = false }, label = { Text("По имени") })
                FilterChip(selected = sortByRecent, onClick = { sortByRecent = true }, label = { Text("По активности") })
            }
            Button(onClick = { addProfile = true }) { Text("Добавить профиль") }
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
                    val largeFont = LocalDensity.current.fontScale >= 1.3f
                    val actions: @Composable (Modifier) -> Unit = { buttonModifier ->
                        if (state.activeProfileId == profile.id) Text("Выбран", color = LocalPalette.current.secondary, modifier = Modifier.padding(12.dp))
                        else OutlinedButton(
                            onClick = { vm.setActiveProfile(profile.id) },
                            modifier = buttonModifier.testTag("profile-open-diary"),
                        ) { Text("Открыть дневник") }
                        if (profile.kind == ProfileKind.TRAINEE) TextButton(onClick = { renameProfile = profile }, modifier = buttonModifier) { Text("Переименовать") }
                    }
                    if (largeFont) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        actions(Modifier.fillMaxWidth())
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        actions(Modifier)
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
private fun ExerciseCatalogScreen(state: DiarySnapshot, modifier: Modifier, selectForPlan: Boolean, selectedNames: Set<String>, onSelectedNamesChange: (Set<String>) -> Unit, openExercise: (String) -> Unit, addSelected: (List<String>) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var verifiedOnly by rememberSaveable { mutableStateOf(false) }
    var selectedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedEquipment by rememberSaveable { mutableStateOf<String?>(null) }
    var groupMenu by remember { mutableStateOf(false) }
    var equipmentMenu by remember { mutableStateOf(false) }
    val allNames = remember(state.plans, state.history) {
        (state.plans.flatMap { it.exercises }.map { it.name } + state.history.flatMap { it.exercises }.map { it.name }).distinct().sorted()
    }
    val guides = allNames.associateWith(::verifiedGuideFor)
    val groups = guides.values.filterNotNull().flatMap { it.primaryMuscles }.distinct().sortedBy { it.compactLabel }
    val equipment = guides.values.filterNotNull().map { it.equipment }.distinct().sorted()
    val names = allNames.filter { name ->
        val guide = guides[name]
        name.contains(query.trim(), ignoreCase = true) &&
            (!verifiedOnly || guide != null) &&
            (selectedGroup == null || guide?.primaryMuscles?.any { it.compactLabel == selectedGroup } == true) &&
            (selectedEquipment == null || guide?.equipment == selectedEquipment)
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Поиск упражнения") }, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !verifiedOnly, onClick = { verifiedOnly = false }, label = { Text("Все") })
            FilterChip(selected = verifiedOnly, onClick = { verifiedOnly = true }, label = { Text("С проверенной техникой") })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                FilterChip(selected = selectedGroup != null, onClick = { groupMenu = true }, label = { Text(selectedGroup ?: "Группа") })
                DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                    DropdownMenuItem(text = { Text("Все группы") }, onClick = { selectedGroup = null; groupMenu = false })
                    groups.forEach { group -> DropdownMenuItem(text = { Text(group.compactLabel) }, onClick = { selectedGroup = group.compactLabel; groupMenu = false }) }
                }
            }
            Box {
                FilterChip(selected = selectedEquipment != null, onClick = { equipmentMenu = true }, label = { Text(selectedEquipment ?: "Оборудование", maxLines = 1, overflow = TextOverflow.Ellipsis) })
                DropdownMenu(expanded = equipmentMenu, onDismissRequest = { equipmentMenu = false }) {
                    DropdownMenuItem(text = { Text("Любое оборудование") }, onClick = { selectedEquipment = null; equipmentMenu = false })
                    equipment.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { selectedEquipment = item; equipmentMenu = false }) }
                }
            }
        }
        Text("${names.size} упражнений", color = LocalPalette.current.muted, style = MaterialTheme.typography.labelMedium)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allNames.isEmpty()) Text("В планах и истории этого профиля пока нет упражнений. Добавь первое упражнение вручную в плане.", color = LocalPalette.current.muted)
            else if (names.isEmpty()) {
                Text("Поиск и фильтры ничего не нашли.", color = LocalPalette.current.muted)
                TextButton(onClick = { query = ""; verifiedOnly = false; selectedGroup = null; selectedEquipment = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Сбросить поиск и фильтры") }
            }
            names.forEach { name ->
                Card(onClick = { if (selectForPlan) onSelectedNamesChange(if (name in selectedNames) selectedNames - name else selectedNames + name) else openExercise(name) }, colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface), border = BorderStroke(if (selectForPlan && name in selectedNames) 1.5.dp else 1.dp, if (selectForPlan && name in selectedNames) LocalPalette.current.accent else LocalPalette.current.muted.copy(alpha = .18f))) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ExerciseArtwork(name, Modifier.size(52.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, color = LocalPalette.current.text, fontWeight = FontWeight.SemiBold)
                            Text(if (verifiedGuideFor(name) != null) "Техника проверена · детали" else "Материал требует уточнения · детали", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (selectForPlan) {
                            Checkbox(checked = name in selectedNames, onCheckedChange = { checked -> onSelectedNamesChange(if (checked) selectedNames + name else selectedNames - name) })
                            TextButton(onClick = { openExercise(name) }) { Text("Детали") }
                        }
                        else Text("Открыть", color = LocalPalette.current.secondary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (selectForPlan) Button(onClick = { addSelected(selectedNames.toList()) }, enabled = selectedNames.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Добавить выбранные · ${selectedNames.size}") }
    }
}

@Composable
private fun GymScreen(state: DiarySnapshot, vm: DiaryViewModel, modifier: Modifier, confirm: (String) -> Unit, openCatalog: () -> Unit, openExercise: (String) -> Unit) {
    val session = state.activeSession
    val scrollState = rememberScrollState()
    var viewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val scrollScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var replacementFor by remember { mutableStateOf<String?>(null) }
    var exerciseActionsFor by remember { mutableStateOf<String?>(null) }
    var showCommentDialog by remember(session?.id) { mutableStateOf(false) }
    var commentDraft by rememberSaveable(session?.id) { mutableStateOf(session?.comment.orEmpty()) }
    ScreenColumn(modifier, scrollState, { viewportCoordinates = it }) {
        if (session == null) ScreenTitle("Зал", "Отмечай фактические подходы")
        if (session == null) {
            val recommended = state.nextPlan
            recommended?.let { plan ->
                Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface), border = BorderStroke(1.dp, LocalPalette.current.accent.copy(alpha = .4f))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("СЛЕДУЮЩАЯ ТРЕНИРОВКА", color = LocalPalette.current.secondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            plan.exercises.firstOrNull()?.let { ExerciseArtwork(it.name, Modifier.size(56.dp)) }
                            Column {
                                Text(plan.name, color = LocalPalette.current.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    plan.exercises.flatMap { verifiedGuideFor(it.name)?.primaryMuscles.orEmpty() }.distinct().joinToString(" · ") { it.compactLabel }
                                        .ifBlank { "${plan.exercises.size} упражнений по плану" },
                                    color = LocalPalette.current.muted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        PrimaryActionButton("Начать тренировку", { vm.start(plan.id) })
                    }
                }
            }
            Text("Другие тренировки", style = MaterialTheme.typography.titleMedium, color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
            state.plans.forEach { plan ->
                if (plan.id == recommended?.id) return@forEach
                Card(onClick = { vm.start(plan.id) }, colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        plan.exercises.firstOrNull()?.let { ExerciseArtwork(it.name, Modifier.size(48.dp)) }
                        Column(Modifier.weight(1f)) { Text(plan.name.removePrefix("Демо · "), fontWeight = FontWeight.Bold, color = LocalPalette.current.text); Text("${plan.exercises.size} упражнений", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall) }
                        Text("Начать", color = LocalPalette.current.secondary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            OutlinedButton(onClick = openCatalog, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Открыть каталог упражнений") }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Упражнения ${session.exercises.count { exercise -> exercise.skipped || exercise.sets.any { it.done } }}/${session.exercises.size}", color = LocalPalette.current.muted, style = MaterialTheme.typography.labelMedium)
                    Text("Подходы ${session.completedSets}/${session.totalSets}", color = LocalPalette.current.muted, style = MaterialTheme.typography.labelMedium)
                }
                LinearProgressIndicator(progress = { if (session.totalSets == 0) 0f else session.completedSets.toFloat() / session.totalSets }, modifier = Modifier.fillMaxWidth().height(4.dp), color = LocalPalette.current.accent, trackColor = LocalPalette.current.surface)
                TextButton(onClick = { commentDraft = session.comment; showCommentDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (session.comment.isBlank()) "Комментарий" else "Комментарий: ${session.comment}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            session.exercises.forEach { exercise ->
                var expanded by rememberSaveable(session.id, exercise.id) { mutableStateOf(exercise.position == 0) }
                Card(modifier = Modifier.testTag("workout-exercise-card"), border = BorderStroke(1.dp, if (expanded) LocalPalette.current.accent else LocalPalette.current.muted.copy(alpha = 0.2f)), colors = CardDefaults.cardColors(containerColor = if (expanded) LocalPalette.current.accent.copy(alpha = .06f) else LocalPalette.current.surface)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            ExerciseArtwork(exercise.name, Modifier.size(48.dp))
                            Column(
                                Modifier.weight(1f).clickable { expanded = !expanded }.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text("${exercise.position + 1}. ${exercise.name}", fontWeight = FontWeight.SemiBold, color = LocalPalette.current.text, maxLines = if (LocalConfiguration.current.screenWidthDp < 390) 3 else 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    verifiedGuideFor(exercise.name)?.primaryMuscles?.joinToString(" · ") { it.compactLabel } ?: "Группа не указана",
                                    color = LocalPalette.current.muted,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            val arrowColor = LocalPalette.current.muted
                            IconButton(
                                onClick = { expanded = !expanded },
                                modifier = Modifier.size(48.dp).semantics { contentDescription = if (expanded) "Свернуть ${exercise.name}" else "Развернуть ${exercise.name}" },
                            ) {
                                Canvas(Modifier.size(18.dp)) {
                                    val top = if (expanded) size.height * .62f else size.height * .38f
                                    val bottom = if (expanded) size.height * .38f else size.height * .62f
                                    drawLine(arrowColor, Offset(size.width * .24f, top), Offset(size.width * .5f, bottom), 2.dp.toPx())
                                    drawLine(arrowColor, Offset(size.width * .5f, bottom), Offset(size.width * .76f, top), 2.dp.toPx())
                                }
                            }
                            if (expanded) {
                            Box {
                                IconButton(
                                    onClick = { exerciseActionsFor = exercise.id },
                                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Действия для упражнения ${exercise.name}" },
                                ) { NavigationGlyph(NavigationIcon.MORE) }
                                DropdownMenu(expanded = exerciseActionsFor == exercise.id, onDismissRequest = { exerciseActionsFor = null }) {
                                    DropdownMenuItem(text = { Text(if (exercise.skipped) "Вернуть" else "Пропустить") }, onClick = { vm.skipExercise(exercise.id, !exercise.skipped); exerciseActionsFor = null })
                                    DropdownMenuItem(text = { Text("Заменить") }, onClick = { replacementFor = exercise.id; exerciseActionsFor = null })
                                }
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
                        TextButton(onClick = { openExercise(exercise.name) }, modifier = Modifier.heightIn(min = 48.dp).testTag("exercise-details")) { Text("Открыть упражнение") }
                        if (exercise.skipped) Text("Упражнение пропущено", color = LocalPalette.current.muted)
                        else {
                            if (LocalDensity.current.fontScale < 1.3f) Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("№", Modifier.width(48.dp), color = LocalPalette.current.muted, style = MaterialTheme.typography.labelSmall)
                                Text("Вес, ${state.settings.weightUnit.label()}", Modifier.weight(1f), color = LocalPalette.current.muted, style = MaterialTheme.typography.labelSmall)
                                Text("Повторы", Modifier.weight(1f), color = LocalPalette.current.muted, style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.width(48.dp)); Spacer(Modifier.width(48.dp))
                            } else Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Вес, ${state.settings.weightUnit.label()}", Modifier.weight(1f), color = LocalPalette.current.muted, style = MaterialTheme.typography.labelSmall)
                                Text("Повторы", Modifier.weight(1f), color = LocalPalette.current.muted, style = MaterialTheme.typography.labelSmall)
                            }
                            exercise.sets.forEachIndexed { index, set -> SetEditor(index, set, vm, exercise.tracksLoad, state.settings.weightUnit) { row ->
                                scrollScope.launch {
                                    delay(250)
                                    repeat(2) {
                                        viewportCoordinates?.let { viewport ->
                                            val rowBottom = row.localToWindow(Offset.Zero).y + row.size.height
                                            val viewportBottom = viewport.localToWindow(Offset.Zero).y + viewport.size.height
                                            val overflow = rowBottom - viewportBottom + with(density) { 8.dp.toPx() }
                                            if (overflow > 0f) {
                                                scrollState.animateScrollTo((scrollState.value + overflow.toInt()).coerceAtMost(scrollState.maxValue))
                                            }
                                        }
                                        delay(120)
                                    }
                                }
                            } }
                        }
                        if (!exercise.skipped) OutlinedButton(onClick = { vm.addSet(exercise.id) }, modifier = Modifier.fillMaxWidth()) { Text("Добавить подход") }
                        }
                    }
                }
            }
        }
        if (session != null) Spacer(Modifier.height(136.dp))
        Spacer(Modifier.height(8.dp))
    }
    if (session != null && showCommentDialog) AlertDialog(
        onDismissRequest = { showCommentDialog = false },
        title = { Text("Комментарий к тренировке") },
        text = {
            OutlinedTextField(
                value = commentDraft,
                onValueChange = { commentDraft = it; vm.updateComment(session.id, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Комментарий") },
            )
        },
        confirmButton = { TextButton(onClick = { showCommentDialog = false }) { Text("Готово") } },
    )
}

internal data class ExerciseGuide(
    val equipment: String,
    val primaryMuscles: List<ExerciseMuscleGroup>,
    val secondaryMuscles: List<ExerciseMuscleGroup>,
    val steps: List<String>,
    val sourceLabel: String,
    val sourceUrl: String,
    val muscleSourceName: String,
)

internal fun verifiedGuideFor(name: String): ExerciseGuide? {
    val key = name.lowercase(Locale.ROOT).replace('ё', 'е').replace(Regex("[^a-zа-я]+"), " ").trim()
    return when (key) {
        "жим штанги средним хватом", "barbell bench press medium grip" -> ExerciseGuide(
            equipment = "Штанга и горизонтальная скамья",
            primaryMuscles = listOf(ExerciseMuscleGroup.CHEST),
            secondaryMuscles = listOf(ExerciseMuscleGroup.TRICEPS, ExerciseMuscleGroup.SHOULDERS),
            steps = listOf(
                "Ляг на скамью, поставь стопы на пол и возьмись за гриф чуть шире плеч.",
                "Сведи и опусти лопатки; удерживай корпус и стопы устойчиво.",
                "Опускай гриф к середине груди, контролируя движение локтей.",
                "Выжми гриф вверх до почти прямых рук, не отрывая лопатки от скамьи.",
            ),
            sourceLabel = "NASM · Barbell Bench Press",
            sourceUrl = "https://www.nasm.org/resource-center/exercise-library/barbell-bench-press",
            muscleSourceName = "Barbell Bench Press - Medium Grip",
        )
        "тяга верхнего блока широким хватом", "wide grip lat pulldown cable" -> ExerciseGuide(
            equipment = "Верхний блок и широкий прямой гриф",
            primaryMuscles = listOf(ExerciseMuscleGroup.LATS),
            secondaryMuscles = listOf(ExerciseMuscleGroup.BICEPS, ExerciseMuscleGroup.MIDDLE_BACK, ExerciseMuscleGroup.SHOULDERS),
            steps = listOf(
                "Зафиксируй бёдра под валиками, поставь стопы на пол и возьмись за гриф широким хватом сверху.",
                "Сохраняй устойчивое положение корпуса и опусти плечи от ушей.",
                "Веди локти вниз к рёбрам и тяни гриф к верхней части груди.",
                "Плавно верни гриф вверх, не раскачивая корпус и не пожимая плечами.",
            ),
            sourceLabel = "NASM · Lat Pulldown: хват и техника",
            sourceUrl = "https://www.nasm.org/resource-center/blog/training/the-biomechanics-of-the-lat-pulldown-muscles-grip-and-form",
            muscleSourceName = "Wide-Grip Lat Pulldown",
        )
        "приседания со штангой", "barbell squat" -> ExerciseGuide(
            equipment = "Штанга и стойки",
            primaryMuscles = listOf(ExerciseMuscleGroup.QUADRICEPS),
            secondaryMuscles = listOf(ExerciseMuscleGroup.CALVES, ExerciseMuscleGroup.GLUTES, ExerciseMuscleGroup.HAMSTRINGS, ExerciseMuscleGroup.LOWER_BACK),
            steps = listOf(
                "Установи гриф на стойках чуть ниже уровня плеч и расположи его на верхней части спины.",
                "Возьмись за гриф шире плеч, напряги корпус и отойди от стоек.",
                "Отводи таз назад и опускайся, удерживая спину ровной и грудь поднятой.",
                "Упрись всей стопой в пол и поднимись, разгибая таз и колени вместе.",
            ),
            sourceLabel = "ACE · Back Squat",
            sourceUrl = "https://www.acefitness.org/resources/everyone/exercise-library/11/back-squat/",
            muscleSourceName = "Barbell Squat",
        )
        "жим ногами", "leg press machine", "seated leg press" -> ExerciseGuide(
            equipment = "Тренажёр для жима ногами",
            primaryMuscles = listOf(ExerciseMuscleGroup.QUADRICEPS),
            secondaryMuscles = listOf(ExerciseMuscleGroup.CALVES, ExerciseMuscleGroup.GLUTES, ExerciseMuscleGroup.HAMSTRINGS),
            steps = listOf(
                "Настрой сиденье так, чтобы стопы полностью стояли на платформе, а колени в исходном положении были примерно под прямым углом.",
                "Прижми спину и таз к спинке; сохраняй это положение во время движения.",
                "Плавно выжми платформу, не отрывая пятки; не переразгибай и не блокируй колени.",
                "Контролируемо верни платформу, не прижимая бёдра к корпусу.",
            ),
            sourceLabel = "ACE · Seated Leg Press",
            sourceUrl = "https://www.acefitness.org/resources/everyone/exercise-library/154/seated-leg-press/",
            muscleSourceName = "Leg Press",
        )
        "тяга нижнего блока узким хватом", "seated cable rows" -> ExerciseGuide(
            equipment = "Нижний блок и рукоять узким хватом",
            primaryMuscles = listOf(ExerciseMuscleGroup.MIDDLE_BACK),
            secondaryMuscles = listOf(ExerciseMuscleGroup.BICEPS, ExerciseMuscleGroup.LATS, ExerciseMuscleGroup.SHOULDERS),
            steps = listOf(
                "Сядь лицом к блоку, поставь стопы на платформу и возьмись за рукоять нейтральным хватом.",
                "Сохраняй ровное положение спины и устойчивый корпус.",
                "Веди локти назад вдоль корпуса и тяни рукоять к нижней части рёбер.",
                "Плавно выпрями руки, не округляя спину и не раскачиваясь.",
            ),
            sourceLabel = "NASM · Seated Machine Row, Close Grip",
            sourceUrl = "https://www.nasm.org/resource-center/exercise-library/seated-machine-row-close-grip",
            muscleSourceName = "Seated Cable Rows",
        )
        "попеременные молотки с гантелями", "alternate hammer curl dumbbells alternating" -> ExerciseGuide(
            equipment = "Пара гантелей",
            primaryMuscles = listOf(ExerciseMuscleGroup.BICEPS),
            secondaryMuscles = listOf(ExerciseMuscleGroup.FOREARMS),
            steps = listOf(
                "Встань устойчиво, держи гантели у бёдер нейтральным хватом: ладони обращены друг к другу.",
                "Сохраняя локти около корпуса и запястья ровными, согни одну руку.",
                "Подними гантель без раскачивания туловища.",
                "Медленно опусти её и повтори другой рукой.",
            ),
            sourceLabel = "ACE · Hammer Curl",
            sourceUrl = "https://www.acefitness.org/resources/everyone/exercise-library/10/hammer-curl/",
            muscleSourceName = "Alternate Hammer Curl",
        )
        else -> null
    }
}

@Composable
private fun ExerciseDetailScreen(name: String, state: DiarySnapshot, vm: DiaryViewModel, tick: Long, modifier: Modifier, onBack: () -> Unit, openSession: (String) -> Unit) {
    var selectedTab by rememberSaveable(name) { mutableIntStateOf(0) }
    val detailScrollState = rememberScrollState()
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    LaunchedEffect(selectedTab) { detailScrollState.scrollTo(0) }
    val guide = remember(name) { verifiedGuideFor(name) }
    val related = state.history.flatMap { session ->
        session.exercises.filter { it.name == name && !it.skipped && it.tracksLoad }.map { session to it }
    }.sortedByDescending { it.first.endedAt ?: it.first.startedAt }
    val workingSets = related.flatMap { it.second.sets }.filter { it.done && !it.skipped && it.kind == SetKind.WORK }
    val recordEntry = related.flatMap { (session, item) -> item.sets.filter { it.done && !it.skipped && it.kind == SetKind.WORK }.mapNotNull { set -> parseFiniteWeight(set.weight)?.takeIf { it > 0.0 }?.let { it to session.id } } }.maxByOrNull { it.first }
    val record = recordEntry?.first
    val context = LocalContext.current
    val activeExercise = state.activeSession?.exercises?.firstOrNull { it.name == name }

        Surface(modifier = modifier.fillMaxSize(), color = LocalPalette.current.background) {
            Column {
                val tabTitles = listOf("Подходы", "Техника", "Мышцы")
                val tabContent: @Composable () -> Unit = {
                    tabTitles.forEachIndexed { index, title ->
                        val tag = listOf("tab-sets", "tab-technique", "tab-muscles")[index]
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }, modifier = Modifier.testTag(tag), text = { Text(title) })
                    }
                }
                if (largeFont) ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = LocalPalette.current.background, edgePadding = 16.dp, tabs = tabContent)
                else TabRow(selectedTabIndex = selectedTab, containerColor = LocalPalette.current.background, tabs = tabContent)
                Column(
                    Modifier.weight(1f).verticalScroll(detailScrollState).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (largeFont) Text(
                        name,
                        modifier = Modifier.fillMaxWidth().testTag("exercise-screen-title"),
                        color = LocalPalette.current.text,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    when (selectedTab) {
                        0 -> {
                            ExerciseArtwork(name, Modifier.fillMaxWidth().height(190.dp))
                            val stackSummaryCards = largeFont || LocalConfiguration.current.screenWidthDp <= 400
                            val previousWorkoutCard: @Composable (Modifier) -> Unit = { cardModifier ->
                                Card(cardModifier.clickable(enabled = related.isNotEmpty()) { related.firstOrNull()?.first?.id?.let(openSession) }, colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Прошлый раз", color = LocalPalette.current.muted, style = MaterialTheme.typography.labelMedium)
                                        val (session, item) = related.firstOrNull() ?: (null to null)
                                        Text(session?.let { date(it.endedAt ?: it.startedAt) } ?: "Нет данных", color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
                                        Text(item?.sets?.filter { it.done && !it.skipped && it.kind == SetKind.WORK }?.joinToString(" · ") { "${displayWeight(it.weight, state.settings.weightUnit)}×${it.reps}" } ?: "", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            val personalRecordCard: @Composable (Modifier) -> Unit = { cardModifier ->
                                Card(cardModifier.clickable(enabled = recordEntry != null) { recordEntry?.second?.let(openSession) }, colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Личный рекорд", color = LocalPalette.current.muted, style = MaterialTheme.typography.labelMedium)
                                        Text(record?.let { formatWeight(it, state.settings.weightUnit) } ?: "—", color = LocalPalette.current.secondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                                        Text("по завершённым подходам", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            if (stackSummaryCards) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                previousWorkoutCard(Modifier.fillMaxWidth())
                                personalRecordCard(Modifier.fillMaxWidth())
                            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                previousWorkoutCard(Modifier.weight(1f))
                                personalRecordCard(Modifier.weight(1f))
                            }
                            if (activeExercise != null) {
                                Text("Текущие подходы", color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("№", Modifier.width(48.dp), color = LocalPalette.current.muted, style = MaterialTheme.typography.labelSmall)
                                    Text("Вес, ${state.settings.weightUnit.label()}", Modifier.weight(1f), color = LocalPalette.current.muted, style = MaterialTheme.typography.labelSmall)
                                    Text("Повторы", Modifier.weight(1f), color = LocalPalette.current.muted, style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.width(48.dp)); Spacer(Modifier.width(48.dp))
                                }
                                activeExercise.sets.forEachIndexed { index, set -> SetEditor(index, set, vm, activeExercise.tracksLoad, state.settings.weightUnit) }
                                OutlinedButton(onClick = { vm.addSet(activeExercise.id) }, modifier = Modifier.fillMaxWidth()) { Text("Добавить подход") }
                            } else {
                                related.firstOrNull()?.let { (_, latest) ->
                                    Text("Подходы прошлого выполнения", color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
                                    latest.sets.filter { it.done && !it.skipped }.forEachIndexed { index, set ->
                                        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("${index + 1}", Modifier.width(48.dp), color = LocalPalette.current.muted)
                                            Text(displayWeight(set.weight, state.settings.weightUnit).ifBlank { "—" }, Modifier.weight(1f), color = LocalPalette.current.text)
                                            Text(set.reps.ifBlank { "—" }, Modifier.weight(1f), color = LocalPalette.current.text)
                                            Text(if (set.kind == SetKind.WORK) "Рабочий" else "Разминка", color = LocalPalette.current.muted, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                val plan = state.plans.firstOrNull { p -> p.exercises.any { it.name == name } }
                                if (plan != null) PrimaryActionButton("Начать тренировку", { vm.start(plan.id) })
                            }
                        }
                        1 -> {
                            ExerciseTechniqueMedia(name, Modifier.fillMaxWidth())
                            if (guide == null) {
                                Text("Техника для этого варианта пока не проверена", color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
                                Text("Иллюстрации помогают узнать упражнение, но не заменяют проверенную инструкцию. Для этого варианта шаги пока не добавлены.", color = LocalPalette.current.muted)
                            } else {
                                Text(guide.equipment, color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                                Text("Техника выполнения", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LocalPalette.current.text)
                                guide.steps.forEachIndexed { index, step ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                                        Surface(shape = CircleShape, color = LocalPalette.current.accent) {
                                            Text("${index + 1}", Modifier.padding(horizontal = 11.dp, vertical = 5.dp), color = LocalPalette.current.background, fontWeight = FontWeight.Bold)
                                        }
                                        Text(step, Modifier.weight(1f), color = LocalPalette.current.text)
                                    }
                                }
                                Card(
                                    Modifier.fillMaxWidth().clickable { selectedTab = 2 },
                                    colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface),
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Целевые мышцы · открыть карту", color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
                                        Text(guide.primaryMuscles.joinToString(" · ") { it.label }, color = LocalPalette.current.secondary)
                                        if (guide.secondaryMuscles.isNotEmpty()) Text("Также работают: ${guide.secondaryMuscles.joinToString(" · ") { it.label }}", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Text("Инструкция подготовлена по материалу ${guide.sourceLabel}.", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(guide.sourceUrl))) } }) { Text("Источник: ${guide.sourceLabel}") }
                            }
                        }
                        else -> {
                            if (guide == null) {
                                ExerciseMediaPlaceholder("Карта мышц не добавлена")
                                Text("Не определяем группы мышц по одному только названию упражнения.", color = LocalPalette.current.muted)
                            } else {
                                ExerciseMuscleMap(
                                    primary = guide.primaryMuscles,
                                    secondary = guide.secondaryMuscles,
                                    primaryColor = LocalPalette.current.accent,
                                    secondaryColor = LocalPalette.current.secondary,
                                    modifier = Modifier.fillMaxWidth().testTag("muscle-map"),
                                )
                                Text("Основная нагрузка", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LocalPalette.current.text)
                                guide.primaryMuscles.forEach { group ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.size(8.dp).background(LocalPalette.current.accent, CircleShape))
                                        Text(group.label, color = LocalPalette.current.text, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                                if (guide.secondaryMuscles.isNotEmpty()) {
                                    Text("Дополнительно", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LocalPalette.current.text)
                                    guide.secondaryMuscles.forEach { group ->
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Box(Modifier.size(8.dp).background(LocalPalette.current.secondary, CircleShape))
                                            Text(group.label, color = LocalPalette.current.text, style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                                Text("Зоны на карте — группы из источника упражнения; схема показывает их расположение, а не точную биомеханику.", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yuhonas/free-exercise-db/blob/main/dist/exercises.json"))) } }) {
                                    Text("Мышцы: Free Exercise DB · ${guide.muscleSourceName}")
                                }
                                Text("Анатомическая иллюстрация: Surya Mouly / muscle_mapper · MIT", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
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
private fun SetEditor(
    index: Int,
    set: TrainingSet,
    vm: DiaryViewModel,
    tracksLoad: Boolean,
    unit: WeightUnit,
    onFieldFocused: ((LayoutCoordinates) -> Unit)? = null,
) {
    var weight by remember(set.id, set.weight, unit) { mutableStateOf(displayWeight(set.weight, unit)) }
    var reps by remember(set.id) { mutableStateOf(set.reps) }
    var menu by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var rowCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val focusCallback: (() -> Unit)? = onFieldFocused?.let { callback -> { rowCoordinates?.let(callback) } }
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val rowModifier = Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoViewRequester).onGloballyPositioned { rowCoordinates = it }.testTag("workout-set-row").background(if (index % 2 == 0) LocalPalette.current.background.copy(alpha = .45f) else Color.Transparent, MaterialTheme.shapes.small)
    val setNumber: @Composable () -> Unit = {
        Row(Modifier.width(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text("${index + 1}", color = LocalPalette.current.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (set.kind == SetKind.WARMUP) Text("Р", color = LocalPalette.current.muted, style = MaterialTheme.typography.labelSmall)
        }
    }
    val setActions: @Composable () -> Unit = {
        Checkbox(checked = set.done, onCheckedChange = { vm.toggleSet(set) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Отметить подход ${index + 1}" })
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Действия для подхода ${index + 1}" }) { NavigationGlyph(NavigationIcon.MORE) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (set.kind == SetKind.WORK) "Отметить разминкой" else "Отметить рабочим") }, onClick = { vm.setKind(set.id, if (set.kind == SetKind.WORK) SetKind.WARMUP else SetKind.WORK); menu = false })
                DropdownMenuItem(text = { Text("Удалить подход") }, onClick = { vm.removeSet(set.id); menu = false })
            }
        }
    }
    val setFields: @Composable RowScope.() -> Unit = {
        if (tracksLoad) {
            CompactNumberField(weight, "Вес ${unit.label()}", Modifier.weight(1f), KeyboardType.Decimal, bringIntoViewRequester, focusCallback) { weight = it; vm.updateSet(set.id, weight = canonicalWeight(weight, unit)) }
            CompactNumberField(reps, "Повторы", Modifier.weight(1f), KeyboardType.Number, bringIntoViewRequester, focusCallback) { reps = it; vm.updateSet(set.id, reps = reps) }
        } else Text("—", Modifier.weight(2f), color = LocalPalette.current.muted)
    }
    if (largeFont && tracksLoad) {
        Column(rowModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                setNumber()
                Spacer(Modifier.weight(1f))
                setActions()
            }
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                setFields()
            }
        }
    } else {
        Row(rowModifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            setNumber()
            setFields()
            setActions()
        }
    }
}

@Composable
private fun CompactNumberField(value: String, label: String, modifier: Modifier, keyboardType: KeyboardType, bringIntoViewRequester: BringIntoViewRequester, onFocused: (() -> Unit)?, onValueChange: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    Surface(modifier.heightIn(min = 48.dp), shape = MaterialTheme.shapes.small, color = LocalPalette.current.background, border = BorderStroke(1.dp, LocalPalette.current.muted.copy(alpha = .25f))) {
        Box(contentAlignment = Alignment.Center) {
            if (value.isEmpty()) Text(label, color = LocalPalette.current.muted.copy(alpha = .7f), style = MaterialTheme.typography.labelSmall)
            BasicTextField(
                value = value,
                onValueChange = { next -> onValueChange(next.filter { it.isDigit() || (keyboardType == KeyboardType.Decimal && (it == '.' || it == ',')) }.take(if (keyboardType == KeyboardType.Decimal) 8 else 4)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 8.dp).onFocusEvent {
                    if (it.isFocused) {
                        if (onFocused != null) onFocused() else scope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                }.semantics { contentDescription = label },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalPalette.current.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun PlanScreen(state: DiarySnapshot, vm: DiaryViewModel, modifier: Modifier, openCatalog: (String) -> Unit, openExercise: (String) -> Unit) {
    val cyclePositions = state.plans.filter { it.inCycle }.mapIndexed { index, plan -> plan.id to index + 1 }.toMap()
    var selectedPlanId by rememberSaveable { mutableStateOf(state.nextPlan?.id ?: state.plans.firstOrNull()?.id) }
    val selectedPlan = state.plans.firstOrNull { it.id == selectedPlanId } ?: state.nextPlan ?: state.plans.firstOrNull()
    var addToPlan by remember { mutableStateOf<TrainingPlan?>(null) }
    var exerciseName by remember { mutableStateOf("") }
    var createPlan by remember { mutableStateOf(false) }
    var renamePlan by remember { mutableStateOf<TrainingPlan?>(null) }
    var deletePlan by remember { mutableStateOf<TrainingPlan?>(null) }
    var editExercise by remember { mutableStateOf<Pair<TrainingPlan, PlanExercise>?>(null) }
    var removeExercise by remember { mutableStateOf<Pair<TrainingPlan, PlanExercise>?>(null) }
    var exerciseActionsFor by remember { mutableStateOf<String?>(null) }
    ScreenColumn(modifier) {
        ScreenTitle("План", "Создавай шаблоны и настраивай тренировочный цикл")
        Button(onClick = { createPlan = true }, modifier = Modifier.fillMaxWidth()) { Text("Создать тренировку") }
        if (state.plans.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.plans.sortedBy { it.position }.forEach { plan ->
                val selected = selectedPlan?.id == plan.id
                            FilterChip(selected = selected, onClick = { selectedPlanId = plan.id }, label = { Text(if (plan.inCycle) "${cyclePositions[plan.id]}. ${plan.name.removePrefix("Демо · ")}" else plan.name.removePrefix("Демо · "), maxLines = 1) })
            }
        }
        if (state.plans.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Пока нет планов", color = LocalPalette.current.text, fontWeight = FontWeight.Bold)
                    Text("Можно начать с пустого плана или добавить предложенный цикл. Это не создаст историю тренировок.", color = LocalPalette.current.muted)
                    OutlinedButton(onClick = { vm.createStarterPlans() }, modifier = Modifier.fillMaxWidth()) { Text("Добавить стартовый цикл Push · Legs · Pull · Up") }
                }
            }
        }
        selectedPlan?.let { plan ->
            Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(plan.name.removePrefix("Демо · "), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LocalPalette.current.text); Text(if (plan.inCycle) "${cyclePositions[plan.id]} · в цикле" else "Вне цикла", color = LocalPalette.current.muted) }
                        Box {
                            var menu by remember(plan.id) { mutableStateOf(false) }
                            IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "Действия плана ${plan.name.removePrefix("Демо · ")}" }) { NavigationGlyph(NavigationIcon.MORE) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Поднять в цикле") }, onClick = { vm.movePlan(plan.id, -1); menu = false })
                                DropdownMenuItem(text = { Text("Опустить в цикле") }, onClick = { vm.movePlan(plan.id, 1); menu = false })
                                DropdownMenuItem(text = { Text("Переименовать") }, onClick = { renamePlan = plan; menu = false })
                                DropdownMenuItem(text = { Text("Дублировать") }, onClick = { vm.duplicatePlan(plan.id); menu = false })
                                DropdownMenuItem(text = { Text(if (plan.inCycle) "Убрать из цикла" else "В цикл") }, onClick = { vm.setPlanInCycle(plan.id, !plan.inCycle); menu = false })
                                DropdownMenuItem(text = { Text("Удалить") }, enabled = state.activeSession?.planId != plan.id, onClick = { deletePlan = plan; menu = false })
                            }
                        }
                    }
                    plan.exercises.forEachIndexed { index, exercise ->
                        Box {
                            CompactPlanExerciseRow(
                                exercise = exercise,
                                position = index + 1,
                                groupLabel = verifiedGuideFor(exercise.name)?.primaryMuscles?.joinToString(" · ") { it.compactLabel }.orEmpty(),
                                summary = "${exercise.plannedSets}×${exercise.targetReps.ifBlank { "—" }} · ${displayWeight(exercise.targetWeight, state.settings.weightUnit).ifBlank { "без веса" }}",
                                onOpen = { openExercise(exercise.name) },
                                onMove = { exerciseActionsFor = exercise.id },
                                onMenu = { exerciseActionsFor = exercise.id },
                            )
                            DropdownMenu(
                                expanded = exerciseActionsFor == exercise.id,
                                onDismissRequest = { exerciseActionsFor = null },
                            ) {
                                DropdownMenuItem(text = { Text("Выше") }, enabled = index > 0, onClick = { vm.moveExercise(exercise.id, plan.id, -1); exerciseActionsFor = null })
                                DropdownMenuItem(text = { Text("Ниже") }, enabled = index < plan.exercises.lastIndex, onClick = { vm.moveExercise(exercise.id, plan.id, 1); exerciseActionsFor = null })
                                DropdownMenuItem(text = { Text("Изменить") }, onClick = { editExercise = plan to exercise; exerciseActionsFor = null })
                                DropdownMenuItem(text = { Text("Удалить") }, onClick = { removeExercise = plan to exercise; exerciseActionsFor = null })
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { openCatalog(plan.id) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Каталог") }
                        OutlinedButton(onClick = { addToPlan = plan; exerciseName = "" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Ввести вручную") }
                    }
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
            OutlinedTextField(sets, { sets = it.filter(Char::isDigit).take(2) }, modifier = Modifier.testTag("plan-exercise-sets"), label = { Text("Подходов в плане") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
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
    var showExchange by rememberSaveable { mutableStateOf(false) }
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
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiaryTheme.entries.forEach { theme ->
                        val selected = state.settings.theme == theme
                        val preview = paletteFor(theme)
                        Surface(
                            modifier = Modifier.semantics { this.selected = selected }.testTag("theme-${theme.name.lowercase(Locale.ROOT)}"),
                            onClick = { vm.setTheme(theme) },
                            shape = MaterialTheme.shapes.medium,
                            color = preview.background,
                            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) LocalPalette.current.accent else LocalPalette.current.muted.copy(alpha = .35f)),
                        ) {
                            Column(Modifier.width(112.dp).heightIn(min = 90.dp).padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.size(22.dp).background(preview.surface, CircleShape))
                                    Box(Modifier.size(22.dp).background(preview.accent, CircleShape))
                                    Box(Modifier.size(22.dp).background(preview.secondary, CircleShape))
                                    if (selected) Text("✓", color = preview.text, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { contentDescription = "Выбрано" })
                                }
                                Text(theme.title(), color = preview.text, style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 2)
                            }
                        }
                    }
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
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { showExchange = !showExchange }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Обмен и связи", color = LocalPalette.current.text, fontWeight = FontWeight.Bold); Text("Вторичные операции с данными", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall) }
                    Text(if (showExchange) "Скрыть" else "Открыть", color = LocalPalette.current.secondary)
                }
                if (showExchange) {
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
private fun HistoryScreen(history: List<TrainingSession>, unit: WeightUnit, targetId: String?, clearTarget: () -> Unit, modifier: Modifier, canSend: Boolean, resend: (String) -> Unit, openExercise: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf("Всё время") }
    val periodDays = when (period) { "7 дней" -> 7; "Месяц" -> 30; else -> null }
    val since = periodDays?.let { System.currentTimeMillis() - it * 86_400_000L }
    val filtered = history.filter { session ->
        val dateValue = session.endedAt ?: session.startedAt
        (since == null || dateValue >= since) && (query.isBlank() || session.planName.contains(query.trim(), true) || session.exercises.any { it.name.contains(query.trim(), true) })
    }
    ScreenColumn(modifier) {
        ScreenTitle("История", "Завершённые тренировки")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("7 дней", "Месяц", "Всё время").forEach { value -> FilterChip(selected = period == value, onClick = { period = value }, label = { Text(value) }) }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Поиск тренировок и упражнений") }, singleLine = true)
        if (history.isEmpty()) Text("История появится после завершения тренировки.", color = LocalPalette.current.muted)
        targetId?.let { id ->
            history.firstOrNull { it.id == id }?.let { session ->
                Text("Тренировка из графика", color = LocalPalette.current.secondary, fontWeight = FontWeight.Bold)
                SessionHistoryCard(session, unit, canSend, { resend(session.id) }, openExercise)
                TextButton(onClick = clearTarget, modifier = Modifier.heightIn(min = 48.dp)) { Text("Показать всю историю") }
            }
        }
        val visibleHistory = filtered.filter { it.id != targetId }
        if (history.isNotEmpty() && visibleHistory.isEmpty() && !(targetId != null && filtered.any { it.id == targetId })) {
            Text("По выбранному периоду и запросу тренировок нет.", color = LocalPalette.current.muted)
            TextButton(onClick = { query = ""; period = "Всё время" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Сбросить поиск и период") }
        }
        visibleHistory.groupBy { session -> SimpleDateFormat("LLLL yyyy", Locale.forLanguageTag("ru")).format(Date(session.endedAt ?: session.startedAt)).replaceFirstChar { it.titlecase(Locale.forLanguageTag("ru")) } }.forEach { (month, sessions) ->
            Text(month, color = LocalPalette.current.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            sessions.forEach { session -> SessionHistoryCard(session, unit, canSend, { resend(session.id) }, openExercise) }
        }
    }
}

@Composable
private fun SessionHistoryCard(session: TrainingSession, unit: WeightUnit, canSend: Boolean = false, resend: () -> Unit = {}, openExercise: (String) -> Unit = {}) {
    var expanded by rememberSaveable(session.id) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = LocalPalette.current.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(session.planName, fontWeight = FontWeight.Bold, color = LocalPalette.current.text)
                    Text("${date(session.endedAt ?: session.startedAt)} · ${session.completedSets} рабочих подходов", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                }
                Text(session.maxWeightKg?.let { formatWeight(it, unit) } ?: "—", color = LocalPalette.current.secondary, fontWeight = FontWeight.Bold)
            }
            if (expanded) {
                val duration = if (session.comment.contains("время и длительность неизвестны")) "длительность не указана" else "${((session.endedAt ?: session.startedAt) - session.startedAt).coerceAtLeast(0) / 60000L} мин"
                Text("$duration · ${session.exercises.size} упражнений", color = LocalPalette.current.muted, style = MaterialTheme.typography.bodySmall)
                if (session.comment.isNotBlank()) Text(session.comment, color = LocalPalette.current.secondary)
                if (canSend) OutlinedButton(onClick = resend, modifier = Modifier.fillMaxWidth()) { Text("Повторно подготовить PDF и пакет") }
                session.exercises.forEach { exercise ->
                    val sets = exercise.sets.filter { it.done || it.skipped }.joinToString(" · ") {
                        when {
                            it.skipped -> "пропуск"
                            else -> "${if (it.kind == SetKind.WARMUP) "разминка " else ""}${displayWeight(it.weight, unit).ifBlank { "—" }}×${it.reps.ifBlank { "—" }}"
                        }
                    }
                    Text("${exercise.name}: ${sets.ifBlank { "нет выполненных подходов" }}", Modifier.clickable { openExercise(exercise.name) }.padding(vertical = 3.dp), color = LocalPalette.current.text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}


private fun russianCount(count: Int, one: String, few: String, many: String): String {
    val remainder100 = count % 100
    val form = when {
        remainder100 in 11..14 -> many
        count % 10 == 1 -> one
        count % 10 in 2..4 -> few
        else -> many
    }
    return "$count $form"
}

private fun date(epoch: Long): String = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru")).format(Date(epoch))
private fun kg(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
private fun WeightUnit.label() = if (this == WeightUnit.KG) "кг" else "lb"
private fun displayWeight(storedKg: String, unit: WeightUnit): String {
    val value = storedKg.replace(',', '.').toDoubleOrNull() ?: return storedKg
    return kg(fromKilograms(value, unit))
}
private fun formatWeight(storedKg: Double, unit: WeightUnit) = "${kg(fromKilograms(storedKg, unit))} ${unit.label()}"
private fun canonicalWeight(displayValue: String, unit: WeightUnit) = parseWeightToKilograms(displayValue, unit)
