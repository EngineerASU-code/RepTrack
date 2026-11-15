package app.reptrack

/** A completion outside the editable cycle leaves the recommendation unchanged. */
internal fun nextCyclePosition(cycleIds: List<String>, lastCompletedPlanId: String?): Int {
    if (cycleIds.isEmpty()) return -1
    val current = cycleIds.indexOf(lastCompletedPlanId)
    return (current + 1) % cycleIds.size
}

private const val poundsPerKilogram = 2.2046226218

internal fun parseFiniteWeight(value: String): Double? =
    value.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

internal fun toKilograms(value: Double, unit: WeightUnit) = if (unit == WeightUnit.LB) value / poundsPerKilogram else value
internal fun fromKilograms(value: Double, unit: WeightUnit) = if (unit == WeightUnit.LB) value * poundsPerKilogram else value
internal fun parseWeightToKilograms(value: String, unit: WeightUnit): String =
    parseFiniteWeight(value)?.let { String.format(java.util.Locale.US, "%.4f", toKilograms(it, unit)) } ?: ""
