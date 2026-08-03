package com.example.amexbenefittracker.ui.offers

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray

/**
 * JavaScript ↔ Kotlin bridge for the Amex offer auto-activator.
 *
 * Provides two key capabilities:
 * 1. Status/completion callbacks from JS → Kotlin UI
 * 2. Native touch event injection: JS reports button coordinates,
 *    Kotlin dispatches real MotionEvent taps that are TRUSTED (isTrusted=true),
 *    bypassing React's synthetic event checks and bot detection.
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

    /**
     * Called by JS when all offers have been processed.
     * NOTE: JS calls this as AndroidBridge.onOffersActivated(count)
     */
    @JavascriptInterface
    fun onOffersActivated(count: Int) {
        mainHandler.post {
            onOffersActivated.invoke(count)
        }
    }

    /**
     * Receives a JSON array of button coordinates from JS and taps each one
     * using real Android MotionEvents. These events are TRUSTED and will be
     * processed by React's event system as genuine user touches.
     *
     * Expected JSON format: [{"x": 150.5, "y": 320.0, "label": "Walmart"}, ...]
     * Coordinates should be in CSS pixels. They will be converted to Android
     * view coordinates using the WebView's scale factor.
     */
    @JavascriptInterface
    fun tapButtons(coordinatesJson: String) {
        Log.d("AmexOfferBridge", "Received coordinates: ${coordinatesJson.take(200)}")
        mainHandler.post {
            try {
                val wv = webView ?: run {
                    Log.e("AmexOfferBridge", "WebView is null!")
                    onStatusUpdate("Error: WebView not available")
                    return@post
                }

                val coords = JSONArray(coordinatesJson)
                val count = coords.length()
                onStatusUpdate("Tapping $count offer buttons with native touch events...")

                // Get the scale factor to convert CSS pixels → Android view pixels
                val scale = wv.scale

                // Tap each button with a delay between taps
                var tapped = 0
                fun tapNext(index: Int) {
                    if (index >= count) {
                        onStatusUpdate("Done! Tapped $tapped offer(s).")
                        onOffersActivated.invoke(tapped)
                        return
                    }

                    val obj = coords.getJSONObject(index)
                    val cssX = obj.getDouble("x").toFloat()
                    val cssY = obj.getDouble("y").toFloat()
                    val label = obj.optString("label", "offer")

                    // Convert CSS pixels to Android view pixels
                    val viewX = cssX * scale
                    val viewY = cssY * scale

                    Log.d("AmexOfferBridge", "Tapping #$index '$label' at CSS($cssX, $cssY) → View($viewX, $viewY) scale=$scale")

                    // First scroll the element into view via JS
                    wv.evaluateJavascript(
                        "(function(){ var els = document.elementsFromPoint($cssX, $cssY); if(els.length>0) els[0].scrollIntoView({block:'center'}); })();",
                        null
                    )

                    // Dispatch MotionEvent after a brief delay for scroll to settle
                    mainHandler.postDelayed({
                        try {
                            // Recalculate Y after potential scroll
                            val downTime = SystemClock.uptimeMillis()

                            // ACTION_DOWN
                            val downEvent = MotionEvent.obtain(
                                downTime, downTime,
                                MotionEvent.ACTION_DOWN,
                                viewX, viewY, 0
                            )
                            wv.dispatchTouchEvent(downEvent)
                            downEvent.recycle()

                            // ACTION_UP after 80ms (simulates a real tap duration)
                            mainHandler.postDelayed({
                                val upEvent = MotionEvent.obtain(
                                    downTime, SystemClock.uptimeMillis(),
                                    MotionEvent.ACTION_UP,
                                    viewX, viewY, 0
                                )
                                wv.dispatchTouchEvent(upEvent)
                                upEvent.recycle()

                                tapped++
                                onStatusUpdate("Tapped $tapped/$count: $label")
                                Log.d("AmexOfferBridge", "Tapped #$index '$label' successfully")

                                // Next tap after 1.5 seconds
                                mainHandler.postDelayed({ tapNext(index + 1) }, 1500)
                            }, 80)
                        } catch (e: Exception) {
                            Log.e("AmexOfferBridge", "Tap error on #$index: ${e.message}")
                            mainHandler.postDelayed({ tapNext(index + 1) }, 500)
                        }
                    }, 300)
                }

                tapNext(0)
            } catch (e: Exception) {
                Log.e("AmexOfferBridge", "Error parsing coordinates: ${e.message}")
                onStatusUpdate("Error: ${e.message}")
            }
        }
    }
}
