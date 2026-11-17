package app.reptrack

import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoReportsInstrumentedTest {
    @Test fun importsSyntheticReportsOnceWithoutChangingPersonalDiary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "demo-reports-${UUID.randomUUID()}.db"
        val repository = DiaryRepository(context, name)
        try {
            repository.createPlan("Моя программа")
            repository.createDemoReportsProfile()
            val imported = repository.load()
            assertEquals(demoReportsProfileId, imported.activeProfileId)
            assertEquals(2, imported.history.size)
            assertTrue(
                imported.history
                    .flatMap { it.exercises }
                    .flatMap { it.sets }
                    .isNotEmpty(),
            )
            repository.createDemoReportsProfile()
            assertEquals(imported.history.map { it.id }, repository.load().history.map { it.id })
            repository.setActiveProfile(personalProfileId)
            assertEquals(
                "Моя программа",
                repository
                    .load()
                    .plans
                    .single()
                    .name,
            )
            assertTrue(repository.load().history.isEmpty())
        } finally {
            repository.close()
            context.deleteDatabase(name)
        }
    }
}
