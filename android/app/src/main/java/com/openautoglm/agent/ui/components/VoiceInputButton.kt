package com.openautoglm.agent.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openautoglm.agent.R
import com.openautoglm.agent.voice.AudioPermissionState
import com.openautoglm.agent.voice.VoiceInputState

/**
 * Microphone button for voice input with state-based appearance.
 *
 * @param voiceState Current voice input state
 * @param permissionState Current audio permission state
 * @param onClick Called when button is clicked
 * @param onLongClick Called when button is long-pressed (optional cancel action)
 * @param enabled Whether the button is enabled
 * @param modifier Modifier for the button
 */
@Composable
fun VoiceInputButton(
    voiceState: VoiceInputState,
    permissionState: AudioPermissionState,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isListening = voiceState is VoiceInputState.Listening
    val isProcessing = voiceState is VoiceInputState.Processing
    val hasError = voiceState is VoiceInputState.Error
    val permissionDenied = permissionState == AudioPermissionState.PERMANENTLY_DENIED

    // Pulsing animation for listening state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Background color animation
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isListening -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            isProcessing -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
            hasError -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
            else -> Color.Transparent
        },
        animationSpec = tween(300),
        label = "backgroundColor"
    )

    // Icon color
    val iconColor by animateColorAsState(
        targetValue = when {
            !enabled || permissionDenied -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            isListening -> MaterialTheme.colorScheme.primary
            isProcessing -> MaterialTheme.colorScheme.secondary
            hasError -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(300),
        label = "iconColor"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(enabled = enabled && !isProcessing) {
                if (isListening) {
                    onLongClick?.invoke() ?: onClick()
                } else {
                    onClick()
                }
            }
            .then(
                if (isListening) Modifier.scale(pulseScale) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            isProcessing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = iconColor,
                    strokeWidth = 2.dp
                )
            }
            else -> {
                Icon(
                    imageVector = if (permissionDenied) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = when {
                        isListening -> stringResource(R.string.voice_stop_listening)
                        permissionDenied -> stringResource(R.string.voice_permission_denied)
                        else -> stringResource(R.string.voice_start_listening)
                    },
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Compact voice input button for use as trailing icon in text fields.
 */
@Composable
fun VoiceInputIconButton(
    voiceState: VoiceInputState,
    permissionState: AudioPermissionState,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isListening = voiceState is VoiceInputState.Listening
    val isProcessing = voiceState is VoiceInputState.Processing
    val permissionDenied = permissionState == AudioPermissionState.PERMANENTLY_DENIED

    // Pulsing animation for listening state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val iconColor = when {
        !enabled || permissionDenied -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isListening -> MaterialTheme.colorScheme.primary
        isProcessing -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(
        onClick = onClick,
        enabled = enabled && !isProcessing,
        modifier = modifier.scale(if (isListening) pulseScale else 1f)
    ) {
        when {
            isProcessing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = iconColor,
                    strokeWidth = 2.dp
                )
            }
            else -> {
                Icon(
                    imageVector = if (permissionDenied) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = when {
                        isListening -> stringResource(R.string.voice_stop_listening)
                        permissionDenied -> stringResource(R.string.voice_permission_denied)
                        else -> stringResource(R.string.voice_start_listening)
                    },
                    tint = iconColor
                )
            }
        }
    }
}

/**
 * Voice input indicator showing listening status with sound level visualization.
 */
@Composable
fun VoiceListeningIndicator(
    listeningState: VoiceInputState.Listening,
    modifier: Modifier = Modifier
) {
    val soundLevel = listeningState.soundLevel

    // Animate based on sound level
    val infiniteTransition = rememberInfiniteTransition(label = "soundPulse")
    val basePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f + (soundLevel * 0.3f),
        animationSpec = infiniteRepeatable(
            animation = tween((200 / (1 + soundLevel)).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "basePulse"
    )

    Box(
        modifier = modifier
            .size(64.dp)
            .scale(basePulse)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = stringResource(R.string.voice_listening),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
    }
}
