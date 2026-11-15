package app.reptrack

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream

@RunWith(AndroidJUnit4::class)
class DiaryRepositoryInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun schemaV1MigratesAndKeepsPlansAndSessions() {
        val name = "migration-${UUID.randomUUID()}.db"
        val legacy = context.openOrCreateDatabase(name, 0, null)
        legacy.setForeignKeyConstraintsEnabled(true)
        legacy.execSQL("CREATE TABLE plans(id TEXT PRIMARY KEY, name TEXT NOT NULL, position INTEGER NOT NULL, in_cycle INTEGER NOT NULL DEFAULT 1)")
        legacy.execSQL("CREATE TABLE plan_exercises(id TEXT PRIMARY KEY, plan_id TEXT NOT NULL REFERENCES plans(id) ON DELETE CASCADE, position INTEGER NOT NULL, name TEXT NOT NULL, planned_sets INTEGER NOT NULL, target_weight TEXT NOT NULL, target_reps TEXT NOT NULL, rest_seconds INTEGER NOT NULL, tracks_load INTEGER NOT NULL DEFAULT 1)")
        legacy.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY, plan_id TEXT REFERENCES plans(id) ON DELETE SET NULL, plan_name TEXT NOT NULL, status TEXT NOT NULL, started_at INTEGER NOT NULL, ended_at INTEGER, comment TEXT NOT NULL DEFAULT '')")
        legacy.execSQL("CREATE TABLE session_exercises(id TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, source_id TEXT, position INTEGER NOT NULL, name TEXT NOT NULL, skipped INTEGER NOT NULL DEFAULT 0, tracks_load INTEGER NOT NULL DEFAULT 1)")
        legacy.execSQL("CREATE TABLE session_sets(id TEXT PRIMARY KEY, exercise_id TEXT NOT NULL REFERENCES session_exercises(id) ON DELETE CASCADE, position INTEGER NOT NULL, weight TEXT NOT NULL, reps TEXT NOT NULL, kind TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, skipped INTEGER NOT NULL DEFAULT 0, rest_seconds INTEGER NOT NULL, rest_until INTEGER, rest_remaining INTEGER NOT NULL DEFAULT 0)")
        legacy.execSQL("CREATE UNIQUE INDEX one_active_session ON sessions(status) WHERE status='ACTIVE'")
        legacy.execSQL("CREATE INDEX session_history_order ON sessions(status,ended_at DESC)")
        legacy.execSQL("INSERT INTO plans(id,name,position,in_cycle) VALUES('plan-v1','Push',0,1)")
        legacy.execSQL("INSERT INTO sessions(id,plan_id,plan_name,status,started_at,ended_at,comment) VALUES('session-v1','plan-v1','Push','COMPLETED',100,200,'saved')")
        legacy.version = 1
        legacy.close()

        val helper = DiaryDb(context, name)
        try {
            val db = helper.writableDatabase
            assertEquals(6, db.version)
            assertEquals(1, db.rawQuery("SELECT COUNT(*) FROM plans WHERE id='plan-v1'", null).use { it.moveToFirst(); it.getInt(0) })
            assertEquals(1, db.rawQuery("SELECT COUNT(*) FROM sessions WHERE id='session-v1'", null).use { it.moveToFirst(); it.getInt(0) })
            assertEquals(personalProfileId, db.rawQuery("SELECT owner_id FROM sessions WHERE id='session-v1'", null).use { it.moveToFirst(); it.getString(0) })
            assertEquals(1, db.rawQuery("SELECT plan_version FROM sessions WHERE id='session-v1'", null).use { it.moveToFirst(); it.getInt(0) })
            assertEquals(1, db.rawQuery("SELECT COUNT(*) FROM profiles WHERE kind='PERSONAL'", null).use { it.moveToFirst(); it.getInt(0) })
            assertEquals(DiarySettings(), helper.settings())
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun schemaV5DropsPendingKeystoreAliasesAndAddsWrappedPairingKeys() {
        val name = "migration-v5-${UUID.randomUUID()}.db"
        val legacy = context.openOrCreateDatabase(name, 0, null)
        legacy.execSQL("CREATE TABLE profiles(id TEXT PRIMARY KEY)")
        legacy.execSQL("CREATE TABLE pairing_pending(invite_id TEXT PRIMARY KEY,link_id TEXT NOT NULL,local_profile_id TEXT NOT NULL,role TEXT NOT NULL,remote_device_id TEXT,private_alias TEXT,wrapped_secret BLOB,fingerprint TEXT,created_at INTEGER NOT NULL,expires_at INTEGER NOT NULL)")
        legacy.execSQL("INSERT INTO pairing_pending(invite_id,link_id,local_profile_id,role,private_alias,created_at,expires_at) VALUES('old','link','local-owner','OUTGOING','reptrack.pair.dead',1,2)")
        legacy.version = 5
        legacy.close()

        val helper = DiaryDb(context, name)
        try {
            val db = helper.writableDatabase
            assertEquals(6, db.version)
            assertEquals(0, db.rawQuery("SELECT COUNT(*) FROM pairing_pending", null).use { it.moveToFirst(); it.getInt(0) })
            val hasWrappedKey = db.rawQuery("PRAGMA table_info(pairing_pending)", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }.contains("private_key_wrapped")
            }
            assertTrue(hasWrappedKey)
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun encryptedBackupRestoresHistorySettingsAndPlanExportImportsTemplates() {
        val originalName = "backup-${UUID.randomUUID()}.db"
        val importedName = "plans-${UUID.randomUUID()}.db"
        val source = DiaryRepository(context, originalName)
        val target = DiaryRepository(context, importedName)
        try {
            source.createPlan("Test Push")
            val plan = source.load().plans.single()
            source.addExercise(plan.id, "Жим лёжа")
            source.setWeightUnit(WeightUnit.LB)
            val sessionId = source.start(plan.id)
            val active = source.load().activeSession!!
            val set = active.exercises.single().sets.first()
            source.updateSet(set.id, weight = "102.0583", reps = "8")
            source.toggleSet(set)
            source.updateComment(sessionId, "backup-test")
            source.finish(sessionId)

            val backup = source.createEncryptedBackup("backup-test-passphrase")
            val exportedPlans = source.exportPlans()
            source.createPlan("Temporary")
            source.setWeightUnit(WeightUnit.KG)
            source.restoreEncryptedBackup(backup, "backup-test-passphrase")

            val restored = source.load()
            assertEquals(listOf("Test Push"), restored.plans.map { it.name })
            assertEquals(WeightUnit.LB, restored.settings.weightUnit)
            assertEquals(1, restored.history.size)
            assertEquals(102.0583, restored.history.single().maxWeightKg!!, 0.0001)
            assertEquals("backup-test", restored.history.single().comment)

            target.importPlans(exportedPlans)
            val imported = target.load()
            assertEquals(listOf("Test Push (импорт)"), imported.plans.map { it.name })
            assertEquals("Жим лёжа", imported.plans.single().exercises.single().name)
            assertTrue(imported.history.isEmpty())
            assertNotNull(imported.plans.single().exercises.single().id)
        } finally {
            source.close()
            target.close()
            context.deleteDatabase(originalName)
            context.deleteDatabase(importedName)
        }
    }

    @Test
    fun emptyCompletedWeightsAreNotListedAsPersonalRecords() {
        val name = "empty-weight-${UUID.randomUUID()}.db"
        val repository = DiaryRepository(context, name)
        try {
            repository.createPlan("Push")
            val plan = repository.load().plans.single()
            repository.addExercise(plan.id, "Жим лёжа")
            val sessionId = repository.start(plan.id)
            val set = repository.load().activeSession!!.exercises.single().sets.single()
            repository.updateSet(set.id, weight = "", reps = "8")
            repository.toggleSet(set)
            repository.finish(sessionId)

            val result = repository.load()
            assertTrue(result.bestByExercise.isEmpty())
            assertNull(result.bestWeightKg)
            assertNull(result.history.single().maxWeightKg)
        } finally {
            repository.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun profilesKeepPlansSessionsAndActiveWorkoutsSeparate() {
        val name = "profiles-${UUID.randomUUID()}.db"
        val repository = DiaryRepository(context, name)
        try {
            repository.createPlan("Личный план")
            val personalPlan = repository.load().plans.single()
            val personalSession = repository.start(personalPlan.id)

            repository.createTraineeProfile("Иван")
            val traineeId = repository.load().activeProfileId
            repository.createPlan("План Ивана")
            val traineePlan = repository.load().plans.single()
            val traineeSession = repository.start(traineePlan.id)

            assertEquals(traineeSession, repository.load().activeSession?.id)
            assertEquals(listOf("План Ивана"), repository.load().plans.map { it.name })
            repository.finish(traineeSession)
            repository.setActiveProfile(personalProfileId)
            assertEquals(personalSession, repository.load().activeSession?.id)
            assertEquals(listOf("Личный план"), repository.load().plans.map { it.name })
            repository.finish(personalSession)

            repository.setActiveProfile(traineeId)
            assertEquals(listOf("План Ивана"), repository.load().history.map { it.planName })
            repository.setActiveProfile(personalProfileId)
            assertEquals(listOf("Личный план"), repository.load().history.map { it.planName })
        } finally {
            repository.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun versionOneBackupStillRestoresAsPersonalProfile() {
        val json = """{"format":"reptrack-backup","version":1,"plans":[],"sessions":[],"settings":{"weightUnit":"KG","theme":"PURPLE_PRO","restTimerEnabled":true}}"""
        val archive = decodeDiaryArchive(json.toByteArray(Charsets.UTF_8))
        assertEquals(listOf(personalProfileId), archive.profiles.map { it.id })
        assertEquals(personalProfileId, archive.activeProfileId)
    }

    @Test
    fun encryptedBackupKeepsAllLocalProfileData() {
        val name = "profile-backup-${UUID.randomUUID()}.db"
        val repository = DiaryRepository(context, name)
        try {
            repository.createPlan("Мой план")
            val selfPlan = repository.load().plans.single()
            repository.finish(repository.start(selfPlan.id))
            repository.createTraineeProfile("Мария")
            val traineeId = repository.load().activeProfileId
            repository.createPlan("План Марии")
            val traineePlan = repository.load().plans.single()
            repository.finish(repository.start(traineePlan.id))
            repository.setActiveProfile(personalProfileId)
            repository.assignPlanToProfile(selfPlan.id, traineeId)
            val assignmentId = repository.load().assignments.single().id
            repository.setActiveProfile(traineeId)
            val backup = repository.createEncryptedBackup("profile-backup-pass")

            repository.createTraineeProfile("Временный профиль")
            repository.restoreEncryptedBackup(backup, "profile-backup-pass")

            assertEquals(setOf("Мой дневник", "Мария"), repository.load().profiles.map { it.name }.toSet())
            assertEquals(traineeId, repository.load().activeProfileId)
            assertEquals(assignmentId, repository.load().assignments.single().id)
            assertEquals(listOf("План Марии"), repository.load().history.map { it.planName })
            repository.setActiveProfile(personalProfileId)
            assertEquals(listOf("Мой план"), repository.load().history.map { it.planName })
        } finally {
            repository.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun assignedPlansAreIndependentCopiesForEachTrainee() {
        val name = "assignments-${UUID.randomUUID()}.db"
        val repository = DiaryRepository(context, name)
        try {
            repository.createPlan("Push")
            val source = repository.load().plans.single()
            repository.addExercise(source.id, "Жим")
            repository.createTraineeProfile("Аня")
            val firstId = repository.load().activeProfileId
            repository.assignPlanToProfile(source.id, firstId)
            val firstPlanId = repository.load().plans.single().id

            repository.createTraineeProfile("Борис")
            val secondId = repository.load().activeProfileId
            repository.assignPlanToProfile(source.id, secondId)
            val secondPlan = repository.load().plans.single()
            assertTrue(firstPlanId != secondPlan.id)

            val localEdit = secondPlan.exercises.single().copy(name = "Изменено только у Бориса")
            repository.updateExercise(localEdit)
            repository.setActiveProfile(firstId)
            assertEquals("Жим", repository.load().plans.single().exercises.single().name)
            repository.setActiveProfile(secondId)
            assertEquals("Изменено только у Бориса", repository.load().plans.single().exercises.single().name)
        } finally {
            repository.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun acceptedProgramUpdateWaitsForActiveSessionToFinish() {
        val name = "program-update-${UUID.randomUUID()}.db"
        val repository = DiaryRepository(context, name)
        try {
            repository.createPlan("Push")
            val source = repository.load().plans.single()
            repository.addExercise(source.id, "Жим")
            repository.createTraineeProfile("Ирина")
            val profileId = repository.load().activeProfileId
            repository.assignPlanToProfile(source.id, profileId)
            val assignment = repository.load().assignments.single()
            val oldSessionId = repository.start(assignment.assignedPlanId)

            repository.setActiveProfile(personalProfileId)
            repository.renamePlan(source.id, "Push v2")
            repository.stageAssignmentUpdate(assignment.id)
            repository.setActiveProfile(profileId)
            val pending = repository.load().assignments.single()
            assertEquals("Push", repository.load().plans.single().name)
            assertEquals(3, pending.pendingVersion)
            assertTrue(repository.acceptAssignmentUpdate(assignment.id))
            assertEquals("Push", repository.load().plans.single().name)

            repository.finish(oldSessionId)
            val updated = repository.load()
            assertEquals("Push v2", updated.plans.single().name)
            assertEquals("Push", updated.history.single().planName)
            assertEquals(2, updated.history.single().planVersion)
            assertEquals(3, updated.assignments.single().version)
            assertNull(updated.assignments.single().pendingVersion)
        } finally {
            repository.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun addingAndRemovingPlansPreservesCycleOrder() {
        val name = "cycle-order-${UUID.randomUUID()}.db"
        val repository = DiaryRepository(context, name)
        try {
            repository.createPlan("A")
            repository.createPlan("B")
            repository.createPlan("C")
            val plans = repository.load().plans.associateBy { it.name }
            repository.setPlanInCycle(plans.getValue("B").id, true)
            repository.setPlanInCycle(plans.getValue("A").id, true)
            repository.setPlanInCycle(plans.getValue("C").id, true)
            assertEquals(listOf("B", "A", "C"), repository.load().plans.filter { it.inCycle }.map { it.name })
            val sessionId = repository.start(plans.getValue("B").id)
            repository.finish(sessionId)
            assertEquals("A", repository.load().nextPlan?.name)

            repository.setPlanInCycle(plans.getValue("A").id, false)
            assertEquals(listOf("B", "C"), repository.load().plans.filter { it.inCycle }.map { it.name })
            assertEquals("C", repository.load().nextPlan?.name)
            repository.setPlanInCycle(plans.getValue("A").id, true)
            repository.setPlanInCycle(plans.getValue("A").id, true)
            assertEquals(listOf("B", "C", "A"), repository.load().plans.filter { it.inCycle }.map { it.name })
            assertEquals("C", repository.load().nextPlan?.name)
        } finally {
            repository.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun planImportRejectsNonFiniteTargetWeights() {
        val json = """{"format":"reptrack-plans","version":1,"plans":[{"id":"p","name":"Bad","position":0,"inCycle":false,"exercises":[{"id":"e","name":"Жим","plannedSets":3,"targetWeight":"Infinity","targetReps":"8","restSeconds":90,"tracksLoad":true}]}]}"""
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            decodePlanExport(json.toByteArray(Charsets.UTF_8))
        }
    }

    @Test
    fun pairedDevicesExchangeAndDeduplicateEncryptedResults() {
        val coach = DiaryRepository(context, "exchange-coach-${UUID.randomUUID()}.db")
        val trainee = DiaryRepository(context, "exchange-trainee-${UUID.randomUUID()}.db")
        try {
            coach.createTraineeProfile("Мария")
            val coachProfileId = coach.load().activeProfileId
            val invitation = coach.createPairingInvitation(coachProfileId)
            val reply = trainee.respondToPairingInvite(invitation, personalProfileId)
            val pending = coach.processPairingResponse(reply.file)
            assertEquals(reply.fingerprint, pending.fingerprint)
            coach.confirmPairing(pending.inviteId)
            trainee.confirmPairing(pending.inviteId)
            val coachLink = coach.trustedLinks(coachProfileId).single()
            val traineeLink = trainee.trustedLinks(personalProfileId).single()
            assertEquals(coachLink.id, traineeLink.id)
            assertEquals(coachLink.fingerprint, traineeLink.fingerprint)

            trainee.createPlan("Push")
            val plan = trainee.load().plans.single()
            trainee.addExercise(plan.id, "Жим лёжа")
            val sessionId = trainee.start(plan.id)
            val set = trainee.load().activeSession!!.exercises.single().sets.single()
            trainee.updateSet(set.id, weight = "80", reps = "8")
            trainee.toggleSet(set)
            trainee.finish(sessionId)

            val report = trainee.createResultPackage(traineeLink.id, setOf(sessionId))
            assertFalse(report.toString(Charsets.UTF_8).contains("Жим лёжа"))
            val preview = coach.previewExchange(report)
            assertEquals("result", preview.type)
            assertEquals(1, preview.recordCount)
            assertEquals(1, coach.acceptExchange(report).importedSessions)
            assertEquals("Жим лёжа", coach.load().history.single().exercises.single().name)
            assertTrue(coach.load().profiles.single { it.id == coachProfileId }.lastImportedAt != null)

            val secondPackage = trainee.createResultPackage(traineeLink.id, setOf(sessionId))
            assertEquals(0, coach.acceptExchange(secondPackage).importedSessions)
            assertEquals(1, coach.load().history.size)
        } finally {
            coach.close()
            trainee.close()
        }
    }

    @Test
    fun telegramDeliveryPacksReadablePdfAndEncryptedImportFile() {
        val coach = DiaryRepository(context, "relay-coach-${UUID.randomUUID()}.db")
        val trainee = DiaryRepository(context, "relay-trainee-${UUID.randomUUID()}.db")
        try {
            coach.createTraineeProfile("Мария")
            val coachProfileId = coach.load().activeProfileId
            val invitation = coach.createPairingInvitation(coachProfileId)
            val response = trainee.respondToPairingInvite(invitation, personalProfileId)
            val pending = coach.processPairingResponse(response.file)
            coach.confirmPairing(pending.inviteId)
            trainee.confirmPairing(pending.inviteId)
            val coachLink = coach.trustedLinks(coachProfileId).single()
            val traineeLink = trainee.trustedLinks(personalProfileId).single()

            val coachRoute = org.json.JSONObject(coach.createTelegramRouteFile(coachLink.id).toString(Charsets.UTF_8))
            val traineeRoute = org.json.JSONObject(trainee.createTelegramRouteFile(traineeLink.id).toString(Charsets.UTF_8))
            assertEquals("TRAINER", coachRoute.getString("role"))
            assertEquals("TRAINEE", traineeRoute.getString("role"))
            assertEquals(coachRoute.getString("localDeviceId"), traineeRoute.getString("remoteDeviceId"))
            assertEquals(traineeRoute.getString("localDeviceId"), coachRoute.getString("remoteDeviceId"))
            assertEquals(coachRoute.getString("fingerprint"), traineeRoute.getString("fingerprint"))

            trainee.createPlan("Push")
            val plan = trainee.load().plans.single()
            trainee.addExercise(plan.id, "Жим лёжа")
            val sessionId = trainee.start(plan.id)
            val set = trainee.load().activeSession!!.exercises.single().sets.single()
            trainee.updateSet(set.id, weight = "80", reps = "8")
            trainee.toggleSet(set)
            val delivery = trainee.finishAndCreateTelegramDelivery(traineeLink.id, sessionId)
            assertNull(trainee.load().activeSession)
            assertEquals(1, trainee.load().history.size)

            val bundleBytes = GZIPInputStream(ByteArrayInputStream(delivery)).use { it.readBytes() }
            val bundle = org.json.JSONObject(bundleBytes.toString(Charsets.UTF_8))
            assertEquals("reptrack-telegram-delivery", bundle.getString("format"))
            assertEquals("Push", bundle.getString("planName"))
            assertTrue(Base64.getDecoder().decode(bundle.getString("reportPdf")).toString(Charsets.US_ASCII).startsWith("%PDF"))
            val resultPackage = Base64.getDecoder().decode(bundle.getString("resultPackage"))
            assertFalse(resultPackage.toString(Charsets.UTF_8).contains("Жим лёжа"))
            assertEquals(1, coach.previewExchange(resultPackage).recordCount)
            assertEquals(1, coach.acceptExchange(resultPackage).importedSessions)

            val retry = trainee.createTelegramDelivery(traineeLink.id, sessionId)
            val retryBundle = org.json.JSONObject(GZIPInputStream(ByteArrayInputStream(retry)).use { it.readBytes() }.toString(Charsets.UTF_8))
            assertEquals(sessionId, retryBundle.getString("sessionId"))
            assertEquals(1, trainee.load().history.size)
            assertEquals(0, coach.acceptExchange(Base64.getDecoder().decode(retryBundle.getString("resultPackage"))).importedSessions)
            assertEquals(1, coach.load().history.size)
        } finally {
            coach.close()
            trainee.close()
        }
    }

    @Test
    fun pairedProgramUpdatesShowConflictAndWaitUntilWorkoutEnds() {
        val coach = DiaryRepository(context, "program-coach-${UUID.randomUUID()}.db")
        val trainee = DiaryRepository(context, "program-trainee-${UUID.randomUUID()}.db")
        try {
            coach.createPlan("Push")
            val source = coach.load().plans.single()
            coach.addExercise(source.id, "Жим")
            coach.createTraineeProfile("Илья")
            val coachProfileId = coach.load().activeProfileId
            coach.assignPlanToProfile(source.id, coachProfileId)
            val assignedPlan = coach.load().plans.single()
            val (coachLink, _) = pairDevices(coach, trainee, coachProfileId)

            val first = coach.createProgramPackage(coachLink.id, assignedPlan.id)
            val firstPreview = trainee.previewExchange(first)
            assertEquals(listOf("Новая программа будет добавлена отдельным планом."), firstPreview.changes)
            assertFalse(trainee.acceptExchange(first).deferred)
            var localPlan = trainee.load().plans.single()
            assertEquals("Жим", localPlan.exercises.single().name)

            trainee.renamePlan(localPlan.id, "Локальная правка")
            val activeSession = trainee.start(localPlan.id)
            coach.addExercise(assignedPlan.id, "Тяга")
            coach.renamePlan(assignedPlan.id, "Push 2")
            val update = coach.createProgramPackage(coachLink.id, assignedPlan.id)
            val updatePreview = trainee.previewExchange(update)
            assertTrue(updatePreview.hasLocalEdits)
            assertTrue(updatePreview.changes.any { it.contains("Тяга") })
            assertTrue(trainee.acceptExchange(update).deferred)
            assertEquals("Локальная правка", trainee.load().plans.single().name)

            val backup = trainee.createEncryptedBackup("pending-update-pass")
            val restoredName = "pending-update-restored-${UUID.randomUUID()}.db"
            var restored = DiaryRepository(context, restoredName)
            try {
                restored.restoreEncryptedBackup(backup, "pending-update-pass")
                assertEquals(activeSession, restored.load().activeSession?.id)
                assertTrue(restored.load().trustedLinks.isEmpty())
                assertEquals("Локальная правка", restored.load().plans.single().name)

                restored.finish(activeSession)
                localPlan = restored.load().plans.single()
                assertEquals("Push 2", localPlan.name)
                assertEquals(listOf("Жим", "Тяга"), localPlan.exercises.map { it.name })
                assertEquals("Локальная правка", restored.load().history.single().planName)
                assertEquals(2, restored.load().history.single().planVersion)
            } finally {
                restored.close()
            }
            restored = DiaryRepository(context, restoredName)
            try {
                assertEquals(listOf("Push 2"), restored.load().plans.map { it.name })
                assertEquals(listOf("Жим", "Тяга"), restored.load().plans.single().exercises.map { it.name })
                assertEquals(1, restored.load().history.size)
            } finally {
                restored.close()
                context.deleteDatabase(restoredName)
            }

            coach.setActiveProfile(personalProfileId)
            assertEquals(listOf("Жим"), coach.load().plans.single().exercises.map { it.name })
            coach.setActiveProfile(coachProfileId)
            assertEquals("Push 2", coach.load().plans.single().name)
        } finally {
            coach.close()
            trainee.close()
        }
    }

    @Test
    fun trainerCanSendIndividuallyEditedAssignedPlanOnlyThroughItsProfileLink() {
        val coach = DiaryRepository(context, "scope-coach-${UUID.randomUUID()}.db")
        val trainee = DiaryRepository(context, "scope-trainee-${UUID.randomUUID()}.db")
        try {
            coach.createPlan("Шаблон")
            val template = coach.load().plans.single()
            coach.addExercise(template.id, "Жим")
            coach.createTraineeProfile("Аня")
            val profileA = coach.load().activeProfileId
            coach.assignPlanToProfile(template.id, profileA)
            val assignedA = coach.load().plans.single()
            coach.updateExercise(assignedA.exercises.single().copy(targetWeight = "80"))
            coach.createTraineeProfile("Борис")
            val profileB = coach.load().activeProfileId
            coach.assignPlanToProfile(template.id, profileB)
            coach.setActiveProfile(profileA)
            val (linkA, _) = pairDevices(coach, trainee, profileA)
            val edited = coach.load().plans.single()
            val exchangePackage = coach.createProgramPackage(linkA.id, edited.id)
            assertEquals("80", trainee.previewExchange(exchangePackage).plan!!.exercises.single().targetWeight)
            assertThrows(RuntimeException::class.java) { coach.createProgramPackage(linkA.id, coach.load().allPlans.single { it.ownerId == profileB }.id) }
        } finally { coach.close(); trainee.close() }
    }

    @Test
    fun consumedInvitationsCannotBeReplayedAfterRevocation() {
        val coach = DiaryRepository(context, "replay-coach-${UUID.randomUUID()}.db")
        val trainee = DiaryRepository(context, "replay-trainee-${UUID.randomUUID()}.db")
        try {
            coach.createTraineeProfile("Оля")
            val invitation = coach.createPairingInvitation(coach.load().activeProfileId)
            val response = trainee.respondToPairingInvite(invitation, personalProfileId)
            val pending = coach.processPairingResponse(response.file)
            coach.confirmPairing(pending.inviteId)
            trainee.confirmPairing(pending.inviteId)
            trainee.revokeLink(trainee.trustedLinks(personalProfileId).single().id)
            assertThrows(RuntimeException::class.java) { trainee.respondToPairingInvite(invitation, personalProfileId) }
        } finally {
            coach.close()
            trainee.close()
        }
    }

    @Test
    fun encryptedExchangeRejectsAuthenticatedHeaderTampering() {
        val secret = ByteArray(32) { it.toByte() }
        val now = System.currentTimeMillis()
        val file = ExchangeCrypto.encrypt(ExchangeHeader("package", "link", "sender", "recipient", "result", now, now + exchangeTtlMs), secret, byteArrayOf(1, 2, 3))
        val root = org.json.JSONObject(file.toString(Charsets.UTF_8))
        val aad = org.json.JSONObject(String(java.util.Base64.getDecoder().decode(root.getString("aad")), Charsets.UTF_8))
        aad.put("recipientDeviceId", "other-device")
        root.put("aad", java.util.Base64.getEncoder().encodeToString(aad.toString().toByteArray(Charsets.UTF_8)))
        assertThrows(Exception::class.java) { ExchangeCrypto.decrypt(root.toString().toByteArray(Charsets.UTF_8), secret) }
        secret.fill(0)
    }

    private fun pairDevices(coach: DiaryRepository, trainee: DiaryRepository, coachProfileId: String): Pair<TrustedLink, TrustedLink> {
        val invitation = coach.createPairingInvitation(coachProfileId)
        val reply = trainee.respondToPairingInvite(invitation, personalProfileId)
        val pending = coach.processPairingResponse(reply.file)
        assertEquals(reply.fingerprint, pending.fingerprint)
        coach.confirmPairing(pending.inviteId)
        trainee.confirmPairing(pending.inviteId)
        return coach.trustedLinks(coachProfileId).single() to trainee.trustedLinks(personalProfileId).single()
    }
}
