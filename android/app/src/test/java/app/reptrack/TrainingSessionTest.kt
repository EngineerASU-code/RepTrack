package app.reptrack

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingSessionTest {
    @Test
    fun cycleAdvancesAfterLastCompletedDayAndIgnoresOutsideDays() {
        val cycle = listOf("push", "legs", "pull", "up")
        assertEquals(0, nextCyclePosition(cycle, null))
        assertEquals(3, nextCyclePosition(cycle, "pull"))
        assertEquals(0, nextCyclePosition(cycle, "outside-cycle"))
        assertEquals(-1, nextCyclePosition(emptyList(), "push"))
    }

    @Test
    fun analyticsFiltersWarmupsSkippedSetsCardioAndPeriod() {
        val session = TrainingSession(
            id = "s", planId = "p", planName = "Push", status = SessionStatus.COMPLETED,
            startedAt = 100L, endedAt = 200L,
            exercises = listOf(SessionExercise("e", "Жим", 0, false, true, listOf(
                TrainingSet("warm", 0, "40", "10", SetKind.WARMUP, true, false, 90, null, 0),
                TrainingSet("work", 1, "60", "5", SetKind.WORK, true, false, 90, null, 0),
                TrainingSet("many-reps", 2, "70", "12", SetKind.WORK, true, false, 90, null, 0),
                TrainingSet("skipped", 3, "100", "5", SetKind.WORK, true, true, 90, null, 0),
            )))
        )

        val result = calculateProgress(listOf(session), "Жим", 150L)
        assertEquals(1, result.sessions)
        assertEquals(2, result.completedWorkingSets)
        assertEquals(70.0, result.maxWeightKg!!, 0.0)
        assertEquals(12, result.maxReps)
        assertEquals(70.0, result.estimatedOneRepMaxKg!!, 0.0001)
        assertEquals(0, calculateProgress(listOf(session), "Жим", 201L).sessions)
        assertEquals(70.0, calculateProgressSeries(listOf(session), "Жим", metric = ProgressMetric.MAX_WEIGHT).single().value, 0.0)
        assertEquals(12.0, calculateProgressSeries(listOf(session), "Жим", metric = ProgressMetric.MAX_REPS).single().value, 0.0)
        assertEquals(70.0, calculateProgressSeries(listOf(session), "Жим", metric = ProgressMetric.ESTIMATED_ONE_REP_MAX).single().value, 0.0001)
    }

    @Test
    fun progressCountsOnlyCompletedWorkingSets() {
        val session = TrainingSession(
            id = "s1", planId = "p1", planName = "Push", status = SessionStatus.COMPLETED,
            startedAt = 1L, endedAt = 2L,
            exercises = listOf(
                SessionExercise("e1", "Жим", 0, false, true, listOf(
                    TrainingSet("warm", 0, "50", "10", SetKind.WARMUP, true, false, 90, null, 0),
                    TrainingSet("work", 1, "80", "10", SetKind.WORK, true, false, 90, null, 0),
                    TrainingSet("skip", 2, "100", "5", SetKind.WORK, false, true, 90, null, 0),
                )),
                SessionExercise("e2", "Пропуск", 1, true, true, listOf(
                    TrainingSet("skipped-exercise", 0, "200", "5", SetKind.WORK, true, true, 90, null, 0),
                )),
                SessionExercise("e3", "Дорожка", 2, false, false, listOf(
                    TrainingSet("cardio", 0, "999", "50", SetKind.WORK, true, false, 90, null, 0),
                )),
            ),
        )

        assertEquals(80.0, session.maxWeightKg!!, 0.0)
        assertEquals(2, session.completedSets)
        assertEquals(2, session.totalSets)
    }

    @Test
    fun changingUnitsConvertsForDisplayWithoutChangingStoredKilograms() {
        val enteredWeight = parseWeightToKilograms("225", WeightUnit.LB).toDouble()
        assertEquals(102.0583, enteredWeight, 0.0001)
        assertEquals(225.0, fromKilograms(enteredWeight, WeightUnit.LB), 0.001)
        assertEquals(102.0583, fromKilograms(enteredWeight, WeightUnit.KG), 0.0001)
        assertEquals("", parseWeightToKilograms("", WeightUnit.LB))
    }
}
