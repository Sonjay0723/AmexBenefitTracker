package com.example.amexbenefittracker.data.local

import com.example.amexbenefittracker.data.local.entities.Benefit
import com.example.amexbenefittracker.data.local.entities.BenefitType
import com.example.amexbenefittracker.data.local.entities.Card
import com.example.amexbenefittracker.data.repository.BenefitRepository
import kotlinx.coroutines.flow.first

class DatabaseInitializer(private val repository: BenefitRepository) {
    suspend fun initialize() {
        repository.deleteBenefitByName("Saks Fifth Avenue")

        // Migrate Walmart+ to monthly if it is currently annual in the database
        val walmartBenefits = repository.getBenefitsByName("Walmart+")
        for (benefit in walmartBenefits) {
            if (benefit.type == BenefitType.ANNUAL) {
                val updatedBenefit = benefit.copy(
                    description = "$12.95 per month",
                    totalValue = 155.4,
                    type = BenefitType.MONTHLY,
                    monthlyValue = 12.95,
                    decemberValue = 12.95
                )
                repository.insertBenefit(updatedBenefit)
            }
        }

        // Migrate lululemon Credit to Lululemon Credit if it exists in the database
        val lululemonBenefits = repository.getBenefitsByName("lululemon Credit")
        for (benefit in lululemonBenefits) {
            val updatedBenefit = benefit.copy(
                name = "Lululemon Credit"
            )
            repository.insertBenefit(updatedBenefit)
        }

        val cards = repository.getAllCards().first()
        if (cards.isEmpty()) {
            val platinumId = repository.insertCard(
                Card(
                    name = "The Platinum Card®",
                    annualFee = 895.0,
                    corporateCredit = 150.0,
                    corporateCreditClaimed = false,
                    isDefault = true
                )
            )

            val platinumBenefits = listOf(
                Benefit(
                    cardId = platinumId,
                    name = "Hotel Credit",
                    description = "$300 per half-year",
                    totalValue = 600.0,
                    type = BenefitType.SEMI_ANNUAL,
                    semiAnnualValue = 300.0,
                    displayOrder = 0
                ),
                Benefit(
                    cardId = platinumId,
                    name = "Uber Cash",
                    description = "$15/mo ($35 Dec)",
                    totalValue = 200.0,
                    type = BenefitType.MONTHLY,
                    monthlyValue = 15.0,
                    decemberValue = 35.0,
                    displayOrder = 1
                ),
                Benefit(
                    cardId = platinumId,
                    name = "Uber One",
                    description = "Annual membership credit",
                    totalValue = 96.0,
                    type = BenefitType.ANNUAL,
                    displayOrder = 2
                ),
                Benefit(
                    cardId = platinumId,
                    name = "Resy Credit",
                    description = "$100 per quarter",
                    totalValue = 400.0,
                    type = BenefitType.QUARTERLY,
                    quarterlyValue = 100.0,
                    displayOrder = 3
                ),
                Benefit(
                    cardId = platinumId,
                    name = "Digital Entertainment",
                    description = "$25 per month",
                    totalValue = 300.0,
                    type = BenefitType.MONTHLY,
                    monthlyValue = 25.0,
                    decemberValue = 25.0,
                    displayOrder = 4
                ),
                Benefit(
                    cardId = platinumId,
                    name = "Lululemon Credit",
                    description = "$75 per quarter",
                    totalValue = 300.0,
                    type = BenefitType.QUARTERLY,
                    quarterlyValue = 75.0,
                    displayOrder = 5
                ),
                Benefit(
                    cardId = platinumId,
                    name = "Walmart+",
                    description = "$12.95 per month",
                    totalValue = 155.4,
                    type = BenefitType.MONTHLY,
                    monthlyValue = 12.95,
                    decemberValue = 12.95,
                    displayOrder = 6
                ),
                Benefit(
                    cardId = platinumId,
                    name = "CLEAR+ Credit",
                    description = "Full membership coverage",
                    totalValue = 209.0,
                    type = BenefitType.ANNUAL,
                    displayOrder = 7
                ),
                Benefit(
                    cardId = platinumId,
                    name = "Airline Fee Credit",
                    description = "Incidental fees only",
                    totalValue = 200.0,
                    type = BenefitType.ANNUAL,
                    displayOrder = 8
                )
            )
            platinumBenefits.forEach { repository.insertBenefit(it) }

            val goldId = repository.insertCard(
                Card(
                    name = "American Express® Gold Card",
                    annualFee = 325.0,
                    corporateCredit = 100.0,
                    corporateCreditClaimed = false,
                    isDefault = false
                )
            )

            val goldBenefits = listOf(
                Benefit(
                    cardId = goldId,
                    name = "Uber Cash",
                    description = "$10/mo",
                    totalValue = 120.0,
                    type = BenefitType.MONTHLY,
                    monthlyValue = 10.0,
                    decemberValue = 10.0,
                    displayOrder = 0
                ),
                Benefit(
                    cardId = goldId,
                    name = "Dining Credit",
                    description = "$10/mo",
                    totalValue = 120.0,
                    type = BenefitType.MONTHLY,
                    monthlyValue = 10.0,
                    decemberValue = 10.0,
                    displayOrder = 1
                ),
                Benefit(
                    cardId = goldId,
                    name = "Dunkin' Credit",
                    description = "$7/mo",
                    totalValue = 84.0,
                    type = BenefitType.MONTHLY,
                    monthlyValue = 7.0,
                    decemberValue = 7.0,
                    displayOrder = 2
                ),
                Benefit(
                    cardId = goldId,
                    name = "Resy Credit",
                    description = "$50 per half-year",
                    totalValue = 100.0,
                    type = BenefitType.SEMI_ANNUAL,
                    semiAnnualValue = 50.0,
                    displayOrder = 3
                )
            )
            goldBenefits.forEach { repository.insertBenefit(it) }
        }
    }
}
