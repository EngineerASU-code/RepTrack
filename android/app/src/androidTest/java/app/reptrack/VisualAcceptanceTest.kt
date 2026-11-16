package app.reptrack

import android.graphics.Bitmap
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Repeatable APK captures, isolated from the user's selected profile and theme. */
@RunWith(AndroidJUnit4::class)
class VisualAcceptanceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun captureRootScreensInPurplePro() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = readUiPreferences(context)
        val originalRestTimerEnabled = readRestTimerEnabled(context)
        val repository = DiaryRepository(context)
        var createdSession: String? = null
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            repository.createDemoProfile()
            repository.setTheme(DiaryTheme.PURPLE_PRO)
            repository.setRestTimerEnabled(true)
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("Демо", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            val allProgressBounds = compose.onNodeWithTag("home-progress-all").fetchSemanticsNode().boundsInRoot
            val minButtonWidth = 48f * context.resources.displayMetrics.density
            org.junit.Assert.assertTrue(
                "Home progress action is too narrow for its label (${allProgressBounds.width} px; expected at least $minButtonWidth px)",
                allProgressBounds.width >= minButtonWidth,
            )
            capture("C01-home")
            compose.onNodeWithTag("home-progress-all").performScrollTo().assertIsDisplayed()
            capture("C01-home-bottom")
            listOf("gym", "plan", "progress", "history").forEach { route ->
                compose.onNodeWithTag("nav-$route").performClick()
                compose.waitForIdle()
                capture("root-$route")
                if (route == "plan") {
                    capture("C09-plan")
                    compose.onAllNodesWithContentDescription("Действия для упражнения", substring = true).onFirst().performClick()
                    compose.onNodeWithText("Изменить").performClick()
                    compose.onNodeWithText("Подходов в плане").assertIsDisplayed()
                    capture("C09-plan-editor")
                    val plannedSets = repository.load().nextPlan?.exercises?.firstOrNull()?.plannedSets
                        ?: error("Demo plan has no exercise to edit")
                    val draftSets = plannedSets % 9 + 1
                    compose.onNodeWithTag("plan-exercise-sets").performTextClearance()
                    compose.onNodeWithTag("plan-exercise-sets").performTextInput(draftSets.toString())
                    val editedSets = compose.onNodeWithTag("plan-exercise-sets").fetchSemanticsNode()
                        .config[SemanticsProperties.EditableText].text
                    org.junit.Assert.assertEquals(draftSets.toString(), editedSets)
                    capture("C09-plan-editor-draft")
                    compose.onNodeWithText("Назад").performClick()
                }
                if (route == "progress") {
                    compose.onNodeWithText("1 мес.").assertIsSelected()
                    compose.onNodeWithTag("progress-chart").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText("Подходы:", substring = true).assertExists()
                    capture("C11-chart")
                    compose.onNodeWithText("История тренировок").performScrollTo().assertIsDisplayed()
                    compose.onAllNodes(hasText("×", substring = true)).onLast().performScrollTo().assertIsDisplayed()
                    capture("C11-chart-bottom")
                }
            }
            compose.onNodeWithTag("open-settings").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("theme-lime_energy").performScrollTo().performClick()
            waitForSelection("theme-lime_energy")
            compose.onNodeWithTag("nav-home").performClick()
            compose.onNodeWithTag("open-settings").performClick()
            waitForSelection("theme-lime_energy")
            compose.onNodeWithTag("theme-purple_pro").performScrollTo().performClick()
            waitForSelection("theme-purple_pro")
            capture("C14-settings")
            compose.onNodeWithText("Импортировать планы").performScrollTo().assertIsDisplayed()
            capture("C14-settings-bottom")
            compose.onNodeWithTag("nav-home").performClick()
            org.junit.Assert.assertNull("Demo profile already has an active workout; leave it intact", repository.load().activeSession)
            compose.onNodeWithTag("start-workout").performScrollTo().performClick()
            compose.waitUntil(10_000) { repository.load().activeSession != null }
            createdSession = repository.load().activeSession?.id
            compose.onNodeWithTag("finish-workout").assertIsDisplayed()
            compose.onNodeWithText("Грудь").assertIsDisplayed()
            capture("C03-workout")
            val finishButton = compose.onNodeWithTag("finish-workout")
            val lastExerciseCard = compose.onAllNodesWithTag("workout-exercise-card").onLast()
            lastExerciseCard.performScrollTo().assertIsDisplayed()
            val lastCard = lastExerciseCard.fetchSemanticsNode()
            val lastCardBounds = lastCard.boundsInRoot
            val finishTop = finishButton.fetchSemanticsNode().boundsInRoot.top
            org.junit.Assert.assertTrue(
                "Last collapsed exercise is clipped (visible height=${lastCardBounds.height}, full height=${lastCard.size.height})",
                lastCardBounds.height >= lastCard.size.height - 1f,
            )
            org.junit.Assert.assertTrue(
                "Last exercise overlaps the pinned finish action (card bottom=${lastCardBounds.bottom}, action top=$finishTop)",
                lastCardBounds.bottom <= finishTop,
            )
            capture("C03-workout-bottom")
            compose.onNodeWithContentDescription("Отметить подход 1").performScrollTo().performClick()
            compose.onNodeWithText("Отдых", substring = true, useUnmergedTree = true).assertExists()
            capture("C08-rest-running")
            compose.onNodeWithContentDescription("Приостановить отдых", useUnmergedTree = true).performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Продолжить отдых", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Продолжить отдых", useUnmergedTree = true).assertExists()
            capture("C08-rest-paused")
            compose.onNodeWithContentDescription("Продолжить отдых", useUnmergedTree = true).performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Приостановить отдых", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Пропустить отдых", useUnmergedTree = true).performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Отдых", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("Отдых", substring = true, useUnmergedTree = true).assertDoesNotExist()
            compose.onAllNodesWithTag("exercise-details").onFirst().performScrollTo().performClick()
            listOf("sets", "technique", "muscles").forEach { section ->
                compose.onNodeWithTag("tab-$section").performClick()
                if (section == "technique") {
                    compose.onNodeWithText("Техника выполнения").assertIsDisplayed()
                    compose.onNodeWithContentDescription("Иллюстрация 1 упражнения «Жим штанги средним хватом»; техника по изображению не сертифицирована").assertIsDisplayed()
                    capture("C06-technique")
                    compose.onNodeWithText("Источник: NASM · Barbell Bench Press").performScrollTo().assertIsDisplayed()
                    capture("C06-technique-bottom")
                }
                if (section == "muscles") {
                    compose.onNodeWithTag("muscle-map").assertExists()
                    compose.onNodeWithText("Спереди").assertIsDisplayed()
                    compose.onNodeWithText("Сзади").assertIsDisplayed()
                    compose.onNodeWithText("Большая грудная мышца").assertIsDisplayed()
                    compose.onNodeWithText("Мышцы: Free Exercise DB · Barbell Bench Press - Medium Grip").assertExists()
                    compose.onNodeWithText("Анатомическая иллюстрация: Surya Mouly / muscle_mapper · MIT").assertExists()
                    capture("C07-muscles")
                    compose.onNodeWithText("Анатомическая иллюстрация: Surya Mouly / muscle_mapper · MIT").performScrollTo().assertIsDisplayed()
                    capture("C07-muscles-bottom")
                }
                if (section == "sets") {
                    capture("C05-approaches")
                    compose.onNodeWithText("Добавить подход").performScrollTo().assertIsDisplayed()
                    capture("C05-approaches-bottom")
                }
                if (section != "technique") capture("exercise-$section")
            }
            compose.onNodeWithTag("exercise-back").performClick()
            compose.onNodeWithTag("finish-workout").assertIsDisplayed()
            org.junit.Assert.assertEquals(createdSession, repository.load().activeSession?.id)
        } finally {
            scenario?.close()
            createdSession?.let(repository::cancel)
            repository.setActiveProfile(original.first)
            repository.setTheme(original.second)
            repository.setRestTimerEnabled(originalRestTimerEnabled)
            repository.close()
        }
    }

    /** Save and restore only UI preferences; test data is created in the isolated demo profile. */
    private fun readUiPreferences(context: android.content.Context): Pair<String, DiaryTheme> {
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

    private fun readRestTimerEnabled(context: android.content.Context): Boolean {
        val databaseFile = context.getDatabasePath("reptrack.db")
        if (!databaseFile.exists()) return true
        return SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT value FROM app_settings WHERE key='rest_timer_enabled'", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).toBooleanStrictOrNull() ?: true else true
            }
        }
    }

    private fun waitForSelection(tag: String) {
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag(tag).assertIsSelected() }.isSuccess
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
                "theme=PURPLE_PRO\nprofile=isolated demo\nvariant=debug\n" +
                "commit=${InstrumentationRegistry.getArguments().getString("gitCommit") ?: "not-provided"}\n" +
                "worktree=${InstrumentationRegistry.getArguments().getString("worktree") ?: "not-provided"}\n" +
                "capture=Compose root and full device display\n"
        File(directory, "$name-metadata.txt").writeText(metadata)
        File(directory, "device.txt").writeText(metadata)
        File(directory, "$name.png").outputStream().use {
            compose.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        File(directory, "$name-device.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
