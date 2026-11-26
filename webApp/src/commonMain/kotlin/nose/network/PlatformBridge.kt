package nose.network

import androidx.compose.ui.graphics.ImageBitmap

data class HttpRawResponse(
    val status: Int,
    val body: String,
)

expect suspend fun platformHttpRequest(
    method: String,
    url: String,
    jsonBody: String?,
    csrfToken: String?,
): HttpRawResponse

expect suspend fun platformFetchBytes(url: String): ByteArray?

expect suspend fun platformDecodeImageBitmap(url: String): ImageBitmap?

expect fun platformInvalidateImageCache(url: String)

expect suspend fun platformPickAndUploadImage(
    productId: Int,
    isPrimary: Boolean,
    csrfToken: String,
): HttpRawResponse?

expect suspend fun platformPickAndSendOrderImage(
    orderCode: String,
    messageText: String,
    csrfToken: String,
): HttpRawResponse?

expect fun platformFormatDate(epochMs: Long): String
