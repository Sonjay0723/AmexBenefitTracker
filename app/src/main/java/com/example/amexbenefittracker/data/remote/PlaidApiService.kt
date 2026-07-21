package com.example.amexbenefittracker.data.remote

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface PlaidApiService {
    @POST
    suspend fun createLinkToken(
        @Url url: String,
        @Body request: LinkTokenRequest
    ): LinkTokenResponse

    @POST
    suspend fun exchangePublicToken(
        @Url url: String,
        @Body request: TokenExchangeRequest
    ): TokenExchangeResponse

    @POST
    suspend fun getAccounts(
        @Url url: String,
        @Body request: AccountsGetRequest
    ): AccountsGetResponse

    @POST
    suspend fun syncTransactions(
        @Url url: String,
        @Body request: TransactionsSyncRequest
    ): TransactionsSyncResponse
}
