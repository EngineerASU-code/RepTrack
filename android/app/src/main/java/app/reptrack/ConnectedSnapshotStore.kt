package app.reptrack

import android.content.Context
import java.io.File
import java.nio.file.Files

/** Stores immutable training snapshots in app-private files, one archive per snapshot ID. */
internal class ConnectedSnapshotStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "training-snapshots")

    fun save(snapshot: TrainingSnapshot): File {
        val bytes = encodeTrainingSnapshot(snapshot)
        check(directory.exists() || directory.mkdirs()) { "Не удалось создать хранилище снимков" }
        val target = archiveFile(snapshot.id)
        val temporary = File.createTempFile("snapshot-", ".tmp", directory)
        try {
            temporary.writeBytes(bytes)
            Files.move(temporary.toPath(), target.toPath())
            return target
        } finally {
            temporary.delete()
        }
    }

    fun get(id: String): TrainingSnapshot? {
        val file = archiveFile(id)
        if (!file.isFile) return null
        require(file.length() in 1..maxExchangePayloadBytes.toLong()) { "Снимок слишком большой или пустой" }
        return decodeTrainingSnapshot(file.readBytes()).also { require(it.id == id) { "ID снимка не совпадает с именем файла" } }
    }

    fun list(): List<TrainingSnapshot> {
        val files = directory.listFiles() ?: return emptyList()
        return files.filter { it.isFile && it.name.endsWith(".rptsnapshot") }
            .map { file ->
                val id = file.name.removeSuffix(".rptsnapshot")
                get(id) ?: error("Не удалось прочитать снимок $id")
            }
            .sortedByDescending(TrainingSnapshot::createdAt)
    }

    private fun archiveFile(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Некорректный ID снимка" }
        return File(directory, "$id.rptsnapshot")
    }
}
