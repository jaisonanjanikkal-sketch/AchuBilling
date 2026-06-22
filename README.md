# Anjanikkal — Full-Stack Smart Billing & Inventory App

Anjanikkal is a responsive, mobile-first, full-stack billing and inventory management application designed for small businesses. It is built using Node.js/Express, React (via Vite), and SQLite.

This project is designed to easily compile into an Android App in the future using **Capacitor**.

---

## Features

1. **Dashboard (HOME & DASHBOARD)**: Real-time summaries (sales, item counts, low stock alerts, transactions).
2. **Analytics**: Top-selling items progress bars, active stock warnings, and transaction logs.
3. **Inventory Management (ITEMS)**: Complete CRUD operations for items with auto-deducting stock logic.
4. **Billing Flow**: Live auto-suggest, multiple line items, instant stock update, and formatted invoice generation with print preview support.
5. **Business Profile**: Customize branding details on the invoices (Business Name, Phone, and Address).
6. **Data Tools**: Seed database with demo items/sales, export system data as JSON backups, and full database reset.

---

## Tech Stack

- **Frontend**: React 18, Vite, CSS Custom Properties (Design System).
- **Backend**: Node.js + Express (REST API).
- **Database**: SQLite (via `better-sqlite3`).

---

## Getting Started

### Prerequisites

Make sure you have **Node.js (version 18 or above)** installed on your machine.

### Installation

1. Open your terminal at the root of the project (`Achu/`).
2. Install dependencies for both the **server** and **client**:

   ```bash
   # Install server dependencies
   cd server
   npm install

   # Install client dependencies
   cd ../client
   npm install
   ```

---

## Running the App in Development

For a live development experience with hot-reloading:

1. **Start the backend server** (runs on `http://localhost:5000`):
   ```bash
   cd server
   npm run dev
   ```

2. **Start the frontend client** (runs on `http://localhost:5173`, with requests proxied to `5000`):
   ```bash
   cd client
   npm run dev
   ```

3. Open your browser and navigate to `http://localhost:5173`.

---

## Running in Production (Single Server)

To compile the React app and run both front-end and back-end from a single Express server:

1. **Build the React frontend**:
   ```bash
   cd client
   npm run build
   ```
   This will generate static production assets under `client/dist`.

2. **Start the server**:
   ```bash
   cd ../server
   npm start
   ```
   The application will now be served globally at `http://localhost:5000`.

---

## Future Android Conversion (Using Capacitor)

Since the frontend is built using React and Vite as a Single Page App (SPA), converting it to a native Android app is simple:

1. **Install Capacitor CLI** inside the `client/` folder:
   ```bash
   cd client
   npm install @capacitor/core @capacitor/cli
   ```

2. **Initialize Capacitor** with your app details:
   ```bash
   npx cap init Anjanikkal com.anjanikkal.app --web-dir=dist
   ```

3. **Add the Android Platform**:
   ```bash
   npm install @capacitor/android
   npx cap add android
   ```

4. **Sync the compiled bundle**:
   ```bash
   npm run build
   npx cap sync
   ```

5. **Open in Android Studio**:
   ```bash
   npx cap open android
   ```
   *In Android Studio, you can run or build a signed APK/Bundle directly.*
