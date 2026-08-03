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

    notify("Scrolling page to load all offers...");

    // Step 1: Scroll the page to trigger lazy loading
    var scrollStep = 0;
    var maxScrollSteps = 10;
    var scrollInterval = setInterval(function() {
        window.scrollBy(0, 600);
        scrollStep++;
        if (scrollStep >= maxScrollSteps) {
            clearInterval(scrollInterval);
            window.scrollTo(0, 0);
            setTimeout(function() { findButtons(); }, 2000);
        }
    }, 300);

    function findButtons() {
        notify("Scanning for 'Add to Card' buttons...");

        var targets = [];
        var seen = new Set();

        function addTarget(el, label) {
            if (seen.has(el)) return;
            seen.add(el);
            var rect = el.getBoundingClientRect();
            // Only add visible, on-screen elements
            if (rect.width < 5 || rect.height < 5) return;
            if (rect.top < -100 || rect.left < -100) return;

            targets.push({
                el: el,
                x: rect.left + rect.width / 2,
                y: rect.top + rect.height / 2,
                label: label || ''
            });
            console.log("[AmexOfferActivator] Found: '" + label + "' at (" + Math.round(rect.left + rect.width/2) + ", " + Math.round(rect.top + rect.height/2) + ") size=" + Math.round(rect.width) + "x" + Math.round(rect.height));
        }

        // ============================================================
        // STRATEGY A: Exact text match for "Add to Card" buttons
        // ============================================================
        var allClickable = document.querySelectorAll('button, a, [role="button"]');
        for (var i = 0; i < allClickable.length; i++) {
            var el = allClickable[i];
            var txt = (el.innerText || '').trim().toLowerCase();
            var aria = (el.getAttribute('aria-label') || '').toLowerCase();
            if (txt === 'add to card' || txt === 'add' ||
                aria.includes('add to card') || aria.includes('add offer') ||
                aria.includes('save offer') || aria.includes('add to') ) {
                var parentText = (el.closest('[class]') || {}).innerText || '';
                var merchantName = parentText.split('\n')[0] || 'offer';
                addTarget(el, merchantName.substring(0, 25));
            }
        }
        notify("Strategy A (text 'Add to Card'): " + targets.length + " found");

        // ============================================================
        // STRATEGY B: Find offer cards via "Terms apply" → find their action button
        // ============================================================
        var termsEls = [];
        var allEls = document.querySelectorAll('*');
        for (var t = 0; t < allEls.length; t++) {
            var te = allEls[t];
            var dText = '';
            for (var c = 0; c < te.childNodes.length; c++) {
                if (te.childNodes[c].nodeType === 3) dText += te.childNodes[c].textContent;
            }
            if (dText.toLowerCase().includes('terms apply')) termsEls.push(te);
        }
        notify("Found " + termsEls.length + " 'Terms apply' markers");

        if (targets.length === 0 && termsEls.length > 0) {
            // Only use Strategy B if Strategy A found nothing
            for (var k = 0; k < termsEls.length; k++) {
                var container = termsEls[k];
                // Walk up to find the offer card container
                for (var d = 0; d < 15; d++) {
                    if (!container.parentElement) break;
                    container = container.parentElement;
                    if (container.offsetHeight > 60 && container.offsetWidth > 80) break;
                }

                // Find the LAST button or role="button" in the card (usually the action button)
                var btns = container.querySelectorAll('button, [role="button"]');
                var lastBtn = null;
                for (var j = 0; j < btns.length; j++) {
                    var b = btns[j];
                    var bTxt = (b.innerText || '').trim().toLowerCase();
                    if (bTxt.includes('view details') || bTxt.includes('terms apply') ||
                        bTxt.includes('added') || bTxt.includes('saved') ||
                        bTxt.includes('log') || bTxt.includes('view all')) continue;
                    lastBtn = b;
                }
                if (lastBtn) {
                    // Try to get the merchant name from the card
                    var cardText = (container.innerText || '').split('\n')[0] || 'offer';
                    addTarget(lastBtn, cardText.substring(0, 25));
                }
            }
            notify("Strategy B (card scan): " + targets.length + " total");
        }

        // ============================================================
        // STRATEGY C: SVG-only buttons (the blue "+" circles)
        // ============================================================
        if (targets.length === 0) {
            // Only try this if A and B found nothing
            var allBtns = document.querySelectorAll('button');
            for (var sb = 0; sb < allBtns.length; sb++) {
                var btn = allBtns[sb];
                if (!btn.querySelector('svg')) continue;
                if ((btn.innerText || '').trim().length > 2) continue;
                var bAria = (btn.getAttribute('aria-label') || '').toLowerCase();
                if (bAria.includes('close') || bAria.includes('menu') || bAria.includes('search') ||
                    bAria.includes('back') || bAria.includes('navigate') || bAria.includes('chat') ||
                    bAria.includes('log') || bAria.includes('feedback') || bAria.includes('previous') ||
                    bAria.includes('next') || bAria.includes('carousel') || bAria.includes('hamburger')) continue;
                if (btn.offsetWidth > 10 && btn.offsetHeight > 10 && btn.offsetWidth < 80) {
                    addTarget(btn, 'icon-btn');
                }
            }
            notify("Strategy C (SVG buttons): " + targets.length + " total");
        }

        // ============================================================
        // REPORT COORDINATES TO KOTLIN FOR NATIVE TAP INJECTION
        // ============================================================
        notify("Found " + targets.length + " offer buttons total.");

        if (targets.length === 0) {
            notify("No eligible offer buttons found. Make sure you're on the Eligible offers page.");
            if (window.AndroidBridge) {
                window.AndroidBridge.onOffersActivated(0);
            }
            return;
        }

        // Build coordinates array for Kotlin
        var coords = [];
        for (var n = 0; n < targets.length; n++) {
            coords.push({
                x: targets[n].x,
                y: targets[n].y,
                label: targets[n].label
            });
        }

        notify("Sending " + coords.length + " button coordinates for native tap...");

        // Send to Kotlin for MotionEvent injection
        if (window.AndroidBridge && window.AndroidBridge.tapButtons) {
            window.AndroidBridge.tapButtons(JSON.stringify(coords));
        } else {
            notify("Error: Native tap not available. Falling back to JS click...");
            // Fallback: try JS clicks
            for (var f = 0; f < targets.length; f++) {
                try { targets[f].el.click(); } catch(e) {}
            }
            if (window.AndroidBridge) {
                window.AndroidBridge.onOffersActivated(targets.length);
            }
        }
    }
})();
"""
