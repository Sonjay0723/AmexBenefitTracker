package com.example.amexbenefittracker.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

// All calls hit the worker's authenticated /plaid/* routes; the
// FirebaseAuthInterceptor attaches the caller's ID token, so none of these
// carry userId/accessToken - the worker derives the uid from the token and
// looks up its own KV-held access token server-side.
interface PlaidApiService {
    @POST
    suspend fun createLinkToken(@Url url: String): LinkTokenResponse

    @POST
    suspend fun exchangePublicToken(
        @Url url: String,
        @Body request: TokenExchangeRequest
    ): TokenExchangeResponse

    @GET
    suspend fun getStatus(@Url url: String): PlaidStatusResponse

    @POST
    suspend fun getAccounts(@Url url: String): AccountsGetResponse

    @POST
    suspend fun syncTransactions(@Url url: String): TransactionsSyncResponse

    @POST
    suspend fun commitCursor(
        @Url url: String,
        @Body request: CommitCursorRequest
    ): CommitCursorResponse

    @POST
    suspend fun saveMappings(
        @Url url: String,
        @Body request: MappingsRequest
    ): MappingsResponse

    @POST
    suspend fun disconnect(@Url url: String): DisconnectResponse

    @POST
    suspend fun migrate(
        @Url url: String,
        @Body request: MigrateRequest
    ): MigrateResponse
}
