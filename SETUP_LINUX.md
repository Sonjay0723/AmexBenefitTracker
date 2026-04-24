# 🐧 Linux Setup Instructions

Follow these steps to get the Amex Benefit Tracker running on your Linux distribution.

## 📋 Prerequisites

1. **Node.js & npm**: Install via your distribution's package manager.
   - **Ubuntu/Debian**:
     ```bash
     sudo apt update
     sudo apt install nodejs npm
     ```
   - **Fedora**:
     ```bash
     sudo dnf install nodejs npm
     ```
   - **Arch**:
     ```bash
     sudo pacman -S nodejs npm
     ```

## 🚀 Installation Steps

1. **Open your favorite Terminal**.

2. **Navigate to the Project**:
   ```bash
   cd path/to/amex-benefit-tracker
   ```

3. **Install Dependencies**:
   ```bash
   npm install
   ```

4. **Start the Application**:
   ```bash
   npm run dev
   ```

5. **View in Browser**:
   - Once the server starts, open your browser and navigate to `http://localhost:5173`.

## 🛠️ Troubleshooting

- **Node Version**: If your package manager installs an old version, use [n](https://github.com/tj/n) or [nvm](https://github.com/nvm-sh/nvm) to upgrade to the latest LTS.
- **Firewall**: If accessing from another device, ensure port `5173` is open in `ufw` or `firewalld`.
