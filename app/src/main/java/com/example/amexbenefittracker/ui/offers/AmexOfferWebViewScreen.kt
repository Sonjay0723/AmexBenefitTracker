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

    val desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun runDiagnosticThenActivate(wv: WebView?) {
        wv?.evaluateJavascript(DIAGNOSTIC_AND_ACTIVATE_SCRIPT, null)
    }

    // Coroutine Poller
    LaunchedEffect(webViewInstance, autoScanActive) {
        if (webViewInstance == null || !autoScanActive) return@LaunchedEffect
        while (autoScanActive) {
            delay(3000)
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
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.userAgentString = desktopUserAgent

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
 * Three-phase offer activation script:
 *
 * Phase 0: Navigation - Check if we're on the "Added to Card" view and navigate
 *          to the "Eligible" offers section by clicking the appropriate tab/link.
 * Phase 1: Diagnostic - Scan the DOM to discover the actual HTML structure of
 *          interactive elements within offer cards.
 * Phase 2: Activate - Click all discovered "Add to Card" buttons.
 *
 * The Amex offers page has two views:
 *   - "Added to Card" (already activated - no action needed)
 *   - "Eligible" (available offers with "Add to Card" / "+" buttons)
 * After login, the page often defaults to showing "Added to Card",
 * so Phase 0 ensures we switch to the "Eligible" view first.
 */
private const val DIAGNOSTIC_AND_ACTIVATE_SCRIPT = """
(function() {
    function notify(msg) {
        console.log("[AmexOfferActivator] " + msg);
        if (window.AndroidBridge) {
            window.AndroidBridge.updateStatus(msg);
        }
    }

    // ============================================================
    // PHASE 0: NAVIGATE TO ELIGIBLE OFFERS
    // ============================================================
    notify("Phase 0: Checking page state...");

    // Dump entire page text for diagnostic (first 500 chars)
    var bodyText = (document.body ? document.body.innerText : '').substring(0, 500);
    console.log("[AmexOfferActivator] Page text: " + bodyText);

    // Check if we are seeing "Added to Card" view instead of "Eligible" view
    var pageText = document.body ? document.body.innerText.toLowerCase() : '';
    var onAddedView = pageText.includes('added to card') && !pageText.includes('add to card');

    if (onAddedView) {
        notify("On 'Added to Card' view. Looking for 'Eligible' tab...");

        // Strategy 1: Look for links/buttons/tabs containing "Eligible" text
        var allLinks = document.querySelectorAll('a, button, [role="tab"], [role="button"], span, div');
        var eligibleLink = null;
        for (var i = 0; i < allLinks.length; i++) {
            var linkText = (allLinks[i].innerText || '').trim().toLowerCase();
            var linkHref = (allLinks[i].getAttribute('href') || '').toLowerCase();
            if (linkText === 'eligible' || linkText.includes('eligible offers') ||
                linkHref.includes('eligible')) {
                eligibleLink = allLinks[i];
                console.log("[AmexOfferActivator] Found eligible link: Tag=" + allLinks[i].tagName + " Text='" + linkText + "' Href='" + linkHref + "'");
                break;
            }
        }

        if (eligibleLink) {
            notify("Clicking 'Eligible' tab...");
            eligibleLink.click();
            eligibleLink.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
            // Wait for page to re-render, then re-run
            setTimeout(function() {
                notify("Re-scanning after Eligible tab click...");
                runScanAndActivate();
            }, 3000);
            return;
        }

        // Strategy 2: Look for the "Select" dropdown and try to find "Eligible" option
        var selectDropdowns = document.querySelectorAll('select');
        for (var s = 0; s < selectDropdowns.length; s++) {
            var options = selectDropdowns[s].querySelectorAll('option');
            for (var o = 0; o < options.length; o++) {
                if ((options[o].textContent || '').toLowerCase().includes('eligible')) {
                    notify("Found 'Eligible' in select dropdown, switching...");
                    selectDropdowns[s].value = options[o].value;
                    selectDropdowns[s].dispatchEvent(new Event('change', { bubbles: true }));
                    setTimeout(function() {
                        notify("Re-scanning after dropdown change...");
                        runScanAndActivate();
                    }, 3000);
                    return;
                }
            }
        }

        // Strategy 3: Direct URL navigation to eligible offers
        notify("No 'Eligible' tab found. Navigating directly to eligible URL...");
        window.location.href = 'https://global.americanexpress.com/offers/eligible';
        return;
    }

    // If we're already on the right page, run scan
    runScanAndActivate();

    function runScanAndActivate() {
        // ============================================================
        // PHASE 1: DOM DIAGNOSTIC
        // ============================================================
        notify("Phase 1: Scanning DOM...");

        var allButtons = document.querySelectorAll('button');
        var allAnchors = document.querySelectorAll('a');
        var allRoleButtons = document.querySelectorAll('[role="button"]');
        var allSvg = document.querySelectorAll('svg');

        var summary = "Btns:" + allButtons.length + " As:" + allAnchors.length + " RoleBtns:" + allRoleButtons.length + " SVGs:" + allSvg.length;
        notify("DOM: " + summary);

        // Strategy A: Search for "Add to Card" text in any element
        var addToCardButtons = [];
        var allEls = document.querySelectorAll('button, a, [role="button"], span, div');
        for (var i = 0; i < allEls.length; i++) {
            var el = allEls[i];
            var elText = (el.innerText || '').trim().toLowerCase();
            var elAria = (el.getAttribute('aria-label') || '').toLowerCase();
            if (elText === 'add to card' || elAria.includes('add to card') || elAria.includes('add offer')) {
                addToCardButtons.push(el);
                console.log("[AmexOfferActivator] AddToCard match: Tag=" + el.tagName + " Text='" + elText + "' Aria='" + elAria.substring(0, 50) + "'");
            }
        }
        notify("Found " + addToCardButtons.length + " 'Add to Card' text matches.");

        // Strategy B: Find "Terms apply" elements (eligible offer marker) and look for nearby action buttons
        var termsApplyEls = [];
        var allElements = document.querySelectorAll('*');
        for (var t = 0; t < allElements.length; t++) {
            var te = allElements[t];
            var directText = '';
            for (var c = 0; c < te.childNodes.length; c++) {
                if (te.childNodes[c].nodeType === 3) {
                    directText += te.childNodes[c].textContent;
                }
            }
            if (directText.toLowerCase().includes('terms apply')) {
                termsApplyEls.push(te);
            }
        }
        notify("Found " + termsApplyEls.length + " 'Terms apply' markers.");

        // For each offer card, find the card container and all interactive elements inside
        var cardButtons = [];
        for (var k = 0; k < termsApplyEls.length; k++) {
            var termsEl = termsApplyEls[k];
            var container = termsEl;
            for (var d = 0; d < 12; d++) {
                if (!container.parentElement) break;
                container = container.parentElement;
                if (container.offsetHeight > 80 && container.offsetWidth > 150) break;
            }

            var interactives = container.querySelectorAll('button, a, [role="button"], [tabindex="0"]');
            for (var j = 0; j < interactives.length; j++) {
                var btn = interactives[j];
                var btnText = (btn.innerText || '').trim().toLowerCase();
                // Skip non-action elements
                if (btnText.includes('view details') || btnText.includes('terms apply') ||
                    btnText.includes('added') || btnText.includes('saved to card') ||
                    btnText.includes('log out')) continue;
                if (cardButtons.indexOf(btn) === -1) {
                    cardButtons.push(btn);
                    console.log("[AmexOfferActivator] Card" + k + " btn: Tag=" + btn.tagName + " Text='" + btnText.substring(0, 30) + "' Aria='" + (btn.getAttribute('aria-label') || '').substring(0, 40) + "' Size=" + btn.offsetWidth + "x" + btn.offsetHeight);
                }
            }
        }
        notify("Found " + cardButtons.length + " interactive elements in offer cards.");

        // Strategy C: Find SVGs inside buttons (icon-only add buttons with "+" icons)
        var svgButtons = [];
        for (var sb = 0; sb < allButtons.length; sb++) {
            var b = allButtons[sb];
            if (b.querySelector('svg') && (b.innerText || '').trim().length < 3) {
                var bAria = (b.getAttribute('aria-label') || '').toLowerCase();
                if (!bAria.includes('close') && !bAria.includes('menu') && !bAria.includes('search') &&
                    !bAria.includes('back') && !bAria.includes('navigate') && !bAria.includes('chat')) {
                    svgButtons.push(b);
                    console.log("[AmexOfferActivator] SVG btn: Aria='" + bAria.substring(0, 40) + "' Size=" + b.offsetWidth + "x" + b.offsetHeight);
                }
            }
        }
        notify("Found " + svgButtons.length + " icon-only (SVG) buttons.");

        // Combine all found buttons, deduplicated
        var allFoundButtons = [];
        function addUnique(arr) {
            for (var x = 0; x < arr.length; x++) {
                if (allFoundButtons.indexOf(arr[x]) === -1) {
                    allFoundButtons.push(arr[x]);
                }
            }
        }
        addUnique(addToCardButtons);
        addUnique(cardButtons);
        addUnique(svgButtons);

        notify("Total unique targets: " + allFoundButtons.length);

        // Show samples
        for (var m = 0; m < Math.min(allFoundButtons.length, 3); m++) {
            var sample = allFoundButtons[m];
            notify("Sample[" + m + "]: " + sample.tagName + " '" + (sample.innerText || '').trim().substring(0, 25) + "' html=" + sample.outerHTML.substring(0, 100));
        }

        // ============================================================
        // PHASE 2: ACTIVATE
        // ============================================================
        if (allFoundButtons.length === 0) {
            notify("No offer buttons found. The page may still be loading or all offers are already activated.");
            if (window.AndroidBridge) {
                window.AndroidBridge.onOffersActivated(0);
            }
            return;
        }

        notify("Phase 2: Activating " + allFoundButtons.length + " offers...");
        var activated = 0;

        function clickNext(index) {
            if (index >= allFoundButtons.length) {
                notify("Done! Activated " + activated + " offer(s).");
                if (window.AndroidBridge) {
                    window.AndroidBridge.onOffersActivated(activated);
                }
                return;
            }

            var btn = allFoundButtons[index];
            try {
                btn.scrollIntoView({ behavior: 'smooth', block: 'center' });

                // Full synthetic event chain for React SPAs
                btn.focus();
                btn.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true }));
                btn.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
                btn.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, cancelable: true }));
                btn.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true }));
                btn.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));

                activated++;
                notify("Clicked " + activated + "/" + allFoundButtons.length + ": " + (btn.innerText || '').trim().substring(0, 20));
            } catch (err) {
                console.error("[AmexOfferActivator] Click error on #" + index + ": " + err);
            }

            setTimeout(function() { clickNext(index + 1); }, 1500);
        }

        setTimeout(function() { clickNext(0); }, 800);
    }
})();
"""
