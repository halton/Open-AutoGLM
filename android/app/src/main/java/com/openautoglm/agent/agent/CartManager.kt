package com.openautoglm.agent.agent

import android.util.Log

/**
 * Helper class for managing shopping cart operations in food delivery apps.
 *
 * This class provides utilities for:
 * - Generating VLM prompts for cart navigation
 * - Tracking cart state and items
 * - Handling quantity modifications
 * - Managing checkout flow
 *
 * Works in conjunction with the VLM agent to navigate food delivery app carts.
 */
object CartManager {

    private const val TAG = "CartManager"

    /**
     * Represents an item in the cart.
     */
    data class CartItem(
        val name: String,
        val quantity: Int = 1,
        val price: String? = null,
        val customizations: List<String> = emptyList()
    ) {
        /**
         * Returns a display string for the item.
         */
        fun toDisplayString(): String {
            return buildString {
                append("$quantity x $name")
                price?.let { append(" ($it)") }
                if (customizations.isNotEmpty()) {
                    append(" [${customizations.joinToString(", ")}]")
                }
            }
        }
    }

    /**
     * Represents the current cart state.
     */
    data class CartState(
        val items: List<CartItem> = emptyList(),
        val subtotal: String? = null,
        val deliveryFee: String? = null,
        val total: String? = null,
        val promoCode: String? = null,
        val discount: String? = null,
        val estimatedDeliveryTime: String? = null
    ) {
        val itemCount: Int get() = items.sumOf { it.quantity }
        val isEmpty: Boolean get() = items.isEmpty()
    }

    /**
     * Cart action types.
     */
    enum class CartAction {
        VIEW_CART,
        ADD_ITEM,
        REMOVE_ITEM,
        UPDATE_QUANTITY,
        APPLY_PROMO,
        CLEAR_CART,
        PROCEED_TO_CHECKOUT,
        CONFIRM_ORDER
    }

    /**
     * Generates VLM prompt for viewing the cart.
     *
     * @param language Language code ("en" or "zh")
     * @return Prompt text for cart viewing
     */
    fun generateViewCartHint(language: String = "en"): String {
        return if (language == "zh") {
            buildString {
                append("查看购物车提示：\n")
                append("1. 找到购物车图标或'购物车'按钮（通常在右下角）\n")
                append("2. 点击购物车图标查看已添加的商品\n")
                append("3. 检查商品数量和价格是否正确\n")
                append("请点击购物车图标打开购物车页面。")
            }
        } else {
            buildString {
                append("View cart hints:\n")
                append("1. Find the cart icon or 'Cart' button (usually at bottom right)\n")
                append("2. Tap the cart icon to view added items\n")
                append("3. Verify item quantities and prices\n")
                append("Please tap the cart icon to open the cart page.")
            }
        }
    }

    /**
     * Generates VLM prompt for modifying item quantity.
     *
     * @param itemName The item to modify
     * @param newQuantity The desired quantity
     * @param language Language code
     * @return Prompt text for quantity modification
     */
    fun generateQuantityModifyHint(
        itemName: String,
        newQuantity: Int,
        language: String = "en"
    ): String {
        return if (language == "zh") {
            buildString {
                append("修改数量提示：\n")
                append("- 商品：$itemName\n")
                append("- 目标数量：$newQuantity\n")
                if (newQuantity == 0) {
                    append("- 操作：点击'-'按钮或删除按钮移除该商品\n")
                } else {
                    append("- 操作：使用'+'/'-'按钮调整数量，或直接点击数量输入\n")
                }
                append("请找到该商品并调整数量。")
            }
        } else {
            buildString {
                append("Quantity modification hints:\n")
                append("- Item: $itemName\n")
                append("- Target quantity: $newQuantity\n")
                if (newQuantity == 0) {
                    append("- Action: Tap '-' button or delete button to remove item\n")
                } else {
                    append("- Action: Use '+'/'-' buttons to adjust, or tap quantity to edit\n")
                }
                append("Please find the item and adjust its quantity.")
            }
        }
    }

    /**
     * Generates VLM prompt for applying a promo code.
     *
     * @param promoCode The promo code to apply
     * @param language Language code
     * @return Prompt text for promo code application
     */
    fun generatePromoCodeHint(promoCode: String, language: String = "en"): String {
        return if (language == "zh") {
            buildString {
                append("使用优惠码提示：\n")
                append("- 优惠码：$promoCode\n")
                append("步骤：\n")
                append("1. 找到'优惠码'、'折扣码'或'促销码'输入框\n")
                append("2. 点击输入框\n")
                append("3. 输入优惠码：$promoCode\n")
                append("4. 点击'使用'或'应用'按钮\n")
                append("请按上述步骤输入优惠码。")
            }
        } else {
            buildString {
                append("Apply promo code hints:\n")
                append("- Promo code: $promoCode\n")
                append("Steps:\n")
                append("1. Find 'Promo code', 'Discount code', or 'Coupon' input field\n")
                append("2. Tap the input field\n")
                append("3. Enter the code: $promoCode\n")
                append("4. Tap 'Apply' or 'Use' button\n")
                append("Please follow these steps to apply the promo code.")
            }
        }
    }

    /**
     * Generates VLM prompt for proceeding to checkout.
     *
     * @param language Language code
     * @return Prompt text for checkout
     */
    fun generateCheckoutHint(language: String = "en"): String {
        return if (language == "zh") {
            buildString {
                append("结算提示：\n")
                append("1. 确认购物车中的商品正确\n")
                append("2. 检查配送地址是否正确\n")
                append("3. 查看订单总价\n")
                append("4. 点击'去结算'、'下单'或'结账'按钮\n")
                append("请点击结算按钮进入支付页面。")
            }
        } else {
            buildString {
                append("Checkout hints:\n")
                append("1. Verify cart items are correct\n")
                append("2. Check delivery address is correct\n")
                append("3. Review order total\n")
                append("4. Tap 'Checkout', 'Place Order', or 'Pay' button\n")
                append("Please tap the checkout button to proceed to payment.")
            }
        }
    }

    /**
     * Generates VLM prompt for order confirmation.
     *
     * @param orderDetails Optional order details to confirm
     * @param language Language code
     * @return Prompt text for order confirmation
     */
    fun generateOrderConfirmHint(
        orderDetails: CartState? = null,
        language: String = "en"
    ): String {
        return if (language == "zh") {
            buildString {
                append("确认订单提示：\n")
                orderDetails?.let { state ->
                    if (state.items.isNotEmpty()) {
                        append("订单内容：\n")
                        state.items.forEach { item ->
                            append("  - ${item.toDisplayString()}\n")
                        }
                    }
                    state.total?.let { append("总价：$it\n") }
                    state.estimatedDeliveryTime?.let { append("预计送达：$it\n") }
                }
                append("\n请仔细检查订单信息，确认无误后点击'确认订单'或'支付'按钮。\n")
                append("注意：此操作将提交订单并可能扣款。")
            }
        } else {
            buildString {
                append("Order confirmation hints:\n")
                orderDetails?.let { state ->
                    if (state.items.isNotEmpty()) {
                        append("Order contents:\n")
                        state.items.forEach { item ->
                            append("  - ${item.toDisplayString()}\n")
                        }
                    }
                    state.total?.let { append("Total: $it\n") }
                    state.estimatedDeliveryTime?.let { append("Estimated delivery: $it\n") }
                }
                append("\nPlease review order details and tap 'Confirm Order' or 'Pay' button.\n")
                append("Note: This will submit your order and may charge your payment method.")
            }
        }
    }

    /**
     * Generates a summary of the order for user confirmation.
     *
     * @param state The cart state
     * @param language Language code
     * @return Human-readable order summary
     */
    fun generateOrderSummary(state: CartState, language: String = "en"): String {
        return if (language == "zh") {
            buildString {
                append("订单摘要：\n")
                append("━━━━━━━━━━━━━━━━━━━━\n")
                if (state.items.isEmpty()) {
                    append("购物车是空的\n")
                } else {
                    state.items.forEach { item ->
                        append("${item.toDisplayString()}\n")
                    }
                    append("━━━━━━━━━━━━━━━━━━━━\n")
                    state.subtotal?.let { append("小计：$it\n") }
                    state.deliveryFee?.let { append("配送费：$it\n") }
                    state.discount?.let { append("折扣：-$it\n") }
                    state.total?.let { append("合计：$it\n") }
                }
            }
        } else {
            buildString {
                append("Order Summary:\n")
                append("━━━━━━━━━━━━━━━━━━━━\n")
                if (state.items.isEmpty()) {
                    append("Cart is empty\n")
                } else {
                    state.items.forEach { item ->
                        append("${item.toDisplayString()}\n")
                    }
                    append("━━━━━━━━━━━━━━━━━━━━\n")
                    state.subtotal?.let { append("Subtotal: $it\n") }
                    state.deliveryFee?.let { append("Delivery fee: $it\n") }
                    state.discount?.let { append("Discount: -$it\n") }
                    state.total?.let { append("Total: $it\n") }
                }
            }
        }
    }

    /**
     * Common cart button labels for different apps.
     */
    object CartLabels {
        val viewCartLabels = listOf(
            // English
            "Cart", "View Cart", "My Cart", "Shopping Cart",
            // Chinese
            "购物车", "去购物车", "我的购物车", "购物袋"
        )

        val checkoutLabels = listOf(
            // English
            "Checkout", "Go to Checkout", "Place Order", "Proceed",
            "Pay", "Continue to Payment",
            // Chinese
            "去结算", "结算", "下单", "去支付", "立即下单", "提交订单"
        )

        val addToCartLabels = listOf(
            // English
            "Add to Cart", "Add", "Add Item", "Add to Order",
            // Chinese
            "加入购物车", "添加", "添加到购物车", "选好了"
        )

        val increaseQuantityLabels = listOf("+", "＋", "Add", "增加")
        val decreaseQuantityLabels = listOf("-", "－", "Remove", "减少")
        val deleteLabels = listOf("Delete", "Remove", "删除", "移除", "×", "✕")
    }

    /**
     * Identifies if the current screen is likely a cart screen.
     *
     * @param screenText Text content from the screen
     * @return true if this appears to be a cart screen
     */
    fun isCartScreen(screenText: String): Boolean {
        val lowerText = screenText.lowercase()
        val cartIndicators = listOf(
            "your cart", "my cart", "shopping cart", "order summary",
            "购物车", "我的购物车", "订单", "待支付"
        )
        return cartIndicators.any { lowerText.contains(it) }
    }

    /**
     * Identifies if the current screen is a checkout screen.
     *
     * @param screenText Text content from the screen
     * @return true if this appears to be a checkout screen
     */
    fun isCheckoutScreen(screenText: String): Boolean {
        val lowerText = screenText.lowercase()
        val checkoutIndicators = listOf(
            "checkout", "payment", "pay now", "place order",
            "delivery address", "payment method",
            "结算", "支付", "收货地址", "付款方式", "确认订单"
        )
        return checkoutIndicators.any { lowerText.contains(it) }
    }

    /**
     * Logs cart action for debugging.
     */
    fun logAction(action: CartAction, details: String? = null) {
        val message = buildString {
            append("Cart action: $action")
            details?.let { append(" - $it") }
        }
        Log.d(TAG, message)
    }
}
