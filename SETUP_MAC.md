# 🍎 macOS Setup Instructions

Follow these steps to get the Amex Benefit Tracker running on macOS.

## 📋 Prerequisites

1. **Node.js**: Install via [nodejs.org](https://nodejs.org/) or using Homebrew:
   ```bash
   brew install node
   ```
2. **Terminal**: Use the default Terminal app or iTerm2.

## 🚀 Installation Steps

1. **Open Terminal**:
   - Press `Cmd + Space` and type "Terminal".

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

- **Permission Issues**: If `npm install` fails with permission errors, try:
  ```bash
  sudo npm install
  ```
  *(Note: Using NVM is recommended to avoid sudo with npm)*.
- **Node Version**: Ensure you are using Node 18 or higher: `node -v`.
