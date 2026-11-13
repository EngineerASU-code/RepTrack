package app.reptrack

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

enum class SetKind { WARMUP, WORK }
enum class SessionStatus { ACTIVE, COMPLETED, CANCELLED }
enum class WeightUnit { KG, LB }
enum class DiaryTheme { PURPLE_PRO, LIME_ENERGY, BLUE_TECH, ORANGE_POWER, LIGHT_CLEAN, GRAPHITE_MINIMAL }

data class DiarySettings(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val theme: DiaryTheme = DiaryTheme.PURPLE_PRO,
    val restTimerEnabled: Boolean = true,
)

data class PlanExercise(
    val id: String,
    val name: String,
    val plannedSets: Int = 3,
    val targetWeight: String = "",
    val targetReps: String = "",
    val restSeconds: Int = 90,
    val tracksLoad: Boolean = true,
)

data class TrainingPlan(val id: String, val name: String, val position: Int, val inCycle: Boolean, val exercises: List<PlanExercise>)

data class TrainingSet(
    val id: String,
    val position: Int,
    val weight: String,
    val reps: String,
    val kind: SetKind,
    val done: Boolean,
    val skipped: Boolean,
    val restSeconds: Int,
    val restUntil: Long?,
    val restRemaining: Long,
)

data class SessionExercise(val id: String, val name: String, val position: Int, val skipped: Boolean, val tracksLoad: Boolean, val sets: List<TrainingSet>)

data class TrainingSession(
    val id: String,
    val planId: String?,
    val planName: String,
    val status: SessionStatus,
    val startedAt: Long,
    val endedAt: Long?,
    val exercises: List<SessionExercise>,
    val comment: String = "",
) {
    val completedSets get() = exercises.filterNot { it.skipped }.sumOf { e -> e.sets.count { it.done && !it.skipped && it.kind == SetKind.WORK } }
    val totalSets get() = exercises.filterNot { it.skipped }.sumOf { e -> e.sets.count { !it.skipped && it.kind == SetKind.WORK } }
    val maxWeightKg get() = exercises.asSequence().filter { it.tracksLoad && !it.skipped }.flatMap { it.sets.asSequence() }
        .filter { it.done && !it.skipped && it.kind == SetKind.WORK }.mapNotNull { it.weight.toDoubleOrNull()?.takeIf { weight -> weight > 0.0 } }.maxOrNull()
}

data class DiarySnapshot(
    val plans: List<TrainingPlan> = emptyList(),
    val nextPlan: TrainingPlan? = null,
    val activeSession: TrainingSession? = null,
    val history: List<TrainingSession> = emptyList(),
    val workouts: Int = 0,
    val bestWeightKg: Double? = null,
    val bestByExercise: Map<String, Double> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null,
    val settings: DiarySettings = DiarySettings(),
)

/** Local-only SQLite storage. Every active session is stored as a snapshot, so later template edits cannot rewrite history. */
class DiaryRepository(context: Context, databaseName: String = "reptrack.db") {
    private val helper = DiaryDb(context.applicationContext, databaseName)

    fun load(): DiarySnapshot {
        val plans = helper.plans()
        val history = helper.history()
        val done = history
        val best = done.flatMap { it.exercises }.filter { it.tracksLoad }.flatMap { e -> e.sets.filter { it.done && !it.skipped && it.kind == SetKind.WORK }.map { e.name to (it.weight.toDoubleOrNull() ?: 0.0) } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.maxOrNull() ?: 0.0 }
        return DiarySnapshot(plans, helper.nextPlan(), helper.active(), history, history.size, best.values.maxOrNull(), best, false, settings = helper.settings())
    }

    fun start(planId: String?): String = helper.start(planId)
    fun updateSet(setId: String, weight: String? = null, reps: String? = null) = helper.updateSet(setId, weight, reps)
    fun toggleSet(set: TrainingSet) = helper.toggleSet(set)
    fun addSet(exerciseId: String) = helper.addSet(exerciseId)
    fun removeSet(id: String) = helper.removeSet(id)
    fun skipRest(id: String) = helper.skipRest(id)
    fun pauseRest(id: String) = helper.pauseRest(id)
    fun resumeRest(id: String) = helper.resumeRest(id)
    fun setKind(setId: String, kind: SetKind) = helper.setKind(setId, kind)
    fun skipExercise(exerciseId: String, skipped: Boolean) = helper.skipExercise(exerciseId, skipped)
    fun replaceExercise(id: String, replacement: PlanExercise) = helper.replaceExercise(id, replacement)
    fun finish(sessionId: String) = helper.finish(sessionId)
    fun cancel(sessionId: String) = helper.cancel(sessionId)
    fun updateComment(sessionId: String, comment: String) = helper.updateComment(sessionId, comment)
    fun renamePlan(id: String, name: String) = helper.renamePlan(id, name)
    fun movePlan(id: String, offset: Int) = helper.movePlan(id, offset)
    fun createPlan(name: String) = helper.createPlan(name)
    fun createStarterPlans() = helper.createStarterPlans()
    fun duplicatePlan(id: String) = helper.duplicatePlan(id)
    fun deletePlan(id: String) = helper.deletePlan(id)
    fun setPlanInCycle(id: String, included: Boolean) = helper.setPlanInCycle(id, included)
    fun addExercise(planId: String, name: String) = helper.addExercise(planId, name)
    fun updateExercise(exercise: PlanExercise) = helper.updateExercise(exercise)
    fun moveExercise(id: String, planId: String, offset: Int) = helper.moveExercise(id, planId, offset)
    fun deleteExercise(id: String) = helper.deleteExercise(id)
    fun setWeightUnit(unit: WeightUnit) = helper.setSetting("weight_unit", unit.name)
    fun setTheme(theme: DiaryTheme) = helper.setSetting("theme", theme.name)
    fun setRestTimerEnabled(enabled: Boolean) = helper.setSetting("rest_timer_enabled", enabled.toString())
    fun createEncryptedBackup(password: String): ByteArray {
        val plainText = encodeDiaryArchive(helper.plans(), helper.allSessions(), helper.settings())
        val secret = password.toCharArray()
        return try { encryptBackup(plainText, secret) } finally { secret.fill('\u0000'); plainText.fill(0) }
    }
    fun restoreEncryptedBackup(container: ByteArray, password: String) {
        val secret = password.toCharArray()
        val plainText = try { decryptBackup(container, secret) } finally { secret.fill('\u0000') }
        try { helper.restoreDiary(decodeDiaryArchive(plainText)) } finally { plainText.fill(0) }
    }
    fun exportPlans(): ByteArray = encodePlanExport(helper.plans())
    fun importPlans(json: ByteArray) = helper.importPlans(decodePlanExport(json))
    fun close() = helper.close()
}

internal class DiaryDb(context: Context, databaseName: String = "reptrack.db") : SQLiteOpenHelper(context, databaseName, null, 2) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE plans(id TEXT PRIMARY KEY, name TEXT NOT NULL, position INTEGER NOT NULL, in_cycle INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE plan_exercises(id TEXT PRIMARY KEY, plan_id TEXT NOT NULL REFERENCES plans(id) ON DELETE CASCADE, position INTEGER NOT NULL, name TEXT NOT NULL, planned_sets INTEGER NOT NULL, target_weight TEXT NOT NULL, target_reps TEXT NOT NULL, rest_seconds INTEGER NOT NULL, tracks_load INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY, plan_id TEXT REFERENCES plans(id) ON DELETE SET NULL, plan_name TEXT NOT NULL, status TEXT NOT NULL, started_at INTEGER NOT NULL, ended_at INTEGER, comment TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE session_exercises(id TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, source_id TEXT, position INTEGER NOT NULL, name TEXT NOT NULL, skipped INTEGER NOT NULL DEFAULT 0, tracks_load INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE session_sets(id TEXT PRIMARY KEY, exercise_id TEXT NOT NULL REFERENCES session_exercises(id) ON DELETE CASCADE, position INTEGER NOT NULL, weight TEXT NOT NULL, reps TEXT NOT NULL, kind TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, skipped INTEGER NOT NULL DEFAULT 0, rest_seconds INTEGER NOT NULL, rest_until INTEGER, rest_remaining INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE UNIQUE INDEX one_active_session ON sessions(status) WHERE status='ACTIVE'")
        db.execSQL("CREATE INDEX session_history_order ON sessions(status,ended_at DESC)")
        db.execSQL("CREATE TABLE app_settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        insertDefaultSettings(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE app_settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            insertDefaultSettings(db)
        }
    }

    private fun insertDefaultSettings(db: SQLiteDatabase) {
        listOf("weight_unit" to "KG", "theme" to "PURPLE_PRO", "rest_timer_enabled" to "true").forEach { (key, value) ->
            db.insertWithOnConflict("app_settings", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun settings(): DiarySettings {
        val values = mutableMapOf<String, String>()
        readableDatabase.rawQuery("SELECT key,value FROM app_settings", null).use { c -> while (c.moveToNext()) values[c.s("key")] = c.s("value") }
        return DiarySettings(
            weightUnit = runCatching { WeightUnit.valueOf(values["weight_unit"] ?: "KG") }.getOrDefault(WeightUnit.KG),
            theme = runCatching { DiaryTheme.valueOf(values["theme"] ?: "PURPLE_PRO") }.getOrDefault(DiaryTheme.PURPLE_PRO),
            restTimerEnabled = values["rest_timer_enabled"]?.toBooleanStrictOrNull() ?: true,
        )
    }

    fun setSetting(key: String, value: String) {
        val db = writableDatabase
        db.insertWithOnConflict("app_settings", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE)
        if (key == "rest_timer_enabled" && value == "false") {
            db.execSQL("UPDATE session_sets SET rest_until=NULL,rest_remaining=0 WHERE exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE')")
        }
    }

    fun createStarterPlans() {
        val seed = listOf(
            "Push" to listOf("Жим угловой", "Хаммер низ груди", "Тяга к поясу", "Трицепс", "Плечи стоя, задняя дельта", "Бицепс Скотт (суперсет)"),
            "Legs" to listOf("Разминка, ходьба в гору", "Жим ногами", "Сгибания ног", "Разгибания ног", "Подъём на носки стоя"),
            "Pull" to listOf("Подтягивания / тяга", "Тяга нижнего блока", "Тяга спины Hammer", "Тяга на трицепс", "Молотки с гантелями", "Плечи Smith"),
            "Up" to listOf("Жим груди в наклоне", "Smith дельта", "Разводка груди Butterfly", "Тяга верхнего блока", "Бицепс стоя со штангой", "Беговая дорожка"),
        )
        val db = writableDatabase
        db.beginTransaction()
        try {
        if (plans().isNotEmpty()) { db.setTransactionSuccessful(); return }
        seed.forEachIndexed { position, (name, exercises) ->
            val id = UUID.randomUUID().toString()
            db.insertOrThrow("plans", null, ContentValues().apply { put("id", id); put("name", name); put("position", position); put("in_cycle", 1) })
            exercises.forEachIndexed { order, exercise ->
                db.insertOrThrow("plan_exercises", null, ContentValues().apply {
                    put("id", UUID.randomUUID().toString()); put("plan_id", id); put("position", order); put("name", exercise)
                    put("planned_sets", 3); put("target_weight", ""); put("target_reps", ""); put("rest_seconds", 90); put("tracks_load", if (exercise.contains("ходьба", true) || exercise.contains("дорожка", true)) 0 else 1)
                })
            }
        }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun plans(): List<TrainingPlan> = readableDatabase.rawQuery("SELECT id,name,position,in_cycle FROM plans ORDER BY position", null).use { c ->
        buildList { while (c.moveToNext()) add(TrainingPlan(c.s("id"), c.s("name"), c.i("position"), c.i("in_cycle") != 0, exercises(c.s("id")))) }
    }

    private fun exercises(planId: String): List<PlanExercise> = readableDatabase.rawQuery("SELECT id,name,planned_sets,target_weight,target_reps,rest_seconds,tracks_load FROM plan_exercises WHERE plan_id=? ORDER BY position", arrayOf(planId)).use { c ->
        buildList { while (c.moveToNext()) add(PlanExercise(c.s("id"), c.s("name"), c.i("planned_sets"), c.s("target_weight"), c.s("target_reps"), c.i("rest_seconds"), c.i("tracks_load") != 0)) }
    }

    fun nextPlan(): TrainingPlan? {
        val cycle = plans().filter { it.inCycle }
        if (cycle.isEmpty()) return null
        val last = readableDatabase.rawQuery("SELECT plan_id FROM sessions WHERE status='COMPLETED' AND plan_id IN (SELECT id FROM plans WHERE in_cycle=1) ORDER BY ended_at DESC LIMIT 1", null).use { if (it.moveToFirst()) it.getString(0) else null }
        return cycle[nextCyclePosition(cycle.map { it.id }, last)]
    }

    fun start(planId: String?): String {
        val db = writableDatabase
        db.beginTransaction()
        try {
            active()?.let { db.setTransactionSuccessful(); return it.id }
            val plan = plans().firstOrNull { it.id == planId } ?: nextPlan() ?: plans().firstOrNull()
                ?: error("Сначала создайте тренировочный план")
            val id = UUID.randomUUID().toString()
            db.insertOrThrow("sessions", null, ContentValues().apply { put("id", id); put("plan_id", plan.id); put("plan_name", plan.name); put("status", "ACTIVE"); put("started_at", System.currentTimeMillis()); put("comment", "") })
            plan.exercises.forEachIndexed { position, source ->
                val exerciseId = UUID.randomUUID().toString()
                db.insertOrThrow("session_exercises", null, ContentValues().apply { put("id", exerciseId); put("session_id", id); put("source_id", source.id); put("position", position); put("name", source.name); put("tracks_load", if (source.tracksLoad) 1 else 0) })
                repeat(source.plannedSets.coerceIn(1, 20)) { setPosition ->
                    db.insertOrThrow("session_sets", null, ContentValues().apply { put("id", UUID.randomUUID().toString()); put("exercise_id", exerciseId); put("position", setPosition); put("weight", source.targetWeight); put("reps", source.targetReps); put("kind", "WORK"); put("rest_seconds", source.restSeconds) })
                }
            }
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }

    fun active(): TrainingSession? = readableDatabase.rawQuery("SELECT id,plan_id,plan_name,status,started_at,ended_at,comment FROM sessions WHERE status='ACTIVE' ORDER BY started_at LIMIT 1", null).use { if (it.moveToFirst()) session(it) else null }

    fun history(): List<TrainingSession> = readableDatabase.rawQuery("SELECT id,plan_id,plan_name,status,started_at,ended_at,comment FROM sessions WHERE status='COMPLETED' ORDER BY ended_at DESC", null).use { c -> buildList { while (c.moveToNext()) add(session(c)) } }

    fun allSessions(): List<TrainingSession> = readableDatabase.rawQuery("SELECT id,plan_id,plan_name,status,started_at,ended_at,comment FROM sessions ORDER BY started_at", null).use { c -> buildList { while (c.moveToNext()) add(session(c)) } }

    fun restoreDiary(archive: ArchivedDiary) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("sessions", null, null)
            db.delete("plans", null, null)
            archive.plans.forEach { plan ->
                db.insertOrThrow("plans", null, ContentValues().apply { put("id", plan.id); put("name", plan.name); put("position", plan.position); put("in_cycle", if (plan.inCycle) 1 else 0) })
                plan.exercises.forEachIndexed { index, exercise ->
                    db.insertOrThrow("plan_exercises", null, ContentValues().apply {
                        put("id", exercise.id); put("plan_id", plan.id); put("position", index); put("name", exercise.name)
                        put("planned_sets", exercise.plannedSets); put("target_weight", exercise.targetWeight); put("target_reps", exercise.targetReps)
                        put("rest_seconds", exercise.restSeconds); put("tracks_load", if (exercise.tracksLoad) 1 else 0)
                    })
                }
            }
            archive.sessions.forEach { session ->
                db.insertOrThrow("sessions", null, ContentValues().apply {
                    put("id", session.id); put("plan_id", session.planId); put("plan_name", session.planName); put("status", session.status.name)
                    put("started_at", session.startedAt); if (session.endedAt == null) putNull("ended_at") else put("ended_at", session.endedAt)
                    put("comment", session.comment)
                })
                session.exercises.forEach { exercise ->
                    db.insertOrThrow("session_exercises", null, ContentValues().apply {
                        put("id", exercise.id); put("session_id", session.id); put("position", exercise.position); put("name", exercise.name)
                        put("skipped", if (exercise.skipped) 1 else 0); put("tracks_load", if (exercise.tracksLoad) 1 else 0)
                    })
                    exercise.sets.forEach { set ->
                        db.insertOrThrow("session_sets", null, ContentValues().apply {
                            put("id", set.id); put("exercise_id", exercise.id); put("position", set.position); put("weight", set.weight); put("reps", set.reps)
                            put("kind", set.kind.name); put("done", if (set.done) 1 else 0); put("skipped", if (set.skipped) 1 else 0); put("rest_seconds", set.restSeconds)
                            if (set.restUntil == null) putNull("rest_until") else put("rest_until", set.restUntil)
                            put("rest_remaining", set.restRemaining)
                        })
                    }
                }
            }
            setSetting("weight_unit", archive.settings.weightUnit.name)
            setSetting("theme", archive.settings.theme.name)
            setSetting("rest_timer_enabled", archive.settings.restTimerEnabled.toString())
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun importPlans(imported: List<TrainingPlan>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            var position = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM plans", null).use { it.moveToFirst(); it.getInt(0) }
            imported.forEach { source ->
                val id = UUID.randomUUID().toString()
                db.insertOrThrow("plans", null, ContentValues().apply { put("id", id); put("name", "${source.name} (импорт)".take(48)); put("position", position++); put("in_cycle", 0) })
                source.exercises.forEachIndexed { order, exercise ->
                    db.insertOrThrow("plan_exercises", null, ContentValues().apply {
                        put("id", UUID.randomUUID().toString()); put("plan_id", id); put("position", order); put("name", exercise.name)
                        put("planned_sets", exercise.plannedSets); put("target_weight", exercise.targetWeight); put("target_reps", exercise.targetReps)
                        put("rest_seconds", exercise.restSeconds); put("tracks_load", if (exercise.tracksLoad) 1 else 0)
                    })
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun session(c: Cursor): TrainingSession {
        val id = c.s("id")
        val exercises = readableDatabase.rawQuery("SELECT id,name,position,skipped,tracks_load FROM session_exercises WHERE session_id=? ORDER BY position", arrayOf(id)).use { ec ->
            buildList {
                while (ec.moveToNext()) {
                    val exerciseId = ec.s("id")
                    val sets = readableDatabase.rawQuery("SELECT id,position,weight,reps,kind,done,skipped,rest_seconds,rest_until,rest_remaining FROM session_sets WHERE exercise_id=? ORDER BY position", arrayOf(exerciseId)).use { sc ->
                        buildList { while (sc.moveToNext()) add(TrainingSet(sc.s("id"), sc.i("position"), sc.s("weight"), sc.s("reps"), runCatching { SetKind.valueOf(sc.s("kind")) }.getOrDefault(SetKind.WORK), sc.i("done") != 0, sc.i("skipped") != 0, sc.i("rest_seconds"), if (sc.isNull(sc.index("rest_until"))) null else sc.getLong(sc.index("rest_until")), sc.l("rest_remaining"))) }
                    }
                    add(SessionExercise(exerciseId, ec.s("name"), ec.i("position"), ec.i("skipped") != 0, ec.i("tracks_load") != 0, sets))
                }
            }
        }
        return TrainingSession(id, if (c.isNull(c.index("plan_id"))) null else c.s("plan_id"), c.s("plan_name"), SessionStatus.valueOf(c.s("status")), c.l("started_at"), if (c.isNull(c.index("ended_at"))) null else c.l("ended_at"), exercises, c.s("comment"))
    }

    fun updateSet(id: String, weight: String?, reps: String?) {
        val values = ContentValues(); weight?.let { values.put("weight", it.replace(',', '.').take(8)) }; reps?.let { values.put("reps", it.take(5)) }
        writableDatabase.update("session_sets", values, "id=? AND exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE')", arrayOf(id))
    }

    fun toggleSet(set: TrainingSet) {
        val done = !set.done
        val restOn = settings().restTimerEnabled
        writableDatabase.update("session_sets", ContentValues().apply { put("done", if (done) 1 else 0); put("rest_until", if (done && restOn && set.restSeconds > 0) System.currentTimeMillis() + set.restSeconds * 1000L else null); put("rest_remaining", 0) }, "id=? AND exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE')", arrayOf(set.id))
    }

    fun setKind(id: String, kind: SetKind) { writableDatabase.update("session_sets", ContentValues().apply { put("kind", kind.name); if (kind == SetKind.WARMUP) { put("done", 0); putNull("rest_until"); put("rest_remaining", 0) } }, "id=? AND exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE')", arrayOf(id)) }

    fun addSet(exerciseId: String) {
        val db = writableDatabase
        val next = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM session_sets WHERE exercise_id=?", arrayOf(exerciseId)).use { it.moveToFirst(); it.getInt(0) }
        val rest = db.rawQuery("SELECT rest_seconds FROM session_sets WHERE exercise_id=? LIMIT 1", arrayOf(exerciseId)).use { if (it.moveToFirst()) it.getInt(0) else 90 }
        val active = db.rawQuery("SELECT 1 FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE e.id=? AND s.status='ACTIVE'", arrayOf(exerciseId)).use { it.moveToFirst() }
        if (active) db.insertOrThrow("session_sets", null, ContentValues().apply { put("id", UUID.randomUUID().toString()); put("exercise_id", exerciseId); put("position", next); put("weight", ""); put("reps", ""); put("kind", "WORK"); put("rest_seconds", rest) })
    }

    fun removeSet(id: String) { writableDatabase.delete("session_sets", "id=? AND exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE')", arrayOf(id)) }
    fun skipRest(id: String) { writableDatabase.update("session_sets", ContentValues().apply { putNull("rest_until"); put("rest_remaining", 0) }, "id=? AND exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE')", arrayOf(id)) }
    fun pauseRest(id: String) {
        val db = writableDatabase
        db.execSQL("UPDATE session_sets SET rest_remaining=MAX(0,rest_until-?),rest_until=NULL WHERE id=? AND rest_until IS NOT NULL AND exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE')", arrayOf<Any>(System.currentTimeMillis(), id))
    }
    fun resumeRest(id: String) {
        val db = writableDatabase
        db.execSQL("UPDATE session_sets SET rest_until=?+rest_remaining,rest_remaining=0 WHERE id=? AND rest_until IS NULL AND rest_remaining>0 AND exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE')", arrayOf<Any>(System.currentTimeMillis(), id))
    }

    fun skipExercise(id: String, skipped: Boolean) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.update("session_exercises", ContentValues().apply { put("skipped", if (skipped) 1 else 0) }, "id=? AND session_id IN (SELECT id FROM sessions WHERE status='ACTIVE')", arrayOf(id))
            db.update("session_sets", ContentValues().apply { put("skipped", if (skipped) 1 else 0) }, "exercise_id=? AND exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE')", arrayOf(id))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun replaceExercise(id: String, replacement: PlanExercise) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.update("session_exercises", ContentValues().apply { put("name", replacement.name); putNull("source_id"); put("tracks_load", if (replacement.tracksLoad) 1 else 0); put("skipped", 0) }, "id=? AND session_id IN (SELECT id FROM sessions WHERE status='ACTIVE')", arrayOf(id))
            db.update("session_sets", ContentValues().apply { put("weight", ""); put("reps", ""); put("done", 0); put("skipped", 0); put("kind", SetKind.WORK.name); put("rest_seconds", replacement.restSeconds); putNull("rest_until"); put("rest_remaining", 0) }, "exercise_id=? AND exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE')", arrayOf(id))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun finish(id: String) { writableDatabase.update("sessions", ContentValues().apply { put("status", "COMPLETED"); put("ended_at", System.currentTimeMillis()) }, "id=? AND status='ACTIVE'", arrayOf(id)) }
    fun cancel(id: String) { writableDatabase.update("sessions", ContentValues().apply { put("status", "CANCELLED"); put("ended_at", System.currentTimeMillis()) }, "id=? AND status='ACTIVE'", arrayOf(id)) }
    fun updateComment(id: String, comment: String) { writableDatabase.update("sessions", ContentValues().apply { put("comment", comment.take(1000)) }, "id=? AND status='ACTIVE'", arrayOf(id)) }

    fun renamePlan(id: String, name: String) { val safe = name.trim().take(48); if (safe.isNotEmpty()) writableDatabase.update("plans", ContentValues().apply { put("name", safe) }, "id=?", arrayOf(id)) }
    fun createPlan(name: String) {
        val safe = name.trim().take(48); if (safe.isEmpty()) return
        val db = writableDatabase
        val position = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM plans", null).use { it.moveToFirst(); it.getInt(0) }
        db.insertOrThrow("plans", null, ContentValues().apply { put("id", UUID.randomUUID().toString()); put("name", safe); put("position", position); put("in_cycle", 0) })
    }
    fun duplicatePlan(id: String) {
        val source = plans().firstOrNull { it.id == id } ?: return
        val db = writableDatabase
        val position = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM plans", null).use { it.moveToFirst(); it.getInt(0) }
        val copyId = UUID.randomUUID().toString()
        db.beginTransaction()
        try {
            db.insertOrThrow("plans", null, ContentValues().apply { put("id", copyId); put("name", "${source.name} копия".take(48)); put("position", position); put("in_cycle", 0) })
            source.exercises.forEachIndexed { order, exercise ->
                db.insertOrThrow("plan_exercises", null, ContentValues().apply { put("id", UUID.randomUUID().toString()); put("plan_id", copyId); put("position", order); put("name", exercise.name); put("planned_sets", exercise.plannedSets); put("target_weight", exercise.targetWeight); put("target_reps", exercise.targetReps); put("rest_seconds", exercise.restSeconds); put("tracks_load", if (exercise.tracksLoad) 1 else 0) })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun deletePlan(id: String) {
        if (active()?.planId == id) return
        val db = writableDatabase
        db.beginTransaction()
        try { db.delete("plan_exercises", "plan_id=?", arrayOf(id)); db.delete("plans", "id=?", arrayOf(id)); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
    fun setPlanInCycle(id: String, included: Boolean) {
        val position = if (included) plans().filter { it.inCycle }.maxOfOrNull { it.position }?.plus(1) ?: 0 else 0
        writableDatabase.update("plans", ContentValues().apply { put("in_cycle", if (included) 1 else 0); if (included) put("position", position) }, "id=?", arrayOf(id))
    }
    fun movePlan(id: String, offset: Int) {
        val db = writableDatabase
        val rows = plans().toMutableList(); val from = rows.indexOfFirst { it.id == id }
        if (from < 0 || rows.isEmpty()) return
        val to = (from + offset).coerceIn(0, rows.lastIndex)
        if (from == to) return
        val moved = rows.removeAt(from); rows.add(to, moved); db.beginTransaction()
        try { rows.forEachIndexed { pos, plan -> db.update("plans", ContentValues().apply { put("position", pos) }, "id=?", arrayOf(plan.id)) }; db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
    fun addExercise(planId: String, name: String) {
        val trimmed = name.trim().take(80); if (trimmed.isEmpty()) return
        val position = writableDatabase.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM plan_exercises WHERE plan_id=?", arrayOf(planId)).use { it.moveToFirst(); it.getInt(0) }
        val tracksLoad = !(trimmed.contains("ходьба", true) || trimmed.contains("дорожка", true))
        writableDatabase.insertOrThrow("plan_exercises", null, ContentValues().apply { put("id", UUID.randomUUID().toString()); put("plan_id", planId); put("position", position); put("name", trimmed); put("planned_sets", 3); put("target_weight", ""); put("target_reps", ""); put("rest_seconds", 90); put("tracks_load", if (tracksLoad) 1 else 0) })
    }
    fun updateExercise(exercise: PlanExercise) {
        writableDatabase.update("plan_exercises", ContentValues().apply { put("name", exercise.name.trim().take(80)); put("planned_sets", exercise.plannedSets.coerceIn(1, 20)); put("target_weight", exercise.targetWeight.replace(',', '.').take(8)); put("target_reps", exercise.targetReps.filter(Char::isDigit).take(4)); put("rest_seconds", exercise.restSeconds.coerceIn(0, 900)); put("tracks_load", if (exercise.tracksLoad) 1 else 0) }, "id=?", arrayOf(exercise.id))
    }
    fun deleteExercise(id: String) { writableDatabase.delete("plan_exercises", "id=?", arrayOf(id)) }
    fun moveExercise(id: String, planId: String, offset: Int) {
        val db = writableDatabase
        val rows = exercises(planId).toMutableList(); val from = rows.indexOfFirst { it.id == id }
        if (from < 0 || rows.isEmpty()) return
        val to = (from + offset).coerceIn(0, rows.lastIndex); if (from == to) return
        val moved = rows.removeAt(from); rows.add(to, moved)
        db.beginTransaction()
        try { rows.forEachIndexed { position, exercise -> db.update("plan_exercises", ContentValues().apply { put("position", position) }, "id=?", arrayOf(exercise.id)) }; db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
}

private fun Cursor.index(name: String) = getColumnIndexOrThrow(name)
private fun Cursor.s(name: String) = getString(index(name))
private fun Cursor.i(name: String) = getInt(index(name))
private fun Cursor.l(name: String) = getLong(index(name))
