# 🪟 Windows Setup Instructions

Follow these steps to get the Amex Benefit Tracker running on Windows.

## 📋 Prerequisites

1. **Node.js**: Download and install the latest LTS version from [nodejs.org](https://nodejs.org/).
   - Ensure "Add to PATH" is checked during installation.
2. **Git**: (Optional) If you haven't cloned the repo yet, install Git from [git-scm.com](https://git-scm.com/).

## 🚀 Installation Steps

1. **Open Terminal**:
   - Press `Win + X` and select **Terminal** or **PowerShell**.

2. **Navigate to the Project**:
   ```powershell
   cd path\to\amex-benefit-tracker
   ```

3. **Install Dependencies**:
   ```powershell
   npm install
   ```

4. **Start the Application**:
   ```powershell
   npm run dev
   ```

5. **View in Browser**:
   - Once the server starts, open your browser and navigate to `http://localhost:5173`.

## 🛠️ Troubleshooting

- **Execution Policy Error**: If you get a script execution error in PowerShell, run:
  ```powershell
  Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
  ```
- **Port Conflict**: If `5173` is taken, Vite will automatically try the next available port. Check the terminal output for the correct URL.
