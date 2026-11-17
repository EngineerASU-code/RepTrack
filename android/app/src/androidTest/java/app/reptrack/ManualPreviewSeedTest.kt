package app.reptrack

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Seeds a separate local trainee profile for debug screen review; personal history stays untouched. */
@RunWith(AndroidJUnit4::class)
class ManualPreviewSeedTest {
    @Test
    fun seedCycleAcrossTheLastMonth() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = DiaryRepository(context)
        val original = repository.load()
        try {
            val originalHistoryCount = original.history.size
            repository.createDemoProfile()
            val demo = repository.load()
            assertEquals("Демо · визуальная проверка", demo.activeProfile?.name)
            assertEquals(ProfileKind.TRAINEE, demo.activeProfile?.kind)
            assertEquals(4, demo.plans.size)
            assertTrue(demo.plans.all { it.inCycle })
            assertEquals(4, demo.plans.first { it.name == "Демо · Push" }.exercises.size)
            val demoExercises = demo.plans.flatMap { it.exercises }.map { it.name }.toSet()
            assertTrue("Wide-grip lat pulldown must stay an exact alias", "Тяга верхнего блока широким хватом" in demoExercises)
            assertTrue("Close-grip cable row must stay an exact alias", "Тяга нижнего блока узким хватом" in demoExercises)
            assertTrue("Alternating hammer curl must stay an exact alias", "Попеременные молотки с гантелями" in demoExercises)
            assertTrue("Ambiguous lat-pulldown alias must not remain in the demo plans", "Тяга верхнего блока" !in demoExercises)
            assertTrue("Ambiguous cable-row alias must not remain in the demo plans", "Тяга нижнего блока" !in demoExercises)
            assertTrue("Ambiguous hammer-curl alias must not remain in the demo plans", "Молотки с гантелями" !in demoExercises)
            assertTrue(
                "Demo history exercise names must match the exact cable-row variant",
                demo.history.flatMap { it.exercises }.any {
                    it.name ==
                        "Тяга нижнего блока узким хватом"
                },
            )
            assertNotNull(demo.nextPlan)
            assertEquals(12, demo.history.size)
            assertNotNull(demo.bestByExercise["Жим штанги средним хватом"])
            assertTrue(demo.history.flatMap { it.exercises }.flatMap { it.sets }.any { it.kind == SetKind.WARMUP })
            assertTrue(demo.history.flatMap { it.exercises }.flatMap { it.sets }.any { it.kind == SetKind.WORK && it.done })

            repository.createDemoProfile()
            assertEquals("Repeated seeding must not duplicate workouts", 12, repository.load().history.size)
            repository.setActiveProfile(original.activeProfileId)
            assertEquals(originalHistoryCount, repository.load().history.size)
        } finally {
            repository.setActiveProfile(original.activeProfileId)
            repository.close()
        }
    }
}
