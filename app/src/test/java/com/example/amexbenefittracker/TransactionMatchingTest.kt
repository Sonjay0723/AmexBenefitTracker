package com.example.amexbenefittracker

import com.example.amexbenefittracker.data.local.dao.CardDao
import com.example.amexbenefittracker.data.local.dao.BenefitDao
import com.example.amexbenefittracker.data.local.dao.UsageHistoryDao
import com.example.amexbenefittracker.data.local.dao.TransactionDao
import com.example.amexbenefittracker.data.local.entities.Card
import com.example.amexbenefittracker.data.local.entities.Benefit
import com.example.amexbenefittracker.data.local.entities.BenefitType
import com.example.amexbenefittracker.data.local.entities.UsageHistory
import com.example.amexbenefittracker.data.local.entities.Transaction
import com.example.amexbenefittracker.data.repository.BenefitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionMatchingTest {

    @Test
    fun testTransactionMatchingRules() {
        val fakeCardDao = object : CardDao {
            override fun getAllCards(): Flow<List<Card>> = flowOf(emptyList())
            override suspend fun getAllCardsDirect(): List<Card> = emptyList()
            override suspend fun getCardById(id: Long): Card? = null
            override suspend fun insertCard(card: Card): Long = 0L
            override suspend fun updateCard(card: Card) {}
            override suspend fun deleteCard(card: Card) {}
        }

        val fakeBenefitDao = object : BenefitDao {
            override fun getBenefitsForCard(cardId: Long): Flow<List<Benefit>> = flowOf(emptyList())
            override suspend fun getBenefitsForCardDirect(cardId: Long): List<Benefit> = emptyList()
            override suspend fun insertBenefit(benefit: Benefit): Long = 0L
            override suspend fun getBenefitById(id: Long): Benefit? = null
            override suspend fun getBenefitsByName(name: String): List<Benefit> = emptyList()
            override suspend fun deleteBenefitByName(name: String) {}
        }

        val fakeUsageHistoryDao = object : UsageHistoryDao {
            override fun getUsageForBenefit(benefitId: Long): Flow<List<UsageHistory>> = flowOf(emptyList())
            override suspend fun getUsageForPeriod(benefitId: Long, periodIdentifier: String): UsageHistory? = null
            override suspend fun insertUsage(usage: UsageHistory) {}
            override suspend fun deleteUsage(benefitId: Long, periodIdentifier: String) {}
            override fun getUsageForCard(cardId: Long): Flow<List<UsageHistory>> = flowOf(emptyList())
            override fun getAllUsage(): Flow<List<UsageHistory>> = flowOf(emptyList())
            override suspend fun getAllUsageDirect(): List<UsageHistory> = emptyList()
            override suspend fun deleteAllUsage() {}
            override suspend fun deleteUsageForBenefitByName(benefitName: String) {}
        }

        val fakeTransactionDao = object : TransactionDao {
            override fun getAllTransactions(): Flow<List<Transaction>> = flowOf(emptyList())
            override fun getTransactionsForCard(cardId: Long): Flow<List<Transaction>> = flowOf(emptyList())
            override suspend fun getTransactionsForCardDirect(cardId: Long): List<Transaction> = emptyList()
            override suspend fun insertTransactions(transactions: List<Transaction>) {}
            override suspend fun deleteTransactionsForCard(cardId: Long) {}
            override suspend fun deleteAllTransactions() {}
        }

        val fakeScope = CoroutineScope(SupervisorJob())

        val repository = BenefitRepository(
            fakeCardDao,
            fakeBenefitDao,
            fakeUsageHistoryDao,
            fakeTransactionDao,
            fakeScope
        )

        // Uber Cash matches
        assertTrue(repository.matchTransactionToBenefit("UBER EATS CHICAGO IL", "Uber Cash"))
        assertTrue(repository.matchTransactionToBenefit("Uber Trip / Pending", "Uber Cash"))
        assertFalse(repository.matchTransactionToBenefit("UBER ONE MONTHLY", "Uber Cash"))

        // Uber One matches
        assertTrue(repository.matchTransactionToBenefit("UBER ONE MEMBERSHIP", "Uber One"))

        // Dunkin' matches
        assertTrue(repository.matchTransactionToBenefit("DUNKIN DONUTS #3492", "Dunkin' Credit"))
        assertTrue(repository.matchTransactionToBenefit("AMEX DUNKIN' CREDIT", "Dunkin' Credit", -7.00))

        // Dining matches
        assertTrue(repository.matchTransactionToBenefit("GRUBHUB*CHICAGO", "Dining Credit"))
        assertTrue(repository.matchTransactionToBenefit("SHAKE SHACK NEW YORK", "Dining Credit"))
        assertTrue(repository.matchTransactionToBenefit("THE CHEESECAKE FACTORY", "Dining Credit"))
        assertTrue(repository.matchTransactionToBenefit("AMEX Dining Credit", "Dining Credit", -10.00))

        // Resy matches
        assertTrue(repository.matchTransactionToBenefit("RESY.COM DINING SEATTLE", "Resy Credit"))

        // CLEAR+ matches
        assertTrue(repository.matchTransactionToBenefit("CLEAR*MEMBERSHIP", "CLEAR+ Credit"))

        // Walmart+ matches
        assertTrue(repository.matchTransactionToBenefit("WALMART PLUS MONTHLY", "Walmart+"))
        assertTrue(repository.matchTransactionToBenefit("WALMART", "Walmart+", -12.95))
        assertTrue(repository.matchTransactionToBenefit("Walmart", "Walmart+", -13.81))
        assertTrue(repository.matchTransactionToBenefit("WALMART STORE #4920", "Walmart+", -13.84))
        assertFalse(repository.matchTransactionToBenefit("WALMART GROCERY", "Walmart+", 13.00))
        assertFalse(repository.matchTransactionToBenefit("WALMART GROCERY", "Walmart+", -50.00))

        // Unmatched remains false
        assertFalse(repository.matchTransactionToBenefit("TARGET STORE #2031", "Uber Cash"))
        assertFalse(repository.matchTransactionToBenefit("NETFLIX.COM", "Digital Entertainment")) // Netflix is not in our list
    }

    @Test
    fun testWalmartBenefitMigration() {
        val insertedBenefits = mutableListOf<Benefit>()
        val fakeBenefitDao = object : BenefitDao {
            override fun getBenefitsForCard(cardId: Long): Flow<List<Benefit>> = flowOf(emptyList())
            override suspend fun getBenefitsForCardDirect(cardId: Long): List<Benefit> = emptyList()
            override suspend fun insertBenefit(benefit: Benefit): Long {
                insertedBenefits.add(benefit)
                return benefit.id
            }
            override suspend fun getBenefitById(id: Long): Benefit? = null
            override suspend fun getBenefitsByName(name: String): List<Benefit> {
                if (name == "Walmart+") {
                    return listOf(
                        Benefit(
                            id = 42L,
                            cardId = 1L,
                            name = "Walmart+",
                            description = "Annual membership credit",
                            totalValue = 98.0,
                            type = BenefitType.ANNUAL,
                            displayOrder = 6
                        )
                    )
                }
                return emptyList()
            }
            override suspend fun deleteBenefitByName(name: String) {}
        }

        val fakeCardDao = object : CardDao {
            override fun getAllCards(): Flow<List<Card>> = flowOf(emptyList())
            override suspend fun getAllCardsDirect(): List<Card> = emptyList()
            override suspend fun getCardById(id: Long): Card? = null
            override suspend fun insertCard(card: Card): Long = 0L
            override suspend fun updateCard(card: Card) {}
            override suspend fun deleteCard(card: Card) {}
        }

        val fakeUsageHistoryDao = object : UsageHistoryDao {
            override fun getUsageForBenefit(benefitId: Long): Flow<List<UsageHistory>> = flowOf(emptyList())
            override suspend fun getUsageForPeriod(benefitId: Long, periodIdentifier: String): UsageHistory? = null
            override suspend fun insertUsage(usage: UsageHistory) {}
            override suspend fun deleteUsage(benefitId: Long, periodIdentifier: String) {}
            override fun getUsageForCard(cardId: Long): Flow<List<UsageHistory>> = flowOf(emptyList())
            override fun getAllUsage(): Flow<List<UsageHistory>> = flowOf(emptyList())
            override suspend fun getAllUsageDirect(): List<UsageHistory> = emptyList()
            override suspend fun deleteAllUsage() {}
            override suspend fun deleteUsageForBenefitByName(benefitName: String) {}
        }

        val fakeTransactionDao = object : TransactionDao {
            override fun getAllTransactions(): Flow<List<Transaction>> = flowOf(emptyList())
            override fun getTransactionsForCard(cardId: Long): Flow<List<Transaction>> = flowOf(emptyList())
            override suspend fun getTransactionsForCardDirect(cardId: Long): List<Transaction> = emptyList()
            override suspend fun insertTransactions(transactions: List<Transaction>) {}
            override suspend fun deleteTransactionsForCard(cardId: Long) {}
            override suspend fun deleteAllTransactions() {}
        }

        val repository = BenefitRepository(
            fakeCardDao,
            fakeBenefitDao,
            fakeUsageHistoryDao,
            fakeTransactionDao,
            CoroutineScope(SupervisorJob())
        )

        val initializer = com.example.amexbenefittracker.data.local.DatabaseInitializer(repository)
        
        kotlinx.coroutines.runBlocking {
            initializer.initialize()
        }

        val migratedWalmart = insertedBenefits.find { it.name == "Walmart+" }
        org.junit.Assert.assertNotNull("Walmart+ benefit should be migrated/inserted", migratedWalmart)
        org.junit.Assert.assertEquals(42L, migratedWalmart!!.id)
        org.junit.Assert.assertEquals(1L, migratedWalmart.cardId)
        org.junit.Assert.assertEquals(BenefitType.MONTHLY, migratedWalmart.type)
        org.junit.Assert.assertEquals(12.95, migratedWalmart.monthlyValue, 0.0)
        org.junit.Assert.assertEquals(12.95, migratedWalmart.decemberValue, 0.0)
        org.junit.Assert.assertEquals(155.4, migratedWalmart.totalValue, 0.0)
        org.junit.Assert.assertEquals("$12.95 per month", migratedWalmart.description)
    }
}
