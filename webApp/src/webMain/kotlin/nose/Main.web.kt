package nose

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    function hideLoading() {
        if (window.__miuixLoading) {
            window.__miuixLoading.finish();
        }
    }
    """,
)
private external fun hideLoading()

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "composeApp") {
        LaunchedEffect(Unit) {
            withFrameNanos {}
            hideLoading()
        }
        NoseStoreApp()
    }
}
