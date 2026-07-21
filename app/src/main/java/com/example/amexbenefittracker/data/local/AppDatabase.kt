package com.example.amexbenefittracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.amexbenefittracker.data.local.dao.BenefitDao
import com.example.amexbenefittracker.data.local.dao.CardDao
import com.example.amexbenefittracker.data.local.dao.UsageHistoryDao
import com.example.amexbenefittracker.data.local.dao.TransactionDao
import com.example.amexbenefittracker.data.local.entities.Benefit
import com.example.amexbenefittracker.data.local.entities.Card
import com.example.amexbenefittracker.data.local.entities.UsageHistory
import com.example.amexbenefittracker.data.local.entities.Transaction

@Database(entities = [Card::class, Benefit::class, UsageHistory::class, Transaction::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun benefitDao(): BenefitDao
    abstract fun usageHistoryDao(): UsageHistoryDao
    abstract fun transactionDao(): TransactionDao


    companion object {
        const val DATABASE_NAME = "amex_benefit_tracker_db"
    }
}
