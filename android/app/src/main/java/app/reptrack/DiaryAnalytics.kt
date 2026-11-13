package app.reptrack

data class ProgressMetrics(
    val sessions: Int,
    val completedWorkingSets: Int,
    val maxWeightKg: Double?,
    val maxReps: Int?,
    val estimatedOneRepMaxKg: Double?,
)

enum class ProgressMetric { MAX_WEIGHT, MAX_REPS, ESTIMATED_ONE_REP_MAX }
data class ProgressPoint(val sessionId: String, val timestamp: Long, val value: Double)

/** Uses completed, non-skipped working sets only; Epley is shown only for 1–10 reps. */
internal fun calculateProgress(
    sessions: List<TrainingSession>,
    exerciseName: String? = null,
    fromEpochMillis: Long? = null,
    untilEpochMillis: Long? = null,
): ProgressMetrics {
    val period = sessions.filter { session ->
        val at = session.endedAt ?: session.startedAt
        session.status == SessionStatus.COMPLETED && (fromEpochMillis == null || at >= fromEpochMillis) && (untilEpochMillis == null || at < untilEpochMillis)
    }
    val sets = period.asSequence().flatMap { it.exercises.asSequence() }
        .filter { !it.skipped && it.tracksLoad && (exerciseName == null || it.name == exerciseName) }
        .flatMap { e -> e.sets.asSequence().filter { it.done && !it.skipped && it.kind == SetKind.WORK }.map { e.name to it } }
        .toList()
    val loads = sets.mapNotNull { (_, set) -> set.weight.toDoubleOrNull()?.takeIf { it > 0.0 } }
    val reps = sets.mapNotNull { (_, set) -> set.reps.toIntOrNull()?.takeIf { it > 0 } }
    val e1rms = sets.mapNotNull { (_, set) ->
        val weight = set.weight.toDoubleOrNull() ?: return@mapNotNull null
        val count = set.reps.toIntOrNull() ?: return@mapNotNull null
        if (weight > 0.0 && count in 1..10) weight * (1.0 + count / 30.0) else null
    }
    val relevantSessions = if (exerciseName == null) period.size else period.count { s -> s.exercises.any { e -> e.name == exerciseName && e.tracksLoad && !e.skipped && e.sets.any { it.done && !it.skipped && it.kind == SetKind.WORK } } }
    return ProgressMetrics(relevantSessions, sets.size, loads.maxOrNull(), reps.maxOrNull(), e1rms.maxOrNull())
}

internal fun calculateProgressSeries(
    sessions: List<TrainingSession>,
    exerciseName: String,
    fromEpochMillis: Long? = null,
    untilEpochMillis: Long? = null,
    metric: ProgressMetric = ProgressMetric.MAX_WEIGHT,
): List<ProgressPoint> = sessions.asSequence()
    .filter { session ->
        val at = session.endedAt ?: session.startedAt
        session.status == SessionStatus.COMPLETED &&
            (fromEpochMillis == null || at >= fromEpochMillis) &&
            (untilEpochMillis == null || at < untilEpochMillis)
    }
    .mapNotNull { session ->
        val sets = session.exercises.asSequence()
            .filter { it.name == exerciseName && it.tracksLoad && !it.skipped }
            .flatMap { it.sets.asSequence() }
            .filter { it.done && !it.skipped && it.kind == SetKind.WORK }
            .toList()
        val value = when (metric) {
            ProgressMetric.MAX_WEIGHT -> sets.mapNotNull { it.weight.toDoubleOrNull()?.takeIf { weight -> weight > 0.0 } }.maxOrNull()
            ProgressMetric.MAX_REPS -> sets.mapNotNull { it.reps.toIntOrNull()?.takeIf { reps -> reps > 0 } }.maxOrNull()?.toDouble()
            ProgressMetric.ESTIMATED_ONE_REP_MAX -> sets.mapNotNull { set ->
                val weight = set.weight.toDoubleOrNull() ?: return@mapNotNull null
                val reps = set.reps.toIntOrNull() ?: return@mapNotNull null
                if (weight > 0.0 && reps in 1..10) weight * (1.0 + reps / 30.0) else null
            }.maxOrNull()
        } ?: return@mapNotNull null
        ProgressPoint(session.id, session.endedAt ?: session.startedAt, value)
    }
    .sortedBy { it.timestamp }
    .toList()
