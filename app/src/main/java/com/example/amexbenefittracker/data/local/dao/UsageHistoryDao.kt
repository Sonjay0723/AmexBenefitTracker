package com.example.amexbenefittracker.data.local.dao

import androidx.room.*
import com.example.amexbenefittracker.data.local.entities.UsageHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageHistoryDao {
    @Query("SELECT * FROM usage_history WHERE benefitId = :benefitId")
    fun getUsageForBenefit(benefitId: Long): Flow<List<UsageHistory>>

    @Query("SELECT * FROM usage_history WHERE benefitId = :benefitId AND periodIdentifier = :periodIdentifier")
    suspend fun getUsageForPeriod(benefitId: Long, periodIdentifier: String): UsageHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsage(usage: UsageHistory)

    @Query("DELETE FROM usage_history WHERE benefitId = :benefitId AND periodIdentifier = :periodIdentifier")
    suspend fun deleteUsage(benefitId: Long, periodIdentifier: String)

    @Query("SELECT uh.* FROM usage_history uh INNER JOIN benefits b ON uh.benefitId = b.id WHERE b.cardId = :cardId")
    fun getUsageForCard(cardId: Long): Flow<List<UsageHistory>>

    @Query("SELECT * FROM usage_history")
    fun getAllUsage(): Flow<List<UsageHistory>>

    @Query("SELECT * FROM usage_history")
    suspend fun getAllUsageDirect(): List<UsageHistory>

    @Query("DELETE FROM usage_history")
    suspend fun deleteAllUsage()

    @Query("DELETE FROM usage_history WHERE benefitId IN (SELECT id FROM benefits WHERE name = :benefitName)")
    suspend fun deleteUsageForBenefitByName(benefitName: String)
}
