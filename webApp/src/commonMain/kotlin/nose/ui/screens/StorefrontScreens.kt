package nose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nose.model.BrandConfig
import nose.model.CategoryDto
import nose.model.ProductDto
import nose.model.formatUsd
import nose.network.platformFormatDate
import nose.state.AppTab
import nose.state.StoreAppState
import nose.ui.components.OrderStatusPill
import nose.ui.components.ProductCard
import nose.ui.components.RemoteProductImage
import nose.ui.components.TagPill
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun StoreHomeScreen(
    state: StoreAppState,
    contentPadding: PaddingValues,
) {
    var searchExpanded by remember { mutableStateOf(false) }

    // Debounced live search
    LaunchedEffect(state.searchQuery) {
        delay(280)
        state.refreshCatalog()
    }

    val sortOptions = listOf(
        "newest" to "Más recientes",
        "price_asc" to "Precio: menor a mayor",
        "price_desc" to "Precio: mayor a menor",
        "stock_desc" to "Mayor stock",
    )
    val selectedSortIdx = sortOptions.indexOfFirst { it.first == state.sortBy }.coerceAtLeast(0)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cols = when {
            maxWidth >= 1120.dp -> 4
            maxWidth >= 780.dp -> 3
            maxWidth >= 520.dp -> 2
            else -> 1
        }
        val rows = remember(state.products, cols) { state.products.chunked(cols) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HeroGlassBanner(
                    state = state,
                    onExploreClick = {
                        state.selectedCategorySlug = ""
                        state.selectedFounder = "Todos"
                        state.searchQuery = ""
                        state.refreshCatalog()
                    },
                    onCrewClick = { state.currentTab = AppTab.Account },
                )
            }

            item {
                SearchBar(
                    inputField = {
                        InputField(
                            query = state.searchQuery,
                            onQueryChange = { state.searchQuery = it },
                            onSearch = {
                                searchExpanded = false
                                state.refreshCatalog()
                            },
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it },
                            label = "Buscar teclados, hoodies, setup...",
                        )
                    },
                    expanded = false,
                    onExpandedChange = { searchExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {}
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item {
                            val allActive = state.selectedCategorySlug.isBlank()
                            CategoryFilterChip(
                                text = "Todas las categorías",
                                selected = allActive,
                                onClick = {
                                    state.selectedCategorySlug = ""
                                    state.selectedFounder = "Todos"
                                    state.refreshCatalog()
                                },
                            )
                        }
                        items(state.categories) { cat ->
                            CategoryFilterChip(
                                text = "${cat.name} (${cat.productCount})",
                                selected = state.selectedCategorySlug == cat.slug,
                                onClick = {
                                    state.selectedFounder = "Todos"
                                    state.selectedCategorySlug = if (state.selectedCategorySlug == cat.slug) "" else cat.slug
                                    state.refreshCatalog()
                                },
                            )
                        }
                    }

                    Card(cornerRadius = 16.dp, insideMargin = PaddingValues(0.dp)) {
                        OverlayDropdownPreference(
                            title = "Ordenar catálogo",
                            items = sortOptions.map { it.second },
                            selectedIndex = selectedSortIdx,
                            onSelectedIndexChange = { idx ->
                                state.sortBy = sortOptions[idx].first
                                state.refreshCatalog()
                            },
                        )
                    }
                }
            }

            if (state.isCatalogLoading) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (rows.isEmpty() && !state.isCatalogLoading) {
                item {
                    val hasFilters = state.searchQuery.isNotBlank() || state.selectedCategorySlug.isNotBlank() || state.selectedFounder != "Todos"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp,
                        insideMargin = PaddingValues(18.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (hasFilters) "Nada con ese filtro" else "Todavía no hay productos disponibles",
                                style = MiuixTheme.textStyles.title4,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (hasFilters) {
                                    "No encontramos productos con ese filtro. Prueba limpiando la búsqueda."
                                } else {
                                    "Pronto habrá nuevos artículos en la tienda."
                                },
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            if (hasFilters) {
                                TextButton(
                                    text = "Ver todos",
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                    onClick = {
                                        state.searchQuery = ""
                                        state.selectedCategorySlug = ""
                                        state.selectedFounder = "Todos"
                                        state.refreshCatalog()
                                    },
                                )
                            }
                        }
                    }
                }
            } else {
                items(rows) { rowProducts ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowProducts.forEach { prod ->
                            ProductCard(
                                product = prod,
                                onOpenDetail = { state.openProductDetail(prod) },
                                onQuickAdd = { state.addToCart(prod, 1) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(cols - rowProducts.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroGlassBanner(
    state: StoreAppState,
    onExploreClick: () -> Unit,
    onCrewClick: () -> Unit,
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val backdrop = rememberLayerBackdrop()
    val shape = GlassShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .squircleClip(24.dp),
    ) {
        // Rich backdrop behind the Miuix glass shader
        Box(
            modifier = Modifier
                .matchParentSize()
                .layerBackdrop(backdrop)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MiuixTheme.colorScheme.primary.copy(alpha = if (isDark) 0.35f else 0.22f),
                            Color(0xFF7C4DFF).copy(alpha = if (isDark) 0.28f else 0.16f),
                            Color(0xFF00BFA5).copy(alpha = if (isDark) 0.24f else 0.14f),
                        ),
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .offset(x = (-20).dp, y = (-30).dp)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(190.dp)
                    .offset(x = 30.dp, y = 40.dp)
                    .background(Color(0xFF7C4DFF).copy(alpha = 0.30f), CircleShape),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (state.glassEnabled) {
                        Modifier.glassPanel(
                            backdrop = backdrop,
                            shape = shape,
                            style = GlassDefaults.style(isDark),
                            material = GlassMaterials.puredThinGlass(isDark),
                            stroke = GlassEdgeStrokes.forTheme(isDark),
                            fallback = Modifier.background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f)),
                        )
                    } else {
                        Modifier.background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f))
                    },
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TagPill(
                    text = "DROP 04 // USD",
                    containerColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.18f),
                    contentColor = MiuixTheme.colorScheme.primary,
                )
                TagPill(
                    text = "Stock Real · Envío Local",
                    containerColor = MiuixTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MiuixTheme.colorScheme.onSurface,
                )
            }

            Text(
                text = BrandConfig.NAME,
                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.ExtraBold,
            )

            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    text = "Ver todo (${state.products.size})",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = onExploreClick,
                )
                TextButton(
                    text = "Mi Cuenta",
                    onClick = onCrewClick,
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .squircleClip(12.dp)
            .background(
                if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainer,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun CategoriesScreen(
    state: StoreAppState,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SmallTitle(
                text = "Explorar por categoría",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        items(state.categories) { cat: CategoryDto ->
            val accent = remember(cat.accentHex) { parseHexColorOrDefault(cat.accentHex, Color(0xFF3482FF)) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                pressFeedbackType = PressFeedbackType.Sink,
                showIndication = true,
                onClick = { state.filterByCategory(cat.slug) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .squircleClip(14.dp)
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(accent, CircleShape),
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(cat.name, style = MiuixTheme.textStyles.headline1, fontWeight = FontWeight.Bold)
                        if (cat.description.isNotBlank()) {
                            Text(
                                text = cat.description,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    TagPill(
                        text = "${cat.productCount} ítems",
                        containerColor = accent.copy(alpha = 0.14f),
                        contentColor = accent,
                    )
                }
            }
        }
    }
}

@Composable
fun CartScreen(
    state: StoreAppState,
    contentPadding: PaddingValues,
) {
    val cart = state.activeCart
    var shippingName by remember(state.user?.id) { mutableStateOf(state.user?.displayName ?: "") }
    var shippingNote by remember { mutableStateOf("") }
    var showConfirmCheckout by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallTitle(
                    text = "Tu Carrito (${cart.itemCount})",
                    insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                )
                if (state.user == null && cart.items.isNotEmpty()) {
                    TagPill(
                        text = "Modo invitado (se fusiona al entrar)",
                        containerColor = MiuixTheme.colorScheme.tertiaryContainer,
                        contentColor = MiuixTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }

        if (cart.items.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    insideMargin = PaddingValues(20.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("El carrito está vacío", style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Agrega algún gadget o prenda de la tienda para probar la compra atómica en SQLite.",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        TextButton(
                            text = "Ir a la tienda",
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = { state.currentTab = AppTab.Store },
                        )
                    }
                }
            }
        } else {
            items(cart.items, key = { it.productId }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RemoteProductImage(
                            url = item.primaryImageUrl,
                            modifier = Modifier.size(68.dp),
                            cornerRadius = 12.dp,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.name, style = MiuixTheme.textStyles.headline2, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${formatUsd(item.priceCents)} c/u • Stock: ${item.stock}",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Text(
                                text = "Subtotal: ${formatUsd(item.priceCents * item.quantity)}",
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IconButton(
                                onClick = { state.updateCartItemQuantityById(item.productId, item.quantity - 1) },
                                backgroundColor = MiuixTheme.colorScheme.secondaryVariant,
                            ) {
                                Icon(MiuixIcons.Remove, contentDescription = "Menos")
                            }
                            Text(
                                text = "${item.quantity}",
                                style = MiuixTheme.textStyles.headline2,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp),
                            )
                            IconButton(
                                onClick = { state.updateCartItemQuantityById(item.productId, item.quantity + 1) },
                                enabled = item.quantity < item.stock && item.quantity < 99,
                                backgroundColor = MiuixTheme.colorScheme.secondaryVariant,
                            ) {
                                Icon(MiuixIcons.Add, contentDescription = "Más")
                            }
                            IconButton(
                                onClick = { state.updateCartItemQuantityById(item.productId, 0) },
                                backgroundColor = MiuixTheme.colorScheme.errorContainer,
                            ) {
                                Icon(MiuixIcons.Delete, contentDescription = "Eliminar", tint = MiuixTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    insideMargin = PaddingValues(18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Datos de entrega (Simulación)", style = MiuixTheme.textStyles.headline1, fontWeight = FontWeight.Bold)
                        TextField(
                            value = shippingName,
                            onValueChange = { shippingName = it },
                            label = "Nombre para recibir pedido",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextField(
                            value = shippingNote,
                            onValueChange = { shippingNote = it },
                            label = "Nota opcional (ej. Aula 4B)",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Total a pagar (USD)", style = MiuixTheme.textStyles.headline1, fontWeight = FontWeight.Bold)
                            Text(
                                text = formatUsd(cart.subtotalCents),
                                style = MiuixTheme.textStyles.title2,
                                fontWeight = FontWeight.ExtraBold,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                        TextButton(
                            text = if (state.user == null) {
                                "Inicia sesión para confirmar compra"
                            } else if (state.isCheckoutBusy) {
                                "Procesando..."
                            } else {
                                "Confirmar Pedido — ${formatUsd(cart.subtotalCents)}"
                            },
                            enabled = !state.isCheckoutBusy,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (state.user == null) {
                                    state.showToast("Inicia sesión primero; tu carrito se conservará")
                                    state.currentTab = AppTab.Account
                                } else if (shippingName.trim().length < 2) {
                                    state.showToast("Ingresa un nombre de entrega válido")
                                } else {
                                    showConfirmCheckout = true
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    OverlayDialog(
        show = showConfirmCheckout,
        title = "Confirmar Pedido",
        summary = "¿Quieres confirmar tu compra?",
        onDismissRequest = { showConfirmCheckout = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Total: ${formatUsd(cart.subtotalCents)}", style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    text = "Cancelar",
                    modifier = Modifier.weight(1f),
                    onClick = { showConfirmCheckout = false },
                )
                TextButton(
                    text = "Confirmar",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        showConfirmCheckout = false
                        state.checkout(shippingName, shippingNote)
                    },
                )
            }
        }
    }
}

@Composable
fun CrewAndAccountScreen(
    state: StoreAppState,
    contentPadding: PaddingValues,
) {
    var authTab by remember { mutableIntStateOf(0) }
    var loginIdentifier by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var showLoginPassword by remember { mutableStateOf(false) }

    var regUser by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPass by remember { mutableStateOf("") }
    var showRegPass by remember { mutableStateOf(false) }

    var showChangePassDialog by remember { mutableStateOf(false) }
    var curPass by remember { mutableStateOf("") }
    var nextPass by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SmallTitle(
                text = if (state.user == null) "Iniciar Sesión o Registrarse" else "Mi Cuenta & Seguridad",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            )
        }

        if (state.user == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    insideMargin = PaddingValues(18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TabRow(
                            tabs = listOf("Iniciar Sesión", "Crear Cuenta"),
                            selectedTabIndex = authTab,
                            onTabSelected = { authTab = it },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (authTab == 0) {
                            TextField(
                                value = loginIdentifier,
                                onValueChange = { loginIdentifier = it },
                                label = "Usuario o correo",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextField(
                                value = loginPassword,
                                onValueChange = { loginPassword = it },
                                label = "Contraseña",
                                singleLine = true,
                                visualTransformation = if (showLoginPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showLoginPassword = !showLoginPassword }) {
                                        Icon(
                                            imageVector = if (showLoginPassword) MiuixIcons.Hide else MiuixIcons.Show,
                                            contentDescription = "Mostrar",
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(
                                text = if (state.authBusy) "Verificando..." else "Entrar con sesión segura",
                                enabled = !state.authBusy,
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    state.login(loginIdentifier, loginPassword) { ok ->
                                        if (ok) loginPassword = ""
                                    }
                                },
                            )
                        } else {
                            TextField(
                                value = regUser,
                                onValueChange = { regUser = it },
                                label = "Nombre de usuario (sin espacios, ej. juan_dev)",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextField(
                                value = regEmail,
                                onValueChange = { regEmail = it },
                                label = "Correo electrónico",
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextField(
                                value = regPass,
                                onValueChange = { regPass = it },
                                label = "Contraseña (mín. 6 caracteres)",
                                singleLine = true,
                                visualTransformation = if (showRegPass) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showRegPass = !showRegPass }) {
                                        Icon(
                                            imageVector = if (showRegPass) MiuixIcons.Hide else MiuixIcons.Show,
                                            contentDescription = "Mostrar",
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(
                                text = if (state.authBusy) "Creando..." else "Crear cuenta",
                                enabled = !state.authBusy,
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    state.register(regUser, regEmail, regPass) { ok ->
                                        if (ok) regPass = ""
                                    }
                                },
                            )
                        }
                    }
                }
            }
        } else {
            val activeUser = state.user!!
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    insideMargin = PaddingValues(18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(activeUser.displayName, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "@${activeUser.username} • ${activeUser.email}",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            TagPill(
                                text = if (activeUser.role == "admin") "ADMIN" else "CLIENTE",
                                containerColor = if (activeUser.role == "admin") {
                                    MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                                } else {
                                    MiuixTheme.colorScheme.secondaryVariant
                                },
                                contentColor = if (activeUser.role == "admin") {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onSecondaryVariant
                                },
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (activeUser.role == "admin") {
                                TextButton(
                                    text = "Abrir Panel Admin",
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        state.currentTab = AppTab.Admin
                                        state.loadAdminAll()
                                    },
                                )
                            }
                            TextButton(
                                text = "Cambiar Clave",
                                modifier = Modifier.weight(1f),
                                onClick = { showChangePassDialog = true },
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                text = "Cerrar Sesión",
                                modifier = Modifier.weight(1f),
                                onClick = { state.logout() },
                            )
                            TextButton(
                                text = "Cerrar Todas (Global)",
                                modifier = Modifier.weight(1f),
                                onClick = { state.logoutAll() },
                            )
                        }
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
                        text = "Mis Pedidos (${state.orders.size})",
                        insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    )
                    IconButton(onClick = { state.bootstrap() }) {
                        Icon(MiuixIcons.Refresh, contentDescription = "Actualizar")
                    }
                }
            }

            if (state.orders.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        insideMargin = PaddingValues(16.dp),
                    ) {
                        Text(
                            text = "Todavía no has realizado compras con esta cuenta.",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            } else {
                items(state.orders, key = { it.orderCode }) { ord ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        pressFeedbackType = PressFeedbackType.Sink,
                        onClick = { state.openOrderChat(ord) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(ord.orderCode, style = MiuixTheme.textStyles.headline2, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${platformFormatDate(ord.createdAt)} • ${ord.itemCount} ítems",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Text(
                                    text = if (ord.messageCount > 0) "Chat (${ord.messageCount} msgs) • Toca para abrir" else "Toca para ver detalles y chatear",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
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
                    }
                }
            }
        }

        // Live Theme Engine Controls
        item {
            SmallTitle(
                text = "Apariencia (En Vivo)",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            )
        }

        item {
            val modes = listOf(
                "Sistema",
                "Claro",
                "Oscuro",
                "Monet Sistema",
                "Monet Claro",
                "Monet Oscuro",
            )
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(0.dp)) {
                OverlayDropdownPreference(
                    title = "Esquema de color",
                    summary = "Cambia en tiempo real",
                    items = modes,
                    selectedIndex = state.colorMode,
                    onSelectedIndexChange = { state.colorMode = it },
                )
                HorizontalDivider()
                OverlayDropdownPreference(
                    title = "Color semilla Monet",
                    items = state.keyColors.map { it.first },
                    selectedIndex = state.keyColorIndex,
                    onSelectedIndexChange = { state.keyColorIndex = it },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = "Bordes suaves",
                    summary = "Curvatura matemática continua",
                    checked = state.squircleEnabled,
                    onCheckedChange = { state.squircleEnabled = it },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = "Efecto cristal",
                    summary = "Superficies translúcidas en el banner",
                    checked = state.glassEnabled,
                    onCheckedChange = { state.glassEnabled = it },
                )
            }
        }
    }

    OverlayDialog(
        show = showChangePassDialog,
        title = "Cambiar Contraseña",
        summary = "Al cambiarla se revocan tus sesiones previas y se genera una nueva cookie segura.",
        onDismissRequest = { showChangePassDialog = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value = curPass,
                onValueChange = { curPass = it },
                label = "Contraseña actual",
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = nextPass,
                onValueChange = { nextPass = it },
                label = "Nueva contraseña (mín. 6 caracteres)",
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "Cancelar",
                    modifier = Modifier.weight(1f),
                    onClick = { showChangePassDialog = false },
                )
                TextButton(
                    text = "Actualizar",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        state.changePassword(curPass, nextPass) {
                            curPass = ""
                            nextPass = ""
                            showChangePassDialog = false
                        }
                    },
                )
            }
        }
    }
}

fun parseHexColorOrDefault(hex: String, fallback: Color): Color {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6) return fallback
    val rgb = clean.toLongOrNull(16) ?: return fallback
    return Color((0xFF000000L or rgb).toInt())
}
