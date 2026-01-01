package com.openautoglm.agent.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openautoglm.agent.data.entities.InferenceMode
import com.openautoglm.agent.inference.InferenceProvider
import com.openautoglm.agent.ui.viewmodels.SettingsViewModel
import com.openautoglm.agent.voice.SpeechRecognizerType
import com.openautoglm.agent.voice.VoskModelDownloadState
import com.openautoglm.agent.voice.VoskModelInfo

/**
 * Screen for app settings.
 *
 * Allows configuration of API keys, model selection, and inference provider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onNavigateToModelDownload: () -> Unit = {}
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
            // Show different message based on inference mode
            val warningMessage = when (uiState.inferenceMode) {
                InferenceMode.ON_DEVICE -> "On-device model required. Download a model below."
                InferenceMode.CLOUD -> "API key required to use the agent"
                InferenceMode.AUTO -> "API key or on-device model required"
            }
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
                        text = warningMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Divider()

        // Inference Mode Selection
        Text(
            text = "Inference Mode / 推理模式",
            style = MaterialTheme.typography.titleMedium
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                InferenceMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.inferenceMode == mode,
                            onClick = { viewModel.updateInferenceMode(mode) }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = when (mode) {
                                    InferenceMode.CLOUD -> "Cloud / 云端"
                                    InferenceMode.ON_DEVICE -> "On-Device / 本地"
                                    InferenceMode.AUTO -> "Auto / 自动"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (uiState.inferenceMode == mode) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = when (mode) {
                                    InferenceMode.CLOUD -> "Use cloud APIs (requires API key)"
                                    InferenceMode.ON_DEVICE -> "Use downloaded models (offline capable)"
                                    InferenceMode.AUTO -> "Auto-select based on availability"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // On-Device Model Selection (shown when On-Device or Auto mode)
        if (uiState.inferenceMode != InferenceMode.CLOUD) {
            if (uiState.downloadedModels.isNotEmpty()) {
                Text(
                    text = "Select On-Device Model / 选择本地模型",
                    style = MaterialTheme.typography.titleSmall
                )

                var modelExpanded by remember { mutableStateOf(false) }
                val selectedModel = uiState.downloadedModels.find { it.id == uiState.selectedOnDeviceModel }
                    ?: uiState.downloadedModels.firstOrNull()

                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = !modelExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedModel?.displayName ?: "Select a model",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Model") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) }
                    )

                    ExposedDropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        uiState.downloadedModels.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(model.displayName)
                                        Text(
                                            text = "${model.sizeMB} MB",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.updateSelectedOnDeviceModel(model.id)
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "No models downloaded",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Download a model from 'Manage On-Device Models' to use on-device inference.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onNavigateToModelDownload,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Download Models")
                        }
                    }
                }
            }
        }

        Divider()

        // Cloud Provider Selection (shown when Cloud or Auto mode)
        if (uiState.inferenceMode != InferenceMode.ON_DEVICE) {
            Text(
                text = "Cloud Provider / 云端服务商",
                style = MaterialTheme.typography.titleMedium
            )

            InferenceProvider.entries.forEach { provider ->
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
        } // End of cloud provider section

        Divider()

        // On-Device Models Section
        Text(
            text = "On-Device Models",
            style = MaterialTheme.typography.titleMedium
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Run AI models locally on your device for offline use and enhanced privacy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onNavigateToModelDownload,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage On-Device Models")
                }
            }
        }

        Divider()

        // HuggingFace Token Section (for downloading gated models like Gemma)
        Text(
            text = "HuggingFace Token / HuggingFace 令牌",
            style = MaterialTheme.typography.titleMedium
        )

        val context = LocalContext.current

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Required for downloading Gemma models (gated on HuggingFace).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "下载 Gemma 模型需要此令牌（在 HuggingFace 上需要授权）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Step 1: Accept License Button
                Text(
                    text = "Step 1: Accept Gemma License / 第一步：接受许可证",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/litert-community/Gemma3-1B-IT"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Gemma License Page")
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Step 2: Create Token Button
                Text(
                    text = "Step 2: Create Token / 第二步：创建令牌",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/settings/tokens"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Token Settings (Read scope)")
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Step 3: Enter Token
                Text(
                    text = "Step 3: Enter Token Below / 第三步：在下方输入令牌",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                var showHfToken by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = uiState.huggingFaceToken,
                    onValueChange = { viewModel.updateHuggingFaceToken(it) },
                    label = { Text("HuggingFace Token") },
                    placeholder = { Text("hf_...") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showHfToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showHfToken = !showHfToken }) {
                            Text(if (showHfToken) "Hide" else "Show")
                        }
                    },
                    singleLine = true
                )
            }
        }

        Divider()

        // Voice Recognition Settings Section
        VoiceRecognitionSettings(
            viewModel = viewModel,
            uiState = uiState
        )

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

/**
 * Voice Recognition Settings section.
 */
@Composable
private fun VoiceRecognitionSettings(
    viewModel: SettingsViewModel,
    uiState: com.openautoglm.agent.ui.viewmodels.SettingsUiState
) {
    val voskDownloadState by viewModel.voskDownloadState.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Voice Recognition / 语音识别",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "Choose between online (Google) or offline (Vosk) speech recognition.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Recognizer Type Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                SpeechRecognizerType.entries.forEach { type ->
                    val isEnabled = when (type) {
                        SpeechRecognizerType.ANDROID_BUILTIN -> true
                        SpeechRecognizerType.VOSK_OFFLINE -> uiState.downloadedVoskModels.isNotEmpty()
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.speechRecognizerType == type,
                            onClick = {
                                if (isEnabled) {
                                    viewModel.updateSpeechRecognizerType(type)
                                }
                            },
                            enabled = isEnabled
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = when (type) {
                                    SpeechRecognizerType.ANDROID_BUILTIN -> "Google (Online)"
                                    SpeechRecognizerType.VOSK_OFFLINE -> "Vosk (Offline)"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (uiState.speechRecognizerType == type) FontWeight.Bold else FontWeight.Normal,
                                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Text(
                                text = when (type) {
                                    SpeechRecognizerType.ANDROID_BUILTIN -> "Uses Google Speech Services (requires internet)"
                                    SpeechRecognizerType.VOSK_OFFLINE -> if (isEnabled) "Fully on-device, privacy-preserving" else "Download a model below to enable"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Vosk Models Section
        Text(
            text = "Vosk Models / 离线语音模型",
            style = MaterialTheme.typography.titleSmall
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                uiState.availableVoskModels.forEach { model ->
                    val isDownloaded = uiState.downloadedVoskModels.any { it.id == model.id }

                    VoskModelItem(
                        model = model,
                        isDownloaded = isDownloaded,
                        downloadState = voskDownloadState,
                        onDownload = { viewModel.downloadVoskModel(model) },
                        onDelete = { viewModel.deleteVoskModel(model) }
                    )

                    if (model != uiState.availableVoskModels.last()) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

/**
 * Individual Vosk model item with download/delete controls.
 */
@Composable
private fun VoskModelItem(
    model: VoskModelInfo,
    isDownloaded: Boolean,
    downloadState: VoskModelDownloadState,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${model.sizeMB} MB - ${model.description}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Show download progress
            when (downloadState) {
                is VoskModelDownloadState.Downloading -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { downloadState.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "%.1f / %.1f MB".format(downloadState.downloadedMB, downloadState.totalMB),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is VoskModelDownloadState.Extracting -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Extracting...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is VoskModelDownloadState.Error -> {
                    Text(
                        text = "Error: ${downloadState.message}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {}
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isDownloaded) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete model",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else {
            val isDownloading = downloadState is VoskModelDownloadState.Downloading ||
                downloadState is VoskModelDownloadState.Extracting

            IconButton(
                onClick = onDownload,
                enabled = !isDownloading
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download model",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
