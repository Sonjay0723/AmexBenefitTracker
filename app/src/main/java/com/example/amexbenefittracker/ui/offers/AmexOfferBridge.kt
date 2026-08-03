package com.example.amexbenefittracker.ui.offers

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

class AmexOfferBridge(
    private val onStatusUpdate: (String) -> Unit,
    private val onOffersActivated: (Int) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun updateStatus(message: String) {
        mainHandler.post {
            onStatusUpdate(message)
        }
    }

    @JavascriptInterface
    fun onComplete(count: Int) {
        mainHandler.post {
            onOffersActivated(count)
        }
    }
}
