package nose.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import nose.model.AdminDashboardResponseDto
import nose.model.AdminUserDto
import nose.model.AuditLogDto
import nose.model.BrandConfig
import nose.model.CartItemDto
import nose.model.CartResponseDto
import nose.model.CategoryDto
import nose.model.OrderDto
import nose.model.OrderMessageDto
import nose.model.ProductDto
import nose.model.UserDto
import nose.network.AdminCategoryPayload
import nose.network.AdminProductPayload
import nose.network.ApiException
import nose.network.GuestCartMergeItem
import nose.network.StoreApi
import nose.network.platformInvalidateImageCache
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState

enum class AppTab(val label: String) {
    Store("Tienda"),
    Categories("Categorías"),
    Cart("Carrito"),
    Account("Cuenta"),
    Admin("Admin"),
}

@Stable
class StoreAppState(
    private val scope: CoroutineScope,
    val api: StoreApi = StoreApi(),
) {
    val snackbarHostState = SnackbarHostState()

    // Theme customization (live ThemeController)
    var colorMode by mutableIntStateOf(0) // 0=System, 1=Light, 2=Dark, 3=MonetSystem, 4=MonetLight, 5=MonetDark
    var keyColorIndex by mutableIntStateOf(0)
    var squircleEnabled by mutableStateOf(true)
    var glassEnabled by mutableStateOf(true)

    val keyColors: List<Pair<String, Color>> = listOf(
        "Azul HyperOS" to Color(0xFF3482FF),
        "Esmeralda" to Color(0xFF00BFA5),
        "Violeta 04" to Color(0xFF7C4DFF),
        "Ámbar" to Color(0xFFFF6D00),
        "Carmesí" to Color(0xFFE91E63),
    )

    // Navigation & overlays
    var currentTab by mutableStateOf(AppTab.Store)
    var selectedProductDetail by mutableStateOf<ProductDto?>(null)
    var isDetailLoading by mutableStateOf(false)
    var latestReceiptOrder by mutableStateOf<OrderDto?>(null)

    // Auth & User
    var user by mutableStateOf<UserDto?>(null)
    var authBusy by mutableStateOf(false)
    var orders by mutableStateOf<List<OrderDto>>(emptyList())
    var selectedOrderDetail by mutableStateOf<OrderDto?>(null)
    var orderChatMessages by mutableStateOf<List<OrderMessageDto>>(emptyList())
    var isOrderChatLoading by mutableStateOf(false)
    var isOrderChatSending by mutableStateOf(false)

    // Public catalog
    var isBootstrapping by mutableStateOf(true)
    var isCatalogLoading by mutableStateOf(false)
    var categories by mutableStateOf<List<CategoryDto>>(emptyList())
    var products by mutableStateOf<List<ProductDto>>(emptyList())
    var searchQuery by mutableStateOf("")
    var selectedCategorySlug by mutableStateOf("")
    var selectedFounder by mutableStateOf("Todos")
    var sortBy by mutableStateOf("newest")

    // Cart (guest in-memory + authenticated server cart)
    var guestCartItems by mutableStateOf<List<CartItemDto>>(emptyList())
    var serverCart by mutableStateOf(CartResponseDto())
    var isCheckoutBusy by mutableStateOf(false)

    // Admin state
    var adminTabIndex by mutableIntStateOf(0)
    var adminLoading by mutableStateOf(false)
    var adminDashboard by mutableStateOf<AdminDashboardResponseDto?>(null)
    var adminCategories by mutableStateOf<List<CategoryDto>>(emptyList())
    var adminProducts by mutableStateOf<List<ProductDto>>(emptyList())
    var adminOrders by mutableStateOf<List<OrderDto>>(emptyList())
    var adminOrdersFilter by mutableStateOf("all")
    var adminUsers by mutableStateOf<List<AdminUserDto>>(emptyList())
    var adminAuditLogs by mutableStateOf<List<AuditLogDto>>(emptyList())

    val activeCart: CartResponseDto
        get() = if (user != null) {
            serverCart
        } else {
            val totalQty = guestCartItems.sumOf { it.quantity }
            val subtotal = guestCartItems.sumOf { it.priceCents * it.quantity }
            CartResponseDto(items = guestCartItems, itemCount = totalQty, subtotalCents = subtotal)
        }

    fun showToast(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        }
    }

    fun bootstrap() {
        scope.launch {
            isBootstrapping = true
            runCatching {
                val me = api.getMe()
                user = me.user
            }
            refreshCatalogInternal()
            if (user != null) {
                refreshCartAndOrders()
            }
            isBootstrapping = false
        }
    }

    fun refreshCatalog() {
        scope.launch {
            refreshCatalogInternal()
        }
    }

    private suspend fun refreshCatalogInternal() {
        isCatalogLoading = true
        try {
            categories = api.getCategories()
            products = api.getProducts(
                query = searchQuery,
                categorySlug = selectedCategorySlug,
                founder = selectedFounder,
                sort = sortBy,
            ).items
        } catch (e: ApiException) {
            showToast(e.message)
        } catch (_: Throwable) {
            showToast("Error cargando el catálogo")
        } finally {
            isCatalogLoading = false
        }
    }

    fun openProductDetail(product: ProductDto) {
        selectedProductDetail = product
        scope.launch {
            isDetailLoading = true
            try {
                selectedProductDetail = api.getProductDetail(product.slug)
            } catch (_: Throwable) {
                // Keep summary data visible.
            } finally {
                isDetailLoading = false
            }
        }
    }

    fun filterByCategory(slug: String) {
        selectedCategorySlug = slug
        currentTab = AppTab.Store
        refreshCatalog()
    }

    fun filterByFounder(founder: String) {
        selectedFounder = founder
        currentTab = AppTab.Store
        refreshCatalog()
    }

    fun addToCart(product: ProductDto, quantityDelta: Int = 1) {
        if (product.stock <= 0) {
            showToast("Producto agotado temporalmente")
            return
        }
        val currentQty = activeCart.items.firstOrNull { it.productId == product.id }?.quantity ?: 0
        val nextQty = (currentQty + quantityDelta).coerceAtMost(product.stock).coerceAtMost(99)
        if (nextQty <= currentQty && currentQty >= product.stock) {
            showToast("Stock máximo disponible alcanzado (${product.stock})")
            return
        }
        setCartItemQuantity(product, nextQty, showFeedback = true)
    }

    fun setCartItemQuantity(product: ProductDto, quantity: Int, showFeedback: Boolean = false) {
        if (user == null) {
            val clamped = quantity.coerceIn(0, product.stock.coerceAtMost(99))
            val mutable = guestCartItems.toMutableList()
            val idx = mutable.indexOfFirst { it.productId == product.id }
            if (clamped == 0) {
                if (idx >= 0) mutable.removeAt(idx)
            } else {
                val entry = CartItemDto(
                    productId = product.id,
                    quantity = clamped,
                    name = product.name,
                    slug = product.slug,
                    priceCents = product.priceCents,
                    stock = product.stock,
                    founderTag = product.founderTag,
                    categoryName = product.categoryName,
                    primaryImageUrl = product.primaryImageUrl,
                    lineTotalCents = product.priceCents * clamped,
                )
                if (idx >= 0) mutable[idx] = entry else mutable.add(0, entry)
            }
            guestCartItems = mutable
            if (showFeedback && clamped > 0) showToast("${product.name} añadido al carrito")
            return
        }

        scope.launch {
            try {
                serverCart = api.setCartQuantity(product.id, quantity)
                if (showFeedback && quantity > 0) showToast("${product.name} añadido al carrito")
            } catch (e: ApiException) {
                if (e.status == 401) handleUnauthorized()
                showToast(e.message)
            }
        }
    }

    fun updateCartItemQuantityById(productId: Int, quantity: Int) {
        if (user == null) {
            val existing = guestCartItems.firstOrNull { it.productId == productId } ?: return
            val clamped = quantity.coerceIn(0, existing.stock.coerceAtMost(99))
            val mutable = guestCartItems.toMutableList()
            val idx = mutable.indexOfFirst { it.productId == productId }
            if (clamped == 0 && idx >= 0) {
                mutable.removeAt(idx)
            } else if (idx >= 0) {
                mutable[idx] = existing.copy(quantity = clamped, lineTotalCents = existing.priceCents * clamped)
            }
            guestCartItems = mutable
            return
        }

        scope.launch {
            try {
                serverCart = api.setCartQuantity(productId, quantity)
            } catch (e: ApiException) {
                if (e.status == 401) handleUnauthorized()
                showToast(e.message)
            }
        }
    }

    fun login(identifier: String, password: String, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            authBusy = true
            try {
                val loggedIn = api.login(identifier.trim(), password)
                user = loggedIn
                if (guestCartItems.isNotEmpty()) {
                    val toMerge = guestCartItems.map { GuestCartMergeItem(it.productId, it.quantity) }
                    serverCart = api.mergeGuestCart(toMerge)
                    guestCartItems = emptyList()
                }
                refreshCartAndOrders()
                showToast("Bienvenido, ${loggedIn.displayName}")
                onDone(true)
            } catch (e: ApiException) {
                showToast(e.message)
                onDone(false)
            } catch (t: Throwable) {
                showToast(t.message ?: "Error inesperado")
                onDone(false)
            } finally {
                authBusy = false
            }
        }
    }

    fun register(username: String, email: String, password: String, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            authBusy = true
            try {
                val created = api.register(username.trim(), email.trim(), password)
                user = created
                if (guestCartItems.isNotEmpty()) {
                    val toMerge = guestCartItems.map { GuestCartMergeItem(it.productId, it.quantity) }
                    serverCart = api.mergeGuestCart(toMerge)
                    guestCartItems = emptyList()
                }
                refreshCartAndOrders()
                showToast("Bienvenido, ${created.displayName}")
                onDone(true)
            } catch (e: ApiException) {
                showToast(e.message)
                onDone(false)
            } catch (t: Throwable) {
                showToast(t.message ?: "Error inesperado")
                onDone(false)
            } finally {
                authBusy = false
            }
        }
    }

    fun logout() {
        scope.launch {
            api.logout()
            clearAuthenticatedState()
            if (currentTab == AppTab.Admin) currentTab = AppTab.Store
            showToast("Sesión cerrada")
        }
    }

    fun logoutAll() {
        scope.launch {
            try {
                api.logoutAll()
                clearAuthenticatedState()
                if (currentTab == AppTab.Admin) currentTab = AppTab.Store
                showToast("Todas las sesiones han sido revocadas")
            } catch (e: ApiException) {
                showToast(e.message)
            }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, onSuccess: () -> Unit) {
        scope.launch {
            authBusy = true
            try {
                api.changePassword(currentPassword, newPassword)
                showToast("Contraseña actualizada y sesiones rotadas")
                onSuccess()
            } catch (e: ApiException) {
                showToast(e.message)
            } finally {
                authBusy = false
            }
        }
    }

    fun checkout(shippingName: String, shippingNote: String) {
        if (user == null) {
            showToast("Inicia sesión para finalizar tu pedido")
            currentTab = AppTab.Account
            return
        }
        scope.launch {
            isCheckoutBusy = true
            try {
                val order = api.checkout(shippingName.trim(), shippingNote.trim())
                latestReceiptOrder = order
                serverCart = CartResponseDto()
                orders = api.getMyOrders()
                refreshCatalogInternal()
                showToast("Pedido ${order.orderCode} confirmado")
            } catch (e: ApiException) {
                if (e.status == 401) handleUnauthorized()
                showToast(e.message)
            } catch (t: Throwable) {
                showToast(t.message ?: "Error procesando el pedido")
            } finally {
                isCheckoutBusy = false
            }
        }
    }

    fun openOrderChat(order: OrderDto) {
        selectedOrderDetail = order
        orderChatMessages = order.messages
        openOrderDetail(order.orderCode)
    }

    fun openOrderDetail(orderCode: String) {
        scope.launch {
            isOrderChatLoading = true
            try {
                val detail = api.getOrderDetail(orderCode)
                selectedOrderDetail = detail
                orderChatMessages = detail.messages
            } catch (e: ApiException) {
                showToast(e.message)
            } catch (t: Throwable) {
                showToast(t.message ?: "Error cargando detalle")
            } finally {
                isOrderChatLoading = false
            }
        }
    }

    fun closeOrderDetail() {
        selectedOrderDetail = null
        orderChatMessages = emptyList()
        isOrderChatLoading = false
    }

    fun refreshOrderChat(orderCode: String, silent: Boolean = true) {
        scope.launch {
            try {
                val freshMessages = api.getOrderMessages(orderCode)
                if (selectedOrderDetail?.orderCode == orderCode) {
                    orderChatMessages = freshMessages
                }
            } catch (e: ApiException) {
                if (!silent) showToast(e.message)
            } catch (_: Throwable) {
                // Keep polling resilient.
            }
        }
    }

    fun sendOrderChatMessage(orderCode: String, text: String, onSent: () -> Unit) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        scope.launch {
            isOrderChatSending = true
            try {
                val sent = api.sendOrderMessage(orderCode, clean)
                orderChatMessages = orderChatMessages + sent
                onSent()
                runCatching { orders = api.getMyOrders() }
                if (user?.role == "admin") runCatching { adminOrders = api.getAdminOrders(adminOrdersFilter) }
            } catch (e: ApiException) {
                if (e.status == 401) handleUnauthorized()
                showToast(e.message)
            } catch (t: Throwable) {
                showToast(t.message ?: "No se pudo enviar el mensaje")
            } finally {
                isOrderChatSending = false
            }
        }
    }

    fun sendOrderChatImage(orderCode: String, caption: String, onSent: () -> Unit) {
        scope.launch {
            isOrderChatSending = true
            try {
                val sent = api.pickAndSendOrderImage(orderCode, caption.trim()) ?: return@launch
                orderChatMessages = orderChatMessages + sent
                onSent()
                showToast("Imagen enviada al chat")
                runCatching { orders = api.getMyOrders() }
                if (user?.role == "admin") runCatching { adminOrders = api.getAdminOrders(adminOrdersFilter) }
            } catch (e: ApiException) {
                if (e.status == 401) handleUnauthorized()
                showToast(e.message)
            } catch (t: Throwable) {
                showToast(t.message ?: "No se pudo enviar la imagen")
            } finally {
                isOrderChatSending = false
            }
        }
    }

    private suspend fun refreshCartAndOrders() {
        runCatching { serverCart = api.getCart() }
        runCatching { orders = api.getMyOrders() }
    }

    private fun handleUnauthorized() {
        clearAuthenticatedState()
        if (currentTab == AppTab.Admin) currentTab = AppTab.Store
    }

    private fun clearAuthenticatedState() {
        user = null
        serverCart = CartResponseDto()
        orders = emptyList()
        selectedOrderDetail = null
        orderChatMessages = emptyList()
        adminDashboard = null
        adminCategories = emptyList()
        adminProducts = emptyList()
        adminOrders = emptyList()
        adminUsers = emptyList()
        adminAuditLogs = emptyList()
    }

    // Admin actions
    fun loadAdminAll() {
        if (user?.role != "admin") return
        scope.launch {
            adminLoading = true
            try {
                adminDashboard = api.getAdminDashboard()
                adminCategories = api.getAdminCategories()
                adminProducts = api.getAdminProducts()
                adminOrders = api.getAdminOrders(adminOrdersFilter)
                adminUsers = api.getAdminUsers()
                adminAuditLogs = api.getAuditLogs()
            } catch (e: ApiException) {
                if (e.status == 401 || e.status == 403) handleUnauthorized()
                showToast(e.message)
            } finally {
                adminLoading = false
            }
        }
    }

    fun saveCategory(editId: Int?, payload: AdminCategoryPayload, onSuccess: () -> Unit) {
        scope.launch {
            try {
                if (editId == null) api.createCategory(payload) else api.updateCategory(editId, payload)
                showToast(if (editId == null) "Categoría creada" else "Categoría actualizada")
                loadAdminAll()
                refreshCatalogInternal()
                onSuccess()
            } catch (e: ApiException) {
                showToast(e.message)
            }
        }
    }

    fun deleteCategory(id: Int) {
        scope.launch {
            try {
                api.deleteCategory(id)
                showToast("Categoría eliminada")
                loadAdminAll()
                refreshCatalogInternal()
            } catch (e: ApiException) {
                showToast(e.message)
            }
        }
    }

    fun saveProduct(editId: Int?, payload: AdminProductPayload, onSuccess: (ProductDto) -> Unit) {
        scope.launch {
            try {
                val saved = if (editId == null) api.createProduct(payload) else api.updateProduct(editId, payload)
                showToast(if (editId == null) "Producto creado" else "Producto actualizado")
                loadAdminAll()
                refreshCatalogInternal()
                onSuccess(saved)
            } catch (e: ApiException) {
                showToast(e.message)
            }
        }
    }

    fun adjustProductStock(productId: Int, nextStock: Int) {
        val clamped = nextStock.coerceAtLeast(0)
        scope.launch {
            try {
                val updated = api.updateProductStock(productId, clamped)
                showToast("Stock de ${updated.name}: ${updated.stock}")
                loadAdminAll()
                refreshCatalogInternal()
            } catch (e: ApiException) {
                showToast(e.message)
            }
        }
    }

    fun deleteProduct(id: Int) {
        scope.launch {
            try {
                api.deleteProduct(id)
                showToast("Producto eliminado")
                loadAdminAll()
                refreshCatalogInternal()
            } catch (e: ApiException) {
                showToast(e.message)
            }
        }
    }

    fun uploadImageForProduct(productId: Int, isPrimary: Boolean, onUpdated: () -> Unit = {}) {
        scope.launch {
            try {
                val uploaded = api.pickAndUploadProductImage(productId, isPrimary) ?: return@launch
                platformInvalidateImageCache(uploaded.image.url)
                showToast("Imagen subida y validada")
                loadAdminAll()
                refreshCatalogInternal()
                onUpdated()
            } catch (e: ApiException) {
                showToast(e.message)
            }
        }
    }

    fun setPrimaryProductImage(mediaId: Int) {
        scope.launch {
            try {
                api.setPrimaryMedia(mediaId)
                showToast("Imagen principal actualizada")
                loadAdminAll()
                refreshCatalogInternal()
            } catch (e: ApiException) {
                showToast(e.message)
            }
        }
    }

    fun deleteProductImage(mediaId: Int, url: String) {
        scope.launch {
            try {
                api.deleteMedia(mediaId)
                platformInvalidateImageCache(url)
                showToast("Imagen eliminada")
                loadAdminAll()
                refreshCatalogInternal()
            } catch (e: ApiException) {
                showToast(e.message)
            }
        }
    }

    fun setAdminOrderFilter(status: String) {
        adminOrdersFilter = status
        scope.launch {
            try {
                adminOrders = api.getAdminOrders(status)
            } catch (e: ApiException) {
                showToast(e.message)
            }
        }
    }

    fun updateOrderStatus(orderId: Int, nextStatus: String) {
        scope.launch {
            try {
                api.updateOrderStatus(orderId, nextStatus)
                if (selectedOrderDetail?.id == orderId) {
                    selectedOrderDetail = selectedOrderDetail?.copy(status = nextStatus)
                }
                showToast("Estado actualizado: ${BrandConfig.orderStatusLabel(nextStatus)}")
                loadAdminAll()
                refreshCatalogInternal()
            } catch (e: ApiException) {
                showToast(e.message)
            }
        }
    }

    fun toggleUserActive(targetUser: AdminUserDto) {
        scope.launch {
            try {
                api.updateAdminUser(targetUser.id, isActive = targetUser.isActive != 1)
                showToast(if (targetUser.isActive == 1) "Usuario suspendido" else "Usuario reactivado")
                loadAdminAll()
            } catch (e: ApiException) {
                showToast(e.message)
            }
        }
    }

    fun toggleUserRole(targetUser: AdminUserDto) {
        val nextRole = if (targetUser.role == "admin") "customer" else "admin"
        scope.launch {
            try {
                api.updateAdminUser(targetUser.id, role = nextRole)
                showToast("Rol cambiado a $nextRole")
                loadAdminAll()
            } catch (e: ApiException) {
                showToast(e.message)
            }
        }
    }

    fun revokeUserSessions(userId: Int) {
        scope.launch {
            try {
                val revoked = api.revokeUserSessions(userId)
                showToast("Sesiones revocadas: $revoked")
                loadAdminAll()
            } catch (e: ApiException) {
                showToast(e.message)
            }
        }
    }

    fun deleteOrder(orderId: Int) {
        scope.launch {
            try {
                api.deleteAdminOrder(orderId)
                if (selectedOrderDetail?.id == orderId) {
                    closeOrderDetail()
                }
                showToast("Orden eliminada")
                loadAdminAll()
                runCatching { orders = api.getMyOrders() }
            } catch (e: ApiException) {
                showToast(e.message)
            }
        }
    }

    fun deleteUser(userId: Int) {
        scope.launch {
            try {
                api.deleteAdminUser(userId)
                showToast("Usuario eliminado")
                loadAdminAll()
            } catch (e: ApiException) {
                showToast(e.message)
            }
        }
    }

    fun resetDatabase(onDone: () -> Unit = {}) {
        scope.launch {
            try {
                api.resetAdminDatabase()
                selectedCategorySlug = ""
                selectedFounder = "Todos"
                searchQuery = ""
                closeOrderDetail()
                showToast("Base de datos limpia (lista para crear desde cero)")
                loadAdminAll()
                refreshCatalogInternal()
                refreshCartAndOrders()
                onDone()
            } catch (e: ApiException) {
                showToast(e.message)
            }
        }
    }
}
