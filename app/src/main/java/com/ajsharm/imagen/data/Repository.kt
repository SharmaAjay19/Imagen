package com.ajsharm.imagen.data

import com.ajsharm.imagen.ui.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.UUID

class Repository(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val imageStorage: ImageStorage,
) {
    val sessions: Flow<List<com.ajsharm.imagen.ui.SessionSummary>> =
        sessionDao.observeAll().map { rows ->
            rows.map {
                com.ajsharm.imagen.ui.SessionSummary(
                    id = it.id, name = it.name,
                    createdAt = it.createdAt, updatedAt = it.updatedAt,
                    messageCount = it.messageCount,
                )
            }
        }

    fun observeMessages(sid: String): Flow<List<Message>> =
        messageDao.observeBySession(sid).map { it.map(::toMessage) }

    suspend fun listMessages(sid: String): List<Message> =
        messageDao.listBySession(sid).map(::toMessage)

    suspend fun createSession(name: String? = null): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val resolved = name?.takeIf { it.isNotBlank() } ?: "Session ${shortDate(now)}"
        sessionDao.upsert(SessionEntity(id, resolved, now, now))
        return id
    }

    suspend fun renameSession(id: String, name: String) {
        sessionDao.rename(id, name.trim().ifBlank { "Untitled" }, System.currentTimeMillis())
    }

    suspend fun touchSession(id: String) {
        sessionDao.touch(id, System.currentTimeMillis())
    }

    suspend fun deleteSession(id: String) {
        val msgs = messageDao.listBySession(id)
        val files = msgs.flatMap { m ->
            (m.outputImagePath?.let { listOf(it) } ?: emptyList()) +
                runCatching {
                    Json.decodeFromString(ListSerializer(String.serializer()), m.inputImagePathsJson)
                }.getOrDefault(emptyList())
        }
        imageStorage.deleteAll(files)
        sessionDao.delete(id)
    }

    suspend fun addMessage(msg: Message) {
        messageDao.insert(toEntity(msg))
        sessionDao.touch(msg.sessionId, System.currentTimeMillis())
    }

    suspend fun deleteMessage(msg: Message) {
        msg.outputImagePath?.let { imageStorage.delete(it) }
        imageStorage.deleteAll(msg.inputImagePaths)
        messageDao.delete(msg.id)
    }

    private fun toMessage(e: MessageEntity) = Message(
        id = e.id,
        sessionId = e.sessionId,
        timestamp = e.timestamp,
            Json.decodeFromString(ListSerializer(String.serializer()), e.inputImagePathsJson)
        }pt = e.prompt,
        inputImagePaths = runCatching { Json.decodeFromString<List<String>>(e.inputImagePathsJson) }
            .getOrDefault(emptyList()),
        outputImagePath = e.outputImagePath,
        size = e.size,
        quality = e.quality,
        revisedPrompt = e.revisedPrompt,
        status = e.status,
        error = e.error,
        durationMs = e.durationMs,
    )

    private fun toEntity(m: Message) = MessageEntity(
        id = m.id,
        sessionId = m.sessionId,
        timestamp = m.timestamp,
        prompt = m.prompt,
        inputImagePathsJson = Json.encodeToString(
            ListSerializer(String.serializer()),
            m.inputImagePaths,
        ),
        outputImagePath = m.outputImagePath,
        size = m.size,
        quality = m.quality,
        revisedPrompt = m.revisedPrompt,
        status = m.status,
        error = m.error,
        durationMs = m.durationMs,
    )

    private fun shortDate(ms: Long): String {
        val fmt = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
        return fmt.format(java.util.Date(ms))
    }
}
