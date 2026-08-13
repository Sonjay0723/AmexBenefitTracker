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

To protect your Plaid credentials, the app routes Plaid API requests through a secure Cloudflare Worker located in `./cloudflare-worker`. The worker also verifies each caller's Firebase ID token before touching Plaid or Cloudflare KV, so a connection linked on one device is available on every device signed into the same account — the worker itself is the only place a Plaid access token is ever stored.

1. Create the KV namespace the worker uses to store each user's Plaid connection:
   ```bash
   cd cloudflare-worker
   npx wrangler login
   npx wrangler kv namespace create PLAID_KV
   npx wrangler kv namespace create PLAID_KV --preview
   ```
   Copy the two ids the commands print into `id` / `preview_id` in `wrangler.jsonc`'s `kv_namespaces` entry.
2. In `wrangler.jsonc`, set `vars.FIREBASE_PROJECT_ID` to your own Firebase project id (from Step 3 below) — this is what the worker checks incoming ID tokens against. If you deploy the worker at a different origin than `http://localhost:5173`, or serve the Electron app somewhere other than `file://`, also update `vars.ALLOWED_ORIGINS` (comma-separated).
3. Set the Plaid secrets and deploy:
   ```bash
   npx wrangler secret put PLAID_CLIENT_ID
   npx wrangler secret put PLAID_SECRET
   npx wrangler secret put PLAID_ENV
   npx wrangler deploy
   ```
   For local testing, copy `cloudflare-worker/.dev.vars.example` to `cloudflare-worker/.dev.vars` (gitignored) and run `npx wrangler dev`.

The Cloudflare Web Dashboard works too (**Workers & Pages → Create Application → Create Worker**, paste in `worker.js` and `auth.js`, add the KV binding and vars under **Settings**), but the CLI is easier to keep in sync with `wrangler.jsonc`.

A handful of legacy, unauthenticated routes (`/create-link-token`, `/exchange-token`, `/accounts`, `/sync-transactions`) are still served for clients that haven't updated yet. They're planned for removal — don't build anything new against them.

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
