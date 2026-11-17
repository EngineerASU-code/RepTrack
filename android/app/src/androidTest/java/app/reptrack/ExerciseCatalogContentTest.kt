package app.reptrack

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseCatalogContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun searchByAliasAndFilterByMuscleWithoutEquipmentOrVerificationFilters() {
        val query = mutableStateOf("")
        val selectedMuscle = mutableStateOf<ExerciseMuscleGroup?>(null)
        val selectedNames = mutableStateOf(emptySet<String>())
        compose.setContent {
            MaterialTheme {
                ExerciseCatalogContent(
                    entries = exerciseCatalog,
                    selectForPlan = false,
                    selectedMuscle = selectedMuscle.value,
                    onMuscleSelected = { selectedMuscle.value = it },
                    query = query.value,
                    onQueryChanged = { query.value = it },
                    selectedNames = selectedNames.value,
                    onToggleSelected = { name, selected ->
                        selectedNames.value = if (selected) selectedNames.value + name else selectedNames.value - name
                    },
                    onOpenExercise = {},
                    onAddSelected = {},
                )
            }
        }

        compose.onNodeWithText("${exerciseCatalog.size} упражнений").assertIsDisplayed()
        compose.onNodeWithText("Оборудование").assertDoesNotExist()
        compose.onNodeWithText("С проверенной техникой").assertDoesNotExist()

        compose.onNodeWithTag("catalog-search").performTextInput("наклонный жим")
        compose.onNodeWithTag("catalog-chest-incline-bench").assertExists()
        compose.onNodeWithTag("catalog-chest-barbell-bench").assertDoesNotExist()

        compose.onNodeWithTag("catalog-search").performTextClearance()
        compose.onNodeWithTag("catalog-muscle-CHEST").performClick()
        val chestExerciseCount = exerciseCatalog.count {
            ExerciseMuscleGroup.CHEST in it.primaryMuscles || ExerciseMuscleGroup.CHEST in it.secondaryMuscles
        }
        compose.onNodeWithText("$chestExerciseCount упражнений").assertIsDisplayed()
        compose.onNodeWithTag("catalog-list").performScrollToIndex(0)
        compose.onNodeWithTag("catalog-chest-barbell-bench").assertIsDisplayed()
        compose.onNodeWithTag("catalog-biceps-barbell-curl").assertDoesNotExist()
    }

    @Test
    fun catalogSelectionAddsExerciseToPlanCallback() {
        val selectedNames = mutableStateOf(emptySet<String>())
        val added = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                ExerciseCatalogContent(
                    entries = exerciseCatalog,
                    selectForPlan = true,
                    selectedMuscle = null,
                    onMuscleSelected = {},
                    query = "",
                    onQueryChanged = {},
                    selectedNames = selectedNames.value,
                    onToggleSelected = { name, selected ->
                        selectedNames.value = if (selected) selectedNames.value + name else selectedNames.value - name
                    },
                    onOpenExercise = {},
                    onAddSelected = { added.addAll(selectedNames.value) },
                )
            }
        }

        compose.onNodeWithTag("catalog-chest-barbell-bench").performClick()
        compose.onNodeWithText("Добавить выбранные · 1").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("Жим штанги лёжа"), added) }
    }
}
