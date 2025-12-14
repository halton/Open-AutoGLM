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
