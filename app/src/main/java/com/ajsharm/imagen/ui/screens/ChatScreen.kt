package com.ajsharm.imagen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ajsharm.imagen.ui.AppState
import com.ajsharm.imagen.ui.GenerationStatus
import com.ajsharm.imagen.ui.Message
import com.ajsharm.imagen.ui.SessionSummary
import com.ajsharm.imagen.ui.theme.LocalImagenColors

@Composable
fun ChatScreen(
    state: AppState,
    onMenu: () -> Unit,
    onTapImage: (String) -> Unit,
    onSave: (String) -> Unit,
    onShare: (String) -> Unit,
    onUseAsInput: (Message) -> Unit,
    onReRun: (Message) -> Unit,
    composer: @Composable () -> Unit,
) {
    val c = LocalImagenColors.current
    val current: SessionSummary? = state.sessions.firstOrNull { it.id == state.currentSessionId }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.generation) {
        val target = state.messages.size + (if (state.generation is GenerationStatus.Generating) 1 else 0) - 1
        if (target >= 0) listState.animateScrollToItem(target)
    }

    Column(modifier = Modifier.fillMaxSize().background(c.background)) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.Menu, contentDescription = "Sessions", tint = c.onSurface)
            }
            Text(
                current?.name ?: "Imagen",
                color = c.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
        }

        // Message list
        Box(modifier = Modifier.weight(1f)) {
            if (state.messages.isEmpty() && state.generation !is GenerationStatus.Generating) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = c.accent, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Start a new generation", color = c.onBackground, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Type a prompt below. Add up to 10 reference images for compositing.",
                        color = c.muted, fontSize = 13.sp,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        MessageItem(
                            msg = msg,
                            onTapImage = onTapImage,
                            onSave = onSave,
                            onShare = onShare,
                            onUseAsInput = onUseAsInput,
                            onReRun = onReRun,
                        )
                    }
                    if (state.generation is GenerationStatus.Generating) {
                        item {
                            GeneratingPlaceholder()
                        }
                    }
                }
            }
        }

        composer()
    }
}

@Composable
private fun GeneratingPlaceholder() {
    val c = LocalImagenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = c.accent)
        Spacer(Modifier.width(8.dp))
        Text("Generating…", color = c.muted, fontSize = 13.sp)
    }
}
