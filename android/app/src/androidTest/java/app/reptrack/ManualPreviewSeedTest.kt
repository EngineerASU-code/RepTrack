package app.reptrack

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Seeds a separate local trainee profile for debug screen review; personal history stays untouched. */
@RunWith(AndroidJUnit4::class)
class ManualPreviewSeedTest {
    @Test
    fun seedFourWorkoutsAcrossTheLastMonth() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = DiaryRepository(context)
        try {
            val personalHistoryCount = repository.load().history.size
            repository.createDemoProfile()
            val demo = repository.load()
            assertEquals("Демо · тестовые данные", demo.activeProfile?.name)
            assertEquals(ProfileKind.TRAINEE, demo.activeProfile?.kind)
            assertEquals(2, demo.plans.size)
            assertEquals(4, demo.history.size)
            assertNotNull(demo.bestByExercise["Жим лёжа"])

            repository.setActiveProfile(personalProfileId)
            assertEquals(personalHistoryCount, repository.load().history.size)
            repository.setActiveProfile(demo.activeProfileId)
        } finally {
            repository.close()
        }
    }
}
