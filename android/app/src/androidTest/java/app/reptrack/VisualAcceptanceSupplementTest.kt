package app.reptrack

import android.graphics.Bitmap
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Supplemental captures: C12 uses an in-memory snapshot; Activity cases use new synthetic trainee profiles. */
@RunWith(AndroidJUnit4::class)
class VisualAcceptanceSupplementTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun captureC12ProgressWithNoData() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides paletteFor(DiaryTheme.PURPLE_PRO)) {
                MaterialTheme(colorScheme = paletteFor(DiaryTheme.PURPLE_PRO).colorScheme()) {
                    Surface(color = LocalPalette.current.background) {
                        ProgressScreen(DiarySnapshot(loading = false)) {}
                    }
                }
            }
        }

        compose.onNodeWithText("Нет записей с нагрузкой").assertIsDisplayed()
        compose.onNodeWithText("Выберите упражнение").assertIsDisplayed()
        compose.onNodeWithTag("progress-chart").assertDoesNotExist()
        capture("C12-progress-no-data")
    }

    @Test
    fun captureC12ProgressWithOnePoint() {
        val now = System.currentTimeMillis()
        val snapshot = DiarySnapshot(
            loading = false,
            history = listOf(
                TrainingSession(
                    id = "visual-c12-one-point",
                    planId = null,
                    planName = "Синтетическая проверка",
                    status = SessionStatus.COMPLETED,
                    startedAt = now - 60_000,
                    endedAt = now,
                    exercises = listOf(
                        SessionExercise(
                            id = "visual-c12-bench",
                            name = "Жим штанги",
                            position = 0,
                            skipped = false,
                            tracksLoad = true,
                            sets = listOf(
                                TrainingSet(
                                    id = "visual-c12-set",
                                    position = 0,
                                    weight = "60",
                                    reps = "8",
                                    kind = SetKind.WORK,
                                    done = true,
                                    skipped = false,
                                    restSeconds = 90,
                                    restUntil = null,
                                    restRemaining = 0,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides paletteFor(DiaryTheme.PURPLE_PRO)) {
                MaterialTheme(colorScheme = paletteFor(DiaryTheme.PURPLE_PRO).colorScheme()) {
                    Surface(color = LocalPalette.current.background) {
                        ProgressScreen(snapshot) {}
                    }
                }
            }
        }

        compose.onNodeWithText("Жим штанги").assertIsDisplayed()
        compose.onNodeWithContentDescription("График Максимальный вес. 1 тренировок.", substring = true).assertExists()
        capture("C12-progress-one-point")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(250)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "visual-acceptance").apply { mkdirs() }
        val configuration = context.resources.configuration
        val metadata =
            "Android=${android.os.Build.VERSION.SDK_INT}\n" +
                "viewport=${configuration.screenWidthDp}x${configuration.screenHeightDp}dp\n" +
                "density=${context.resources.displayMetrics.density}\nfontScale=${configuration.fontScale}\n" +
                "theme=PURPLE_PRO\nprofile=synthetic in-memory snapshot\nvariant=debug\n" +
                "commit=${InstrumentationRegistry.getArguments().getString("gitCommit") ?: "not-provided"}\n" +
                "worktree=${InstrumentationRegistry.getArguments().getString("worktree") ?: "not-provided"}\n" +
                "capture=ProgressScreen Compose content\n"
        File(directory, "device.txt").writeText(metadata)
        File(directory, "$name-metadata.txt").writeText(metadata)
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        File(directory, "$name-device.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

/** Activity captures use only newly created synthetic trainee profiles. */
@RunWith(AndroidJUnit4::class)
class VisualAcceptanceActivitySupplementTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun captureC15LongNamesAtLargeFont() = withSyntheticProfile("C15") { repository ->
        val longProfileBase = "Демо спортсмен с длинным именем для адаптивности"
        val longPlan = "Длинная программа для проверки переноса названия"
        val longExercise = "Тяга верхнего блока широким хватом в тренажёре с длинным названием"
        val activeProfile = repository.load().activeProfile ?: error("Synthetic profile is missing")
        val longProfile = "$longProfileBase ${activeProfile.id.take(6)}"
        repository.renameProfile(activeProfile.id, longProfile)
        repository.createPlan(longPlan)
        val plan = repository.load().plans.first { it.name == longPlan }
        repository.setPlanInCycle(plan.id, true)
        repository.addExercise(plan.id, longExercise)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("nav-home").fetchSemanticsNodes().isNotEmpty()
            }
            listOf("1.3", "2.0").forEach { scale ->
                instrumentation.uiAutomation.executeShellCommand("settings put system font_scale $scale").close()
                compose.waitUntil(10_000) { instrumentation.targetContext.resources.configuration.fontScale >= scale.toFloat() }
                val suffix = scale.replace('.', '-')
                compose.onNodeWithTag("open-profiles").performClick()
                compose.onAllNodesWithText(longProfile, substring = true).onFirst().performScrollTo().assertIsDisplayed()
                capture("C15-long-profile-font-$suffix")

                compose.onNodeWithTag("nav-plan").performClick()
                compose.onNodeWithText(longExercise, substring = true).performScrollTo().performClick()
                compose.onNodeWithText(longExercise, substring = true).assertIsDisplayed()
                capture("C15-long-exercise-font-$suffix")
                compose.onNodeWithTag("exercise-back").performClick()
            }
        } finally {
            scenario.close()
            instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 1.0").close()
        }
    }

    @Test
    fun captureC02HomeWithEmptyTraineeProfile() = withSyntheticProfile("C02") { repository ->
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("nav-home").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("История появится после первой тренировки").assertIsDisplayed()
            compose.onNodeWithText("Пока нет записей").assertIsDisplayed()
            capture("C02-home-empty")
        } finally {
            scenario.close()
        }
    }

    @Test
    fun captureC10CatalogSearchSelectionAndNoResults() = withSyntheticProfile("C10") { repository ->
        repository.createPlan("Фикстура каталога")
        val plan = repository.load().plans.first { it.name == "Фикстура каталога" }
        repository.addExercise(plan.id, "Жим ногами")
        repository.createPlan("Источник каталога")
        val sourcePlan = repository.load().plans.first { it.name == "Источник каталога" }
        repository.addExercise(sourcePlan.id, "Приседания со штангой")

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("nav-home").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("nav-gym").performClick()
            // GymScreen opens the general catalog through this button.
            compose.onNodeWithText("Открыть каталог упражнений").performScrollTo().performClick()
            compose.onNodeWithText("Каталог упражнений").assertIsDisplayed()
            val search = compose.onNodeWithText("Поиск упражнения")
            search.performClick()
            search.performTextInput("Жим ногами")
            compose.onAllNodesWithText("Жим ногами").assertCountEquals(2)
            capture("C10-catalog-search")

            compose.onNodeWithText("Поиск упражнения").performClick()
            compose.onNodeWithText("Поиск упражнения").performTextClearance()
            compose.onNodeWithText("Поиск упражнения").performTextInput("нет такого упражнения")
            compose.onNodeWithText("Поиск и фильтры ничего не нашли.").assertIsDisplayed()
            capture("C10-catalog-no-results")

            compose.onNodeWithText("Поиск упражнения").performClick()
            compose.onNodeWithText("Поиск упражнения").performTextClearance()
            compose.onNodeWithText("Поиск упражнения").performTextInput("Жим ногами")
            compose.onAllNodesWithText("Жим ногами").onLast().performClick()
            compose.onNodeWithTag("tab-technique").performClick()
            compose.onNodeWithText("Техника выполнения").assertIsDisplayed()
            capture("C10-catalog-selected-exercise")

            compose.onNodeWithTag("exercise-back").performClick()
            compose.onNodeWithTag("catalog-back").performClick()
            compose.onNodeWithTag("nav-plan").performClick()
            compose.onNodeWithText("Каталог").performScrollTo().performClick()
            compose.onNodeWithText("Каталог упражнений").assertIsDisplayed()
            compose.onNodeWithText("Поиск упражнения").performClick()
            compose.onNodeWithText("Поиск упражнения").performTextInput("Приседания со штангой")
            compose.onAllNodesWithText("Приседания со штангой").onLast().performClick()
            compose.onNodeWithText("Добавить выбранные · 1").assertIsDisplayed()
            capture("C10-catalog-plan-select")
            compose.onNodeWithText("Детали").performClick()
            compose.onNodeWithTag("tab-technique").performClick()
            compose.onNodeWithText("Техника выполнения").assertIsDisplayed()
            val targetExercises = repository.load().plans.first { it.name == "Фикстура каталога" }.exercises
            check(targetExercises.map { it.name } == listOf("Жим ногами")) {
                "Opening catalog details must not add the selected exercise to the plan"
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun captureC13SyntheticHistoryAndCompletedSessionDetail() = withSyntheticProfile("C13") { repository ->
        repository.createPlan("Фикстура истории")
        val plan = repository.load().plans.first { it.name == "Фикстура истории" }
        repository.addExercise(plan.id, "Жим штанги")
        val startedId = repository.start(plan.id)
        val active = repository.load().activeSession ?: error("Synthetic session was not created")
        val firstSet = active.exercises.first().sets.first()
        repository.updateSet(firstSet.id, "60", "8")
        repository.toggleSet(firstSet.copy(weight = "60", reps = "8"))
        repository.updateComment(startedId, "Синтетическая запись для visual acceptance")
        repository.finish(startedId)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("nav-home").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("nav-history").performClick()
            compose.onNodeWithText("Фикстура истории").assertIsDisplayed()
            capture("C13-history")
            compose.onNodeWithText("Фикстура истории").performClick()
            compose.onNodeWithText("Синтетическая запись для visual acceptance").assertIsDisplayed()
            compose.onNodeWithText("Жим штанги: 60×8").assertIsDisplayed()
            capture("C13-history-session-detail")
        } finally {
            scenario.close()
        }
    }

    private fun withSyntheticProfile(name: String, run: (DiaryRepository) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = readUiPreferences(context)
        val repository = DiaryRepository(context)
        try {
            repository.createTraineeProfile("Visual acceptance · $name · ${System.currentTimeMillis()}")
            repository.setTheme(DiaryTheme.PURPLE_PRO)
            run(repository)
        } finally {
            repository.setActiveProfile(original.first)
            repository.setTheme(original.second)
            repository.close()
        }
    }

    private fun readUiPreferences(context: Context): Pair<String, DiaryTheme> {
        val databaseFile = context.getDatabasePath("reptrack.db")
        if (!databaseFile.exists()) return personalProfileId to DiaryTheme.PURPLE_PRO
        return SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val settings = mutableMapOf<String, String>()
            db.rawQuery("SELECT key,value FROM app_settings WHERE key IN ('active_profile_id','theme')", null).use { cursor ->
                while (cursor.moveToNext()) settings[cursor.getString(0)] = cursor.getString(1)
            }
            (settings["active_profile_id"] ?: personalProfileId) to
                (runCatching { DiaryTheme.valueOf(settings["theme"] ?: "PURPLE_PRO") }.getOrDefault(DiaryTheme.PURPLE_PRO))
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(250)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "visual-acceptance").apply { mkdirs() }
        val configuration = context.resources.configuration
        val metadata =
            "Android=${android.os.Build.VERSION.SDK_INT}\n" +
                "viewport=${configuration.screenWidthDp}x${configuration.screenHeightDp}dp\n" +
                "density=${context.resources.displayMetrics.density}\nfontScale=${configuration.fontScale}\n" +
                "theme=PURPLE_PRO\nprofile=isolated synthetic trainee\nvariant=debug\n" +
                "commit=${InstrumentationRegistry.getArguments().getString("gitCommit") ?: "not-provided"}\n" +
                "worktree=${InstrumentationRegistry.getArguments().getString("worktree") ?: "not-provided"}\n" +
                "capture=MainActivity Compose and full device display\n"
        File(directory, "device.txt").writeText(metadata)
        File(directory, "$name-metadata.txt").writeText(metadata)
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        File(directory, "$name-device.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
