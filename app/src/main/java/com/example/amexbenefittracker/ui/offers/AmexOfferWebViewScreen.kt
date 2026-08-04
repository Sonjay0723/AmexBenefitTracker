package com.example.amexbenefittracker.ui.offers

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AmexOfferWebViewScreen(
    issuer: CardIssuer,
    onDismiss: () -> Unit
) {
    var statusText by remember { mutableStateOf("Sign into Amex, then tap 'Activate Offers Now'...") }
    var isActivating by remember { mutableStateOf(false) }
    var activatedCount by remember { mutableStateOf(0) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var autoScanActive by remember { mutableStateOf(true) }
    var scriptRunning by remember { mutableStateOf(false) }

    fun runActivation(wv: WebView?) {
        if (scriptRunning) return
        scriptRunning = true
        wv?.evaluateJavascript(FIND_AND_TAP_OFFERS_SCRIPT, null)
    }

    // Coroutine Poller - checks URL and triggers activation on offers page
    LaunchedEffect(webViewInstance, autoScanActive) {
        if (webViewInstance == null || !autoScanActive) return@LaunchedEffect
        while (autoScanActive) {
            delay(10000) // 10 second poll interval
            if (scriptRunning) continue
            webViewInstance?.let { webView ->
                withContext(Dispatchers.Main) {
                    val currentUrl = webView.url ?: ""
                    Log.d("AmexOfferWebView", "Polling URL: $currentUrl")
                    if (currentUrl.contains("dashboard") || currentUrl.contains("account/summary")) {
                        statusText = "Logged in! Redirecting to Amex Offers..."
                        webView.loadUrl(issuer.offersUrl)
                    }
                    // Don't auto-run on offers page - let user click the button
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
                                maxLines = 2
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
                                scriptRunning = false // Reset so it can run again
                                statusText = "Scanning for offers..."
                                runActivation(webViewInstance)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Activate Offers Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewInstance = this

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = false
                            settings.loadWithOverviewMode = false
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    val msg = consoleMessage?.message() ?: ""
                                    Log.d("AmexOfferJS", msg)
                                    return super.onConsoleMessage(consoleMessage)
                                }
                            }

                            val bridge = AmexOfferBridge(
                                onStatusUpdate = { msg ->
                                    statusText = msg
                                    if (msg.contains("Found") || msg.contains("Tapping") || msg.contains("Scanning")) {
                                        isActivating = true
                                    }
                                },
                                onOffersActivated = { count ->
                                    activatedCount = count
                                    isActivating = false
                                    scriptRunning = false
                                    statusText = "Done! Activated $count offer(s)."
                                }
                            )
                            bridge.setWebView(this)
                            addJavascriptInterface(bridge, "AndroidBridge")

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
                                        statusText = "Offers page loaded. Tap 'Activate Offers Now' when ready."
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

/**
 * JavaScript that finds all eligible offer "Add" buttons and reports their
 * CENTER COORDINATES back to Kotlin via AndroidBridge.tapButtons(json).
 *
 * Kotlin then dispatches real MotionEvent taps at those coordinates,
 * creating TRUSTED touch events that React processes as genuine user input.
 *
 * This approach bypasses the fundamental limitation of JS dispatchEvent()
 * which always creates untrusted events (isTrusted: false).
 */
private const val FIND_AND_TAP_OFFERS_SCRIPT = """
(function() {
    function notify(msg) {
        console.log("[AmexOfferActivator] " + msg);
        if (window.AndroidBridge) {
            window.AndroidBridge.updateStatus(msg);
        }
    }

    notify("Step 1: Auto-scrolling page to discover all offers...");

    // Smooth scroll down to load lazy components
    var scrollCount = 0;
    var maxScrolls = 8;
    var scrollTimer = setInterval(function() {
        window.scrollBy(0, 700);
        scrollCount++;
        if (scrollCount >= maxScrolls) {
            clearInterval(scrollTimer);
            window.scrollTo(0, 0);
            setTimeout(function() { startScanning(); }, 1500);
        }
    }, 350);

    function startScanning() {
        notify("Step 2: Finding eligible offer 'Add' buttons...");

        var candidates = [];
        var seen = new Set();

        function addCandidate(el, label) {
            if (!el || seen.has(el)) return;
            seen.add(el);
            candidates.push({ el: el, label: label || 'Offer' });
        }

        // 1. Text & Aria matching (case-insensitive substring)
        var allElems = document.querySelectorAll('button, a, [role="button"], div, span');
        for (var i = 0; i < allElems.length; i++) {
            var el = allElems[i];
            var txt = (el.innerText || '').trim().toLowerCase();
            var aria = (el.getAttribute('aria-label') || '').toLowerCase();
            var title = (el.getAttribute('title') || '').toLowerCase();

            if (txt.includes('add to card') || txt.includes('add offer') || txt.includes('save offer') ||
                aria.includes('add to card') || aria.includes('add offer') || aria.includes('save offer') ||
                title.includes('add to card')) {
                // Ignore elements with very long text (containers)
                if (txt.length < 50 || aria.length < 50) {
                    var parentCard = el.closest('[class*="offer"]') || el.closest('[class*="card"]') || el.parentElement;
                    var labelText = (parentCard ? parentCard.innerText : '').split('\n')[0] || 'Offer';
                    addCandidate(el, labelText.substring(0, 25));
                }
            }
        }
        notify("Strategy 1 (Add text/aria): " + candidates.length + " found");

        // 2. Offer card containers (if strategy 1 yielded 0)
        if (candidates.length === 0) {
            notify("Searching offer cards for action buttons...");
            var allElements = document.querySelectorAll('*');
            var cardContainers = [];
            for (var e = 0; e < allElements.length; e++) {
                var elem = allElements[e];
                var rawText = (elem.innerText || '').toLowerCase();
                if ((rawText.includes('terms apply') || rawText.includes('spend') || rawText.includes('expires')) &&
                    elem.offsetWidth > 150 && elem.offsetHeight > 60 && elem.offsetHeight < 300) {
                    cardContainers.push(elem);
                }
            }

            for (var c = 0; c < cardContainers.length; c++) {
                var card = cardContainers[c];
                var btns = card.querySelectorAll('button, a, [role="button"]');
                for (var b = 0; b < btns.length; b++) {
                    var btn = btns[b];
                    var bTxt = (btn.innerText || '').trim().toLowerCase();
                    if (!bTxt.includes('view details') && !bTxt.includes('terms') &&
                        !bTxt.includes('added') && !bTxt.includes('saved')) {
                        addCandidate(btn, card.innerText.split('\n')[0].substring(0, 25));
                    }
                }
            }
            notify("Strategy 2 (Card containers): " + candidates.length + " total");
        }

        // 3. Fallback: Icon buttons within offer list (if 1 & 2 failed)
        if (candidates.length === 0) {
            notify("Searching icon buttons in offer list...");
            var iconBtns = document.querySelectorAll('button');
            for (var k = 0; k < iconBtns.length; k++) {
                var ib = iconBtns[k];
                if (ib.querySelector('svg') && (ib.innerText || '').trim().length < 4) {
                    var aria = (ib.getAttribute('aria-label') || '').toLowerCase();
                    // Skip general header/footer navigation
                    if (!aria.includes('menu') && !aria.includes('close') && !aria.includes('search') &&
                        !aria.includes('chat') && !aria.includes('log') && !aria.includes('back') &&
                        !aria.includes('next') && !aria.includes('previous') && !aria.includes('carousel')) {
                        if (ib.offsetWidth > 15 && ib.offsetWidth < 80 && ib.offsetHeight > 15 && ib.offsetHeight < 80) {
                            addCandidate(ib, 'Icon Button');
                        }
                    }
                }
            }
            notify("Strategy 3 (Icon buttons): " + candidates.length + " total");
        }

        if (candidates.length === 0) {
            notify("No eligible offer buttons found. Ensure you are on the Eligible offers page.");
            if (window.AndroidBridge) {
                window.AndroidBridge.onOffersActivated(0);
            }
            return;
        }

        notify("Step 3: Sequential Activation (" + candidates.length + " candidates)...");
        var dpr = window.devicePixelRatio || 1.0;
        var activated = 0;

        function processIndex(idx) {
            if (idx >= candidates.length) {
                notify("Finished! Activated " + activated + " offer(s).");
                if (window.AndroidBridge) {
                    window.AndroidBridge.onOffersActivated(activated);
                }
                return;
            }

            var item = candidates[idx];
            var el = item.el;

            // Scroll element to center of screen
            el.scrollIntoView({ behavior: 'instant', block: 'center', inline: 'center' });

            setTimeout(function() {
                var rect = el.getBoundingClientRect();
                // Verify element is visible in viewport
                if (rect.width > 0 && rect.height > 0 &&
                    rect.top >= -50 && rect.top <= (window.innerHeight + 50)) {

                    var cssX = rect.left + rect.width / 2;
                    var cssY = rect.top + rect.height / 2;
                    var viewX = cssX * dpr;
                    var viewY = cssY * dpr;

                    notify("Tapping (" + (idx+1) + "/" + candidates.length + "): " + item.label);
                    console.log("[AmexOfferActivator] Tapping item " + idx + " (" + item.label + ") at CSS(" + Math.round(cssX) + "," + Math.round(cssY) + ") View(" + Math.round(viewX) + "," + Math.round(viewY) + ")");

                    // 1. Native MotionEvent tap via Kotlin bridge
                    if (window.AndroidBridge && window.AndroidBridge.tapAt) {
                        window.AndroidBridge.tapAt(viewX, viewY, item.label);
                    }

                    // 2. Backup JS event dispatch
                    try {
                        el.focus();
                        el.click();
                    } catch(e) {}

                    activated++;
                } else {
                    console.log("[AmexOfferActivator] Item " + idx + " not visible after scroll. Skipping.");
                }

                // Wait 1.5s for Amex API response to settle before processing next offer
                setTimeout(function() {
                    processIndex(idx + 1);
                }, 1500);
            }, 300);
        }

        processIndex(0);
    }
})();
"""
