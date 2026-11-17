package app.reptrack

import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalIdentityTest {
    @Test
    fun backupPreservesPublicIdAndUserRole() {
        val profile = profile()
        val archive = encodeDiaryArchive(
            plans = emptyList(),
            sessions = emptyList(),
            settings = DiarySettings(userRole = UserRole.TRAINER),
            profiles = listOf(profile),
            activeProfileId = personalProfileId,
            assignments = emptyList(),
        )

        val decoded = decodeDiaryArchive(archive)
        assertEquals(profile.publicId, decoded.profiles.single().publicId)
        assertEquals(UserRole.TRAINER, decoded.settings.userRole)
        assertEquals(profile.publicId, profile.copy(name = "Новое имя").publicId)
    }

    @Test
    fun olderBackupGetsNewPublicIdAndDefaultsToSelfDirectedRole() {
        val json = JSONObject(
            encodeDiaryArchive(
                emptyList(),
                emptyList(),
                DiarySettings(),
                listOf(profile()),
                personalProfileId,
                emptyList(),
            ).toString(Charsets.UTF_8),
        )
        json.put("version", 4)
        json.getJSONArray("profiles").getJSONObject(0).remove("publicId")
        json.getJSONObject("settings").remove("userRole")
        val legacyBytes = json.toString().toByteArray(Charsets.UTF_8)

        val first = decodeDiaryArchive(legacyBytes)
        val second = decodeDiaryArchive(legacyBytes)
        assertNotEquals(first.profiles.single().publicId, second.profiles.single().publicId)
        assertEquals(first.profiles.single().publicId, UUID.fromString(first.profiles.single().publicId).toString())
        assertEquals(UserRole.SELF_DIRECTED, first.settings.userRole)
    }

    @Test
    fun resultExportV1StaysCompatibleAndV2CarriesAuthorMetadata() {
        val session = completedSession()
        val legacy = encodeResultExport(listOf(session))
        assertEquals(1, JSONObject(legacy.toString(Charsets.UTF_8)).getInt("version"))
        assertNull(decodeResultExportEnvelope(legacy).author)
        assertEquals(listOf(session), decodeResultExport(legacy))

        val author = ResultExportAuthor("Тренер", "123e4567-e89b-12d3-a456-426614174000", UserRole.TRAINER)
        val current = encodeResultExport(listOf(session), author)
        val decoded = decodeResultExportEnvelope(current)
        assertEquals(2, JSONObject(current.toString(Charsets.UTF_8)).getInt("version"))
        assertEquals(author, decoded.author)
        assertEquals(listOf(session), decoded.sessions)
        assertTrue(JSONObject(current.toString(Charsets.UTF_8)).has("author"))

        val invalidRole = JSONObject(current.toString(Charsets.UTF_8)).apply {
            getJSONObject("author").put("role", "UNKNOWN")
        }.toString().toByteArray(Charsets.UTF_8)
        assertThrows(IllegalStateException::class.java) { decodeResultExportEnvelope(invalidRole) }
    }

    private fun profile() = DiaryProfile(
        id = personalProfileId,
        name = "Мой дневник",
        kind = ProfileKind.PERSONAL,
        createdAt = 0L,
        publicId = "123e4567-e89b-12d3-a456-426614174000",
    )

    private fun completedSession() = TrainingSession(
        id = "session-1",
        planId = null,
        planName = "Самостоятельная",
        status = SessionStatus.COMPLETED,
        startedAt = 100L,
        endedAt = 200L,
        exercises = emptyList(),
    )
}
