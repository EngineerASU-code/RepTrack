package app.reptrack

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.view.WindowInsets
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
                compose.onAllNodesWithTag("start-workout").fetchSemanticsNodes().isNotEmpty()
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
                        val insets = activity.window.decorView.rootWindowInsets
                        insets?.isVisible(WindowInsets.Type.ime()) == true &&
                            insets.getInsets(WindowInsets.Type.ime()).bottom > 0
                    } else {
                        false
                    }
                }
                imeVisible
            }
            compose.waitUntil(5_000) {
                val row = compose.onAllNodesWithTag("workout-set-row").onLast().fetchSemanticsNode()
                val actionTop = finishButton.fetchSemanticsNode().boundsInRoot.top
                row.boundsInRoot.height >= row.size.height - 1f && row.boundsInRoot.bottom <= actionTop
            }
            capture("C04-workout-ime")
            val settledRow = compose.onAllNodesWithTag("workout-set-row").onLast().fetchSemanticsNode()
            val settledBounds = settledRow.boundsInRoot
            assertTrue(
                "Last set row is clipped above the visible viewport (visible height=${settledBounds.height}, full height=${settledRow.size.height})",
                settledBounds.height >= settledRow.size.height - 1f,
            )
            val actionTop = finishButton.fetchSemanticsNode().boundsInRoot.top
            val rowBottom = settledBounds.bottom
            assertTrue(
                "Last set row overlaps the pinned finish action (row bottom=$rowBottom, action top=$actionTop)",
                rowBottom <= actionTop,
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
