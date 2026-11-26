package nose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import nose.model.BrandConfig
import nose.state.AppTab
import nose.state.StoreAppState
import nose.ui.components.OrderDetailAndChatSheet
import nose.ui.components.OrderReceiptDialog
import nose.ui.components.ProductDetailSheet
import nose.ui.screens.AdminPanelScreen
import nose.ui.screens.CartScreen
import nose.ui.screens.CategoriesScreen
import nose.ui.screens.CrewAndAccountScreen
import nose.ui.screens.StoreHomeScreen
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.BankCards
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

private data class NavEntryUi(
    val tab: AppTab,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun NoseStoreApp() {
    val scope = rememberCoroutineScope()
    val state = remember { StoreAppState(scope) }

    LaunchedEffect(Unit) {
        state.bootstrap()
    }

    val keyColor = state.keyColors.getOrNull(state.keyColorIndex)?.second
    val systemDark = isSystemInDarkTheme()
    val controller = remember(state.colorMode, keyColor, systemDark) {
        when (state.colorMode) {
            1 -> ThemeController(ColorSchemeMode.Light)
            2 -> ThemeController(ColorSchemeMode.Dark)
            3 -> ThemeController(ColorSchemeMode.MonetSystem, keyColor = keyColor, isDark = systemDark)
            4 -> ThemeController(ColorSchemeMode.MonetLight, keyColor = keyColor)
            5 -> ThemeController(ColorSchemeMode.MonetDark, keyColor = keyColor)
            else -> ThemeController(ColorSchemeMode.System, isDark = systemDark)
        }
    }

    val navItems = remember(state.user?.role) {
        buildList {
            add(NavEntryUi(AppTab.Store, "Tienda", MiuixIcons.Store))
            add(NavEntryUi(AppTab.Categories, "Categorías", MiuixIcons.GridView))
            add(NavEntryUi(AppTab.Cart, "Carrito", MiuixIcons.BankCards))
            add(NavEntryUi(AppTab.Account, "Cuenta", MiuixIcons.Contacts))
            if (state.user?.role == "admin") {
                add(NavEntryUi(AppTab.Admin, "Admin", MiuixIcons.Lock))
            }
        }
    }

    CompositionLocalProvider(LocalSquircleEnabled provides state.squircleEnabled) {
        MiuixTheme(controller = controller) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWide = maxWidth >= 840.dp
                val isRailExpanded = maxWidth >= 1180.dp
                val cartCount = state.activeCart.itemCount

                Scaffold(
                    topBar = {
                        SmallTopAppBar(
                            title = BrandConfig.NAME,
                            subtitle = when (state.currentTab) {
                                AppTab.Store -> "Enrique · Edgarvis · Leonel · Aníbal"
                                AppTab.Categories -> "Categorías del catálogo"
                                AppTab.Cart -> "Checkout USD seguro"
                                AppTab.Account -> if (state.user != null) "@${state.user!!.username}" else "Acceso y ajustes"
                                AppTab.Admin -> "Sesión Administrador"
                            },
                        )
                    },
                    bottomBar = {
                        if (!isWide) {
                            NavigationBar {
                                navItems.forEach { item ->
                                    NavigationBarItem(
                                        selected = state.currentTab == item.tab,
                                        onClick = {
                                            state.currentTab = item.tab
                                            if (item.tab == AppTab.Admin) state.loadAdminAll()
                                        },
                                        icon = item.icon,
                                        label = item.label,
                                        badge = if (item.tab == AppTab.Cart && cartCount > 0) {
                                            {
                                                Badge { Text("$cartCount") }
                                            }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(state.snackbarHostState) },
                ) { padding ->
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (isWide) {
                            NavigationRail(
                                expanded = isRailExpanded,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(top = padding.calculateTopPadding()),
                                defaultWindowInsetsPadding = false,
                            ) {
                                Spacer(Modifier.height(8.dp))
                                navItems.forEach { item ->
                                    NavigationRailItem(
                                        selected = state.currentTab == item.tab,
                                        onClick = {
                                            state.currentTab = item.tab
                                            if (item.tab == AppTab.Admin) state.loadAdminAll()
                                        },
                                        icon = item.icon,
                                        label = item.label,
                                        badge = if (item.tab == AppTab.Cart && cartCount > 0) {
                                            {
                                                Badge { Text("$cartCount") }
                                            }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }

                        val innerPadding = if (isWide) {
                            PaddingValues(top = padding.calculateTopPadding(), bottom = 16.dp)
                        } else {
                            padding
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            when (state.currentTab) {
                                AppTab.Store -> StoreHomeScreen(state, innerPadding)
                                AppTab.Categories -> CategoriesScreen(state, innerPadding)
                                AppTab.Cart -> CartScreen(state, innerPadding)
                                AppTab.Account -> CrewAndAccountScreen(state, innerPadding)
                                AppTab.Admin -> AdminPanelScreen(state, innerPadding)
                            }
                        }
                    }

                    ProductDetailSheet(
                        product = state.selectedProductDetail,
                        isLoading = state.isDetailLoading,
                        onDismiss = { state.selectedProductDetail = null },
                        onAddToCart = { prod, qty -> state.addToCart(prod, qty) },
                    )

                    OrderReceiptDialog(
                        order = state.latestReceiptOrder,
                        title = "¡Pedido Confirmado!",
                        onDismiss = { state.latestReceiptOrder = null },
                        onOpenChat = { ord ->
                            state.latestReceiptOrder = null
                            state.openOrderChat(ord)
                        },
                    )

                    OrderDetailAndChatSheet(
                        order = state.selectedOrderDetail,
                        messages = state.orderChatMessages,
                        currentUserId = state.user?.id,
                        isAdmin = state.user?.role == "admin",
                        isLoading = state.isOrderChatLoading,
                        isSending = state.isOrderChatSending,
                        onDismiss = { state.closeOrderDetail() },
                        onRefresh = { code -> state.refreshOrderChat(code) },
                        onSendText = { code, text, clear -> state.sendOrderChatMessage(code, text, clear) },
                        onSendImage = { code, caption, clear -> state.sendOrderChatImage(code, caption, clear) },
                        onUpdateStatus = if (state.user?.role == "admin") {
                            { ordId, nextStatus -> state.updateOrderStatus(ordId, nextStatus) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}
