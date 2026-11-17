package app.reptrack

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MuscleMapMappingTest {
    @Test
    fun starterExerciseGroupsMatchCatalogAndLocalSvgRegions() {
        data class Expected(
            val appName: String,
            val catalogName: String,
            val primary: List<ExerciseMuscleGroup>,
            val secondary: List<ExerciseMuscleGroup>,
        )

        val cases = listOf(
            Expected(
                "жим штанги средним хватом",
                "Barbell Bench Press - Medium Grip",
                listOf(ExerciseMuscleGroup.CHEST),
                listOf(ExerciseMuscleGroup.TRICEPS, ExerciseMuscleGroup.SHOULDERS),
            ),
            Expected(
                "тяга верхнего блока широким хватом",
                "Wide-Grip Lat Pulldown",
                listOf(ExerciseMuscleGroup.LATS),
                listOf(ExerciseMuscleGroup.BICEPS, ExerciseMuscleGroup.MIDDLE_BACK, ExerciseMuscleGroup.SHOULDERS),
            ),
            Expected(
                "приседания со штангой",
                "Barbell Squat",
                listOf(ExerciseMuscleGroup.QUADRICEPS),
                listOf(
                    ExerciseMuscleGroup.CALVES,
                    ExerciseMuscleGroup.GLUTES,
                    ExerciseMuscleGroup.HAMSTRINGS,
                    ExerciseMuscleGroup.LOWER_BACK,
                ),
            ),
            Expected(
                "жим ногами",
                "Leg Press",
                listOf(ExerciseMuscleGroup.QUADRICEPS),
                listOf(ExerciseMuscleGroup.CALVES, ExerciseMuscleGroup.GLUTES, ExerciseMuscleGroup.HAMSTRINGS),
            ),
            Expected(
                "тяга нижнего блока узким хватом",
                "Seated Cable Rows",
                listOf(ExerciseMuscleGroup.MIDDLE_BACK),
                listOf(ExerciseMuscleGroup.BICEPS, ExerciseMuscleGroup.LATS, ExerciseMuscleGroup.SHOULDERS),
            ),
            Expected(
                "попеременные молотки с гантелями",
                "Alternate Hammer Curl",
                listOf(ExerciseMuscleGroup.BICEPS),
                listOf(ExerciseMuscleGroup.FOREARMS),
            ),
        )

        cases.forEach { expected ->
            val guide = requireNotNull(verifiedGuideFor(expected.appName))
            assertEquals(expected.catalogName, guide.muscleSourceName)
            assertEquals(expected.primary, guide.primaryMuscles)
            assertEquals(expected.secondary, guide.secondaryMuscles)
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val frontIds = context.assets.open("exercise-artwork/anatomy/male_front_muscle_anatomy.svg").bufferedReader().use(::regionIds)
        val backIds = context.assets.open("exercise-artwork/anatomy/male_back_muscle_anatomy.svg").bufferedReader().use(::regionIds)
        ExerciseMuscleGroup.values().forEach { group ->
            group.frontRegions.forEach { id -> assertTrue("${group.label}: front SVG is missing $id", id in frontIds) }
            group.backRegions.forEach { id -> assertTrue("${group.label}: back SVG is missing $id", id in backIds) }
        }
    }

    private fun regionIds(reader: java.io.BufferedReader): Set<String> =
        Regex("""<g\b[^>]*\bid="([^"]+)"""").findAll(reader.readText()).map { it.groupValues[1] }.toSet()
}
