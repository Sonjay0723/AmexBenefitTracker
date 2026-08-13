import React, { useState, useMemo, useEffect, useRef } from 'react';
import {
  CreditCard,
  TrendingUp,
  CheckCircle2,
  Circle,
  Info,
  ShieldCheck,
  Utensils,
  Plane,
  ShoppingBag,
  RotateCcw,
  History,
  LogOut,
  Mail,
  Lock,
  Eye,
  EyeOff,
  Settings,
  Zap,
  Building2,
  Link2,
  RefreshCw,
  X,
  ExternalLink,
  Check,
  AlertCircle,
  Calendar,
  DollarSign,
  ChevronDown,
  ChevronUp
} from 'lucide-react';
import { auth, db, googleProvider, isConfigured } from './firebase';
import {
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signInWithPopup,
  signOut,
  onAuthStateChanged
} from 'firebase/auth';
import { doc, getDoc, setDoc, updateDoc, onSnapshot, deleteField } from 'firebase/firestore';

const MONTH_ABBRS = [
  'JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN',
  'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'
];

const DEFAULT_PLAID_WORKER_URL = 'https://amex-plaid-broker.jpitta0723.workers.dev';

// The stable card identifiers shared with Firestore claim keys and with the
// Plaid broker's card_mappings (which are keyed by these slugs, not by the
// app's internal 'platinum'/'gold' card keys).
const CARD_SLUGS = {
  platinum: 'the_platinum_card',
  gold: 'american_express_gold_card'
};

const INITIAL_DATA = {
  platinum: {
    name: 'The Platinum Card®',
    bgColor: 'bg-slate-950',
    accent: 'text-blue-400',
    accentBg: 'bg-blue-600',
    activeColor: 'bg-blue-600 border-blue-500 text-white',
    fee: 895,
    defaultCorpCredit: 150,
    benefits: [
      { id: 'p_hotel', name: 'Hotel Credit', total: 600, freq: 'semi', desc: '$300 per half-year' },
      { id: 'p_uber', name: 'Uber Cash', total: 200, freq: 'month', desc: '$15/mo ($35 Dec)' },
      { id: 'p_uber_one', name: 'Uber One', total: 96, freq: 'annual', desc: 'Annual membership credit' },
      { id: 'p_resy', name: 'Resy Credit', total: 400, freq: 'quart', desc: '$100 per quarter' },
      { id: 'p_streaming', name: 'Digital Entertainment', total: 300, freq: 'month', desc: '$25 per month' },
      { id: 'p_lulu', name: 'Lululemon Credit', total: 300, freq: 'quart', desc: '$75 per quarter' },
      { id: 'p_walmart', name: 'Walmart+', total: 155.40, freq: 'month', desc: '$12.95 per month' },
      { id: 'p_clear', name: 'CLEAR+ Credit', total: 209, freq: 'annual', desc: 'Full membership coverage' },
      { id: 'p_airline', name: 'Airline Fee Credit', total: 200, freq: 'annual', desc: 'Incidental fees only' }
    ]
  },
  gold: {
    name: 'American Express® Gold Card',
    bgColor: 'bg-slate-950',
    accent: 'text-amber-400',
    accentBg: 'bg-amber-600',
    activeColor: 'bg-amber-600 border-amber-500 text-white',
    fee: 325,
    defaultCorpCredit: 100,
    benefits: [
      { id: 'g_uber', name: 'Uber Cash', total: 120, freq: 'month', desc: '$10 per month' },
      { id: 'g_dining', name: 'Dining Credit', total: 120, freq: 'month', desc: '$10 per month' },
      { id: 'g_dunkin', name: "Dunkin' Credit", total: 84, freq: 'month', desc: '$7 per month' },
      { id: 'g_resy', name: 'Resy Credit', total: 100, freq: 'semi', desc: '$50 per half-year' }
    ]
  }
};

const BENEFIT_MAP = {
  p_hotel: { card: 'platinum', path: 'hotel_credit', freq: 'semi' },
  p_uber: { card: 'platinum', path: 'uber_cash', freq: 'month' },
  p_uber_one: { card: 'platinum', path: 'uber_one', freq: 'annual' },
  p_resy: { card: 'platinum', path: 'resy_credit', freq: 'quart' },
  p_streaming: { card: 'platinum', path: 'digital_entertainment', freq: 'month' },
  p_lulu: { card: 'platinum', path: 'lululemon_credit', freq: 'quart' },
  p_walmart: { card: 'platinum', path: 'walmartplus', freq: 'month' },
  p_clear: { card: 'platinum', path: 'clearplus_credit', freq: 'annual' },
  p_airline: { card: 'platinum', path: 'airline_fee_credit', freq: 'annual' },
  g_uber: { card: 'gold', path: 'uber_cash', freq: 'month' },
  g_dining: { card: 'gold', path: 'dining_credit', freq: 'month' },
  g_dunkin: { card: 'gold', path: 'dunkin_credit', freq: 'month' },
  g_resy: { card: 'gold', path: 'resy_credit', freq: 'semi' }
};

// Strict Amount Formatter to eliminate floating point glitches like $12.950000000000002
const formatAmount = (val) => {
  const num = Number(val);
  if (isNaN(num)) return '$0';
  if (Number.isInteger(num)) return `$${num}`;
  return `$${num.toFixed(2)}`;
};

const getBenefitAmount = (benefitId, idx) => {
  if (benefitId === 'p_uber') {
    return idx === 11 ? 35 : 15;
  }
  if (benefitId === 'p_walmart') {
    return 12.95;
  }
  for (const cardKey of ['platinum', 'gold']) {
    const benefit = INITIAL_DATA[cardKey].benefits.find(b => b.id === benefitId);
    if (benefit) {
      if (benefit.freq === 'month') return Number((benefit.total / 12).toFixed(2));
      if (benefit.freq === 'quart') return Number((benefit.total / 4).toFixed(2));
      if (benefit.freq === 'semi') return Number((benefit.total / 2).toFixed(2));
      if (benefit.freq === 'annual') return Number(benefit.total.toFixed(2));
    }
  }
  return 0;
};

const getPeriodInfo = (freq) => {
  if (freq === 'annual') return { keys: ['ANNUAL CREDIT'], indices: [0] };
  if (freq === 'quart') return { keys: ['Q1', 'Q2', 'Q3', 'Q4'], indices: [0, 3, 6, 9] };
  if (freq === 'semi') return { keys: ['H1', 'H2'], indices: [0, 6] };
  return { keys: MONTH_ABBRS, indices: Array.from({ length: 12 }, (_, i) => i) };
};

const matchTransactionToBenefit = (txName, benefitName, amount = 0.0) => {
  const name = (txName || '').toLowerCase();
  switch (benefitName) {
    case 'Uber Cash':
      return name.includes('uber') && !name.includes('uber one');
    case 'Uber One':
      return name.includes('uber one');
    case 'Hotel Credit':
      return name.includes('fine hotels') || name.includes('hotel credit') || name.includes('hotel collection');
    case 'Resy Credit':
      return name.includes('resy');
    case 'Digital Entertainment':
      return name.includes('disney') || name.includes('hulu') || name.includes('peacock') ||
        name.includes('ny times') || name.includes('new york times') || name.includes('espn') || name.includes('digital entertainment');
    case 'Lululemon Credit':
      return name.includes('lululemon');
    case 'Walmart+': {
      const isExplicitPlus = name.includes('walmart+') || name.includes('walmart plus') || name.includes('wm+ membership');
      if (isExplicitPlus) return true;
      if (name.includes('walmart')) {
        const absVal = Math.abs(amount);
        return absVal >= 12.00 && absVal <= 15.00;
      }
      return false;
    }
    case 'CLEAR+ Credit':
      return name.includes('clear ') || name.includes('clear*') || name.includes('clear me');
    case 'Airline Fee Credit':
      return name.includes('delta') || name.includes('united air') || name.includes('american air') ||
        name.includes('southwest') || name.includes('jetblue') || name.includes('alaska air') || name.includes('hawaiian air');
    case 'Dining Credit':
      return name.includes('dining credit') || name.includes('dining') || name.includes('grubhub') ||
        name.includes('shake shack') || name.includes('five guys') || name.includes('cheesecake factory') ||
        name.includes('goldbelly') || name.includes('wine.com');
    case "Dunkin' Credit":
      return name.includes('dunkin');
    default:
      return false;
  }
};

const getPossibleFirestorePeriodKeys = (freq, key, monthIdx) => {
  if (freq === 'month') {
    const pad = String(monthIdx + 1).padStart(2, '0');
    const noPad = String(monthIdx + 1);
    const abbr = MONTH_ABBRS[monthIdx] || key;
    return [pad, noPad, abbr];
  }
  if (freq === 'annual') {
    return ['Annual', 'ANNUAL CREDIT', 'ANNUAL', key];
  }
  return [key];
};

const deserializeClaims = (claims, year) => {
  const usage = {}, timestamps = {};
  Object.keys(BENEFIT_MAP).forEach(id => {
    usage[id] = Array(12).fill(false);
    timestamps[id] = Array(12).fill(null);
  });
  if (!claims) return { usage, timestamps };

  Object.entries(BENEFIT_MAP).forEach(([benefitId, { card, path, freq }]) => {
    const { keys, indices } = getPeriodInfo(freq);

    keys.forEach((key, kIdx) => {
      const monthIdx = indices[kIdx];
      let claim = null;

      const candidateKeys = getPossibleFirestorePeriodKeys(freq, key, monthIdx);

      for (const fKey of candidateKeys) {
        if (path === 'uber_cash') {
          const platClaims = claims[CARD_SLUGS.platinum]?.[year]?.['uber_cash']?.[fKey];
          const goldClaims = claims[CARD_SLUGS.gold]?.[year]?.['uber_cash']?.[fKey];
          claim = platClaims || goldClaims;
        } else {
          const firestoreCardKey = CARD_SLUGS[card];
          claim = claims[firestoreCardKey]?.[year]?.[path]?.[fKey];
        }
        if (claim) break;
      }

      if (claim) {
        usage[benefitId][monthIdx] = true;
        timestamps[benefitId][monthIdx] = claim.d || Date.now();
      }
    });
  });
  return { usage, timestamps };
};

const serializeClaims = (usage, timestamps, year) => {
  const claims = {};

  const processedUberCashPeriods = new Set();

  Object.entries(BENEFIT_MAP).forEach(([benefitId, { card, path, freq }]) => {
    const usedArr = usage[benefitId] || Array(12).fill(false);
    const tsArr = timestamps[benefitId] || Array(12).fill(null);
    const { keys, indices } = getPeriodInfo(freq);

    indices.forEach((monthIdx, kIdx) => {
      if (usedArr[monthIdx]) {
        let periodKey = keys[kIdx];
        if (freq === 'month') {
          periodKey = String(monthIdx + 1).padStart(2, '0');
        } else if (freq === 'annual') {
          periodKey = 'Annual';
        }

        const periodIdentifier = `${year}-${periodKey}`;

        if (path === 'uber_cash') {
          if (processedUberCashPeriods.has(periodIdentifier)) {
            return;
          }
          processedUberCashPeriods.add(periodIdentifier);
        }

        const firestoreCardKey = CARD_SLUGS[card];
        if (!claims[firestoreCardKey]) claims[firestoreCardKey] = {};
        if (!claims[firestoreCardKey][year]) claims[firestoreCardKey][year] = {};
        const cardClaims = claims[firestoreCardKey][year];
        if (!cardClaims[path]) cardClaims[path] = {};
        
        cardClaims[path][periodKey] = {
          a: getBenefitAmount(benefitId, monthIdx),
          d: tsArr[monthIdx] || Date.now()
        };
      }
    });
  });
  return claims;
};

export default function App() {
  const [user, setUser] = useState(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [activeCard, setActiveCard] = useState('gold');
  const [usage, setUsage] = useState({});
  const [timestamps, setTimestamps] = useState({});
  
  const currentSystemYear = new Intl.DateTimeFormat('en-US', { year: 'numeric', timeZone: 'America/New_York' }).format(new Date());
  const [trackingYear, setTrackingYear] = useState(currentSystemYear);
  const [allClaims, setAllClaims] = useState({});
  
  const [corpCreditSettings, setCorpCreditSettings] = useState({
    platinum: { enabled: true },
    gold: { enabled: true }
  });

  // Plaid state - the access token and sync cursor live only in the
  // Cloudflare Worker's KV store now, keyed by Firebase uid, so every
  // signed-in device shares one connection instead of linking separately.
  const [plaidConnected, setPlaidConnected] = useState(false);
  const [plaidAccounts, setPlaidAccounts] = useState([]);
  const [cardPlaidMappings, setCardPlaidMappings] = useState({ platinum: '', gold: '' });
  const [recentCredits, setRecentCredits] = useState([]);
  const [isSyncingPlaid, setIsSyncingPlaid] = useState(false);
  const [isRecentCreditsOpen, setIsRecentCreditsOpen] = useState(false);
  const [isRefreshingCloud, setIsRefreshingCloud] = useState(false);
  const migratedLegacyPlaidRef = useRef(false);

  // Modals state
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [isActivatorOpen, setIsActivatorOpen] = useState(false);
  const [showResetConfirm, setShowResetConfirm] = useState(false);
  const [plaidError, setPlaidError] = useState(null);

  // Auth Form state
  const [authMode, setAuthMode] = useState('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [authError, setAuthError] = useState('');

  // 1. Firebase Auth listener
  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (u) => {
      setUser(u);
      setAuthLoading(false);
      if (!u) {
        setPlaidConnected(false);
        setPlaidAccounts([]);
      }
    });
    return () => unsubscribe();
  }, []);

  // Attaches a verified Firebase ID token to every Plaid broker call. The
  // worker derives the caller's uid from this token - it no longer accepts
  // a client-supplied userId or accessToken.
  const authedFetch = async (method, path, body) => {
    const idToken = await user.getIdToken();
    const res = await fetch(`${DEFAULT_PLAID_WORKER_URL}${path}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${idToken}`
      },
      ...(body !== undefined ? { body: JSON.stringify(body) } : {})
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(data.error || `Plaid broker request failed (${res.status})`);
    }
    return data;
  };

  const translateCardMappings = (slugMappings = {}) => ({
    platinum: slugMappings[CARD_SLUGS.platinum] || '',
    gold: slugMappings[CARD_SLUGS.gold] || ''
  });

  // Pulls the account's Plaid connection state from the broker. This is the
  // single source of truth for "connected" now - unlike the access token
  // itself, it's cheap and safe to ask for on every device.
  const refreshPlaidStatus = async () => {
    if (!user) return;
    try {
      const data = await authedFetch('GET', '/plaid/status');
      setPlaidConnected(!!data.connected);
      setPlaidAccounts(data.accounts || []);
      setCardPlaidMappings(translateCardMappings(data.card_mappings));
    } catch (err) {
      console.error('Error fetching Plaid status:', err);
    }
  };

  // One-time import of a Plaid connection a pre-update client still holds
  // in Firestore. Runs at most once per session (migratedLegacyPlaidRef);
  // the worker's /plaid/migrate is itself insert-only, so this is also safe
  // to run again in a future session if the field somehow wasn't cleared.
  const migrateLegacyPlaidToken = async (legacyPlaidTokens) => {
    try {
      await authedFetch('POST', '/plaid/migrate', {
        accessToken: legacyPlaidTokens.access_token,
        card_mappings: legacyPlaidTokens.card_mappings || {}
      });
      const userDocRef = doc(db, 'users', user.uid);
      await updateDoc(userDocRef, { plaid_tokens: deleteField() });
      await refreshPlaidStatus();
    } catch (err) {
      console.error('Error migrating legacy Plaid connection:', err);
    }
  };

  // 2. Firebase Firestore real-time snapshot listener
  useEffect(() => {
    if (!user) return;

    const userDocRef = doc(db, 'users', user.uid);
    const unsubscribe = onSnapshot(userDocRef, (docSnap) => {
      if (docSnap.exists()) {
        const data = docSnap.data();

        const remoteYear = data.tracking_year || currentSystemYear;
        setTrackingYear(remoteYear);

        if (data.claims) {
          setAllClaims(data.claims);
          const { usage: u, timestamps: t } = deserializeClaims(data.claims, remoteYear);
          setUsage(u);
          setTimestamps(t);
        }

        if (data.corp_credits) {
          setCorpCreditSettings(data.corp_credits);
        }

        if (Array.isArray(data.recent_credits)) {
          setRecentCredits(data.recent_credits);
        }

        if (data.plaid_tokens?.access_token && !migratedLegacyPlaidRef.current) {
          migratedLegacyPlaidRef.current = true;
          migrateLegacyPlaidToken(data.plaid_tokens);
        }
      } else {
        const initialDoc = {
          tracking_year: currentSystemYear,
          corp_credits: { platinum: { enabled: true }, gold: { enabled: true } },
          claims: {},
          recent_credits: []
        };
        setDoc(userDocRef, initialDoc, { merge: true });
      }
    }, (error) => {
      console.error('Firestore snapshot listener error:', error);
    });

    return () => unsubscribe();
  }, [user]);

  // 2b. Plaid connection status - lives in the broker's KV store, not
  // Firestore, so it's fetched separately rather than via onSnapshot.
  // (Reset on sign-out happens in the auth listener above instead of here,
  // to avoid calling setState synchronously in this effect's body.)
  useEffect(() => {
    if (!user) return;
    // Fetch-on-mount: refreshPlaidStatus's setState calls run after the
    // await inside it resolves, not synchronously in this effect body.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    refreshPlaidStatus();
  }, [user]);

  // 3. Manual Firestore Refresh Handler
  const handleManualRefresh = async () => {
    if (!user) return;
    setIsRefreshingCloud(true);
    try {
      const userDocRef = doc(db, 'users', user.uid);
      const docSnap = await getDoc(userDocRef);
      if (docSnap.exists()) {
        const data = docSnap.data();
        const remoteYear = data.tracking_year || currentSystemYear;
        setTrackingYear(remoteYear);

        if (data.claims) {
          setAllClaims(data.claims);
          const { usage: u, timestamps: t } = deserializeClaims(data.claims, remoteYear);
          setUsage(u);
          setTimestamps(t);
        }

        if (data.corp_credits) {
          setCorpCreditSettings(data.corp_credits);
        }

        if (Array.isArray(data.recent_credits)) {
          setRecentCredits(data.recent_credits);
        }
      }
      await refreshPlaidStatus();
    } catch (error) {
      console.error('Error manually refreshing Firestore data:', error);
    } finally {
      setTimeout(() => {
        setIsRefreshingCloud(false);
      }, 500);
    }
  };

  // Auth Submit
  const handleAuthSubmit = async (e) => {
    e.preventDefault();
    setAuthError('');
    try {
      if (authMode === 'login') {
        await signInWithEmailAndPassword(auth, email, password);
      } else {
        await createUserWithEmailAndPassword(auth, email, password);
      }
    } catch (err) {
      setAuthError(err.message.replace('Firebase: ', ''));
    }
  };

  const handleGoogleSignIn = async () => {
    setAuthError('');
    try {
      await signInWithPopup(auth, googleProvider);
    } catch (err) {
      setAuthError(err.message.replace('Firebase: ', ''));
    }
  };

  const handleSignOut = async () => {
    await signOut(auth);
    setIsSettingsOpen(false);
  };

  // Toggle Benefit
  const toggleBenefit = async (benefitId, idx) => {
    if (!user) return;
    
    const newUsage = { ...usage };
    const newTs = { ...timestamps };
    if (!newUsage[benefitId]) newUsage[benefitId] = Array(12).fill(false);
    if (!newTs[benefitId]) newTs[benefitId] = Array(12).fill(null);

    const isClaimed = !newUsage[benefitId][idx];
    newUsage[benefitId][idx] = isClaimed;
    newTs[benefitId][idx] = isClaimed ? Date.now() : null;

    if (benefitId === 'p_uber' || benefitId === 'g_uber') {
      const otherId = benefitId === 'p_uber' ? 'g_uber' : 'p_uber';
      if (!newUsage[otherId]) newUsage[otherId] = Array(12).fill(false);
      if (!newTs[otherId]) newTs[otherId] = Array(12).fill(null);
      newUsage[otherId][idx] = isClaimed;
      newTs[otherId][idx] = isClaimed ? Date.now() : null;
    }

    setUsage(newUsage);
    setTimestamps(newTs);

    const serialized = serializeClaims(newUsage, newTs, trackingYear);
    const updatedClaims = { ...allClaims };

    Object.keys(serialized).forEach(cardKey => {
      if (!updatedClaims[cardKey]) updatedClaims[cardKey] = {};
      if (!updatedClaims[cardKey][trackingYear]) updatedClaims[cardKey][trackingYear] = {};
      updatedClaims[cardKey][trackingYear] = {
        ...updatedClaims[cardKey][trackingYear],
        ...serialized[cardKey][trackingYear]
      };
    });

    setAllClaims(updatedClaims);

    try {
      const userDocRef = doc(db, 'users', user.uid);
      await setDoc(userDocRef, {
        claims: updatedClaims,
        tracking_year: trackingYear
      }, { merge: true });
    } catch (e) {
      console.error('Error saving claim:', e);
    }
  };

  const toggleCorpCredit = async (cardKey) => {
    if (!user) return;
    const newSettings = {
      ...corpCreditSettings,
      [cardKey]: { enabled: !corpCreditSettings[cardKey]?.enabled }
    };
    setCorpCreditSettings(newSettings);
    try {
      const userDocRef = doc(db, 'users', user.uid);
      await setDoc(userDocRef, { corp_credits: newSettings }, { merge: true });
    } catch (e) {
      console.error('Error saving corp credit:', e);
    }
  };

  const handleResetClaims = async () => {
    if (!user) return;
    const emptyUsage = {};
    const emptyTs = {};
    Object.keys(BENEFIT_MAP).forEach(id => {
      emptyUsage[id] = Array(12).fill(false);
      emptyTs[id] = Array(12).fill(null);
    });
    setUsage(emptyUsage);
    setTimestamps(emptyTs);
    setShowResetConfirm(false);

    try {
      const userDocRef = doc(db, 'users', user.uid);
      await setDoc(userDocRef, {
        claims: {},
        recent_credits: []
      }, { merge: true });
      setRecentCredits([]);
    } catch (e) {
      console.error('Error resetting claims:', e);
    }
  };

  // Plaid Integration - every call carries the caller's Firebase ID token
  // (see authedFetch above); the broker resolves that to a Plaid connection
  // in its own KV store, so it never needs an access token from us.
  const launchPlaidLink = async () => {
    setPlaidError(null);
    try {
      const data = await authedFetch('POST', '/plaid/link-token');
      if (!data.link_token) {
        throw new Error(data.error || 'Failed to generate link token');
      }

      if (window.Plaid) {
        const handler = window.Plaid.create({
          token: data.link_token,
          onSuccess: async (public_token) => {
            await exchangePlaidPublicToken(public_token);
          }
        });
        handler.open();
      } else {
        alert('Plaid Link SDK is loading... please try again.');
      }
    } catch (err) {
      setPlaidError(err.message);
    }
  };

  const exchangePlaidPublicToken = async (publicToken) => {
    try {
      await authedFetch('POST', '/plaid/exchange', { publicToken });
      await refreshPlaidStatus();
    } catch (err) {
      setPlaidError(err.message);
    }
  };

  const mapCardToPlaid = async (cardKey, plaidAccountId) => {
    if (!user) return;
    try {
      const data = await authedFetch('POST', '/plaid/mappings', {
        card_mappings: { [CARD_SLUGS[cardKey]]: plaidAccountId }
      });
      setCardPlaidMappings(translateCardMappings(data.card_mappings));
    } catch (e) {
      console.error('Error saving card mapping:', e);
    }
  };

  const syncPlaidTransactions = async () => {
    if (!plaidConnected || !user) return;
    setIsSyncingPlaid(true);
    setPlaidError(null);

    try {
      const { added, from_cursor, next_cursor } = await authedFetch('POST', '/plaid/sync');
      const newAdded = added || [];

      const matchedList = [...recentCredits];
      const newUsage = { ...usage };
      const newTs = { ...timestamps };

      newAdded.forEach((tx) => {
        const txName = tx.name || tx.merchant_name || '';
        const txAmount = tx.amount || 0;
        const txDate = tx.date || new Date().toISOString().split('T')[0];

        ['platinum', 'gold'].forEach((cardKey) => {
          INITIAL_DATA[cardKey].benefits.forEach((benefit) => {
            if (matchTransactionToBenefit(txName, benefit.name, txAmount)) {
              const monthIdx = new Date(txDate).getMonth();
              if (!newUsage[benefit.id]) newUsage[benefit.id] = Array(12).fill(false);
              if (!newTs[benefit.id]) newTs[benefit.id] = Array(12).fill(null);

              newUsage[benefit.id][monthIdx] = true;
              newTs[benefit.id][monthIdx] = new Date(txDate).getTime();

              matchedList.unshift({
                id: tx.transaction_id || Math.random().toString(),
                date: txDate,
                merchant: txName,
                benefitName: benefit.name,
                amount: Math.abs(txAmount),
                card: cardKey
              });
            }
          });
        });
      });

      const uniqueMatched = [];
      const seen = new Set();
      matchedList.forEach(item => {
        const key = `${item.date}-${item.merchant}-${item.amount}`;
        if (!seen.has(key)) {
          seen.add(key);
          uniqueMatched.push(item);
        }
      });

      setUsage(newUsage);
      setTimestamps(newTs);
      setRecentCredits(uniqueMatched.slice(0, 20));

      const serialized = serializeClaims(newUsage, newTs, trackingYear);

      const userDocRef = doc(db, 'users', user.uid);
      await setDoc(userDocRef, {
        claims: serialized,
        recent_credits: uniqueMatched.slice(0, 20)
      }, { merge: true });

      // Only commit the cursor once the claims it produced are safely
      // persisted - Plaid's cursor is destructive-on-advance, so committing
      // it first and then failing the write above would lose transactions
      // permanently. fromCursor lets the broker detect and reject a commit
      // if another device already advanced the cursor first.
      await authedFetch('POST', '/plaid/cursor', { cursor: next_cursor, fromCursor: from_cursor });
    } catch (err) {
      setPlaidError(err.message);
    } finally {
      setIsSyncingPlaid(false);
    }
  };

  // Card Stats Math
  const cardStats = useMemo(() => {
    const card = INITIAL_DATA[activeCard];
    const isCorp = corpCreditSettings[activeCard]?.enabled;
    const corpCreditVal = isCorp ? card.defaultCorpCredit : 0;
    const standardFee = card.fee;
    
    let totalBenefitsClaimed = 0;

    card.benefits.forEach(b => {
      const usedArr = usage[b.id] || Array(12).fill(false);
      const { indices } = getPeriodInfo(b.freq);

      indices.forEach((monthIdx) => {
        if (usedArr[monthIdx]) {
          totalBenefitsClaimed += getBenefitAmount(b.id, monthIdx);
        }
      });
    });

    totalBenefitsClaimed = Number(totalBenefitsClaimed.toFixed(2));
    
    // Effective Annual Fee Calculation:
    // Standard Fee - Corporate Credit - Total Benefits Claimed
    // If negative -> Profit!
    const netResult = standardFee - corpCreditVal - totalBenefitsClaimed;
    const isProfit = netResult <= 0;
    const profitOrFeeAmount = Math.abs(netResult);

    return {
      standardFee,
      corpCreditVal,
      isCorp,
      totalBenefitsClaimed,
      isProfit,
      profitOrFeeAmount
    };
  }, [activeCard, usage, corpCreditSettings]);

  if (authLoading) {
    return (
      <div className="min-h-screen bg-[#070b14] text-slate-100 flex items-center justify-center">
        <div className="flex items-center space-x-3 text-blue-400">
          <RefreshCw className="w-6 h-6 animate-spin" />
          <span className="text-lg font-medium">Loading Amex Tracker...</span>
        </div>
      </div>
    );
  }

  // Auth Screen
  if (!user) {
    return (
      <div className="min-h-screen bg-[#070b14] text-slate-100 flex items-center justify-center p-4">
        <div className="w-full max-w-md bg-[#0e1626] border border-slate-800 rounded-3xl p-8 shadow-2xl space-y-6">
          <div className="text-center space-y-3">
            <img src="/logo.png" alt="Amex Logo" className="w-16 h-16 object-contain mx-auto" />
            <h1 className="text-2xl font-bold tracking-tight text-white">Amex Benefit Tracker</h1>
            <p className="text-sm text-slate-400">Cloud synchronized across all your browsers</p>
          </div>

          {authError && (
            <div className="p-3 bg-red-500/10 border border-red-500/30 rounded-xl flex items-start space-x-3 text-red-400 text-sm">
              <AlertCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
              <span>{authError}</span>
            </div>
          )}

          <button
            onClick={handleGoogleSignIn}
            className="w-full py-3 px-4 bg-slate-800 hover:bg-slate-700 border border-slate-700 text-white rounded-2xl font-medium flex items-center justify-center space-x-3 transition-colors shadow-sm"
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24">
              <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
              <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
              <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"/>
              <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"/>
            </svg>
            <span>Continue with Google</span>
          </button>

          <div className="relative flex items-center justify-center my-4">
            <div className="border-t border-slate-800 w-full" />
            <span className="bg-[#0e1626] px-3 text-xs text-slate-500 uppercase font-medium">Or email</span>
          </div>

          <form onSubmit={handleAuthSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1">Email address</label>
              <div className="relative">
                <Mail className="w-5 h-5 text-slate-500 absolute left-3 top-3" />
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="name@example.com"
                  className="w-full pl-10 pr-4 py-2.5 bg-[#070b14] border border-slate-800 rounded-xl text-slate-100 placeholder-slate-600 focus:outline-none focus:border-blue-500 text-sm"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1">Password</label>
              <div className="relative">
                <Lock className="w-5 h-5 text-slate-500 absolute left-3 top-3" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="w-full pl-10 pr-10 py-2.5 bg-[#070b14] border border-slate-800 rounded-xl text-slate-100 placeholder-slate-600 focus:outline-none focus:border-blue-500 text-sm"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-3 text-slate-500 hover:text-slate-400"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              className="w-full py-3 bg-blue-600 hover:bg-blue-500 text-white rounded-2xl font-medium transition-colors shadow-lg shadow-blue-600/25"
            >
              {authMode === 'login' ? 'Sign In' : 'Create Account'}
            </button>
          </form>

          <div className="text-center pt-2">
            <button
              onClick={() => {
                setAuthMode(authMode === 'login' ? 'register' : 'login');
                setAuthError('');
              }}
              className="text-xs text-blue-400 hover:underline"
            >
              {authMode === 'login' ? "Don't have an account? Sign up" : 'Already have an account? Sign in'}
            </button>
          </div>
        </div>
      </div>
    );
  }

  const currentCard = INITIAL_DATA[activeCard];

  return (
    <div className="min-h-screen bg-[#070b14] text-slate-100 font-sans pb-16">
      {/* Top Header */}
      <header className="sticky top-0 z-30 bg-[#070b14]/90 backdrop-blur-md border-b border-slate-800/80 px-4 py-3">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center space-x-3">
            {/* Actual Amex Logo image instead of generic card symbol */}
            <img src="/logo.png" alt="Amex Logo" className="w-10 h-10 object-contain rounded-lg" />
            <div>
              <h1 className="text-base font-bold text-white leading-tight">Amex Benefit Tracker</h1>
              <p className="text-xs text-slate-400">
                Tracking <strong className="text-white">{trackingYear}</strong> Refreshed Benefits
              </p>
            </div>
          </div>

          <div className="flex items-center space-x-2.5">
            {/* Card Selector Buttons */}
            <div className="flex items-center bg-[#0e1626] p-1 rounded-2xl border border-slate-800/80">
              <button
                onClick={() => setActiveCard('platinum')}
                className={`px-3.5 py-1.5 rounded-xl font-bold text-xs transition-all text-center ${
                  activeCard === 'platinum'
                    ? 'bg-[#2563eb] text-white shadow-md shadow-blue-500/20'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                Platinum
              </button>
              <button
                onClick={() => setActiveCard('gold')}
                className={`px-3.5 py-1.5 rounded-xl font-bold text-xs transition-all text-center ${
                  activeCard === 'gold'
                    ? 'bg-[#d97706] text-white shadow-md shadow-amber-500/20'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                Gold
              </button>
            </div>

            <button
              onClick={handleManualRefresh}
              disabled={isRefreshingCloud}
              className="p-2.5 bg-[#0e1626] hover:bg-slate-800 border border-slate-800 text-slate-300 hover:text-white rounded-2xl transition-colors shadow-sm disabled:opacity-50 flex items-center justify-center"
              title="Refresh Data from Cloud (Firestore)"
            >
              <RefreshCw className={`w-4.5 h-4.5 ${isRefreshingCloud ? 'animate-spin text-blue-400' : ''}`} />
            </button>
            <button
              onClick={() => setIsSettingsOpen(true)}
              className="p-2.5 bg-[#0e1626] hover:bg-slate-800 border border-slate-800 text-slate-300 hover:text-white rounded-2xl transition-colors shadow-sm"
              title="Settings"
            >
              <Settings className="w-4.5 h-4.5" />
            </button>
          </div>
        </div>
      </header>

      {/* Main Container: 2 Column Layout */}
      <main className="max-w-7xl mx-auto px-4 pt-4 pb-4">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          
          {/* Left Column: Annual Fee, Benefits Claimed, Effective Fee, Recent Credits */}
          <div className="lg:col-span-5 space-y-4 lg:sticky lg:top-[69px] lg:max-h-[calc(100vh-85px)] lg:overflow-y-auto lg:pr-1">
            
            {/* Card 1: Card Overview & Fee Breakdown */}
            <div className="bg-[#0e1626] border border-slate-800/80 rounded-2xl p-5 space-y-4 shadow-xl">
              <div className="flex items-center justify-between">
                <h2 className="text-base font-bold text-white">{currentCard.name}</h2>
                <CreditCard className={`w-5 h-5 ${currentCard.accent}`} />
              </div>

              <div className="space-y-2.5 pt-1 text-sm">
                <div className="flex items-center justify-between text-slate-400">
                  <span>Standard Annual Fee</span>
                  <span className="font-bold text-white">${cardStats.standardFee}</span>
                </div>

                {/* Interactive Corporate Credit Toggle Row */}
                <div
                  onClick={() => toggleCorpCredit(activeCard)}
                  className="flex items-center justify-between cursor-pointer select-none py-1 hover:bg-slate-800/30 rounded-lg transition-colors px-1 -mx-1"
                  title="Click to toggle Corporate Credit"
                >
                  <span className={`flex items-center space-x-2 font-medium transition-colors ${
                    cardStats.isCorp ? 'text-emerald-400' : 'text-slate-500'
                  }`}>
                    {cardStats.isCorp ? (
                      <CheckCircle2 className="w-4.5 h-4.5 text-emerald-400 flex-shrink-0" />
                    ) : (
                      <Circle className="w-4.5 h-4.5 text-slate-600 flex-shrink-0" />
                    )}
                    <span>Corporate Credit</span>
                  </span>
                  <span className={`font-bold transition-colors ${
                    cardStats.isCorp ? 'text-emerald-400' : 'text-slate-500'
                  }`}>
                    -${cardStats.corpCreditVal}
                  </span>
                </div>

                <div className={`flex items-center justify-between ${currentCard.accent}`}>
                  <span>Total Benefits Claimed</span>
                  <span className="font-bold">
                    -${formatAmount(cardStats.totalBenefitsClaimed).replace('$', '')}
                  </span>
                </div>
              </div>
            </div>

            {/* Card 2: EFFECTIVE ANNUAL FEE Profit / Fee Box */}
            <div className={`border rounded-2xl p-5 shadow-xl space-y-2 transition-all ${
              cardStats.isProfit
                ? 'bg-[#0a201c] border-emerald-500/30 shadow-emerald-950/20'
                : 'bg-[#0e1626] border-slate-800/80'
            }`}>
              <div className="flex items-center justify-between text-xs font-bold text-slate-400 tracking-wider uppercase">
                <span>EFFECTIVE ANNUAL FEE</span>
                <TrendingUp className={`w-4 h-4 ${cardStats.isProfit ? 'text-emerald-400' : currentCard.accent}`} />
              </div>

              <div className="flex items-baseline space-x-2">
                <span className={`text-3xl font-extrabold transition-colors ${
                  cardStats.isProfit ? 'text-emerald-400' : 'text-white'
                }`}>
                  {formatAmount(cardStats.profitOrFeeAmount)}
                </span>
                <span className={`text-lg font-bold transition-colors ${
                  cardStats.isProfit ? 'text-emerald-400' : 'text-slate-400'
                }`}>
                  {cardStats.isProfit ? 'Profit' : 'Fee'}
                </span>
              </div>
            </div>

            {/* Card 3: Recent Credits Section */}
            <div className="bg-[#0e1626] border border-slate-800/80 rounded-2xl p-4 shadow-xl space-y-3">
              <button
                onClick={() => setIsRecentCreditsOpen(!isRecentCreditsOpen)}
                className="w-full flex items-center justify-between text-left"
              >
                <h3 className="text-sm font-bold text-white">Recent Credits</h3>
                {isRecentCreditsOpen ? (
                  <ChevronUp className={`w-5 h-5 ${currentCard.accent}`} />
                ) : (
                  <ChevronDown className={`w-5 h-5 ${currentCard.accent}`} />
                )}
              </button>

              {isRecentCreditsOpen && (
                <div className="pt-2 space-y-3 border-t border-slate-800/80">
                  <div className="flex items-center justify-between">
                    <p className="text-xs text-slate-400">Plaid synced statement charges</p>
                    <button
                      onClick={syncPlaidTransactions}
                      disabled={!plaidConnected || isSyncingPlaid}
                      className={`px-3 py-1.5 rounded-xl text-xs font-bold flex items-center space-x-1.5 ${
                        plaidConnected ? 'bg-blue-600 text-white' : 'bg-slate-800 text-slate-500 cursor-not-allowed'
                      }`}
                    >
                      <RefreshCw className={`w-3.5 h-3.5 ${isSyncingPlaid ? 'animate-spin' : ''}`} />
                      <span>Sync</span>
                    </button>
                  </div>

                  {recentCredits.length === 0 ? (
                    <p className="text-xs text-slate-500 text-center py-2">No recent synced credits.</p>
                  ) : (
                    <div className="space-y-2 max-h-48 overflow-y-auto">
                      {recentCredits.map((credit, i) => (
                        <div key={i} className="p-2.5 bg-[#070b14] border border-slate-800 rounded-xl flex items-center justify-between text-xs">
                          <div>
                            <div className="font-bold text-white">{credit.merchant}</div>
                            <div className="text-[10px] text-slate-400">{credit.date} • {credit.benefitName}</div>
                          </div>
                          <div className="font-bold text-emerald-400">${credit.amount}</div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>

          </div>

          {/* Right Column: All Credits Grid */}
          <div className="lg:col-span-7 space-y-3 lg:max-h-[calc(100vh-85px)] lg:overflow-y-auto lg:pr-1">
            {currentCard.benefits.map((benefit) => {
              const usedArr = usage[benefit.id] || Array(12).fill(false);
              const { keys, indices } = getPeriodInfo(benefit.freq);
              
              let claimedVal = 0;
              indices.forEach((mIdx) => {
                if (usedArr[mIdx]) {
                  claimedVal += getBenefitAmount(benefit.id, mIdx);
                }
              });

              const ratioText = `${formatAmount(claimedVal)} / ${formatAmount(benefit.total)}`;

              return (
                <div
                  key={benefit.id}
                  className="bg-[#0e1626] border border-slate-800/80 rounded-2xl p-5 shadow-xl space-y-3"
                >
                  {/* Header Row: Title & Ratio */}
                  <div className="flex items-center justify-between">
                    <div>
                      <h4 className="font-bold text-white text-base leading-tight">{benefit.name}</h4>
                      <p className="text-xs text-slate-400 mt-0.5">{benefit.desc}</p>
                    </div>

                    <div className="text-right">
                      <span className={`text-base font-extrabold ${
                        activeCard === 'platinum' ? 'text-[#60a5fa]' : 'text-[#fbbf24]'
                      }`}>
                        {ratioText}
                      </span>
                      <div className="w-12 h-0.5 ml-auto mt-0.5 rounded-full bg-slate-700" />
                    </div>
                  </div>

                  {/* Period Buttons Grid */}
                  <div className={`grid gap-2 ${
                    benefit.freq === 'month'
                      ? 'grid-cols-6'
                      : benefit.freq === 'quart'
                      ? 'grid-cols-4'
                      : benefit.freq === 'semi'
                      ? 'grid-cols-2'
                      : 'grid-cols-1'
                  }`}>
                    {keys.map((label, kIdx) => {
                      const monthIdx = indices[kIdx];
                      const isClaimed = usedArr[monthIdx];

                      return (
                        <button
                          key={label}
                          onClick={() => toggleBenefit(benefit.id, monthIdx)}
                          className={`py-3 px-2 rounded-xl text-xs font-bold transition-all text-center uppercase tracking-wide border ${
                            isClaimed
                              ? activeCard === 'platinum'
                                ? 'bg-[#2563eb] border-[#3b82f6] text-white shadow-md shadow-blue-600/30'
                                : 'bg-[#d97706] border-[#f59e0b] text-white shadow-md shadow-amber-600/30'
                              : 'bg-[#070b14] border-slate-800 text-slate-400 hover:border-slate-700'
                          }`}
                        >
                          {label}
                        </button>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>

        </div>
      </main>

      {/* Settings Modal */}
      {isSettingsOpen && (
        <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-[#0e1626] border border-slate-800 rounded-3xl max-w-md w-full p-6 space-y-6 shadow-2xl relative max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-slate-800 pb-4">
              <h3 className="text-lg font-bold text-white flex items-center space-x-2">
                <Settings className="w-5 h-5 text-blue-400" />
                <span>Settings & Integration</span>
              </h3>
              <button onClick={() => setIsSettingsOpen(false)} className="text-slate-400 hover:text-white">
                <X className="w-5 h-5" />
              </button>
            </div>

            {plaidError && (
              <div className="p-3 bg-red-500/10 border border-red-500/30 rounded-xl text-xs text-red-400 flex items-start space-x-2">
                <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
                <span>{plaidError}</span>
              </div>
            )}

            {/* Account */}
            <div className="space-y-2">
              <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Account</label>
              <div className="p-3 bg-[#070b14] border border-slate-800 rounded-2xl flex items-center justify-between">
                <div className="text-xs">
                  <div className="font-bold text-white">{user?.email || 'User'}</div>
                  <div className="text-emerald-400 text-[10px]">Cloud Synced</div>
                </div>
                <button
                  onClick={handleSignOut}
                  className="px-3 py-1 bg-red-500/10 text-red-400 border border-red-500/20 rounded-xl text-xs font-bold hover:bg-red-500/20"
                >
                  Sign Out
                </button>
              </div>
            </div>

            {/* Auto-Activate Offers */}
            <div className="space-y-2.5">
              <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Auto-Activate Offers</label>
              <div className="p-4 bg-[#070b14] border border-slate-800 rounded-2xl space-y-3">
                <button
                  onClick={() => {
                    setIsSettingsOpen(false);
                    setIsActivatorOpen(true);
                  }}
                  className="w-full py-3 bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 border border-amber-500/20 rounded-xl text-xs font-bold flex items-center justify-center space-x-2 transition-all"
                >
                  <Zap className="w-4 h-4 fill-amber-400 text-amber-400" />
                  <span>Auto-Activate Card Offers</span>
                </button>
              </div>
            </div>

            {/* Bank Connection */}
            <div className="space-y-2.5">
              <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Bank Connection</label>
              <div className="p-4 bg-[#070b14] border border-slate-800 rounded-2xl space-y-3">
                <button
                  onClick={launchPlaidLink}
                  className="w-full py-3 bg-blue-600 hover:bg-blue-500 text-white rounded-xl text-xs font-bold flex items-center justify-center space-x-2 transition-all"
                >
                  <Link2 className="w-4 h-4" />
                  <span>{plaidConnected ? 'Relink Accounts via Plaid' : 'Connect Amex via Plaid'}</span>
                </button>

                {plaidConnected && (
                  <div className="pt-2 space-y-2 border-t border-slate-800 text-xs">
                    <label className="block text-[11px] font-bold text-slate-400">Card Mappings</label>
                    <div className="grid grid-cols-2 gap-2">
                      <div>
                        <span className="text-[10px] text-slate-400 block mb-1">Platinum Account</span>
                        <select
                          value={cardPlaidMappings.platinum || ''}
                          onChange={(e) => mapCardToPlaid('platinum', e.target.value)}
                          className="w-full px-2.5 py-1.5 bg-[#0e1626] border border-slate-800 rounded-xl text-slate-200 text-xs"
                        >
                          <option value="">None</option>
                          {plaidAccounts.map((acc) => (
                            <option key={acc.account_id} value={acc.account_id}>
                              {acc.name} (…{acc.mask})
                            </option>
                          ))}
                        </select>
                      </div>
                      <div>
                        <span className="text-[10px] text-slate-400 block mb-1">Gold Account</span>
                        <select
                          value={cardPlaidMappings.gold || ''}
                          onChange={(e) => mapCardToPlaid('gold', e.target.value)}
                          className="w-full px-2.5 py-1.5 bg-[#0e1626] border border-slate-800 rounded-xl text-slate-200 text-xs"
                        >
                          <option value="">None</option>
                          {plaidAccounts.map((acc) => (
                            <option key={acc.account_id} value={acc.account_id}>
                              {acc.name} (…{acc.mask})
                            </option>
                          ))}
                        </select>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Reset */}
            <div className="pt-2 border-t border-slate-800 flex items-center justify-between">
              <div>
                <div className="text-xs font-bold text-red-400">Reset Benefit Claims</div>
                <div className="text-[10px] text-slate-500">Clear checked benefits for current year</div>
              </div>
              <button
                onClick={() => setShowResetConfirm(true)}
                className="px-3.5 py-1.5 bg-red-500/10 border border-red-500/20 text-red-400 hover:bg-red-500/20 rounded-xl text-xs font-bold"
              >
                Reset
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Auto-Activator Assistant Modal */}
      {isActivatorOpen && (
        <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-[#0e1626] border border-slate-800 rounded-3xl max-w-lg w-full p-6 space-y-5 shadow-2xl relative max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-slate-800 pb-4">
              <h3 className="text-lg font-bold text-white flex items-center space-x-2">
                <Zap className="w-5 h-5 text-amber-400 fill-amber-400" />
                <span>Amex Offer Auto-Activator</span>
              </h3>
              <button onClick={() => setIsActivatorOpen(false)} className="text-slate-400 hover:text-white">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-3 text-xs text-slate-300">
              <p>Activate 100+ Amex offers on your cards automatically in seconds.</p>

              <div className="p-4 bg-[#070b14] border border-slate-800 rounded-2xl space-y-2.5 font-sans">
                <div className="text-slate-400 font-bold">Step-by-Step Guide:</div>
                <ol className="list-decimal list-inside space-y-1.5 text-slate-300">
                  <li>Log in to <a href="https://global.americanexpress.com/offers/eligible" target="_blank" rel="noreferrer" className="text-blue-400 underline font-bold">americanexpress.com/offers</a>.</li>
                  <li>Press <kbd className="bg-slate-800 px-1.5 py-0.5 rounded font-mono text-[10px]">F12</kbd> to open Browser Developer Console.</li>
                  <li>Click Copy Script below, paste into Console, and press <kbd className="bg-slate-800 px-1.5 py-0.5 rounded font-mono text-[10px]">Enter</kbd>.</li>
                </ol>
              </div>

              <div className="bg-[#070b14] p-3 rounded-2xl border border-slate-800 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-[10px] font-bold text-slate-400 uppercase">Auto-Activation Script</span>
                  <button
                    onClick={() => {
                      const scriptText = `(function(){const btns=Array.from(document.querySelectorAll('button')).filter(b=>b.textContent.includes('Add to Card')||b.textContent.includes('Enroll'));console.log('Found '+btns.length+' offers');btns.forEach((b,i)=>setTimeout(()=>b.click(),i*400));})();`;
                      navigator.clipboard.writeText(scriptText);
                      alert('Script copied to clipboard!');
                    }}
                    className="px-3 py-1 bg-amber-500/10 text-amber-400 border border-amber-500/20 rounded-xl text-[10px] font-bold hover:bg-amber-500/20"
                  >
                    Copy Script
                  </button>
                </div>
                <pre className="text-[10px] text-emerald-400 overflow-x-auto whitespace-pre-wrap font-mono p-2 bg-[#0e1626] rounded-xl">
                  {`(function() {
  const btns = Array.from(document.querySelectorAll('button')).filter(b => 
    b.textContent.includes('Add to Card') || b.textContent.includes('Enroll')
  );
  console.log('Found ' + btns.length + ' offers');
  btns.forEach((b, i) => setTimeout(() => b.click(), i * 400));
})();`}
                </pre>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Confirmation Modal for Reset */}
      {showResetConfirm && (
        <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-[#0e1626] border border-slate-800 rounded-3xl max-w-sm w-full p-6 space-y-4 shadow-2xl text-center">
            <h4 className="text-base font-bold text-white">Reset All Claims?</h4>
            <p className="text-xs text-slate-400">Are you sure you want to clear all checked benefits for {trackingYear}?</p>
            <div className="flex items-center justify-center space-x-3 pt-2">
              <button
                onClick={() => setShowResetConfirm(false)}
                className="px-4 py-2 bg-slate-800 text-slate-300 rounded-xl text-xs font-bold"
              >
                Cancel
              </button>
              <button
                onClick={handleResetClaims}
                className="px-4 py-2 bg-red-600 hover:bg-red-500 text-white rounded-xl text-xs font-bold shadow-md"
              >
                Confirm Reset
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}