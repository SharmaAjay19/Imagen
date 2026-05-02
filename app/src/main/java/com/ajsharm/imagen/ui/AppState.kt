package com.ajsharm.imagen.ui

import android.net.Uri

enum class ImageSize(val api: String, val display: String) {
    S1024("1024x1024", "1024×1024"),
    S1024x1536("1024x1536", "1024×1536"),
    S1536x1024("1536x1024", "1536×1024"),
    AUTO("auto", "Auto"),
}

enum class ImageQuality(val api: String, val display: String) {
    AUTO("auto", "Auto"),
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High"),
}

enum class ThemeChoice { MIDNIGHT, PAPER, DYNAMIC }

data class SessionSummary(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int,
)

data class Message(
    val id: String,
    val sessionId: String,
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

/** Pre-saved staging image for the composer. URI is a content:// pointer. */
data class DraftImage(
    val id: String,
    val uri: Uri,
)

sealed interface GenerationStatus {
    data object Idle : GenerationStatus
    data class Generating(val startedAt: Long) : GenerationStatus
    data class Error(val message: String) : GenerationStatus
}

data class AppState(
    val isReady: Boolean = false,
    val configReady: Boolean = false,

    val sessions: List<SessionSummary> = emptyList(),

    val currentSessionId: String? = null,
    val messages: List<Message> = emptyList(),

    val draftPrompt: String = "",
    val draftImages: List<DraftImage> = emptyList(),
    val size: ImageSize = ImageSize.S1024,
    val quality: ImageQuality = ImageQuality.AUTO,

    val generation: GenerationStatus = GenerationStatus.Idle,
    val elapsedMillis: Long = 0L,

    val isSessionPanelOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val viewerImage: String? = null,
    val pendingDelete: SessionSummary? = null,
    val toast: String? = null,

    val theme: ThemeChoice = ThemeChoice.MIDNIGHT,
)

const val MAX_REFERENCE_IMAGES = 10
