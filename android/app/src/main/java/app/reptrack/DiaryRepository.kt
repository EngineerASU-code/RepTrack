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
enum class ProfileKind { PERSONAL, TRAINEE }

internal const val personalProfileId = "local-owner"

data class DiaryProfile(
    val id: String,
    val name: String,
    val kind: ProfileKind,
    val createdAt: Long,
    val completedWorkouts: Int = 0,
    val lastCompletedAt: Long? = null,
    val lastImportedAt: Long? = null,
)

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

data class TrainingPlan(
    val id: String,
    val name: String,
    val position: Int,
    val inCycle: Boolean,
    val exercises: List<PlanExercise>,
    val ownerId: String = personalProfileId,
    val version: Int = 1,
)

data class ProgramAssignment(
    val id: String,
    val profileId: String,
    val sourcePlanId: String,
    val assignedPlanId: String,
    val version: Int,
    val pendingVersion: Int?,
    val pendingAccepted: Boolean,
    val updatedAt: Long,
    val planName: String,
    val assignedPlanVersion: Int,
    val sourceVersion: Int,
    val pendingPlanJson: String? = null,
    val pendingPlan: TrainingPlan? = null,
)

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
    val ownerId: String = personalProfileId,
    val planVersion: Int = 1,
) {
    val completedSets get() = exercises.filterNot { it.skipped }.sumOf { e -> e.sets.count { it.done && !it.skipped && it.kind == SetKind.WORK } }
    val totalSets get() = exercises.filterNot { it.skipped }.sumOf { e -> e.sets.count { !it.skipped && it.kind == SetKind.WORK } }
    val maxWeightKg get() = exercises.asSequence().filter { it.tracksLoad && !it.skipped }.flatMap { it.sets.asSequence() }
        .filter { it.done && !it.skipped && it.kind == SetKind.WORK }.mapNotNull { parseFiniteWeight(it.weight)?.takeIf { weight -> weight > 0.0 } }.maxOrNull()
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
    val profiles: List<DiaryProfile> = emptyList(),
    val activeProfileId: String = personalProfileId,
    val activeProfile: DiaryProfile? = null,
    val assignments: List<ProgramAssignment> = emptyList(),
    val allPlans: List<TrainingPlan> = emptyList(),
    val trainerPlans: List<TrainingPlan> = emptyList(),
    val trustedLinks: List<TrustedLinkSummary> = emptyList(),
    val pendingPairings: List<PairingPending> = emptyList(),
)

/** Local-only SQLite storage. Every active session is stored as a snapshot, so later template edits cannot rewrite history. */
class DiaryRepository(context: Context, databaseName: String = "reptrack.db") {
    private val appContext = context.applicationContext
    private val helper = DiaryDb(context.applicationContext, databaseName)

    fun load(): DiarySnapshot {
        helper.removeExpiredPairings(System.currentTimeMillis())
        val activeProfile = helper.activeProfile()
        val profiles = helper.profiles()
        val plans = helper.plans()
        val history = helper.history()
        val best = history.asSequence().flatMap { it.exercises.asSequence() }.filter { it.tracksLoad }
            .flatMap { e -> e.sets.asSequence().filter { it.done && !it.skipped && it.kind == SetKind.WORK }
                .mapNotNull { set -> parseFiniteWeight(set.weight)?.takeIf { it > 0.0 }?.let { e.name to it } } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.maxOrNull()!! }
        return DiarySnapshot(
            plans = plans,
            nextPlan = helper.nextPlan(),
            activeSession = helper.active(),
            history = history,
            workouts = history.size,
            bestWeightKg = best.values.maxOrNull(),
            bestByExercise = best,
            loading = false,
            settings = helper.settings(),
            profiles = profiles,
            activeProfileId = activeProfile.id,
            activeProfile = activeProfile,
            assignments = helper.assignments(),
            trainerPlans = helper.personalPlans(),
            allPlans = helper.allPlans(),
            trustedLinks = helper.trustedLinks().map { it.summary() },
            pendingPairings = helper.pendingPairings(),
        )
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
    fun createTraineeProfile(name: String) = helper.createTraineeProfile(name)
    fun renameProfile(id: String, name: String) = helper.renameProfile(id, name)
    fun setActiveProfile(id: String) = helper.setActiveProfile(id)
    internal fun createDemoProfile() {
        check(appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            "Тестовые данные доступны только в debug-сборке"
        }
        val name = "Демо · тестовые данные"
        val profile = helper.profiles().firstOrNull { it.kind == ProfileKind.TRAINEE && it.name == name }
        if (profile == null) helper.createTraineeProfile(name) else helper.setActiveProfile(profile.id)

        fun plan(name: String, exercises: List<String>): TrainingPlan {
            val current = helper.plans().firstOrNull { it.name == name }
            val plan = current ?: run {
                helper.createPlan(name)
                helper.plans().first { it.name == name }
            }
            exercises.filterNot { exercise -> plan.exercises.any { it.name == exercise } }
                .forEach { helper.addExercise(plan.id, it) }
            return helper.plans().first { it.id == plan.id }
        }

        val push = plan("Демо · Push", listOf("Жим лёжа", "Тяга верхнего блока"))
        val legs = plan("Демо · Ноги", listOf("Приседания", "Жим ногами"))
        val sessions = listOf(
            push.id to listOf(40.0, 30.0),
            legs.id to listOf(60.0, 40.0),
            push.id to listOf(45.0, 32.5),
            legs.id to listOf(65.0, 42.5),
        )
        if (helper.active() != null) return
        val existing = helper.history().size.coerceAtMost(sessions.size)
        val now = System.currentTimeMillis()
        for (index in existing until sessions.size) {
            val (planId, baseWeights) = sessions[index]
            val sessionId = helper.start(planId)
            val active = helper.active() ?: error("Не удалось создать демо-тренировку")
            active.exercises.forEachIndexed { exerciseIndex, exercise ->
                val baseWeight = baseWeights.getOrElse(exerciseIndex) { 40.0 }
                exercise.sets.forEachIndexed { setIndex, set ->
                    val weight = (baseWeight - setIndex * 2.5).toString()
                    val reps = (10 - setIndex).toString()
                    helper.updateSet(set.id, weight, reps)
                    helper.toggleSet(set.copy(weight = weight, reps = reps))
                    helper.skipRest(set.id)
                }
            }
            helper.updateComment(sessionId, "Демо-запись для проверки истории и прогресса")
            helper.finish(sessionId)
            val startedAt = now - (28 - index * 7) * 86_400_000L
            helper.writableDatabase.update(
                "sessions",
                ContentValues().apply { put("started_at", startedAt); put("ended_at", startedAt + 45 * 60_000L) },
                "id=?",
                arrayOf(sessionId),
            )
        }
    }
    fun assignPlanToProfile(planId: String, profileId: String) = helper.assignPlanToProfile(planId, profileId)
    fun stageAssignmentUpdate(assignmentId: String) = helper.stageAssignmentUpdate(assignmentId)
    fun acceptAssignmentUpdate(assignmentId: String) = helper.acceptAssignmentUpdate(assignmentId)
    fun rejectAssignmentUpdate(assignmentId: String) = helper.rejectAssignmentUpdate(assignmentId)
    internal fun deviceId() = helper.deviceId()
    internal fun trustedLinks(profileId: String = helper.activeProfile().id) = helper.trustedLinks(profileId)
    internal fun pendingPairings() = helper.pendingPairings()
    internal fun createPairingInvitation(profileId: String = helper.activeProfile().id): ByteArray {
        val profile = helper.profile(profileId) ?: error("Профиль не найден")
        require(helper.trustedLinks(profile.id).isEmpty()) { "Сначала отзовите существующую связь для этого профиля" }
        val now = System.currentTimeMillis()
        val inviteId = UUID.randomUUID().toString()
        val linkId = UUID.randomUUID().toString()
        val deviceId = helper.deviceId()
        val pair = ExchangeCrypto.newEphemeralKeyPair()
        val privateKey = ExchangeCrypto.wrapPrivateKey(pair.private, linkId)
        try {
            helper.removeExpiredPairings(now)
            helper.addPendingPairing(PairingPending(inviteId, linkId, profile.id, "OUTGOING", null, null, now + exchangeTtlMs), privateKey)
        } finally { privateKey.fill(0) }
        return ExchangeCrypto.encodePairing(PairingFile("request", inviteId, linkId, deviceId, java.util.Base64.getEncoder().encodeToString(pair.public.encoded), now, now + exchangeTtlMs))
    }
    internal fun respondToPairingInvite(bytes: ByteArray, profileId: String = helper.activeProfile().id): PairingResponse {
        val invite = ExchangeCrypto.decodePairing(bytes)
        require(invite.kind == "request") { "Ожидалось приглашение для создания связи" }
        val deviceId = helper.deviceId()
        require(invite.deviceId != deviceId) { "Нельзя связать устройство само с собой" }
        require(helper.profile(profileId)?.kind == ProfileKind.PERSONAL) { "Для связи выбери личный профиль" }
        require(helper.trustedLinks(profileId).isEmpty()) { "Сначала отзови существующую связь для этого профиля" }
        val now = System.currentTimeMillis()
        val pair = ExchangeCrypto.newEphemeralKeyPair()
        val secret = ExchangeCrypto.deriveLinkKey(pair.private, java.util.Base64.getDecoder().decode(invite.publicKey), invite.linkId, deviceId, invite.deviceId)
        val fingerprint = ExchangeCrypto.fingerprint(secret)
        val wrapped = try { ExchangeCrypto.wrapSecret(secret, invite.linkId) } finally { secret.fill(0) }
        try { helper.addIncomingPairing(PairingPending(invite.inviteId, invite.linkId, profileId, "INCOMING", invite.deviceId, fingerprint, invite.expiresAt), wrapped, now) }
        finally { wrapped.fill(0) }
        val reply = PairingFile("response", invite.inviteId, invite.linkId, deviceId, java.util.Base64.getEncoder().encodeToString(pair.public.encoded), now, invite.expiresAt)
        return PairingResponse(ExchangeCrypto.encodePairing(reply), fingerprint)
    }
    internal fun processPairingResponse(bytes: ByteArray): PairingPending {
        val reply = ExchangeCrypto.decodePairing(bytes)
        require(reply.kind == "response") { "Ожидался ответ на приглашение" }
        val pending = helper.pendingPairing(reply.inviteId) ?: error("Приглашение не найдено или уже обработано")
        require(pending.role == "OUTGOING" && pending.linkId == reply.linkId && pending.expiresAt == reply.expiresAt) { "Ответ не соответствует приглашению" }
        val deviceId = helper.deviceId()
        require(reply.deviceId != deviceId) { "Нельзя связать устройство само с собой" }
        val wrappedPrivateKey = helper.pendingPrivateKey(reply.inviteId) ?: error("Срок приглашения истёк")
        val key = ExchangeCrypto.unwrapPrivateKey(wrappedPrivateKey, reply.linkId)
        val secret = ExchangeCrypto.deriveLinkKey(key, java.util.Base64.getDecoder().decode(reply.publicKey), reply.linkId, deviceId, reply.deviceId)
        val fingerprint = ExchangeCrypto.fingerprint(secret)
        val wrapped = try { ExchangeCrypto.wrapSecret(secret, reply.linkId) } finally { secret.fill(0); wrappedPrivateKey.fill(0) }
        try { helper.completeOutgoingPairing(reply.inviteId, reply.deviceId, fingerprint, wrapped) }
        finally { wrapped.fill(0) }
        return helper.pendingPairing(reply.inviteId) ?: error("Ответ на приглашение не сохранён")
    }
    internal fun confirmPairing(inviteId: String) = helper.confirmPairing(inviteId)
    internal fun revokeLink(linkId: String) = helper.revokeLink(linkId)
    internal fun createResultPackage(linkId: String, sessionIds: Set<String>): ByteArray {
        require(sessionIds.isNotEmpty()) { "Выбери тренировки для отправки" }
        val link = helper.trustedLink(linkId) ?: error("Связь не найдена")
        val sessions = helper.completedSessions(link.localProfileId).filter { it.id in sessionIds }
        require(sessions.size == sessionIds.size) { "Часть выбранных тренировок уже недоступна" }
        val now = System.currentTimeMillis()
        val payload = encodeResultExport(sessions)
        val secret = ExchangeCrypto.unwrapSecret(link.wrappedSecret, link.id)
        return try { ExchangeCrypto.encrypt(ExchangeHeader(UUID.randomUUID().toString(), link.id, helper.deviceId(), link.remoteDeviceId, "result", now, now + exchangeTtlMs), secret, payload) }
        finally { secret.fill(0); payload.fill(0) }
    }
    internal fun createTelegramRouteFile(linkId: String): ByteArray {
        val link = helper.trustedLink(linkId) ?: error("Связь не найдена")
        val profile = helper.profile(link.localProfileId) ?: error("Профиль связи не найден")
        return encodeTelegramRoute(link, profile.kind, helper.deviceId())
    }
    internal fun createTelegramDelivery(linkId: String, sessionId: String): ByteArray {
        val link = helper.trustedLink(linkId) ?: error("Связь не найдена")
        require(helper.profile(link.localProfileId)?.kind == ProfileKind.PERSONAL) { "Отчёт можно отправить только из профиля подопечного" }
        val session = helper.completedSessions(link.localProfileId).singleOrNull { it.id == sessionId } ?: error("Завершённая тренировка не найдена")
        val encrypted = createResultPackage(linkId, setOf(sessionId))
        val pdf = WorkoutReportPdf.create(session, helper.profile(link.localProfileId)?.name ?: "Подопечный", helper.settings().weightUnit)
        return try { encodeTelegramDelivery(ExchangeCrypto.peekHeader(encrypted), session, pdf, encrypted) }
        finally { encrypted.fill(0); pdf.fill(0) }
    }
    internal fun finishAndCreateTelegramDelivery(linkId: String, sessionId: String): ByteArray {
        val link = helper.trustedLink(linkId) ?: error("Связь не найдена")
        require(helper.profile(link.localProfileId)?.kind == ProfileKind.PERSONAL) { "Отчёт можно отправить только подопечному" }
        require(helper.active()?.id == sessionId) { "Активная тренировка не найдена" }
        helper.finish(sessionId)
        return createTelegramDelivery(linkId, sessionId)
    }
    internal fun createProgramPackage(linkId: String, planId: String): ByteArray {
        val link = helper.trustedLink(linkId) ?: error("Связь не найдена")
        require(helper.profile(link.localProfileId)?.kind == ProfileKind.TRAINEE) { "Связь не относится к профилю подопечного" }
        val plan = helper.allPlans().firstOrNull { it.id == planId && it.ownerId == link.localProfileId } ?: error("План не принадлежит профилю этой связи")
        val payload = encodePlanExport(listOf(plan))
        val now = System.currentTimeMillis()
        val secret = ExchangeCrypto.unwrapSecret(link.wrappedSecret, link.id)
        return try { ExchangeCrypto.encrypt(ExchangeHeader(UUID.randomUUID().toString(), link.id, helper.deviceId(), link.remoteDeviceId, "program", now, now + exchangeTtlMs, programVersion = plan.version), secret, payload) }
        finally { secret.fill(0); payload.fill(0) }
    }
    internal fun previewExchange(bytes: ByteArray): ExchangePreview {
        val header = ExchangeCrypto.peekHeader(bytes)
        val link = helper.trustedLink(header.linkId) ?: error("Для этого пакета нет активной доверенной связи")
        require(header.senderDeviceId == link.remoteDeviceId && header.recipientDeviceId == helper.deviceId()) { "Пакет адресован другому устройству или отправителю" }
        val secret = ExchangeCrypto.unwrapSecret(link.wrappedSecret, link.id)
        val decrypted = try { ExchangeCrypto.decrypt(bytes, secret) } finally { secret.fill(0) }
        return try { when (header.type) {
            "result" -> {
                val sessions = decodeResultExport(decrypted.payload)
                val duplicate = helper.packageImported(header.packageId)
                val dates = sessions.mapNotNull { it.endedAt }.sorted()
                val destination = helper.profile(link.localProfileId)?.name ?: "профиля"
                ExchangePreview(header.packageId, "result", "Отчёт о тренировках", "Профиль: $destination · ${if (dates.isEmpty()) "${sessions.size} завершённых тренировок" else "${sessions.size} тренировок · ${exchangeDateRange(dates.first(), dates.last())}"}", sessions.size, alreadyImported = duplicate)
            }
            "program" -> {
                val plan = decodePlanExport(decrypted.payload).singleOrNull() ?: error("В пакете должна быть одна программа")
                require(plan.version == header.programVersion) { "Версия программы в заголовке не совпадает с содержимым" }
                val duplicate = helper.packageImported(header.packageId)
                val current = helper.incomingProgram(link.id, plan.id)
                val currentPlan = current?.localPlanId?.let(helper::plan)
                require(duplicate || current == null || plan.version > (current.pendingVersion ?: current.appliedVersion)) { "Получена устаревшая версия программы" }
                val destination = helper.profile(link.localProfileId)?.name ?: "профиля"
                ExchangePreview(header.packageId, "program", plan.name, "Профиль: $destination · версия ${plan.version} · ${plan.exercises.size} упражнений", plan.exercises.size, plan, currentPlan?.name, currentPlan != null && currentPlan.version != current.appliedLocalVersion, duplicate, planChanges(currentPlan, plan))
            }
            else -> error("Неподдерживаемый тип пакета")
        } } finally { decrypted.payload.fill(0) }
    }
    internal fun acceptExchange(bytes: ByteArray): ExchangeImportResult {
        val preview = previewExchange(bytes)
        require(!preview.alreadyImported) { "Этот пакет уже был обработан" }
        val header = ExchangeCrypto.peekHeader(bytes)
        val link = helper.trustedLink(header.linkId) ?: error("Связь отозвана")
        val secret = ExchangeCrypto.unwrapSecret(link.wrappedSecret, link.id)
        val payload = try { ExchangeCrypto.decrypt(bytes, secret).payload } finally { secret.fill(0) }
        return try { when (header.type) {
            "result" -> ExchangeImportResult(importedSessions = helper.importResults(header.packageId, link.id, link.localProfileId, decodeResultExport(payload)))
            "program" -> {
                val plan = decodePlanExport(payload).singleOrNull() ?: error("В пакете должна быть одна программа")
                ExchangeImportResult(planName = plan.name, deferred = helper.acceptIncomingProgram(header, link, plan))
            }
            else -> error("Неподдерживаемый тип пакета")
        } } finally { payload.fill(0) }
    }
    internal fun rejectExchange(bytes: ByteArray) {
        val preview = previewExchange(bytes)
        require(preview.type == "program") { "Отклонять можно только обновление программы" }
        val header = ExchangeCrypto.peekHeader(bytes)
        helper.recordRejectedPackage(header.packageId, header.linkId)
    }
    fun createEncryptedBackup(password: String): ByteArray {
        val plainText = encodeDiaryArchive(helper.allPlans(), helper.allSessions(), helper.settings(), helper.profiles(), helper.activeProfile().id, helper.assignments(), helper.acceptedIncomingPrograms())
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

private fun exchangeDateRange(start: Long, end: Long): String {
    val format = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.forLanguageTag("ru"))
    return "${format.format(java.util.Date(start))} — ${format.format(java.util.Date(end))}"
}

private fun planChanges(current: TrainingPlan?, incoming: TrainingPlan): List<String> {
    if (current == null) return listOf("Новая программа будет добавлена отдельным планом.")
    return describePlanChanges(current, incoming)
}

internal class DiaryDb(context: Context, databaseName: String = "reptrack.db") : SQLiteOpenHelper(context, databaseName, null, 7) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE profiles(id TEXT PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL, created_at INTEGER NOT NULL, last_imported_at INTEGER)")
        db.insertOrThrow("profiles", null, ContentValues().apply { put("id", personalProfileId); put("name", "Мой дневник"); put("kind", ProfileKind.PERSONAL.name); put("created_at", System.currentTimeMillis()) })
        db.execSQL("CREATE TABLE plans(id TEXT PRIMARY KEY, name TEXT NOT NULL, position INTEGER NOT NULL, in_cycle INTEGER NOT NULL DEFAULT 1, owner_id TEXT NOT NULL REFERENCES profiles(id), version INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE plan_exercises(id TEXT PRIMARY KEY, plan_id TEXT NOT NULL REFERENCES plans(id) ON DELETE CASCADE, position INTEGER NOT NULL, name TEXT NOT NULL, planned_sets INTEGER NOT NULL, target_weight TEXT NOT NULL, target_reps TEXT NOT NULL, rest_seconds INTEGER NOT NULL, tracks_load INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY, plan_id TEXT REFERENCES plans(id) ON DELETE SET NULL, owner_id TEXT NOT NULL REFERENCES profiles(id), plan_name TEXT NOT NULL, plan_version INTEGER NOT NULL DEFAULT 1, status TEXT NOT NULL, started_at INTEGER NOT NULL, ended_at INTEGER, comment TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE session_exercises(id TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, source_id TEXT, position INTEGER NOT NULL, name TEXT NOT NULL, skipped INTEGER NOT NULL DEFAULT 0, tracks_load INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE session_sets(id TEXT PRIMARY KEY, exercise_id TEXT NOT NULL REFERENCES session_exercises(id) ON DELETE CASCADE, position INTEGER NOT NULL, weight TEXT NOT NULL, reps TEXT NOT NULL, kind TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, skipped INTEGER NOT NULL DEFAULT 0, rest_seconds INTEGER NOT NULL, rest_until INTEGER, rest_remaining INTEGER NOT NULL DEFAULT 0)")
        createAssignmentTable(db)
        createExchangeTables(db)
        createArchivedIncomingTable(db)
        db.execSQL("CREATE UNIQUE INDEX one_active_session ON sessions(owner_id) WHERE status='ACTIVE'")
        db.execSQL("CREATE INDEX session_history_order ON sessions(status,ended_at DESC)")
        db.execSQL("CREATE TABLE app_settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        insertDefaultSettings(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE app_settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            insertDefaultSettings(db)
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE profiles(id TEXT PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL, created_at INTEGER NOT NULL, last_imported_at INTEGER)")
            db.insertOrThrow("profiles", null, ContentValues().apply { put("id", personalProfileId); put("name", "Мой дневник"); put("kind", ProfileKind.PERSONAL.name); put("created_at", System.currentTimeMillis()) })
            db.execSQL("ALTER TABLE plans ADD COLUMN owner_id TEXT REFERENCES profiles(id)")
            db.execSQL("ALTER TABLE sessions ADD COLUMN owner_id TEXT REFERENCES profiles(id)")
            db.execSQL("UPDATE plans SET owner_id='local-owner'")
            db.execSQL("UPDATE sessions SET owner_id='local-owner'")
            db.execSQL("DROP INDEX IF EXISTS one_active_session")
            db.execSQL("CREATE UNIQUE INDEX one_active_session ON sessions(owner_id) WHERE status='ACTIVE'")
            db.execSQL("INSERT OR IGNORE INTO app_settings(key,value) VALUES('active_profile_id','local-owner')")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE plans ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE sessions ADD COLUMN plan_version INTEGER NOT NULL DEFAULT 1")
            createAssignmentTable(db)
        }
        if (oldVersion < 5) createExchangeTables(db)
        if (oldVersion == 5) {
            val aliases = db.rawQuery("SELECT private_alias FROM pairing_pending WHERE private_alias IS NOT NULL", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            aliases.forEach { runCatching { ExchangeCrypto.deleteEphemeralKey(it) } }
            db.execSQL("ALTER TABLE pairing_pending ADD COLUMN private_key_wrapped BLOB")
            db.delete("pairing_pending", null, null)
        }
        if (oldVersion < 7) createArchivedIncomingTable(db)
    }

    private fun createAssignmentTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE program_assignments(id TEXT PRIMARY KEY, profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE, source_plan_id TEXT NOT NULL, assigned_plan_id TEXT NOT NULL UNIQUE REFERENCES plans(id) ON DELETE CASCADE, version INTEGER NOT NULL, pending_version INTEGER, pending_plan_json TEXT, pending_accepted INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL, UNIQUE(profile_id,source_plan_id))")
        db.execSQL("CREATE INDEX program_assignments_profile ON program_assignments(profile_id,updated_at DESC)")
    }

    private fun createExchangeTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE exchange_identity(id INTEGER PRIMARY KEY CHECK(id=1),device_id TEXT NOT NULL)")
        db.execSQL("CREATE TABLE pairing_pending(invite_id TEXT PRIMARY KEY,link_id TEXT NOT NULL,local_profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,role TEXT NOT NULL,remote_device_id TEXT,private_key_wrapped BLOB,wrapped_secret BLOB,fingerprint TEXT,created_at INTEGER NOT NULL,expires_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE consumed_invites(invite_id TEXT PRIMARY KEY,consumed_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE trusted_links(id TEXT PRIMARY KEY,local_profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,remote_device_id TEXT NOT NULL,wrapped_secret BLOB NOT NULL,fingerprint TEXT NOT NULL,created_at INTEGER NOT NULL,UNIQUE(local_profile_id,remote_device_id))")
        db.execSQL("CREATE TABLE imported_packages(package_id TEXT PRIMARY KEY,link_id TEXT NOT NULL,imported_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE imported_events(event_id TEXT PRIMARY KEY,owner_id TEXT NOT NULL REFERENCES profiles(id),package_id TEXT NOT NULL REFERENCES imported_packages(package_id))")
        db.execSQL("CREATE TABLE incoming_programs(link_id TEXT NOT NULL,remote_plan_id TEXT NOT NULL,local_plan_id TEXT,applied_version INTEGER NOT NULL DEFAULT 0,applied_local_version INTEGER NOT NULL DEFAULT 0,pending_version INTEGER,pending_plan_json TEXT,pending_package_id TEXT,pending_accepted INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL,PRIMARY KEY(link_id,remote_plan_id))")
    }

    private fun createArchivedIncomingTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE archived_incoming_programs(profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,remote_plan_id TEXT NOT NULL,local_plan_id TEXT,applied_version INTEGER NOT NULL,applied_local_version INTEGER NOT NULL,pending_version INTEGER NOT NULL,plan_json TEXT NOT NULL,package_id TEXT NOT NULL,PRIMARY KEY(profile_id,remote_plan_id))")
    }

    fun acceptedIncomingPrograms(): List<ArchivedIncomingProgram> = readableDatabase.rawQuery(
        "SELECT l.local_profile_id,r.remote_plan_id,r.local_plan_id,r.applied_version,r.applied_local_version,r.pending_version,r.pending_plan_json,r.pending_package_id FROM incoming_programs r JOIN trusted_links l ON l.id=r.link_id WHERE r.pending_accepted=1", null,
    ).use { c -> buildList { while (c.moveToNext()) add(ArchivedIncomingProgram(c.s("local_profile_id"), c.s("remote_plan_id"), if (c.isNull(2)) null else c.s("local_plan_id"), c.i("applied_version"), c.i("applied_local_version"), c.i("pending_version"), c.s("pending_plan_json"), c.s("pending_package_id"))) } } + readableDatabase.rawQuery(
        "SELECT profile_id,remote_plan_id,local_plan_id,applied_version,applied_local_version,pending_version,plan_json,package_id FROM archived_incoming_programs", null,
    ).use { c -> buildList { while (c.moveToNext()) add(ArchivedIncomingProgram(c.s("profile_id"), c.s("remote_plan_id"), if (c.isNull(2)) null else c.s("local_plan_id"), c.i("applied_version"), c.i("applied_local_version"), c.i("pending_version"), c.s("plan_json"), c.s("package_id"))) } }

    private fun insertDefaultSettings(db: SQLiteDatabase) {
        listOf("weight_unit" to "KG", "theme" to "PURPLE_PRO", "rest_timer_enabled" to "true", "active_profile_id" to personalProfileId).forEach { (key, value) ->
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

    fun profiles(): List<DiaryProfile> = readableDatabase.rawQuery(
        "SELECT p.id,p.name,p.kind,p.created_at,p.last_imported_at,COUNT(s.id) AS workout_count,MAX(s.ended_at) AS last_completed_at FROM profiles p LEFT JOIN sessions s ON s.owner_id=p.id AND s.status='COMPLETED' GROUP BY p.id,p.name,p.kind,p.created_at,p.last_imported_at ORDER BY CASE p.kind WHEN 'PERSONAL' THEN 0 ELSE 1 END,p.name COLLATE NOCASE",
        null,
    ).use { c -> buildList {
        while (c.moveToNext()) add(DiaryProfile(
            id = c.s("id"), name = c.s("name"),
            kind = runCatching { ProfileKind.valueOf(c.s("kind")) }.getOrDefault(ProfileKind.TRAINEE),
            createdAt = c.l("created_at"), completedWorkouts = c.i("workout_count"),
            lastCompletedAt = if (c.isNull(c.index("last_completed_at"))) null else c.l("last_completed_at"),
            lastImportedAt = if (c.isNull(c.index("last_imported_at"))) null else c.l("last_imported_at"),
        ))
    } }

    fun activeProfile(): DiaryProfile {
        val rows = profiles()
        val id = readableDatabase.rawQuery("SELECT value FROM app_settings WHERE key='active_profile_id'", null).use { if (it.moveToFirst()) it.getString(0) else null }
        return rows.firstOrNull { it.id == id } ?: rows.firstOrNull { it.kind == ProfileKind.PERSONAL }
            ?.also { setSetting("active_profile_id", it.id) } ?: error("Не найден личный профиль")
    }

    private fun activeOwnerId() = activeProfile().id

    fun deviceId(): String {
        readableDatabase.rawQuery("SELECT device_id FROM exchange_identity WHERE id=1", null).use { if (it.moveToFirst()) return it.getString(0) }
        val id = UUID.randomUUID().toString()
        writableDatabase.insertWithOnConflict("exchange_identity", null, ContentValues().apply { put("id", 1); put("device_id", id) }, SQLiteDatabase.CONFLICT_IGNORE)
        return readableDatabase.rawQuery("SELECT device_id FROM exchange_identity WHERE id=1", null).use { it.moveToFirst(); it.getString(0) }
    }

    fun trustedLinks(profileId: String): List<TrustedLink> = readableDatabase.rawQuery(
        "SELECT id,local_profile_id,remote_device_id,wrapped_secret,fingerprint,created_at FROM trusted_links WHERE local_profile_id=? ORDER BY created_at DESC", arrayOf(profileId),
    ).use { c -> buildList { while (c.moveToNext()) add(TrustedLink(c.s("id"), c.s("local_profile_id"), c.s("remote_device_id"), c.getBlob(c.index("wrapped_secret")), c.s("fingerprint"), c.l("created_at"))) } }

    fun trustedLinks(): List<TrustedLink> = readableDatabase.rawQuery(
        "SELECT id,local_profile_id,remote_device_id,wrapped_secret,fingerprint,created_at FROM trusted_links ORDER BY created_at DESC", null,
    ).use { c -> buildList { while (c.moveToNext()) add(TrustedLink(c.s("id"), c.s("local_profile_id"), c.s("remote_device_id"), c.getBlob(c.index("wrapped_secret")), c.s("fingerprint"), c.l("created_at"))) } }

    fun trustedLink(linkId: String): TrustedLink? = readableDatabase.rawQuery(
        "SELECT id,local_profile_id,remote_device_id,wrapped_secret,fingerprint,created_at FROM trusted_links WHERE id=?", arrayOf(linkId),
    ).use { c -> if (c.moveToFirst()) TrustedLink(c.s("id"), c.s("local_profile_id"), c.s("remote_device_id"), c.getBlob(c.index("wrapped_secret")), c.s("fingerprint"), c.l("created_at")) else null }

    fun profile(id: String): DiaryProfile? = profiles().firstOrNull { it.id == id }

    fun plan(id: String): TrainingPlan? = plansFor(null).firstOrNull { it.id == id }

    fun completedSessions(profileId: String): List<TrainingSession> = readableDatabase.rawQuery(
        "SELECT id,plan_id,owner_id,plan_name,plan_version,status,started_at,ended_at,comment FROM sessions WHERE owner_id=? AND status='COMPLETED' ORDER BY ended_at DESC", arrayOf(profileId),
    ).use { c -> buildList { while (c.moveToNext()) add(session(c)) } }

    fun packageImported(packageId: String): Boolean = readableDatabase.rawQuery("SELECT 1 FROM imported_packages WHERE package_id=?", arrayOf(packageId)).use { it.moveToFirst() }

    fun incomingProgram(linkId: String, remotePlanId: String): IncomingProgramState? = readableDatabase.rawQuery(
        "SELECT local_plan_id,applied_version,applied_local_version,pending_version,pending_accepted,pending_package_id FROM incoming_programs WHERE link_id=? AND remote_plan_id=?", arrayOf(linkId, remotePlanId),
    ).use { c -> if (!c.moveToFirst()) null else IncomingProgramState(
        if (c.isNull(0)) null else c.getString(0), c.getInt(1), c.getInt(2), if (c.isNull(3)) null else c.getInt(3), c.getInt(4) != 0, if (c.isNull(5)) null else c.getString(5),
    ) }

    fun pendingPairings(): List<PairingPending> = readableDatabase.rawQuery(
        "SELECT invite_id,link_id,local_profile_id,role,remote_device_id,fingerprint,expires_at FROM pairing_pending ORDER BY created_at", null,
    ).use { c -> buildList { while (c.moveToNext()) add(PairingPending(c.s("invite_id"), c.s("link_id"), c.s("local_profile_id"), c.s("role"), if (c.isNull(c.index("remote_device_id"))) null else c.s("remote_device_id"), if (c.isNull(c.index("fingerprint"))) null else c.s("fingerprint"), c.l("expires_at"))) } }

    fun pendingPairing(inviteId: String): PairingPending? = pendingPairings().firstOrNull { it.inviteId == inviteId }

    fun pendingPrivateKey(inviteId: String): ByteArray? = readableDatabase.rawQuery("SELECT private_key_wrapped FROM pairing_pending WHERE invite_id=?", arrayOf(inviteId)).use {
        if (it.moveToFirst() && !it.isNull(0)) it.getBlob(0) else null
    }

    fun removeExpiredPairings(now: Long) {
        writableDatabase.delete("pairing_pending", "expires_at<=?", arrayOf(now.toString()))
    }

    fun addPendingPairing(pairing: PairingPending, privateKey: ByteArray) {
        writableDatabase.insertOrThrow("pairing_pending", null, ContentValues().apply {
            put("invite_id", pairing.inviteId); put("link_id", pairing.linkId); put("local_profile_id", pairing.localProfileId)
            put("role", pairing.role); putNull("remote_device_id"); put("private_key_wrapped", privateKey); putNull("wrapped_secret")
            putNull("fingerprint"); put("created_at", System.currentTimeMillis()); put("expires_at", pairing.expiresAt)
        })
    }

    fun addIncomingPairing(pairing: PairingPending, secret: ByteArray, now: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow("consumed_invites", null, ContentValues().apply { put("invite_id", pairing.inviteId); put("consumed_at", now) })
            db.insertOrThrow("pairing_pending", null, ContentValues().apply {
                put("invite_id", pairing.inviteId); put("link_id", pairing.linkId); put("local_profile_id", pairing.localProfileId)
                put("role", pairing.role); put("remote_device_id", pairing.remoteDeviceId); putNull("private_key_wrapped")
                put("wrapped_secret", secret); put("fingerprint", pairing.fingerprint); put("created_at", now); put("expires_at", pairing.expiresAt)
            })
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun completeOutgoingPairing(inviteId: String, remoteDeviceId: String, fingerprint: String, secret: ByteArray) {
        val count = writableDatabase.update("pairing_pending", ContentValues().apply {
            put("remote_device_id", remoteDeviceId); put("fingerprint", fingerprint); put("wrapped_secret", secret); putNull("private_key_wrapped")
        }, "invite_id=? AND role='OUTGOING' AND wrapped_secret IS NULL", arrayOf(inviteId))
        check(count == 1) { "Ответ на приглашение уже обработан" }
    }

    fun confirmPairing(inviteId: String) {
        val pending = readableDatabase.rawQuery("SELECT link_id,local_profile_id,remote_device_id,wrapped_secret,fingerprint,created_at,expires_at FROM pairing_pending WHERE invite_id=?", arrayOf(inviteId)).use { c ->
            if (!c.moveToFirst()) null else PairingPending(inviteId, c.s("link_id"), c.s("local_profile_id"), "", if (c.isNull(c.index("remote_device_id"))) null else c.s("remote_device_id"), if (c.isNull(c.index("fingerprint"))) null else c.s("fingerprint"), c.l("expires_at")) to c.getBlob(c.index("wrapped_secret"))
        } ?: error("Связь не найдена")
        val profile = pending.first
        val remoteDeviceId = profile.remoteDeviceId ?: error("Ответ на приглашение ещё не получен")
        val fingerprint = profile.fingerprint ?: error("Сначала обработай ответ на приглашение")
        val wrappedSecret = pending.second ?: error("Ключ связи не найден")
        require(profile.expiresAt > System.currentTimeMillis()) { "Срок подтверждения истёк" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow("trusted_links", null, ContentValues().apply {
                put("id", profile.linkId); put("local_profile_id", profile.localProfileId); put("remote_device_id", remoteDeviceId)
                put("wrapped_secret", wrappedSecret); put("fingerprint", fingerprint); put("created_at", System.currentTimeMillis())
            })
            db.delete("pairing_pending", "invite_id=?", arrayOf(inviteId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun revokeLink(linkId: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("incoming_programs", "link_id=?", arrayOf(linkId))
            db.delete("trusted_links", "id=?", arrayOf(linkId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun importResults(packageId: String, linkId: String, ownerId: String, sessions: List<TrainingSession>): Int {
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(!packageImported(packageId)) { "Этот пакет уже был обработан" }
            require(profile(ownerId) != null && trustedLink(linkId)?.localProfileId == ownerId) { "Профиль связи больше не существует" }
            db.insertOrThrow("imported_packages", null, ContentValues().apply { put("package_id", packageId); put("link_id", linkId); put("imported_at", System.currentTimeMillis()) })
            var imported = 0
            sessions.forEach { session ->
                val eventOwner = db.rawQuery("SELECT owner_id FROM imported_events WHERE event_id=?", arrayOf(session.id)).use { if (it.moveToFirst()) it.getString(0) else null }
                require(eventOwner == null || eventOwner == ownerId) { "Запись уже связана с другим профилем" }
                if (eventOwner == null) {
                    val saved = db.rawQuery("SELECT owner_id,status FROM sessions WHERE id=?", arrayOf(session.id)).use { if (it.moveToFirst()) it.getString(0) to it.getString(1) else null }
                    require(saved == null || saved.first == ownerId && saved.second == SessionStatus.COMPLETED.name) { "ID тренировки уже используется другой записью" }
                    if (saved == null) { insertImportedSession(db, ownerId, session); imported++ }
                    db.insertOrThrow("imported_events", null, ContentValues().apply { put("event_id", session.id); put("owner_id", ownerId); put("package_id", packageId) })
                }
            }
            if (imported > 0) db.update("profiles", ContentValues().apply { put("last_imported_at", System.currentTimeMillis()) }, "id=?", arrayOf(ownerId))
            db.setTransactionSuccessful()
            return imported
        } finally { db.endTransaction() }
    }

    private fun insertImportedSession(db: SQLiteDatabase, ownerId: String, session: TrainingSession) {
        db.insertOrThrow("sessions", null, ContentValues().apply {
            put("id", session.id); putNull("plan_id"); put("owner_id", ownerId); put("plan_name", session.planName); put("plan_version", session.planVersion)
            put("status", SessionStatus.COMPLETED.name); put("started_at", session.startedAt); put("ended_at", session.endedAt); put("comment", session.comment)
        })
        session.exercises.forEach { exercise ->
            val exerciseId = UUID.randomUUID().toString()
            db.insertOrThrow("session_exercises", null, ContentValues().apply {
                put("id", exerciseId); put("session_id", session.id); putNull("source_id"); put("position", exercise.position)
                put("name", exercise.name); put("skipped", if (exercise.skipped) 1 else 0); put("tracks_load", if (exercise.tracksLoad) 1 else 0)
            })
            exercise.sets.forEach { set ->
                db.insertOrThrow("session_sets", null, ContentValues().apply {
                    put("id", UUID.randomUUID().toString()); put("exercise_id", exerciseId); put("position", set.position); put("weight", set.weight); put("reps", set.reps)
                    put("kind", set.kind.name); put("done", if (set.done) 1 else 0); put("skipped", if (set.skipped) 1 else 0); put("rest_seconds", set.restSeconds)
                    if (set.restUntil == null) putNull("rest_until") else put("rest_until", set.restUntil)
                    put("rest_remaining", set.restRemaining)
                })
            }
        }
    }

    fun recordRejectedPackage(packageId: String, linkId: String) {
        writableDatabase.insertOrThrow("imported_packages", null, ContentValues().apply { put("package_id", packageId); put("link_id", linkId); put("imported_at", System.currentTimeMillis()) })
    }

    fun acceptIncomingProgram(header: ExchangeHeader, link: TrustedLink, plan: TrainingPlan): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(!packageImported(header.packageId)) { "Этот пакет уже был обработан" }
            require(trustedLink(link.id)?.localProfileId == link.localProfileId && profile(link.localProfileId) != null) { "Связь отозвана" }
            val old = incomingProgram(link.id, plan.id)
            require(old?.pendingAccepted != true) { "Предыдущее обновление уже принято и ждёт завершения тренировки" }
            require(plan.version > (old?.pendingVersion ?: old?.appliedVersion ?: 0)) { "Получена устаревшая версия программы" }
            db.insertOrThrow("imported_packages", null, ContentValues().apply { put("package_id", header.packageId); put("link_id", link.id); put("imported_at", System.currentTimeMillis()) })
            val json = encodePlanExport(listOf(plan)).toString(Charsets.UTF_8)
            val values = ContentValues().apply {
                put("link_id", link.id); put("remote_plan_id", plan.id); put("local_plan_id", old?.localPlanId)
                put("applied_version", old?.appliedVersion ?: 0); put("applied_local_version", old?.appliedLocalVersion ?: 0)
                put("pending_version", plan.version); put("pending_plan_json", json); put("pending_package_id", header.packageId)
                put("pending_accepted", 1); put("updated_at", System.currentTimeMillis())
            }
            if (old == null) db.insertOrThrow("incoming_programs", null, values)
            else db.update("incoming_programs", values, "link_id=? AND remote_plan_id=?", arrayOf(link.id, plan.id))
            val active = db.rawQuery("SELECT 1 FROM sessions WHERE owner_id=? AND status='ACTIVE' LIMIT 1", arrayOf(link.localProfileId)).use { it.moveToFirst() }
            if (!active) applyIncomingProgram(db, link.id, plan.id)
            db.setTransactionSuccessful()
            return active
        } finally { db.endTransaction() }
    }

    fun applyAcceptedIncomingPrograms(db: SQLiteDatabase, profileId: String) {
        val pending = db.rawQuery("SELECT r.link_id,r.remote_plan_id FROM incoming_programs r JOIN trusted_links l ON l.id=r.link_id WHERE l.local_profile_id=? AND r.pending_accepted=1", arrayOf(profileId)).use { c ->
            buildList { while (c.moveToNext()) add(c.s("link_id") to c.s("remote_plan_id")) }
        }
        pending.forEach { (linkId, remotePlanId) -> applyIncomingProgram(db, linkId, remotePlanId) }
    }

    private fun applyArchivedIncomingPrograms(db: SQLiteDatabase, profileId: String) {
        if (db.rawQuery("SELECT 1 FROM sessions WHERE owner_id=? AND status='ACTIVE' LIMIT 1", arrayOf(profileId)).use { it.moveToFirst() }) return
        val pending = db.rawQuery("SELECT remote_plan_id,local_plan_id,pending_version,plan_json FROM archived_incoming_programs WHERE profile_id=?", arrayOf(profileId)).use { c ->
            buildList { while (c.moveToNext()) add(ArchivedIncomingProgram(profileId, c.s("remote_plan_id"), if (c.isNull(1)) null else c.s("local_plan_id"), 0, 0, c.i("pending_version"), c.s("plan_json"), "")) }
        }
        pending.forEach { item ->
            val incoming = decodePlanExport(item.planJson.toByteArray(Charsets.UTF_8)).singleOrNull() ?: error("Повреждено принятое обновление")
            require(incoming.id == item.remotePlanId && incoming.version == item.pendingVersion) { "Обновление не совпадает с архивными метаданными" }
            val localId = item.localPlanId?.takeIf { id -> db.rawQuery("SELECT 1 FROM plans WHERE id=? AND owner_id=?", arrayOf(id, profileId)).use { it.moveToFirst() } } ?: UUID.randomUUID().toString()
            val oldVersion = db.rawQuery("SELECT version FROM plans WHERE id=? AND owner_id=?", arrayOf(localId, profileId)).use { if (it.moveToFirst()) it.getInt(0) else null }
            if (oldVersion == null) {
                val position = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM plans WHERE owner_id=?", arrayOf(profileId)).use { it.moveToFirst(); it.getInt(0) }
                db.insertOrThrow("plans", null, ContentValues().apply { put("id", localId); put("name", incoming.name); put("position", position); put("in_cycle", if (incoming.inCycle) 1 else 0); put("owner_id", profileId); put("version", 1) })
            } else {
                db.delete("plan_exercises", "plan_id=?", arrayOf(localId))
                db.update("plans", ContentValues().apply { put("name", incoming.name); put("in_cycle", if (incoming.inCycle) 1 else 0); put("version", oldVersion + 1) }, "id=? AND owner_id=?", arrayOf(localId, profileId))
            }
            incoming.exercises.forEachIndexed { index, exercise -> db.insertOrThrow("plan_exercises", null, ContentValues().apply {
                put("id", UUID.randomUUID().toString()); put("plan_id", localId); put("position", index); put("name", exercise.name); put("planned_sets", exercise.plannedSets)
                put("target_weight", exercise.targetWeight); put("target_reps", exercise.targetReps); put("rest_seconds", exercise.restSeconds); put("tracks_load", if (exercise.tracksLoad) 1 else 0)
            }) }
            db.delete("archived_incoming_programs", "profile_id=? AND remote_plan_id=?", arrayOf(profileId, item.remotePlanId))
        }
    }

    private fun applyIncomingProgram(db: SQLiteDatabase, linkId: String, remotePlanId: String) {
        val c = db.rawQuery("SELECT l.local_profile_id,r.local_plan_id,r.pending_plan_json,r.pending_version FROM incoming_programs r JOIN trusted_links l ON l.id=r.link_id WHERE r.link_id=? AND r.remote_plan_id=? AND r.pending_accepted=1", arrayOf(linkId, remotePlanId))
        val row = c.use { if (!it.moveToFirst()) return else Triple(it.s("local_profile_id"), if (it.isNull(it.index("local_plan_id"))) "" else it.s("local_plan_id"), it.s("pending_plan_json") to it.i("pending_version")) }
        val ownerId = row.first
        val incoming = decodePlanExport(row.third.first.toByteArray(Charsets.UTF_8)).singleOrNull() ?: error("Некорректное обновление программы")
        val existingVersion = db.rawQuery("SELECT version FROM plans WHERE id=? AND owner_id=?", arrayOf(row.second, ownerId)).use { if (row.second.isNotEmpty() && it.moveToFirst()) it.getInt(0) else null }
        val localPlanId = if (existingVersion == null) UUID.randomUUID().toString() else row.second
        val localVersion = (existingVersion ?: 0) + 1
        if (existingVersion == null) {
            val position = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM plans WHERE owner_id=?", arrayOf(ownerId)).use { it.moveToFirst(); it.getInt(0) }
            db.insertOrThrow("plans", null, ContentValues().apply { put("id", localPlanId); put("name", incoming.name); put("position", position); put("in_cycle", if (incoming.inCycle) 1 else 0); put("owner_id", ownerId); put("version", localVersion) })
        } else {
            db.delete("plan_exercises", "plan_id=?", arrayOf(localPlanId))
            db.update("plans", ContentValues().apply { put("name", incoming.name); put("in_cycle", if (incoming.inCycle) 1 else 0); put("version", localVersion) }, "id=? AND owner_id=?", arrayOf(localPlanId, ownerId))
        }
        incoming.exercises.forEachIndexed { position, exercise ->
            db.insertOrThrow("plan_exercises", null, ContentValues().apply {
                put("id", UUID.randomUUID().toString()); put("plan_id", localPlanId); put("position", position); put("name", exercise.name)
                put("planned_sets", exercise.plannedSets); put("target_weight", exercise.targetWeight); put("target_reps", exercise.targetReps)
                put("rest_seconds", exercise.restSeconds); put("tracks_load", if (exercise.tracksLoad) 1 else 0)
            })
        }
        db.update("incoming_programs", ContentValues().apply {
            put("local_plan_id", localPlanId); put("applied_version", row.third.second); put("applied_local_version", localVersion)
            putNull("pending_version"); putNull("pending_plan_json"); putNull("pending_package_id"); put("pending_accepted", 0); put("updated_at", System.currentTimeMillis())
        }, "link_id=? AND remote_plan_id=?", arrayOf(linkId, remotePlanId))
    }

    fun setActiveProfile(id: String) {
        if (profiles().any { it.id == id }) setSetting("active_profile_id", id)
    }

    fun createTraineeProfile(name: String) {
        val safe = name.trim().take(64)
        if (safe.isEmpty()) return
        val id = UUID.randomUUID().toString()
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow("profiles", null, ContentValues().apply {
                put("id", id); put("name", safe); put("kind", ProfileKind.TRAINEE.name); put("created_at", System.currentTimeMillis())
            })
            db.insertWithOnConflict("app_settings", null, ContentValues().apply {
                put("key", "active_profile_id"); put("value", id)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun renameProfile(id: String, name: String) {
        val safe = name.trim().take(64)
        if (safe.isNotEmpty()) writableDatabase.update("profiles", ContentValues().apply { put("name", safe) }, "id=? AND kind='TRAINEE'", arrayOf(id))
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
        val ownerId = activeOwnerId()
        seed.forEachIndexed { position, (name, exercises) ->
            val id = UUID.randomUUID().toString()
            db.insertOrThrow("plans", null, ContentValues().apply { put("id", id); put("name", name); put("position", position); put("in_cycle", 1); put("owner_id", ownerId) })
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

    fun plans(): List<TrainingPlan> = plansFor(activeOwnerId())

    fun allPlans(): List<TrainingPlan> = plansFor(null)

    private fun plansFor(ownerId: String?): List<TrainingPlan> {
        val sql = "SELECT id,name,position,in_cycle,owner_id,version FROM plans${if (ownerId == null) "" else " WHERE owner_id=?"} ORDER BY position,id"
        return readableDatabase.rawQuery(sql, if (ownerId == null) null else arrayOf(ownerId)).use { c ->
            buildList { while (c.moveToNext()) add(TrainingPlan(c.s("id"), c.s("name"), c.i("position"), c.i("in_cycle") != 0, exercises(c.s("id")), c.s("owner_id"), c.i("version"))) }
        }
    }

    fun personalPlans(): List<TrainingPlan> = plansFor(personalProfileId)

    fun assignments(): List<ProgramAssignment> = readableDatabase.rawQuery(
        "SELECT a.id,a.profile_id,a.source_plan_id,a.assigned_plan_id,a.version,a.pending_version,a.pending_accepted,a.updated_at,assigned.name AS plan_name,assigned.version AS assigned_version,COALESCE(source.version,a.version) AS source_version,a.pending_plan_json FROM program_assignments a JOIN plans assigned ON assigned.id=a.assigned_plan_id LEFT JOIN plans source ON source.id=a.source_plan_id AND source.owner_id=? ORDER BY a.updated_at DESC",
        arrayOf(personalProfileId),
    ).use { c -> buildList {
        while (c.moveToNext()) {
            val pendingJson = if (c.isNull(c.index("pending_plan_json"))) null else c.s("pending_plan_json")
            val pending = pendingJson?.let { runCatching { decodePlanExport(it.toByteArray(Charsets.UTF_8)).singleOrNull() }.getOrNull() }
            add(ProgramAssignment(
                id = c.s("id"), profileId = c.s("profile_id"), sourcePlanId = c.s("source_plan_id"), assignedPlanId = c.s("assigned_plan_id"),
                version = c.i("version"), pendingVersion = if (c.isNull(c.index("pending_version"))) null else c.i("pending_version"),
                pendingAccepted = c.i("pending_accepted") != 0, updatedAt = c.l("updated_at"), planName = c.s("plan_name"),
                assignedPlanVersion = c.i("assigned_version"), sourceVersion = c.i("source_version"), pendingPlanJson = pendingJson, pendingPlan = pending,
            ))
        }
    } }

    fun assignPlanToProfile(sourcePlanId: String, profileId: String) {
        val source = personalPlans().firstOrNull { it.id == sourcePlanId } ?: return
        if (profiles().none { it.id == profileId && it.kind == ProfileKind.TRAINEE }) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existing = db.rawQuery("SELECT 1 FROM program_assignments WHERE profile_id=? AND source_plan_id=?", arrayOf(profileId, sourcePlanId)).use { it.moveToFirst() }
            if (existing) { db.setTransactionSuccessful(); return }
            val assignedId = UUID.randomUUID().toString()
            val assignmentId = UUID.randomUUID().toString()
            val position = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM plans WHERE owner_id=?", arrayOf(profileId)).use { it.moveToFirst(); it.getInt(0) }
            db.insertOrThrow("plans", null, ContentValues().apply {
                put("id", assignedId); put("name", source.name); put("position", position); put("in_cycle", 1)
                put("owner_id", profileId); put("version", source.version)
            })
            source.exercises.forEachIndexed { order, exercise ->
                db.insertOrThrow("plan_exercises", null, ContentValues().apply {
                    put("id", UUID.randomUUID().toString()); put("plan_id", assignedId); put("position", order); put("name", exercise.name)
                    put("planned_sets", exercise.plannedSets); put("target_weight", exercise.targetWeight); put("target_reps", exercise.targetReps)
                    put("rest_seconds", exercise.restSeconds); put("tracks_load", if (exercise.tracksLoad) 1 else 0)
                })
            }
            db.insertOrThrow("program_assignments", null, ContentValues().apply {
                put("id", assignmentId); put("profile_id", profileId); put("source_plan_id", source.id); put("assigned_plan_id", assignedId)
                put("version", source.version); put("updated_at", System.currentTimeMillis())
            })
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun stageAssignmentUpdate(assignmentId: String) {
        if (activeOwnerId() != personalProfileId) return
        val db = writableDatabase
        val assignment = db.rawQuery("SELECT source_plan_id,version,pending_accepted FROM program_assignments WHERE id=?", arrayOf(assignmentId)).use { c ->
            if (c.moveToFirst()) Triple(c.s("source_plan_id"), c.i("version"), c.i("pending_accepted") != 0) else null
        } ?: return
        if (assignment.third) return
        val source = personalPlans().firstOrNull { it.id == assignment.first } ?: return
        if (source.version <= assignment.second) return
        val json = encodePlanExport(listOf(source)).toString(Charsets.UTF_8)
        db.update("program_assignments", ContentValues().apply {
            put("pending_plan_json", json); put("pending_version", source.version); put("pending_accepted", 0); put("updated_at", System.currentTimeMillis())
        }, "id=?", arrayOf(assignmentId))
    }

    /** Returns true when acceptance is queued until the active workout finishes. */
    fun acceptAssignmentUpdate(assignmentId: String): Boolean {
        val ownerId = activeOwnerId()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val row = db.rawQuery("SELECT pending_plan_json,pending_version FROM program_assignments WHERE id=? AND profile_id=?", arrayOf(assignmentId, ownerId)).use { c ->
                if (c.moveToFirst() && !c.isNull(0) && !c.isNull(1)) c.getString(0) to c.getInt(1) else null
            } ?: run { db.setTransactionSuccessful(); return false }
            if (active() != null) {
                db.update("program_assignments", ContentValues().apply { put("pending_accepted", 1) }, "id=? AND profile_id=?", arrayOf(assignmentId, ownerId))
                db.setTransactionSuccessful()
                return true
            }
            applyAssignmentUpdate(db, assignmentId, ownerId, row.first, row.second)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return false
    }

    fun rejectAssignmentUpdate(assignmentId: String) {
        writableDatabase.update("program_assignments", ContentValues().apply {
            putNull("pending_plan_json"); putNull("pending_version"); put("pending_accepted", 0); put("updated_at", System.currentTimeMillis())
        }, "id=? AND profile_id=?", arrayOf(assignmentId, activeOwnerId()))
    }

    private fun applyAssignmentUpdate(db: SQLiteDatabase, assignmentId: String, profileId: String, json: String, version: Int) {
        val incoming = decodePlanExport(json.toByteArray(Charsets.UTF_8)).singleOrNull() ?: error("Некорректное обновление программы")
        val assignedId = db.rawQuery("SELECT assigned_plan_id FROM program_assignments WHERE id=? AND profile_id=?", arrayOf(assignmentId, profileId)).use {
            if (it.moveToFirst()) it.getString(0) else error("Назначение программы не найдено")
        }
        db.delete("plan_exercises", "plan_id=?", arrayOf(assignedId))
        db.update("plans", ContentValues().apply { put("name", incoming.name); put("in_cycle", if (incoming.inCycle) 1 else 0); put("version", version) }, "id=? AND owner_id=?", arrayOf(assignedId, profileId))
        incoming.exercises.forEachIndexed { order, exercise ->
            db.insertOrThrow("plan_exercises", null, ContentValues().apply {
                put("id", UUID.randomUUID().toString()); put("plan_id", assignedId); put("position", order); put("name", exercise.name)
                put("planned_sets", exercise.plannedSets); put("target_weight", exercise.targetWeight); put("target_reps", exercise.targetReps)
                put("rest_seconds", exercise.restSeconds); put("tracks_load", if (exercise.tracksLoad) 1 else 0)
            })
        }
        db.update("program_assignments", ContentValues().apply {
            put("version", version); putNull("pending_version"); putNull("pending_plan_json"); put("pending_accepted", 0); put("updated_at", System.currentTimeMillis())
        }, "id=? AND profile_id=?", arrayOf(assignmentId, profileId))
    }

    private fun applyAcceptedAssignmentUpdates(db: SQLiteDatabase, profileId: String) {
        val pending = db.rawQuery("SELECT id,pending_plan_json,pending_version FROM program_assignments WHERE profile_id=? AND pending_accepted=1", arrayOf(profileId)).use { c ->
            buildList { while (c.moveToNext()) if (!c.isNull(1) && !c.isNull(2)) add(Triple(c.s("id"), c.s("pending_plan_json"), c.i("pending_version"))) }
        }
        pending.forEach { (id, json, version) ->
            runCatching { applyAssignmentUpdate(db, id, profileId, json, version) }
                .onFailure { db.update("program_assignments", ContentValues().apply { put("pending_accepted", 0) }, "id=?", arrayOf(id)) }
        }
    }

    private fun exercises(planId: String): List<PlanExercise> = readableDatabase.rawQuery("SELECT id,name,planned_sets,target_weight,target_reps,rest_seconds,tracks_load FROM plan_exercises WHERE plan_id=? ORDER BY position", arrayOf(planId)).use { c ->
        buildList { while (c.moveToNext()) add(PlanExercise(c.s("id"), c.s("name"), c.i("planned_sets"), c.s("target_weight"), c.s("target_reps"), c.i("rest_seconds"), c.i("tracks_load") != 0)) }
    }

    fun nextPlan(): TrainingPlan? {
        val ownerId = activeOwnerId()
        val cycle = plans().filter { it.inCycle }
        if (cycle.isEmpty()) return null
        val last = readableDatabase.rawQuery("SELECT plan_id FROM sessions WHERE owner_id=? AND status='COMPLETED' AND plan_id IN (SELECT id FROM plans WHERE owner_id=? AND in_cycle=1) ORDER BY ended_at DESC LIMIT 1", arrayOf(ownerId, ownerId)).use { if (it.moveToFirst()) it.getString(0) else null }
        return cycle[nextCyclePosition(cycle.map { it.id }, last)]
    }

    fun start(planId: String?): String {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val ownerId = activeOwnerId()
            active()?.let { db.setTransactionSuccessful(); return it.id }
            val plan = plans().firstOrNull { it.id == planId } ?: nextPlan() ?: plans().firstOrNull()
                ?: error("Сначала создайте тренировочный план")
            val id = UUID.randomUUID().toString()
            db.insertOrThrow("sessions", null, ContentValues().apply { put("id", id); put("plan_id", plan.id); put("owner_id", ownerId); put("plan_name", plan.name); put("plan_version", plan.version); put("status", "ACTIVE"); put("started_at", System.currentTimeMillis()); put("comment", "") })
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

    fun active(): TrainingSession? = readableDatabase.rawQuery("SELECT id,plan_id,owner_id,plan_name,plan_version,status,started_at,ended_at,comment FROM sessions WHERE owner_id=? AND status='ACTIVE' ORDER BY started_at LIMIT 1", arrayOf(activeOwnerId())).use { if (it.moveToFirst()) session(it) else null }

    fun history(): List<TrainingSession> = readableDatabase.rawQuery("SELECT id,plan_id,owner_id,plan_name,plan_version,status,started_at,ended_at,comment FROM sessions WHERE owner_id=? AND status='COMPLETED' ORDER BY ended_at DESC", arrayOf(activeOwnerId())).use { c -> buildList { while (c.moveToNext()) add(session(c)) } }

    fun allSessions(): List<TrainingSession> = readableDatabase.rawQuery("SELECT id,plan_id,owner_id,plan_name,plan_version,status,started_at,ended_at,comment FROM sessions ORDER BY started_at", null).use { c -> buildList { while (c.moveToNext()) add(session(c)) } }

    fun restoreDiary(archive: ArchivedDiary) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("pairing_pending", null, null)
            db.delete("trusted_links", null, null)
            db.delete("imported_events", null, null)
            db.delete("imported_packages", null, null)
            db.delete("incoming_programs", null, null)
            db.delete("archived_incoming_programs", null, null)
            db.delete("sessions", null, null)
            db.delete("program_assignments", null, null)
            db.delete("plans", null, null)
            db.delete("profiles", null, null)
            archive.profiles.forEach { profile ->
                db.insertOrThrow("profiles", null, ContentValues().apply {
                    put("id", profile.id); put("name", profile.name); put("kind", profile.kind.name); put("created_at", profile.createdAt)
                    if (profile.lastImportedAt == null) putNull("last_imported_at") else put("last_imported_at", profile.lastImportedAt)
                })
            }
            archive.plans.forEach { plan ->
                db.insertOrThrow("plans", null, ContentValues().apply { put("id", plan.id); put("name", plan.name); put("position", plan.position); put("in_cycle", if (plan.inCycle) 1 else 0); put("owner_id", plan.ownerId); put("version", plan.version) })
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
                    put("id", session.id); put("plan_id", session.planId); put("owner_id", session.ownerId); put("plan_name", session.planName); put("plan_version", session.planVersion); put("status", session.status.name)
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
            archive.assignments.forEach { assignment ->
                db.insertOrThrow("program_assignments", null, ContentValues().apply {
                    put("id", assignment.id); put("profile_id", assignment.profileId); put("source_plan_id", assignment.sourcePlanId)
                    put("assigned_plan_id", assignment.assignedPlanId); put("version", assignment.version)
                    if (assignment.pendingVersion == null) putNull("pending_version") else put("pending_version", assignment.pendingVersion)
                    if (assignment.pendingPlanJson == null) putNull("pending_plan_json") else put("pending_plan_json", assignment.pendingPlanJson)
                    put("pending_accepted", if (assignment.pendingAccepted) 1 else 0); put("updated_at", assignment.updatedAt)
                })
            }
            archive.acceptedIncoming.forEach { pending -> db.insertOrThrow("archived_incoming_programs", null, ContentValues().apply {
                put("profile_id", pending.profileId); put("remote_plan_id", pending.remotePlanId); if (pending.localPlanId == null) putNull("local_plan_id") else put("local_plan_id", pending.localPlanId)
                put("applied_version", pending.appliedVersion); put("applied_local_version", pending.appliedLocalVersion); put("pending_version", pending.pendingVersion); put("plan_json", pending.planJson); put("package_id", pending.packageId)
            }) }
            archive.profiles.forEach { applyArchivedIncomingPrograms(db, it.id) }
            setSetting("weight_unit", archive.settings.weightUnit.name)
            setSetting("theme", archive.settings.theme.name)
            setSetting("rest_timer_enabled", archive.settings.restTimerEnabled.toString())
            setSetting("active_profile_id", archive.activeProfileId)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun importPlans(imported: List<TrainingPlan>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val ownerId = activeOwnerId()
            var position = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM plans WHERE owner_id=?", arrayOf(ownerId)).use { it.moveToFirst(); it.getInt(0) }
            imported.forEach { source ->
                val id = UUID.randomUUID().toString()
                db.insertOrThrow("plans", null, ContentValues().apply { put("id", id); put("name", "${source.name} (импорт)".take(48)); put("position", position++); put("in_cycle", 0); put("owner_id", ownerId); put("version", source.version) })
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
        return TrainingSession(id, if (c.isNull(c.index("plan_id"))) null else c.s("plan_id"), c.s("plan_name"), SessionStatus.valueOf(c.s("status")), c.l("started_at"), if (c.isNull(c.index("ended_at"))) null else c.l("ended_at"), exercises, c.s("comment"), c.s("owner_id"), c.i("plan_version"))
    }

    fun updateSet(id: String, weight: String?, reps: String?) {
        val values = ContentValues(); weight?.let { values.put("weight", it.replace(',', '.').take(8)) }; reps?.let { values.put("reps", it.take(5)) }
        writableDatabase.update("session_sets", values, "id=? AND exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE' AND s.owner_id=?)", arrayOf(id, activeOwnerId()))
    }

    fun toggleSet(set: TrainingSet) {
        val done = !set.done
        val restOn = settings().restTimerEnabled
        writableDatabase.update("session_sets", ContentValues().apply { put("done", if (done) 1 else 0); put("rest_until", if (done && restOn && set.restSeconds > 0) System.currentTimeMillis() + set.restSeconds * 1000L else null); put("rest_remaining", 0) }, "id=? AND exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE' AND s.owner_id=?)", arrayOf(set.id, activeOwnerId()))
    }

    fun setKind(id: String, kind: SetKind) { writableDatabase.update("session_sets", ContentValues().apply { put("kind", kind.name); if (kind == SetKind.WARMUP) { put("done", 0); putNull("rest_until"); put("rest_remaining", 0) } }, "id=? AND exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE' AND s.owner_id=?)", arrayOf(id, activeOwnerId())) }

    fun addSet(exerciseId: String) {
        val db = writableDatabase
        val ownerId = activeOwnerId()
        val next = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM session_sets WHERE exercise_id=?", arrayOf(exerciseId)).use { it.moveToFirst(); it.getInt(0) }
        val rest = db.rawQuery("SELECT rest_seconds FROM session_sets WHERE exercise_id=? LIMIT 1", arrayOf(exerciseId)).use { if (it.moveToFirst()) it.getInt(0) else 90 }
        val active = db.rawQuery("SELECT 1 FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE e.id=? AND s.status='ACTIVE' AND s.owner_id=?", arrayOf(exerciseId, ownerId)).use { it.moveToFirst() }
        if (active) db.insertOrThrow("session_sets", null, ContentValues().apply { put("id", UUID.randomUUID().toString()); put("exercise_id", exerciseId); put("position", next); put("weight", ""); put("reps", ""); put("kind", "WORK"); put("rest_seconds", rest) })
    }

    fun removeSet(id: String) { writableDatabase.delete("session_sets", "id=? AND exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE' AND s.owner_id=?)", arrayOf(id, activeOwnerId())) }
    fun skipRest(id: String) { writableDatabase.update("session_sets", ContentValues().apply { putNull("rest_until"); put("rest_remaining", 0) }, "id=? AND exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE' AND s.owner_id=?)", arrayOf(id, activeOwnerId())) }
    fun pauseRest(id: String) {
        val db = writableDatabase
        db.execSQL("UPDATE session_sets SET rest_remaining=MAX(0,rest_until-?),rest_until=NULL WHERE id=? AND rest_until IS NOT NULL AND exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE' AND s.owner_id=?)", arrayOf<Any>(System.currentTimeMillis(), id, activeOwnerId()))
    }
    fun resumeRest(id: String) {
        val db = writableDatabase
        db.execSQL("UPDATE session_sets SET rest_until=?+rest_remaining,rest_remaining=0 WHERE id=? AND rest_until IS NULL AND rest_remaining>0 AND exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE' AND s.owner_id=?)", arrayOf<Any>(System.currentTimeMillis(), id, activeOwnerId()))
    }

    fun skipExercise(id: String, skipped: Boolean) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val ownerId = activeOwnerId()
            db.update("session_exercises", ContentValues().apply { put("skipped", if (skipped) 1 else 0) }, "id=? AND session_id IN (SELECT id FROM sessions WHERE status='ACTIVE' AND owner_id=?)", arrayOf(id, ownerId))
            db.update("session_sets", ContentValues().apply { put("skipped", if (skipped) 1 else 0) }, "exercise_id=? AND exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE' AND s.owner_id=?)", arrayOf(id, ownerId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun replaceExercise(id: String, replacement: PlanExercise) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val ownerId = activeOwnerId()
            db.update("session_exercises", ContentValues().apply { put("name", replacement.name); putNull("source_id"); put("tracks_load", if (replacement.tracksLoad) 1 else 0); put("skipped", 0) }, "id=? AND session_id IN (SELECT id FROM sessions WHERE status='ACTIVE' AND owner_id=?)", arrayOf(id, ownerId))
            db.update("session_sets", ContentValues().apply { put("weight", ""); put("reps", ""); put("done", 0); put("skipped", 0); put("kind", SetKind.WORK.name); put("rest_seconds", replacement.restSeconds); putNull("rest_until"); put("rest_remaining", 0) }, "exercise_id=? AND exercise_id IN (SELECT e.id FROM session_exercises e JOIN sessions s ON s.id=e.session_id WHERE s.status='ACTIVE' AND s.owner_id=?)", arrayOf(id, ownerId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun finish(id: String) {
        val ownerId = activeOwnerId()
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (db.update("sessions", ContentValues().apply { put("status", "COMPLETED"); put("ended_at", System.currentTimeMillis()) }, "id=? AND status='ACTIVE' AND owner_id=?", arrayOf(id, ownerId)) > 0) {
                applyAcceptedAssignmentUpdates(db, ownerId)
                applyAcceptedIncomingPrograms(db, ownerId)
                applyArchivedIncomingPrograms(db, ownerId)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun cancel(id: String) {
        val ownerId = activeOwnerId()
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (db.update("sessions", ContentValues().apply { put("status", "CANCELLED"); put("ended_at", System.currentTimeMillis()) }, "id=? AND status='ACTIVE' AND owner_id=?", arrayOf(id, ownerId)) > 0) {
                applyAcceptedAssignmentUpdates(db, ownerId)
                applyAcceptedIncomingPrograms(db, ownerId)
                applyArchivedIncomingPrograms(db, ownerId)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun updateComment(id: String, comment: String) { writableDatabase.update("sessions", ContentValues().apply { put("comment", comment.take(1000)) }, "id=? AND status='ACTIVE' AND owner_id=?", arrayOf(id, activeOwnerId())) }

    private fun bumpPlanVersion(id: String, ownerId: String = activeOwnerId()) {
        writableDatabase.execSQL("UPDATE plans SET version=version+1 WHERE id=? AND owner_id=?", arrayOf(id, ownerId))
    }

    fun renamePlan(id: String, name: String) {
        val safe = name.trim().take(48)
        if (safe.isNotEmpty() && writableDatabase.update("plans", ContentValues().apply { put("name", safe) }, "id=? AND owner_id=?", arrayOf(id, activeOwnerId())) > 0) bumpPlanVersion(id)
    }
    fun createPlan(name: String) {
        val safe = name.trim().take(48); if (safe.isEmpty()) return
        val db = writableDatabase
        val ownerId = activeOwnerId()
        val position = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM plans WHERE owner_id=?", arrayOf(ownerId)).use { it.moveToFirst(); it.getInt(0) }
        db.insertOrThrow("plans", null, ContentValues().apply { put("id", UUID.randomUUID().toString()); put("name", safe); put("position", position); put("in_cycle", 0); put("owner_id", ownerId) })
    }
    fun duplicatePlan(id: String) {
        val source = plans().firstOrNull { it.id == id } ?: return
        val db = writableDatabase
        val position = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM plans WHERE owner_id=?", arrayOf(source.ownerId)).use { it.moveToFirst(); it.getInt(0) }
        val copyId = UUID.randomUUID().toString()
        db.beginTransaction()
        try {
            db.insertOrThrow("plans", null, ContentValues().apply { put("id", copyId); put("name", "${source.name} копия".take(48)); put("position", position); put("in_cycle", 0); put("owner_id", source.ownerId) })
            source.exercises.forEachIndexed { order, exercise ->
                db.insertOrThrow("plan_exercises", null, ContentValues().apply { put("id", UUID.randomUUID().toString()); put("plan_id", copyId); put("position", order); put("name", exercise.name); put("planned_sets", exercise.plannedSets); put("target_weight", exercise.targetWeight); put("target_reps", exercise.targetReps); put("rest_seconds", exercise.restSeconds); put("tracks_load", if (exercise.tracksLoad) 1 else 0) })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun deletePlan(id: String) {
        if (plans().none { it.id == id } || active()?.planId == id) return
        val db = writableDatabase
        db.beginTransaction()
        try { db.delete("plan_exercises", "plan_id=?", arrayOf(id)); db.delete("plans", "id=?", arrayOf(id)); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
    fun setPlanInCycle(id: String, included: Boolean) {
        val plans = plans()
        val plan = plans.firstOrNull { it.id == id } ?: return
        if (plan.inCycle == included) return
        val position = if (included) plans.maxOfOrNull { it.position }?.plus(1) else null
        writableDatabase.update("plans", ContentValues().apply { put("in_cycle", if (included) 1 else 0); put("version", plan.version + 1); position?.let { put("position", it) } }, "id=? AND owner_id=?", arrayOf(id, activeOwnerId()))
    }
    fun movePlan(id: String, offset: Int) {
        val db = writableDatabase
        val rows = plans().toMutableList(); val from = rows.indexOfFirst { it.id == id }
        if (from < 0 || rows.isEmpty()) return
        val to = (from + offset).coerceIn(0, rows.lastIndex)
        if (from == to) return
        val moved = rows.removeAt(from); rows.add(to, moved); db.beginTransaction()
        try {
            rows.forEachIndexed { pos, plan -> db.update("plans", ContentValues().apply { put("position", pos) }, "id=? AND owner_id=?", arrayOf(plan.id, plan.ownerId)) }
            if (moved.inCycle) db.execSQL("UPDATE plans SET version=version+1 WHERE owner_id=? AND in_cycle=1", arrayOf(moved.ownerId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun addExercise(planId: String, name: String) {
        val trimmed = name.trim().take(80); if (trimmed.isEmpty()) return
        if (plans().none { it.id == planId }) return
        val position = writableDatabase.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM plan_exercises WHERE plan_id=?", arrayOf(planId)).use { it.moveToFirst(); it.getInt(0) }
        val tracksLoad = !(trimmed.contains("ходьба", true) || trimmed.contains("дорожка", true))
        writableDatabase.insertOrThrow("plan_exercises", null, ContentValues().apply { put("id", UUID.randomUUID().toString()); put("plan_id", planId); put("position", position); put("name", trimmed); put("planned_sets", 3); put("target_weight", ""); put("target_reps", ""); put("rest_seconds", 90); put("tracks_load", if (tracksLoad) 1 else 0) })
        bumpPlanVersion(planId)
    }
    fun updateExercise(exercise: PlanExercise) {
        val ownerId = activeOwnerId()
        val db = writableDatabase
        if (db.update("plan_exercises", ContentValues().apply { put("name", exercise.name.trim().take(80)); put("planned_sets", exercise.plannedSets.coerceIn(1, 20)); put("target_weight", exercise.targetWeight.replace(',', '.').take(8)); put("target_reps", exercise.targetReps.filter(Char::isDigit).take(4)); put("rest_seconds", exercise.restSeconds.coerceIn(0, 900)); put("tracks_load", if (exercise.tracksLoad) 1 else 0) }, "id=? AND plan_id IN (SELECT id FROM plans WHERE owner_id=?)", arrayOf(exercise.id, ownerId)) > 0) {
            db.execSQL("UPDATE plans SET version=version+1 WHERE id=(SELECT plan_id FROM plan_exercises WHERE id=?) AND owner_id=?", arrayOf(exercise.id, ownerId))
        }
    }
    fun deleteExercise(id: String) {
        val ownerId = activeOwnerId()
        val db = writableDatabase
        val planId = db.rawQuery("SELECT plan_id FROM plan_exercises WHERE id=? AND plan_id IN (SELECT id FROM plans WHERE owner_id=?)", arrayOf(id, ownerId)).use { if (it.moveToFirst()) it.getString(0) else null } ?: return
        if (db.delete("plan_exercises", "id=?", arrayOf(id)) > 0) bumpPlanVersion(planId, ownerId)
    }
    fun moveExercise(id: String, planId: String, offset: Int) {
        val db = writableDatabase
        if (plans().none { it.id == planId }) return
        val rows = exercises(planId).toMutableList(); val from = rows.indexOfFirst { it.id == id }
        if (from < 0 || rows.isEmpty()) return
        val to = (from + offset).coerceIn(0, rows.lastIndex); if (from == to) return
        val moved = rows.removeAt(from); rows.add(to, moved)
        db.beginTransaction()
        try {
            rows.forEachIndexed { position, exercise -> db.update("plan_exercises", ContentValues().apply { put("position", position) }, "id=? AND plan_id IN (SELECT id FROM plans WHERE owner_id=?)", arrayOf(exercise.id, activeOwnerId())) }
            bumpPlanVersion(planId)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}

private fun Cursor.index(name: String) = getColumnIndexOrThrow(name)
private fun Cursor.s(name: String) = getString(index(name))
private fun Cursor.i(name: String) = getInt(index(name))
private fun Cursor.l(name: String) = getLong(index(name))
