package com.example.amexbenefittracker.ui.offers

enum class CardIssuer(
    val displayName: String,
    val offersUrl: String,
    val enabled: Boolean
) {
    AMEX(
        displayName = "American Express",
        offersUrl = "https://global.americanexpress.com/offers/eligible",
        enabled = true
    ),
    CHASE(
        displayName = "Chase Bank",
        offersUrl = "https://www.chase.com",
        enabled = false
    ),
    CAPITAL_ONE(
        displayName = "Capital One",
        offersUrl = "https://www.capitalone.com",
        enabled = false
    ),
    CITI(
        displayName = "Citi Bank",
        offersUrl = "https://www.citi.com",
        enabled = false
    )
}
