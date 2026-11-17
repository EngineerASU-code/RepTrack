package app.reptrack

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectedSnapshotStoreInstrumentedTest {
    @Test
    fun snapshotsStaySeparateAndDuplicateIdCannotReplaceAnExistingSnapshot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstId = "test-${UUID.randomUUID()}"
        val secondId = "test-${UUID.randomUUID()}"
        val first = snapshot(firstId, "session-1", 1L)
        val second = snapshot(secondId, "session-2", 2L)
        val store = ConnectedSnapshotStore(context)
        val directory = File(context.filesDir, "training-snapshots")
        try {
            store.save(first)
            store.save(second)

            assertEquals(first, store.get(firstId))
            assertEquals(second, store.get(secondId))
            assertEquals(setOf(firstId, secondId), store.list().map(TrainingSnapshot::id).toSet())
            assertThrows(java.nio.file.FileAlreadyExistsException::class.java) { store.save(first.copy(createdAt = 10L)) }
            assertEquals(first, store.get(firstId))
        } finally {
            File(directory, "$firstId.rptsnapshot").delete()
            File(directory, "$secondId.rptsnapshot").delete()
            directory.delete()
        }
    }

    private fun snapshot(id: String, sessionId: String, createdAt: Long): TrainingSnapshot {
        val date = LocalDate.of(2026, 11, 17)
        val startedAt = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val session = TrainingSession(
            id = sessionId,
            planId = null,
            planName = "Синтетическая тренировка",
            status = SessionStatus.COMPLETED,
            startedAt = startedAt,
            endedAt = startedAt + 1_000L,
            exercises = emptyList(),
        )
        return TrainingSnapshot(id, createdAt, TrainingSnapshotScope.SingleSession(sessionId), listOf(session))
    }
}
