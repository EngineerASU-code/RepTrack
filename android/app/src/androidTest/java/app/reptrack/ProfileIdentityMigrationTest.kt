package app.reptrack

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileIdentityMigrationTest {
    @Test
    fun versionSevenProfilesReceiveUniqueStableIdsWithoutChangingNames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "profile-identity-${UUID.randomUUID()}.db"
        val legacy = context.openOrCreateDatabase(databaseName, 0, null)
        legacy.execSQL(
            "CREATE TABLE profiles(id TEXT PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL, created_at INTEGER NOT NULL, last_imported_at INTEGER)",
        )
        legacy.execSQL("INSERT INTO profiles(id,name,kind,created_at) VALUES('local-owner','Личный','PERSONAL',1)")
        legacy.execSQL("INSERT INTO profiles(id,name,kind,created_at) VALUES('trainee-1','Подопечный 1','TRAINEE',2)")
        legacy.execSQL("INSERT INTO profiles(id,name,kind,created_at) VALUES('trainee-2','Подопечный 2','TRAINEE',3)")
        legacy.execSQL("CREATE TABLE app_settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        legacy.execSQL("INSERT INTO app_settings(key,value) VALUES('theme','PURPLE_PRO')")
        legacy.version = 7
        legacy.close()

        val helper = DiaryDb(context, databaseName)
        try {
            val db = helper.writableDatabase
            assertEquals(8, db.version)
            val firstRead = db.rawQuery("SELECT id,name,public_id FROM profiles ORDER BY id", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
                }
            }
            assertEquals(listOf("Личный", "Подопечный 1", "Подопечный 2"), firstRead.map { it.second })
            assertEquals(firstRead.size, firstRead.map { it.third }.toSet().size)
            firstRead.forEach { (_, _, publicId) -> assertEquals(publicId, UUID.fromString(publicId).toString()) }
            assertEquals(
                "PURPLE_PRO",
                db.rawQuery("SELECT value FROM app_settings WHERE key='theme'", null).use {
                    it.moveToFirst()
                    it.getString(0)
                },
            )
            assertEquals(
                "SELF_DIRECTED",
                db.rawQuery("SELECT value FROM app_settings WHERE key='user_role'", null).use {
                    it.moveToFirst()
                    it.getString(0)
                },
            )

            val stableIds = firstRead.associate { it.first to it.third }
            helper.close()
            val reopened = DiaryDb(context, databaseName)
            try {
                val persistedIds = reopened.writableDatabase.rawQuery("SELECT id,public_id FROM profiles", null).use { cursor ->
                    buildMap {
                        while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1))
                    }
                }
                assertEquals(stableIds, persistedIds)
            } finally {
                reopened.close()
            }
        } finally {
            helper.close()
            context.deleteDatabase(databaseName)
        }
    }
}
