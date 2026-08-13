package com.example.amexbenefittracker.data.remote

import android.content.Context
import com.example.amexbenefittracker.BuildConfig
import com.example.amexbenefittracker.data.local.entities.Card
import com.example.amexbenefittracker.util.toSlug
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Talks to the Cloudflare Plaid broker's authenticated /plaid/... routes.
 *
 * As of the worker's KV-backed auth rework, the worker is the sole holder of
 * the Plaid access token, sync cursor, and card-to-account mappings, keyed
 * by Firebase uid. This class therefore no longer persists any of that
 * locally (no access_token, no sync_cursor, no per-card mapping prefs) - a
 * Plaid connection linked on one device is available on every device signed
 * into the same account because Cloudflare KV, not this SharedPreferences
 * file, is now the shared source of truth. [FirebaseAuthInterceptor]
 * attaches the caller's ID token to every request; the worker derives the
 * uid from it, so requests no longer carry userId/accessToken at all.
 */
class PlaidManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("plaid_prefs", Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(FirebaseAuthInterceptor())
        .addInterceptor(HttpLoggingInterceptor().apply {
            // BODY logging would print the bearer token now carried on
            // every request (and previously the raw Plaid access token) in
            // cleartext, so it's restricted to debug builds.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://localhost/") // Fallback base URL
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val apiService = retrofit.create(PlaidApiService::class.java)

    // In-memory cache of the last known /plaid/status result, refreshed by
    // fetchStatus() and kept up to date by the mutating calls below. Never
    // persisted - the worker is the source of truth - and cleared on
    // sign-out via clearLocalState() so the next device user doesn't
    // briefly see a stale "connected" status.
    @Volatile private var cachedStatus: PlaidStatusResponse? = null

    // Cloud Function URL Management
    fun saveCloudFunctionUrl(url: String) {
        prefs.edit().putString("cloud_function_url", url).apply()
    }

    fun getCloudFunctionUrl(): String {
        val savedUrl = prefs.getString("cloud_function_url", "") ?: ""
        if (savedUrl.isNotEmpty() && !savedUrl.contains("localhost")) return savedUrl
        val buildConfigUrl = BuildConfig.PLAID_CLOUD_FUNCTION_URL
        if (buildConfigUrl.isNotEmpty() && !buildConfigUrl.contains("localhost")) return buildConfigUrl
        return "https://amex-plaid-broker.jpitta0723.workers.dev"
    }

    fun hasCloudFunctionUrl(): Boolean {
        return getCloudFunctionUrl().isNotEmpty()
    }

    /** Last-fetched connected state, without hitting the network. */
    fun isConnected(): Boolean = cachedStatus?.connected == true

    /** Last-fetched card_mappings (slug -> Plaid account id), without hitting the network. */
    fun cachedCardMappings(): Map<String, String> = cachedStatus?.cardMappings ?: emptyMap()

    /**
     * Clears locally cached Plaid UI state on sign-out. There is no access
     * token to clear anymore - the worker holds it - this only resets the
     * in-memory cache so the next signed-in user on this device doesn't see
     * the previous user's connected status/mappings before the first fetch.
     */
    fun clearLocalState() {
        cachedStatus = null
    }

    private fun getFullUrl(endpoint: String): String {
        val baseUrl = getCloudFunctionUrl().trim().trimEnd('/')
        return "$baseUrl$endpoint"
    }

    suspend fun createLinkToken(): String {
        val url = getFullUrl("/plaid/link-token")
        return apiService.createLinkToken(url).linkToken
    }

    suspend fun exchangePublicToken(publicToken: String) {
        val url = getFullUrl("/plaid/exchange")
        apiService.exchangePublicToken(url, TokenExchangeRequest(publicToken = publicToken))
        // The worker doesn't hand back an access_token anymore (it never
        // leaves KV) - callers should follow up with fetchStatus() to learn
        // the resulting connected/accounts state.
    }

    suspend fun fetchStatus(): PlaidStatusResponse {
        val url = getFullUrl("/plaid/status")
        val response = apiService.getStatus(url)
        cachedStatus = response
        return response
    }

    suspend fun getAccounts(): List<PlaidAccount> {
        val url = getFullUrl("/plaid/accounts")
        return apiService.getAccounts(url).accounts
    }

    suspend fun syncTransactions(): TransactionsSyncResponse {
        val url = getFullUrl("/plaid/sync")
        return apiService.syncTransactions(url)
    }

    /**
     * Commits the sync cursor after the caller has durably persisted the
     * transactions/claims from that sync. Returns false (instead of
     * throwing) on a 409 - meaning another device already advanced the
     * cursor first - so the caller can re-sync rather than silently
     * dropping transactions.
     */
    suspend fun commitCursor(cursor: String, fromCursor: String?): Boolean {
        val url = getFullUrl("/plaid/cursor")
        return try {
            apiService.commitCursor(url, CommitCursorRequest(cursor = cursor, fromCursor = fromCursor))
            true
        } catch (e: HttpException) {
            if (e.code() == 409) false else throw e
        }
    }

    suspend fun saveCardMappings(mappings: Map<String, String>): Map<String, String> {
        val url = getFullUrl("/plaid/mappings")
        val response = apiService.saveMappings(url, MappingsRequest(cardMappings = mappings))
        cachedStatus = cachedStatus?.copy(cardMappings = response.cardMappings)
        return response.cardMappings
    }

    suspend fun disconnect() {
        val url = getFullUrl("/plaid/disconnect")
        apiService.disconnect(url)
        cachedStatus = PlaidStatusResponse(connected = false, accounts = emptyList(), cardMappings = emptyMap())
    }

    /** One-time, insert-only import of a token this device still holds locally. */
    suspend fun migrate(accessToken: String, cardMappings: Map<String, String>): Boolean {
        val url = getFullUrl("/plaid/migrate")
        val response = apiService.migrate(
            url,
            MigrateRequest(accessToken = accessToken, cardMappings = cardMappings)
        )
        return response.migrated
    }

    /**
     * Maps a Plaid account id back to the local Room card it's assigned to,
     * using the last-fetched card_mappings (slug -> account id) and each
     * card's stable slug. Returns null if not mapped or status hasn't been
     * fetched yet.
     */
    fun getCardIdForPlaidAccount(plaidAccountId: String, cards: List<Card>): Long? {
        if (plaidAccountId.isBlank()) return null
        val mappings = cachedStatus?.cardMappings ?: return null
        val slug = mappings.entries.find { it.value == plaidAccountId }?.key ?: return null
        return cards.find { it.name.toSlug() == slug }?.id
    }

    // --- Legacy local prefs -------------------------------------------
    // Retained only so a device that linked Plaid before this change can
    // migrate its locally-held access_token/card_mapping_* prefs to the
    // worker via migrate() above. Deleted afterward - see
    // DashboardViewModel's one-time migration on first refresh.

    fun getLegacyAccessToken(): String? = prefs.getString("access_token", null)

    fun getLegacyCardMapping(cardId: Long): String? = prefs.getString("card_mapping_$cardId", null)

    fun clearLegacyPlaidPrefs() {
        val editor = prefs.edit()
        editor.remove("access_token")
        editor.remove("sync_cursor")
        prefs.all.keys.filter { it.startsWith("card_mapping_") }.forEach { editor.remove(it) }
        editor.apply()
    }
}
