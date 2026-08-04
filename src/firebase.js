import { initializeApp } from "firebase/app";
import { getAuth, GoogleAuthProvider } from "firebase/auth";
import { getFirestore } from "firebase/firestore";

// Your web app's Firebase configuration
// For Firebase JS SDK v7.20.0 and later, measurementId is optional
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "AIzaSyBSyMH0O4B9RqunKj2rQCpf61cRNXnxyvs",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "amex-benefit-tracker.firebaseapp.com",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "amex-benefit-tracker",
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "amex-benefit-tracker.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "443435413708",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || "1:443435413708:web:amexbenefittracker"
};

const isConfigured = true;

// Initialize Firebase
const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const googleProvider = new GoogleAuthProvider();
const db = getFirestore(app);

export { app, auth, googleProvider, db, isConfigured };
