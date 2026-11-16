package app.reptrack

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import org.junit.Rule
import org.junit.Test

class HomeHeroArtworkTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersWithAccessibilityDescription() {
        compose.setContent {
            Box(Modifier.testTag("home-hero-artwork-test")) {
                HomeHeroArtwork()
            }
        }

        compose.onNodeWithTag("home-hero-artwork-test").assertIsDisplayed()
        compose.onNodeWithContentDescription("Фиолетовая иллюстрация гантели").assertIsDisplayed()
    }
}
