# 💳 Amex Benefit Tracker

A premium, interactive dashboard designed to help American Express Platinum and Gold cardholders maximize their membership value. Track your credits, calculate your effective annual fee, and ensure no benefit goes unused.

![Amex Benefit Tracker Mockup](https://images.unsplash.com/photo-1563013544-824ae1b704d3?auto=format&fit=crop&q=80&w=1200)

## Branches

This repo ships two separate apps that share a Firestore schema and Plaid broker. Each has its own main branch:

- **[`windows_main`](https://github.com/Sonjay0723/AmexBenefitTracker/tree/windows_main)** — the Windows/web app (React + Vite + Electron). This is the code checked out by default.
- **[`android_main`](https://github.com/Sonjay0723/AmexBenefitTracker/tree/android_main)** — the native Android app (Kotlin + Jetpack Compose).

All other branches (`master`, `windows-local-download`, `windows-remote-download`, `windows-web-app`, `android_cloudflare_auth`, `android_gcp_cloud_function_auth`) are working branches used to build and test individual features before they land on `windows_main` or `android_main`. Don't treat them as up to date.

## ✨ Features

- **Multi-Card Support**: Seamlessly switch between The Platinum Card® and American Express® Gold Card.
- **Real-Time Calculations**: Instantly see your "Effective Annual Fee" update as you claim benefits.
- **Smart Tracking**:
  - Monthly credits (Uber, Dining, Dunkin, etc.)
  - Quarterly credits (Resy, lululemon)
  - Semi-annual credits (Hotel)
  - Annual credits (CLEAR+, Airline Fee, Walmart+)
- **Corporate Credit Integration**: Toggle corporate card credits to refine your fee calculation.
- **Cloud Sync**: Sign in with Google or email/password; benefit claims sync in real time via Firebase Auth + Firestore, so your data follows you across devices.
- **Bank Connection (Plaid)**: Link your Amex account via Plaid and auto-check off benefits as matching transactions post.
- **Modern UI**: A sleek, dark-mode interface built with Tailwind CSS and Lucide React.

## 🚀 Tech Stack

- **Framework**: [React 19](https://react.dev/)
- **Build Tool**: [Vite](https://vitejs.dev/)
- **Desktop Shell**: [Electron](https://www.electronjs.org/)
- **Styling**: [Tailwind CSS 4](https://tailwindcss.com/)
- **Icons**: [Lucide React](https://lucide.dev/)
- **Backend**: [Firebase](https://firebase.google.com/) (Auth + Firestore) and a [Cloudflare Worker](https://workers.cloudflare.com/) broker for [Plaid](https://plaid.com/)

## 🛠️ Setup — `windows_main` (Web / Electron)

1. **Clone and check out the branch**:
   ```bash
   git clone https://github.com/Sonjay0723/AmexBenefitTracker.git
   cd AmexBenefitTracker
   git checkout windows_main
   ```

2. **Install dependencies**:
   ```bash
   npm install
   ```

3. **(Optional) Configure Firebase**: copy `.env.example` to `.env` and fill in your own Firebase project's web config (`VITE_FIREBASE_*`). If you skip this, the app falls back to its bundled default project.

4. **Run it**:
   ```bash
   npm run dev             # Vite dev server at http://localhost:5173
   npm run electron:dev    # Vite + Electron desktop shell together
   ```

5. **Build for production**:
   ```bash
   npm run build           # Web build to dist/
   npm run electron:build  # Windows installer, output to release/
   ```

Detailed OS-specific guides: [Windows Setup](SETUP_WINDOWS.md) · [macOS Setup](SETUP_MAC.md) · [Linux Setup](SETUP_LINUX.md) · [Installing the packaged Windows app](INSTALL_WINDOWS_APP.md).

## 🛠️ Setup — `android_main` (Android)

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (Koala / Ladybug or newer) with JDK 11+
- [Node.js](https://nodejs.org/) (v18+)
- A [Plaid Developer Account](https://dashboard.plaid.com/signup)
- A free [Cloudflare Account](https://dash.cloudflare.com/sign-up)
- A [Firebase](https://console.firebase.google.com/) Account

### Steps

1. **Check out the branch**:
   ```bash
   git clone https://github.com/Sonjay0723/AmexBenefitTracker.git
   cd AmexBenefitTracker
   git checkout android_main
   ```

2. **Set up Plaid**: in the [Plaid Dashboard](https://dashboard.plaid.com/), copy your Client ID and Secret from **Team Settings → Keys**, and add the Android package name (`com.example.amexbenefittracker`) under **Developers → API settings**.

3. **Deploy the Cloudflare Worker broker** (protects your Plaid credentials — code lives in `./cloudflare-worker`):
   ```bash
   cd cloudflare-worker
   npx wrangler login
   npx wrangler secret put PLAID_CLIENT_ID
   npx wrangler secret put PLAID_SECRET
   npx wrangler secret put PLAID_ENV
   npx wrangler deploy
   ```
   (Or use the Cloudflare dashboard: **Workers & Pages → Create Application → Create Worker**, paste in `worker.js`, and set the same secrets under **Settings → Variables & Secrets**.)

4. **Set up Firebase**: create a project in the [Firebase Console](https://console.firebase.google.com/), add an Android app with package name `com.example.amexbenefittracker`, download `google-services.json`, and place it at `app/google-services.json`. Optionally enable Authentication (Google / Email) and Cloud Firestore.

5. **Configure `local.properties`** with your deployed worker URL:
   ```properties
   PLAID_CLOUD_FUNCTION_URL=https://your-worker-subdomain.workers.dev/
   ```
   (This can also be overridden later from the app's Settings screen.)

6. **Build and run**: open the project in Android Studio, sync Gradle, then either run from the IDE or:
   ```bash
   ./gradlew assembleDebug
   ```

---
Developed by [Jayson Pitta](https://github.com/jpitta)
