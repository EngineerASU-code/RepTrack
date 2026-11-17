package app.reptrack

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityAdaptiveTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun rootNavigationKeepsLabelsAndRouteAtLargeFontAndRespectsNavigationInset() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val originalFontScale = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("settings get system font_scale"),
        ).bufferedReader().use { it.readText().trim() }.takeIf { it.toFloatOrNull() != null } ?: "1.0"
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 2.0").close()
            compose.waitUntil(10_000) { instrumentation.targetContext.resources.configuration.fontScale >= 2f }

            listOf("home", "gym", "plan", "progress", "history").forEach { tag ->
                compose.onNodeWithTag("nav-label-$tag", useUnmergedTree = true).assertIsDisplayed()
            }
            compose.onNodeWithTag("open-settings").assertContentDescriptionEquals("Настройки")
            compose.onNodeWithTag("nav-plan").performClick().assertIsSelected()
            scenario.recreate()
            compose.waitUntil(10_000) {
                runCatching { compose.onNodeWithTag("nav-plan").assertIsSelected() }.isSuccess
            }

            val navigationInset = intArrayOf(0)
            scenario.onActivity { activity ->
                navigationInset[0] = ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            }
            val rootBottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
            val navigationBottom = compose.onNodeWithTag("nav-home").fetchSemanticsNode().boundsInRoot.bottom
            org.junit.Assert.assertTrue("Expected a visible system navigation inset", navigationInset[0] > 0)
            org.junit.Assert.assertTrue(
                "Root navigation overlaps the system navigation inset",
                navigationBottom <= rootBottom - navigationInset[0] + 1f,
            )
        } finally {
            scenario.close()
            instrumentation.uiAutomation.executeShellCommand("settings put system font_scale $originalFontScale").close()
        }
    }
}
