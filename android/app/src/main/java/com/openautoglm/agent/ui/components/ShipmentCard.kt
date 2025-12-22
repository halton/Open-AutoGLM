package com.openautoglm.agent.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openautoglm.agent.data.entities.Shipment
import com.openautoglm.agent.data.entities.ShipmentStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Card component for displaying a single shipment.
 *
 * @param shipment The shipment to display
 * @param onClick Callback when card is clicked
 * @param modifier Modifier for the card
 */
@Composable
fun ShipmentCard(
    shipment: Shipment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = getStatusColor(shipment.status),
        label = "statusColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            StatusIcon(
                status = shipment.status,
                statusColor = statusColor
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Shipment details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Item description
                Text(
                    text = shipment.itemDescription,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Carrier and source app
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = shipment.carrier,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = shipment.sourceApp,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Status and location
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status chip
                    StatusChip(
                        status = shipment.status,
                        statusColor = statusColor
                    )

                    // Last update time
                    Text(
                        text = formatRelativeTime(shipment.lastUpdateTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Location if available
                shipment.lastLocation?.let { location ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Status icon with colored background.
 */
@Composable
private fun StatusIcon(
    status: ShipmentStatus,
    statusColor: Color
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(statusColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = getStatusIcon(status),
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Status chip component.
 */
@Composable
private fun StatusChip(
    status: ShipmentStatus,
    statusColor: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = statusColor.copy(alpha = 0.15f)
    ) {
        Text(
            text = getStatusDisplayName(status),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Compact version of the shipment card.
 */
@Composable
fun ShipmentCardCompact(
    shipment: Shipment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = getStatusColor(shipment.status)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Description
            Text(
                text = shipment.itemDescription,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Status text
            Text(
                text = getStatusDisplayName(shipment.status),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Gets the color for a shipment status.
 */
private fun getStatusColor(status: ShipmentStatus): Color {
    return when (status) {
        ShipmentStatus.PENDING -> Color(0xFF9E9E9E)       // Gray
        ShipmentStatus.PICKED_UP -> Color(0xFF2196F3)    // Blue
        ShipmentStatus.IN_TRANSIT -> Color(0xFFFF9800)   // Orange
        ShipmentStatus.OUT_FOR_DELIVERY -> Color(0xFF4CAF50) // Green
        ShipmentStatus.DELIVERED -> Color(0xFF4CAF50)    // Green
        ShipmentStatus.DELIVERY_FAILED -> Color(0xFFF44336) // Red
        ShipmentStatus.RETURNED -> Color(0xFFFF5722)     // Deep Orange
        ShipmentStatus.CANCELLED -> Color(0xFF9E9E9E)    // Gray
        ShipmentStatus.UNKNOWN -> Color(0xFF9E9E9E)      // Gray
    }
}

/**
 * Gets the icon for a shipment status.
 */
private fun getStatusIcon(status: ShipmentStatus): ImageVector {
    return when (status) {
        ShipmentStatus.PENDING -> Icons.Default.Schedule
        ShipmentStatus.PICKED_UP -> Icons.Default.LocalShipping
        ShipmentStatus.IN_TRANSIT -> Icons.Default.LocalShipping
        ShipmentStatus.OUT_FOR_DELIVERY -> Icons.Default.LocalShipping
        ShipmentStatus.DELIVERED -> Icons.Default.Check
        ShipmentStatus.DELIVERY_FAILED -> Icons.Default.Warning
        ShipmentStatus.RETURNED -> Icons.Default.Warning
        ShipmentStatus.CANCELLED -> Icons.Default.Warning
        ShipmentStatus.UNKNOWN -> Icons.Default.Schedule
    }
}

/**
 * Gets display name for status.
 */
private fun getStatusDisplayName(status: ShipmentStatus): String {
    return when (status) {
        ShipmentStatus.PENDING -> "待发货"
        ShipmentStatus.PICKED_UP -> "已揽收"
        ShipmentStatus.IN_TRANSIT -> "运输中"
        ShipmentStatus.OUT_FOR_DELIVERY -> "派送中"
        ShipmentStatus.DELIVERED -> "已送达"
        ShipmentStatus.DELIVERY_FAILED -> "派送失败"
        ShipmentStatus.RETURNED -> "已退回"
        ShipmentStatus.CANCELLED -> "已取消"
        ShipmentStatus.UNKNOWN -> "未知"
    }
}

/**
 * Formats a timestamp as relative time (e.g., "5 minutes ago").
 */
private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60 * 1000 -> "刚刚"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
        diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}天前"
        else -> {
            val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
