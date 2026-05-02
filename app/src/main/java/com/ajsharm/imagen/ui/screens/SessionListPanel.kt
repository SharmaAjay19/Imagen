package com.ajsharm.imagen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ajsharm.imagen.ui.SessionSummary
import com.ajsharm.imagen.ui.theme.LocalImagenColors

@Composable
fun SessionListPanel(
    sessions: List<SessionSummary>,
    currentId: String?,
    onCreate: () -> Unit,
    onSelect: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (SessionSummary) -> Unit,
    onSettings: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val c = LocalImagenColors.current
    var renaming by remember { mutableStateOf<SessionSummary?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Imagen", color = c.onSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = c.muted)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.accent)
                .clickable { onCreate() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = c.accentOn)
                Spacer(Modifier.width(8.dp))
                Text("New session", color = c.accentOn, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            items(sessions, key = { it.id }) { s ->
                SessionRow(
                    s = s,
                    selected = s.id == currentId,
                    onClick = { onSelect(s.id) },
                    onRename = { renaming = s },
                    onDelete = { onDelete(s) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FooterChip(label = "Backup", icon = Icons.Filled.Upload, onClick = onExport, modifier = Modifier.weight(1f))
            FooterChip(label = "Import", icon = Icons.Filled.Download, onClick = onImport, modifier = Modifier.weight(1f))
        }
    }

    renaming?.let { s ->
        var text by remember(s.id) { mutableStateOf(s.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename session") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(s.id, text)
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SessionRow(
    s: SessionSummary,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalImagenColors.current
    val bg = if (selected) c.surfaceElevated else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(s.name, color = c.onSurface, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                "${s.messageCount} message${if (s.messageCount == 1) "" else "s"}",
                color = c.muted,
                fontSize = 12.sp,
            )
        }
        IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = "Rename", tint = c.muted)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = c.error)
        }
    }
}

@Composable
private fun FooterChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalImagenColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.surfaceElevated)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.onSurface)
        Spacer(Modifier.width(8.dp))
        Text(label, color = c.onSurface)
    }
}
