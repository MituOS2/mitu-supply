package nose.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nose.model.BrandConfig
import nose.model.OrderDto
import nose.model.OrderMessageDto
import nose.model.ProductDto
import nose.model.formatUsd
import nose.network.platformDecodeImageBitmap
import nose.network.platformFormatDate
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Image as ImageIcon
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun RemoteProductImage(
    url: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember(url) { mutableStateOf(url != null) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            bitmap = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        bitmap = platformDecodeImageBitmap(url)
        loading = false
    }

    Box(
        modifier = modifier
            .squircleClip(cornerRadius)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Icon(
                imageVector = MiuixIcons.ImageIcon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.size(32.dp),
            )
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
fun TagPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .squircleClip(8.dp)
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
fun FounderTagPill(founder: String, modifier: Modifier = Modifier) {
    val low = founder.lowercase()
    val accent = when {
        low.contains("enrique") -> Color(0xFF3482FF)
        low.contains("edgar") -> Color(0xFF00BFA5)
        low.contains("leonel") || low.contains("leoner") -> Color(0xFF7C4DFF)
        low.contains("anibal") || low.contains("aníbal") -> Color(0xFFFF6D00)
        else -> MiuixTheme.colorScheme.primary
    }
    TagPill(
        text = founder,
        containerColor = accent.copy(alpha = 0.14f),
        contentColor = accent,
        modifier = modifier,
    )
}

@Composable
fun OrderStatusPill(status: String, modifier: Modifier = Modifier) {
    val label = BrandConfig.orderStatusLabel(status)
    val color = when (status) {
        "pending" -> Color(0xFFFF9100)
        "preparing" -> Color(0xFF3482FF)
        "shipped" -> Color(0xFF7C4DFF)
        "delivered" -> Color(0xFF00BFA5)
        "cancelled" -> MiuixTheme.colorScheme.error
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    TagPill(
        text = label,
        containerColor = color.copy(alpha = 0.14f),
        contentColor = color,
        modifier = modifier,
    )
}

@Composable
fun PriceDisplay(
    priceCents: Int,
    compareAtCents: Int? = null,
    large: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = formatUsd(priceCents),
            style = if (large) MiuixTheme.textStyles.title3 else MiuixTheme.textStyles.headline1,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
        )
        if (compareAtCents != null && compareAtCents > priceCents) {
            Text(
                text = formatUsd(compareAtCents),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textDecoration = TextDecoration.LineThrough,
            )
        }
    }
}

@Composable
fun ProductCard(
    product: ProductDto,
    onOpenDetail: () -> Unit,
    onQuickAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(14.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onOpenDetail,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            RemoteProductImage(
                url = product.primaryImageUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f),
                cornerRadius = 14.dp,
            )
            FounderTagPill(
                founder = product.categoryName.ifBlank { product.founderTag },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
            if (product.stock <= 0) {
                TagPill(
                    text = "AGOTADO",
                    containerColor = MiuixTheme.colorScheme.errorContainer,
                    contentColor = MiuixTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            } else if (product.stock <= 5) {
                TagPill(
                    text = "Quedan ${product.stock}",
                    containerColor = Color(0xFFFF6D00).copy(alpha = 0.9f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = product.categoryName.uppercase(),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = product.name,
            style = MiuixTheme.textStyles.headline2,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (product.subtitle.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = product.subtitle,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PriceDisplay(product.priceCents, product.compareAtCents)
            IconButton(
                onClick = onQuickAdd,
                enabled = product.stock > 0,
                backgroundColor = if (product.stock > 0) {
                    MiuixTheme.colorScheme.tertiaryContainer
                } else {
                    MiuixTheme.colorScheme.disabledSecondary
                },
                cornerRadius = 12.dp,
            ) {
                Icon(
                    imageVector = MiuixIcons.Add,
                    contentDescription = "Agregar al carrito",
                    tint = if (product.stock > 0) {
                        MiuixTheme.colorScheme.onTertiaryContainer
                    } else {
                        MiuixTheme.colorScheme.disabledOnSurface
                    },
                )
            }
        }
    }
}

@Composable
fun ProductDetailSheet(
    product: ProductDto?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onAddToCart: (ProductDto, Int) -> Unit,
) {
    var qty by remember(product?.id) { mutableIntStateOf(1) }
    var selectedImageIndex by remember(product?.id) { mutableIntStateOf(0) }

    OverlayBottomSheet(
        show = product != null,
        title = product?.name ?: "Detalle",
        onDismissRequest = onDismiss,
        endAction = {
            IconButton(onClick = onDismiss) {
                Icon(MiuixIcons.Basic.Close, contentDescription = "Cerrar")
            }
        },
    ) {
        if (product != null) {
            val galleryUrls = remember(product) {
                val list = product.images.map { it.url }
                if (list.isNotEmpty()) list else listOfNotNull(product.primaryImageUrl)
            }
            val activeUrl = galleryUrls.getOrNull(selectedImageIndex) ?: product.primaryImageUrl

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RemoteProductImage(
                    url = activeUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    cornerRadius = 18.dp,
                    contentScale = ContentScale.Fit,
                )

                if (galleryUrls.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(galleryUrls) { idx, imgUrl ->
                            val isSelected = idx == selectedImageIndex
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .squircleClip(10.dp)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable { selectedImageIndex = idx },
                            ) {
                                RemoteProductImage(url = imgUrl, modifier = Modifier.fillMaxSize(), cornerRadius = 10.dp)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FounderTagPill(product.founderTag)
                        if (product.categoryName.isNotBlank()) {
                            TagPill(
                                text = product.categoryName,
                                containerColor = MiuixTheme.colorScheme.secondaryVariant,
                                contentColor = MiuixTheme.colorScheme.onSecondaryVariant,
                            )
                        }
                    }
                    Text(
                        text = if (product.stock > 0) "Stock: ${product.stock}" else "Sin stock",
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.SemiBold,
                        color = if (product.stock > 0) Color(0xFF00BFA5) else MiuixTheme.colorScheme.error,
                    )
                }

                PriceDisplay(product.priceCents, product.compareAtCents, large = true)

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (product.subtitle.isNotBlank()) {
                    Text(
                        text = product.subtitle,
                        style = MiuixTheme.textStyles.headline2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }

                Text(
                    text = product.description,
                    style = MiuixTheme.textStyles.paragraph,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconButton(
                            onClick = { if (qty > 1) qty -= 1 },
                            enabled = qty > 1 && product.stock > 0,
                            backgroundColor = MiuixTheme.colorScheme.secondaryVariant,
                        ) {
                            Icon(MiuixIcons.Remove, contentDescription = "Menos")
                        }
                        Text(
                            text = "$qty",
                            style = MiuixTheme.textStyles.headline1,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        IconButton(
                            onClick = { if (qty < product.stock && qty < 99) qty += 1 },
                            enabled = qty < product.stock && qty < 99,
                            backgroundColor = MiuixTheme.colorScheme.secondaryVariant,
                        ) {
                            Icon(MiuixIcons.Add, contentDescription = "Más")
                        }
                    }

                    TextButton(
                        text = if (product.stock > 0) {
                            "Agregar — ${formatUsd(product.priceCents * qty)}"
                        } else {
                            "Agotado"
                        },
                        enabled = product.stock > 0,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onAddToCart(product, qty)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun OrderReceiptDialog(
    order: OrderDto?,
    title: String,
    onDismiss: () -> Unit,
    onOpenChat: ((OrderDto) -> Unit)? = null,
) {
    OverlayDialog(
        show = order != null,
        title = title,
        summary = order?.let { "Código ${it.orderCode} • ${platformFormatDate(it.createdAt)}" },
        onDismissRequest = onDismiss,
    ) {
        if (order != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Estado", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    OrderStatusPill(order.status)
                }
                Text("Entrega a: ${order.shippingName}", style = MiuixTheme.textStyles.body2)
                if (order.shippingNote.isNotBlank()) {
                    Text(
                        text = "Nota: ${order.shippingNote}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                HorizontalDivider()
                if (order.items.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        order.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${item.quantity}x ${item.productName}",
                                    style = MiuixTheme.textStyles.body2,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = formatUsd(item.lineTotalCents),
                                    style = MiuixTheme.textStyles.body2,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Total (USD)", style = MiuixTheme.textStyles.headline2, fontWeight = FontWeight.Bold)
                    Text(
                        text = formatUsd(order.totalCents),
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onOpenChat != null) {
                        TextButton(
                            text = "Abrir Chat",
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenChat(order) },
                        )
                    }
                    TextButton(
                        text = "Listo",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
fun OrderDetailAndChatSheet(
    order: OrderDto?,
    messages: List<OrderMessageDto>,
    currentUserId: Int?,
    isAdmin: Boolean,
    isLoading: Boolean,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onRefresh: (String) -> Unit,
    onSendText: (String, String, () -> Unit) -> Unit,
    onSendImage: (String, String, () -> Unit) -> Unit,
    onUpdateStatus: ((Int, String) -> Unit)? = null,
) {
    var draft by remember(order?.orderCode) { mutableStateOf("") }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(order?.orderCode) {
        val code = order?.orderCode ?: return@LaunchedEffect
        while (true) {
            delay(4000)
            onRefresh(code)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    OverlayBottomSheet(
        show = order != null,
        title = order?.let { "Pedido ${it.orderCode}" } ?: "Detalle de Pedido",
        sheetMaxWidth = 600.dp,
        onDismissRequest = onDismiss,
        endAction = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (order != null) {
                    IconButton(onClick = { onRefresh(order.orderCode) }) {
                        Icon(MiuixIcons.Refresh, contentDescription = "Actualizar chat")
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(MiuixIcons.Basic.Close, contentDescription = "Cerrar")
                }
            }
        },
    ) {
        if (order != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (order.username.isNotBlank()) {
                                        "Cliente: ${order.shippingName} (@${order.username})"
                                    } else {
                                        "Entrega: ${order.shippingName}"
                                    },
                                    style = MiuixTheme.textStyles.headline2,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = platformFormatDate(order.createdAt),
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = formatUsd(order.totalCents),
                                    style = MiuixTheme.textStyles.headline1,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MiuixTheme.colorScheme.primary,
                                )
                                OrderStatusPill(order.status)
                            }
                        }

                        if (order.shippingNote.isNotBlank()) {
                            Text(
                                text = "Datos / Nota: ${order.shippingNote}",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        }

                        if (order.items.isNotEmpty()) {
                            HorizontalDivider()
                            order.items.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "${item.quantity}x ${item.productName}",
                                        style = MiuixTheme.textStyles.footnote1,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = formatUsd(item.lineTotalCents),
                                        style = MiuixTheme.textStyles.footnote1,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }

                        if (isAdmin && onUpdateStatus != null) {
                            HorizontalDivider()
                            val currentIdx = BrandConfig.ORDER_STATUSES.indexOf(order.status).coerceAtLeast(0)
                            OverlayDropdownPreference(
                                title = "Estado del pedido",
                                items = BrandConfig.ORDER_STATUSES.map { BrandConfig.orderStatusLabel(it) },
                                selectedIndex = currentIdx,
                                onSelectedIndexChange = { idx ->
                                    onUpdateStatus(order.id, BrandConfig.ORDER_STATUSES[idx])
                                },
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Messages,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (isAdmin) "Chat con el cliente (${messages.size})" else "Chat con la tienda (${messages.size})",
                        style = MiuixTheme.textStyles.headline2,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (isLoading && messages.isEmpty()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 330.dp)
                        .squircleClip(16.dp)
                        .background(MiuixTheme.colorScheme.surfaceContainer)
                        .padding(10.dp),
                ) {
                    if (messages.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Todavía no hay mensajes. Escribe aquí o adjunta una foto para coordinar el envío o pago.",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                val isMine = currentUserId != null && msg.senderUserId == currentUserId
                                ChatMessageBubble(
                                    message = msg,
                                    isMine = isMine,
                                    onImageClick = { imgUrl -> previewImageUrl = imgUrl },
                                )
                            }
                        }
                    }
                }

                if (isSending) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextField(
                            value = draft,
                            onValueChange = { draft = it },
                            label = "Escribe un mensaje o nota...",
                            singleLine = false,
                            maxLines = 3,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            enabled = !isSending,
                            backgroundColor = MiuixTheme.colorScheme.secondaryVariant,
                            cornerRadius = 14.dp,
                            onClick = {
                                onSendImage(order.orderCode, draft) {
                                    draft = ""
                                }
                            },
                        ) {
                            Icon(
                                imageVector = MiuixIcons.ImageIcon,
                                contentDescription = "Adjuntar y enviar imagen",
                                tint = MiuixTheme.colorScheme.onSecondaryVariant,
                            )
                        }
                        IconButton(
                            enabled = !isSending && draft.isNotBlank(),
                            backgroundColor = if (!isSending && draft.isNotBlank()) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.disabledSecondary
                            },
                            cornerRadius = 14.dp,
                            onClick = {
                                val toSend = draft
                                onSendText(order.orderCode, toSend) {
                                    draft = ""
                                }
                            },
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Send,
                                contentDescription = "Enviar mensaje",
                                tint = if (!isSending && draft.isNotBlank()) {
                                    MiuixTheme.colorScheme.onPrimary
                                } else {
                                    MiuixTheme.colorScheme.disabledOnSurface
                                },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            text = "Subir / Enviar Imagen",
                            enabled = !isSending,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onSendImage(order.orderCode, draft) {
                                    draft = ""
                                }
                            },
                        )
                        TextButton(
                            text = "Enviar Mensaje",
                            enabled = !isSending && draft.isNotBlank(),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val toSend = draft
                                onSendText(order.orderCode, toSend) {
                                    draft = ""
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    OverlayDialog(
        show = previewImageUrl != null,
        title = "Imagen adjunta",
        onDismissRequest = { previewImageUrl = null },
    ) {
        if (previewImageUrl != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RemoteProductImage(
                    url = previewImageUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 380.dp),
                    cornerRadius = 14.dp,
                    contentScale = ContentScale.Fit,
                )
                TextButton(
                    text = "Cerrar",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { previewImageUrl = null },
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: OrderMessageDto,
    isMine: Boolean,
    onImageClick: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .squircleClip(16.dp)
                .background(
                    if (isMine) {
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MiuixTheme.colorScheme.surfaceContainerHigh
                    },
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isMine) "Tú (@${message.senderUsername})" else "@${message.senderUsername}",
                    style = MiuixTheme.textStyles.footnote2,
                    fontWeight = FontWeight.Bold,
                    color = if (message.senderRole == "admin") {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceSecondary
                    },
                )
                if (message.senderRole == "admin") {
                    TagPill(
                        text = "TIENDA",
                        containerColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.18f),
                        contentColor = MiuixTheme.colorScheme.primary,
                    )
                }
            }

            if (!message.imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .squircleClip(12.dp)
                        .clickable { onImageClick(message.imageUrl) },
                ) {
                    RemoteProductImage(
                        url = message.imageUrl,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 12.dp,
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            if (message.messageText.isNotBlank()) {
                Text(
                    text = message.messageText,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }

            Text(
                text = platformFormatDate(message.createdAt),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
