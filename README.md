# Amex Benefit Tracker (Android)

A modern, high-performance Android application built with Jetpack Compose designed to help American Express Platinum and Gold cardholders maximize their membership value. This app provides a streamlined, interactive interface to track various credits (Monthly, Quarterly, Semi-Annual, and Annual) ensuring no benefit goes unused.

## Key Features

*   **Dual-Card Dashboard**: Seamlessly switch between American Express Platinum and Gold card profiles with a premium dark-mode aesthetic.
*   **Intelligent Tracking**:
    *   **Monthly Credits**: Tracking for Uber Cash, Dining Credit, Digital Entertainment, and Dunkin'.
    *   **Quarterly Credits**: Specialized tracking for Resy and Lululemon credits.
    *   **Semi-Annual & Annual**: Tracking for Hotel, CLEAR+, and Airline Fee credits.
*   **Linked Uber Cash**: Synchronized tracking between cards—checking Uber Cash for one card automatically updates the other for the same month.
*   **Plaid Transaction Syncing**: Automatically import and check off qualifying transactions via Plaid integration.
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
*   **Database**: Room & Shared Preferences
*   **Backend / Middleware**: Cloudflare Workers (Edge Functions) & Firebase SDK
*   **Bank Syncing**: Plaid API & Plaid Link SDK
*   **Concurrency**: Kotlin Coroutines & Flow

---

## Setup & Configuration Guide

Follow these step-by-step instructions to set up your own Plaid developer account, Cloudflare Worker broker, and Firebase project to build and run the application.

### Prerequisites

*   [Android Studio](https://developer.android.com/studio) (Koala / Ladybug or newer) with JDK 11+
*   [Node.js](https://nodejs.org/) (v18+)
*   A [Plaid Developer Account](https://dashboard.plaid.com/signup)
*   A free [Cloudflare Account](https://dash.cloudflare.com/sign-up)
*   A [Firebase](https://console.firebase.google.com/) Account

---

### Step 1: Set Up Plaid

1. Sign in to your [Plaid Dashboard](https://dashboard.plaid.com/).
2. Navigate to **Team Settings** > **Keys** to copy your **Client ID** and **Secret** (use `Sandbox` for testing or `Development`/`Production` for live accounts).
3. Under **Developers** > **API settings**, add your Android application details:
   * **Package Name**: `com.example.amexbenefittracker`

---

### Step 2: Deploy the Cloudflare Worker Broker (100% Free)

To protect your Plaid credentials, the app routes Plaid API requests through a secure Cloudflare Worker located in `./cloudflare-worker`.

#### Option A: Using Cloudflare Web Dashboard
1. Log into your [Cloudflare Dashboard](https://dash.cloudflare.com/).
2. Go to **Workers & Pages** -> Click **Create Application** -> **Create Worker**.
3. Name your Worker (e.g., `amex-plaid-broker`) and click **Deploy**.
4. Click **Edit Code**, replace the contents of `worker.js` with the code in `./cloudflare-worker/worker.js`, and click **Deploy**.
5. Go to **Settings** -> **Variables & Secrets**, add secret variables `PLAID_CLIENT_ID`, `PLAID_SECRET`, and `PLAID_ENV` (`sandbox` or `production`), and save.

#### Option B: Using Wrangler CLI
```bash
cd cloudflare-worker
npx wrangler login
npx wrangler secret put PLAID_CLIENT_ID
npx wrangler secret put PLAID_SECRET
npx wrangler secret put PLAID_ENV
npx wrangler deploy
```

---

### Step 3: Set Up Firebase

1. Open the [Firebase Console](https://console.firebase.google.com/) and click **Add Project**.
2. Name your project (e.g., `Amex Benefit Tracker`).
3. Click **Add App** and select **Android**.
4. Enter the package name: `com.example.amexbenefittracker`
5. Download the `google-services.json` file provided by Firebase.
6. Copy `google-services.json` into the `app/` folder of this repository:
   ```text
   AmexBenefitTracker/
   └── app/
       └── google-services.json
   ```
7. *(Optional)* In Firebase Console:
   * Enable **Authentication** (Google Sign-In / Email & Password).
   * Enable **Cloud Firestore** database.

---

### Step 4: Configure `local.properties` & Build the App

1. In the root directory of the project, edit (or create) the `local.properties` file and add your deployed Cloudflare Worker URL:
   ```properties
   PLAID_CLOUD_FUNCTION_URL=https://amex-plaid-broker.jpitta0723.workers.dev/
   ```
   > **Tip**: You can also configure or override this Worker URL directly inside the app's **Settings UI** at runtime.

2. Open the project in **Android Studio**.
3. Sync the project with Gradle files (`File` > `Sync Project with Gradle Files`).
4. Build and run the app on an Android Emulator or physical device:
   ```bash
   ./gradlew assembleDebug
   ```

---

## Design Language

*   **Platinum Theme**: Blue 400 accents with Blue 600 indicators.
*   **Gold Theme**: Amber 400 accents with Amber 600 indicators.
*   **Status Colors**: Emerald 400 for profits and checked items, Red 400 for destructive actions.
*   **Background**: Deep Slate 950 with semi-transparent Slate 900 containers.

---

*Disclaimer: This is an independent tracking tool and is not affiliated with American Express.*
