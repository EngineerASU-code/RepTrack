package app.reptrack

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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
        val repository = DiaryRepository(context)
        val original = repository.load()
        var createdSession: String? = null
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            repository.createDemoProfile()
            repository.setTheme(DiaryTheme.PURPLE_PRO)
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("Демо", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            capture("C01-home")
            listOf("gym", "plan", "progress", "history").forEach { route ->
                compose.onNodeWithTag("nav-$route").performClick()
                compose.waitForIdle()
                capture("root-$route")
                if (route == "progress") {
                    compose.onNodeWithTag("progress-chart").performScrollTo().assertIsDisplayed()
                    capture("C11-chart")
                }
            }
            compose.onNodeWithTag("open-settings").performClick()
            compose.waitForIdle()
            capture("C14-settings")
            compose.onNodeWithTag("nav-home").performClick()
            org.junit.Assert.assertNull("Demo profile already has an active workout; leave it intact", repository.load().activeSession)
            compose.onNodeWithTag("start-workout").performScrollTo().performClick()
            compose.waitUntil(10_000) { repository.load().activeSession != null }
            createdSession = repository.load().activeSession?.id
            compose.onNodeWithTag("finish-workout").assertIsDisplayed()
            compose.onNodeWithText("Грудь").assertIsDisplayed()
            capture("C03-workout")
            compose.onNodeWithContentDescription("Отметить подход 1").performClick()
            compose.onNodeWithText("Отдых", substring = true, useUnmergedTree = true).assertExists()
            capture("C09-rest-running")
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
                    compose.onNodeWithText("Источник: NASM · Barbell Bench Press").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithContentDescription("Фото 1 упражнения «Жим штанги средним хватом»; техника по фотографии не сертифицирована").assertExists()
                }
                if (section == "muscles") {
                    compose.onNodeWithTag("muscle-map").assertExists()
                    compose.onNodeWithText("Спереди").assertIsDisplayed()
                    compose.onNodeWithText("Сзади").assertIsDisplayed()
                    compose.onNodeWithText("Большая грудная мышца").assertIsDisplayed()
                    compose.onNodeWithText("Мышцы: Free Exercise DB · Barbell Bench Press - Medium Grip").assertExists()
                    compose.onNodeWithText("Анатомическая иллюстрация: Surya Mouly / muscle_mapper · MIT").assertExists()
                }
                capture("exercise-$section")
            }
            compose.onNodeWithTag("exercise-back").performClick()
            compose.onNodeWithTag("finish-workout").assertIsDisplayed()
            org.junit.Assert.assertEquals(createdSession, repository.load().activeSession?.id)
        } finally {
            scenario?.close()
            createdSession?.let(repository::cancel)
            repository.setActiveProfile(original.activeProfileId)
            repository.setTheme(original.settings.theme)
            repository.close()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(250)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "visual-acceptance").apply { mkdirs() }
        val configuration = context.resources.configuration
        File(directory, "device.txt").writeText(
            "Android=${android.os.Build.VERSION.SDK_INT}\n" +
                "viewport=${configuration.screenWidthDp}x${configuration.screenHeightDp}dp\n" +
                "density=${context.resources.displayMetrics.density}\nfontScale=${configuration.fontScale}\n" +
                "theme=PURPLE_PRO\nprofile=isolated demo\ncapture=Compose root and full device display\n"
        )
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        File(directory, "$name-device.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
