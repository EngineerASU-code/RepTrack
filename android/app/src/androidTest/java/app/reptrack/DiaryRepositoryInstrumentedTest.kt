package app.reptrack

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

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
            assertEquals(2, db.version)
            assertEquals(1, db.rawQuery("SELECT COUNT(*) FROM plans WHERE id='plan-v1'", null).use { it.moveToFirst(); it.getInt(0) })
            assertEquals(1, db.rawQuery("SELECT COUNT(*) FROM sessions WHERE id='session-v1'", null).use { it.moveToFirst(); it.getInt(0) })
            assertEquals(DiarySettings(), helper.settings())
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
}
