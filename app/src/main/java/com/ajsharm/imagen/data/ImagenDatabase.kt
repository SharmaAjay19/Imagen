package com.ajsharm.imagen.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val timestamp: Long,
    val prompt: String,
    val inputImagePathsJson: String,
    val outputImagePath: String?,
    val size: String,
    val quality: String,
    val revisedPrompt: String?,
    val status: String,
    val error: String?,
    val durationMs: Long,
)

data class SessionWithCount(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(name = "messageCount") val messageCount: Int,
)

@Dao
interface SessionDao {
    @Query("""
        SELECT s.id, s.name, s.createdAt, s.updatedAt,
               (SELECT COUNT(*) FROM messages m WHERE m.sessionId = s.id) AS messageCount
        FROM sessions s ORDER BY s.updatedAt DESC
    """)
    fun observeAll(): Flow<List<SessionWithCount>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    @Query("UPDATE sessions SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, name: String, now: Long)

    @Query("UPDATE sessions SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sid ORDER BY timestamp ASC")
    fun observeBySession(sid: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sid ORDER BY timestamp ASC")
    suspend fun listBySession(sid: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(msg: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [SessionEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ImagenDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
}
