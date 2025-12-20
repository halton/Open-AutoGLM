package com.openautoglm.agent.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openautoglm.agent.data.entities.Shipment
import com.openautoglm.agent.data.entities.ShipmentFilter
import com.openautoglm.agent.data.entities.ShipmentSummary
import com.openautoglm.agent.ui.components.ShipmentCard
import kotlinx.coroutines.flow.StateFlow

/**
 * Main screen for displaying and managing tracked shipments.
 *
 * Features:
 * - Filter chips for status filtering
 * - Manual refresh via button
 * - Search functionality
 * - Summary statistics card
 *
 * @param shipmentsFlow Flow of shipments to display
 * @param summaryFlow Flow of shipment summary stats
 * @param currentFilter Current active filter
 * @param onFilterChange Callback when filter changes
 * @param onRefresh Callback to refresh shipments
 * @param onShipmentClick Callback when a shipment is clicked
 * @param onScanApps Callback to scan apps for new shipments
 * @param isRefreshing Whether refresh is in progress
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShipmentListScreen(
    shipmentsFlow: StateFlow<List<Shipment>>,
    summaryFlow: StateFlow<ShipmentSummary?>,
    currentFilter: ShipmentFilter,
    onFilterChange: (ShipmentFilter) -> Unit,
    onRefresh: () -> Unit,
    onShipmentClick: (Shipment) -> Unit,
    onScanApps: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val shipments by shipmentsFlow.collectAsState()
    val summary by summaryFlow.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("物流追踪") },
                    actions = {
                        IconButton(onClick = { isSearchActive = !isSearchActive }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                        IconButton(
                            onClick = onRefresh,
                            enabled = !isRefreshing
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                )

                // Loading indicator
                if (isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onScanApps,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "扫描应用")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            AnimatedVisibility(
                visible = isSearchActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { /* Handle search */ },
                    active = false,
                    onActiveChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索包裹...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                ) {}
            }

            // Summary card
            summary?.let { stats ->
                SummaryCard(
                    summary = stats,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Filter chips
            FilterChipsRow(
                currentFilter = currentFilter,
                onFilterChange = onFilterChange,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Shipment list
            if (shipments.isEmpty() && !isRefreshing) {
                EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                )
            } else if (shipments.isEmpty() && isRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val filteredShipments = if (searchQuery.isNotBlank()) {
                    shipments.filter {
                        it.itemDescription.contains(searchQuery, ignoreCase = true) ||
                        it.trackingNumber.contains(searchQuery, ignoreCase = true) ||
                        it.carrier.contains(searchQuery, ignoreCase = true)
                    }
                } else {
                    shipments
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = filteredShipments,
                        key = { it.id }
                    ) { shipment ->
                        ShipmentCard(
                            shipment = shipment,
                            onClick = { onShipmentClick(shipment) }
                        )
                    }

                    // Bottom spacing for FAB
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

/**
 * Summary statistics card.
 */
@Composable
private fun SummaryCard(
    summary: ShipmentSummary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            SummaryItem(
                count = summary.totalActive,
                label = "跟踪中",
                color = MaterialTheme.colorScheme.primary
            )
            SummaryItem(
                count = summary.inTransit,
                label = "运输中",
                color = Color(0xFFFF9800)
            )
            SummaryItem(
                count = summary.outForDelivery,
                label = "派送中",
                color = Color(0xFF4CAF50)
            )
            SummaryItem(
                count = summary.delivered,
                label = "已送达",
                color = Color(0xFF9E9E9E)
            )
        }
    }
}

/**
 * Single summary statistic item.
 */
@Composable
private fun SummaryItem(
    count: Int,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Filter chips row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipsRow(
    currentFilter: ShipmentFilter,
    onFilterChange: (ShipmentFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val filters = listOf(
            ShipmentFilter.ALL to "全部",
            ShipmentFilter.IN_TRANSIT to "运输中",
            ShipmentFilter.OUT_FOR_DELIVERY to "派送中",
            ShipmentFilter.DELIVERED to "已送达",
            ShipmentFilter.PENDING to "待发货"
        )

        items(filters) { (filter, label) ->
            FilterChip(
                selected = currentFilter == filter,
                onClick = { onFilterChange(filter) },
                label = { Text(label) }
            )
        }
    }
}

/**
 * Empty state when no shipments.
 */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.LocalShipping,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "暂无包裹",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "点击右下角按钮扫描购物应用\n自动发现您的包裹",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}
