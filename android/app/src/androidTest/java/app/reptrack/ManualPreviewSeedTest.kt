package app.reptrack

import android.content.ContentValues
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/** Seeds only the dedicated emulator's app database for manual screen review. */
@RunWith(AndroidJUnit4::class)
class ManualPreviewSeedTest {
    @Test
    fun seedFourWorkoutsAcrossTheLastMonth() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = DiaryRepository(context)
        try {
            if (repository.load().history.isNotEmpty()) return
            repository.createPlan("Пример · Push")
            val plan = repository.load().plans.last()
            repository.addExercise(plan.id, "Жим лёжа")
            val db = context.openOrCreateDatabase("reptrack.db", 0, null)
            val weights = listOf("40", "45", "50", "52.5")
            weights.forEachIndexed { index, weight ->
                val sessionId = repository.start(plan.id)
                val set = repository.load().activeSession!!.exercises.single().sets.first()
                repository.updateSet(set.id, weight = weight, reps = if (index < 2) "8" else "6")
                repository.toggleSet(set)
                repository.updateComment(sessionId, "Пример записи для проверки экрана")
                repository.finish(sessionId)
                val at = System.currentTimeMillis() - (28 - index * 7) * 86_400_000L
                db.update("sessions", ContentValues().apply { put("started_at", at); put("ended_at", at + 45 * 60_000L) }, "id=?", arrayOf(sessionId))
            }
        } finally {
            repository.close()
        }
    }
}
