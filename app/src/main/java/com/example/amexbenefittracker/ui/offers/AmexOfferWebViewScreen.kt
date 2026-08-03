package com.example.amexbenefittracker.ui.offers

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.amexbenefittracker.ui.theme.Slate900
import com.example.amexbenefittracker.ui.theme.Slate950

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AmexOfferWebViewScreen(
    issuer: CardIssuer,
    onDismiss: () -> Unit
) {
    var statusText by remember { mutableStateOf("Please sign into ${issuer.displayName} to activate offers...") }
    var isActivating by remember { mutableStateOf(false) }
    var activatedCount by remember { mutableStateOf(0) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Slate950
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Control Header
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "${issuer.displayName} Auto-Activator",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            webViewInstance?.reload()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload Page", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate900)
                )

                if (isActivating) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // WebView Container
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewInstance = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.userAgentString =
                                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Mobile Safari/537.36"

                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            addJavascriptInterface(
                                AmexOfferBridge(
                                    onStatusUpdate = { msg ->
                                        statusText = msg
                                        if (msg.contains("Activating") || msg.contains("Scanning")) {
                                            isActivating = true
                                        }
                                    },
                                    onOffersActivated = { count ->
                                        activatedCount = count
                                        isActivating = false
                                        statusText = "Completed! Activated $count offers successfully."
                                    }
                                ),
                                "AndroidBridge"
                            )

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (url?.contains("offers") == true || url?.contains("eligible") == true) {
                                        statusText = "Offers page loaded. Launching auto-activation script..."
                                        isActivating = true
                                        view?.evaluateJavascript(AUTO_ACTIVATOR_JS_SCRIPT, null)
                                    }
                                }
                            }

                            loadUrl(issuer.offersUrl)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

private const val AUTO_ACTIVATOR_JS_SCRIPT = """
(function activateAllOffers() {
    if (window.isActivatingOffersRunning) return;
    window.isActivatingOffersRunning = true;

    function notify(msg) {
        console.log("[OfferActivator] " + msg);
        if (window.AndroidBridge) {
            window.AndroidBridge.updateStatus(msg);
        }
    }

    notify("Scanning page for eligible offers...");

    let totalActivated = 0;

    function getEligibleButtons() {
        return Array.from(document.querySelectorAll('button')).filter(btn => {
            const txt = (btn.innerText || btn.getAttribute('aria-label') || '').toLowerCase();
            return txt.includes('add to card') || txt.includes('activate offer');
        });
    }

    async function processLoop() {
        const buttons = getEligibleButtons();
        if (buttons.length === 0) {
            notify("No unactivated offers found on this page.");
            if (window.AndroidBridge) {
                window.AndroidBridge.onComplete(0);
            }
            window.isActivatingOffersRunning = false;
            return;
        }

        notify("Found " + buttons.length + " offer(s). Activating now...");

        for (let i = 0; i < buttons.length; i++) {
            try {
                const btn = buttons[i];
                btn.scrollIntoView({ behavior: 'smooth', block: 'center' });
                btn.click();
                totalActivated++;
                notify("Activated offer (" + totalActivated + " of " + buttons.length + ")...");
                await new Promise(r => setTimeout(r, 900 + Math.random() * 600));
            } catch (err) {
                console.error(err);
            }
        }

        notify("Finished activating " + totalActivated + " offer(s)!");
        if (window.AndroidBridge) {
            window.AndroidBridge.onComplete(totalActivated);
        }
        window.isActivatingOffersRunning = false;
    }

    setTimeout(processLoop, 2500);
})();
"""
