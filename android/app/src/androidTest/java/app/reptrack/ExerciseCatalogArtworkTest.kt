package app.reptrack

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseCatalogArtworkTest {
    @Test
    fun everyCatalogExerciseHasAnAvailableLocalImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val checkedSheets = mutableSetOf<Int>()
        exerciseCatalog.forEach { entry ->
            if (entry.artKey.startsWith("sprite:")) {
                val parts = entry.artKey.split(':')
                val sheet = parts[1].toInt()
                val cell = parts[2].toInt()
                assertTrue("Invalid sprite cell for ${entry.id}", cell in 0..15)
                if (checkedSheets.add(sheet)) {
                    context.assets.open("exercise-artwork/generated/exercise-catalog-sheet-%02d.png".format(sheet)).use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        assertTrue("Unreadable sprite sheet $sheet for ${entry.id}", bitmap != null)
                        bitmap?.recycle()
                    }
                }
            } else {
                val resourceId = context.resources.getIdentifier(entry.artKey, "drawable", context.packageName)
                assertTrue("Missing drawable ${entry.artKey} for ${entry.id}", resourceId != 0)
            }
        }
    }
}
