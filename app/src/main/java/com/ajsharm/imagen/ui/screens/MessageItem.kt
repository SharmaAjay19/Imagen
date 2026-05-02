package com.ajsharm.imagen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ajsharm.imagen.di.ServiceLocator
import com.ajsharm.imagen.ui.Message
import com.ajsharm.imagen.ui.theme.LocalImagenColors
import com.ajsharm.imagen.util.DurationFormat

@Composable
fun MessageItem(
    msg: Message,
    onTapImage: (String) -> Unit,
    onSave: (String) -> Unit,
    onShare: (String) -> Unit,
    onUseAsInput: (Message) -> Unit,
    onReRun: (Message) -> Unit,
) {
    val c = LocalImagenColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        // User bubble
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                    .background(c.userBubble)
                    .padding(12.dp),
            ) {
                if (msg.inputImagePaths.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(msg.inputImagePaths) { path ->
                            AsyncImage(
                                model = ServiceLocator.imageStorage.absolute(path),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onTapImage(path) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (msg.prompt.isNotBlank()) {
                    Text(msg.prompt, color = c.userBubbleOn)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${msg.size} · ${msg.quality}",
                    color = c.userBubbleOn.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Assistant or error bubble
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            if (msg.status == "error") {
                Column(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.errorBg)
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = c.error)
                        Spacer(Modifier.width(6.dp))
                        Text("Generation failed", color = c.error, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(msg.error ?: "Unknown error", color = c.onSurface, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { onReRun(msg) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = c.accent)
                        Spacer(Modifier.width(4.dp))
                        Text("Retry", color = c.accent)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
                        .background(c.assistantBubble)
                        .padding(8.dp),
                ) {
                    msg.outputImagePath?.let { path ->
                        AsyncImage(
                            model = ServiceLocator.imageStorage.absolute(path),
                            contentDescription = "Generated image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onTapImage(path) },
                        )
                    }
                    if (!msg.revisedPrompt.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(msg.revisedPrompt, color = c.muted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Timer, contentDescription = null, tint = c.muted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Generated in ${DurationFormat.finalDuration(msg.durationMs)}",
                            color = c.muted, fontSize = 11.sp,
                        )
                    }
                    msg.outputImagePath?.let { path ->
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ActionChip(icon = Icons.Filled.Download, label = "Save") { onSave(path) }
                            ActionChip(icon = Icons.Filled.Share, label = "Share") { onShare(path) }
                            ActionChip(icon = Icons.Filled.AutoAwesome, label = "Use as input") { onUseAsInput(msg) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val c = LocalImagenColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(c.surface)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = c.onSurface, fontSize = 12.sp)
        }
    }
}
