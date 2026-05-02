package com.ajsharm.imagen.ui

import android.net.Uri

sealed interface AppIntent {
    data object OpenSessionPanel : AppIntent
    data object CloseSessionPanel : AppIntent
    data object OpenSettings : AppIntent
    data object CloseSettings : AppIntent

    data object CreateSession : AppIntent
    data class SelectSession(val id: String) : AppIntent
    data class RequestDeleteSession(val session: SessionSummary) : AppIntent
    data object ConfirmDelete : AppIntent
    data object CancelDelete : AppIntent
    data class RenameSession(val id: String, val name: String) : AppIntent

    data class UpdateDraftPrompt(val text: String) : AppIntent
    data class AddDraftImages(val uris: List<Uri>) : AppIntent
    data class RemoveDraftImage(val id: String) : AppIntent
    data class ReorderDraftImages(val ordered: List<DraftImage>) : AppIntent
    data class UpdateSize(val size: ImageSize) : AppIntent
    data class UpdateQuality(val q: ImageQuality) : AppIntent

    data object Send : AppIntent
    data object CancelGeneration : AppIntent
    data class DeleteMessage(val msg: Message) : AppIntent
    data class ReRunMessage(val msg: Message) : AppIntent
    data class UseAsInput(val msg: Message) : AppIntent

    data class OpenViewer(val path: String) : AppIntent
    data object CloseViewer : AppIntent

    data class SetTheme(val choice: ThemeChoice) : AppIntent
    data class SaveConfig(
        val endpoint: String,
        val apiKey: String,
        val deployment: String,
        val apiVersion: String,
    ) : AppIntent

    data class ToastShown(val token: String) : AppIntent
    data class ShowToast(val message: String) : AppIntent

    data class ExportBackup(val target: Uri) : AppIntent
    data class ImportBackup(val source: Uri) : AppIntent
}
