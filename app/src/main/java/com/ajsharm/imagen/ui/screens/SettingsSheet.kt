package com.ajsharm.imagen.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ajsharm.imagen.data.SecureConfigStore
import com.ajsharm.imagen.ui.ThemeChoice
import com.ajsharm.imagen.ui.theme.LocalImagenColors

@Composable
fun SettingsContent(
    config: SecureConfigStore,
    currentTheme: ThemeChoice,
    onSave: (endpoint: String, key: String, deployment: String, version: String) -> Unit,
    onThemeChange: (ThemeChoice) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val c = LocalImagenColors.current
    var endpoint by remember { mutableStateOf(config.endpoint) }
    var apiKey by remember { mutableStateOf(config.apiKey) }
    var deployment by remember { mutableStateOf(config.deploymentName) }
    var version by remember { mutableStateOf(config.apiVersion) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Settings", color = c.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Text("Azure OpenAI", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = endpoint, onValueChange = { endpoint = it },
            label = { Text("Endpoint") },
            placeholder = { Text("https://YOUR_RESOURCE.cognitiveservices.azure.com/") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey, onValueChange = { apiKey = it },
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = deployment, onValueChange = { deployment = it },
            label = { Text("Deployment name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = version, onValueChange = { version = it },
            label = { Text("API version") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onSave(endpoint, apiKey, deployment, version) },
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.accentOn),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }

        Spacer(Modifier.height(20.dp))
        Text("Theme", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeChoice.values().forEach { t ->
                val selected = t == currentTheme
                TextButton(onClick = { onThemeChange(t) }) {
                    Text(
                        t.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = if (selected) c.accent else c.muted,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Data", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Backup") }
            Button(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import") }
        }
        Spacer(Modifier.height(24.dp))
    }
}
