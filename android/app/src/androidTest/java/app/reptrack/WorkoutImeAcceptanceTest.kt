package app.reptrack

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.view.WindowInsets
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** C04/S03: the last workout field and pinned finish action remain usable with the numeric IME open. */
@RunWith(AndroidJUnit4::class)
class WorkoutImeAcceptanceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun captureWorkoutImeWithPinnedFinishAction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val (originalProfile, originalTheme) = readUiPreferences(context)
        val repository = DiaryRepository(context)
        var createdSession: String? = null
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            repository.createDemoProfile()
            repository.setTheme(DiaryTheme.PURPLE_PRO)
            assertTrue("The isolated demo profile already has an active workout", repository.load().activeSession == null)

            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("Демо", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("start-workout").performScrollTo().performClick()
            compose.waitUntil(10_000) { repository.load().activeSession != null }
            createdSession = repository.load().activeSession?.id
                ?: error("Demo workout did not start")

            val lastRepsField = compose.onAllNodesWithContentDescription("Повторы").onLast()
            lastRepsField.performScrollTo().performClick()
            lastRepsField.assertIsDisplayed()
            val finishButton = compose.onNodeWithTag("finish-workout")
            finishButton.assertIsDisplayed()
            compose.waitUntil(5_000) {
                var imeVisible = false
                scenario?.onActivity { activity ->
                    imeVisible = if (android.os.Build.VERSION.SDK_INT >= 30) {
                        activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
                    } else false
                }
                imeVisible
            }
            capture("C04-workout-ime")
            val fieldBottom = lastRepsField.fetchSemanticsNode().boundsInRoot.bottom
            val actionTop = finishButton.fetchSemanticsNode().boundsInRoot.top
            assertTrue(
                "Last reps field overlaps the pinned finish action (field bottom=$fieldBottom, action top=$actionTop)",
                fieldBottom <= actionTop,
            )
        } finally {
            scenario?.close()
            createdSession?.let(repository::cancel)
            repository.setActiveProfile(originalProfile)
            repository.setTheme(originalTheme)
            repository.close()
        }
    }

    /** Read only UI preferences; do not load or inspect the active profile's workout history. */
    private fun readUiPreferences(context: android.content.Context): Pair<String, DiaryTheme> {
        val databaseFile = context.getDatabasePath("reptrack.db")
        if (!databaseFile.exists()) return personalProfileId to DiaryTheme.PURPLE_PRO
        return SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val settings = mutableMapOf<String, String>()
            db.rawQuery(
                "SELECT key,value FROM app_settings WHERE key IN ('active_profile_id','theme')",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) settings[cursor.getString(0)] = cursor.getString(1)
            }
            (settings["active_profile_id"] ?: personalProfileId) to
                runCatching { DiaryTheme.valueOf(settings["theme"] ?: "PURPLE_PRO") }
                    .getOrDefault(DiaryTheme.PURPLE_PRO)
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(250)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.getExternalFilesDir(null), "visual-acceptance").apply { mkdirs() }
        val configuration = context.resources.configuration
        val metadata =
            "Android=${android.os.Build.VERSION.SDK_INT}\n" +
                "viewport=${configuration.screenWidthDp}x${configuration.screenHeightDp}dp\n" +
                "density=${context.resources.displayMetrics.density}\nfontScale=${configuration.fontScale}\n" +
                "theme=PURPLE_PRO\nprofile=isolated demo\nvariant=debug\n" +
                "commit=${InstrumentationRegistry.getArguments().getString("gitCommit") ?: "not-provided"}\n" +
                "worktree=${InstrumentationRegistry.getArguments().getString("worktree") ?: "not-provided"}\n" +
                "capture=last reps field with numeric IME and pinned finish action\n"
        File(directory, "$name-metadata.txt").writeText(metadata)
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        File(directory, "$name-device.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
