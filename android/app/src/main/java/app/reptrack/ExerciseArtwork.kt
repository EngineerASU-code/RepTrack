package app.reptrack

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object ExerciseArtworkCache {
    private val images = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    @Synchronized
    fun load(context: android.content.Context, artKey: String): Bitmap? {
        images.get(artKey)?.let { return it }
        val bitmap = if (artKey.startsWith("sprite:")) {
            val parts = artKey.split(':')
            val sheetNumber = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..99 } ?: return null
            val cell = parts.getOrNull(2)?.toIntOrNull()?.takeIf { it in 0..15 } ?: return null
            val sheet = runCatching {
                context.assets.open(
                    "exercise-artwork/generated/exercise-catalog-sheet-%02d.png".format(sheetNumber),
                ).use(BitmapFactory::decodeStream)
            }.getOrNull() ?: return null
            val column = cell % 4
            val row = cell / 4
            val left = (column * sheet.width / 4.0).toInt()
            val top = (row * sheet.height / 4.0).toInt()
            val right = ((column + 1) * sheet.width / 4.0).toInt()
            val bottom = ((row + 1) * sheet.height / 4.0).toInt()
            val inset = (minOf(right - left, bottom - top) / 100).coerceAtLeast(2)
            val crop = Bitmap.createBitmap(sheet, left + inset, top + inset, right - left - inset * 2, bottom - top - inset * 2)
            sheet.recycle()
            crop
        } else {
            val resourceId = context.resources.getIdentifier(artKey, "drawable", context.packageName)
            if (resourceId == 0) null else BitmapFactory.decodeResource(context.resources, resourceId)
        }
        bitmap?.let { images.put(artKey, it) }
        return bitmap
    }
}

@Composable
internal fun ExerciseArtwork(entry: ExerciseCatalogEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, entry.artKey, context) {
        value = withContext(Dispatchers.IO) { ExerciseArtworkCache.load(context, entry.artKey)?.asImageBitmap() }
    }.value
    if (bitmap == null) {
        MissingExerciseArtwork(entry.name, modifier, RoundedCornerShape(12.dp))
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = "Фотореалистичная иллюстрация упражнения «${entry.name}»; ознакомительный материал",
            contentScale = if (entry.artKey.startsWith("sprite:")) ContentScale.Fit else ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(12.dp)).semantics {
                contentDescription = "Фотореалистичная иллюстрация упражнения «${entry.name}»; ознакомительный материал"
            },
        )
    }
}

/** Exact-variant artwork mapping; generic grip/equipment names stay unsupported. */
@Composable
fun ExerciseArtwork(name: String, modifier: Modifier = Modifier) {
    val entry = exerciseCatalogEntryForName(name)
    if (entry != null) {
        ExerciseArtwork(entry, modifier)
        return
    }
    val firstPhoto = exercisePhotos(name).firstOrNull()
    if (firstPhoto == null) {
        MissingExerciseArtwork(name, modifier, RoundedCornerShape(12.dp))
    } else {
        CatalogPhoto(name, firstPhoto, "Иллюстрация упражнения «$name»; не является инструкцией по технике", modifier)
    }
}

/** Shows the available local illustrative artwork for an exact exercise variant. */
@Composable
fun ExerciseTechniqueMedia(name: String, modifier: Modifier = Modifier) {
    val catalogEntry = exerciseCatalogEntryForName(name)
    if (catalogEntry != null) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ExerciseArtwork(catalogEntry, Modifier.fillMaxWidth().aspectRatio(1f))
            Text(
                "Иллюстрация для наглядности; техника описана отдельно.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        return
    }
    val photos = exercisePhotos(name)
    if (photos.isEmpty()) {
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .semantics { contentDescription = "Для упражнения «$name» нет проверенных локальных фотографий" }
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Нет проверенных фото для этого варианта",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (photos.size == 1) {
            TechniquePhoto(name, 0, photos.single(), Modifier.fillMaxWidth())
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                photos.forEachIndexed { index, resource ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        TechniquePhoto(name, index, resource, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        Text(
            "Иллюстрации для наглядности; они не заменяют проверенную инструкцию по технике.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TechniquePhoto(name: String, index: Int, resource: Int, modifier: Modifier) {
    CatalogPhoto(
        name,
        resource,
        "Иллюстрация ${index + 1} упражнения «$name»; техника по изображению не сертифицирована",
        modifier.aspectRatio(1.5f),
    )
    Text("Иллюстрация ${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
}

private fun exercisePhotos(name: String): List<Int> = when (
    name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
) {
    "жим штанги средним хватом", "barbell bench press - medium grip" ->
        listOf(R.drawable.exercise_art_bench_press)
    "тяга верхнего блока широким хватом", "wide-grip lat pulldown (cable)" ->
        listOf(R.drawable.exercise_art_lat_pulldown)
    "приседания со штангой", "barbell squat" ->
        listOf(R.drawable.exercise_art_barbell_squat)
    "жим ногами", "жим ногами в тренажёре", "leg press (machine)" ->
        listOf(R.drawable.exercise_art_leg_press)
    "тяга нижнего блока узким хватом", "seated cable rows" ->
        listOf(R.drawable.exercise_art_seated_cable_row)
    "попеременные молотки с гантелями", "молотки с гантелями", "alternate hammer curl (dumbbells, alternating)" ->
        listOf(R.drawable.exercise_art_hammer_curl)
    "плечи стоя, задняя дельта", "задняя дельта, канаты" -> listOf(R.drawable.exercise_art_standing_rear_delt_cable_fly)
    "бицепс скотт (суперсет)", "бицепс на скамье скотта" -> listOf(R.drawable.exercise_art_preacher_curl)
    "беговая дорожка" -> listOf(R.drawable.exercise_art_incline_treadmill_walk)
    "сгибания ног в тренажёре" -> listOf(R.drawable.exercise_art_seated_leg_curl)
    "разгибания ног в тренажёре" -> listOf(R.drawable.exercise_art_leg_extension)
    "подъём на носки стоя" -> listOf(R.drawable.exercise_art_standing_calf_raise)
    "тяга спины hammer", "тяга спина (хаммер)" -> listOf(R.drawable.exercise_art_hammer_row)
    "армейский жим (смит)" -> listOf(R.drawable.exercise_art_smith_shoulder_press)
    "разводка груди butterfly" -> listOf(R.drawable.exercise_art_pec_deck_fly)
    "бицепс стоя со штангой" -> listOf(R.drawable.exercise_art_standing_barbell_curl)
    else -> emptyList()
}

@Composable
private fun CatalogPhoto(name: String, resource: Int, description: String, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap = remember(resource, context) {
        BitmapFactory.decodeResource(context.resources, resource)?.asImageBitmap()
    }
    if (bitmap == null) {
        MissingExerciseArtwork(name, modifier, RoundedCornerShape(12.dp))
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(12.dp)).semantics { contentDescription = description },
        )
    }
}

@Composable
private fun MissingExerciseArtwork(name: String, modifier: Modifier, shape: RoundedCornerShape) {
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .semantics { contentDescription = "Для упражнения «$name» нет подтверждённого изображения" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (maxWidth < 96.dp) "Нет фото" else "Нет подтверждённого\nматериала",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = if (maxWidth < 96.dp) 8.sp else 10.sp,
            lineHeight = if (maxWidth < 96.dp) 10.sp else 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
