package app.reptrack

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class CompactPlanExerciseRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsExerciseDetailsAndAccessibleActions() {
        val exercise = PlanExercise(id = "plan-exercise-test", name = "Жим ногами")

        composeRule.setContent {
            MaterialTheme {
                CompactPlanExerciseRow(
                    exercise = exercise,
                    position = 2,
                    groupLabel = "Квадрицепс",
                    summary = "3 подхода · 120 кг × 10",
                    onOpen = {},
                    onMove = {},
                    onMenu = {},
                )
            }
        }

        composeRule.onNodeWithText("2. Жим ногами").assertIsDisplayed()
        composeRule.onNodeWithText("Квадрицепс").assertIsDisplayed()
        composeRule.onNodeWithText("3 подхода · 120 кг × 10").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Изменить порядок упражнения «Жим ногами»").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Действия для упражнения «Жим ногами»").assertIsDisplayed()
    }
}
