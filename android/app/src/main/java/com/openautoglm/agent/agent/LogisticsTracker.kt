package com.openautoglm.agent.agent

import android.content.Context
import android.util.Log
import com.openautoglm.agent.data.dao.ShipmentDao
import com.openautoglm.agent.data.entities.Shipment
import com.openautoglm.agent.data.entities.ShipmentFilter
import com.openautoglm.agent.data.entities.ShipmentSort
import com.openautoglm.agent.data.entities.ShipmentStatus
import com.openautoglm.agent.data.entities.ShipmentSummary
import com.openautoglm.agent.knowledge.AppKnowledgeBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Orchestrates multi-app logistics tracking across shopping platforms.
 *
 * Features:
 * - Query shipments across multiple apps (淘宝, 京东, 拼多多, Amazon, etc.)
 * - Consolidate tracking information into unified view
 * - Detect status changes and notify user
 * - Handle app re-authentication when needed
 *
 * @param context Application context
 * @param shipmentDao DAO for shipment database operations
 * @param shipmentExtractor Extractor for parsing shipment data from screens
 * @param authHandler Handler for re-authentication scenarios
 */
class LogisticsTracker(
    private val context: Context,
    private val shipmentDao: ShipmentDao,
    private val shipmentExtractor: ShipmentExtractor,
    private val authHandler: AuthenticationHandler
) {
    companion object {
        private const val TAG = "LogisticsTracker"

        // Stale threshold: shipments not updated in 6 hours
        private const val STALE_THRESHOLD_MS = 6 * 60 * 60 * 1000L

        // Supported shopping apps for logistics tracking
        val SUPPORTED_APPS = mapOf(
            // Chinese apps
            "com.taobao.taobao" to "淘宝",
            "com.jingdong.app.mall" to "京东",
            "com.xunmeng.pinduoduo" to "拼多多",
            "com.tmall.wireless" to "天猫",
            "com.alibaba.aliexpresshd" to "速卖通",

            // International apps
            "com.amazon.mShop.android.shopping" to "Amazon",
            "com.ebay.mobile" to "eBay",
            "com.shopee.ph" to "Shopee",

            // Logistics apps (direct tracking)
            "com.sf.activity" to "顺丰速运",
            "com.zto.zto" to "中通快递",
            "com.yto.sto" to "圆通速递",
            "com.yunda.app" to "韵达快递",
            "com.ems.ems" to "中国邮政"
        )
    }

    /**
     * Gets all active shipments.
     */
    fun getAllActiveShipments(): Flow<List<Shipment>> {
        return shipmentDao.getAllActive()
    }

    /**
     * Gets shipments filtered by status.
     */
    fun getShipmentsByFilter(filter: ShipmentFilter): Flow<List<Shipment>> {
        return when (filter) {
            ShipmentFilter.ALL -> shipmentDao.getAll()
            ShipmentFilter.ACTIVE -> shipmentDao.getAllActive()
            ShipmentFilter.IN_TRANSIT -> shipmentDao.getInTransit()
            ShipmentFilter.OUT_FOR_DELIVERY -> shipmentDao.getByStatus(ShipmentStatus.OUT_FOR_DELIVERY)
            ShipmentFilter.DELIVERED -> shipmentDao.getByStatus(ShipmentStatus.DELIVERED)
            ShipmentFilter.PENDING -> shipmentDao.getByStatus(ShipmentStatus.PENDING)
            ShipmentFilter.ARCHIVED -> shipmentDao.getArchived()
        }
    }

    /**
     * Gets shipments sorted by specified criteria.
     */
    fun getShipmentsSorted(sort: ShipmentSort): Flow<List<Shipment>> {
        return shipmentDao.getAllActive().map { shipments ->
            when (sort) {
                ShipmentSort.LAST_UPDATE -> shipments.sortedByDescending { it.lastUpdateTime }
                ShipmentSort.ESTIMATED_DELIVERY -> shipments.sortedBy { it.estimatedDeliveryDate ?: Long.MAX_VALUE }
                ShipmentSort.CREATED_DATE -> shipments.sortedByDescending { it.createdAt }
                ShipmentSort.CARRIER -> shipments.sortedBy { it.carrier }
                ShipmentSort.STATUS -> shipments.sortedBy { it.status.ordinal }
            }
        }
    }

    /**
     * Gets a summary of shipment statistics.
     */
    suspend fun getShipmentSummary(): ShipmentSummary {
        return ShipmentSummary(
            totalActive = shipmentDao.countActive(),
            inTransit = shipmentDao.countInTransit(),
            outForDelivery = shipmentDao.countOutForDelivery(),
            delivered = shipmentDao.countByStatus(ShipmentStatus.DELIVERED),
            pending = shipmentDao.countByStatus(ShipmentStatus.PENDING)
        )
    }

    /**
     * Searches shipments by query.
     */
    fun searchShipments(query: String): Flow<List<Shipment>> {
        return shipmentDao.search(query)
    }

    /**
     * Gets shipments from a specific app.
     */
    fun getShipmentsFromApp(appPackage: String): Flow<List<Shipment>> {
        val appName = SUPPORTED_APPS[appPackage] ?: appPackage
        return shipmentDao.getBySourceApp(appName)
    }

    /**
     * Refreshes all shipments that need updating.
     *
     * This will:
     * 1. Find stale shipments (not updated recently)
     * 2. Query each source app for updates
     * 3. Update the database with new status
     *
     * @return List of shipments that were updated
     */
    suspend fun refreshStaleShipments(): List<Shipment> {
        val staleTime = System.currentTimeMillis() - STALE_THRESHOLD_MS
        val staleShipments = shipmentDao.getStaleShipments(staleTime)

        Log.i(TAG, "Found ${staleShipments.size} stale shipments to refresh")

        val updatedShipments = mutableListOf<Shipment>()

        // Group by source app for efficiency
        val shipmentsByApp = staleShipments.groupBy { it.sourceAppPackage }

        for ((appPackage, shipments) in shipmentsByApp) {
            try {
                val updates = refreshShipmentsFromApp(appPackage, shipments)
                updatedShipments.addAll(updates)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh shipments from $appPackage: ${e.message}")
            }
        }

        return updatedShipments
    }

    /**
     * Refreshes shipments from a specific app.
     */
    private suspend fun refreshShipmentsFromApp(
        appPackage: String,
        shipments: List<Shipment>
    ): List<Shipment> {
        Log.d(TAG, "Refreshing ${shipments.size} shipments from $appPackage")

        // Check if app requires re-authentication
        if (authHandler.needsReauthentication(appPackage)) {
            Log.w(TAG, "App $appPackage needs re-authentication")
            authHandler.requestReauthentication(appPackage)
            return emptyList()
        }

        val updatedShipments = mutableListOf<Shipment>()

        for (shipment in shipments) {
            try {
                val updatedShipment = refreshSingleShipment(shipment)
                if (updatedShipment != null) {
                    shipmentDao.update(updatedShipment)
                    updatedShipments.add(updatedShipment)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh shipment ${shipment.trackingNumber}: ${e.message}")
            }
        }

        return updatedShipments
    }

    /**
     * Refreshes a single shipment by extracting latest data.
     */
    private suspend fun refreshSingleShipment(shipment: Shipment): Shipment? {
        // Extract updated shipment data from the app
        val extractionResult = shipmentExtractor.extractShipmentUpdate(
            appPackage = shipment.sourceAppPackage,
            trackingNumber = shipment.trackingNumber,
            orderId = shipment.orderId
        )

        if (extractionResult == null) {
            Log.w(TAG, "Could not extract update for ${shipment.trackingNumber}")
            return null
        }

        // Merge with existing data
        return shipment.copy(
            status = extractionResult.status,
            statusMessage = extractionResult.statusMessage,
            lastLocation = extractionResult.lastLocation,
            lastUpdateTime = System.currentTimeMillis(),
            trackingHistory = extractionResult.trackingHistory ?: shipment.trackingHistory,
            estimatedDeliveryDate = extractionResult.estimatedDeliveryDate ?: shipment.estimatedDeliveryDate,
            actualDeliveryDate = if (extractionResult.status == ShipmentStatus.DELIVERED) {
                System.currentTimeMillis()
            } else {
                shipment.actualDeliveryDate
            }
        )
    }

    /**
     * Adds a new shipment to track.
     */
    suspend fun addShipment(shipment: Shipment) {
        Log.i(TAG, "Adding shipment: ${shipment.trackingNumber}")
        shipmentDao.insert(shipment)
    }

    /**
     * Archives a shipment.
     */
    suspend fun archiveShipment(shipmentId: java.util.UUID) {
        Log.i(TAG, "Archiving shipment: $shipmentId")
        shipmentDao.archive(shipmentId)
    }

    /**
     * Deletes a shipment.
     */
    suspend fun deleteShipment(shipmentId: java.util.UUID) {
        Log.i(TAG, "Deleting shipment: $shipmentId")
        shipmentDao.deleteById(shipmentId)
    }

    /**
     * Scans all supported apps for new shipments.
     *
     * @return List of newly discovered shipments
     */
    suspend fun scanForNewShipments(): List<Shipment> {
        Log.i(TAG, "Scanning for new shipments across apps")

        val newShipments = mutableListOf<Shipment>()
        val installedApps = getInstalledSupportedApps()

        for (appPackage in installedApps) {
            try {
                val shipments = shipmentExtractor.extractAllShipments(appPackage)

                for (shipment in shipments) {
                    // Check if we're already tracking this
                    if (!shipmentDao.exists(shipment.trackingNumber)) {
                        shipmentDao.insert(shipment)
                        newShipments.add(shipment)
                        Log.i(TAG, "Found new shipment: ${shipment.trackingNumber}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to scan $appPackage: ${e.message}")
            }
        }

        return newShipments
    }

    /**
     * Gets list of installed supported apps.
     */
    private fun getInstalledSupportedApps(): List<String> {
        val pm = context.packageManager
        return SUPPORTED_APPS.keys.filter { packageName ->
            try {
                pm.getPackageInfo(packageName, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Gets the display name for an app package.
     */
    fun getAppDisplayName(appPackage: String): String {
        return SUPPORTED_APPS[appPackage] ?: appPackage
    }

    /**
     * Checks if an app is supported for logistics tracking.
     */
    fun isAppSupported(appPackage: String): Boolean {
        return SUPPORTED_APPS.containsKey(appPackage)
    }

    /**
     * Gets distinct source apps from tracked shipments.
     */
    suspend fun getTrackedApps(): List<String> {
        return shipmentDao.getDistinctSourceApps()
    }

    /**
     * Gets distinct carriers from tracked shipments.
     */
    suspend fun getTrackedCarriers(): List<String> {
        return shipmentDao.getDistinctCarriers()
    }
}
