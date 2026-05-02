package com.ajsharm.imagen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ajsharm.imagen.ui.theme.LocalImagenColors

@Composable
fun EmptyState(onCreate: () -> Unit) {
    val c = LocalImagenColors.current
    Box(
        modifier = Modifier.fillMaxSize().background(c.background).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.AutoFixHigh,
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("Imagen", color = c.onBackground, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Generate and edit images with Azure GPT-Image-2.",
                color = c.muted,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onCreate,
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.accentOn),
            ) { Text("Create your first session") }
        }
    }
}
