package app.reptrack

import org.json.JSONArray
import org.json.JSONObject

internal data class ArchivedDiary(
    val plans: List<TrainingPlan>,
    val sessions: List<TrainingSession>,
    val settings: DiarySettings,
    val profiles: List<DiaryProfile>,
    val activeProfileId: String,
    val assignments: List<ProgramAssignment>,
    val acceptedIncoming: List<ArchivedIncomingProgram>,
)
internal data class ArchivedIncomingProgram(val profileId: String, val remotePlanId: String, val localPlanId: String?, val appliedVersion: Int, val appliedLocalVersion: Int, val pendingVersion: Int, val planJson: String, val packageId: String)

internal fun describePlanChanges(current: TrainingPlan, incoming: TrainingPlan): List<String> = buildList {
    if (current.name != incoming.name) add("Название: ${current.name} → ${incoming.name}")
    if (current.inCycle != incoming.inCycle) add(if (incoming.inCycle) "Программа будет добавлена в цикл" else "Программа будет убрана из цикла")
    for (index in 0 until maxOf(current.exercises.size, incoming.exercises.size)) {
        val old = current.exercises.getOrNull(index)
        val new = incoming.exercises.getOrNull(index)
        when {
            old == null && new != null -> add("Добавлено, позиция ${index + 1}: ${new.name}")
            old != null && new == null -> add("Удалено, позиция ${index + 1}: ${old.name}")
            old != null && new != null -> {
                if (old.name != new.name) add("Порядок/упражнение ${index + 1}: ${old.name} → ${new.name}")
                if (old.copy(id = new.id, name = new.name) != new) add("Обновлены параметры, позиция ${index + 1}: ${new.name}")
            }
        }
    }
    if (isEmpty()) add("Изменений нет")
}.let { changes -> changes.take(10) + if (changes.size > 10) listOf("И ещё ${changes.size - 10} изменений.") else emptyList() }

internal fun encodeDiaryArchive(
    plans: List<TrainingPlan>,
    sessions: List<TrainingSession>,
    settings: DiarySettings,
    profiles: List<DiaryProfile>,
    activeProfileId: String,
    assignments: List<ProgramAssignment>,
    acceptedIncoming: List<ArchivedIncomingProgram> = emptyList(),
): ByteArray = JSONObject()
    .put("format", "reptrack-backup")
    .put("version", 4)
    .put("activeProfileId", activeProfileId)
    .put("profiles", JSONArray().apply { profiles.forEach { put(it.toJson()) } })
    .put("plans", JSONArray().apply { plans.forEach { put(it.toJson().put("ownerId", it.ownerId)) } })
    .put("sessions", JSONArray().apply { sessions.forEach { put(it.toJson()) } })
    .put("assignments", JSONArray().apply { assignments.forEach { put(it.toJson()) } })
    .put("acceptedIncoming", JSONArray().apply { acceptedIncoming.forEach { put(it.toJson()) } })
    .put("settings", JSONObject().put("weightUnit", settings.weightUnit.name).put("theme", settings.theme.name).put("restTimerEnabled", settings.restTimerEnabled))
    .toString().toByteArray(Charsets.UTF_8)

internal fun decodeDiaryArchive(json: ByteArray): ArchivedDiary {
    val root = JSONObject(json.toString(Charsets.UTF_8))
    val version = root.optInt("version")
    require(root.optString("format") == "reptrack-backup" && version in 1..4) { "Неизвестная версия резервной копии" }
    val plans = root.requireArray("plans").toObjects(::parsePlan, max = 500)
    val sessions = root.requireArray("sessions").toObjects(::parseSession, max = 20_000)
    val profiles = if (version == 1) listOf(DiaryProfile(personalProfileId, "Мой дневник", ProfileKind.PERSONAL, 0L))
        else root.requireArray("profiles").toObjects(::parseProfile, max = 5_000)
    val assignments = if (version < 3) emptyList() else root.requireArray("assignments").toObjects(::parseAssignment, max = 20_000)
    val acceptedIncoming = if (version < 4) emptyList() else root.requireArray("acceptedIncoming").toObjects(::parseArchivedIncoming, max = 5_000)
    require(profiles.map { it.id }.distinct().size == profiles.size && profiles.count { it.kind == ProfileKind.PERSONAL } == 1) { "В копии некорректный список профилей" }
    require(profiles.single { it.kind == ProfileKind.PERSONAL }.id == personalProfileId) { "В копии некорректный личный профиль" }
    val profileIds = profiles.mapTo(mutableSetOf()) { it.id }
    require(plans.all { it.ownerId in profileIds } && sessions.all { it.ownerId in profileIds }) { "В копии найдены данные без профиля" }
    require(assignments.all { assignment -> assignment.profileId in profileIds && plans.any { it.id == assignment.assignedPlanId && it.ownerId == assignment.profileId } }) { "В копии найдено назначение без профиля или программы" }
    require(assignments.map { it.id }.distinct().size == assignments.size) { "В копии повторяется ID назначения" }
    require(acceptedIncoming.all { it.profileId in profileIds }) { "В копии найдено принятое обновление без профиля" }
    require(acceptedIncoming.map { it.profileId to it.remotePlanId }.distinct().size == acceptedIncoming.size) { "В копии повторяется принятое обновление" }
    require(sessions.filter { it.status == SessionStatus.ACTIVE }.groupingBy { it.ownerId }.eachCount().values.all { it <= 1 }) { "В копии у профиля несколько активных тренировок" }
    val activeProfileId = if (version == 1) personalProfileId else root.requireString("activeProfileId", 128)
    require(activeProfileId in profileIds) { "В копии не найден выбранный профиль" }
    val settingsJson = root.optJSONObject("settings") ?: error("В копии отсутствуют настройки")
    val settings = DiarySettings(
        weightUnit = runCatching { WeightUnit.valueOf(settingsJson.optString("weightUnit")) }.getOrDefault(WeightUnit.KG),
        theme = runCatching { DiaryTheme.valueOf(settingsJson.optString("theme")) }.getOrDefault(DiaryTheme.PURPLE_PRO),
        restTimerEnabled = settingsJson.optBoolean("restTimerEnabled", true),
    )
    return ArchivedDiary(plans, sessions, settings, profiles, activeProfileId, assignments, acceptedIncoming)
}

private fun ArchivedIncomingProgram.toJson() = JSONObject().put("profileId", profileId).put("remotePlanId", remotePlanId).put("localPlanId", localPlanId ?: JSONObject.NULL)
    .put("appliedVersion", appliedVersion).put("appliedLocalVersion", appliedLocalVersion).put("pendingVersion", pendingVersion).put("planJson", planJson).put("packageId", packageId)

private fun parseArchivedIncoming(json: JSONObject): ArchivedIncomingProgram {
    val planJson = json.requireString("planJson", 2_000_000)
    val plan = decodePlanExport(planJson.toByteArray(Charsets.UTF_8)).singleOrNull() ?: error("В копии повреждено принятое обновление")
    val version = json.nonNegative("pendingVersion").also { require(it == plan.version) { "В копии версия обновления не совпадает с программой" } }
    val remotePlanId = json.requireString("remotePlanId", 128)
    require(plan.id == remotePlanId) { "В копии ID обновления не совпадает с программой" }
    val appliedVersion = json.nonNegative("appliedVersion")
    require(version > appliedVersion) { "В копии некорректная версия обновления" }
    return ArchivedIncomingProgram(json.requireString("profileId", 128), remotePlanId, if (json.isNull("localPlanId")) null else json.requireString("localPlanId", 128), appliedVersion, json.nonNegative("appliedLocalVersion"), version, planJson, json.requireString("packageId", 128))
}

internal fun encodePlanExport(plans: List<TrainingPlan>): ByteArray = JSONObject()
    .put("format", "reptrack-plans")
    .put("version", 2)
    .put("plans", JSONArray().apply { plans.forEach { put(it.toJson()) } })
    .toString().toByteArray(Charsets.UTF_8)

internal fun decodePlanExport(json: ByteArray): List<TrainingPlan> {
    val root = JSONObject(json.toString(Charsets.UTF_8))
    require(root.optString("format") == "reptrack-plans" && root.optInt("version") in 1..2) { "Файл не является экспортом планов RepTrack" }
    val plans = root.requireArray("plans").toObjects(::parsePlan, max = 500)
    require(plans.map { it.id }.distinct().size == plans.size && plans.all { it.exercises.map(PlanExercise::id).distinct().size == it.exercises.size }) { "В файле повторяются ID планов или упражнений" }
    return plans
}

internal fun encodeResultExport(sessions: List<TrainingSession>): ByteArray {
    require(sessions.isNotEmpty()) { "Отчёт не содержит тренировок" }
    require(sessions.all { it.status == SessionStatus.COMPLETED && it.endedAt != null }) { "В отчёт можно добавить только завершённые тренировки" }
    require(sessions.map { it.ownerId }.distinct().size == 1) { "Отчёт может содержать историю только одного профиля" }
    return JSONObject().put("format", "reptrack-results").put("version", 1)
        .put("sessions", JSONArray().apply { sessions.forEach { put(it.toJson()) } })
        .toString().toByteArray(Charsets.UTF_8)
}

internal fun decodeResultExport(bytes: ByteArray): List<TrainingSession> {
    require(bytes.size in 1..maxExchangePayloadBytes) { "Отчёт слишком большой или пустой" }
    val root = JSONObject(bytes.toString(Charsets.UTF_8))
    require(root.optString("format") == "reptrack-results" && root.optInt("version") == 1) { "Неизвестный формат отчёта" }
    val sessions = root.requireArray("sessions").toObjects(::parseSession, max = 20_000)
    require(sessions.isNotEmpty()) { "Отчёт не содержит тренировок" }
    require(sessions.map { it.id }.distinct().size == sessions.size && sessions.map { it.ownerId }.distinct().size == 1 && sessions.all { it.status == SessionStatus.COMPLETED && it.endedAt != null }) { "В отчёте повторяются записи, смешаны профили или есть незавершённые тренировки" }
    return sessions
}

private fun TrainingPlan.toJson() = JSONObject()
    .put("id", id).put("name", name).put("position", position).put("inCycle", inCycle)
    .put("version", version)
    .put("exercises", JSONArray().apply { exercises.forEach { put(it.toJson()) } })

private fun PlanExercise.toJson() = JSONObject()
    .put("id", id).put("name", name).put("plannedSets", plannedSets).put("targetWeight", targetWeight)
    .put("targetReps", targetReps).put("restSeconds", restSeconds).put("tracksLoad", tracksLoad)

internal fun TrainingSession.toJson() = JSONObject()
    .put("id", id).put("planId", planId ?: JSONObject.NULL).put("planName", planName).put("status", status.name)
    .put("startedAt", startedAt).put("endedAt", endedAt ?: JSONObject.NULL).put("comment", comment).put("ownerId", ownerId).put("planVersion", planVersion)
    .put("exercises", JSONArray().apply { exercises.forEach { put(it.toJson()) } })

private fun ProgramAssignment.toJson() = JSONObject()
    .put("id", id).put("profileId", profileId).put("sourcePlanId", sourcePlanId).put("assignedPlanId", assignedPlanId)
    .put("version", version).put("pendingVersion", pendingVersion ?: JSONObject.NULL)
    .put("pendingAccepted", pendingAccepted).put("updatedAt", updatedAt)
    .put("pendingPlanJson", pendingPlanJson ?: JSONObject.NULL)

private fun DiaryProfile.toJson() = JSONObject()
    .put("id", id).put("name", name).put("kind", kind.name).put("createdAt", createdAt)
    .put("lastImportedAt", lastImportedAt ?: JSONObject.NULL)

private fun SessionExercise.toJson() = JSONObject()
    .put("id", id).put("name", name).put("position", position).put("skipped", skipped).put("tracksLoad", tracksLoad)
    .put("sets", JSONArray().apply { sets.forEach { put(it.toJson()) } })

private fun TrainingSet.toJson() = JSONObject()
    .put("id", id).put("position", position).put("weight", weight).put("reps", reps).put("kind", kind.name)
    .put("done", done).put("skipped", skipped).put("restSeconds", restSeconds)
    .put("restUntil", restUntil ?: JSONObject.NULL).put("restRemaining", restRemaining)

private fun parsePlan(json: JSONObject): TrainingPlan {
    val exercises = json.requireArray("exercises").toObjects(::parsePlanExercise, max = 500)
    val version = if (json.has("version")) json.nonNegative("version").coerceAtLeast(1) else 1
    return TrainingPlan(
        json.requireString("id", 128), json.requireString("name", 48), json.nonNegative("position"),
        json.optBoolean("inCycle"), exercises,
        if (json.has("ownerId")) json.requireString("ownerId", 128) else personalProfileId,
        version,
    )
}

private fun parseProfile(json: JSONObject): DiaryProfile {
    val kind = runCatching { ProfileKind.valueOf(json.requireString("kind", 16)) }.getOrElse { error("Неизвестный тип профиля") }
    return DiaryProfile(
        id = json.requireString("id", 128), name = json.requireString("name", 64), kind = kind,
        createdAt = json.nonNegativeLong("createdAt"),
        lastImportedAt = json.optionalNonNegativeLong("lastImportedAt"),
    )
}

private fun parseAssignment(json: JSONObject): ProgramAssignment {
    val pendingJson = if (json.isNull("pendingPlanJson")) null else json.requireString("pendingPlanJson", 2_000_000)
    val pendingVersion = json.optionalNonNegative("pendingVersion")
    val version = json.nonNegative("version").coerceAtLeast(1)
    require((pendingJson == null) == (pendingVersion == null)) { "В копии повреждено обновление программы" }
    require(pendingVersion == null || pendingVersion > version) { "В копии некорректна версия обновления" }
    val pendingPlan = pendingJson?.let { runCatching { decodePlanExport(it.toByteArray(Charsets.UTF_8)).singleOrNull() }.getOrNull() }
    require(pendingJson == null || pendingPlan != null) { "В копии повреждено обновление программы" }
    return ProgramAssignment(
        id = json.requireString("id", 128), profileId = json.requireString("profileId", 128),
        sourcePlanId = json.requireString("sourcePlanId", 128), assignedPlanId = json.requireString("assignedPlanId", 128),
        version = version, pendingVersion = pendingVersion,
        pendingAccepted = json.optBoolean("pendingAccepted"), updatedAt = json.nonNegativeLong("updatedAt"),
        planName = "", assignedPlanVersion = version, sourceVersion = version, pendingPlanJson = pendingJson, pendingPlan = pendingPlan,
    )
}

private fun parsePlanExercise(json: JSONObject): PlanExercise = PlanExercise(
    id = json.requireString("id", 128), name = json.requireString("name", 80),
    plannedSets = json.nonNegative("plannedSets").coerceIn(1, 20),
    targetWeight = json.requireString("targetWeight", 16).also { weight ->
        require(weight.isEmpty() || parseFiniteWeight(weight)?.let { it >= 0.0 } == true) { "В плане некорректный целевой вес" }
    },
    targetReps = json.requireString("targetReps", 8).also { reps ->
        require(reps.isEmpty() || reps.toIntOrNull()?.let { it >= 0 } == true) { "В плане некорректное число повторений" }
    },
    restSeconds = json.nonNegative("restSeconds").coerceAtMost(900),
    tracksLoad = json.optBoolean("tracksLoad", true),
)

internal fun parseSession(json: JSONObject): TrainingSession {
    val status = runCatching { SessionStatus.valueOf(json.requireString("status", 16)) }.getOrElse { error("Неизвестный статус тренировки") }
    val planId = if (json.isNull("planId")) null else json.requireString("planId", 128)
    return TrainingSession(
        id = json.requireString("id", 128), planId = planId, planName = json.requireString("planName", 80), status = status,
        startedAt = json.nonNegativeLong("startedAt"), endedAt = json.optionalNonNegativeLong("endedAt"),
        comment = json.requireString("comment", 1000), exercises = json.requireArray("exercises").toObjects(::parseSessionExercise, max = 1000),
        ownerId = if (json.has("ownerId")) json.requireString("ownerId", 128) else personalProfileId,
        planVersion = if (json.has("planVersion")) json.nonNegative("planVersion").coerceAtLeast(1) else 1,
    ).also { session ->
        require(session.status != SessionStatus.COMPLETED || session.endedAt != null && session.endedAt >= session.startedAt) { "В истории некорректные даты тренировки" }
        require(session.status == SessionStatus.ACTIVE || session.status == SessionStatus.COMPLETED || session.status == SessionStatus.CANCELLED) { "Неизвестный статус тренировки" }
    }
}

private fun parseSessionExercise(json: JSONObject): SessionExercise = SessionExercise(
    id = json.requireString("id", 128), name = json.requireString("name", 80), position = json.nonNegative("position"),
    skipped = json.optBoolean("skipped"), tracksLoad = json.optBoolean("tracksLoad", true),
    sets = json.requireArray("sets").toObjects(::parseSet, max = 1000),
)

private fun parseSet(json: JSONObject): TrainingSet {
    val kind = runCatching { SetKind.valueOf(json.requireString("kind", 16)) }.getOrElse { error("Неизвестный тип подхода") }
    val weight = json.requireString("weight", 16)
    val numericWeight = parseFiniteWeight(weight)
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
private fun JSONObject.optionalNonNegative(key: String): Int? {
    if (isNull(key)) return null
    return nonNegative(key)
}
private fun JSONObject.nonNegativeLong(key: String): Long = optLong(key, -1L).also { require(it >= 0L) { "В файле некорректно поле $key" } }
private fun JSONObject.optionalNonNegativeLong(key: String): Long? {
    if (isNull(key)) return null
    return nonNegativeLong(key)
}
