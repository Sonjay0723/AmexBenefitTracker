package com.example.amexbenefittracker.data.remote

import android.content.Context
import com.example.amexbenefittracker.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class PlaidManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("plaid_prefs", Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://localhost/") // Fallback base URL
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val apiService = retrofit.create(PlaidApiService::class.java)

    // Cloud Function URL Management
    fun saveCloudFunctionUrl(url: String) {
        prefs.edit().putString("cloud_function_url", url).apply()
    }

    fun getCloudFunctionUrl(): String {
        val savedUrl = prefs.getString("cloud_function_url", "") ?: ""
        if (savedUrl.isNotEmpty()) return savedUrl
        return BuildConfig.PLAID_CLOUD_FUNCTION_URL
    }

    fun hasCloudFunctionUrl(): Boolean {
        return getCloudFunctionUrl().isNotEmpty()
    }


    fun saveAccessToken(accessToken: String) {
        prefs.edit().putString("access_token", accessToken).apply()
    }

    fun getAccessToken(): String? {
        return prefs.getString("access_token", null)
    }

    fun hasAccessToken(): Boolean {
        return getAccessToken() != null
    }

    fun saveCardMapping(cardId: Long, plaidAccountId: String) {
        prefs.edit().putString("card_mapping_$cardId", plaidAccountId).apply()
    }

    fun getCardMapping(cardId: Long): String? {
        return prefs.getString("card_mapping_$cardId", null)
    }

    fun getCardIdForPlaidAccount(plaidAccountId: String): Long? {
        val allKeys = prefs.all
        for ((key, value) in allKeys) {
            if (key.startsWith("card_mapping_") && value == plaidAccountId) {
                return key.removePrefix("card_mapping_").toLongOrNull()
            }
        }
        return null
    }

    fun saveSyncCursor(cursor: String?) {
        prefs.edit().putString("sync_cursor", cursor).apply()
    }

    fun getSyncCursor(): String? {
        return prefs.getString("sync_cursor", null)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun getFullUrl(endpoint: String): String {
        val baseUrl = getCloudFunctionUrl().trim().trimEnd('/')
        return "$baseUrl$endpoint"
    }

    suspend fun createLinkToken(clientUserId: String): String {
        val url = getFullUrl("/create-link-token")
        val request = LinkTokenRequest(userId = clientUserId)
        val response = apiService.createLinkToken(url, request)
        return response.linkToken
    }

    suspend fun exchangePublicToken(publicToken: String): String {
        val url = getFullUrl("/exchange-token")
        val request = TokenExchangeRequest(publicToken = publicToken)
        val response = apiService.exchangePublicToken(url, request)
        saveAccessToken(response.accessToken)
        // Reset sync cursor on new link
        saveSyncCursor(null)
        return response.accessToken
    }

    suspend fun getAccounts(accessToken: String): List<PlaidAccount> {
        val url = getFullUrl("/accounts")
        val request = AccountsGetRequest(accessToken = accessToken)
        val response = apiService.getAccounts(url, request)
        return response.accounts
    }

    suspend fun syncTransactions(accessToken: String): List<PlaidTransaction> {
        val url = getFullUrl("/sync-transactions")
        val addedTransactions = mutableListOf<PlaidTransaction>()
        var cursor = getSyncCursor()
        var hasMore = true

        while (hasMore) {
            val request = TransactionsSyncRequest(
                accessToken = accessToken,
                cursor = cursor
            )
            val response = apiService.syncTransactions(url, request)
            addedTransactions.addAll(response.added)
            cursor = response.nextCursor
            hasMore = response.hasMore
            saveSyncCursor(cursor)
        }

        return addedTransactions
    }
}
