package com.openautoglm.agent.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Confirmation dialog for sensitive actions.
 *
 * Used for purchase confirmations, payment authorization, and other
 * critical actions that require user approval (FR-007).
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    actionName: String = "Confirm",
    details: List<Pair<String, String>>? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )

                // Display details if provided
                details?.let { detailsList ->
                    Divider()

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            detailsList.forEach { (key, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            ) {
                Text(actionName)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}

/**
 * Specific confirmation dialog for purchase actions.
 */
@Composable
fun PurchaseConfirmationDialog(
    productName: String,
    price: Double,
    storeName: String,
    appName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmationDialog(
        title = "Confirm Purchase",
        message = "Do you want to proceed with this purchase?",
        actionName = "Purchase",
        details = listOf(
            "Product" to productName,
            "Store" to storeName,
            "App" to appName,
            "Price" to "¥${String.format("%.2f", price)}"
        ),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * Confirmation dialog for payment authorization.
 */
@Composable
fun PaymentConfirmationDialog(
    amount: Double,
    merchant: String,
    paymentMethod: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmationDialog(
        title = "Authorize Payment",
        message = "Confirm payment to proceed with this transaction.",
        actionName = "Pay",
        details = listOf(
            "Amount" to "¥${String.format("%.2f", amount)}",
            "Merchant" to merchant,
            "Payment Method" to paymentMethod
        ),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * Data class representing an item in a food order.
 */
data class OrderItem(
    val name: String,
    val quantity: Int,
    val price: String? = null,
    val customizations: List<String> = emptyList()
)

/**
 * Confirmation dialog for food orders.
 *
 * Displays order details including items, restaurant, delivery info,
 * and total cost before submitting the order.
 */
@Composable
fun OrderConfirmationDialog(
    restaurantName: String,
    items: List<OrderItem>,
    subtotal: String? = null,
    deliveryFee: String? = null,
    discount: String? = null,
    total: String,
    deliveryAddress: String? = null,
    estimatedDeliveryTime: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Confirm Order",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Restaurant name
                Text(
                    text = restaurantName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Divider()

                // Order items
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Order Items",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.quantity}x ${item.name}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (item.customizations.isNotEmpty()) {
                                        Text(
                                            text = item.customizations.joinToString(", "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                item.price?.let { price ->
                                    Text(
                                        text = price,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Price breakdown
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        subtotal?.let {
                            PriceRow(label = "Subtotal", value = it)
                        }
                        deliveryFee?.let {
                            PriceRow(label = "Delivery Fee", value = it)
                        }
                        discount?.let {
                            PriceRow(
                                label = "Discount",
                                value = "-$it",
                                valueColor = MaterialTheme.colorScheme.primary
                            )
                        }
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        PriceRow(
                            label = "Total",
                            value = total,
                            isBold = true
                        )
                    }
                }

                // Delivery info
                if (deliveryAddress != null || estimatedDeliveryTime != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Delivery Info",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            deliveryAddress?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            estimatedDeliveryTime?.let {
                                Text(
                                    text = "Est. delivery: $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Warning
                Text(
                    text = "This will submit your order and may charge your payment method.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            ) {
                Text("Place Order")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Helper composable for price rows in order summary.
 */
@Composable
private fun PriceRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            color = valueColor
        )
    }
}

/**
 * Simplified order confirmation dialog with minimal details.
 */
@Composable
fun SimpleOrderConfirmationDialog(
    restaurantName: String,
    itemCount: Int,
    total: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmationDialog(
        title = "Confirm Order",
        message = "Ready to place your order from $restaurantName?",
        actionName = "Place Order",
        details = listOf(
            "Restaurant" to restaurantName,
            "Items" to "$itemCount item(s)",
            "Total" to total
        ),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * Data class representing a passenger for booking.
 */
data class BookingPassenger(
    val name: String,
    val idType: String,
    val seatClass: String,
    val seatNumber: String? = null
)

/**
 * Confirmation dialog for travel bookings (train, flight, etc.).
 *
 * Displays booking details including route, date/time, passengers,
 * and total cost before confirming the booking.
 */
@Composable
fun BookingConfirmationDialog(
    bookingType: String = "Train",
    routeFrom: String,
    routeTo: String,
    vehicleNumber: String,  // Train number or flight number
    departureDateTime: String,
    arrivalDateTime: String,
    duration: String,
    passengers: List<BookingPassenger>,
    totalPrice: String,
    serviceFee: String? = null,
    insuranceInfo: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Confirm $bookingType Booking",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Route info
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = vehicleNumber,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = departureDateTime,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = routeFrom,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Text(
                                    text = arrivalDateTime,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = routeTo,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Text(
                            text = "Duration: $duration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Passenger info
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Passengers (${passengers.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        passengers.forEach { passenger ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = passenger.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = passenger.idType,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                    Text(
                                        text = passenger.seatClass,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    passenger.seatNumber?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Price breakdown
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        serviceFee?.let {
                            PriceRow(label = "Service Fee", value = it)
                        }
                        insuranceInfo?.let {
                            PriceRow(label = "Insurance", value = it)
                        }
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        PriceRow(
                            label = "Total",
                            value = totalPrice,
                            isBold = true
                        )
                    }
                }

                // Warning
                Text(
                    text = "This will submit your booking. Please ensure all information is correct.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            ) {
                Text("Confirm Booking")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Simple booking confirmation with minimal details.
 */
@Composable
fun SimpleBookingConfirmationDialog(
    bookingType: String = "Train",
    route: String,
    dateTime: String,
    passengerCount: Int,
    totalPrice: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmationDialog(
        title = "Confirm $bookingType Booking",
        message = "Ready to book your $bookingType ticket?",
        actionName = "Book Now",
        details = listOf(
            "Route" to route,
            "Date/Time" to dateTime,
            "Passengers" to "$passengerCount",
            "Total" to totalPrice
        ),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
