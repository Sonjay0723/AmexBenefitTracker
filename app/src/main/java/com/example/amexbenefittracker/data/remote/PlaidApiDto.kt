package com.example.amexbenefittracker.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LinkTokenRequest(
    @Json(name = "userId") val userId: String
)

@JsonClass(generateAdapter = true)
data class LinkTokenResponse(
    @Json(name = "link_token") val linkToken: String
)

@JsonClass(generateAdapter = true)
data class TokenExchangeRequest(
    @Json(name = "publicToken") val publicToken: String
)

@JsonClass(generateAdapter = true)
data class TokenExchangeResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "item_id") val itemId: String
)

@JsonClass(generateAdapter = true)
data class AccountsGetRequest(
    @Json(name = "accessToken") val accessToken: String
)

@JsonClass(generateAdapter = true)
data class PlaidAccount(
    @Json(name = "account_id") val accountId: String,
    @Json(name = "mask") val mask: String?,
    @Json(name = "name") val name: String,
    @Json(name = "official_name") val officialName: String?,
    @Json(name = "type") val type: String,
    @Json(name = "subtype") val subtype: String?
)

@JsonClass(generateAdapter = true)
data class AccountsGetResponse(
    @Json(name = "accounts") val accounts: List<PlaidAccount>
)

@JsonClass(generateAdapter = true)
data class TransactionsSyncRequest(
    @Json(name = "accessToken") val accessToken: String,
    @Json(name = "cursor") val cursor: String? = null,
    @Json(name = "count") val count: Int = 100
)

@JsonClass(generateAdapter = true)
data class PlaidTransaction(
    @Json(name = "transaction_id") val transactionId: String,
    @Json(name = "account_id") val accountId: String,
    @Json(name = "amount") val amount: Double,
    @Json(name = "date") val date: String, // YYYY-MM-DD
    @Json(name = "name") val name: String,
    @Json(name = "pending") val pending: Boolean
)

@JsonClass(generateAdapter = true)
data class TransactionsSyncResponse(
    @Json(name = "added") val added: List<PlaidTransaction>,
    @Json(name = "modified") val modified: List<PlaidTransaction>,
    @Json(name = "removed") val removed: List<Map<String, Any>>,
    @Json(name = "next_cursor") val nextCursor: String,
    @Json(name = "has_more") val hasMore: Boolean
)
