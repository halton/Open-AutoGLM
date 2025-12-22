package com.openautoglm.agent.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.openautoglm.agent.data.entities.Shipment
import com.openautoglm.agent.data.entities.ShipmentStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for Shipment entities.
 *
 * Provides methods for:
 * - CRUD operations on shipments
 * - Filtering by status, app, carrier
 * - Observing shipment updates via Flow
 * - Aggregation queries for summaries
 */
@Dao
interface ShipmentDao {

    // ==================== Insert Operations ====================

    /**
     * Inserts a new shipment.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shipment: Shipment)

    /**
     * Inserts multiple shipments.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(shipments: List<Shipment>)

    // ==================== Update Operations ====================

    /**
     * Updates an existing shipment.
     */
    @Update
    suspend fun update(shipment: Shipment)

    /**
     * Updates the status of a shipment.
     */
    @Query("UPDATE shipments SET status = :status, statusMessage = :statusMessage, lastUpdateTime = :updateTime WHERE id = :id")
    suspend fun updateStatus(id: UUID, status: ShipmentStatus, statusMessage: String?, updateTime: Long = System.currentTimeMillis())

    /**
     * Archives a shipment.
     */
    @Query("UPDATE shipments SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: UUID)

    /**
     * Unarchives a shipment.
     */
    @Query("UPDATE shipments SET isArchived = 0 WHERE id = :id")
    suspend fun unarchive(id: UUID)

    /**
     * Updates the last location of a shipment.
     */
    @Query("UPDATE shipments SET lastLocation = :location, lastUpdateTime = :updateTime WHERE id = :id")
    suspend fun updateLocation(id: UUID, location: String, updateTime: Long = System.currentTimeMillis())

    // ==================== Delete Operations ====================

    /**
     * Deletes a shipment.
     */
    @Delete
    suspend fun delete(shipment: Shipment)

    /**
     * Deletes a shipment by ID.
     */
    @Query("DELETE FROM shipments WHERE id = :id")
    suspend fun deleteById(id: UUID)

    /**
     * Deletes all archived shipments.
     */
    @Query("DELETE FROM shipments WHERE isArchived = 1")
    suspend fun deleteAllArchived()

    /**
     * Deletes shipments older than specified time.
     */
    @Query("DELETE FROM shipments WHERE createdAt < :olderThan AND isArchived = 1")
    suspend fun deleteOldArchived(olderThan: Long)

    // ==================== Query Operations ====================

    /**
     * Gets a shipment by ID.
     */
    @Query("SELECT * FROM shipments WHERE id = :id")
    suspend fun getById(id: UUID): Shipment?

    /**
     * Gets a shipment by ID as Flow.
     */
    @Query("SELECT * FROM shipments WHERE id = :id")
    fun getByIdFlow(id: UUID): Flow<Shipment?>

    /**
     * Gets a shipment by tracking number.
     */
    @Query("SELECT * FROM shipments WHERE trackingNumber = :trackingNumber")
    suspend fun getByTrackingNumber(trackingNumber: String): Shipment?

    /**
     * Gets all shipments ordered by last update time.
     */
    @Query("SELECT * FROM shipments WHERE isArchived = 0 ORDER BY lastUpdateTime DESC")
    fun getAllActive(): Flow<List<Shipment>>

    /**
     * Gets all shipments including archived.
     */
    @Query("SELECT * FROM shipments ORDER BY lastUpdateTime DESC")
    fun getAll(): Flow<List<Shipment>>

    /**
     * Gets archived shipments.
     */
    @Query("SELECT * FROM shipments WHERE isArchived = 1 ORDER BY lastUpdateTime DESC")
    fun getArchived(): Flow<List<Shipment>>

    /**
     * Gets shipments by status.
     */
    @Query("SELECT * FROM shipments WHERE status = :status AND isArchived = 0 ORDER BY lastUpdateTime DESC")
    fun getByStatus(status: ShipmentStatus): Flow<List<Shipment>>

    /**
     * Gets shipments from a specific app.
     */
    @Query("SELECT * FROM shipments WHERE sourceApp = :appName AND isArchived = 0 ORDER BY lastUpdateTime DESC")
    fun getBySourceApp(appName: String): Flow<List<Shipment>>

    /**
     * Gets shipments by carrier.
     */
    @Query("SELECT * FROM shipments WHERE carrier = :carrier AND isArchived = 0 ORDER BY lastUpdateTime DESC")
    fun getByCarrier(carrier: String): Flow<List<Shipment>>

    /**
     * Gets shipments with pending delivery (out for delivery).
     */
    @Query("SELECT * FROM shipments WHERE status = 'OUT_FOR_DELIVERY' AND isArchived = 0 ORDER BY estimatedDeliveryDate ASC")
    fun getPendingDelivery(): Flow<List<Shipment>>

    /**
     * Gets shipments in transit.
     */
    @Query("SELECT * FROM shipments WHERE status IN ('PICKED_UP', 'IN_TRANSIT') AND isArchived = 0 ORDER BY lastUpdateTime DESC")
    fun getInTransit(): Flow<List<Shipment>>

    /**
     * Gets recently delivered shipments.
     */
    @Query("SELECT * FROM shipments WHERE status = 'DELIVERED' AND isArchived = 0 ORDER BY actualDeliveryDate DESC LIMIT :limit")
    fun getRecentlyDelivered(limit: Int = 10): Flow<List<Shipment>>

    /**
     * Searches shipments by description or tracking number.
     */
    @Query("SELECT * FROM shipments WHERE (itemDescription LIKE '%' || :query || '%' OR trackingNumber LIKE '%' || :query || '%') AND isArchived = 0 ORDER BY lastUpdateTime DESC")
    fun search(query: String): Flow<List<Shipment>>

    // ==================== Aggregation Queries ====================

    /**
     * Counts all active shipments.
     */
    @Query("SELECT COUNT(*) FROM shipments WHERE isArchived = 0")
    suspend fun countActive(): Int

    /**
     * Counts shipments by status.
     */
    @Query("SELECT COUNT(*) FROM shipments WHERE status = :status AND isArchived = 0")
    suspend fun countByStatus(status: ShipmentStatus): Int

    /**
     * Counts in-transit shipments.
     */
    @Query("SELECT COUNT(*) FROM shipments WHERE status IN ('PICKED_UP', 'IN_TRANSIT') AND isArchived = 0")
    suspend fun countInTransit(): Int

    /**
     * Counts out-for-delivery shipments.
     */
    @Query("SELECT COUNT(*) FROM shipments WHERE status = 'OUT_FOR_DELIVERY' AND isArchived = 0")
    suspend fun countOutForDelivery(): Int

    /**
     * Gets distinct source apps.
     */
    @Query("SELECT DISTINCT sourceApp FROM shipments WHERE isArchived = 0")
    suspend fun getDistinctSourceApps(): List<String>

    /**
     * Gets distinct carriers.
     */
    @Query("SELECT DISTINCT carrier FROM shipments WHERE isArchived = 0")
    suspend fun getDistinctCarriers(): List<String>

    /**
     * Gets shipments that need status update (not updated in last N hours).
     */
    @Query("SELECT * FROM shipments WHERE status NOT IN ('DELIVERED', 'RETURNED', 'CANCELLED') AND isArchived = 0 AND lastUpdateTime < :olderThan")
    suspend fun getStaleShipments(olderThan: Long): List<Shipment>

    /**
     * Checks if a tracking number already exists.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM shipments WHERE trackingNumber = :trackingNumber)")
    suspend fun exists(trackingNumber: String): Boolean
}
