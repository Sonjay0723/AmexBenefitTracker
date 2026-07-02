# Amex Benefit Tracker (Android)

A modern, high-performance Android application built with Jetpack Compose designed to help American Express Platinum and Gold cardholders maximize their membership value. This app provides a streamlined, interactive interface to track various credits (Monthly, Quarterly, Semi-Annual, and Annual) ensuring no benefit goes unused.

## Key Features

*   **Dual-Card Dashboard**: Seamlessly switch between American Express Platinum and Gold card profiles with a premium dark-mode aesthetic.
*   **Intelligent Tracking**:
    *   **Monthly Credits**: Tracking for Uber Cash, Dining Credit, Digital Entertainment, and Dunkin'.
    *   **Quarterly Credits**: Specialized tracking for Resy and lululemon credits.
    *   **Semi-Annual & Annual**: Tracking for Hotel, CLEAR+, and Airline Fee credits.
*   **Linked Uber Cash**: Synchronized tracking between cards—checking Uber Cash for one card automatically updates the other for the same month.
*   **Interactive Card Details**:
    *   Toggle **Corporate Credit** ($150 for Platinum, $100 for Gold) to see real-time impact on your financial summary.
    *   Dynamic calculation of **Effective Annual Fee** and **Total Profit**.
*   **Adaptive Layout**: Fully optimized for both portrait (vertical) and landscape (horizontal) orientations. Features a vertical card selector and two-row month layout on mobile screens.
*   **Customization**: Double-click (or single-click) to edit the tracking year for historical record keeping.
*   **Premium Design**: Neon-inspired accent colors, "glass" card effects, and circular checkmark language matching the Amex brand identity.
*   **Local Persistence**: Powered by **Room Database** for fast, offline-first data management.

## Technical Stack

*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose (Material 3)
*   **Architecture**: MVVM with Repository pattern
*   **Database**: Room
*   **Concurrency**: Kotlin Coroutines & Flow
*   **Navigation**: Navigation 3

## Design Language

*   **Platinum Theme**: Blue 400 accents with Blue 600 indicators.
*   **Gold Theme**: Amber 400 accents with Amber 600 indicators.
*   **Status Colors**: Emerald 400 for profits and checked items, Red 400 for destructive actions.
*   **Background**: Deep Slate 950 with semi-transparent Slate 900 containers.

---
*Disclaimer: This is an independent tracking tool and is not affiliated with American Express.*
