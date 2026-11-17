package app.reptrack

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoWorkoutReportsTest {
    @Test fun syntheticReportsHaveStableIdsAndDoNotInventCardioSets() {
        val asset =
            listOf(
                File("src/debug/assets/workout-reports.json"),
                File("app/src/debug/assets/workout-reports.json"),
            )
                .first { it.isFile }
                .readBytes()
        val sessions = decodeDemoWorkoutReports(asset)
        assertEquals(2, sessions.size)
        assertEquals(2, sessions.map { it.id }.distinct().size)
        assertEquals(sessions, decodeDemoWorkoutReports(asset))
        assertTrue(
            sessions.all {
                it.ownerId == demoReportsProfileId &&
                    it.status == SessionStatus.COMPLETED
            },
        )
        assertTrue(
            sessions.flatMap {
                it.exercises
            }.filter { !it.tracksLoad }.all { it.sets.isEmpty() },
        )
        val strength = sessions.first { it.planName == "Демонстрация A" }.exercises.first()
        assertEquals("30", strength.sets.first().weight)
        assertEquals("8", strength.sets.first().reps)
        assertTrue(sessions.all { it.comment.contains("Синтетический демонстрационный пример") })
    }
}
