package com.ajsharm.imagen.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ajsharm.imagen.ui.AppState
import com.ajsharm.imagen.ui.DraftImage
import com.ajsharm.imagen.ui.GenerationStatus
import com.ajsharm.imagen.ui.ImageQuality
import com.ajsharm.imagen.ui.ImageSize
import com.ajsharm.imagen.ui.MAX_REFERENCE_IMAGES
import com.ajsharm.imagen.ui.theme.LocalImagenColors
import com.ajsharm.imagen.util.DurationFormat

@Composable
fun PromptComposer(
    state: AppState,
    onPromptChange: (String) -> Unit,
    onAddImages: (List<android.net.Uri>) -> Unit,
    onRemoveImage: (String) -> Unit,
    onSizeChange: (ImageSize) -> Unit,
    onQualityChange: (ImageQuality) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onMic: () -> Unit,
) {
    val c = LocalImagenColors.current
    val isGenerating = state.generation is GenerationStatus.Generating

    val pickerCount = (MAX_REFERENCE_IMAGES - state.draftImages.size).coerceIn(2, MAX_REFERENCE_IMAGES)
    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = pickerCount),
    ) { uris -> if (uris.isNotEmpty()) onAddImages(uris) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface)
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Reference image strip
        if (state.draftImages.isNotEmpty() || true) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.draftImages, key = { it.id }) { img ->
                    Box {
                        AsyncImage(
                            model = img.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(c.surfaceElevated),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(c.background.copy(alpha = 0.85f))
                                .clickable { onRemoveImage(img.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = c.onSurface, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                if (state.draftImages.size < MAX_REFERENCE_IMAGES) {
                    item {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(c.surfaceElevated)
                                .clickable {
                                    pickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add images", tint = c.muted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // Prompt field row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.surfaceElevated)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicTextField(
                value = state.draftPrompt,
                onValueChange = onPromptChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp, max = 160.dp),
                textStyle = TextStyle(color = c.onSurface, fontSize = 15.sp),
                cursorBrush = SolidColor(c.accent),
                decorationBox = { inner ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (state.draftPrompt.isEmpty()) {
                            Text("Describe what you want…", color = c.muted, fontSize = 15.sp)
                        }
                        inner()
                    }
                },
            )
            IconButton(onClick = onMic, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Mic, contentDescription = "Voice prompt", tint = c.muted)
            }
        }

        Spacer(Modifier.height(6.dp))

        // Controls row
        Row(verticalAlignment = Alignment.CenterVertically) {
            SizeMenu(state.size, onSizeChange)
            Spacer(Modifier.width(8.dp))
            QualityMenu(state.quality, onQualityChange)
            Spacer(Modifier.weight(1f))
            if (isGenerating) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.surfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        DurationFormat.live(state.elapsedMillis),
                        color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.width(8.dp))
                SendButton(stop = true, accent = c.error, onColor = c.userBubbleOn, onClick = onCancel)
            } else {
                SendButton(stop = false, accent = c.accent, onColor = c.accentOn, onClick = onSend)
            }
        }
    }
}

@Composable
private fun SendButton(stop: Boolean, accent: androidx.compose.ui.graphics.Color, onColor: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(accent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (stop) Icons.Filled.Stop else Icons.Filled.Send,
            contentDescription = if (stop) "Cancel" else "Send",
            tint = onColor,
        )
    }
}

@Composable
private fun SizeMenu(value: ImageSize, onChange: (ImageSize) -> Unit) {
    val c = LocalImagenColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(c.surfaceElevated)
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Tune, contentDescription = null, tint = c.muted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(value.display, color = c.onSurface, fontSize = 12.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ImageSize.values().forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.display) },
                    onClick = { onChange(s); open = false },
                )
            }
        }
    }
}

@Composable
private fun QualityMenu(value: ImageQuality, onChange: (ImageQuality) -> Unit) {
    val c = LocalImagenColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(c.surfaceElevated)
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Q: ${value.display}", color = c.onSurface, fontSize = 12.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ImageQuality.values().forEach { q ->
                DropdownMenuItem(
                    text = { Text(q.display) },
                    onClick = { onChange(q); open = false },
                )
            }
        }
    }
}
