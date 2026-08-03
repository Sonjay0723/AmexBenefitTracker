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
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhoneAndroid
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
    var statusText by remember { mutableStateOf("Sign into Amex, then activation will start automatically...") }
    var isActivating by remember { mutableStateOf(false) }
    var activatedCount by remember { mutableStateOf(0) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var autoScanActive by remember { mutableStateOf(true) }
    var isDesktopMode by remember { mutableStateOf(true) } // Default to Desktop site mode for cleaner DOM buttons

    val desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    val mobileUserAgent = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Mobile Safari/537.36"

    fun runInjection(wv: WebView?) {
        wv?.let {
            it.evaluateJavascript(AUTO_ACTIVATOR_JS_SCRIPT, null)
            it.loadUrl("javascript:$AUTO_ACTIVATOR_JS_SCRIPT")
        }
    }

    // Coroutine Poller bound to webViewInstance
    LaunchedEffect(webViewInstance, autoScanActive) {
        if (webViewInstance == null || !autoScanActive) return@LaunchedEffect
        
        while (autoScanActive) {
            delay(2500)
            webViewInstance?.let { webView ->
                withContext(Dispatchers.Main) {
                    val currentUrl = webView.url ?: ""
                    Log.d("AmexOfferWebView", "Polling URL: $currentUrl")

                    if (currentUrl.contains("dashboard") || currentUrl.contains("account/summary")) {
                        statusText = "Logged in! Redirecting to Amex Offers..."
                        webView.loadUrl(issuer.offersUrl)
                    } else if (currentUrl.contains("offers") || currentUrl.contains("eligible") || currentUrl.contains("account")) {
                        isActivating = true
                        runInjection(webView)
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
                        // Desktop / Mobile Mode Toggle
                        IconButton(onClick = {
                            isDesktopMode = !isDesktopMode
                            webViewInstance?.let { wv ->
                                wv.settings.userAgentString = if (isDesktopMode) desktopUserAgent else mobileUserAgent
                                wv.reload()
                            }
                        }) {
                            Icon(
                                imageVector = if (isDesktopMode) Icons.Default.DesktopWindows else Icons.Default.PhoneAndroid,
                                contentDescription = "Toggle Desktop/Mobile Site",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Button(
                            onClick = {
                                isActivating = true
                                statusText = "Scanning page for offer buttons..."
                                runInjection(webViewInstance)
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
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            settings.userAgentString = if (isDesktopMode) desktopUserAgent else mobileUserAgent

                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

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
                                        if (msg.contains("Activating") || msg.contains("Scanning") || msg.contains("Attempt") || msg.contains("Found")) {
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
                                        runInjection(view)
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
    function notify(msg) {
        console.log("[AmexOfferActivator] " + msg);
        if (window.AndroidBridge) {
            window.AndroidBridge.updateStatus(msg);
        }
    }

    function getTargetDocuments() {
        const docs = [document];
        for (let i = 0; i < window.frames.length; i++) {
            try {
                if (window.frames[i].document) {
                    docs.push(window.frames[i].document);
                }
            } catch (e) {}
        }
        return docs;
    }

    function findOfferCardActionButtons() {
        const docs = getTargetDocuments();
        const actionButtons = [];

        docs.forEach(doc => {
            const allElements = Array.from(doc.querySelectorAll('button, a, div[role="button"], span[role="button"], input[type="button"], svg'));

            allElements.forEach(el => {
                if (el.offsetWidth === 0 && el.offsetHeight === 0) return;

                const text = (el.innerText || '').trim();
                const lowerText = text.toLowerCase();
                const ariaLabel = (el.getAttribute('aria-label') || '').toLowerCase();
                const title = (el.getAttribute('title') || '').toLowerCase();
                const className = (el.className || '').toString().toLowerCase();

                if (lowerText.includes('view details') || lowerText.includes('terms apply') || 
                    lowerText.includes('filter') || lowerText.includes('sort') || lowerText.includes('search') ||
                    lowerText.includes('log out') || lowerText.includes('added to card') || lowerText.includes('activated')) {
                    return;
                }

                // Desktop + Mobile text matches
                const isDirectAdd = lowerText.includes('add to card') || lowerText.includes('activate offer') || lowerText.includes('enroll') || 
                                    ariaLabel.includes('add to card') || ariaLabel.includes('activate offer') || title.includes('add to card') ||
                                    lowerText === 'add to card';

                if (isDirectAdd) {
                    actionButtons.push(el);
                    return;
                }

                // Structural match inside offer card
                let parent = el.parentElement;
                let isOfferCard = false;
                let depth = 0;

                while (parent && depth < 8) {
                    const pText = (parent.innerText || '').toLowerCase();
                    if (pText.includes('terms apply') || pText.includes('view details') || pText.includes('spend') || pText.includes('earn')) {
                        isOfferCard = true;
                        break;
                    }
                    parent = parent.parentElement;
                    depth++;
                }

                if (isOfferCard) {
                    const isInteractive = el.tagName === 'BUTTON' || el.getAttribute('role') === 'button' || 
                                          el.classList.contains('btn') || className.includes('button') || className.includes('add');
                    const hasSvg = el.querySelector('svg') !== null || el.tagName.toLowerCase() === 'svg';
                    const hasPlusIcon = text === '+' || text.includes('+') || ariaLabel.includes('+') || hasSvg;

                    if (isInteractive || hasPlusIcon) {
                        if (!actionButtons.includes(el) && !actionButtons.includes(el.parentElement)) {
                            actionButtons.push(el);
                        }
                    }
                }
            });
        });

        return actionButtons;
    }

    async function executeActivation() {
        notify("Scanning page & frames for offer buttons...");

        window.scrollBy(0, 400);
        await new Promise(r => setTimeout(r, 600));

        const buttons = findOfferCardActionButtons();

        if (buttons.length === 0) {
            notify("Scan complete: 0 unactivated buttons found on screen.");
            if (window.AndroidBridge) {
                window.AndroidBridge.onOffersActivated(0);
            }
            return;
        }

        notify("Found " + buttons.length + " offer button(s). Activating...");
        let count = 0;

        for (let i = 0; i < buttons.length; i++) {
            try {
                const btn = buttons[i];
                btn.scrollIntoView({ behavior: 'smooth', block: 'center' });
                
                btn.focus();
                btn.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
                btn.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
                btn.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
                btn.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
                btn.click();

                count++;
                notify("Activated " + count + " of " + buttons.length + " offers...");
                await new Promise(r => setTimeout(r, 1000 + Math.random() * 500));
            } catch (err) {
                console.error("Click error:", err);
            }
        }

        notify("Finished! Activated " + count + " offer(s) successfully.");
        if (window.AndroidBridge) {
            window.AndroidBridge.onOffersActivated(count);
        }
    }

    executeActivation();
})();
"""
