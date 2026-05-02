package com.ajsharm.imagen

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.ajsharm.imagen.di.ServiceLocator
import com.ajsharm.imagen.ui.AppIntent
import com.ajsharm.imagen.ui.AppViewModel
import com.ajsharm.imagen.ui.GenerationStatus
import com.ajsharm.imagen.ui.screens.ChatScreen
import com.ajsharm.imagen.ui.screens.EmptyState
import com.ajsharm.imagen.ui.screens.FirstRunSetup
import com.ajsharm.imagen.ui.screens.ImageViewerScreen
import com.ajsharm.imagen.ui.screens.PromptComposer
import com.ajsharm.imagen.ui.screens.SessionListPanel
import com.ajsharm.imagen.ui.screens.SettingsContent
import com.ajsharm.imagen.ui.theme.ImagenTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Handle SEND intents (share an image into the app).
        handleShareIntent(intent)

        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent) {
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> {
                val u = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                listOfNotNull(u)
            }
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList().orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            viewModel.handle(AppIntent.AddDraftImages(uris))
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun App() {
        val state by viewModel.state.collectAsState()
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()

        // Show toasts via system Toast for v1.
        LaunchedEffect(state.toast) {
            val msg = state.toast ?: return@LaunchedEffect
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            viewModel.handle(AppIntent.ToastShown(msg))
        }

        ImagenTheme(state.theme) {
            if (!state.isReady) return@ImagenTheme
            if (!state.configReady) {
                FirstRunSetup(initial = ServiceLocator.secureConfig) { ep, k, d, v ->
                    viewModel.handle(AppIntent.SaveConfig(ep, k, d, v))
                }
                return@ImagenTheme
            }

            // System launchers
            val voiceLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val texts = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    val first = texts?.firstOrNull()
                    if (!first.isNullOrBlank()) {
                        val current = viewModel.state.value.draftPrompt
                        val merged = if (current.isBlank()) first else "$current $first"
                        viewModel.handle(AppIntent.UpdateDraftPrompt(merged))
                    }
                }
            }

            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/zip")
            ) { uri -> if (uri != null) viewModel.handle(AppIntent.ExportBackup(uri)) }

            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri -> if (uri != null) viewModel.handle(AppIntent.ImportBackup(uri)) }

            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            // Sync state.isSessionPanelOpen <-> drawerState
            LaunchedEffect(state.isSessionPanelOpen) {
                if (state.isSessionPanelOpen) drawerState.open() else drawerState.close()
            }
            LaunchedEffect(drawerState.currentValue) {
                if (drawerState.currentValue == DrawerValue.Open && !state.isSessionPanelOpen) {
                    viewModel.handle(AppIntent.OpenSessionPanel)
                } else if (drawerState.currentValue == DrawerValue.Closed && state.isSessionPanelOpen) {
                    viewModel.handle(AppIntent.CloseSessionPanel)
                }
            }

            // Back handlers — pattern §1.5 priority order.
            BackHandler(enabled = state.viewerImage != null) {
                viewModel.handle(AppIntent.CloseViewer)
            }
            BackHandler(enabled = state.viewerImage == null && state.isSettingsOpen) {
                viewModel.handle(AppIntent.CloseSettings)
            }
            BackHandler(enabled = state.viewerImage == null && !state.isSettingsOpen && state.pendingDelete != null) {
                viewModel.handle(AppIntent.CancelDelete)
            }
            BackHandler(enabled = state.viewerImage == null && !state.isSettingsOpen && state.pendingDelete == null && state.isSessionPanelOpen) {
                viewModel.handle(AppIntent.CloseSessionPanel)
            }
            BackHandler(enabled = state.viewerImage == null && !state.isSettingsOpen && state.pendingDelete == null && !state.isSessionPanelOpen && state.generation is GenerationStatus.Generating) {
                viewModel.handle(AppIntent.CancelGeneration)
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    SessionListPanel(
                        sessions = state.sessions,
                        currentId = state.currentSessionId,
                        onCreate = { viewModel.handle(AppIntent.CreateSession) },
                        onSelect = { viewModel.handle(AppIntent.SelectSession(it)) },
                        onRename = { id, name -> viewModel.handle(AppIntent.RenameSession(id, name)) },
                        onDelete = { viewModel.handle(AppIntent.RequestDeleteSession(it)) },
                        onSettings = { viewModel.handle(AppIntent.OpenSettings) },
                        onExport = {
                            exportLauncher.launch("imagen-backup-${System.currentTimeMillis()}.zip")
                        },
                        onImport = {
                            importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        },
                    )
                },
            ) {
                if (state.currentSessionId == null) {
                    EmptyState(onCreate = { viewModel.handle(AppIntent.CreateSession) })
                } else {
                    ChatScreen(
                        state = state,
                        onMenu = { viewModel.handle(AppIntent.OpenSessionPanel) },
                        onTapImage = { viewModel.handle(AppIntent.OpenViewer(it)) },
                        onSave = { viewModel.saveToGallery(it) },
                        onShare = { shareImage(it) },
                        onUseAsInput = { viewModel.handle(AppIntent.UseAsInput(it)) },
                        onReRun = { viewModel.handle(AppIntent.ReRunMessage(it)) },
                        composer = {
                            PromptComposer(
                                state = state,
                                onPromptChange = { viewModel.handle(AppIntent.UpdateDraftPrompt(it)) },
                                onAddImages = { viewModel.handle(AppIntent.AddDraftImages(it)) },
                                onRemoveImage = { viewModel.handle(AppIntent.RemoveDraftImage(it)) },
                                onSizeChange = { viewModel.handle(AppIntent.UpdateSize(it)) },
                                onQualityChange = { viewModel.handle(AppIntent.UpdateQuality(it)) },
                                onSend = { viewModel.handle(AppIntent.Send) },
                                onCancel = { viewModel.handle(AppIntent.CancelGeneration) },
                                onMic = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your prompt")
                                    }
                                    runCatching { voiceLauncher.launch(intent) }
                                        .onFailure { viewModel.handle(AppIntent.ShowToast("Voice input not available")) }
                                },
                            )
                        },
                    )
                }
            }

            // Settings sheet
            if (state.isSettingsOpen) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.handle(AppIntent.CloseSettings) },
                    sheetState = sheetState,
                ) {
                    SettingsContent(
                        config = ServiceLocator.secureConfig,
                        currentTheme = state.theme,
                        onSave = { ep, k, d, v -> viewModel.handle(AppIntent.SaveConfig(ep, k, d, v)) },
                        onThemeChange = { viewModel.handle(AppIntent.SetTheme(it)) },
                        onExport = {
                            scope.launch {
                                exportLauncher.launch("imagen-backup-${System.currentTimeMillis()}.zip")
                            }
                        },
                        onImport = {
                            scope.launch {
                                importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                            }
                        },
                    )
                }
            }

            // Confirm delete
            state.pendingDelete?.let { s ->
                AlertDialog(
                    onDismissRequest = { viewModel.handle(AppIntent.CancelDelete) },
                    title = { Text("Delete session?") },
                    text = { Text("\"${s.name}\" and all its images will be permanently removed.") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.handle(AppIntent.ConfirmDelete) }) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.handle(AppIntent.CancelDelete) }) { Text("Cancel") }
                    },
                )
            }

            // Full-screen image viewer
            state.viewerImage?.let { path ->
                ImageViewerScreen(
                    relativePath = path,
                    onClose = { viewModel.handle(AppIntent.CloseViewer) },
                    onSave = { viewModel.saveToGallery(it) },
                    onShare = { shareImage(it) },
                )
            }
        }
    }

    private fun shareImage(relativePath: String) {
        val file = ServiceLocator.imageStorage.absolute(relativePath)
        if (!file.exists()) return
        val authority = "$packageName.fileprovider"
        val uri = runCatching { FileProvider.getUriForFile(this, authority, file) }
            .getOrElse {
                Toast.makeText(this, "Share unavailable", Toast.LENGTH_SHORT).show()
                return
            }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share image"))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}
