package app.reptrack

import org.json.JSONArray
import org.json.JSONObject

internal data class ArchivedDiary(
    val plans: List<TrainingPlan>,
    val sessions: List<TrainingSession>,
    val settings: DiarySettings,
)

internal fun encodeDiaryArchive(plans: List<TrainingPlan>, sessions: List<TrainingSession>, settings: DiarySettings): ByteArray = JSONObject()
    .put("format", "reptrack-backup")
    .put("version", 1)
    .put("plans", JSONArray().apply { plans.forEach { put(it.toJson()) } })
    .put("sessions", JSONArray().apply { sessions.forEach { put(it.toJson()) } })
    .put("settings", JSONObject().put("weightUnit", settings.weightUnit.name).put("theme", settings.theme.name).put("restTimerEnabled", settings.restTimerEnabled))
    .toString().toByteArray(Charsets.UTF_8)

internal fun decodeDiaryArchive(json: ByteArray): ArchivedDiary {
    val root = JSONObject(json.toString(Charsets.UTF_8))
    require(root.optString("format") == "reptrack-backup" && root.optInt("version") == 1) { "Неизвестная версия резервной копии" }
    val plans = root.requireArray("plans").toObjects(::parsePlan, max = 500)
    val sessions = root.requireArray("sessions").toObjects(::parseSession, max = 20_000)
    require(sessions.count { it.status == SessionStatus.ACTIVE } <= 1) { "В копии несколько активных тренировок" }
    val settingsJson = root.optJSONObject("settings") ?: error("В копии отсутствуют настройки")
    val settings = DiarySettings(
        weightUnit = runCatching { WeightUnit.valueOf(settingsJson.optString("weightUnit")) }.getOrDefault(WeightUnit.KG),
        theme = runCatching { DiaryTheme.valueOf(settingsJson.optString("theme")) }.getOrDefault(DiaryTheme.PURPLE_PRO),
        restTimerEnabled = settingsJson.optBoolean("restTimerEnabled", true),
    )
    return ArchivedDiary(plans, sessions, settings)
}

internal fun encodePlanExport(plans: List<TrainingPlan>): ByteArray = JSONObject()
    .put("format", "reptrack-plans")
    .put("version", 1)
    .put("plans", JSONArray().apply { plans.forEach { put(it.toJson()) } })
    .toString().toByteArray(Charsets.UTF_8)

internal fun decodePlanExport(json: ByteArray): List<TrainingPlan> {
    val root = JSONObject(json.toString(Charsets.UTF_8))
    require(root.optString("format") == "reptrack-plans" && root.optInt("version") == 1) { "Файл не является экспортом планов RepTrack" }
    return root.requireArray("plans").toObjects(::parsePlan, max = 500)
}

private fun TrainingPlan.toJson() = JSONObject()
    .put("id", id).put("name", name).put("position", position).put("inCycle", inCycle)
    .put("exercises", JSONArray().apply { exercises.forEach { put(it.toJson()) } })

private fun PlanExercise.toJson() = JSONObject()
    .put("id", id).put("name", name).put("plannedSets", plannedSets).put("targetWeight", targetWeight)
    .put("targetReps", targetReps).put("restSeconds", restSeconds).put("tracksLoad", tracksLoad)

private fun TrainingSession.toJson() = JSONObject()
    .put("id", id).put("planId", planId ?: JSONObject.NULL).put("planName", planName).put("status", status.name)
    .put("startedAt", startedAt).put("endedAt", endedAt ?: JSONObject.NULL).put("comment", comment)
    .put("exercises", JSONArray().apply { exercises.forEach { put(it.toJson()) } })

private fun SessionExercise.toJson() = JSONObject()
    .put("id", id).put("name", name).put("position", position).put("skipped", skipped).put("tracksLoad", tracksLoad)
    .put("sets", JSONArray().apply { sets.forEach { put(it.toJson()) } })

private fun TrainingSet.toJson() = JSONObject()
    .put("id", id).put("position", position).put("weight", weight).put("reps", reps).put("kind", kind.name)
    .put("done", done).put("skipped", skipped).put("restSeconds", restSeconds)
    .put("restUntil", restUntil ?: JSONObject.NULL).put("restRemaining", restRemaining)

private fun parsePlan(json: JSONObject): TrainingPlan {
    val exercises = json.requireArray("exercises").toObjects(::parsePlanExercise, max = 500)
    return TrainingPlan(json.requireString("id", 128), json.requireString("name", 48), json.nonNegative("position"), json.optBoolean("inCycle"), exercises)
}

private fun parsePlanExercise(json: JSONObject): PlanExercise = PlanExercise(
    id = json.requireString("id", 128), name = json.requireString("name", 80),
    plannedSets = json.nonNegative("plannedSets").coerceIn(1, 20), targetWeight = json.requireString("targetWeight", 16),
    targetReps = json.requireString("targetReps", 8), restSeconds = json.nonNegative("restSeconds").coerceAtMost(900),
    tracksLoad = json.optBoolean("tracksLoad", true),
)

private fun parseSession(json: JSONObject): TrainingSession {
    val status = runCatching { SessionStatus.valueOf(json.requireString("status", 16)) }.getOrElse { error("Неизвестный статус тренировки") }
    val planId = if (json.isNull("planId")) null else json.requireString("planId", 128)
    return TrainingSession(
        id = json.requireString("id", 128), planId = planId, planName = json.requireString("planName", 80), status = status,
        startedAt = json.nonNegativeLong("startedAt"), endedAt = json.optionalNonNegativeLong("endedAt"),
        comment = json.requireString("comment", 1000), exercises = json.requireArray("exercises").toObjects(::parseSessionExercise, max = 1000),
    )
}

private fun parseSessionExercise(json: JSONObject): SessionExercise = SessionExercise(
    id = json.requireString("id", 128), name = json.requireString("name", 80), position = json.nonNegative("position"),
    skipped = json.optBoolean("skipped"), tracksLoad = json.optBoolean("tracksLoad", true),
    sets = json.requireArray("sets").toObjects(::parseSet, max = 1000),
)

private fun parseSet(json: JSONObject): TrainingSet {
    val kind = runCatching { SetKind.valueOf(json.requireString("kind", 16)) }.getOrElse { error("Неизвестный тип подхода") }
    val weight = json.requireString("weight", 16)
    val numericWeight = weight.replace(',', '.').toDoubleOrNull()
    require(weight.isEmpty() || numericWeight != null && numericWeight >= 0.0) { "В копии некорректный вес" }
    val reps = json.requireString("reps", 8)
    require(reps.isEmpty() || reps.toIntOrNull()?.let { it >= 0 } == true) { "В копии некорректное число повторений" }
    return TrainingSet(
        id = json.requireString("id", 128), position = json.nonNegative("position"), weight = weight, reps = reps,
        kind = kind, done = json.optBoolean("done"), skipped = json.optBoolean("skipped"),
        restSeconds = json.nonNegative("restSeconds").coerceAtMost(900), restUntil = json.optionalNonNegativeLong("restUntil"),
        restRemaining = json.optLong("restRemaining").coerceAtLeast(0L).coerceAtMost(900_000L),
    )
}

private fun JSONObject.requireArray(key: String): JSONArray = optJSONArray(key) ?: error("В файле отсутствует список $key")

private fun <T> JSONArray.toObjects(parse: (JSONObject) -> T, max: Int): List<T> {
    require(length() <= max) { "В файле слишком много записей" }
    return buildList(length()) {
        for (index in 0 until length()) add(parse(optJSONObject(index) ?: error("Повреждена запись $index")))
    }
}

private fun JSONObject.requireString(key: String, max: Int): String {
    val value = opt(key)
    require(value is String && value.length <= max) { "В файле некорректно поле $key" }
    return value
}

private fun JSONObject.nonNegative(key: String): Int = optInt(key, -1).also { require(it >= 0) { "В файле некорректно поле $key" } }
private fun JSONObject.nonNegativeLong(key: String): Long = optLong(key, -1L).also { require(it >= 0L) { "В файле некорректно поле $key" } }
private fun JSONObject.optionalNonNegativeLong(key: String): Long? {
    if (isNull(key)) return null
    return nonNegativeLong(key)
}
