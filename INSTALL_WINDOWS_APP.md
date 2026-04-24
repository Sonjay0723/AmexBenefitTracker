# 🖥️ Windows Desktop App — Install Guide

> Download and run the **Amex Benefit Tracker** as a native Windows application. No browser required — your data is stored locally on your machine.

---

## 📥 Download

1. Go to the [**windows-download**](https://github.com/Sonjay0723/AmexBenefitTracker/tree/windows-download) branch
2. Click **`release/`** → download **`Amex Benefit Tracker Setup 1.0.0.exe`**

---

## 🚀 Installation

1. **Run the installer** — double-click `Amex Benefit Tracker Setup 1.0.0.exe`
2. **Windows SmartScreen warning** — if you see *"Windows protected your PC"*, click **More info → Run anyway**
   > This happens because the app is not code-signed. It is safe to proceed.
3. **Choose install location** — the default is `C:\Program Files\Amex Benefit Tracker`
4. Click **Install** and then **Finish**
5. A **Desktop shortcut** and **Start Menu entry** will be created automatically

---

## 💾 Data Storage

All your benefit tracking data is stored **100% locally** on your device:

- Data persists automatically between launches — no account or login needed
- Storage location: `%AppData%\amex-benefit-tracker\Local Storage\`
- To back up your data, copy that folder to a safe location

---

## 🔄 Updating the App

When a new version is released:

1. Download the new `Setup.exe` from this branch
2. Run the installer — it will upgrade the existing installation in place
3. Your saved data will be preserved

---

## 🗑️ Uninstalling

Go to **Settings → Apps → Amex Benefit Tracker → Uninstall**, or use **Add/Remove Programs** in the Control Panel.

---

## 🛠️ Build It Yourself

If you'd prefer to build from source:

```powershell
# Clone the repo and switch to this branch
git clone https://github.com/Sonjay0723/AmexBenefitTracker.git
cd AmexBenefitTracker
git checkout windows-download

# Install dependencies
npm install

# Build the .exe
npm run electron:build
```

The installer will be output to `release\Amex Benefit Tracker Setup 1.0.0.exe`.

---

*© 2026 Amex Benefit Tracker — Jayson Pitta*
