package app.reptrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogDataTest {
    @Test
    fun catalogEntriesAreCompleteUniqueAndCoverAllMuscleGroups() {
        assertTrue(exerciseCatalog.size >= 120)
        assertEquals(exerciseCatalog.size, exerciseCatalog.map { it.id }.toSet().size)
        assertEquals(exerciseCatalog.map { it.id }.toSet(), exerciseCatalogArtworkById.keys)
        assertEquals(ExerciseMuscleGroup.entries.toSet(), exerciseCatalog.flatMap { it.primaryMuscles }.toSet())

        exerciseCatalog.forEach { exercise ->
            assertFalse(exercise.id.isBlank())
            assertFalse(exercise.name.isBlank())
            assertTrue(exercise.aliases.isNotEmpty() && exercise.aliases.all(String::isNotBlank))
            assertTrue(exercise.primaryMuscles.isNotEmpty())
            assertFalse(exercise.equipment.isBlank())
            assertFalse(exercise.setup.isBlank())
            assertTrue(exercise.steps.size >= 2 && exercise.steps.all(String::isNotBlank))
            assertFalse(exercise.breathing.isBlank())
            assertTrue(exercise.commonMistakes.isNotEmpty() && exercise.commonMistakes.all(String::isNotBlank))
            assertFalse(exercise.safetyNotes.isBlank())
            assertFalse(exercise.artKey.isBlank())
            assertFalse(exercise.artKey == "fallback.transparent")
        }
    }

    @Test
    fun catalogResolvesNamesAndAliasesAndPlanSummariesUseOnlyPrimaryGroups() {
        exerciseCatalog.forEach { entry ->
            assertEquals(entry.id, exerciseCatalogEntryForName(entry.name)?.id)
        }

        val bench = requireNotNull(exerciseCatalogEntryForName("жим лёжа со штангой"))
        assertEquals("chest-barbell-bench", bench.id)

        val pushGroups = targetMuscleGroupsForExercises(
            listOf("Жим штанги лёжа", "Жим гантелей лёжа", "Сведение рук в кроссовере"),
        )
        assertEquals(listOf(ExerciseMuscleGroup.CHEST), pushGroups)
        assertFalse(ExerciseMuscleGroup.LATS in pushGroups)
        assertFalse(ExerciseMuscleGroup.QUADRICEPS in pushGroups)
    }
}
