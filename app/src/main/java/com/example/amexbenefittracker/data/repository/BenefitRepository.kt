package com.example.amexbenefittracker.data.repository

import com.example.amexbenefittracker.data.local.dao.BenefitDao
import com.example.amexbenefittracker.data.local.dao.CardDao
import com.example.amexbenefittracker.data.local.dao.UsageHistoryDao
import com.example.amexbenefittracker.data.local.entities.Benefit
import com.example.amexbenefittracker.data.local.entities.BenefitType
import com.example.amexbenefittracker.data.local.entities.Card
import com.example.amexbenefittracker.data.local.entities.UsageHistory
import com.example.amexbenefittracker.domain.model.CardSummary
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BenefitRepository(
    private val cardDao: CardDao,
    private val benefitDao: BenefitDao,
    private val usageHistoryDao: UsageHistoryDao,
    private val externalScope: CoroutineScope,
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun getAllCards(): Flow<List<Card>> = cardDao.getAllCards()

    fun getBenefitsForCard(cardId: Long): Flow<List<Benefit>> = benefitDao.getBenefitsForCard(cardId)

    fun getUsageForCard(cardId: Long): Flow<List<UsageHistory>> = usageHistoryDao.getUsageForCard(cardId)

    fun getCardSummary(cardId: Long): Flow<CardSummary?> {
        val cardFlow = cardDao.getAllCards().map { cards -> cards.find { it.id == cardId } }
        val usageFlow = usageHistoryDao.getUsageForCard(cardId)

        return combine(cardFlow, usageFlow) { card, usages ->
            if (card == null) return@combine null

            val totalClaimed = usages.sumOf { it.amountClaimed }
            val corpCreditValue = if (card.corporateCreditClaimed) card.corporateCredit else 0.0
            val effectiveFee = card.annualFee - corpCreditValue - totalClaimed
            val profit = if (effectiveFee < 0) -effectiveFee else 0.0

            CardSummary(
                cardId = card.id,
                cardName = card.name,
                standardAnnualFee = card.annualFee,
                corporateCredit = card.corporateCredit,
                corporateCreditClaimed = card.corporateCreditClaimed,
                totalBenefitsClaimed = totalClaimed,
                effectiveAnnualFee = effectiveFee,
                profit = profit
            )
        }
    }

    suspend fun toggleUsage(benefit: Benefit, periodIdentifier: String, date: Long) {
        val existing = usageHistoryDao.getUsageForPeriod(benefit.id, periodIdentifier)
        if (existing != null) {
            usageHistoryDao.deleteUsage(benefit.id, periodIdentifier)
            
            // Link Uber Cash logic
            if (benefit.name == "Uber Cash") {
                val otherUberBenefits = benefitDao.getBenefitsByName("Uber Cash")
                otherUberBenefits.forEach { other ->
                    if (other.id != benefit.id) {
                        usageHistoryDao.deleteUsage(other.id, periodIdentifier)
                    }
                }
            }
        } else {
            val amount = when (benefit.type) {
                BenefitType.MONTHLY -> {
                    if (periodIdentifier.endsWith("-12")) benefit.decemberValue else benefit.monthlyValue
                }
                BenefitType.QUARTERLY -> benefit.quarterlyValue
                BenefitType.SEMI_ANNUAL -> benefit.semiAnnualValue
                BenefitType.ANNUAL -> benefit.totalValue
            }
            val usage = UsageHistory(
                benefitId = benefit.id,
                amountClaimed = amount,
                dateClaimed = date,
                periodIdentifier = periodIdentifier
            )
            usageHistoryDao.insertUsage(usage)

            // Link Uber Cash logic
            if (benefit.name == "Uber Cash") {
                val otherUberBenefits = benefitDao.getBenefitsByName("Uber Cash")
                otherUberBenefits.forEach { other ->
                    if (other.id != benefit.id) {
                        val otherAmount = if (periodIdentifier.endsWith("-12")) other.decemberValue else other.monthlyValue
                        usageHistoryDao.insertUsage(
                            UsageHistory(
                                benefitId = other.id,
                                amountClaimed = otherAmount,
                                dateClaimed = date,
                                periodIdentifier = periodIdentifier
                            )
                        )
                    }
                }
            }
        }
        externalScope.launch {
            syncLocalToFirestore()
        }
    }

    suspend fun toggleCorporateCredit(cardId: Long) {
        val card = cardDao.getCardById(cardId)
        if (card != null) {
            cardDao.updateCard(card.copy(corporateCreditClaimed = !card.corporateCreditClaimed))
            externalScope.launch {
                syncLocalToFirestore()
            }
        }
    }

    suspend fun resetAllTracking() {
        usageHistoryDao.deleteAllUsage()
        val cards = cardDao.getAllCardsDirect()
        cards.forEach { card ->
            cardDao.updateCard(card.copy(corporateCreditClaimed = false))
        }
        
        // Also clear cloud data
        val userId = auth.currentUser?.uid ?: return
        externalScope.launch {
            try {
                firestore.collection("users").document(userId).delete().await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun refreshData(): String? {
        val userId = auth.currentUser?.uid ?: return null
        try {
            val snapshot = firestore.collection("users").document(userId).get().await()
            if (snapshot.exists()) {
                val data = snapshot.data ?: return null
                
                // Restore tracking year
                val cloudYear = data["tracking_year"] as? String
                
                // Clear local tracking before restoring
                usageHistoryDao.deleteAllUsage()
                
                // Restore Corporate Credit Status
                val cards = cardDao.getAllCardsDirect()
                cards.forEach { card ->
                    val claimed = (data["corp_credit_${card.name}"] as? Boolean) ?: false
                    cardDao.updateCard(card.copy(corporateCreditClaimed = claimed))
                }

                // Restore Benefit History from new 'claims' map
                @Suppress("UNCHECKED_CAST")
                val claims = data["claims"] as? Map<String, Any>
                if (claims != null) {
                    processClaimsMap(claims, cards)
                } else {
                    // Fallback to legacy 'history' array if 'claims' doesn't exist
                    @Suppress("UNCHECKED_CAST")
                    val legacyHistory = data["history"] as? List<Map<String, Any>>
                    legacyHistory?.let { processLegacyHistory(it, cards) }
                }
                return cloudYear
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private suspend fun processClaimsMap(claims: Map<String, Any>, cards: List<Card>) {
        claims.forEach { (cardSlug, years) ->
            val card = cards.find { it.name.toSlug() == cardSlug } ?: return@forEach
            @Suppress("UNCHECKED_CAST")
            val yearsMap = years as? Map<String, Any> ?: return@forEach
            
            yearsMap.forEach { (year, benefitMap) ->
                @Suppress("UNCHECKED_CAST")
                val benefitsInYear = benefitMap as? Map<String, Any> ?: return@forEach
                
                benefitsInYear.forEach { (benefitSlug, periods) ->
                    val benefit = benefitDao.getBenefitsForCardDirect(card.id).find { it.name.toSlug() == benefitSlug }
                    @Suppress("UNCHECKED_CAST")
                    val periodsMap = periods as? Map<String, Any> ?: return@forEach
                    
                    periodsMap.forEach { (periodKey, details) ->
                        @Suppress("UNCHECKED_CAST")
                        val detailsMap = details as? Map<String, Any> ?: return@forEach
                        val amount = (detailsMap["a"] as? Number)?.toDouble() ?: 0.0
                        val date = (detailsMap["d"] as? Number)?.toLong() ?: 0L
                        val periodIdentifier = if (periodKey == "Annual") "$year-Annual" else "$year-$periodKey"
                        
                        if (benefit != null) {
                            if (benefit.name == "Uber Cash") {
                                // Link Uber Cash across all cards
                                benefitDao.getBenefitsByName("Uber Cash").forEach { b ->
                                    if (usageHistoryDao.getUsageForPeriod(b.id, periodIdentifier) == null) {
                                        usageHistoryDao.insertUsage(
                                            UsageHistory(
                                                benefitId = b.id,
                                                amountClaimed = if (periodIdentifier.endsWith("-12")) b.decemberValue else b.monthlyValue,
                                                dateClaimed = date,
                                                periodIdentifier = periodIdentifier
                                            )
                                        )
                                    }
                                }
                            } else {
                                if (usageHistoryDao.getUsageForPeriod(benefit.id, periodIdentifier) == null) {
                                    usageHistoryDao.insertUsage(
                                        UsageHistory(
                                            benefitId = benefit.id,
                                            amountClaimed = amount,
                                            dateClaimed = date,
                                            periodIdentifier = periodIdentifier
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun processLegacyHistory(historyList: List<Map<String, Any>>, cards: List<Card>) {
        historyList.forEach { item ->
            val bName = item["name"] as? String
            val cName = item["card"] as? String
            val pId = item["period"] as? String
            val amount = (item["amount"] as? Number)?.toDouble() ?: 0.0
            val date = (item["date"] as? Number)?.toLong() ?: 0L
            
            if (bName != null && pId != null) {
                if (bName == "Uber Cash") {
                    val benefits = benefitDao.getBenefitsByName(bName)
                    benefits.forEach { b ->
                        usageHistoryDao.insertUsage(
                            UsageHistory(
                                benefitId = b.id,
                                amountClaimed = if (pId.endsWith("-12")) b.decemberValue else b.monthlyValue,
                                dateClaimed = date,
                                periodIdentifier = pId
                            )
                        )
                    }
                } else {
                    val card = cards.find { it.name == cName }
                    if (card != null) {
                        val benefit = benefitDao.getBenefitsForCardDirect(card.id).find { it.name == bName }
                        if (benefit != null) {
                            usageHistoryDao.insertUsage(
                                UsageHistory(
                                    benefitId = benefit.id,
                                    amountClaimed = amount,
                                    dateClaimed = date,
                                    periodIdentifier = pId
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun updateTrackingYear(year: String) {
        val userId = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(userId).update("tracking_year", year).await()
        } catch (e: Exception) {
            // If document doesn't exist, we might need a set
            syncLocalToFirestore()
        }
    }

    private suspend fun syncLocalToFirestore() {
        val userId = auth.currentUser?.uid ?: return
        try {
            val cards = cardDao.getAllCardsDirect()
            val allUsage = usageHistoryDao.getAllUsageDirect()
            
            val data = mutableMapOf<String, Any>()
            
            // Sync Corporate Credits
            cards.forEach { card ->
                data["corp_credit_${card.name}"] = card.corporateCreditClaimed
            }

            // Sync Tracking Year (local VM state might not be available here, but we can assume)
            // This sync method is used for benefit toggles too. 
            // In a real app, trackingYear might be in a PreferenceDataStore.
            // For now, we'll keep it as is.

            // Hierarchical Claims structure
            val claims = mutableMapOf<String, Any>()
            val processedUberCashPeriods = mutableSetOf<String>()
            
            allUsage.forEach { usage ->
                val benefit = benefitDao.getBenefitById(usage.benefitId) ?: return@forEach
                val card = cards.find { it.id == benefit.cardId } ?: return@forEach
                
                val cardKey = card.name.toSlug()
                val benefitKey = benefit.name.toSlug()
                
                // Parse period identifier (e.g., "2026-01" -> year: "2026", period: "01")
                val parts = usage.periodIdentifier.split("-")
                val year = parts.getOrNull(0) ?: "Unknown"
                val periodKey = parts.getOrNull(1) ?: "Annual"

                // Uber Cash is linked across cards, so we only need to store it once per period
                if (benefit.name == "Uber Cash") {
                    if (processedUberCashPeriods.contains(usage.periodIdentifier)) {
                        return@forEach
                    }
                    processedUberCashPeriods.add(usage.periodIdentifier)
                }

                @Suppress("UNCHECKED_CAST")
                val cardMap = claims.getOrPut(cardKey) { mutableMapOf<String, Any>() } as MutableMap<String, Any>
                @Suppress("UNCHECKED_CAST")
                val yearMap = cardMap.getOrPut(year) { mutableMapOf<String, Any>() } as MutableMap<String, Any>
                @Suppress("UNCHECKED_CAST")
                val benefitMap = yearMap.getOrPut(benefitKey) { mutableMapOf<String, Any>() } as MutableMap<String, Any>
                
                benefitMap[periodKey] = mapOf(
                    "a" to usage.amountClaimed,
                    "d" to usage.dateClaimed
                )
            }
            data["claims"] = claims

            android.util.Log.d("BenefitRepository", "Syncing data to Firestore claims map: $claims")
            firestore.collection("users").document(userId).set(data).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun String.toSlug() = lowercase()
        .replace("®", "")
        .replace("'", "")
        .replace("+", "plus")
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')

    suspend fun insertCard(card: Card): Long = cardDao.insertCard(card)
    suspend fun insertBenefit(benefit: Benefit): Long = benefitDao.insertBenefit(benefit)
}
