package nose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nose.model.AuditLogDto
import nose.model.BrandConfig
import nose.model.CategoryDto
import nose.model.ProductDto
import nose.model.formatUsd
import nose.model.parseUsdToCents
import nose.network.AdminCategoryPayload
import nose.network.AdminProductPayload
import nose.network.platformFormatDate
import nose.state.StoreAppState
import nose.ui.components.FounderTagPill
import nose.ui.components.OrderStatusPill
import nose.ui.components.RemoteProductImage
import nose.ui.components.TagPill
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun AdminPanelScreen(
    state: StoreAppState,
    contentPadding: PaddingValues,
) {
    if (state.user?.role != "admin") {
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text("Acceso restringido a administradores", style = MiuixTheme.textStyles.headline1)
        }
        return
    }

    LaunchedEffect(state.user?.id) {
        if (state.adminDashboard == null) {
            state.loadAdminAll()
        }
    }

    var editingProductId by remember { mutableStateOf<Int?>(null) }
    var creatingProduct by remember { mutableStateOf(false) }
    var managingImagesForProductId by remember { mutableStateOf<Int?>(null) }

    var editingCategory by remember { mutableStateOf<CategoryDto?>(null) }
    var creatingCategory by remember { mutableStateOf(false) }
    var showAuditSheet by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val editingProduct = editingProductId?.let { pid ->
        state.adminProducts.firstOrNull { it.id == pid }
    }

    val adminTabs = listOf("Resumen", "Productos", "Categorías", "Órdenes", "Usuarios")
    val selectedTab = state.adminTabIndex.coerceIn(0, adminTabs.lastIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding() + 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Panel de Administración", style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Bold)
                Text(
                    text = "Crea, edita y borra productos, categorías, pedidos y usuarios fácilmente",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            IconButton(onClick = { state.loadAdminAll() }) {
                Icon(MiuixIcons.Refresh, contentDescription = "Recargar")
            }
        }

        Spacer(Modifier.height(10.dp))

        TabRow(
            tabs = adminTabs,
            selectedTabIndex = selectedTab,
            onTabSelected = { state.adminTabIndex = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        if (state.adminLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
        }

        val listPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        )

        when (selectedTab) {
            0 -> AdminDashboardTab(
                state = state,
                padding = listPadding,
                onQuickCreateProduct = { creatingProduct = true },
                onQuickCreateCategory = { creatingCategory = true },
                onRequestResetDb = { showResetConfirm = true },
                onOpenAudit = { showAuditSheet = true },
            )
            1 -> AdminProductsTab(
                state = state,
                padding = listPadding,
                onCreate = { creatingProduct = true },
                onEdit = { editingProductId = it.id },
                onManageImages = { managingImagesForProductId = it.id },
            )
            2 -> AdminCategoriesTab(
                state = state,
                padding = listPadding,
                onCreate = { creatingCategory = true },
                onEdit = { editingCategory = it },
            )
            3 -> AdminOrdersTab(state, listPadding)
            4 -> AdminUsersTab(state, listPadding)
        }
    }

    if (creatingProduct || editingProduct != null) {
        ProductEditorSheet(
            initial = editingProduct,
            categories = state.adminCategories,
            onInvalid = { msg -> state.showToast(msg) },
            onUploadImage = { prodId, isPrimary -> state.uploadImageForProduct(prodId, isPrimary) },
            onSetPrimaryImage = { mediaId -> state.setPrimaryProductImage(mediaId) },
            onDeleteImage = { mediaId, url -> state.deleteProductImage(mediaId, url) },
            onDismiss = {
                creatingProduct = false
                editingProductId = null
            },
            onSave = { editId, payload, openPhotoAfter ->
                state.saveProduct(editId, payload) { savedProd ->
                    creatingProduct = false
                    editingProductId = null
                    if (openPhotoAfter) {
                        state.uploadImageForProduct(savedProd.id, isPrimary = true)
                    }
                }
            },
        )
    }

    val mediaProd = managingImagesForProductId?.let { pid ->
        state.adminProducts.firstOrNull { it.id == pid }
    }
    if (mediaProd != null) {
        ProductImagesSheet(
            product = mediaProd,
            onDismiss = { managingImagesForProductId = null },
            onUpload = { isPrimary -> state.uploadImageForProduct(mediaProd.id, isPrimary) },
            onSetPrimary = { mediaId -> state.setPrimaryProductImage(mediaId) },
            onDeleteMedia = { mediaId, url -> state.deleteProductImage(mediaId, url) },
        )
    }

    if (creatingCategory || editingCategory != null) {
        CategoryEditorDialog(
            initial = editingCategory,
            onInvalid = { msg -> state.showToast(msg) },
            onDismiss = {
                creatingCategory = false
                editingCategory = null
            },
            onSave = { editId, payload ->
                state.saveCategory(editId, payload) {
                    creatingCategory = false
                    editingCategory = null
                }
            },
        )
    }

    if (showAuditSheet) {
        AuditHistorySheet(
            logs = state.adminAuditLogs,
            onDismiss = { showAuditSheet = false },
        )
    }

    OverlayDialog(
        show = showResetConfirm,
        title = "Vaciar Base de Datos",
        summary = "Se borrarán todos los productos, categorías, pedidos, mensajes y usuarios clientes (solo quedará tu cuenta Admin) para que empieces desde cero.",
        onDismissRequest = { showResetConfirm = false },
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(
                text = "Cancelar",
                modifier = Modifier.weight(1f),
                onClick = { showResetConfirm = false },
            )
            TextButton(
                text = "Sí, Borrar Todo",
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
                onClick = {
                    showResetConfirm = false
                    state.resetDatabase()
                },
            )
        }
    }
}

private fun friendlyAuditAction(action: String): String = when (action) {
    "BOOTSTRAP_ADMIN" -> "Cuenta administrador iniciada"
    "SEED_CATALOG" -> "Catálogo inicial cargado"
    "REGISTER" -> "Nuevo usuario registrado"
    "LOGIN_SUCCESS" -> "Inicio de sesión"
    "LOGIN_FAILED" -> "Intento de acceso fallido"
    "LOGIN_LOCKED" -> "Cuenta bloqueada temporalmente"
    "LOGOUT" -> "Sesión cerrada"
    "LOGOUT_ALL" -> "Todas las sesiones cerradas"
    "PASSWORD_CHANGED" -> "Contraseña actualizada"
    "ORDER_CREATED" -> "Nuevo pedido realizado"
    "ORDER_MESSAGE_SENT" -> "Mensaje enviado en pedido"
    "ADMIN_ORDER_STATUS" -> "Estado de pedido cambiado"
    "ADMIN_ORDER_DELETE" -> "Pedido eliminado"
    "ADMIN_PRODUCT_CREATE" -> "Producto creado"
    "ADMIN_PRODUCT_UPDATE" -> "Producto actualizado"
    "ADMIN_PRODUCT_DELETE" -> "Producto eliminado"
    "ADMIN_PRODUCT_ARCHIVE" -> "Producto archivado"
    "ADMIN_MEDIA_UPLOAD" -> "Foto de producto subida"
    "ADMIN_MEDIA_DELETE" -> "Foto de producto eliminada"
    "ADMIN_CATEGORY_CREATE" -> "Categoría creada"
    "ADMIN_CATEGORY_UPDATE" -> "Categoría actualizada"
    "ADMIN_CATEGORY_DELETE" -> "Categoría eliminada"
    "ADMIN_USER_UPDATE" -> "Permisos de usuario actualizados"
    "ADMIN_USER_DELETE" -> "Usuario eliminado"
    "ADMIN_REVOKE_SESSIONS" -> "Sesiones de usuario cerradas"
    "ADMIN_RESET_DB" -> "Base de datos vaciada"
    else -> action
}

@Composable
private fun AdminDashboardTab(
    state: StoreAppState,
    padding: PaddingValues,
    onQuickCreateProduct: () -> Unit,
    onQuickCreateCategory: () -> Unit,
    onRequestResetDb: () -> Unit,
    onOpenAudit: () -> Unit,
) {
    val dash = state.adminDashboard
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "+ Crear Producto",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                    onClick = onQuickCreateProduct,
                )
                TextButton(
                    text = "+ Crear Categoría",
                    modifier = Modifier.weight(1f),
                    onClick = onQuickCreateCategory,
                )
            }
        }

        if (dash != null) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(
                        label = "Ingresos (USD)",
                        value = formatUsd(dash.summary.revenueCents.toInt()),
                        accent = Color(0xFF00BFA5),
                        onClick = { state.adminTabIndex = 3 },
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = "Órdenes / Pendientes",
                        value = "${dash.summary.totalOrders} / ${dash.summary.pendingOrders}",
                        accent = Color(0xFF3482FF),
                        onClick = { state.adminTabIndex = 3 },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(
                        label = "Productos / Stock Bajo",
                        value = "${dash.summary.activeProducts} / ${dash.summary.lowStockCount}",
                        accent = Color(0xFFFF6D00),
                        onClick = { state.adminTabIndex = 1 },
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = "Usuarios / Sesiones",
                        value = "${dash.summary.activeUsers} / ${dash.summary.activeSessions}",
                        accent = Color(0xFF7C4DFF),
                        onClick = { state.adminTabIndex = 4 },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (dash.lowStockProducts.isNotEmpty()) {
                item {
                    SmallTitle(
                        text = "Alertas de Stock Bajo (<= 5)",
                        insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
                items(dash.lowStockProducts) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MiuixTheme.textStyles.headline2, fontWeight = FontWeight.SemiBold)
                                Text(formatUsd(item.priceCents), style = MiuixTheme.textStyles.footnote1)
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TagPill(
                                    text = "Stock: ${item.stock}",
                                    containerColor = MiuixTheme.colorScheme.errorContainer,
                                    contentColor = MiuixTheme.colorScheme.error,
                                )
                                TextButton(
                                    text = "+5",
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                    onClick = { state.adjustProductStock(item.id, item.stock + 5) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Limpiar Base de Datos", style = MiuixTheme.textStyles.headline2, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Borra productos, categorías, pedidos y usuarios de prueba.",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        TextButton(
                            text = "Vaciar Todo",
                            onClick = onRequestResetDb,
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallTitle(
                        text = "Actividad Reciente",
                        insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    )
                    TextButton(
                        text = "Ver historial",
                        onClick = onOpenAudit,
                    )
                }
            }
            items(dash.recentAuditLogs.take(8)) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = friendlyAuditAction(log.action),
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Por: ${log.actorUsername ?: "sistema"} • ${log.targetType} ${log.targetId}",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Text(
                            text = platformFormatDate(log.createdAt),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = value,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
            )
        }
    }
}

@Composable
private fun AdminProductsTab(
    state: StoreAppState,
    padding: PaddingValues,
    onCreate: () -> Unit,
    onEdit: (ProductDto) -> Unit,
    onManageImages: (ProductDto) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TextButton(
                text = "+ Nuevo Producto (con Categoría y Foto)",
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth(),
                onClick = onCreate,
            )
        }
        if (state.adminProducts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(18.dp),
                ) {
                    Text(
                        text = "No hay productos todavía. Toca '+ Nuevo Producto' arriba: puedes escribir cualquier categoría (ej. Enrique, Leonel, Aníbal, lo que quieras) y se creará sola.",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        } else {
            items(state.adminProducts, key = { it.id }) { prod ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RemoteProductImage(
                                url = prod.primaryImageUrl,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clickable { onManageImages(prod) },
                                cornerRadius = 12.dp,
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(prod.name, style = MiuixTheme.textStyles.headline2, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${formatUsd(prod.priceCents)} • Stock: ${prod.stock}",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FounderTagPill(prod.categoryName.ifBlank { prod.founderTag })
                                    TagPill(
                                        text = if (prod.isActive == 1) "Visible" else "Oculto",
                                        containerColor = if (prod.isActive == 1) Color(0xFF00BFA5).copy(alpha = 0.14f) else MiuixTheme.colorScheme.errorContainer,
                                        contentColor = if (prod.isActive == 1) Color(0xFF00BFA5) else MiuixTheme.colorScheme.error,
                                    )
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                text = "Editar",
                                modifier = Modifier.weight(1f),
                                onClick = { onEdit(prod) },
                            )
                            TextButton(
                                text = if (prod.images.isEmpty()) "Subir Foto" else "Fotos (${prod.images.size})",
                                colors = if (prod.images.isEmpty()) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (prod.images.isEmpty()) {
                                        state.uploadImageForProduct(prod.id, isPrimary = true)
                                    } else {
                                        onManageImages(prod)
                                    }
                                },
                            )
                            TextButton(
                                text = "-1",
                                enabled = prod.stock > 0,
                                onClick = { state.adjustProductStock(prod.id, prod.stock - 1) },
                            )
                            TextButton(
                                text = "+1",
                                onClick = { state.adjustProductStock(prod.id, prod.stock + 1) },
                            )
                            IconButton(
                                onClick = { state.deleteProduct(prod.id) },
                                backgroundColor = MiuixTheme.colorScheme.errorContainer,
                            ) {
                                Icon(MiuixIcons.Delete, contentDescription = "Borrar producto", tint = MiuixTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminCategoriesTab(
    state: StoreAppState,
    padding: PaddingValues,
    onCreate: () -> Unit,
    onEdit: (CategoryDto) -> Unit,
) {
    val quickNames = listOf("Enrique", "Edgarvis", "Leonel", "Aníbal")
    val existingLower = remember(state.adminCategories) {
        state.adminCategories.map { it.name.lowercase() }.toSet()
    }
    val missingQuick = quickNames.filter { it.lowercase() !in existingLower }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TextButton(
                text = "+ Nueva Categoría",
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth(),
                onClick = onCreate,
            )
        }

        if (missingQuick.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Crear categoría rápida en 1 toque:",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(missingQuick) { qName ->
                                val hex = when (qName) {
                                    "Enrique" -> "#3482FF"
                                    "Edgarvis" -> "#00BFA5"
                                    "Leonel" -> "#7C4DFF"
                                    else -> "#FF6D00"
                                }
                                TextButton(
                                    text = "+ $qName",
                                    onClick = {
                                        state.saveCategory(
                                            editId = null,
                                            payload = AdminCategoryPayload(name = qName, accentHex = hex),
                                            onSuccess = {},
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.adminCategories.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(18.dp),
                ) {
                    Text(
                        text = "No hay categorías creadas. Usa los botones rápidos de arriba o toca '+ Nueva Categoría'.",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        } else {
            items(state.adminCategories, key = { it.id }) { cat ->
                val accent = remember(cat.accentHex) { parseHexColorOrDefault(cat.accentHex, Color(0xFF3482FF)) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(accent, CircleShape),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(cat.name, style = MiuixTheme.textStyles.headline2, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (cat.description.isNotBlank()) {
                                    "${cat.productCount} productos • ${cat.description}"
                                } else {
                                    "${cat.productCount} productos"
                                },
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        TextButton(
                            text = "Editar",
                            onClick = { onEdit(cat) },
                        )
                        IconButton(
                            onClick = { state.deleteCategory(cat.id) },
                            backgroundColor = MiuixTheme.colorScheme.errorContainer,
                        ) {
                            Icon(MiuixIcons.Delete, contentDescription = "Borrar categoría", tint = MiuixTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminOrdersTab(state: StoreAppState, padding: PaddingValues) {
    val filters = listOf("all" to "Todas") + BrandConfig.ORDER_STATUSES.map { it to BrandConfig.orderStatusLabel(it) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters) { (key, label) ->
                    val selected = state.adminOrdersFilter == key
                    TextButton(
                        text = label,
                        colors = if (selected) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                        onClick = { state.setAdminOrderFilter(key) },
                    )
                }
            }
        }
        if (state.adminOrders.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(18.dp),
                ) {
                    Text(
                        text = "No hay pedidos en este filtro.",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        } else {
            items(state.adminOrders, key = { it.id }) { ord ->
                val currentIdx = BrandConfig.ORDER_STATUSES.indexOf(ord.status).coerceAtLeast(0)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ord.orderCode, style = MiuixTheme.textStyles.headline2, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "@${ord.username} • ${ord.shippingName}",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                if (ord.shippingNote.isNotBlank()) {
                                    Text(
                                        text = "Nota: ${ord.shippingNote}",
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = formatUsd(ord.totalCents),
                                    style = MiuixTheme.textStyles.headline2,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.primary,
                                )
                                OrderStatusPill(ord.status)
                            }
                        }

                        ord.items.forEach { item ->
                            Text(
                                text = "• ${item.quantity}x ${item.productName} (${formatUsd(item.lineTotalCents)})",
                                style = MiuixTheme.textStyles.footnote1,
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                text = if (ord.messageCount > 0) {
                                    "Chat / Enviar Imagen (${ord.messageCount})"
                                } else {
                                    "Abrir Chat / Enviar Imagen"
                                },
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                modifier = Modifier.weight(1f),
                                onClick = { state.openOrderChat(ord) },
                            )
                            IconButton(
                                onClick = { state.deleteOrder(ord.id) },
                                backgroundColor = MiuixTheme.colorScheme.errorContainer,
                            ) {
                                Icon(MiuixIcons.Delete, contentDescription = "Borrar orden", tint = MiuixTheme.colorScheme.error)
                            }
                        }
                    }
                    HorizontalDivider()
                    OverlayDropdownPreference(
                        title = "Estado",
                        summary = "Cambiar a Cancelado devuelve el stock automáticamente",
                        items = BrandConfig.ORDER_STATUSES.map { BrandConfig.orderStatusLabel(it) },
                        selectedIndex = currentIdx,
                        onSelectedIndexChange = { nextIdx ->
                            state.updateOrderStatus(ord.id, BrandConfig.ORDER_STATUSES[nextIdx])
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminUsersTab(state: StoreAppState, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.adminUsers, key = { it.id }) { u ->
            val isSelf = state.user?.id == u.id
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp,
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${u.displayName} (@${u.username})", style = MiuixTheme.textStyles.headline2, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${u.email} • Sesiones: ${u.activeSessions}",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        TagPill(
                            text = if (u.role == "admin") "ADMIN" else "CLIENTE",
                            containerColor = if (u.role == "admin") MiuixTheme.colorScheme.primary.copy(alpha = 0.16f) else MiuixTheme.colorScheme.secondaryVariant,
                            contentColor = if (u.role == "admin") MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSecondaryVariant,
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            text = if (u.isActive == 1) "Suspender" else "Reactivar",
                            enabled = !isSelf,
                            modifier = Modifier.weight(1f),
                            onClick = { state.toggleUserActive(u) },
                        )
                        TextButton(
                            text = if (u.role == "admin") "Quitar Admin" else "Hacer Admin",
                            enabled = !isSelf,
                            modifier = Modifier.weight(1f),
                            onClick = { state.toggleUserRole(u) },
                        )
                        if (!isSelf) {
                            IconButton(
                                onClick = { state.deleteUser(u.id) },
                                backgroundColor = MiuixTheme.colorScheme.errorContainer,
                            ) {
                                Icon(MiuixIcons.Delete, contentDescription = "Borrar usuario", tint = MiuixTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditHistorySheet(
    logs: List<AuditLogDto>,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = true,
        title = "Historial completo (${logs.size})",
        sheetMaxWidth = 600.dp,
        onDismissRequest = onDismiss,
        endAction = {
            IconButton(onClick = onDismiss) {
                Icon(MiuixIcons.Basic.Close, contentDescription = "Cerrar")
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(logs, key = { it.id }) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(friendlyAuditAction(log.action), style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Bold)
                            Text(
                                text = platformFormatDate(log.createdAt),
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Text(
                            text = "Por: ${log.actorUsername ?: "sistema"} • ${log.targetType}: ${log.targetId}",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductEditorSheet(
    initial: ProductDto?,
    categories: List<CategoryDto>,
    onInvalid: (String) -> Unit,
    onUploadImage: (Int, Boolean) -> Unit,
    onSetPrimaryImage: (Int) -> Unit,
    onDeleteImage: (Int, String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Int?, AdminProductPayload, Boolean) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var categoryName by remember(initial?.id) {
        mutableStateOf(
            initial?.categoryName?.ifBlank { null }
                ?: categories.firstOrNull()?.name
                ?: "Enrique",
        )
    }
    var description by remember(initial?.id) { mutableStateOf(initial?.description ?: "") }
    var priceUsd by remember(initial?.id) {
        mutableStateOf(initial?.let { (it.priceCents / 100.0).toString() } ?: "10.00")
    }
    var stockStr by remember(initial?.id) { mutableStateOf((initial?.stock ?: 10).toString()) }
    var isActive by remember(initial?.id) { mutableStateOf((initial?.isActive ?: 1) == 1) }

    val quickCategorySuggestions = remember(categories) {
        val base = listOf("Enrique", "Edgarvis", "Leonel", "Aníbal", "General")
        val existing = categories.map { it.name }
        (existing + base).distinctBy { it.lowercase() }
    }

    fun submit(openPhotoAfter: Boolean) {
        val cleanName = name.trim()
        if (cleanName.length < 2) {
            onInvalid("Escribe un nombre para el producto")
            return
        }
        val cleanCat = categoryName.trim()
        if (cleanCat.length < 2) {
            onInvalid("Escribe o selecciona una categoría (ej. Enrique, Leonel, Aníbal)")
            return
        }
        val priceCents = parseUsdToCents(priceUsd)
        if (priceCents == null || priceCents <= 0) {
            onInvalid("Ingresa un precio en USD válido (ej. 10.00)")
            return
        }
        val stock = stockStr.trim().toIntOrNull()
        if (stock == null || stock < 0) {
            onInvalid("Ingresa un stock válido (0 o más)")
            return
        }
        onSave(
            initial?.id,
            AdminProductPayload(
                categoryName = cleanCat,
                name = cleanName,
                description = description.trim(),
                priceCents = priceCents,
                stock = stock,
                isActive = isActive,
            ),
            openPhotoAfter,
        )
    }

    OverlayBottomSheet(
        show = true,
        title = if (initial == null) "Nuevo Producto" else "Editar Producto",
        sheetMaxWidth = 580.dp,
        onDismissRequest = onDismiss,
        endAction = {
            IconButton(onClick = onDismiss) {
                Icon(MiuixIcons.Basic.Close, contentDescription = "Cerrar")
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = "Nombre del producto",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            TextField(
                value = categoryName,
                onValueChange = { categoryName = it },
                label = "Categoría (escribe cualquiera o toca una abajo)",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quickCategorySuggestions) { suggestion ->
                    val selected = categoryName.trim().equals(suggestion, ignoreCase = true)
                    TextButton(
                        text = suggestion,
                        colors = if (selected) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                        onClick = { categoryName = suggestion },
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = priceUsd,
                    onValueChange = { priceUsd = it },
                    label = "Precio USD ($)",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                TextField(
                    value = stockStr,
                    onValueChange = { stockStr = it },
                    label = "Stock",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.8f),
                )
            }

            TextField(
                value = description,
                onValueChange = { description = it },
                label = "Descripción (opcional)",
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            SwitchPreference(
                title = "Visible en tienda",
                checked = isActive,
                onCheckedChange = { isActive = it },
            )

            if (initial != null) {
                HorizontalDivider()
                Text(
                    text = "Fotos del producto (${initial.images.size})",
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.Bold,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = "Subir Foto Principal",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                        onClick = { onUploadImage(initial.id, true) },
                    )
                    TextButton(
                        text = "+ Foto Extra",
                        modifier = Modifier.weight(1f),
                        onClick = { onUploadImage(initial.id, false) },
                    )
                }
                initial.images.forEach { img ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RemoteProductImage(
                            url = img.url,
                            modifier = Modifier.size(56.dp),
                            cornerRadius = 10.dp,
                        )
                        Text(
                            text = if (img.isPrimary == 1) "Portada principal" else "Foto extra",
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.weight(1f),
                        )
                        if (img.isPrimary != 1) {
                            TextButton(
                                text = "Hacer Portada",
                                onClick = { onSetPrimaryImage(img.id) },
                            )
                        }
                        IconButton(
                            onClick = { onDeleteImage(img.id, img.url) },
                            backgroundColor = MiuixTheme.colorScheme.errorContainer,
                        ) {
                            Icon(MiuixIcons.Delete, contentDescription = "Borrar foto", tint = MiuixTheme.colorScheme.error)
                        }
                    }
                }
                HorizontalDivider()
                TextButton(
                    text = "Guardar Cambios",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { submit(openPhotoAfter = false) },
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = "Crear sin Foto",
                        modifier = Modifier.weight(1f),
                        onClick = { submit(openPhotoAfter = false) },
                    )
                    TextButton(
                        text = "Crear y Subir Foto",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                        onClick = { submit(openPhotoAfter = true) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductImagesSheet(
    product: ProductDto,
    onDismiss: () -> Unit,
    onUpload: (Boolean) -> Unit,
    onSetPrimary: (Int) -> Unit,
    onDeleteMedia: (Int, String) -> Unit,
) {
    OverlayBottomSheet(
        show = true,
        title = "Fotos: ${product.name}",
        onDismissRequest = onDismiss,
        endAction = {
            IconButton(onClick = onDismiss) {
                Icon(MiuixIcons.Basic.Close, contentDescription = "Cerrar")
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Acepta imágenes PNG, JPEG o WebP (máx. 3 MB).",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "Subir Foto Principal",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                    onClick = { onUpload(true) },
                )
                TextButton(
                    text = "Agregar Foto Extra",
                    modifier = Modifier.weight(1f),
                    onClick = { onUpload(false) },
                )
            }

            if (product.images.isEmpty()) {
                Text("Este producto todavía no tiene fotos.", style = MiuixTheme.textStyles.body2)
            } else {
                product.images.forEach { img ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        insideMargin = PaddingValues(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            RemoteProductImage(
                                url = img.url,
                                modifier = Modifier.size(64.dp),
                                cornerRadius = 10.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (img.isPrimary == 1) "Portada principal" else "Foto extra #${img.id}",
                                    style = MiuixTheme.textStyles.body2,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (img.isPrimary != 1) {
                                TextButton(
                                    text = "Hacer Portada",
                                    onClick = { onSetPrimary(img.id) },
                                )
                            }
                            IconButton(
                                onClick = { onDeleteMedia(img.id, img.url) },
                                backgroundColor = MiuixTheme.colorScheme.errorContainer,
                            ) {
                                Icon(MiuixIcons.Delete, contentDescription = "Borrar", tint = MiuixTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryEditorDialog(
    initial: CategoryDto?,
    onInvalid: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Int?, AdminCategoryPayload) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var description by remember(initial?.id) { mutableStateOf(initial?.description ?: "") }
    var accentHex by remember(initial?.id) { mutableStateOf(initial?.accentHex ?: "#3482FF") }
    var isActive by remember(initial?.id) { mutableStateOf((initial?.isActive ?: 1) == 1) }

    val quickSuggestions = listOf(
        "Enrique" to "#3482FF",
        "Edgarvis" to "#00BFA5",
        "Leonel" to "#7C4DFF",
        "Aníbal" to "#FF6D00",
    )

    OverlayDialog(
        show = true,
        title = if (initial == null) "Nueva Categoría" else "Editar Categoría",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (initial == null) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickSuggestions) { (qName, qHex) ->
                        TextButton(
                            text = qName,
                            onClick = {
                                name = qName
                                accentHex = qHex
                            },
                        )
                    }
                }
            }

            TextField(value = name, onValueChange = { name = it }, label = "Nombre de categoría", singleLine = true, modifier = Modifier.fillMaxWidth())
            TextField(value = description, onValueChange = { description = it }, label = "Descripción corta (opcional)", modifier = Modifier.fillMaxWidth())

            Text(
                text = "Color de etiqueta",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BrandConfig.CATEGORY_ACCENTS.forEach { (_, hex) ->
                    val color = parseHexColorOrDefault(hex, Color(0xFF3482FF))
                    val selected = accentHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .squircleClip(10.dp)
                            .background(color)
                            .then(
                                if (selected) {
                                    Modifier.border(3.dp, MiuixTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { accentHex = hex },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Box(modifier = Modifier.size(10.dp).background(Color.White, CircleShape))
                        }
                    }
                }
            }

            SwitchPreference(title = "Categoría activa", checked = isActive, onCheckedChange = { isActive = it })
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(text = "Cancelar", modifier = Modifier.weight(1f), onClick = onDismiss)
                TextButton(
                    text = "Guardar",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val cleanName = name.trim()
                        if (cleanName.length < 2) {
                            onInvalid("Escribe un nombre válido (mín. 2 letras)")
                            return@TextButton
                        }
                        onSave(
                            initial?.id,
                            AdminCategoryPayload(
                                name = cleanName,
                                description = description.trim(),
                                accentHex = accentHex.trim(),
                                sortOrder = initial?.sortOrder ?: 10,
                                isActive = isActive,
                            ),
                        )
                    },
                )
            }
        }
    }
}
