package app.reptrack

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.json.JSONObject

internal sealed interface TrainingSnapshotScope {
    data class SingleSession(val sessionId: String) : TrainingSnapshotScope
    data class DateRange(val startDate: LocalDate, val endDateInclusive: LocalDate) : TrainingSnapshotScope
}

internal data class TrainingSnapshot(
    val id: String,
    val createdAt: Long,
    val scope: TrainingSnapshotScope,
    val sessions: List<TrainingSession>,
)

private const val snapshotFormat = "reptrack-training-snapshot"
private const val snapshotVersion = 1
private val snapshotIdPattern = Regex("[A-Za-z0-9_-]{1,128}")

internal fun newTrainingSnapshotId(): String = java.util.UUID.randomUUID().toString()

internal fun encodeTrainingSnapshot(snapshot: TrainingSnapshot): ByteArray {
    validateTrainingSnapshot(snapshot)
    val result = JSONObject(encodeResultExport(snapshot.sessions).toString(Charsets.UTF_8))
    val scope = when (val value = snapshot.scope) {
        is TrainingSnapshotScope.SingleSession -> JSONObject()
            .put("type", "session")
            .put("sessionId", value.sessionId)
        is TrainingSnapshotScope.DateRange -> JSONObject()
            .put("type", "dateRange")
            .put("startDate", value.startDate.toString())
            .put("endDateInclusive", value.endDateInclusive.toString())
    }
    val bytes = JSONObject()
        .put("format", snapshotFormat)
        .put("version", snapshotVersion)
        .put("id", snapshot.id)
        .put("createdAt", snapshot.createdAt)
        .put("scope", scope)
        .put("result", result)
        .toString()
        .toByteArray(Charsets.UTF_8)
    require(bytes.size in 1..maxExchangePayloadBytes) { "Снимок слишком большой" }
    return bytes
}

internal fun decodeTrainingSnapshot(bytes: ByteArray): TrainingSnapshot {
    require(bytes.size in 1..maxExchangePayloadBytes) { "Снимок слишком большой или пустой" }
    val root = JSONObject(bytes.toString(Charsets.UTF_8))
    require(root.optString("format") == snapshotFormat && root.optInt("version") == snapshotVersion) { "Неизвестный формат снимка" }
    val scopeJson = root.optJSONObject("scope") ?: error("В снимке отсутствует охват")
    val scope = when (scopeJson.optString("type")) {
        "session" -> TrainingSnapshotScope.SingleSession(scopeJson.requireSnapshotString("sessionId", 128))
        "dateRange" -> TrainingSnapshotScope.DateRange(
            LocalDate.parse(scopeJson.requireSnapshotString("startDate", 10)),
            LocalDate.parse(scopeJson.requireSnapshotString("endDateInclusive", 10)),
        )
        else -> error("Неизвестный охват снимка")
    }
    val result = root.optJSONObject("result") ?: error("В снимке отсутствуют тренировки")
    val sessions = decodeResultExport(result.toString().toByteArray(Charsets.UTF_8))
    return TrainingSnapshot(
        id = root.requireSnapshotString("id", 128),
        createdAt = root.optLong("createdAt", -1L).also { require(it >= 0L) { "В снимке некорректная дата создания" } },
        scope = scope,
        sessions = sessions,
    ).also(::validateTrainingSnapshot)
}

private fun validateTrainingSnapshot(snapshot: TrainingSnapshot) {
    require(snapshot.id.matches(snapshotIdPattern)) { "Некорректный ID снимка" }
    require(snapshot.createdAt >= 0L) { "Некорректная дата создания снимка" }
    require(snapshot.sessions.isNotEmpty()) { "Снимок не содержит тренировок" }
    require(
        snapshot.sessions.all {
            it.status == SessionStatus.COMPLETED && it.endedAt != null
        },
    ) { "В снимок можно добавить только завершённые тренировки" }
    require(snapshot.sessions.map { it.id }.distinct().size == snapshot.sessions.size) { "В снимке повторяются ID тренировок" }
    when (val scope = snapshot.scope) {
        is TrainingSnapshotScope.SingleSession -> {
            require(scope.sessionId.isNotBlank() && scope.sessionId.length <= 128) { "Некорректный ID тренировки в охвате" }
            require(snapshot.sessions.size == 1 && snapshot.sessions.single().id == scope.sessionId) {
                "Содержимое не совпадает с охватом снимка"
            }
        }
        is TrainingSnapshotScope.DateRange -> {
            require(!scope.endDateInclusive.isBefore(scope.startDate)) { "Некорректный диапазон дат" }
            require(
                snapshot.sessions.all { session ->
                    val date = Instant.ofEpochMilli(session.endedAt!!).atZone(ZoneOffset.UTC).toLocalDate()
                    date >= scope.startDate && date <= scope.endDateInclusive
                },
            ) { "В снимке есть тренировка вне диапазона дат" }
        }
    }
}

private fun JSONObject.requireSnapshotString(key: String, max: Int): String {
    val value = opt(key)
    require(value is String && value.isNotBlank() && value.length <= max) { "В снимке некорректное поле $key" }
    return value
}
