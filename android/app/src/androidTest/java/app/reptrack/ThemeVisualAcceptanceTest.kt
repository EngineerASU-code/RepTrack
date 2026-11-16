package app.reptrack

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Core visual coverage for every palette, kept on synthetic demo data. */
@RunWith(AndroidJUnit4::class)
class ThemeVisualAcceptanceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun coreScreensRemainUsableAcrossPalettes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = readUiPreferences(context)
        val originalRestTimerEnabled = readRestTimerEnabled(context)
        val repository = DiaryRepository(context)
        var scenario: ActivityScenario<MainActivity>? = null
        var demoProfileId: String? = null
        try {
            repository.createDemoProfile()
            demoProfileId = repository.load().activeProfileId
            repository.setRestTimerEnabled(true)

            DiaryTheme.entries.forEach { theme ->
                repository.setTheme(theme)
                scenario = ActivityScenario.launch(MainActivity::class.java)
                waitForHome()
                compose.onNodeWithTag("nav-home").assertIsSelected()
                capture("C01-home-${theme.name.lowercase()}", theme)

                compose.onNodeWithTag("start-workout").performScrollTo().performClick()
                compose.waitUntil(10_000) { repository.load().activeSession != null }
                val session = repository.load().activeSession ?: error("Demo workout did not start")
                compose.onNodeWithTag("finish-workout").assertIsDisplayed()
                capture("C03-workout-${theme.name.lowercase()}", theme)

                val lastCardNode = compose.onAllNodesWithTag("workout-exercise-card").onLast()
                lastCardNode.performScrollTo().assertIsDisplayed()
                val lastCard = lastCardNode.fetchSemanticsNode()
                val finishTop = compose.onNodeWithTag("finish-workout").fetchSemanticsNode().boundsInRoot.top
                org.junit.Assert.assertTrue(lastCard.boundsInRoot.height >= lastCard.size.height - 1f)
                org.junit.Assert.assertTrue(lastCard.boundsInRoot.bottom <= finishTop)
                capture("C03-workout-bottom-${theme.name.lowercase()}", theme)

                scenario?.close()
                scenario = null
                repository.cancel(session.id)
                repository.setTheme(theme)

                scenario = ActivityScenario.launch(MainActivity::class.java)
                waitForHome()
                compose.onNodeWithTag("nav-progress").performClick()
                compose.onNodeWithTag("progress-chart").performScrollTo().assertIsDisplayed()
                capture("C11-progress-${theme.name.lowercase()}", theme)
                compose.onNodeWithText("История тренировок").performScrollTo().assertIsDisplayed()
                capture("C11-progress-bottom-${theme.name.lowercase()}", theme)
                scenario?.close()
                scenario = null
            }
        } finally {
            scenario?.close()
            val snapshot = repository.load()
            if (snapshot.activeProfileId == demoProfileId) snapshot.activeSession?.id?.let(repository::cancel)
            repository.setActiveProfile(original.first)
            repository.setTheme(original.second)
            repository.setRestTimerEnabled(originalRestTimerEnabled)
            repository.close()
        }
    }

    private fun waitForHome() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("nav-home").fetchSemanticsNodes().isNotEmpty()
        }
    }

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
                cursor.moveToFirst() && cursor.getString(0).toBooleanStrictOrNull() != false
            }
        }
    }

    private fun capture(name: String, theme: DiaryTheme) {
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
                "theme=${theme.name}\nprofile=isolated demo\nvariant=debug\n" +
                "commit=${InstrumentationRegistry.getArguments().getString("gitCommit") ?: "not-provided"}\n" +
                "worktree=${InstrumentationRegistry.getArguments().getString("worktree") ?: "not-provided"}\n" +
                "capture=MainActivity Compose and full device display\n"
        File(directory, "device.txt").writeText(metadata)
        File(directory, "$name-metadata.txt").writeText(metadata)
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        File(directory, "$name-device.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
