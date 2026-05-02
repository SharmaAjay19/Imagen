package com.ajsharm.imagen.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
private data class BackupRoot(
    val version: Int = 1,
    val exportedAt: Long,
    val sessions: List<BackupSession>,
)

@Serializable
private data class BackupSession(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<BackupMessage>,
)

@Serializable
private data class BackupMessage(
    val id: String,
    val timestamp: Long,
    val prompt: String,
    val inputImagePaths: List<String>,
    val outputImagePath: String?,
    val size: String,
    val quality: String,
    val revisedPrompt: String?,
    val status: String,
    val error: String?,
    val durationMs: Long,
)

class BackupManager(
    private val context: Context,
    private val db: ImagenDatabase,
    private val imageStorage: ImageStorage,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val pathListSerializer = ListSerializer(String.serializer())

    suspend fun exportTo(out: Uri): Int = withContext(Dispatchers.IO) {
        val sessionRows = db.sessionDao().observeAll().first()
        val backupSessions = sessionRows.map { row ->
            val msgs = db.messageDao().listBySession(row.id).map { m ->
                BackupMessage(
                    id = m.id,
                    timestamp = m.timestamp,
                    prompt = m.prompt,
                    inputImagePaths = runCatching {
                        Json.decodeFromString(pathListSerializer, m.inputImagePathsJson)
                    }.getOrDefault(emptyList()),
                    outputImagePath = m.outputImagePath,
                    size = m.size,
                    quality = m.quality,
                    revisedPrompt = m.revisedPrompt,
                    status = m.status,
                    error = m.error,
                    durationMs = m.durationMs,
                )
            }
            BackupSession(row.id, row.name, row.createdAt, row.updatedAt, msgs)
        }
        val root = BackupRoot(exportedAt = System.currentTimeMillis(), sessions = backupSessions)

        context.contentResolver.openOutputStream(out)?.use { os ->
            ZipOutputStream(os).use { zos ->
                zos.putNextEntry(ZipEntry("backup.json"))
                zos.write(json.encodeToString(BackupRoot.serializer(), root).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                val allFiles = backupSessions.flatMap { s ->
                    s.messages.flatMap { m ->
                        (m.outputImagePath?.let { listOf(it) } ?: emptyList()) + m.inputImagePaths
                    }
                }.toSet()
                for (rel in allFiles) {
                    val f = imageStorage.absolute(rel)
                    if (!f.exists()) continue
                    zos.putNextEntry(ZipEntry(rel))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        } ?: error("Could not open output for backup")
        backupSessions.size
    }

    /** Replace-mode import. Returns number of sessions imported. */
    suspend fun importFrom(input: Uri): Int = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "import-${System.currentTimeMillis()}.zip")
        context.contentResolver.openInputStream(input)?.use { ins ->
            FileOutputStream(tmp).use { ins.copyTo(it) }
        } ?: error("Could not open backup file")

        var rootJson: BackupRoot? = null
        ZipInputStream(tmp.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "backup.json" -> {
                        val text = zis.readBytes().toString(Charsets.UTF_8)
                        rootJson = json.decodeFromString(BackupRoot.serializer(), text)
                    }
                    name.startsWith("uploads/") || name.startsWith("generated/") -> {
                        val outFile = imageStorage.absolute(name)
                        if (withinFilesDir(outFile)) {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { zis.copyTo(it) }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        tmp.delete()
        val root = rootJson ?: error("Missing backup.json")

        for (s in root.sessions) {
            db.sessionDao().upsert(SessionEntity(s.id, s.name, s.createdAt, s.updatedAt))
            for (m in s.messages) {
                db.messageDao().insert(
                    MessageEntity(
                        id = m.id,
                        sessionId = s.id,
                        timestamp = m.timestamp,
                        prompt = m.prompt,
                        inputImagePathsJson = Json.encodeToString(pathListSerializer, m.inputImagePaths),
                        outputImagePath = m.outputImagePath,
                        size = m.size,
                        quality = m.quality,
                        revisedPrompt = m.revisedPrompt,
                        status = m.status,
                        error = m.error,
                        durationMs = m.durationMs,
                    )
                )
            }
        }
        root.sessions.size
    }

    private fun withinFilesDir(target: File): Boolean {
        val canonicalRoot = context.filesDir.canonicalPath
        return runCatching { target.canonicalPath.startsWith(canonicalRoot) }.getOrDefault(false)
    }
}
