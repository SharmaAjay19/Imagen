package com.ajsharm.imagen.ui

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ajsharm.imagen.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val sl = ServiceLocator
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state

    private var generationJob: Job? = null
    private var messagesObservationJob: Job? = null

    init {
        viewModelScope.launch { bootstrap() }
        // Theme is observed continuously (display preference).
        viewModelScope.launch {
            sl.prefs.theme.collectLatest { t -> _state.update { it.copy(theme = t) } }
        }
        // Sessions list observed continuously (display).
        viewModelScope.launch {
            sl.repository.sessions.collectLatest { s -> _state.update { it.copy(sessions = s) } }
        }
    }

    private suspend fun bootstrap() {
        val startup = sl.prefs.loadStartup()
        val configReady = sl.secureConfig.isConfigured
        // currentSessionId loaded ONCE then managed in-memory (avoids back-exit race).
        _state.update {
            it.copy(
                isReady = true,
                configReady = configReady,
                currentSessionId = startup.currentSessionId,
                size = startup.size,
                quality = startup.quality,
            )
        }
        startup.currentSessionId?.let { startObservingMessages(it) }
    }

    fun handle(intent: AppIntent) {
        when (intent) {
            AppIntent.OpenSessionPanel -> _state.update { it.copy(isSessionPanelOpen = true) }
            AppIntent.CloseSessionPanel -> _state.update { it.copy(isSessionPanelOpen = false) }
            AppIntent.OpenSettings -> _state.update { it.copy(isSettingsOpen = true) }
            AppIntent.CloseSettings -> _state.update { it.copy(isSettingsOpen = false) }

            AppIntent.CreateSession -> viewModelScope.launch {
                val id = sl.repository.createSession()
                selectSession(id)
                _state.update { it.copy(isSessionPanelOpen = false) }
            }

            is AppIntent.SelectSession -> viewModelScope.launch { selectSession(intent.id) }

            is AppIntent.RequestDeleteSession ->
                _state.update { it.copy(pendingDelete = intent.session) }
            AppIntent.CancelDelete ->
                _state.update { it.copy(pendingDelete = null) }
            AppIntent.ConfirmDelete -> viewModelScope.launch {
                val s = _state.value.pendingDelete ?: return@launch
                sl.repository.deleteSession(s.id)
                if (_state.value.currentSessionId == s.id) {
                    messagesObservationJob?.cancel()
                    _state.update { it.copy(currentSessionId = null, messages = emptyList()) }
                    sl.prefs.setCurrentSession(null)
                }
                _state.update { it.copy(pendingDelete = null) }
            }
            is AppIntent.RenameSession -> viewModelScope.launch {
                sl.repository.renameSession(intent.id, intent.name)
            }

            is AppIntent.UpdateDraftPrompt ->
                _state.update { it.copy(draftPrompt = intent.text) }
            is AppIntent.AddDraftImages -> {
                val current = _state.value.draftImages
                val remaining = MAX_REFERENCE_IMAGES - current.size
                val toAdd = intent.uris.take(remaining).map { DraftImage(UUID.randomUUID().toString(), it) }
                _state.update { it.copy(draftImages = current + toAdd) }
            }
            is AppIntent.RemoveDraftImage ->
                _state.update { s -> s.copy(draftImages = s.draftImages.filterNot { it.id == intent.id }) }
            is AppIntent.ReorderDraftImages ->
                _state.update { it.copy(draftImages = intent.ordered) }
            is AppIntent.UpdateSize -> {
                _state.update { it.copy(size = intent.size) }
                viewModelScope.launch { sl.prefs.setSize(intent.size) }
            }
            is AppIntent.UpdateQuality -> {
                _state.update { it.copy(quality = intent.q) }
                viewModelScope.launch { sl.prefs.setQuality(intent.q) }
            }

            AppIntent.Send -> startGeneration()
            AppIntent.CancelGeneration -> {
                generationJob?.cancel()
                generationJob = null
                _state.update { it.copy(generation = GenerationStatus.Idle, elapsedMillis = 0L) }
            }
            is AppIntent.DeleteMessage -> viewModelScope.launch {
                sl.repository.deleteMessage(intent.msg)
            }
            is AppIntent.ReRunMessage -> {
                _state.update { it.copy(draftPrompt = intent.msg.prompt) }
                // Don't try to recreate input file URIs — user can reattach if needed.
            }
            is AppIntent.UseAsInput -> {
                val path = intent.msg.outputImagePath ?: return
                val uri = android.net.Uri.fromFile(sl.imageStorage.absolute(path))
                val current = _state.value.draftImages
                if (current.size < MAX_REFERENCE_IMAGES) {
                    _state.update {
                        it.copy(draftImages = current + DraftImage(UUID.randomUUID().toString(), uri))
                    }
                }
            }

            is AppIntent.OpenViewer -> _state.update { it.copy(viewerImage = intent.path) }
            AppIntent.CloseViewer -> _state.update { it.copy(viewerImage = null) }

            is AppIntent.SetTheme -> viewModelScope.launch { sl.prefs.setTheme(intent.choice) }
            is AppIntent.SaveConfig -> {
                sl.secureConfig.endpoint = intent.endpoint
                sl.secureConfig.apiKey = intent.apiKey
                sl.secureConfig.deploymentName = intent.deployment
                sl.secureConfig.apiVersion = intent.apiVersion
                _state.update { it.copy(configReady = sl.secureConfig.isConfigured, isSettingsOpen = false) }
            }

            is AppIntent.ShowToast ->
                _state.update { it.copy(toast = intent.message) }
            is AppIntent.ToastShown ->
                _state.update { if (it.toast == intent.token) it.copy(toast = null) else it }

            is AppIntent.ExportBackup -> viewModelScope.launch {
                runCatching { sl.backupManager.exportTo(intent.target) }
                    .onSuccess { count ->
                        _state.update { it.copy(toast = "Exported $count session(s)") }
                    }
                    .onFailure { e ->
                        _state.update { it.copy(toast = "Export failed: ${e.message}") }
                    }
            }
            is AppIntent.ImportBackup -> viewModelScope.launch {
                runCatching { sl.backupManager.importFrom(intent.source) }
                    .onSuccess { count ->
                        _state.update { it.copy(toast = "Imported $count session(s)") }
                    }
                    .onFailure { e ->
                        _state.update { it.copy(toast = "Import failed: ${e.message}") }
                    }
            }
        }
    }

    private suspend fun selectSession(id: String) {
        if (_state.value.currentSessionId == id) {
            _state.update { it.copy(isSessionPanelOpen = false) }
            return
        }
        startObservingMessages(id)
        _state.update { it.copy(currentSessionId = id, isSessionPanelOpen = false, messages = emptyList()) }
        sl.prefs.setCurrentSession(id)
    }

    private fun startObservingMessages(sid: String) {
        messagesObservationJob?.cancel()
        messagesObservationJob = viewModelScope.launch {
            sl.repository.observeMessages(sid).distinctUntilChanged().collectLatest { msgs ->
                if (_state.value.currentSessionId == sid) {
                    _state.update { it.copy(messages = msgs) }
                }
            }
        }
    }

    private fun startGeneration() {
        val s = _state.value
        if (s.generation is GenerationStatus.Generating) return
        if (!s.configReady) {
            _state.update { it.copy(toast = "Configure your Azure endpoint and API key first.") }
            return
        }
        val prompt = s.draftPrompt.trim()
        if (prompt.isEmpty()) {
            _state.update { it.copy(toast = "Enter a prompt.") }
            return
        }
        // Capture target session at intent time (pattern §1.2 corollary).
        val targetSessionId = s.currentSessionId
        val draftImages = s.draftImages
        val size = s.size
        val quality = s.quality

        val started = System.currentTimeMillis()
        _state.update { it.copy(generation = GenerationStatus.Generating(started), elapsedMillis = 0L) }
        // Live ticker.
        val ticker = viewModelScope.launch {
            while (true) {
                delay(100)
                val gen = _state.value.generation
                if (gen !is GenerationStatus.Generating) break
                _state.update { it.copy(elapsedMillis = System.currentTimeMillis() - gen.startedAt) }
            }
        }

        generationJob = viewModelScope.launch {
            try {
                // Ensure session exists (create on send if none).
                val sessionId = targetSessionId ?: sl.repository.createSession().also { id ->
                    _state.update { st -> st.copy(currentSessionId = id) }
                    sl.prefs.setCurrentSession(id)
                    startObservingMessages(id)
                }

                // Persist input images first.
                val inputPaths = draftImages.map { sl.imageStorage.saveUploadFromUri(it.uri) }
                val inputFiles = inputPaths.map { sl.imageStorage.absolute(it) }

                val result = sl.azureClient.generateOrEdit(prompt, inputFiles, size, quality)
                val outputPath = sl.imageStorage.saveGeneratedBytes(result.pngBytes)
                val durationMs = System.currentTimeMillis() - started

                val msg = Message(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    timestamp = System.currentTimeMillis(),
                    prompt = prompt,
                    inputImagePaths = inputPaths,
                    outputImagePath = outputPath,
                    size = size.api,
                    quality = quality.api,
                    revisedPrompt = result.revisedPrompt,
                    status = "success",
                    error = null,
                    durationMs = durationMs,
                )
                sl.repository.addMessage(msg)
                _state.update {
                    it.copy(
                        generation = GenerationStatus.Idle,
                        elapsedMillis = 0L,
                        draftPrompt = "",
                        draftImages = emptyList(),
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.update { it.copy(generation = GenerationStatus.Idle, elapsedMillis = 0L) }
                throw e
            } catch (e: Throwable) {
                Log.e("Imagen", "Generation failed", e)
                val sessionId = _state.value.currentSessionId
                if (sessionId != null) {
                    val errorMsg = Message(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        timestamp = System.currentTimeMillis(),
                        prompt = prompt,
                        inputImagePaths = emptyList(),
                        outputImagePath = null,
                        size = size.api,
                        quality = quality.api,
                        revisedPrompt = null,
                        status = "error",
                        error = e.message ?: e::class.java.simpleName,
                        durationMs = System.currentTimeMillis() - started,
                    )
                    runCatching { sl.repository.addMessage(errorMsg) }
                }
                _state.update {
                    it.copy(
                        generation = GenerationStatus.Error(e.message ?: "Generation failed"),
                        elapsedMillis = 0L,
                        toast = e.message ?: "Generation failed",
                    )
                }
            } finally {
                ticker.cancel()
                generationJob = null
            }
        }
    }

    /** Saves a generated image to the device's MediaStore (Pictures/Imagen). */
    fun saveToGallery(relativePath: String) {
        viewModelScope.launch {
            val ok = runCatching { saveImageToMediaStore(relativePath) }.getOrElse { false }
            _state.update {
                it.copy(toast = if (ok) "Saved to Pictures/Imagen" else "Save failed")
            }
        }
    }

    private suspend fun saveImageToMediaStore(relativePath: String): Boolean {
        val ctx = getApplication<Application>()
        val src: File = sl.imageStorage.absolute(relativePath)
        if (!src.exists()) return false
        val resolver = ctx.contentResolver
        val name = "imagen-${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Imagen")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri).use { os ->
            if (os == null) return false
            src.inputStream().use { it.copyTo(os) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return true
    }
}
