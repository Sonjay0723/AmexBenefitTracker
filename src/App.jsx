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
  HardDrive
} from 'lucide-react';

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
  const [trackingYear, setTrackingYear] = useState('2025');
  const [isEditingYear, setIsEditingYear] = useState(false);
  const [corpCreditSettings, setCorpCreditSettings] = useState({
    platinum: { enabled: true },
    gold: { enabled: true }
  });
  const [isLoaded, setIsLoaded] = useState(false);

  // Load from localStorage on mount
  useEffect(() => {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      const parsed = JSON.parse(saved);
      if (parsed.usage) setUsage(parsed.usage);
      if (parsed.trackingYear) setTrackingYear(parsed.trackingYear);
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
  }, []);

  // Save to localStorage on change
  useEffect(() => {
    if (isLoaded) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        usage,
        trackingYear,
        corpCreditSettings
      }));
    }
  }, [usage, trackingYear, corpCreditSettings, isLoaded]);

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
    if (window.confirm("Are you sure you want to clear all tracking progress on this device?")) {
      localStorage.removeItem(STORAGE_KEY);
      window.location.reload();
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

  return (
    <div className={`min-h-screen p-4 md:p-8 bg-slate-950 text-white font-sans`}>
      <header className="max-w-6xl mx-auto mb-8 flex flex-col md:flex-row md:items-end justify-between gap-6">
        <div className="flex items-center gap-4">
          <img src="/logo.png" alt="Amex Logo" className="w-16 h-16 object-contain rounded-2xl shadow-lg" />
          <div>
            <h1 className="text-3xl font-bold tracking-tight mb-1">Benefit Tracker</h1>
            <div className="flex items-center gap-2 group cursor-pointer" onClick={() => setIsEditingYear(true)}>
              {isEditingYear ? (
              <input
                autoFocus
                type="text"
                value={trackingYear}
                onChange={e => setTrackingYear(e.target.value)}
                onBlur={() => setIsEditingYear(false)}
                onKeyDown={e => e.key === 'Enter' && setIsEditingYear(false)}
                className="bg-slate-900 border border-slate-700 rounded px-2 py-0.5 text-sm italic text-white outline-none w-24"
              />
            ) : (
              <p className="text-slate-400 italic flex items-center gap-1.5 hover:text-slate-200 transition-colors">
                Tracking {trackingYear} Refreshed Benefits
                <span className="opacity-0 group-hover:opacity-50 transition-opacity"><Edit2 size={12} /></span>
              </p>
            )}
          </div>
          </div>
        </div>
        <div className="flex items-center gap-4">
          <button onClick={resetData} className="p-2 text-slate-600 hover:text-red-400 transition-colors" title="Clear Local Data"><RotateCcw size={20} /></button>
          <div className="flex bg-slate-900/50 backdrop-blur-md p-1 rounded-xl border border-slate-800">
            <button onClick={() => setActiveCard('platinum')} className={`px-8 py-2 rounded-lg font-medium transition-all ${activeCard === 'platinum' ? 'bg-blue-600 text-white shadow-lg' : 'text-slate-400 hover:text-slate-200'}`}>Platinum</button>
            <button onClick={() => setActiveCard('gold')} className={`px-8 py-2 rounded-lg font-medium transition-all ${activeCard === 'gold' ? 'bg-amber-600 text-white shadow-lg' : 'text-slate-400 hover:text-slate-200'}`}>Gold</button>
          </div>
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
              <div className="p-2 rounded-lg bg-slate-800 text-blue-400"><TrendingUp size={20} /></div>
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
          <HardDrive className="text-slate-500" size={14} />
          <span className="text-slate-500">Local Storage Active</span>
        </div>
      </footer>
    </div>
  );
}