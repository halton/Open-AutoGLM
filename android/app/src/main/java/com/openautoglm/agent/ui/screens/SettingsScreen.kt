package com.openautoglm.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openautoglm.agent.inference.InferenceProvider
import com.openautoglm.agent.ui.viewmodels.SettingsViewModel

/**
 * Screen for app settings.
 *
 * Allows configuration of API keys, model selection, and inference provider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showApiKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // Configuration Status
        if (uiState.isConfigured) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Configuration complete",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "API key required to use the agent",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Divider()

        // Provider Selection
        Text(
            text = "Inference Provider",
            style = MaterialTheme.typography.titleMedium
        )

        InferenceProvider.values().forEach { provider ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = uiState.selectedProvider == provider,
                    onClick = { viewModel.selectProvider(provider) }
                )
                Text(
                    text = when (provider) {
                        InferenceProvider.BIGMODEL -> "BigModel (AutoGLM-Phone)"
                        InferenceProvider.DASHSCOPE -> "DashScope (Qwen2.5-VL-72B)"
                        InferenceProvider.OPENAI -> "OpenAI (GPT-4 Vision)"
                        InferenceProvider.CUSTOM -> "Custom API"
                    },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Divider()

        // API Key Configuration based on selected provider
        when (uiState.selectedProvider) {
            InferenceProvider.BIGMODEL -> {
                BigModelSettings(
                    apiKey = uiState.bigModelApiKey,
                    modelId = uiState.bigModelModelId,
                    showApiKey = showApiKey,
                    onApiKeyChange = { viewModel.updateBigModelApiKey(it) },
                    onModelIdChange = { viewModel.updateBigModelModelId(it) },
                    onToggleVisibility = { showApiKey = !showApiKey }
                )
            }
            InferenceProvider.DASHSCOPE -> {
                DashScopeSettings(
                    apiKey = uiState.dashScopeApiKey,
                    modelId = uiState.dashScopeModelId,
                    showApiKey = showApiKey,
                    onApiKeyChange = { viewModel.updateDashScopeApiKey(it) },
                    onModelIdChange = { viewModel.updateDashScopeModelId(it) },
                    onToggleVisibility = { showApiKey = !showApiKey }
                )
            }
            InferenceProvider.OPENAI -> {
                OpenAISettings(
                    apiKey = uiState.openAiApiKey,
                    showApiKey = showApiKey,
                    onApiKeyChange = { viewModel.updateOpenAiApiKey(it) },
                    onToggleVisibility = { showApiKey = !showApiKey }
                )
            }
            InferenceProvider.CUSTOM -> {
                CustomApiSettings(
                    apiKey = uiState.customApiKey,
                    baseUrl = uiState.customBaseUrl,
                    showApiKey = showApiKey,
                    onApiKeyChange = { viewModel.updateCustomApiKey(it) },
                    onBaseUrlChange = { viewModel.updateCustomBaseUrl(it) },
                    onToggleVisibility = { showApiKey = !showApiKey }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Save Button
        Button(
            onClick = { viewModel.saveSettings() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSaving
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (uiState.isSaving) "Saving..." else "Save Configuration")
        }

        // Success/Error Messages
        if (uiState.saveSuccess) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "✓ Settings saved successfully",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        uiState.saveError?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Error: $error",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            }
        }

        // Info card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Getting Started",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "1. Select your preferred inference provider\n" +
                           "2. Enter your API key (get it from the provider's website)\n" +
                           "3. Choose a model (or use the default)\n" +
                           "4. Click 'Save Configuration'\n" +
                           "5. Return to Tasks and try automating!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BigModelSettings(
    apiKey: String,
    modelId: String,
    showApiKey: Boolean,
    onApiKeyChange: (String) -> Unit,
    onModelIdChange: (String) -> Unit,
    onToggleVisibility: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "BigModel Configuration",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key") },
            placeholder = { Text("Enter your BigModel API key") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = onToggleVisibility) {
                    Text(if (showApiKey) "Hide" else "Show")
                }
            }
        )

        var expanded by remember { mutableStateOf(false) }
        val models = listOf("AutoGLM-Phone", "AutoGLM-Phone-Multilingual", "glm-4v", "glm-4v-plus")

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = modelId,
                onValueChange = {},
                readOnly = true,
                label = { Text("Model") },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            onModelIdChange(model)
                            expanded = false
                        }
                    )
                }
            }
        }

        Text(
            text = "Get your API key from: https://open.bigmodel.cn/",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashScopeSettings(
    apiKey: String,
    modelId: String,
    showApiKey: Boolean,
    onApiKeyChange: (String) -> Unit,
    onModelIdChange: (String) -> Unit,
    onToggleVisibility: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "DashScope Configuration",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key") },
            placeholder = { Text("sk-...") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = onToggleVisibility) {
                    Text(if (showApiKey) "Hide" else "Show")
                }
            }
        )

        var expanded by remember { mutableStateOf(false) }
        val models = listOf(
            "qwen2.5-vl-72b-instruct",
            "qwen2.5-vl-7b-instruct",
            "qwen2.5-vl-3b-instruct",
            "qwen-vl-max",
            "qwen-vl-plus"
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = modelId,
                onValueChange = {},
                readOnly = true,
                label = { Text("Model") },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            onModelIdChange(model)
                            expanded = false
                        }
                    )
                }
            }
        }

        Text(
            text = "Get your API key from: https://dashscope.console.aliyun.com/",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OpenAISettings(
    apiKey: String,
    showApiKey: Boolean,
    onApiKeyChange: (String) -> Unit,
    onToggleVisibility: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "OpenAI Configuration",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key") },
            placeholder = { Text("sk-...") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = onToggleVisibility) {
                    Text(if (showApiKey) "Hide" else "Show")
                }
            }
        )

        Text(
            text = "Model: GPT-4 Vision Preview",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "Get your API key from: https://platform.openai.com/api-keys",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CustomApiSettings(
    apiKey: String,
    baseUrl: String,
    showApiKey: Boolean,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onToggleVisibility: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Custom API Configuration",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text("Base URL") },
            placeholder = { Text("https://your-api.com/v1") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key") },
            placeholder = { Text("Enter your API key") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = onToggleVisibility) {
                    Text(if (showApiKey) "Hide" else "Show")
                }
            }
        )

        Text(
            text = "Use for self-hosted or alternative OpenAI-compatible APIs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
