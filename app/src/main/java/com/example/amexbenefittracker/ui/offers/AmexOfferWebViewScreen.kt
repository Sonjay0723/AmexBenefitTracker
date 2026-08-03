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
 * This script first runs a DOM DIAGNOSTIC to discover what elements actually exist
 * on the Amex offers page, then attempts to click them.
 *
 * Phase 1: Diagnostic - report counts and sample HTML of interactive elements near offer cards.
 * Phase 2: Activate - use discovered selectors to click offer buttons.
 */
private const val DIAGNOSTIC_AND_ACTIVATE_SCRIPT = """
(function() {
    function notify(msg) {
        console.log("[AmexOfferActivator] " + msg);
        if (window.AndroidBridge) {
            window.AndroidBridge.updateStatus(msg);
        }
    }

    notify("Phase 1: DOM Diagnostic scanning...");

    // Gather ALL interactive elements on the page
    var allButtons = document.querySelectorAll('button');
    var allAnchors = document.querySelectorAll('a');
    var allRoleButtons = document.querySelectorAll('[role="button"]');
    var allSvg = document.querySelectorAll('svg');
    var allClickable = document.querySelectorAll('[onclick], [tabindex="0"]');

    var summary = "Btns:" + allButtons.length + " As:" + allAnchors.length + " RoleBtns:" + allRoleButtons.length + " SVGs:" + allSvg.length + " Clickable:" + allClickable.length;
    notify("DOM: " + summary);

    // Find all elements that contain offer-related keywords to identify card containers
    var allEls = document.querySelectorAll('*');
    var offerCards = [];
    for (var i = 0; i < allEls.length; i++) {
        var el = allEls[i];
        var directText = '';
        for (var c = 0; c < el.childNodes.length; c++) {
            if (el.childNodes[c].nodeType === 3) {
                directText += el.childNodes[c].textContent;
            }
        }
        if (directText.toLowerCase().includes('terms apply')) {
            offerCards.push(el);
        }
    }
    notify("Found " + offerCards.length + " 'Terms apply' elements (offer cards).");

    // For each offer card, walk up to find the card container, then find interactive children
    var targetButtons = [];
    
    for (var k = 0; k < offerCards.length; k++) {
        var termsEl = offerCards[k];
        
        // Walk up to find a reasonable card container (something with significant height)
        var container = termsEl;
        for (var d = 0; d < 10; d++) {
            if (!container.parentElement) break;
            container = container.parentElement;
            if (container.offsetHeight > 100 && container.offsetWidth > 200) break;
        }

        // Now find ALL interactive elements inside this container
        var interactives = container.querySelectorAll('button, a, [role="button"], [tabindex="0"], svg');
        
        for (var j = 0; j < interactives.length; j++) {
            var btn = interactives[j];
            var btnText = (btn.innerText || '').trim().toLowerCase();
            var btnAria = (btn.getAttribute('aria-label') || '').toLowerCase();

            // Skip known non-action elements
            if (btnText.includes('view details') || btnText.includes('terms apply') || 
                btnText.includes('added') || btnText.includes('saved')) continue;

            // Log what we found for diagnostic
            var info = "Tag:" + btn.tagName + " Text:'" + (btn.innerText || '').trim().substring(0, 30) + "' Aria:'" + (btn.getAttribute('aria-label') || '').substring(0, 40) + "' Class:'" + (btn.className || '').toString().substring(0, 40) + "'";
            console.log("[AmexOfferActivator] Card " + k + " btn: " + info);

            // Collect this as a potential target
            if (targetButtons.indexOf(btn) === -1) {
                targetButtons.push(btn);
            }
        }
    }

    notify("Phase 1 done. Found " + targetButtons.length + " candidate interactive elements across " + offerCards.length + " offer cards.");

    // If we found targetButtons, report first few for diagnostic
    for (var m = 0; m < Math.min(targetButtons.length, 3); m++) {
        var sample = targetButtons[m];
        var sampleInfo = "Sample[" + m + "]: Tag=" + sample.tagName + " OuterHTML=" + sample.outerHTML.substring(0, 120);
        notify(sampleInfo);
    }

    // Phase 2: Now try to activate. For each offer card, find the action button
    // Strategy: In each card container, the "add" action is typically the element
    // that is NOT "View Details" and NOT "Terms apply" - usually the last interactive element or an icon button
    
    if (targetButtons.length === 0) {
        notify("No candidate buttons found. Page may still be loading.");
        if (window.AndroidBridge) {
            window.AndroidBridge.onOffersActivated(0);
        }
        return;
    }

    // Filter to likely "add" buttons: elements that are small icon buttons or contain SVG/plus
    var addButtons = [];
    for (var n = 0; n < targetButtons.length; n++) {
        var tb = targetButtons[n];
        var tbText = (tb.innerText || '').trim().toLowerCase();
        var tbAria = (tb.getAttribute('aria-label') || '').toLowerCase();
        var tbTag = tb.tagName.toLowerCase();
        
        // Direct match
        if (tbText.includes('add to card') || tbAria.includes('add') || tbText === '+' || tbText === '') {
            addButtons.push(tb);
            continue;
        }
        
        // SVG or icon-only buttons (no meaningful text)
        if (tbTag === 'svg' || (tb.querySelector('svg') && tbText.length < 3)) {
            addButtons.push(tb);
            continue;
        }

        // Small buttons that are likely icon buttons
        if (tb.offsetWidth < 80 && tb.offsetHeight < 80 && tb.offsetWidth > 10) {
            addButtons.push(tb);
            continue;
        }
    }

    notify("Phase 2: " + addButtons.length + " likely 'Add' buttons identified. Clicking...");

    var activated = 0;
    
    function clickNext(index) {
        if (index >= addButtons.length) {
            notify("Finished! Activated " + activated + " offer(s).");
            if (window.AndroidBridge) {
                window.AndroidBridge.onOffersActivated(activated);
            }
            return;
        }
        
        var btn = addButtons[index];
        try {
            btn.scrollIntoView({ behavior: 'smooth', block: 'center' });
            
            // Full synthetic event chain
            btn.focus();
            btn.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true }));
            btn.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
            btn.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, cancelable: true }));
            btn.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true }));
            btn.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
            
            activated++;
            notify("Clicked " + activated + "/" + addButtons.length + "...");
        } catch (err) {
            console.error("Click error: " + err);
        }
        
        setTimeout(function() { clickNext(index + 1); }, 1200);
    }

    // Start clicking after a brief delay
    setTimeout(function() { clickNext(0); }, 500);
})();
"""
