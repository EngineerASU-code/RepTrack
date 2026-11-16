package app.reptrack

import android.graphics.Bitmap
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
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

/** Acceptance captures use the packaged debug app with newly created synthetic trainee profiles. */
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
        val originalFontScale = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("settings get system font_scale")
        ).bufferedReader().use { it.readText().trim() }.takeIf { it.toFloatOrNull() != null } ?: "1.0"
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
                if (scale == "2.0") {
                    val screenWidth = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
                    val action = compose.onAllNodesWithTag("profile-open-diary").onFirst().performScrollTo().assertIsDisplayed().fetchSemanticsNode()
                    org.junit.Assert.assertTrue(
                        "Large-font profile action is too narrow to read (${action.boundsInRoot.width} px; screen $screenWidth px)",
                        action.boundsInRoot.width >= screenWidth * 0.7f,
                    )
                }
                capture("C15-long-profile-font-$suffix")

                compose.onNodeWithTag("nav-plan").performClick()
                compose.onNodeWithText(longExercise, substring = true).performScrollTo().performClick()
                compose.onNodeWithTag("exercise-screen-title").assertIsDisplayed().assertTextEquals(longExercise)
                capture("C15-long-exercise-font-$suffix")
                compose.onNodeWithTag("exercise-back").performClick()
            }
        } finally {
            scenario.close()
            instrumentation.uiAutomation.executeShellCommand("settings put system font_scale $originalFontScale").close()
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
    fun captureC12ProgressWithNoDataFromApp() = withSyntheticProfile("C12-empty") {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav-progress").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("nav-progress").performClick()
            compose.onNodeWithText("Нет записей с нагрузкой").assertIsDisplayed()
            compose.onNodeWithText("Выберите упражнение").assertIsDisplayed()
            compose.onNodeWithTag("progress-chart").assertDoesNotExist()
            capture("C12-progress-no-data")
        } finally {
            scenario.close()
        }
    }

    @Test
    fun captureC12ProgressWithOnePointFromApp() = withSyntheticProfile("C12-one-point") { repository ->
        repository.createPlan("Синтетическая проверка прогресса")
        val plan = repository.load().plans.first { it.name == "Синтетическая проверка прогресса" }
        repository.addExercise(plan.id, "Жим штанги средним хватом")
        val exercise = repository.load().plans.first { it.id == plan.id }.exercises.single()
        repository.updateExercise(exercise.copy(restSeconds = 0))
        val sessionId = repository.start(plan.id)
        val set = repository.load().activeSession?.exercises?.single()?.sets?.first()
            ?: error("Synthetic progress set was not created")
        repository.updateSet(set.id, "60", "8")
        repository.toggleSet(set.copy(weight = "60", reps = "8"))
        repository.finish(sessionId)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav-progress").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("nav-progress").performClick()
            compose.onNodeWithText("Жим штанги средним хватом").assertIsDisplayed()
            compose.onNodeWithTag("progress-chart").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("1 тренировка", substring = true).assertExists()
            capture("C12-progress-one-point")
        } finally {
            scenario.close()
        }
    }

    @Test
    fun captureC08FinishedRestFromApp() = withSyntheticProfile("C08-finished-rest") { repository ->
        repository.createPlan("Синтетическая проверка таймера")
        val plan = repository.load().plans.first { it.name == "Синтетическая проверка таймера" }
        repository.addExercise(plan.id, "Жим штанги средним хватом")
        val exercise = repository.load().plans.first { it.id == plan.id }.exercises.single()
        repository.updateExercise(exercise.copy(restSeconds = 10))
        var sessionId: String? = null
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            sessionId = repository.start(plan.id)
            org.junit.Assert.assertEquals(sessionId, repository.load().activeSession?.id)
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav-gym").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("nav-gym").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("finish-workout").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Отметить подход 1").performScrollTo().performClick()
            compose.waitUntil(5_000) {
                repository.load().activeSession?.exercises?.singleOrNull()?.sets?.firstOrNull()?.restUntil != null
            }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Отдых", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            val restSet = repository.load().activeSession?.exercises?.single()?.sets?.first()
                ?: error("Synthetic rest set disappeared")
            org.junit.Assert.assertEquals(10, restSet.restSeconds)
            val restDeadline = restSet.restUntil ?: error("Rest deadline was not saved")
            compose.waitUntil(13_000) { restDeadline <= System.currentTimeMillis() }
            compose.waitUntil(4_000) { compose.onAllNodesWithTag("rest-complete").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Отдых завершён").assertIsDisplayed()
            capture("C08-rest-finished")
        } finally {
            scenario?.close()
            sessionId?.let(repository::cancel)
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
        val originalRestTimerEnabled = readRestTimerEnabled(context)
        val bootstrap = DiaryRepository(context)
        val profiles = bootstrap.load().profiles
        val preservedProfileId = profiles.firstOrNull { it.id == original.first && !it.name.startsWith("Visual acceptance · ") }?.id
            ?: profiles.firstOrNull { it.kind == ProfileKind.PERSONAL }?.id
            ?: personalProfileId
        val previousSyntheticIds = profiles.filter { it.name.startsWith("Visual acceptance · ") }.map { it.id }
        bootstrap.close()
        deleteSyntheticProfiles(context, previousSyntheticIds)

        val repository = DiaryRepository(context)
        var syntheticProfileId: String? = null
        try {
            repository.setActiveProfile(preservedProfileId)
            repository.createTraineeProfile("Visual acceptance · $name")
            syntheticProfileId = repository.load().activeProfile?.id
                ?: error("Synthetic acceptance profile was not selected")
            repository.setTheme(DiaryTheme.PURPLE_PRO)
            repository.setRestTimerEnabled(true)
            run(repository)
        } finally {
            repository.setActiveProfile(preservedProfileId)
            repository.setTheme(original.second)
            repository.setRestTimerEnabled(originalRestTimerEnabled)
            repository.close()
            syntheticProfileId?.let { deleteSyntheticProfiles(context, listOf(it)) }
        }
    }

    private fun readRestTimerEnabled(context: Context): Boolean {
        val databaseFile = context.getDatabasePath("reptrack.db")
        if (!databaseFile.exists()) return true
        return SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT value FROM app_settings WHERE key='rest_timer_enabled'", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).toBooleanStrictOrNull() ?: true else true
            }
        }
    }

    private fun deleteSyntheticProfiles(context: Context, profileIds: List<String>) {
        val databaseFile = context.getDatabasePath("reptrack.db")
        if (profileIds.isEmpty() || !databaseFile.exists()) return
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.setForeignKeyConstraintsEnabled(true)
            db.beginTransaction()
            try {
                profileIds.forEach { id ->
                    db.delete("sessions", "owner_id=?", arrayOf(id))
                    db.delete("program_assignments", "profile_id=?", arrayOf(id))
                    db.delete("imported_events", "owner_id=?", arrayOf(id))
                    db.delete("plans", "owner_id=?", arrayOf(id))
                    db.delete("profiles", "id=? AND name LIKE 'Visual acceptance · %'", arrayOf(id))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
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
