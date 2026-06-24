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

    suspend fun refreshData() {
        val userId = auth.currentUser?.uid ?: return
        try {
            val snapshot = firestore.collection("users").document(userId).get().await()
            if (snapshot.exists()) {
                val data = snapshot.data ?: return
                
                // Clear local tracking before restoring
                usageHistoryDao.deleteAllUsage()
                
                // Restore Corporate Credit Status
                val cards = cardDao.getAllCardsDirect()
                cards.forEach { card ->
                    val claimed = (data["corp_credit_${card.name}"] as? Boolean) ?: false
                    cardDao.updateCard(card.copy(corporateCreditClaimed = claimed))
                }

                // Restore Benefit History
                @Suppress("UNCHECKED_CAST")
                val historyList = (data["history"] as? List<Map<String, Any>>) ?: emptyList()
                historyList.forEach { item ->
                    val bName = item["name"] as? String
                    val cName = item["card"] as? String
                    val pId = item["period"] as? String
                    val amount = (item["amount"] as? Number)?.toDouble() ?: 0.0
                    val date = (item["date"] as? Number)?.toLong() ?: 0L
                    
                    if (bName != null && pId != null) {
                        if (bName == "Uber Cash") {
                            // Link Uber Cash across all cards
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
                            // Apply only to the specific card it was saved for
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
        } catch (e: Exception) {
            e.printStackTrace()
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

            // Sync History
            val historyExport = mutableListOf<Map<String, Any>>()
            allUsage.forEach { usage ->
                val benefit = benefitDao.getBenefitById(usage.benefitId)
                if (benefit != null) {
                    val card = cards.find { it.id == benefit.cardId }
                    if (card != null) {
                        historyExport.add(
                            mapOf(
                                "name" to benefit.name,
                                "card" to card.name,
                                "period" to usage.periodIdentifier,
                                "amount" to usage.amountClaimed,
                                "date" to usage.dateClaimed,
                            ),
                        )
                    }
                }
            }
            // For Uber Cash, we only need to store it once in the cloud export to avoid bloat
            // since it's linked during refresh anyway.
            data["history"] = historyExport.distinctBy { 
                if (it["name"] == "Uber Cash") "${it["name"]}_${it["period"]}" 
                else "${it["name"]}_${it["card"]}_${it["period"]}"
            }

            firestore.collection("users").document(userId).set(data).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun insertCard(card: Card): Long = cardDao.insertCard(card)
    suspend fun insertBenefit(benefit: Benefit): Long = benefitDao.insertBenefit(benefit)
}
