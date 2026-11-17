package app.reptrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
            id = "s",
            planId = "p",
            planName = "Push",
            status = SessionStatus.COMPLETED,
            startedAt = 100L,
            endedAt = 200L,
            exercises = listOf(
                SessionExercise(
                    "e",
                    "Жим",
                    0,
                    false,
                    true,
                    listOf(
                        TrainingSet("warm", 0, "40", "10", SetKind.WARMUP, true, false, 90, null, 0),
                        TrainingSet("work", 1, "60", "5", SetKind.WORK, true, false, 90, null, 0),
                        TrainingSet("many-reps", 2, "70", "12", SetKind.WORK, true, false, 90, null, 0),
                        TrainingSet("skipped", 3, "100", "5", SetKind.WORK, true, true, 90, null, 0),
                    ),
                ),
            ),
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
        assertEquals(
            70.0,
            calculateProgressSeries(listOf(session), "Жим", metric = ProgressMetric.ESTIMATED_ONE_REP_MAX).single().value,
            0.0001,
        )
    }

    @Test
    fun progressCountsOnlyCompletedWorkingSets() {
        val session = TrainingSession(
            id = "s1",
            planId = "p1",
            planName = "Push",
            status = SessionStatus.COMPLETED,
            startedAt = 1L,
            endedAt = 2L,
            exercises = listOf(
                SessionExercise(
                    "e1",
                    "Жим",
                    0,
                    false,
                    true,
                    listOf(
                        TrainingSet("warm", 0, "50", "10", SetKind.WARMUP, true, false, 90, null, 0),
                        TrainingSet("work", 1, "80", "10", SetKind.WORK, true, false, 90, null, 0),
                        TrainingSet("skip", 2, "100", "5", SetKind.WORK, false, true, 90, null, 0),
                    ),
                ),
                SessionExercise(
                    "e2",
                    "Пропуск",
                    1,
                    true,
                    true,
                    listOf(
                        TrainingSet("skipped-exercise", 0, "200", "5", SetKind.WORK, true, true, 90, null, 0),
                    ),
                ),
                SessionExercise(
                    "e3",
                    "Дорожка",
                    2,
                    false,
                    false,
                    listOf(
                        TrainingSet("cardio", 0, "999", "50", SetKind.WORK, true, false, 90, null, 0),
                    ),
                ),
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

    @Test
    fun missingAndNonFiniteWeightsDoNotCreateStrengthRecords() {
        val session = TrainingSession(
            id = "s",
            planId = "p",
            planName = "Push",
            status = SessionStatus.COMPLETED,
            startedAt = 100L,
            endedAt = 200L,
            exercises = listOf(
                SessionExercise(
                    "e",
                    "Жим",
                    0,
                    false,
                    true,
                    listOf(
                        TrainingSet("blank", 0, "", "8", SetKind.WORK, true, false, 90, null, 0),
                        TrainingSet("infinite", 1, "Infinity", "8", SetKind.WORK, true, false, 90, null, 0),
                    ),
                ),
            ),
        )

        assertNull(session.maxWeightKg)
        assertNull(calculateProgress(listOf(session), "Жим").maxWeightKg)
        assertNull(calculateProgress(listOf(session), "Жим").estimatedOneRepMaxKg)
        assertTrue(calculateProgressSeries(listOf(session), "Жим").isEmpty())
        assertNull(parseFiniteWeight("Infinity"))
    }

    @Test
    fun planDiffReportsOrderDuplicateOccurrencesParametersAndNoOp() {
        fun exercise(id: String, name: String, reps: String) = PlanExercise(id, name, targetReps = reps)
        fun plan(exercises: List<PlanExercise>) = TrainingPlan("plan", "Plan", 0, false, exercises)
        val before = plan(listOf(exercise("a1", "A", "8"), exercise("b", "B", "10"), exercise("a2", "A", "12")))
        val reordered =
            describePlanChanges(before, plan(listOf(exercise("b2", "B", "10"), exercise("a3", "A", "8"), exercise("a4", "A", "12"))))
        assertTrue(reordered.any { it.contains("A → B") })
        val duplicateEdited =
            describePlanChanges(
                plan(listOf(exercise("old1", "A", "8"), exercise("old2", "A", "12"))),
                plan(listOf(exercise("new1", "A", "8"), exercise("new2", "A", "15"))),
            )
        assertTrue(duplicateEdited.any { it.contains("Обновлены параметры") })
        val duplicateRemoved =
            describePlanChanges(
                plan(listOf(exercise("old1", "A", "8"), exercise("old2", "A", "12"))),
                plan(listOf(exercise("new1", "A", "8"))),
            )
        assertTrue(duplicateRemoved.any { it.contains("Удалено") || it.contains("Обновлены параметры") })
        assertTrue(
            describePlanChanges(
                before,
                plan(
                    before.exercises.map {
                        it.copy(id = "local-${it.id}")
                    },
                ),
            ).single().contains("Изменений нет"),
        )
    }

    @Test
    fun backupV5PreservesValidatedPendingProgramWithoutTrustMetadata() {
        val profileId = personalProfileId
        val remotePlan =
            TrainingPlan("remote-plan", "Обновление", 0, true, listOf(PlanExercise("exercise", "Жим", targetReps = "8")), version = 2)
        val pending =
            ArchivedIncomingProgram(
                profileId,
                remotePlan.id,
                null,
                0,
                0,
                2,
                encodePlanExport(listOf(remotePlan)).toString(Charsets.UTF_8),
                "package",
            )
        val profiles = listOf(DiaryProfile(personalProfileId, "Личный", ProfileKind.PERSONAL, 0))
        val bytes = encodeDiaryArchive(emptyList(), emptyList(), DiarySettings(), profiles, personalProfileId, emptyList(), listOf(pending))
        val decoded = decodeDiaryArchive(bytes)
        assertEquals(5, org.json.JSONObject(bytes.toString(Charsets.UTF_8)).getInt("version"))
        assertEquals(pending, decoded.acceptedIncoming.single())
        val corrupted = bytes.toString(Charsets.UTF_8).replace("\"planJson\"", "\"brokenPlan\"").toByteArray(Charsets.UTF_8)
        assertThrows(IllegalArgumentException::class.java) { decodeDiaryArchive(corrupted) }
    }
}
