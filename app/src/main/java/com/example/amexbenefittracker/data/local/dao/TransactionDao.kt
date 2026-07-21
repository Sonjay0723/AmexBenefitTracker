package com.example.amexbenefittracker.data.local.dao

import androidx.room.*
import com.example.amexbenefittracker.data.local.entities.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE cardId = :cardId ORDER BY date DESC")
    fun getTransactionsForCard(cardId: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE cardId = :cardId ORDER BY date DESC")
    suspend fun getTransactionsForCardDirect(cardId: Long): List<Transaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<Transaction>)

    @Query("DELETE FROM transactions WHERE cardId = :cardId")
    suspend fun deleteTransactionsForCard(cardId: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}
