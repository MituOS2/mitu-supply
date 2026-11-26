package nose.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nose.model.AdminDashboardResponseDto
import nose.model.AdminProductsResponseDto
import nose.model.AdminUsersResponseDto
import nose.model.AuditLogsResponseDto
import nose.model.AuthResponseDto
import nose.model.CartResponseDto
import nose.model.CategoriesResponseDto
import nose.model.CategoryDto
import nose.model.CategorySingleResponseDto
import nose.model.ErrorDto
import nose.model.OrderDto
import nose.model.OrderMessageDto
import nose.model.OrderMessageSingleResponseDto
import nose.model.OrderMessagesResponseDto
import nose.model.OrderSingleResponseDto
import nose.model.OrdersResponseDto
import nose.model.ProductDto
import nose.model.ProductSingleResponseDto
import nose.model.ProductsPageDto
import nose.model.SimpleOkDto
import nose.model.UploadMediaResponseDto
import nose.model.UserDto

class ApiException(val status: Int, override val message: String) : Exception(message)

@Serializable
private data class LoginReq(val identifier: String, val password: String)

@Serializable
private data class RegisterReq(
    val username: String,
    val email: String,
    val password: String,
    val displayName: String? = null,
)

@Serializable
private data class ChangePasswordReq(val currentPassword: String, val newPassword: String)

@Serializable
private data class CartQtyReq(val quantity: Int)

@Serializable
data class GuestCartMergeItem(val productId: Int, val quantity: Int)

@Serializable
private data class CartMergeReq(val items: List<GuestCartMergeItem>)

@Serializable
private data class CheckoutReq(val shippingName: String, val shippingNote: String)

@Serializable
data class AdminCategoryPayload(
    val name: String,
    val slug: String? = null,
    val description: String = "",
    val accentHex: String = "#3482FF",
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
)

@Serializable
data class AdminProductPayload(
    val categoryId: Int? = null,
    val categoryName: String? = null,
    val founderTag: String = "Crew",
    val name: String,
    val slug: String? = null,
    val subtitle: String = "",
    val description: String = "",
    val priceCents: Int,
    val compareAtCents: Int? = null,
    val stock: Int,
    val isActive: Boolean = true,
)

@Serializable
private data class OrderStatusReq(val status: String)

@Serializable
private data class OrderMessageReq(val message: String)

@Serializable
private data class ProductStockPatchReq(val stock: Int)

@Serializable
private data class AdminUserPatchReq(val isActive: Boolean? = null, val role: String? = null)

private fun encodeQueryComponent(value: String): String {
    val bytes = value.encodeToByteArray()
    val hex = "0123456789ABCDEF"
    val sb = StringBuilder(bytes.size)
    for (b in bytes) {
        val u = b.toInt() and 0xFF
        val isSafe = (u in 'A'.code..'Z'.code) ||
            (u in 'a'.code..'z'.code) ||
            (u in '0'.code..'9'.code) ||
            u == '-'.code || u == '_'.code || u == '.'.code || u == '~'.code
        if (isSafe) {
            sb.append(u.toChar())
        } else {
            sb.append('%')
            sb.append(hex[(u ushr 4) and 0x0F])
            sb.append(hex[u and 0x0F])
        }
    }
    return sb.toString()
}

class StoreApi {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    var csrfToken: String? = null
        private set

    private suspend inline fun <reified T> call(
        method: String,
        path: String,
        bodyJson: String? = null,
    ): T {
        val resp = platformHttpRequest(method, path, bodyJson, if (method != "GET") csrfToken else null)
        if (resp.status == 0) {
            throw ApiException(0, "No se pudo conectar con el servidor")
        }
        if (resp.status !in 200..299) {
            val errMsg = runCatching { json.decodeFromString<ErrorDto>(resp.body).error }
                .getOrElse { "Error HTTP ${resp.status}" }
            throw ApiException(resp.status, errMsg)
        }
        return json.decodeFromString<T>(resp.body)
    }

    suspend fun getMe(): AuthResponseDto {
        val res = call<AuthResponseDto>("GET", "/api/auth/me")
        csrfToken = res.csrfToken
        return res
    }

    suspend fun login(identifier: String, password: String): UserDto {
        val res = call<AuthResponseDto>(
            "POST",
            "/api/auth/login",
            json.encodeToString(LoginReq(identifier, password)),
        )
        csrfToken = res.csrfToken
        return res.user ?: throw ApiException(500, "Respuesta de sesion invalida")
    }

    suspend fun register(username: String, email: String, password: String, displayName: String? = null): UserDto {
        val res = call<AuthResponseDto>(
            "POST",
            "/api/auth/register",
            json.encodeToString(RegisterReq(username, email, password, displayName)),
        )
        csrfToken = res.csrfToken
        return res.user ?: throw ApiException(500, "Respuesta de registro invalida")
    }

    suspend fun logout() {
        runCatching { call<SimpleOkDto>("POST", "/api/auth/logout", "{}") }
        csrfToken = null
    }

    suspend fun logoutAll() {
        call<SimpleOkDto>("POST", "/api/auth/logout-all", "{}")
        csrfToken = null
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val res = call<AuthResponseDto>(
            "POST",
            "/api/auth/change-password",
            json.encodeToString(ChangePasswordReq(currentPassword, newPassword)),
        )
        if (res.csrfToken != null) csrfToken = res.csrfToken
    }

    suspend fun getCategories(): List<CategoryDto> =
        call<CategoriesResponseDto>("GET", "/api/categories").categories

    suspend fun getProducts(
        query: String = "",
        categorySlug: String = "",
        founder: String = "",
        sort: String = "newest",
    ): ProductsPageDto {
        val params = buildList {
            if (query.isNotBlank()) add("q=${encodeQueryComponent(query.trim())}")
            if (categorySlug.isNotBlank()) add("category=${encodeQueryComponent(categorySlug)}")
            if (founder.isNotBlank() && founder != "Todos") add("founder=${encodeQueryComponent(founder)}")
            if (sort.isNotBlank()) add("sort=${encodeQueryComponent(sort)}")
            add("limit=48")
        }.joinToString("&")
        return call("GET", "/api/products?$params")
    }

    suspend fun getProductDetail(slug: String): ProductDto =
        call<ProductSingleResponseDto>("GET", "/api/products/${encodeQueryComponent(slug)}").product

    suspend fun getCart(): CartResponseDto = call("GET", "/api/cart")

    suspend fun setCartQuantity(productId: Int, quantity: Int): CartResponseDto =
        call("PUT", "/api/cart/items/$productId", json.encodeToString(CartQtyReq(quantity)))

    suspend fun mergeGuestCart(items: List<GuestCartMergeItem>): CartResponseDto =
        call("POST", "/api/cart/merge", json.encodeToString(CartMergeReq(items)))

    suspend fun checkout(shippingName: String, shippingNote: String): OrderDto =
        call<OrderSingleResponseDto>(
            "POST",
            "/api/checkout",
            json.encodeToString(CheckoutReq(shippingName, shippingNote)),
        ).order

    suspend fun getMyOrders(): List<OrderDto> =
        call<OrdersResponseDto>("GET", "/api/orders").orders

    suspend fun getOrderDetail(orderCode: String): OrderDto =
        call<OrderSingleResponseDto>("GET", "/api/orders/${encodeQueryComponent(orderCode)}").order

    suspend fun getOrderMessages(orderCode: String): List<OrderMessageDto> =
        call<OrderMessagesResponseDto>("GET", "/api/orders/${encodeQueryComponent(orderCode)}/messages").messages

    suspend fun sendOrderMessage(orderCode: String, message: String): OrderMessageDto =
        call<OrderMessageSingleResponseDto>(
            "POST",
            "/api/orders/${encodeQueryComponent(orderCode)}/messages",
            json.encodeToString(OrderMessageReq(message)),
        ).message

    suspend fun pickAndSendOrderImage(orderCode: String, message: String = ""): OrderMessageDto? {
        val token = csrfToken ?: throw ApiException(401, "Sesion requerida")
        val resp = platformPickAndSendOrderImage(orderCode, message, token) ?: return null
        if (resp.status == 0) throw ApiException(0, resp.body.ifBlank { "Error al enviar imagen" })
        if (resp.status !in 200..299) {
            val errMsg = runCatching { json.decodeFromString<ErrorDto>(resp.body).error }
                .getOrElse { "Error al enviar imagen (${resp.status})" }
            throw ApiException(resp.status, errMsg)
        }
        return json.decodeFromString<OrderMessageSingleResponseDto>(resp.body).message
    }

    // Admin endpoints
    suspend fun getAdminDashboard(): AdminDashboardResponseDto =
        call("GET", "/api/admin/dashboard")

    suspend fun getAdminCategories(): List<CategoryDto> =
        call<CategoriesResponseDto>("GET", "/api/admin/categories").categories

    suspend fun createCategory(payload: AdminCategoryPayload): CategoryDto =
        call<CategorySingleResponseDto>("POST", "/api/admin/categories", json.encodeToString(payload)).category

    suspend fun updateCategory(id: Int, payload: AdminCategoryPayload): CategoryDto =
        call<CategorySingleResponseDto>("PATCH", "/api/admin/categories/$id", json.encodeToString(payload)).category

    suspend fun deleteCategory(id: Int) {
        call<SimpleOkDto>("DELETE", "/api/admin/categories/$id", "{}")
    }

    suspend fun getAdminProducts(): List<ProductDto> =
        call<AdminProductsResponseDto>("GET", "/api/admin/products").products

    suspend fun createProduct(payload: AdminProductPayload): ProductDto =
        call<ProductSingleResponseDto>("POST", "/api/admin/products", json.encodeToString(payload)).product

    suspend fun updateProduct(id: Int, payload: AdminProductPayload): ProductDto =
        call<ProductSingleResponseDto>("PATCH", "/api/admin/products/$id", json.encodeToString(payload)).product

    suspend fun updateProductStock(id: Int, stock: Int): ProductDto =
        call<ProductSingleResponseDto>("PATCH", "/api/admin/products/$id", json.encodeToString(ProductStockPatchReq(stock))).product

    suspend fun deleteProduct(id: Int): SimpleOkDto =
        call("DELETE", "/api/admin/products/$id", "{}")

    suspend fun pickAndUploadProductImage(productId: Int, isPrimary: Boolean): UploadMediaResponseDto? {
        val token = csrfToken ?: throw ApiException(401, "Sesion requerida")
        val resp = platformPickAndUploadImage(productId, isPrimary, token) ?: return null
        if (resp.status == 0) throw ApiException(0, resp.body.ifBlank { "Error al subir imagen" })
        if (resp.status !in 200..299) {
            val errMsg = runCatching { json.decodeFromString<ErrorDto>(resp.body).error }
                .getOrElse { "Error al subir imagen (${resp.status})" }
            throw ApiException(resp.status, errMsg)
        }
        return json.decodeFromString<UploadMediaResponseDto>(resp.body)
    }

    suspend fun setPrimaryMedia(mediaId: Int) {
        call<SimpleOkDto>("PATCH", "/api/admin/media/$mediaId/primary", "{}")
    }

    suspend fun deleteMedia(mediaId: Int) {
        call<SimpleOkDto>("DELETE", "/api/admin/media/$mediaId", "{}")
    }

    suspend fun getAdminOrders(status: String = ""): List<OrderDto> {
        val q = if (status.isNotBlank() && status != "all") "?status=${encodeQueryComponent(status)}" else ""
        return call<OrdersResponseDto>("GET", "/api/admin/orders$q").orders
    }

    suspend fun updateOrderStatus(orderId: Int, status: String) {
        call<SimpleOkDto>("PATCH", "/api/admin/orders/$orderId/status", json.encodeToString(OrderStatusReq(status)))
    }

    suspend fun deleteAdminOrder(orderId: Int) {
        call<SimpleOkDto>("DELETE", "/api/admin/orders/$orderId", "{}")
    }

    suspend fun getAdminUsers() =
        call<AdminUsersResponseDto>("GET", "/api/admin/users").users

    suspend fun updateAdminUser(userId: Int, isActive: Boolean? = null, role: String? = null) {
        call<SimpleOkDto>("PATCH", "/api/admin/users/$userId", json.encodeToString(AdminUserPatchReq(isActive, role)))
    }

    suspend fun revokeUserSessions(userId: Int): Int =
        call<SimpleOkDto>("POST", "/api/admin/users/$userId/revoke-sessions", "{}").revoked ?: 0

    suspend fun deleteAdminUser(userId: Int) {
        call<SimpleOkDto>("DELETE", "/api/admin/users/$userId", "{}")
    }

    suspend fun resetAdminDatabase() {
        call<SimpleOkDto>("POST", "/api/admin/reset-db", "{}")
    }

    suspend fun getAuditLogs(limit: Int = 60) =
        call<AuditLogsResponseDto>("GET", "/api/admin/audit-logs?limit=$limit").logs
}
