package com.example.amexbenefittracker.ui.offers

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AmexOfferWebViewScreen(
    issuer: CardIssuer,
    onDismiss: () -> Unit
) {
    var statusText by remember { mutableStateOf("Sign into Amex, then activation will start automatically...") }
    var isActivating by remember { mutableStateOf(false) }
    var activatedCount by remember { mutableStateOf(0) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var autoScanActive by remember { mutableStateOf(true) }

    // Continuous 2-second URL & DOM Poller Loop in Kotlin
    // Solves Single Page Application (React SPA) route changes where onPageFinished does not re-trigger!
    LaunchedEffect(autoScanActive) {
        while (autoScanActive) {
            delay(2000)
            webViewInstance?.let { webView ->
                val currentUrl = webView.url ?: ""
                Log.d("AmexOfferWebView", "Polling URL: $currentUrl")

                if (currentUrl.contains("dashboard") || currentUrl.contains("account/summary")) {
                    statusText = "Logged in! Redirecting to Amex Offers..."
                    webView.loadUrl(issuer.offersUrl)
                } else if (currentUrl.contains("offers") || currentUrl.contains("eligible")) {
                    isActivating = true
                    webView.evaluateJavascript(AUTO_ACTIVATOR_JS_SCRIPT, null)
                }
            }
        }
    }

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
                        IconButton(onClick = {
                            autoScanActive = false
                            onDismiss()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    },
                    actions = {
                        Button(
                            onClick = {
                                isActivating = true
                                statusText = "Scanning page for (+) offer buttons..."
                                webViewInstance?.evaluateJavascript(AUTO_ACTIVATOR_JS_SCRIPT, null)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Activate Offers Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
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

                // Embedded WebView Container
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewInstance = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.userAgentString =
                                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Mobile Safari/537.36"

                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            // Chrome Client for Console Logging
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    val msg = consoleMessage?.message() ?: ""
                                    Log.d("AmexOfferJS", msg)
                                    if (msg.startsWith("[AmexOfferActivator]")) {
                                        statusText = msg.removePrefix("[AmexOfferActivator]").trim()
                                    }
                                    return super.onConsoleMessage(consoleMessage)
                                }
                            }

                            addJavascriptInterface(
                                AmexOfferBridge(
                                    onStatusUpdate = { msg ->
                                        statusText = msg
                                        if (msg.contains("Activating") || msg.contains("Scanning") || msg.contains("Attempt")) {
                                            isActivating = true
                                        }
                                    },
                                    onOffersActivated = { count ->
                                        activatedCount = count
                                        isActivating = false
                                        statusText = "Done! Activated $count offer(s) successfully."
                                    }
                                ),
                                "AndroidBridge"
                            )

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val currentUrl = url ?: ""

                                    if (currentUrl.contains("dashboard") || currentUrl.contains("account/summary")) {
                                        statusText = "Logged in! Navigating to Amex Offers..."
                                        view?.loadUrl(issuer.offersUrl)
                                        return
                                    }

                                    if (currentUrl.contains("offers") || currentUrl.contains("eligible")) {
                                        statusText = "Offers page detected. Launching activator..."
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
(function launchOfferActivator() {
    if (window.isActivatingScriptRunning) return;
    window.isActivatingScriptRunning = true;

    function notify(msg) {
        console.log("[AmexOfferActivator] " + msg);
        if (window.AndroidBridge) {
            window.AndroidBridge.updateStatus(msg);
        }
    }

    function getAllButtons(root) {
        let list = Array.from(root.querySelectorAll('button, div[role="button"], span[role="button"], a[role="button"], svg'));
        // Include buttons inside shadow roots if present
        const allElements = Array.from(root.querySelectorAll('*'));
        allElements.forEach(el => {
            if (el.shadowRoot) {
                list = list.concat(getAllButtons(el.shadowRoot));
            }
        });
        return list;
    }

    function getEligibleButtons() {
        const candidates = getAllButtons(document);
        
        return candidates.filter(el => {
            if (el.offsetWidth === 0 && el.offsetHeight === 0) return false;

            const text = (el.innerText || '').trim().toLowerCase();
            const ariaLabel = (el.getAttribute('aria-label') || '').toLowerCase();
            const title = (el.getAttribute('title') || '').toLowerCase();
            const testId = (el.getAttribute('data-testid') || '').toLowerCase();
            const className = (el.className || '').toString().toLowerCase();

            const isDirectAdd = text.includes('add to card') || text.includes('activate offer') || text.includes('enroll') || 
                                ariaLabel.includes('add') || title.includes('add') || testId.includes('add') || className.includes('add');
            const isAlreadyAdded = text.includes('added') || text.includes('activated') || ariaLabel.includes('added') || ariaLabel.includes('activated');
            
            if (isAlreadyAdded) return false;
            if (isDirectAdd) return true;

            // Structural Card Matching for Mobile Layout (+ Circular Buttons):
            let parent = el.parentElement;
            let isInsideOfferCard = false;
            let depth = 0;
            while (parent && depth < 7) {
                const pText = (parent.innerText || '').toLowerCase();
                if (pText.includes('terms apply') || pText.includes('view details') || pText.includes('spend') || pText.includes('earn')) {
                    isInsideOfferCard = true;
                    break;
                }
                parent = parent.parentElement;
                depth++;
            }

            if (isInsideOfferCard) {
                if (text.includes('view details') || text.includes('terms apply') || text.includes('filter') || text.includes('sort') || text.includes('search')) {
                    return false;
                }

                const hasSvg = el.querySelector('svg') !== null || el.tagName.toLowerCase() === 'svg';
                const hasPlus = text === '+' || text.includes('+') || ariaLabel.includes('+');
                const isClickableIcon = hasSvg || hasPlus || el.children.length > 0;
                
                if (isClickableIcon) return true;
            }

            return false;
        });
    }

    let attempts = 0;
    const maxAttempts = 10;
    let totalActivated = 0;

    async function scanAndProcess() {
        attempts++;
        notify("Scanning DOM for (+) offer buttons (Attempt " + attempts + "/" + maxAttempts + ")...");
        
        window.scrollBy(0, 450);

        const buttons = getEligibleButtons();

        if (buttons.length > 0) {
            notify("Found " + buttons.length + " (+) offer button(s). Activating...");
            for (let i = 0; i < buttons.length; i++) {
                try {
                    const btn = buttons[i];
                    btn.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    
                    // Dispatch explicit pointer & mouse click events for React touch compatibility
                    btn.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
                    btn.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
                    btn.click();

                    totalActivated++;
                    notify("Activated " + totalActivated + " of " + buttons.length + " offers...");
                    await new Promise(r => setTimeout(r, 1000 + Math.random() * 500));
                } catch (e) {
                    console.error("Click error:", e);
                }
            }
            
            window.scrollBy(0, 500);
            await new Promise(r => setTimeout(r, 1500));
            const extraButtons = getEligibleButtons();
            if (extraButtons.length > 0 && attempts < maxAttempts) {
                scanAndProcess();
                return;
            }

            notify("Finished activating " + totalActivated + " offer(s)!");
            if (window.AndroidBridge) {
                window.AndroidBridge.onComplete(totalActivated);
            }
            window.isActivatingScriptRunning = false;
            return;
        }

        if (attempts < maxAttempts) {
            notify("Waiting for (+) offer cards to render (Attempt " + attempts + "/" + maxAttempts + ")...");
            setTimeout(scanAndProcess, 1500);
        } else {
            notify("No unactivated offers detected. Tap 'Activate Offers Now' anytime.");
            if (window.AndroidBridge) {
                window.AndroidBridge.onComplete(0);
            }
            window.isActivatingScriptRunning = false;
        }
    }

    scanAndProcess();
})();
"""
