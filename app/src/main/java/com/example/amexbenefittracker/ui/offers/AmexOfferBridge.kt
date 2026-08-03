package com.example.amexbenefittracker.ui.offers

import android.webkit.JavascriptInterface

class AmexOfferBridge(
    private val onStatusUpdate: (String) -> Unit,
    private val onOffersActivated: (Int) -> Unit
) {
    @JavascriptInterface
    fun updateStatus(message: String) {
        onStatusUpdate(message)
    }

    @JavascriptInterface
    fun onComplete(count: Int) {
        onOffersActivated(count)
    }
}
