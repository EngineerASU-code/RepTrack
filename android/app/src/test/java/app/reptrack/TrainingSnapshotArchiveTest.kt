package app.reptrack

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TrainingSnapshotArchiveTest {
    @Test
    fun singleSessionRoundTripsWithoutChangingSession() {
        val session = completedSession("session-1", LocalDate.of(2026, 11, 17))
        val snapshot = TrainingSnapshot(
            id = "snapshot-1",
            createdAt = 123L,
            scope = TrainingSnapshotScope.SingleSession(session.id),
            sessions = listOf(session),
        )

        assertEquals(snapshot, decodeTrainingSnapshot(encodeTrainingSnapshot(snapshot)))
    }

    @Test
    fun dateRangeIncludesItsEndDateAndRejectsOutOfRangeSessions() {
        val first = completedSession("first", LocalDate.of(2026, 11, 16))
        val last = completedSession("last", LocalDate.of(2026, 11, 17))
        val snapshot = TrainingSnapshot(
            id = "range-1",
            createdAt = 456L,
            scope = TrainingSnapshotScope.DateRange(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 17)),
            sessions = listOf(first, last),
        )

        assertEquals(snapshot, decodeTrainingSnapshot(encodeTrainingSnapshot(snapshot)))
        val outside = completedSession("outside", LocalDate.of(2026, 11, 18))
        assertThrows(IllegalArgumentException::class.java) {
            encodeTrainingSnapshot(snapshot.copy(sessions = listOf(first, last, outside)))
        }
    }

    @Test
    fun rejectsDuplicateSessionIdsAndCorruptedBytes() {
        val session = completedSession("same", LocalDate.of(2026, 11, 17))
        val snapshot = TrainingSnapshot(
            id = "duplicate-1",
            createdAt = 1L,
            scope = TrainingSnapshotScope.DateRange(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 17)),
            sessions = listOf(session, session.copy(endedAt = session.endedAt!! + 1L)),
        )
        assertThrows(IllegalArgumentException::class.java) { encodeTrainingSnapshot(snapshot) }
        assertThrows(IllegalArgumentException::class.java) { decodeTrainingSnapshot("{}".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { decodeTrainingSnapshot(ByteArray(maxExchangePayloadBytes + 1)) }
    }

    private fun completedSession(id: String, date: LocalDate) = TrainingSession(
        id = id,
        planId = null,
        planName = "Самостоятельная",
        status = SessionStatus.COMPLETED,
        startedAt = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        endedAt = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 1_000L,
        exercises = emptyList(),
    )
}
