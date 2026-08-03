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

    fun runDiagnosticThenActivate(wv: WebView?) {
        if (scriptRunning) return
        scriptRunning = true
        wv?.evaluateJavascript(DIAGNOSTIC_AND_ACTIVATE_SCRIPT, null)
    }

    // Coroutine Poller
    LaunchedEffect(webViewInstance, autoScanActive) {
        if (webViewInstance == null || !autoScanActive) return@LaunchedEffect
        while (autoScanActive) {
            delay(8000)
            if (scriptRunning) { continue }
            webViewInstance?.let { webView ->
                withContext(Dispatchers.Main) {
                    val currentUrl = webView.url ?: ""
                    Log.d("AmexOfferWebView", "Polling URL: $currentUrl")
                    if (currentUrl.contains("dashboard") || currentUrl.contains("account/summary")) {
                        statusText = "Logged in! Redirecting to Amex Offers..."
                        webView.loadUrl(issuer.offersUrl)
                    } else if (currentUrl.contains("offers") || currentUrl.contains("eligible")) {
                        runDiagnosticThenActivate(webView)
                    }
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
                                statusText = "Running diagnostic & activation scan..."
                                runDiagnosticThenActivate(webViewInstance)
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

                            addJavascriptInterface(
                                AmexOfferBridge(
                                    onStatusUpdate = { msg ->
                                        statusText = msg
                                        if (msg.contains("Found") || msg.contains("Activat") || msg.contains("Scanning")) {
                                            isActivating = true
                                        }
                                    },
                                    onOffersActivated = { count ->
                                        activatedCount = count
                                        isActivating = false
                                        scriptRunning = false
                                        statusText = "Done! Activated $count offer(s)."
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
                                        statusText = "Offers page detected. Running diagnostic..."
                                        isActivating = true
                                        runDiagnosticThenActivate(view)
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
 * Offer activation script for the Amex MOBILE layout.
 *
 * The mobile layout renders offer cards with:
 *   - Merchant name and description
 *   - "Terms apply" text
 *   - A blue circular "+" button (SVG icon) to add the offer
 *
 * This script:
 *   1. Scrolls the page to force lazy-loading of all offer cards
 *   2. Scans the full DOM using multiple strategies to find add-offer buttons
 *   3. Clicks each discovered button with a full synthetic event chain
 */
private const val DIAGNOSTIC_AND_ACTIVATE_SCRIPT = """
(function() {
    function notify(msg) {
        console.log("[AmexOfferActivator] " + msg);
        if (window.AndroidBridge) {
            window.AndroidBridge.updateStatus(msg);
        }
    }

    notify("Step 1: Scrolling page to load all offers...");

    // Dump page text for diagnostic
    var bodyText = (document.body ? document.body.innerText : '').substring(0, 300);
    console.log("[AmexOfferActivator] Page preview: " + bodyText);

    // Scroll to the bottom in steps to trigger lazy-loading, then scan
    var scrollStep = 0;
    var maxScrollSteps = 10;
    var scrollInterval = setInterval(function() {
        window.scrollBy(0, 800);
        scrollStep++;
        if (scrollStep >= maxScrollSteps) {
            clearInterval(scrollInterval);
            window.scrollTo(0, 0); // scroll back to top
            setTimeout(function() { runScan(); }, 1500);
        }
    }, 400);

    function runScan() {
        notify("Step 2: Scanning for offer buttons...");

        // Count all element types for diagnostic
        var allButtons = document.querySelectorAll('button');
        var allAnchors = document.querySelectorAll('a');
        var allRoleButtons = document.querySelectorAll('[role="button"]');
        notify("DOM: " + allButtons.length + " buttons, " + allAnchors.length + " links, " + allRoleButtons.length + " role=button");

        var targets = [];
        var seen = new Set();

        function addTarget(el, source) {
            if (seen.has(el)) return;
            seen.add(el);
            targets.push(el);
            console.log("[AmexOfferActivator] Target[" + source + "]: Tag=" + el.tagName + " Text='" + (el.innerText||'').trim().substring(0,30) + "' Aria='" + (el.getAttribute('aria-label')||'').substring(0,50) + "' Size=" + el.offsetWidth + "x" + el.offsetHeight + " HTML=" + el.outerHTML.substring(0,120));
        }

        // ---- STRATEGY A: Buttons/links with 'Add to Card' or 'add offer' text or aria ----
        var clickables = document.querySelectorAll('button, a, [role="button"], span, div');
        for (var i = 0; i < clickables.length; i++) {
            var el = clickables[i];
            var txt = (el.innerText || '').trim().toLowerCase();
            var aria = (el.getAttribute('aria-label') || '').toLowerCase();
            if (txt === 'add to card' || txt === 'add' ||
                aria.includes('add to card') || aria.includes('add offer') ||
                aria.includes('save offer')) {
                addTarget(el, 'A-text');
            }
        }
        notify("Strategy A (text match): " + targets.length + " found");

        // ---- STRATEGY B: 'Terms apply' card containers -> find action buttons inside ----
        var termsEls = [];
        var allEls = document.querySelectorAll('*');
        for (var t = 0; t < allEls.length; t++) {
            var te = allEls[t];
            // Check direct text nodes only
            var dText = '';
            for (var c = 0; c < te.childNodes.length; c++) {
                if (te.childNodes[c].nodeType === 3) dText += te.childNodes[c].textContent;
            }
            if (dText.toLowerCase().includes('terms apply')) termsEls.push(te);
        }
        notify("Found " + termsEls.length + " 'Terms apply' markers");

        for (var k = 0; k < termsEls.length; k++) {
            // Walk up to card container
            var container = termsEls[k];
            for (var d = 0; d < 15; d++) {
                if (!container.parentElement) break;
                container = container.parentElement;
                var h = container.offsetHeight;
                var w = container.offsetWidth;
                if (h > 80 && w > 100) break;
            }

            // Find interactive elements in the card
            var btns = container.querySelectorAll('button, a, [role="button"], [tabindex="0"]');
            for (var j = 0; j < btns.length; j++) {
                var b = btns[j];
                var bTxt = (b.innerText || '').trim().toLowerCase();
                // Skip non-action elements
                if (bTxt.includes('view details') || bTxt.includes('terms apply') ||
                    bTxt.includes('added to card') || bTxt.includes('saved') ||
                    bTxt.includes('log out') || bTxt.includes('view all') ||
                    bTxt.length > 50) continue;
                addTarget(b, 'B-card' + k);
            }
        }
        notify("After Strategy B (card scan): " + targets.length + " total");

        // ---- STRATEGY C: Icon-only buttons (SVG inside, no text) that aren't nav/UI ----
        for (var sb = 0; sb < allButtons.length; sb++) {
            var btn = allButtons[sb];
            var hasSvg = btn.querySelector('svg') !== null;
            var btnTxt = (btn.innerText || '').trim();
            if (hasSvg && btnTxt.length < 3) {
                var bAria = (btn.getAttribute('aria-label') || '').toLowerCase();
                // Skip known UI buttons
                if (bAria.includes('close') || bAria.includes('menu') || bAria.includes('search') ||
                    bAria.includes('back') || bAria.includes('navigate') || bAria.includes('chat') ||
                    bAria.includes('log') || bAria.includes('feedback') || bAria.includes('previous') ||
                    bAria.includes('next') || bAria.includes('carousel')) continue;
                // Must be visible and in a reasonable position
                if (btn.offsetWidth > 10 && btn.offsetHeight > 10 && btn.offsetWidth < 100) {
                    addTarget(btn, 'C-svg');
                }
            }
        }
        notify("After Strategy C (SVG buttons): " + targets.length + " total");

        // ---- STRATEGY D: All role="button" elements with short/no text ----
        for (var rb = 0; rb < allRoleButtons.length; rb++) {
            var rBtn = allRoleButtons[rb];
            var rTxt = (rBtn.innerText || '').trim().toLowerCase();
            var rAria = (rBtn.getAttribute('aria-label') || '').toLowerCase();
            if (rTxt.length < 5 && !rAria.includes('close') && !rAria.includes('menu') &&
                !rAria.includes('search') && !rAria.includes('chat') && !rAria.includes('feedback') &&
                !rAria.includes('carousel') && !rAria.includes('previous') && !rAria.includes('next')) {
                if (rBtn.offsetWidth > 10 && rBtn.offsetHeight > 10) {
                    addTarget(rBtn, 'D-role');
                }
            }
        }
        notify("After Strategy D (role buttons): " + targets.length + " total");

        // Show samples in status
        for (var m = 0; m < Math.min(targets.length, 3); m++) {
            var s = targets[m];
            notify("Target[" + m + "]: <" + s.tagName.toLowerCase() + "> '" + (s.innerText||'').trim().substring(0,20) + "' aria='" + (s.getAttribute('aria-label')||'').substring(0,30) + "'");
        }

        // ---- PHASE 2: CLICK ----
        if (targets.length === 0) {
            notify("No offer buttons found. Try scrolling manually to load offers, then tap Activate again.");
            if (window.AndroidBridge) {
                window.AndroidBridge.onOffersActivated(0);
            }
            return;
        }

        notify("Step 3: Clicking " + targets.length + " offer buttons...");
        var activated = 0;

        function clickNext(idx) {
            if (idx >= targets.length) {
                notify("Done! Clicked " + activated + " offer button(s).");
                if (window.AndroidBridge) {
                    window.AndroidBridge.onOffersActivated(activated);
                }
                return;
            }
            var btn = targets[idx];
            try {
                btn.scrollIntoView({ behavior: 'smooth', block: 'center' });
                // Full event chain for React
                btn.focus();
                btn.dispatchEvent(new PointerEvent('pointerdown', {bubbles:true, cancelable:true}));
                btn.dispatchEvent(new MouseEvent('mousedown', {bubbles:true, cancelable:true}));
                btn.dispatchEvent(new PointerEvent('pointerup', {bubbles:true, cancelable:true}));
                btn.dispatchEvent(new MouseEvent('mouseup', {bubbles:true, cancelable:true}));
                btn.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true}));
                activated++;
                notify("Clicked " + activated + "/" + targets.length);
            } catch(e) {
                console.error("[AmexOfferActivator] Click #" + idx + " error: " + e);
            }
            setTimeout(function(){ clickNext(idx+1); }, 1500);
        }
        setTimeout(function(){ clickNext(0); }, 800);
    }
})();
"""
