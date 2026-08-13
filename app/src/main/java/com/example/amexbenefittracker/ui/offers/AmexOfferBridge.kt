package com.example.amexbenefittracker.ui.offers

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * JavaScript ↔ Kotlin bridge for the Amex offer auto-activator.
 *
 * Key method: tapAt(x, y) receives coordinates in ANDROID VIEW PIXELS
 * (already converted from CSS pixels by JS using window.devicePixelRatio)
 * and dispatches a real MotionEvent tap that is TRUSTED.
 */
class AmexOfferBridge(
    private val onStatusUpdate: (String) -> Unit,
    private val onOffersActivated: (Int) -> Unit,
    private var webView: WebView? = null
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun setWebView(wv: WebView) {
        webView = wv
    }

    @JavascriptInterface
    fun updateStatus(message: String) {
        mainHandler.post {
            onStatusUpdate(message)
        }
    }

    @JavascriptInterface
    fun onOffersActivated(count: Int) {
        mainHandler.post {
            onOffersActivated.invoke(count)
        }
    }

    /**
     * Dispatches a single native MotionEvent tap at the given coordinates.
     * Coordinates must be in Android View pixels (CSS pixels * devicePixelRatio).
     *
     * The tap sequence is: ACTION_DOWN → 80ms delay → ACTION_UP
     * This simulates a natural human tap duration.
     */
    @JavascriptInterface
    fun tapAt(viewX: Float, viewY: Float, label: String) {
        Log.d("AmexOfferBridge", "tapAt($viewX, $viewY) label='$label'")
        mainHandler.post {
            val wv = webView ?: return@post
            try {
                val downTime = SystemClock.uptimeMillis()

                val downEvent = MotionEvent.obtain(
                    downTime, downTime,
                    MotionEvent.ACTION_DOWN,
                    viewX, viewY, 0
                )
                wv.dispatchTouchEvent(downEvent)
                downEvent.recycle()

                // ACTION_UP after 80ms to simulate a real tap
                mainHandler.postDelayed({
                    val upEvent = MotionEvent.obtain(
                        downTime, SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_UP,
                        viewX, viewY, 0
                    )
                    wv.dispatchTouchEvent(upEvent)
                    upEvent.recycle()
                    Log.d("AmexOfferBridge", "Tap completed: $label at ($viewX, $viewY)")
                }, 80)
            } catch (e: Exception) {
                Log.e("AmexOfferBridge", "Tap error: ${e.message}")
            }
        }
    }
}
