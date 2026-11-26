package nose.model

import kotlinx.serialization.Serializable

object BrandConfig {
    const val NAME = "TBD.SUPPLY"
    const val TAGLINE = "Hardware, Drip & Objetos con Criterio"
    const val CURRENCY = "USD"
    val FOUNDERS = listOf("Todos", "Enrique", "Edgarvis", "Leonel", "Aníbal")
    val ADMIN_FOUNDER_TAGS = listOf("Enrique", "Edgarvis", "Leonel", "Aníbal", "Crew")
    val ORDER_STATUSES = listOf("pending", "preparing", "shipped", "delivered", "cancelled")
    val CATEGORY_ACCENTS = listOf(
        "Azul" to "#3482FF",
        "Esmeralda" to "#00BFA5",
        "Violeta" to "#7C4DFF",
        "Naranja" to "#FF6D00",
        "Rosa" to "#E91E63",
        "Cian" to "#00BCD4",
    )

    fun orderStatusLabel(status: String): String = when (status) {
        "pending" -> "Pendiente"
        "preparing" -> "Preparando"
        "shipped" -> "Enviado"
        "delivered" -> "Entregado"
        "cancelled" -> "Cancelado"
        else -> status
    }
}

fun formatUsd(cents: Int): String {
    val neg = cents < 0
    val absVal = kotlin.math.abs(cents)
    val dollars = absVal / 100
    val rem = absVal % 100
    val remStr = if (rem < 10) "0$rem" else "$rem"
    return if (neg) "-$$dollars.$remStr" else "$$dollars.$remStr"
}

fun parseUsdToCents(input: String): Int? {
    val clean = input.trim().replace("$", "").replace(",", ".")
    if (clean.isEmpty()) return null
    val parts = clean.split(".")
    if (parts.size > 2) return null
    val dollars = parts[0].toLongOrNull() ?: return null
    if (dollars < 0 || dollars > 1_000_000L) return null
    val cents = when {
        parts.size == 1 -> 0L
        parts[1].isEmpty() -> 0L
        parts[1].length == 1 -> (parts[1].toLongOrNull() ?: return null) * 10L
        parts[1].length == 2 -> parts[1].toLongOrNull() ?: return null
        else -> return null
    }
    return (dollars * 100L + cents).toInt()
}

@Serializable
data class ErrorDto(val error: String = "Error inesperado")

@Serializable
data class SimpleOkDto(val ok: Boolean = true, val revoked: Int? = null, val archived: Boolean? = null)

@Serializable
data class UserDto(
    val id: Int,
    val username: String,
    val email: String,
    val displayName: String,
    val role: String,
)

@Serializable
data class AuthResponseDto(
    val user: UserDto? = null,
    val csrfToken: String? = null,
)

@Serializable
data class CategoryDto(
    val id: Int,
    val name: String,
    val slug: String,
    val description: String = "",
    val accentHex: String = "#3482FF",
    val sortOrder: Int = 0,
    val isActive: Int = 1,
    val productCount: Int = 0,
)

@Serializable
data class CategoriesResponseDto(val categories: List<CategoryDto> = emptyList())

@Serializable
data class CategorySingleResponseDto(val category: CategoryDto)

@Serializable
data class ProductImageDto(
    val id: Int,
    val url: String,
    val mimeType: String = "image/png",
    val isPrimary: Int = 0,
    val sortOrder: Int = 0,
)

@Serializable
data class ProductDto(
    val id: Int,
    val categoryId: Int,
    val categoryName: String = "",
    val categorySlug: String = "",
    val categoryAccent: String = "#3482FF",
    val founderTag: String = "Crew",
    val name: String,
    val slug: String,
    val subtitle: String = "",
    val description: String = "",
    val priceCents: Int,
    val compareAtCents: Int? = null,
    val stock: Int = 0,
    val isActive: Int = 1,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val primaryImageUrl: String? = null,
    val images: List<ProductImageDto> = emptyList(),
)

@Serializable
data class ProductsPageDto(
    val items: List<ProductDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 24,
)

@Serializable
data class ProductSingleResponseDto(val product: ProductDto)

@Serializable
data class AdminProductsResponseDto(val products: List<ProductDto> = emptyList())

@Serializable
data class CartItemDto(
    val productId: Int,
    val quantity: Int,
    val name: String,
    val slug: String,
    val priceCents: Int,
    val stock: Int,
    val founderTag: String = "Crew",
    val categoryName: String = "",
    val primaryImageUrl: String? = null,
    val lineTotalCents: Int = 0,
    val stockExceeded: Boolean = false,
)

@Serializable
data class CartResponseDto(
    val items: List<CartItemDto> = emptyList(),
    val itemCount: Int = 0,
    val subtotalCents: Int = 0,
)

@Serializable
data class OrderItemDto(
    val id: Int = 0,
    val productId: Int? = null,
    val productName: String,
    val unitPriceCents: Int,
    val quantity: Int,
    val lineTotalCents: Int,
)

@Serializable
data class OrderMessageDto(
    val id: Int,
    val orderId: Int,
    val senderUserId: Int,
    val senderUsername: String,
    val senderDisplayName: String,
    val senderRole: String,
    val messageText: String = "",
    val imageUrl: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class OrderMessagesResponseDto(
    val orderCode: String = "",
    val messages: List<OrderMessageDto> = emptyList(),
)

@Serializable
data class OrderMessageSingleResponseDto(
    val message: OrderMessageDto,
)

@Serializable
data class OrderDto(
    val id: Int,
    val orderCode: String,
    val userId: Int = 0,
    val username: String = "",
    val email: String = "",
    val status: String,
    val subtotalCents: Int = 0,
    val totalCents: Int,
    val shippingName: String,
    val shippingNote: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val itemCount: Int = 0,
    val messageCount: Int = 0,
    val items: List<OrderItemDto> = emptyList(),
    val messages: List<OrderMessageDto> = emptyList(),
)

@Serializable
data class OrdersResponseDto(val orders: List<OrderDto> = emptyList())

@Serializable
data class OrderSingleResponseDto(val order: OrderDto)

@Serializable
data class DashboardSummaryDto(
    val revenueCents: Long = 0L,
    val totalOrders: Int = 0,
    val pendingOrders: Int = 0,
    val activeProducts: Int = 0,
    val lowStockCount: Int = 0,
    val activeCategories: Int = 0,
    val activeUsers: Int = 0,
    val activeSessions: Int = 0,
)

@Serializable
data class LowStockProductDto(
    val id: Int,
    val name: String,
    val slug: String,
    val stock: Int,
    val priceCents: Int,
)

@Serializable
data class AuditLogDto(
    val id: Int,
    val actorUserId: Int? = null,
    val actorUsername: String? = null,
    val action: String,
    val targetType: String,
    val targetId: String = "",
    val metadataJson: String = "{}",
    val createdAt: Long = 0L,
)

@Serializable
data class AdminDashboardResponseDto(
    val summary: DashboardSummaryDto = DashboardSummaryDto(),
    val lowStockProducts: List<LowStockProductDto> = emptyList(),
    val recentAuditLogs: List<AuditLogDto> = emptyList(),
)

@Serializable
data class AdminUserDto(
    val id: Int,
    val username: String,
    val email: String,
    val displayName: String,
    val role: String,
    val isActive: Int,
    val failedAttempts: Int = 0,
    val lockedUntil: Long? = null,
    val createdAt: Long = 0L,
    val activeSessions: Int = 0,
)

@Serializable
data class AdminUsersResponseDto(val users: List<AdminUserDto> = emptyList())

@Serializable
data class AuditLogsResponseDto(val logs: List<AuditLogDto> = emptyList())

@Serializable
data class UploadedImageDto(
    val id: Int,
    val productId: Int,
    val url: String,
    val mimeType: String = "image/png",
    val byteSize: Int = 0,
    val isPrimary: Int = 0,
)

@Serializable
data class UploadMediaResponseDto(val image: UploadedImageDto)
