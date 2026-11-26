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

private val DEMO_CATEGORIES = listOf(
    CategoryDto(id = 1, name = "Hardware & Teclados", slug = "hardware-teclados", description = "Periféricos táctiles y cables que sobreviven a cualquier entrega final.", accentHex = "#3482FF", sortOrder = 1, productCount = 2),
    CategoryDto(id = 2, name = "Desk Setup", slug = "desk-setup", description = "Iluminación, deskmats y hubs para programar a las 2:00 AM.", accentHex = "#00BFA5", sortOrder = 2, productCount = 2),
    CategoryDto(id = 3, name = "Apparel & Drip", slug = "apparel-drip", description = "Prendas heavyweight y accesorios con humor seco de ingeniería.", accentHex = "#7C4DFF", sortOrder = 3, productCount = 1),
    CategoryDto(id = 4, name = "Edición Especial", slug = "edicion-04", description = "Curaduría limitada oficial de TBD.SUPPLY.", accentHex = "#FF6D00", sortOrder = 4, productCount = 1)
)

private val DEMO_PRODUCTS = listOf(
    ProductDto(id = 1, categoryId = 1, categoryName = "Hardware & Teclados", categorySlug = "hardware-teclados", founderTag = "Crew", name = "Teclado 65% \"Ctrl+Z Físico\"", slug = "teclado-65-ctrl-z", subtitle = "Switches táctiles silenciosos + tecla Esc color alerta", description = "Diseñado para cuando borras la base de datos por accidente. Cuerpo compacto de aluminio cepillado, keycaps PBT y retroceso emocional inmediato.", priceCents = 8900, compareAtCents = 10900, stock = 14, primaryImageUrl = "/media/prod-keyboard.png", accentHex = "#3482FF"),
    ProductDto(id = 2, categoryId = 1, categoryName = "Hardware & Teclados", categorySlug = "hardware-teclados", founderTag = "Crew", name = "Cable USB-C Blindado Anti-Parciales", slug = "cable-usbc-blindado", subtitle = "Trenzado de nylon 100W PD (no hace falsos contactos)", description = "Soporta datos a alta velocidad y cargas rápidas antes de entrar a clase.", priceCents = 1450, compareAtCents = 1900, stock = 38, primaryImageUrl = "/media/prod-cable.png", accentHex = "#2979FF"),
    ProductDto(id = 3, categoryId = 2, categoryName = "Desk Setup", categorySlug = "desk-setup", founderTag = "Crew", name = "Lámpara Barra \"2:00 AM Focus\"", slug = "lampara-2am-focus", subtitle = "Temperatura regulable 2700K-6500K sin reflejo en monitor", description = "Diseñada para sesiones nocturnas de código sin destruir tus ojos ni iluminar toda la habitación.", priceCents = 3900, compareAtCents = 4900, stock = 19, primaryImageUrl = "/media/prod-lamp.png", accentHex = "#00BFA5"),
    ProductDto(id = 4, categoryId = 2, categoryName = "Desk Setup", categorySlug = "desk-setup", founderTag = "Crew", name = "Deskmat Topográfico \"Dark Compile\"", slug = "deskmat-dark-compile", subtitle = "900x400mm microtejido impermeable con bordes cosidos", description = "Líneas topográficas minimalistas que disimulan manchas de café y mejoran el deslizamiento del mouse.", priceCents = 2499, compareAtCents = 3200, stock = 27, primaryImageUrl = "/media/prod-deskmat.png", accentHex = "#00ACC1"),
    ProductDto(id = 5, categoryId = 3, categoryName = "Apparel & Drip", categorySlug = "apparel-drip", founderTag = "Crew", name = "Hoodie Heavyweight \"404 Sleep Not Found\"", slug = "hoodie-404-sleep", subtitle = "Algodón 420gsm corte boxy fit color grafito", description = "Selección oficial TBD.SUPPLY. Suave por dentro, estructurado por fuera, ideal para aulas con aire acondicionado polar.", priceCents = 5400, compareAtCents = 6500, stock = 12, primaryImageUrl = "/media/prod-hoodie.png", accentHex = "#7C4DFF"),
    ProductDto(id = 6, categoryId = 4, categoryName = "Edición Especial", categorySlug = "edicion-04", founderTag = "Crew", name = "Termo Térmico \"Cold Brew Protocol\"", slug = "termo-cold-brew", subtitle = "Acero inoxidable doble pared 600ml (12h caliente / 24h frío)", description = "Edición recomendada: mantiene tu café frío intacto desde la primera hora hasta la última exposición.", priceCents = 2600, compareAtCents = 3100, stock = 20, primaryImageUrl = "/media/prod-thermos.png", accentHex = "#FF6D00")
)

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

    suspend fun getMe(): AuthResponseDto = try {
        val res = call<AuthResponseDto>("GET", "/api/auth/me")
        csrfToken = res.csrfToken
        res
    } catch (e: ApiException) {
        if (e.status == 404 || e.status == 0) {
            AuthResponseDto(user = null, csrfToken = "demo-csrf-token")
        } else throw e
    }

    suspend fun login(identifier: String, password: String): UserDto = try {
        val res = call<AuthResponseDto>(
            "POST",
            "/api/auth/login",
            json.encodeToString(LoginReq(identifier, password)),
        )
        csrfToken = res.csrfToken
        res.user ?: throw ApiException(500, "Respuesta de sesion invalida")
    } catch (e: ApiException) {
        if (e.status == 404 || e.status == 0) {
            throw ApiException(404, "Netlify es un servidor estático CDN (solo frontend). Para conectar la base de datos real con usuario admin, despliega el backend Node.js en Render.com o ejecuta 'iniciar-publico.bat' con Cloudflare Tunnel.")
        } else throw e
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

    suspend fun getCategories(): List<CategoryDto> = try {
        call<CategoriesResponseDto>("GET", "/api/categories").categories
    } catch (e: ApiException) {
        if (e.status == 404 || e.status == 0) DEMO_CATEGORIES else throw e
    }

    suspend fun getProducts(
        query: String = "",
        categorySlug: String = "",
        founder: String = "",
        sort: String = "newest",
    ): ProductsPageDto = try {
        val params = buildList {
            if (query.isNotBlank()) add("q=${encodeQueryComponent(query.trim())}")
            if (categorySlug.isNotBlank()) add("category=${encodeQueryComponent(categorySlug)}")
            if (founder.isNotBlank() && founder != "Todos") add("founder=${encodeQueryComponent(founder)}")
            if (sort.isNotBlank()) add("sort=${encodeQueryComponent(sort)}")
            add("limit=48")
        }.joinToString("&")
        call("GET", "/api/products?$params")
    } catch (e: ApiException) {
        if (e.status == 404 || e.status == 0) {
            val filtered = DEMO_PRODUCTS.filter { p ->
                (categorySlug.isBlank() || p.categorySlug == categorySlug) &&
                (query.isBlank() || p.name.contains(query, ignoreCase = true) || p.description.contains(query, ignoreCase = true))
            }
            ProductsPageDto(
                items = filtered,
                total = filtered.size,
                page = 1,
                limit = 48,
                hasMore = false,
                query = query,
                category = categorySlug.ifBlank { null },
                founder = founder.ifBlank { null },
                sort = sort
            )
        } else throw e
    }

    suspend fun getProductDetail(slug: String): ProductDto = try {
        call<ProductSingleResponseDto>("GET", "/api/products/${encodeQueryComponent(slug)}").product
    } catch (e: ApiException) {
        if (e.status == 404 || e.status == 0) {
            DEMO_PRODUCTS.firstOrNull { it.slug == slug } ?: DEMO_PRODUCTS.first()
        } else throw e
    }

    suspend fun getCart(): CartResponseDto = try {
        call("GET", "/api/cart")
    } catch (e: ApiException) {
        if (e.status == 404 || e.status == 0) {
            CartResponseDto(items = emptyList(), itemCount = 0, subtotalCents = 0)
        } else throw e
    }

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
