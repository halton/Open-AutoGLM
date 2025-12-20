package com.openautoglm.agent.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Represents a shipment/package being tracked across shopping apps.
 *
 * Supports tracking from multiple platforms:
 * - Chinese: 淘宝, 京东, 拼多多, 天猫
 * - International: Amazon, eBay, AliExpress
 * - Logistics: 顺丰, 中通, 圆通, 韵达, etc.
 */
@Entity(tableName = "shipments")
@TypeConverters(ShipmentConverters::class)
data class Shipment(
    @PrimaryKey
    val id: UUID = UUID.randomUUID(),

    /** Tracking number from the carrier */
    val trackingNumber: String,

    /** Name of the carrier/logistics company */
    val carrier: String,

    /** Source app where the order was placed */
    val sourceApp: String,

    /** Package name of the source app */
    val sourceAppPackage: String,

    /** Order ID from the source platform */
    val orderId: String? = null,

    /** Product/item description */
    val itemDescription: String,

    /** Current shipment status */
    val status: ShipmentStatus = ShipmentStatus.PENDING,

    /** Detailed status message */
    val statusMessage: String? = null,

    /** Sender information */
    val senderName: String? = null,
    val senderAddress: String? = null,

    /** Recipient information */
    val recipientName: String? = null,
    val recipientAddress: String? = null,

    /** Estimated delivery date (epoch millis) */
    val estimatedDeliveryDate: Long? = null,

    /** Actual delivery date (epoch millis) */
    val actualDeliveryDate: Long? = null,

    /** Last location update */
    val lastLocation: String? = null,

    /** Timestamp of last status update */
    val lastUpdateTime: Long = System.currentTimeMillis(),

    /** When this shipment was created in our system */
    val createdAt: Long = System.currentTimeMillis(),

    /** Tracking history events */
    val trackingHistory: List<TrackingEvent> = emptyList(),

    /** Whether this shipment has been archived */
    val isArchived: Boolean = false,

    /** User notes */
    val notes: String? = null,

    /** Product image URL if available */
    val productImageUrl: String? = null,

    /** Order total amount */
    val orderAmount: String? = null
)

/**
 * Status of a shipment.
 */
enum class ShipmentStatus {
    /** Order placed, not yet shipped */
    PENDING,

    /** Package picked up by carrier */
    PICKED_UP,

    /** In transit */
    IN_TRANSIT,

    /** Out for delivery */
    OUT_FOR_DELIVERY,

    /** Delivered successfully */
    DELIVERED,

    /** Delivery failed (e.g., recipient not home) */
    DELIVERY_FAILED,

    /** Returned to sender */
    RETURNED,

    /** Shipment cancelled */
    CANCELLED,

    /** Status unknown or error fetching */
    UNKNOWN
}

/**
 * A single tracking event in the shipment history.
 */
@Serializable
data class TrackingEvent(
    /** Timestamp of the event */
    val timestamp: Long,

    /** Location where the event occurred */
    val location: String?,

    /** Description of the event */
    val description: String,

    /** Status at this point */
    val status: String
)

/**
 * Type converters for Room database.
 */
class ShipmentConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromTrackingEventList(events: List<TrackingEvent>): String {
        return json.encodeToString(events)
    }

    @TypeConverter
    fun toTrackingEventList(value: String): List<TrackingEvent> {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromShipmentStatus(status: ShipmentStatus): String {
        return status.name
    }

    @TypeConverter
    fun toShipmentStatus(value: String): ShipmentStatus {
        return try {
            ShipmentStatus.valueOf(value)
        } catch (e: Exception) {
            ShipmentStatus.UNKNOWN
        }
    }

    @TypeConverter
    fun fromUUID(uuid: UUID): String {
        return uuid.toString()
    }

    @TypeConverter
    fun toUUID(value: String): UUID {
        return UUID.fromString(value)
    }
}

/**
 * Summary statistics for shipments.
 */
data class ShipmentSummary(
    val totalActive: Int,
    val inTransit: Int,
    val outForDelivery: Int,
    val delivered: Int,
    val pending: Int
)

/**
 * Filter options for shipment list.
 */
enum class ShipmentFilter {
    ALL,
    ACTIVE,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    PENDING,
    ARCHIVED
}

/**
 * Sort options for shipment list.
 */
enum class ShipmentSort {
    LAST_UPDATE,
    ESTIMATED_DELIVERY,
    CREATED_DATE,
    CARRIER,
    STATUS
}
