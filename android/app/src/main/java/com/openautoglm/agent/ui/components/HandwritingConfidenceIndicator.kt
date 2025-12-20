package com.openautoglm.agent.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A visual indicator showing the confidence level of text extraction
 * and whether handwriting was detected.
 *
 * The indicator uses colors to represent confidence levels:
 * - Green: High confidence (>= 0.8)
 * - Orange/Yellow: Medium confidence (0.5 - 0.8)
 * - Red: Low confidence (< 0.5)
 *
 * An icon indicates whether the text was typed or handwritten.
 *
 * @param confidence The confidence score from 0.0 to 1.0
 * @param hasHandwriting Whether handwritten text was detected
 * @param modifier Optional modifier for the component
 */
@Composable
fun HandwritingConfidenceIndicator(
    confidence: Float,
    hasHandwriting: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedConfidence by animateFloatAsState(
        targetValue = confidence,
        animationSpec = tween(500),
        label = "confidence"
    )

    val confidenceColor by animateColorAsState(
        targetValue = getConfidenceColor(confidence),
        animationSpec = tween(500),
        label = "color"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Type icon (handwriting or typed)
            Icon(
                imageVector = if (hasHandwriting) Icons.Default.Draw else Icons.Default.TextFields,
                contentDescription = if (hasHandwriting) "手写 / Handwritten" else "打印 / Typed",
                tint = if (hasHandwriting) MaterialTheme.colorScheme.tertiary
                       else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )

            // Confidence indicator dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(confidenceColor)
            )

            // Confidence percentage
            Text(
                text = "${(animatedConfidence * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = confidenceColor
            )
        }
    }
}

/**
 * An expanded version of the confidence indicator with more details.
 *
 * Shows:
 * - Text type (handwritten/typed)
 * - Confidence bar
 * - Confidence percentage
 * - Confidence level label
 *
 * @param confidence The confidence score from 0.0 to 1.0
 * @param hasHandwriting Whether handwritten text was detected
 * @param modifier Optional modifier for the component
 */
@Composable
fun HandwritingConfidenceIndicatorExpanded(
    confidence: Float,
    hasHandwriting: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedConfidence by animateFloatAsState(
        targetValue = confidence,
        animationSpec = tween(500),
        label = "confidence"
    )

    val confidenceColor by animateColorAsState(
        targetValue = getConfidenceColor(confidence),
        animationSpec = tween(500),
        label = "color"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header with icon and label
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasHandwriting) Icons.Default.Draw else Icons.Default.TextFields,
                    contentDescription = null,
                    tint = if (hasHandwriting) MaterialTheme.colorScheme.tertiary
                           else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasHandwriting) "手写文字 / Handwritten" else "打印文字 / Typed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Confidence label
            Row(
                modifier = Modifier.padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "识别置信度 / Confidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = getConfidenceLabel(confidence),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = confidenceColor
                    )
                    Text(
                        text = "(${(animatedConfidence * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Confidence bar
            LinearProgressIndicator(
                progress = animatedConfidence,
                modifier = Modifier
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = confidenceColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

/**
 * A minimal confidence badge showing just the percentage.
 *
 * @param confidence The confidence score from 0.0 to 1.0
 * @param modifier Optional modifier for the component
 */
@Composable
fun ConfidenceBadge(
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val confidenceColor = getConfidenceColor(confidence)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = confidenceColor.copy(alpha = 0.2f)
    ) {
        Text(
            text = "${(confidence * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = confidenceColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// Helper functions
private fun getConfidenceColor(confidence: Float): Color {
    return when {
        confidence >= 0.8f -> Color(0xFF4CAF50) // Green
        confidence >= 0.5f -> Color(0xFFFFA000) // Orange/Amber
        else -> Color(0xFFF44336) // Red
    }
}

private fun getConfidenceLabel(confidence: Float): String {
    return when {
        confidence >= 0.9f -> "很高 / Very High"
        confidence >= 0.8f -> "高 / High"
        confidence >= 0.6f -> "中等 / Medium"
        confidence >= 0.4f -> "较低 / Low"
        else -> "很低 / Very Low"
    }
}

// Previews
@Preview(showBackground = true)
@Composable
private fun HandwritingConfidenceIndicatorPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // High confidence, typed
            HandwritingConfidenceIndicator(
                confidence = 0.95f,
                hasHandwriting = false
            )

            // Medium confidence, handwritten
            HandwritingConfidenceIndicator(
                confidence = 0.65f,
                hasHandwriting = true
            )

            // Low confidence, handwritten
            HandwritingConfidenceIndicator(
                confidence = 0.35f,
                hasHandwriting = true
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HandwritingConfidenceIndicatorExpandedPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HandwritingConfidenceIndicatorExpanded(
                confidence = 0.85f,
                hasHandwriting = false
            )

            HandwritingConfidenceIndicatorExpanded(
                confidence = 0.55f,
                hasHandwriting = true
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConfidenceBadgePreview() {
    MaterialTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConfidenceBadge(confidence = 0.95f)
            ConfidenceBadge(confidence = 0.65f)
            ConfidenceBadge(confidence = 0.35f)
        }
    }
}
