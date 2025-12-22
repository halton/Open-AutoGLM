package com.openautoglm.agent.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Utility object for handling RECORD_AUDIO permission.
 */
object PermissionHandler {

    /**
     * Check current permission state for RECORD_AUDIO.
     *
     * @param context Application context
     * @param activity Activity for checking rationale (optional)
     * @return Current permission state
     */
    fun checkAudioPermissionState(context: Context, activity: Activity? = null): AudioPermissionState {
        val permissionStatus = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        )

        return when {
            permissionStatus == PackageManager.PERMISSION_GRANTED -> {
                AudioPermissionState.GRANTED
            }
            activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.RECORD_AUDIO
            ) -> {
                AudioPermissionState.DENIED_SHOW_RATIONALE
            }
            else -> {
                // Could be NOT_REQUESTED or PERMANENTLY_DENIED
                // We'll treat it as NOT_REQUESTED initially
                AudioPermissionState.NOT_REQUESTED
            }
        }
    }

    /**
     * Check if RECORD_AUDIO permission is granted.
     */
    fun isAudioPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Create intent to open app settings for manual permission grant.
     */
    fun createAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Open app settings for user to manually grant permission.
     */
    fun openAppSettings(context: Context) {
        context.startActivity(createAppSettingsIntent(context))
    }
}

/**
 * Composable helper for requesting RECORD_AUDIO permission.
 *
 * @param onPermissionResult Callback when permission result is received
 * @param onShowRationale Callback to show rationale dialog before re-requesting
 * @return Pair of (current state, request function)
 */
@Composable
fun rememberAudioPermissionState(
    onPermissionResult: (Boolean) -> Unit = {},
    onShowRationale: (() -> Unit)? = null
): Pair<AudioPermissionState, () -> Unit> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? Activity

    var permissionState by remember {
        mutableStateOf(PermissionHandler.checkAudioPermissionState(context, activity))
    }

    var hasRequestedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRequestedOnce = true
        if (isGranted) {
            permissionState = AudioPermissionState.GRANTED
            onPermissionResult(true)
        } else {
            // Check if we should show rationale (denied but can ask again)
            val newState = PermissionHandler.checkAudioPermissionState(context, activity)
            permissionState = if (newState == AudioPermissionState.NOT_REQUESTED && hasRequestedOnce) {
                // User denied and checked "Don't ask again"
                AudioPermissionState.PERMANENTLY_DENIED
            } else {
                newState
            }
            onPermissionResult(false)
        }
    }

    val requestPermission: () -> Unit = {
        when (permissionState) {
            AudioPermissionState.GRANTED -> {
                // Already granted
                onPermissionResult(true)
            }
            AudioPermissionState.DENIED_SHOW_RATIONALE -> {
                // Show rationale first, then request
                onShowRationale?.invoke() ?: permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            AudioPermissionState.PERMANENTLY_DENIED -> {
                // Need to open settings
                PermissionHandler.openAppSettings(context)
            }
            else -> {
                // Request permission
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Update state when resuming (user might have changed permission in settings)
    LaunchedEffect(Unit) {
        permissionState = PermissionHandler.checkAudioPermissionState(context, activity)
    }

    return permissionState to requestPermission
}

/**
 * Composable dialog for showing permission rationale.
 *
 * @param onDismiss Called when dialog is dismissed
 * @param onOpenSettings Called when user wants to open settings
 */
@Composable
fun PermissionRationaleDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            androidx.compose.material3.Text(
                text = PermissionRationale.getTitle(java.util.Locale.getDefault().language)
            )
        },
        text = {
            androidx.compose.material3.Text(
                text = PermissionRationale.getSettingsMessage(java.util.Locale.getDefault().language)
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onOpenSettings) {
                androidx.compose.material3.Text(
                    text = PermissionRationale.getOpenSettingsButton(java.util.Locale.getDefault().language)
                )
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text(
                    text = PermissionRationale.getCancelButton(java.util.Locale.getDefault().language)
                )
            }
        }
    )
}

/**
 * Rationale text for RECORD_AUDIO permission (bilingual).
 */
object PermissionRationale {
    fun getTitle(language: String): String {
        return if (language.startsWith("zh")) {
            "需要麦克风权限"
        } else {
            "Microphone Permission Required"
        }
    }

    fun getMessage(language: String): String {
        return if (language.startsWith("zh")) {
            "语音输入需要访问麦克风来将您的语音转换为文字。您的语音不会被存储或发送到任何服务器。"
        } else {
            "Voice input needs microphone access to convert your speech to text. Your voice is not stored or sent to any server."
        }
    }

    fun getConfirmButton(language: String): String {
        return if (language.startsWith("zh")) "允许" else "Allow"
    }

    fun getCancelButton(language: String): String {
        return if (language.startsWith("zh")) "取消" else "Cancel"
    }

    fun getSettingsMessage(language: String): String {
        return if (language.startsWith("zh")) {
            "麦克风权限已被拒绝。请在设置中手动开启权限以使用语音输入功能。"
        } else {
            "Microphone permission was denied. Please enable it in Settings to use voice input."
        }
    }

    fun getOpenSettingsButton(language: String): String {
        return if (language.startsWith("zh")) "打开设置" else "Open Settings"
    }
}
