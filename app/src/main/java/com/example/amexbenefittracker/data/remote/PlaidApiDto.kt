package com.example.amexbenefittracker.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// -- /plaid/link-token -------------------------------------------------
// No request body: the worker derives client_user_id from the caller's
// verified Firebase uid rather than trusting a client-supplied value.

@JsonClass(generateAdapter = true)
data class LinkTokenResponse(
    @Json(name = "link_token") val linkToken: String
)

// -- /plaid/exchange -----------------------------------------------------

@JsonClass(generateAdapter = true)
data class TokenExchangeRequest(
    @Json(name = "publicToken") val publicToken: String
)

@JsonClass(generateAdapter = true)
data class TokenExchangeResponse(
    @Json(name = "connected") val connected: Boolean
)

// -- Shared account shape --------------------------------------------------

@JsonClass(generateAdapter = true)
data class PlaidAccount(
    @Json(name = "account_id") val accountId: String,
    @Json(name = "mask") val mask: String?,
    @Json(name = "name") val name: String,
    @Json(name = "official_name") val officialName: String?,
    @Json(name = "type") val type: String,
    @Json(name = "subtype") val subtype: String?
)

// -- /plaid/status ---------------------------------------------------------
// Sole source of truth for "is this account linked" - the worker is the
// only holder of the access token, so there is no local equivalent anymore.

@JsonClass(generateAdapter = true)
data class PlaidStatusResponse(
    @Json(name = "connected") val connected: Boolean,
    @Json(name = "accounts") val accounts: List<PlaidAccount>? = null,
    @Json(name = "card_mappings") val cardMappings: Map<String, String>? = null
)

// -- /plaid/accounts ---------------------------------------------------

@JsonClass(generateAdapter = true)
data class AccountsGetResponse(
    @Json(name = "accounts") val accounts: List<PlaidAccount>
)

// -- /plaid/sync + /plaid/cursor -------------------------------------------
// No request body for sync: the worker pages through Plaid's cursor using
// its own KV-stored cursor. The cursor is NOT committed by /plaid/sync - see
// PlaidManager.commitCursor for why it's a separate, explicit step.

@JsonClass(generateAdapter = true)
data class PlaidTransaction(
    @Json(name = "transaction_id") val transactionId: String,
    @Json(name = "account_id") val accountId: String,
    @Json(name = "amount") val amount: Double,
    @Json(name = "date") val date: String, // YYYY-MM-DD
    @Json(name = "name") val name: String,
    @Json(name = "original_description") val originalDescription: String? = null,
    @Json(name = "merchant_name") val merchantName: String? = null,
    @Json(name = "pending") val pending: Boolean
)

@JsonClass(generateAdapter = true)
data class TransactionsSyncResponse(
    @Json(name = "added") val added: List<PlaidTransaction>,
    @Json(name = "from_cursor") val fromCursor: String?,
    @Json(name = "next_cursor") val nextCursor: String
)

@JsonClass(generateAdapter = true)
data class CommitCursorRequest(
    @Json(name = "cursor") val cursor: String,
    @Json(name = "fromCursor") val fromCursor: String?
)

@JsonClass(generateAdapter = true)
data class CommitCursorResponse(
    @Json(name = "ok") val ok: Boolean
)

// -- /plaid/mappings ---------------------------------------------------
// Keyed by the stable card slug (e.g. "the_platinum_card"), not a Room row
// id - Room ids are local and meaningless to the worker/other devices.

@JsonClass(generateAdapter = true)
data class MappingsRequest(
    @Json(name = "card_mappings") val cardMappings: Map<String, String>
)

@JsonClass(generateAdapter = true)
data class MappingsResponse(
    @Json(name = "card_mappings") val cardMappings: Map<String, String>
)

// -- /plaid/disconnect ---------------------------------------------------

@JsonClass(generateAdapter = true)
data class DisconnectResponse(
    @Json(name = "ok") val ok: Boolean
)

// -- /plaid/migrate ---------------------------------------------------
// One-time, insert-only import of a token this device still holds locally
// from before the worker became the sole token holder.

@JsonClass(generateAdapter = true)
data class MigrateRequest(
    @Json(name = "accessToken") val accessToken: String,
    @Json(name = "card_mappings") val cardMappings: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class MigrateResponse(
    @Json(name = "migrated") val migrated: Boolean
)
