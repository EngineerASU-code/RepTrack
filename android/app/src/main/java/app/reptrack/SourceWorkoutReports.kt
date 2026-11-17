package app.reptrack

import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal const val demoReportsProfileId = "demo-workout-reports"

/** Only standalone weight × reps tokens are interpreted from the synthetic debug asset. */
internal fun decodeDemoWorkoutReports(bytes: ByteArray): List<TrainingSession> {
    val root = JSONObject(bytes.toString(Charsets.UTF_8))
    require(root.getString("format") == "reptrack-demo-workouts" && root.getInt("version") == 1)
    val reports = root.getJSONArray("reports")
    val seen = mutableSetOf<String>()
    return buildList {
        for (index in 0 until reports.length()) {
            val report = reports.getJSONObject(index)
            val reportId = report.getString("id")
            require(reportId.matches(Regex("[a-z0-9-]{1,64}")) && seen.add(reportId))
            val date = LocalDate.parse(report.getString("date"))
            // Demo dates are fixed only to make ordering repeatable.
            val day = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
            val items = report.getJSONArray("exercises")
            val comments = mutableListOf("Синтетический демонстрационный пример; дата, время и длительность условны.")
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
                SessionExercise(demoId("$reportId/exercise/$exerciseIndex"), name, exerciseIndex, false, strength,
                    pairs.mapIndexed { setIndex, pair -> TrainingSet(demoId("$reportId/$exerciseIndex/$setIndex"), setIndex,
                        pair.first, pair.second, SetKind.WORK, true, false, 0, null, 0) })
            }
            add(TrainingSession(demoId(reportId), null, report.getString("title"), SessionStatus.COMPLETED, day, day,
                exercises, comments.joinToString("\n").take(1000), demoReportsProfileId))
        }
    }
}

private fun demoId(key: String): String = UUID.nameUUIDFromBytes("reptrack/demo-workouts/v1/$key".toByteArray(Charsets.UTF_8)).toString()
