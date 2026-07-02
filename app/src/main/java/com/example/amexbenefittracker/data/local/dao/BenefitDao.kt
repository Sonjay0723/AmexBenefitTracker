package com.example.amexbenefittracker.data.local.dao

import androidx.room.*
import com.example.amexbenefittracker.data.local.entities.Benefit
import kotlinx.coroutines.flow.Flow

@Dao
interface BenefitDao {
    @Query("SELECT * FROM benefits WHERE cardId = :cardId ORDER BY displayOrder ASC")
    fun getBenefitsForCard(cardId: Long): Flow<List<Benefit>>

    @Query("SELECT * FROM benefits WHERE cardId = :cardId ORDER BY displayOrder ASC")
    suspend fun getBenefitsForCardDirect(cardId: Long): List<Benefit>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBenefit(benefit: Benefit): Long

    @Query("SELECT * FROM benefits WHERE id = :id")
    suspend fun getBenefitById(id: Long): Benefit?

    @Query("SELECT * FROM benefits WHERE name = :name")
    suspend fun getBenefitsByName(name: String): List<Benefit>

    @Query("DELETE FROM benefits WHERE name = :name")
    suspend fun deleteBenefitByName(name: String)
}
