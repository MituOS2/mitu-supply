package nose.network

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.Image

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    function fetchJsonJs(method, url, bodyJson, csrfToken, onDone) {
        var headers = {};
        if (bodyJson !== null && bodyJson !== undefined && bodyJson.length > 0) {
            headers['Content-Type'] = 'application/json';
        }
        if (csrfToken !== null && csrfToken !== undefined && csrfToken.length > 0) {
            headers['X-CSRF-Token'] = csrfToken;
        }
        var opts = {
            method: method,
            credentials: 'same-origin',
            headers: headers
        };
        if (method !== 'GET' && method !== 'HEAD' && bodyJson !== null && bodyJson !== undefined) {
            opts.body = bodyJson;
        }
        fetch(url, opts)
            .then(function(r) {
                return r.text().then(function(t) {
                    onDone(r.status, t || '');
                });
            })
            .catch(function(e) {
                onDone(0, String(e));
            });
    }
    """,
)
private external fun fetchJsonJs(
    method: String,
    url: String,
    bodyJson: String?,
    csrfToken: String?,
    onDone: (Int, String) -> Unit,
)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    function fetchBytesBase64Js(url, onOk, onErr) {
        fetch(url, { credentials: 'same-origin' })
            .then(function(r) {
                if (!r.ok) {
                    onErr('http ' + r.status);
                    return null;
                }
                return r.arrayBuffer();
            })
            .then(function(buf) {
                if (buf === null) return;
                var bytes = new Uint8Array(buf);
                var chunks = [];
                var step = 0x8000;
                for (var i = 0; i < bytes.length; i += step) {
                    chunks.push(String.fromCharCode.apply(null, bytes.subarray(i, i + step)));
                }
                onOk(btoa(chunks.join('')));
            })
            .catch(function(e) {
                onErr(String(e));
            });
    }
    """,
)
private external fun fetchBytesBase64Js(
    url: String,
    onOk: (String) -> Unit,
    onErr: (String) -> Unit,
)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    function pickAndUploadImageJs(productId, isPrimary, csrfToken, onDone) {
        var input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/png,image/jpeg,image/webp';
        input.style.display = 'none';
        var settled = false;

        function finish(status, body) {
            if (settled) return;
            settled = true;
            if (input.parentNode) input.parentNode.removeChild(input);
            window.removeEventListener('focus', onWindowFocus);
            onDone(status, body);
        }

        function onWindowFocus() {
            setTimeout(function() {
                if (!settled && (!input.files || input.files.length === 0)) {
                    finish(-1, '');
                }
            }, 400);
        }

        input.addEventListener('change', function() {
            var file = input.files && input.files[0];
            if (!file) {
                finish(-1, '');
                return;
            }
            if (file.size > 3 * 1024 * 1024) {
                finish(413, JSON.stringify({ error: 'La imagen supera el limite de 3 MB' }));
                return;
            }
            var form = new FormData();
            form.append('productId', String(productId));
            form.append('isPrimary', isPrimary ? 'true' : 'false');
            form.append('image', file);
            fetch('/api/admin/media', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'X-CSRF-Token': csrfToken },
                body: form
            })
            .then(function(r) {
                return r.text().then(function(t) {
                    finish(r.status, t || '');
                });
            })
            .catch(function(e) {
                finish(0, String(e));
            });
        });

        window.addEventListener('focus', onWindowFocus);
        document.body.appendChild(input);
        input.click();
    }
    """,
)
private external fun pickAndUploadImageJs(
    productId: Int,
    isPrimary: Boolean,
    csrfToken: String,
    onDone: (Int, String) -> Unit,
)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    function pickAndSendOrderImageJs(orderCode, messageText, csrfToken, onDone) {
        var input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/png,image/jpeg,image/webp';
        input.style.display = 'none';
        var settled = false;

        function finish(status, body) {
            if (settled) return;
            settled = true;
            if (input.parentNode) input.parentNode.removeChild(input);
            window.removeEventListener('focus', onWindowFocus);
            onDone(status, body);
        }

        function onWindowFocus() {
            setTimeout(function() {
                if (!settled && (!input.files || input.files.length === 0)) {
                    finish(-1, '');
                }
            }, 400);
        }

        input.addEventListener('change', function() {
            var file = input.files && input.files[0];
            if (!file) {
                finish(-1, '');
                return;
            }
            if (file.size > 3 * 1024 * 1024) {
                finish(413, JSON.stringify({ error: 'La imagen supera el limite de 3 MB' }));
                return;
            }
            var form = new FormData();
            if (messageText && messageText.trim().length > 0) {
                form.append('message', messageText.trim());
            }
            form.append('image', file);
            fetch('/api/orders/' + encodeURIComponent(orderCode) + '/messages', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'X-CSRF-Token': csrfToken },
                body: form
            })
            .then(function(r) {
                return r.text().then(function(t) {
                    finish(r.status, t || '');
                });
            })
            .catch(function(e) {
                finish(0, String(e));
            });
        });

        window.addEventListener('focus', onWindowFocus);
        document.body.appendChild(input);
        input.click();
    }
    """,
)
private external fun pickAndSendOrderImageJs(
    orderCode: String,
    messageText: String,
    csrfToken: String,
    onDone: (Int, String) -> Unit,
)

private val imageCache = linkedMapOf<String, ImageBitmap>()
private const val MAX_IMAGE_CACHE = 64

actual suspend fun platformHttpRequest(
    method: String,
    url: String,
    jsonBody: String?,
    csrfToken: String?,
): HttpRawResponse = suspendCancellableCoroutine { cont ->
    fetchJsonJs(method, url, jsonBody, csrfToken) { status, body ->
        if (cont.isActive) {
            cont.resume(HttpRawResponse(status, body))
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
actual suspend fun platformFetchBytes(url: String): ByteArray? = suspendCancellableCoroutine { cont ->
    fetchBytesBase64Js(
        url,
        { b64 ->
            if (cont.isActive) {
                cont.resume(runCatching { Base64.decode(b64) }.getOrNull())
            }
        },
        {
            if (cont.isActive) cont.resume(null)
        },
    )
}

actual suspend fun platformDecodeImageBitmap(url: String): ImageBitmap? {
    imageCache[url]?.let { existing ->
        imageCache.remove(url)
        imageCache[url] = existing
        return existing
    }
    val bytes = platformFetchBytes(url) ?: return null
    val bmp = runCatching {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull() ?: return null

    if (imageCache.size >= MAX_IMAGE_CACHE) {
        val oldestKey = imageCache.keys.firstOrNull()
        if (oldestKey != null) imageCache.remove(oldestKey)
    }
    imageCache[url] = bmp
    return bmp
}

actual fun platformInvalidateImageCache(url: String) {
    imageCache.remove(url)
}

actual suspend fun platformPickAndUploadImage(
    productId: Int,
    isPrimary: Boolean,
    csrfToken: String,
): HttpRawResponse? = suspendCancellableCoroutine { cont ->
    pickAndUploadImageJs(productId, isPrimary, csrfToken) { status, body ->
        if (cont.isActive) {
            if (status == -1) cont.resume(null)
            else cont.resume(HttpRawResponse(status, body))
        }
    }
}

actual suspend fun platformPickAndSendOrderImage(
    orderCode: String,
    messageText: String,
    csrfToken: String,
): HttpRawResponse? = suspendCancellableCoroutine { cont ->
    pickAndSendOrderImageJs(orderCode, messageText, csrfToken) { status, body ->
        if (cont.isActive) {
            if (status == -1) cont.resume(null)
            else cont.resume(HttpRawResponse(status, body))
        }
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    function formatDateJs(ms) {
        try {
            return new Date(ms).toLocaleString('es-ES', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch (e) {
            return String(ms);
        }
    }
    """,
)
private external fun formatDateJs(ms: Double): String

actual fun platformFormatDate(epochMs: Long): String = formatDateJs(epochMs.toDouble())

