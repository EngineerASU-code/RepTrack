package app.reptrack

import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

internal const val sourceReportsProfileId = "source-reports-owner"

/** Only standalone weight × reps tokens are interpreted. Full transcription stays in the asset. */
internal fun decodeSourceWorkoutReports(bytes: ByteArray): List<TrainingSession> {
    val root = JSONObject(bytes.toString(Charsets.UTF_8))
    require(root.getString("format") == "reptrack-source-reports" && root.getInt("version") == 1)
    val reports = root.getJSONArray("reports")
    val seen = mutableSetOf<String>()
    return buildList {
        for (index in 0 until reports.length()) {
            val report = reports.getJSONObject(index)
            val file = report.getString("file")
            require(file.matches(Regex("[0-9]{8}\\.jpg")) && seen.add(file))
            if (report.has("duplicateOf")) continue
            val date = LocalDate.parse(file.substringBefore('.'), DateTimeFormatter.ofPattern("ddMMuuuu"))
            // Date-only source: UTC noon is a sorting anchor, not a recorded workout time.
            val day = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
            val items = report.getJSONArray("exercises")
            val comments = mutableListOf("Источник: database/$file. Дата из имени файла; время и длительность неизвестны. Типы подходов не указаны: числовые записи отмечены рабочими для тестового отображения.")
            report.optString("note").takeIf { it.isNotBlank() }?.let(comments::add)
            val exercises = (0 until items.length()).map { exerciseIndex ->
                val item = items.getJSONArray(exerciseIndex)
                val name = item.getString(0)
                val raw = item.getString(1)
                comments.add("$name: $raw")
                val strength = listOf("Кардио", "ходьба", "Мобильность", "Бассейн", "МФР").none { name.contains(it, true) }
                // ponytail: supersets/dropsets stay as source text until grouped sets are supported.
                val pairs = if (!strength || raw.contains('+') || raw.contains("шнурки")) emptyList() else raw.split(',').mapNotNull { token ->
                    Regex("^([0-9]+(?:\\.[0-9]+)?)\\s*[xхXХ×]\\s*([0-9]+)$").matchEntire(token.trim())
                        ?.let { it.groupValues[1] to it.groupValues[2] }
                }
                SessionExercise(sourceId("$file/exercise/$exerciseIndex"), name, exerciseIndex, false, strength,
                    pairs.mapIndexed { setIndex, pair -> TrainingSet(sourceId("$file/$exerciseIndex/$setIndex"), setIndex,
                        pair.first, pair.second, SetKind.WORK, true, false, 0, null, 0) })
            }
            add(TrainingSession(sourceId(file), null, report.getString("title"), SessionStatus.COMPLETED, day, day,
                exercises, comments.joinToString("\n").take(1000), sourceReportsProfileId))
        }
    }
}

private fun sourceId(key: String): String = UUID.nameUUIDFromBytes("reptrack/source-reports/v1/$key".toByteArray(Charsets.UTF_8)).toString()
