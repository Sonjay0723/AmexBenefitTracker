package com.example.amexbenefittracker.ui.offers

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
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
    var statusText by remember { mutableStateOf("Sign into ${issuer.displayName} then tap 'Activate Offers Now'...") }
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
                        Button(
                            onClick = {
                                isActivating = true
                                statusText = "Scanning page for offers..."
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
                            settings.userAgentString =
                                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Mobile Safari/537.36"

                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

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

                                    // If user was redirected to Dashboard after login, redirect them directly to Offers tab
                                    if (currentUrl.contains("dashboard") || currentUrl.contains("account/summary")) {
                                        statusText = "Logged in! Navigating to Amex Offers..."
                                        view?.loadUrl(issuer.offersUrl)
                                        return
                                    }

                                    // Auto-trigger when landing on offers page
                                    if (currentUrl.contains("offers") || currentUrl.contains("eligible")) {
                                        statusText = "Offers page loaded. Launching auto-activator..."
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
    window.isActivatingOffersRunning = true;

    function notify(msg) {
        console.log("[AmexOfferActivator] " + msg);
        if (window.AndroidBridge) {
            window.AndroidBridge.updateStatus(msg);
        }
    }

    function getEligibleButtons() {
        const candidates = Array.from(document.querySelectorAll('button, a, div[role="button"], span[role="button"]'));
        return candidates.filter(el => {
            const label = (el.innerText || el.getAttribute('aria-label') || el.getAttribute('title') || '').toLowerCase().trim();
            const isAdd = label.includes('add to card') || label.includes('activate offer') || label.includes('enroll in offer') || label === 'add to card';
            const isAlreadyAdded = label.includes('added to card') || label.includes('activated') || label.includes('enrolled');
            return isAdd && !isAlreadyAdded;
        });
    }

    let attempts = 0;
    const maxAttempts = 15;
    let totalActivated = 0;

    async function scanAndProcess() {
        attempts++;
        notify("Attempt " + attempts + "/" + maxAttempts + ": Scanning DOM for 'Add to Card' buttons...");
        
        // Scroll down slightly to trigger lazy-loaded cards
        window.scrollBy(0, 350);

        const buttons = getEligibleButtons();

        if (buttons.length > 0) {
            notify("Found " + buttons.length + " eligible offer(s). Activating now...");
            for (let i = 0; i < buttons.length; i++) {
                try {
                    const btn = buttons[i];
                    btn.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    btn.click();
                    totalActivated++;
                    notify("Activated " + totalActivated + " of " + buttons.length + " offers...");
                    await new Promise(r => setTimeout(r, 900 + Math.random() * 600));
                } catch (e) {
                    console.error("Click error:", e);
                }
            }
            notify("Finished activating " + totalActivated + " offer(s)!");
            if (window.AndroidBridge) {
                window.AndroidBridge.onComplete(totalActivated);
            }
            return;
        }

        if (attempts < maxAttempts) {
            notify("Attempt " + attempts + "/" + maxAttempts + ": Waiting for offer cards to render...");
            setTimeout(scanAndProcess, 1500);
        } else {
            notify("No unactivated offers detected. Make sure you are on the 'Amex Offers' tab.");
            if (window.AndroidBridge) {
                window.AndroidBridge.onComplete(0);
            }
        }
    }

    scanAndProcess();
})();
"""
