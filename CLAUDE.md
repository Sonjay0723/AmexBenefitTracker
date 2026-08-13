# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
npm run dev             # Vite dev server on http://localhost:5173
npm run build           # Production build to dist/
npm run lint            # ESLint over the repo
npm run electron:dev    # Vite + Electron together (waits for :5173)
npm run electron:build  # Clean release/, vite build, then electron-builder (NSIS installer)
```

There is no test framework, test script, or test file in this repo. Do not invent one without asking.

## Branches matter more than usual

The same product exists in several very different forms across branches, and there are two **main** branches, not one:

- **`windows_main`** — the main branch for the web/Electron app. `windows-web-app` (checked out here, same commit as `windows_main`) is where that work lands: React + Vite + Electron, with **Firebase Auth + Firestore cloud sync** and Plaid transaction sync via a Cloudflare Worker broker.
- **`android_main`** — the main branch for the native Android app: Kotlin + Jetpack Compose, MVVM, Room for local persistence, syncing to the *same* Firestore schema and the same Plaid broker.
- Everything else — `master`, `windows-local-download`, `windows-remote-download`, `windows-web-app`, `android_cloudflare_auth`, `android_gcp_cloud_function_auth` — is a working branch used to build and test a feature before it lands on one of the two main branches. Treat these as in-progress, not authoritative. In particular, `master`'s `README.md` and `INSTALL_WINDOWS_APP.md` describe an older **localStorage-only, no-login** version and are stale relative to `windows_main`.

Untracked `app/`, `build/`, `.gradle/`, and `local.properties` in the working tree are Android build leftovers that belong to `android_main`, not this branch. They should never be committed from a web-app branch.

## Architecture

Everything lives in `src/App.jsx` (~1300 lines): all state, all UI, Firestore sync, and Plaid integration in one default-exported component. `src/firebase.js` only initializes the SDK. There is no router, no state library, and no component directory — extend the existing file unless asked to split it.

### Two representations of the same data

The in-memory model and the Firestore model are deliberately different, and `BENEFIT_MAP` is the only bridge between them.

**In memory:** `usage` and `timestamps` are objects keyed by benefit id (`p_uber`, `g_resy`, …), each holding a **12-slot array indexed by month**, *regardless of the benefit's frequency*. Non-monthly benefits only ever use anchor indices, defined by `getPeriodInfo`: annual → `[0]`, quarterly → `[0,3,6,9]`, semi-annual → `[0,6]`. Any new code that loops over periods must go through `getPeriodInfo`, never `0..11`.

**In Firestore:** `users/{uid}` with

```
claims[<the_platinum_card|american_express_gold_card>][<year>][<benefit_path>][<periodKey>] = { a: amount, d: epochMillis }
tracking_year, corp_credits, recent_credits
```

`plaid_tokens` used to live here too but no longer should — see the Plaid section below. New code should never write to it.

`serializeClaims` writes **canonical** period keys (`"01".."12"`, `"Annual"`, `"Q1".."Q4"`, `"H1"/"H2"`), while `deserializeClaims` reads **leniently** via `getPossibleFirestorePeriodKeys`, which also accepts unpadded months and `JAN`-style abbreviations written by older clients and the Android app. Preserve that write-strict/read-tolerant asymmetry when touching key formats — dropping a legacy key silently loses users' historical claims.

`year` is a **string** (e.g. `"2026"`) because it is a Firestore map key. It is derived from the current `America/New_York` date on load, then overridden by `tracking_year` from the document.

### Uber Cash is linked across both cards

`p_uber` and `g_uber` represent one shared Amex benefit. Toggling either one toggles the other in `toggleBenefit`; `serializeClaims` dedupes via `processedUberCashPeriods` so only one card's record is written; `deserializeClaims` accepts a claim found under *either* card. Changes to claim handling must keep all three sites consistent.

### Amounts

`getBenefitAmount` derives per-period value from `total / periods`, with hardcoded exceptions: `p_uber` is `$35` in December (index 11) and `$15` otherwise, and `p_walmart` is a flat `$12.95`. `formatAmount` exists to suppress float artifacts (`$12.950000000000002`) — use it for any displayed dollar value.

### Persistence flow

An `onSnapshot` listener keeps state live; every toggle also fires an immediate `setDoc(..., { merge: true })` (deliberate — an earlier version lost data by deferring writes until close). `handleManualRefresh` duplicates the snapshot handler's parsing for the header refresh button, so **adding a persisted field means editing both** plus the write site.

### Plaid

No backend lives in this branch. All Plaid calls go to a Cloudflare Worker at `DEFAULT_PLAID_WORKER_URL` (`https://amex-plaid-broker.jpitta0723.workers.dev`) via `authedFetch`, which attaches `Authorization: Bearer <firebase_id_token>` to every call — the worker resolves that to a Plaid connection in its own KV store (see the worker's source on `android_cloudflare_auth`/`android_main`), so the client never holds a Plaid access token at all. Routes: `/plaid/link-token`, `/plaid/exchange`, `/plaid/status`, `/plaid/accounts`, `/plaid/sync`, `/plaid/cursor`, `/plaid/mappings`, `/plaid/disconnect`, `/plaid/migrate`. This is what makes a Plaid connection linked on one device (web or Android) available on every device signed into the same Firebase account.

`/plaid/sync` does not commit its own cursor — it returns `next_cursor`, and the client only calls `/plaid/cursor` after the resulting claims are successfully written to Firestore (Plaid's cursor is destructive-on-advance, so committing it before the write is confirmed would risk losing transactions permanently). `plaid_tokens` in Firestore is legacy: `onSnapshot`'s handler checks for it once per session and, if present, migrates it to the worker via `/plaid/migrate` then deletes the field — new code should never write to `plaid_tokens`.

The Plaid Link SDK is loaded by a `<script>` tag in `index.html` and reached as `window.Plaid` (the `cloudflare-worker/` directory is empty here).

`matchTransactionToBenefit` matches merchant strings against a `switch` keyed on the benefit's **display `name`**, not its id — renaming a benefit in `INITIAL_DATA` silently breaks its transaction matching unless the `case` label is renamed too.

### Config and Electron

`src/firebase.js` reads `VITE_FIREBASE_*` from `.env` but falls back to hardcoded production values, so the app runs without a `.env`. `vite.config.js` sets `base: './'`, which is required for Electron to load `dist/index.html` over `file://` — do not change it to an absolute base. `electron/main.js` loads the dev server when unpackaged and the built files when packaged, and has no preload or IPC (`nodeIntegration: false`, `contextIsolation: true`).

## Conventions

- Styling is Tailwind 4 utility classes inline, dark-only, with literal hex colors (`bg-[#0e1626]`, `bg-[#070b14]`) rather than theme tokens. Per-card accents branch on `activeCard === 'platinum'` (blue) vs gold (amber).
- Icons come from `lucide-react`.
- Benefit definitions (`INITIAL_DATA`), the Firestore bridge (`BENEFIT_MAP`), and the merchant matcher must be updated together when adding a benefit.
- `package.json` `version` is bumped as part of user-visible changes; several commit messages do this explicitly.
