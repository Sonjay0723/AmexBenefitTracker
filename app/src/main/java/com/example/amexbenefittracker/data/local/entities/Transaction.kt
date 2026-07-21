package com.example.amexbenefittracker.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val id: String, // Plaid's transaction_id
    val cardId: Long,
    val description: String,
    val amount: Double,
    val date: Long, // Epoch timestamp in milliseconds
    val matchedBenefitId: Long? = null,
    val matchedBenefitName: String? = null,
    val matchedPeriod: String? = null // e.g. "2026-05"
)
