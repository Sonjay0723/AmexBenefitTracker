package com.example.amexbenefittracker.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Attaches the signed-in user's Firebase ID token as a Bearer credential to
 * every outgoing request. The Cloudflare worker verifies the token itself
 * (see cloudflare-worker/auth.js) and derives the caller's uid from it, so
 * request bodies no longer need to carry userId or accessToken - the worker
 * is the sole holder of the Plaid access token now, keyed by that uid.
 *
 * [Interceptor.intercept] runs synchronously on OkHttp's own dispatcher
 * thread pool, never on the caller's coroutine thread, so a blocking
 * [Tasks.await] here is the standard, safe way to fetch the token - no
 * coroutine bridge (e.g. kotlinx-coroutines-play-services) is needed.
 */
class FirebaseAuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val user = FirebaseAuth.getInstance().currentUser
        val token = user?.let {
            try {
                Tasks.await(it.getIdToken(false), 15, TimeUnit.SECONDS)?.token
            } catch (e: Exception) {
                // No signed-in user or token refresh failed - proceed
                // unauthenticated and let the worker return 401 rather than
                // failing the request client-side.
                null
            }
        }
        val authedRequest = if (token != null) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            request
        }
        return chain.proceed(authedRequest)
    }
}
