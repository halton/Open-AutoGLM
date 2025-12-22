package com.openautoglm.agent.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.openautoglm.agent.R
import com.openautoglm.agent.data.AppDatabase
import com.openautoglm.agent.data.entities.Shipment
import com.openautoglm.agent.data.entities.ShipmentStatus
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Monitors shipment status changes and sends notifications.
 *
 * Features:
 * - Periodic background checks using WorkManager
 * - Smart notification grouping
 * - Priority-based alerts (out for delivery > status change)
 * - Configurable check intervals
 *
 * @param context Application context
 * @param logisticsTracker Tracker for shipment operations
 */
class ShipmentStatusMonitor(
    private val context: Context,
    private val logisticsTracker: LogisticsTracker
) {
    companion object {
        private const val TAG = "ShipmentStatusMonitor"
        private const val CHANNEL_ID = "shipment_updates"
        private const val CHANNEL_NAME = "Shipment Updates"
        private const val GROUP_KEY = "shipment_group"
        private const val WORK_NAME = "shipment_status_check"

        // Notification IDs
        private const val NOTIFICATION_ID_OUT_FOR_DELIVERY = 1001
        private const val NOTIFICATION_ID_DELIVERED = 1002
        private const val NOTIFICATION_ID_STATUS_CHANGE = 1003
        private const val NOTIFICATION_ID_SUMMARY = 1000

        // Check intervals
        private const val DEFAULT_CHECK_INTERVAL_MINUTES = 30L
        private const val URGENT_CHECK_INTERVAL_MINUTES = 15L
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    /**
     * Starts periodic status monitoring.
     *
     * @param intervalMinutes Interval between checks in minutes
     */
    fun startMonitoring(intervalMinutes: Long = DEFAULT_CHECK_INTERVAL_MINUTES) {
        Log.i(TAG, "Starting shipment monitoring with $intervalMinutes minute interval")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<ShipmentStatusWorker>(
            intervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    /**
     * Stops periodic monitoring.
     */
    fun stopMonitoring() {
        Log.i(TAG, "Stopping shipment monitoring")
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * Checks for status changes and triggers notifications.
     *
     * @return List of shipments with status changes
     */
    suspend fun checkForStatusChanges(): List<StatusChange> {
        Log.d(TAG, "Checking for shipment status changes")

        val changes = mutableListOf<StatusChange>()

        try {
            // Get current shipments from database
            val currentShipments = logisticsTracker.getAllActiveShipments().first()

            // Refresh stale shipments
            val updatedShipments = logisticsTracker.refreshStaleShipments()

            // Compare and detect changes
            for (updated in updatedShipments) {
                val current = currentShipments.find { it.id == updated.id }
                if (current != null && current.status != updated.status) {
                    val change = StatusChange(
                        shipment = updated,
                        previousStatus = current.status,
                        newStatus = updated.status
                    )
                    changes.add(change)
                    Log.i(TAG, "Status change detected: ${updated.trackingNumber} ${current.status} -> ${updated.status}")
                }
            }

            // Send notifications for changes
            if (changes.isNotEmpty()) {
                notifyStatusChanges(changes)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error checking status changes: ${e.message}")
        }

        return changes
    }

    /**
     * Sends notifications for status changes.
     */
    private fun notifyStatusChanges(changes: List<StatusChange>) {
        // Group by priority
        val outForDelivery = changes.filter { it.newStatus == ShipmentStatus.OUT_FOR_DELIVERY }
        val delivered = changes.filter { it.newStatus == ShipmentStatus.DELIVERED }
        val otherChanges = changes.filter {
            it.newStatus != ShipmentStatus.OUT_FOR_DELIVERY &&
            it.newStatus != ShipmentStatus.DELIVERED
        }

        // High priority: Out for delivery
        for (change in outForDelivery) {
            notifyOutForDelivery(change.shipment)
        }

        // Medium priority: Delivered
        for (change in delivered) {
            notifyDelivered(change.shipment)
        }

        // Low priority: Other status changes (grouped)
        if (otherChanges.isNotEmpty()) {
            notifyStatusUpdates(otherChanges)
        }

        // Summary notification if multiple changes
        if (changes.size > 1) {
            notifySummary(changes)
        }
    }

    /**
     * Sends notification for package out for delivery.
     */
    private fun notifyOutForDelivery(shipment: Shipment) {
        val title = getLocalizedString(
            "包裹即将送达",
            "Package Out for Delivery"
        )
        val content = "${shipment.itemDescription}\n${shipment.carrier}: ${shipment.lastLocation ?: ""}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(GROUP_KEY)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(
            NOTIFICATION_ID_OUT_FOR_DELIVERY + shipment.trackingNumber.hashCode(),
            notification
        )
    }

    /**
     * Sends notification for delivered package.
     */
    private fun notifyDelivered(shipment: Shipment) {
        val title = getLocalizedString(
            "包裹已送达",
            "Package Delivered"
        )
        val content = "${shipment.itemDescription}\n${shipment.carrier}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(GROUP_KEY)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(
            NOTIFICATION_ID_DELIVERED + shipment.trackingNumber.hashCode(),
            notification
        )
    }

    /**
     * Sends grouped notification for other status updates.
     */
    private fun notifyStatusUpdates(changes: List<StatusChange>) {
        val title = getLocalizedString(
            "${changes.size}个包裹状态更新",
            "${changes.size} Package Updates"
        )

        val content = changes.joinToString("\n") { change ->
            "${change.shipment.itemDescription}: ${getStatusDisplayName(change.newStatus)}"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(GROUP_KEY)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_STATUS_CHANGE, notification)
    }

    /**
     * Sends summary notification for multiple updates.
     */
    private fun notifySummary(changes: List<StatusChange>) {
        val title = getLocalizedString(
            "物流更新",
            "Shipping Updates"
        )

        val summaryText = getLocalizedString(
            "${changes.size}个包裹有更新",
            "${changes.size} packages have updates"
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(summaryText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_SUMMARY, notification)
    }

    /**
     * Creates the notification channel for Android O+.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for package shipping status updates"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Gets display name for status.
     */
    private fun getStatusDisplayName(status: ShipmentStatus): String {
        return when (status) {
            ShipmentStatus.PENDING -> getLocalizedString("待发货", "Pending")
            ShipmentStatus.PICKED_UP -> getLocalizedString("已揽收", "Picked Up")
            ShipmentStatus.IN_TRANSIT -> getLocalizedString("运输中", "In Transit")
            ShipmentStatus.OUT_FOR_DELIVERY -> getLocalizedString("派送中", "Out for Delivery")
            ShipmentStatus.DELIVERED -> getLocalizedString("已送达", "Delivered")
            ShipmentStatus.DELIVERY_FAILED -> getLocalizedString("派送失败", "Delivery Failed")
            ShipmentStatus.RETURNED -> getLocalizedString("已退回", "Returned")
            ShipmentStatus.CANCELLED -> getLocalizedString("已取消", "Cancelled")
            ShipmentStatus.UNKNOWN -> getLocalizedString("未知", "Unknown")
        }
    }

    /**
     * Returns localized string based on system language.
     */
    private fun getLocalizedString(chinese: String, english: String): String {
        val locale = context.resources.configuration.locales[0]
        return if (locale.language == "zh") chinese else english
    }

    /**
     * Represents a status change for a shipment.
     */
    data class StatusChange(
        val shipment: Shipment,
        val previousStatus: ShipmentStatus,
        val newStatus: ShipmentStatus
    )
}

/**
 * WorkManager worker for periodic status checks.
 */
class ShipmentStatusWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ShipmentStatusWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Running shipment status check")

        return try {
            val database = AppDatabase.getInstance(applicationContext)
            val shipmentDao = database.shipmentDao()

            // Create dependencies
            val agentRepository = com.openautoglm.agent.data.AgentRepository.getInstance(database)
            val inferenceRouter = com.openautoglm.agent.inference.InferenceRouterImpl(applicationContext, agentRepository)
            val shipmentExtractor = ShipmentExtractor(applicationContext, inferenceRouter)
            val authHandler = AuthenticationHandler(applicationContext)
            val logisticsTracker = LogisticsTracker(
                applicationContext,
                shipmentDao,
                shipmentExtractor,
                authHandler
            )

            val monitor = ShipmentStatusMonitor(applicationContext, logisticsTracker)
            val changes = monitor.checkForStatusChanges()

            Log.i(TAG, "Status check complete. ${changes.size} changes detected.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Status check failed: ${e.message}")
            Result.retry()
        }
    }
}
