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
*   **Backend / Middleware**: GCP Cloud Functions (Node.js) & Firebase SDK
*   **Bank Syncing**: Plaid API & Plaid Link SDK
*   **Concurrency**: Kotlin Coroutines & Flow

---

## Setup & Configuration Guide

Follow these step-by-step instructions to set up your own Plaid developer account, GCP Cloud Function broker, and Firebase project to build and run the application.

### Prerequisites

*   [Android Studio](https://developer.android.com/studio) (Koala / Ladybug or newer) with JDK 11+
*   [Node.js](https://nodejs.org/) (v18+)
*   [Google Cloud SDK (`gcloud` CLI)](https://cloud.google.com/sdk/docs/install)
*   A [Plaid Developer Account](https://dashboard.plaid.com/signup)
*   A [Google Cloud Platform (GCP)](https://console.cloud.google.com/) Account
*   A [Firebase](https://console.firebase.google.com/) Account

---

### Step 1: Set Up Plaid

1. Sign in to your [Plaid Dashboard](https://dashboard.plaid.com/).
2. Navigate to **Team Settings** > **Keys** to copy your **Client ID** and **Secret** (use `Sandbox` for testing or `Development`/`Production` for live accounts).
3. Under **Developers** > **API settings**, add your Android application details:
   * **Package Name**: `com.example.amexbenefittracker`

---

### Step 2: Deploy the GCP Cloud Function Broker

To protect your Plaid credentials, the app routes Plaid API requests through a secure Google Cloud Function located in `./cloud-function`.

1. Authenticate with Google Cloud CLI:
   ```bash
   gcloud auth login
   gcloud config set project YOUR_GCP_PROJECT_ID
   ```
2. Navigate to the `cloud-function` folder in your terminal:
   ```bash
   cd cloud-function
   ```
3. Deploy the Cloud Function:
   ```bash
   gcloud functions deploy plaidBroker \
     --gen2 \
     --runtime=nodejs20 \
     --region=us-central1 \
     --source=. \
     --entry-point=plaidBroker \
     --trigger-http \
     --allow-unauthenticated \
     --set-env-vars PLAID_CLIENT_ID=YOUR_PLAID_CLIENT_ID,PLAID_SECRET=YOUR_PLAID_SECRET,PLAID_ENV=sandbox
   ```
   > **Note**: Replace `YOUR_GCP_PROJECT_ID`, `YOUR_PLAID_CLIENT_ID`, and `YOUR_PLAID_SECRET` with your actual credentials. Change `PLAID_ENV` to `development` or `production` when ready for live data.

4. Once deployment succeeds, copy the **HTTPS Trigger URL** from the terminal output (e.g., `https://plaid-broker-xxxxxx-uc.a.run.app` or `https://us-central1-YOUR_PROJECT_ID.cloudfunctions.net/plaidBroker`).

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

1. In the root directory of the project, edit (or create) the `local.properties` file and add your deployed Cloud Function URL:
   ```properties
   PLAID_CLOUD_FUNCTION_URL=https://YOUR_DEPLOYED_CLOUD_FUNCTION_URL
   ```
   > **Tip**: You can also configure or override this Cloud Function URL directly inside the app's **Settings UI** at runtime.

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

