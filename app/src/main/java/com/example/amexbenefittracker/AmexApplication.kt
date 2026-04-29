package com.example.amexbenefittracker

import android.app.Application
import androidx.room.Room
import com.example.amexbenefittracker.data.local.AppDatabase
import com.example.amexbenefittracker.data.local.DatabaseInitializer
import com.example.amexbenefittracker.data.repository.AuthRepository
import com.example.amexbenefittracker.data.repository.BenefitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AmexApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
    }

    val repository by lazy {
        BenefitRepository(
            database.cardDao(),
            database.benefitDao(),
            database.usageHistoryDao()
        )
    }

    val authRepository by lazy { AuthRepository() }

    private val applicationScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            DatabaseInitializer(repository).initialize()
        }
    }
}
