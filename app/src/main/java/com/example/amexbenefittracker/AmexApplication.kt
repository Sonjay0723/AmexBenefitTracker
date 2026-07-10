package com.example.amexbenefittracker

import android.app.Application
import androidx.room.Room
import com.example.amexbenefittracker.data.local.AppDatabase
import com.example.amexbenefittracker.data.local.DatabaseInitializer
import com.example.amexbenefittracker.data.repository.AuthRepository
import com.example.amexbenefittracker.data.repository.BenefitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.amexbenefittracker.worker.UnusedCreditWorker
import java.util.concurrent.TimeUnit

class AmexApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME,
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    val repository by lazy {
        BenefitRepository(
            database.cardDao(),
            database.benefitDao(),
            database.usageHistoryDao(),
            applicationScope
        )
    }

    val authRepository by lazy { AuthRepository() }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            DatabaseInitializer(repository).initialize()
        }

        // Schedule periodic task to check for unused benefits on the 20th of the month
        val workRequest = PeriodicWorkRequestBuilder<UnusedCreditWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UnusedCreditWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
