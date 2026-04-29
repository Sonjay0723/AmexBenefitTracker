import React, { useState, useMemo, useEffect } from 'react';
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
  Edit2,
  HardDrive,
  LogOut,
  Mail,
  Lock,
  Eye,
  EyeOff
} from 'lucide-react';
import { auth, db, googleProvider, isConfigured } from './firebase';
import { signInWithEmailAndPassword, createUserWithEmailAndPassword, signInWithPopup, signOut, onAuthStateChanged } from 'firebase/auth';
import { doc, getDoc, setDoc } from 'firebase/firestore';

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'
];

const STORAGE_KEY = 'pitta_amex_tracker_v2';

const INITIAL_DATA = {
  platinum: {
    name: 'The Platinum Card®',
    bgColor: 'bg-slate-950',
    accent: 'text-blue-400',
    accentBg: 'bg-blue-600',
    fee: 895,
    defaultCorpCredit: 150,
    benefits: [
      { id: 'p_hotel', name: 'Hotel Credit', total: 600, freq: 'semi', desc: '$300 per half-year' },
      { id: 'p_uber', name: 'Uber Cash', total: 200, freq: 'month', desc: '$15/mo ($35 Dec)' },
      { id: 'p_uber_one', name: 'Uber One', total: 96, freq: 'annual', desc: 'Annual membership credit' },
      { id: 'p_resy', name: 'Resy Credit', total: 400, freq: 'quart', desc: '$100 per quarter' },
      { id: 'p_streaming', name: 'Digital Entertainment', total: 300, freq: 'month', desc: '$25 per month' },
      { id: 'p_lulu', name: 'lululemon Credit', total: 300, freq: 'quart', desc: '$75 per quarter' },
      { id: 'p_walmart', name: 'Walmart+', total: 98, freq: 'annual', desc: 'Annual membership credit' },
      { id: 'p_saks', name: 'Saks Fifth Avenue', total: 100, freq: 'semi', desc: '$50 per half-year' },
      { id: 'p_clear', name: 'CLEAR+ Credit', total: 209, freq: 'annual', desc: 'Full membership coverage' },
      { id: 'p_airline', name: 'Airline Fee Credit', total: 200, freq: 'annual', desc: 'Incidental fees only' }
    ]
  },
  gold: {
    name: 'American Express® Gold Card',
    bgColor: 'bg-slate-950',
    accent: 'text-amber-400',
    accentBg: 'bg-amber-600',
    fee: 325,
    defaultCorpCredit: 100,
    benefits: [
      { id: 'g_uber', name: 'Uber Cash', total: 120, freq: 'month', desc: '$10 per month' },
      { id: 'g_dining', name: 'Dining Credit', total: 120, freq: 'month', desc: '$10 per month' },
      { id: 'g_dunkin', name: 'Dunkin Credit', total: 84, freq: 'month', desc: '$7 per month' },
      { id: 'g_resy', name: 'Resy Credit', total: 100, freq: 'semi', desc: '$50 per half-year' }
    ]
  }
};

export default function App() {
  const [activeCard, setActiveCard] = useState('platinum');
  const [usage, setUsage] = useState({});
  const trackingYear = new Intl.DateTimeFormat('en-US', { year: 'numeric', timeZone: 'America/New_York' }).format(new Date());
  const [corpCreditSettings, setCorpCreditSettings] = useState({
    platinum: { enabled: false },
    gold: { enabled: false }
  });
  const [isLoaded, setIsLoaded] = useState(false);
  const [user, setUser] = useState(null);
  const [isAuthChecking, setIsAuthChecking] = useState(true);

  // Load from Firestore on auth state change
  useEffect(() => {
    if (!isConfigured) {
      setIsAuthChecking(false);
      return;
    }
    const unsubscribe = onAuthStateChanged(auth, async (currentUser) => {
      setUser(currentUser);
      if (currentUser) {
        const docRef = doc(db, 'users', currentUser.uid);
        const docSnap = await getDoc(docRef);
        
        if (docSnap.exists()) {
          const parsed = docSnap.data();
          if (parsed.usage) setUsage(parsed.usage);
          if (parsed.corpCreditSettings) setCorpCreditSettings(parsed.corpCreditSettings);
        } else {
          const initialUsage = {};
          Object.keys(INITIAL_DATA).forEach(cardKey => {
            INITIAL_DATA[cardKey].benefits.forEach(b => {
              initialUsage[b.id] = Array(12).fill(false);
            });
          });
          setUsage(initialUsage);
        }
        setIsLoaded(true);
      } else {
        setIsLoaded(false);
      }
      setIsAuthChecking(false);
    });

    return () => unsubscribe();
  }, []);

  // Save to Firestore on change
  useEffect(() => {
    if (isLoaded && user) {
      setDoc(doc(db, 'users', user.uid), {
        usage,
        corpCreditSettings
      }, { merge: true });
    }
  }, [usage, corpCreditSettings, isLoaded, user]);

  const toggleMonth = (benefitId, monthIndex) => {
    setUsage(prev => {
      const currentArr = prev[benefitId] || Array(12).fill(false);
      const newVal = !currentArr[monthIndex];
      const nextUsage = { ...prev };
      nextUsage[benefitId] = currentArr.map((val, idx) => idx === monthIndex ? newVal : val);

      // Link Uber Cash across cards
      if (benefitId === 'p_uber' || benefitId === 'g_uber') {
        const otherId = benefitId === 'p_uber' ? 'g_uber' : 'p_uber';
        const otherArr = prev[otherId] || Array(12).fill(false);
        nextUsage[otherId] = otherArr.map((val, idx) => idx === monthIndex ? newVal : val);
      }
      return nextUsage;
    });
  };

  const toggleCorpCredit = () => {
    setCorpCreditSettings(prev => ({
      ...prev,
      [activeCard]: { ...prev[activeCard], enabled: !prev[activeCard].enabled }
    }));
  };

  const resetData = () => {
    if (window.confirm("Are you sure you want to reset your tracking progress?")) {
      const initialUsage = {};
      Object.keys(INITIAL_DATA).forEach(cardKey => {
        INITIAL_DATA[cardKey].benefits.forEach(b => {
          initialUsage[b.id] = Array(12).fill(false);
        });
      });
      setUsage(initialUsage);
    }
  };

  const handleSignOut = () => {
    if (window.confirm("Are you sure you want to sign out?")) {
      signOut(auth);
    }
  };

  const currentCard = INITIAL_DATA[activeCard];
  const currentCorp = corpCreditSettings[activeCard] || { enabled: false };

  const stats = useMemo(() => {
    let totalValue = 0;
    const benefitStats = currentCard.benefits.map(benefit => {
      const usedMonths = usage[benefit.id] || Array(12).fill(false);
      let earned = 0;
      if (benefit.freq === 'month') {
        const count = usedMonths.filter(Boolean).length;
        if (benefit.id === 'p_uber') {
          earned = usedMonths.reduce((acc, val, idx) => acc + (val ? (idx === 11 ? 35 : 15) : 0), 0);
        } else {
          earned = (benefit.total / 12) * count;
        }
      } else if (benefit.freq === 'quart') {
        const qCount = [usedMonths[0], usedMonths[3], usedMonths[6], usedMonths[9]].filter(Boolean).length;
        earned = qCount * (benefit.total / 4);
      } else if (benefit.freq === 'semi') {
        const hCount = [usedMonths[0], usedMonths[6]].filter(Boolean).length;
        earned = hCount * (benefit.total / 2);
      } else if (benefit.freq === 'annual') {
        earned = usedMonths[0] ? benefit.total : 0;
      }
      totalValue += earned;
      return { ...benefit, earned };
    });

    const activeCorpCredit = currentCorp.enabled ? currentCard.defaultCorpCredit : 0;
    const effectiveFee = currentCard.fee - activeCorpCredit - totalValue;
    return { totalValue, benefitStats, effectiveFee };
  }, [activeCard, usage, currentCorp]);

  const renderPeriods = (benefit) => {
    const used = usage[benefit.id] || Array(12).fill(false);
    if (benefit.freq === 'month') {
      return MONTH_NAMES.map((m, i) => (
        <button
          key={m}
          onClick={() => toggleMonth(benefit.id, i)}
          className={`h-11 rounded-lg flex flex-col items-center justify-center transition-all col-span-1 border ${used[i] ? `${currentCard.accentBg} text-white border-transparent shadow-lg` : 'bg-slate-800/50 text-slate-400 hover:text-white border-slate-700/50'}`}
        >
          <span className="text-[10px] font-bold uppercase">{m.substring(0, 3)}</span>
          <div className="mt-0.5">{used[i] ? <CheckCircle2 size={12} /> : <Circle size={12} className="opacity-30" />}</div>
        </button>
      ));
    }

    const configMap = {
      quart: { span: 'col-span-3', labels: ['Q1', 'Q2', 'Q3', 'Q4'], idx: [0, 3, 6, 9] },
      semi: { span: 'col-span-6', labels: ['Half 1', 'Half 2'], idx: [0, 6] },
      annual: { span: 'col-span-12', labels: ['Annual Credit'], idx: [0] }
    };

    const config = configMap[benefit.freq];

    return config.labels.map((label, i) => (
      <button
        key={label}
        onClick={() => toggleMonth(benefit.id, config.idx[i])}
        className={`h-11 rounded-lg flex flex-col items-center justify-center transition-all ${config.span} border ${used[config.idx[i]] ? `${currentCard.accentBg} text-white border-transparent shadow-lg` : 'bg-slate-800/50 text-slate-400 hover:text-white border-slate-700/50'}`}
      >
        <span className="text-[10px] font-bold uppercase">{label}</span>
        <div className="mt-0.5">{used[config.idx[i]] ? <CheckCircle2 size={12} /> : <Circle size={12} className="opacity-30" />}</div>
      </button>
    ));
  };

  if (!isConfigured) {
    return (
      <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center p-4">
        <div className="w-full max-w-md bg-slate-900/60 backdrop-blur-md border border-red-900/50 rounded-3xl p-8 shadow-2xl text-center">
          <img src="./logo.png" alt="Amex Logo" className="w-20 h-20 object-contain rounded-2xl shadow-lg mx-auto mb-6 opacity-50" />
          <h2 className="text-2xl font-bold text-white mb-4">Setup Required</h2>
          <p className="text-slate-400 mb-6">
            You must provide your Firebase configuration in the <code className="bg-slate-800 px-2 py-1 rounded text-blue-400">.env</code> file before the app can run.
          </p>
          <p className="text-sm text-slate-500">
            See the <strong>walkthrough.md</strong> file for instructions on how to set up Firebase and create your <code className="bg-slate-800 px-1 py-0.5 rounded text-slate-300">.env</code> file.
          </p>
        </div>
      </div>
    );
  }

  if (isAuthChecking) {
    return <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center text-slate-400"><div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mb-4"></div>Loading...</div>;
  }

  if (!user) {
    return <LoginScreen />;
  }

  return (
    <div className={`min-h-screen p-4 md:p-8 bg-slate-950 text-white font-sans`}>
      <header className="max-w-6xl mx-auto mb-8 flex flex-col md:flex-row md:items-end justify-between gap-6">
        <div className="flex items-center gap-4">
          <img src="./logo.png" alt="Amex Logo" className="w-16 h-16 object-contain rounded-2xl shadow-lg" />
          <div>
            <h1 className="text-3xl font-bold tracking-tight mb-1">Amex Benefit Tracker</h1>
            <p className="text-slate-400 italic">
              Tracking {trackingYear} Refreshed Benefits
            </p>
          </div>
        </div>
        <div className="flex items-center gap-4">
          <button onClick={resetData} className="p-2 text-slate-600 hover:text-red-400 transition-colors" title="Clear Progress"><RotateCcw size={20} /></button>
          <div className="flex bg-slate-900/50 backdrop-blur-md p-1 rounded-xl border border-slate-800">
            <button onClick={() => setActiveCard('platinum')} className={`px-8 py-2 rounded-lg font-medium transition-all ${activeCard === 'platinum' ? 'bg-blue-600 text-white shadow-lg' : 'text-slate-400 hover:text-slate-200'}`}>Platinum</button>
            <button onClick={() => setActiveCard('gold')} className={`px-8 py-2 rounded-lg font-medium transition-all ${activeCard === 'gold' ? 'bg-amber-600 text-white shadow-lg' : 'text-slate-400 hover:text-slate-200'}`}>Gold</button>
          </div>
          <button onClick={handleSignOut} className="p-2 text-slate-600 hover:text-red-400 transition-colors" title="Sign Out"><LogOut size={20} /></button>
        </div>
      </header>

      <div className="max-w-6xl mx-auto grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div className="space-y-6">
          <div className="bg-slate-900/40 backdrop-blur-sm border border-slate-800/50 p-8 rounded-3xl">
            <div className="flex items-center gap-3 mb-6">
              <span className={currentCard.accent}><CreditCard size={28} /></span>
              <h3 className="text-xl font-bold">{currentCard.name}</h3>
            </div>
            <div className="space-y-4">
              <div className="flex justify-between items-center py-3 border-b border-slate-800/50">
                <span className="text-slate-400">Standard Annual Fee</span>
                <span className="font-mono font-bold text-white">${currentCard.fee}</span>
              </div>
              <div className="flex justify-between items-center py-3 border-b border-slate-800/50">
                <div className="flex items-center gap-2">
                  <button onClick={toggleCorpCredit} className={currentCorp.enabled ? 'text-emerald-400' : 'text-slate-600'}>
                    {currentCorp.enabled ? <CheckCircle2 size={18} /> : <Circle size={18} />}
                  </button>
                  <span className={currentCorp.enabled ? 'text-slate-200' : 'text-slate-500'}>Corporate Credit</span>
                </div>
                <span className={`font-mono font-bold ${currentCorp.enabled ? 'text-emerald-400' : 'text-slate-600'}`}>-${currentCard.defaultCorpCredit}</span>
              </div>
              <div className="flex justify-between items-center py-3">
                <span className="text-slate-400">Total Benefits Claimed</span>
                <span className={`font-bold ${currentCard.accent}`}>-${stats.totalValue.toFixed(0)}</span>
              </div>
            </div>
          </div>

          <div className={`p-8 rounded-3xl border shadow-xl transition-all ${stats.effectiveFee <= 0 ? 'bg-emerald-500/10 border-emerald-500/30' : 'bg-slate-900/60 border-slate-800'}`}>
            <div className="flex items-center justify-between mb-4">
              <div className={`p-2 rounded-lg bg-slate-800 ${currentCard.accent}`}><TrendingUp size={20} /></div>
              <span className="text-xs font-bold text-slate-500 uppercase tracking-widest">Effective Annual Fee</span>
            </div>
            <h2 className={`text-5xl font-black ${stats.effectiveFee <= 0 ? 'text-emerald-400' : 'text-white'}`}>
              ${Math.abs(stats.effectiveFee).toFixed(0)}
              {stats.effectiveFee <= 0 && <span className="text-xl ml-2 font-medium">Profit</span>}
            </h2>
            <p className="mt-4 text-xs text-slate-400 leading-relaxed">
              {stats.effectiveFee <= 0 ? `Excellent management. You've officially 'beaten' the annual fee for ${trackingYear}.` : `Extract $${stats.effectiveFee.toFixed(0)} more in value to reach break-even status.`}
            </p>
          </div>
        </div>

        <div className="lg:col-span-2 space-y-4">
          {stats.benefitStats.map(b => (
            <div key={b.id} className="bg-slate-900/50 backdrop-blur-sm border border-slate-800/50 rounded-2xl overflow-hidden p-6 hover:border-slate-700 transition-colors">
              <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
                <div>
                  <h4 className="font-bold text-lg text-white">{b.name}</h4>
                  <p className="text-xs text-slate-500">{b.desc}</p>
                </div>
                <div className="flex items-center gap-4 bg-slate-950/50 px-4 py-2 rounded-xl border border-slate-800/50">
                  <span className="text-xl font-bold text-white">${b.earned.toFixed(0)} <span className="text-slate-500 text-xs font-normal">/ ${b.total}</span></span>
                  <div className="w-20 h-1.5 bg-slate-800 rounded-full overflow-hidden">
                    <div className={`h-full ${currentCard.accentBg}`} style={{ width: `${(b.earned / b.total) * 100}%` }}></div>
                  </div>
                </div>
              </div>
              <div className="grid grid-cols-4 sm:grid-cols-6 md:grid-cols-12 gap-2">
                {renderPeriods(b)}
              </div>
            </div>
          ))}
        </div>
      </div>

      <footer className="max-w-6xl mx-auto mt-12 pt-8 border-t border-slate-900/50 text-center text-slate-600 text-xs flex justify-between items-center">
        <p>© 2026 Amex Dashboard | Jayson Pitta</p>
        <div className="flex items-center gap-2">
          <HardDrive className="text-blue-500" size={14} />
          <span className="text-blue-500">Cloud Sync Active</span>
        </div>
      </footer>
    </div>
  );
}

function LoginScreen() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [isLogin, setIsLogin] = useState(true);
  const [error, setError] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const handleEmailAuth = async (e) => {
    e.preventDefault();
    setError('');
    try {
      if (isLogin) {
        await signInWithEmailAndPassword(auth, email, password);
      } else {
        await createUserWithEmailAndPassword(auth, email, password);
      }
    } catch (err) {
      setError(err.message.replace('Firebase: ', ''));
    }
  };

  const handleGoogleAuth = async () => {
    setError('');
    try {
      await signInWithPopup(auth, googleProvider);
    } catch (err) {
      setError(err.message.replace('Firebase: ', ''));
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center p-4">
      <div className="w-full max-w-md bg-slate-900/60 backdrop-blur-md border border-slate-800 rounded-3xl p-8 shadow-2xl">
        <div className="flex flex-col items-center mb-8">
          <img src="./logo.png" alt="Amex Logo" className="w-20 h-20 object-contain rounded-2xl shadow-lg mb-4" />
          <h2 className="text-2xl font-bold text-white">Amex Benefit Tracker</h2>
          <p className="text-slate-400 text-sm mt-1">{isLogin ? 'Sign in to access your data' : 'Create an account to start tracking'}</p>
        </div>

        {error && <div className="mb-4 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-red-400 text-sm text-center">{error}</div>}

        <form onSubmit={handleEmailAuth} className="space-y-4">
          <div>
            <div className="relative">
              <Mail className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" size={18} />
              <input 
                type="email" 
                placeholder="Email address" 
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl py-3 pl-10 pr-4 text-white placeholder-slate-500 focus:outline-none focus:border-blue-500 transition-colors"
                required
              />
            </div>
          </div>
          <div>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" size={18} />
              <input 
                type={showPassword ? "text" : "password"} 
                placeholder="Password" 
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl py-3 pl-10 pr-12 text-white placeholder-slate-500 focus:outline-none focus:border-blue-500 transition-colors"
                required
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition-colors"
              >
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
          </div>
          
          <button type="submit" className="w-full bg-blue-600 hover:bg-blue-700 text-white font-medium py-3 rounded-xl transition-colors shadow-lg shadow-blue-600/20">
            {isLogin ? 'Sign In' : 'Create Account'}
          </button>
        </form>

        <div className="mt-6 mb-6 relative flex items-center justify-center">
          <div className="absolute inset-0 flex items-center"><div className="w-full border-t border-slate-800"></div></div>
          <span className="relative px-4 bg-slate-900/60 text-slate-500 text-sm">or</span>
        </div>

        <button 
          onClick={handleGoogleAuth}
          type="button"
          className="w-full bg-slate-800 hover:bg-slate-700 text-white font-medium py-3 rounded-xl transition-colors border border-slate-700 flex items-center justify-center gap-2"
        >
          <svg className="w-5 h-5" viewBox="0 0 24 24">
            <path fill="currentColor" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
            <path fill="currentColor" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
            <path fill="currentColor" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" />
            <path fill="currentColor" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" />
          </svg>
          Continue with Google
        </button>

        <p className="mt-8 text-center text-sm text-slate-400">
          {isLogin ? "Don't have an account? " : "Already have an account? "}
          <button onClick={() => setIsLogin(!isLogin)} className="text-blue-400 hover:text-blue-300 font-medium transition-colors">
            {isLogin ? 'Sign up' : 'Sign in'}
          </button>
        </p>
      </div>
    </div>
  );
}