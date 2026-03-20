import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { Subscription } from 'rxjs';
import { MarketDataService, CryptoAsset, MarketGlobalData, GoldData, VNStockData } from '../../services/market-data.service';
import { LanguageService } from '../../services/language.service';
import { InvestmentLotService, InvestmentLot, AssetType } from '../../services/investment-lot.service';
import { PortfolioCoinService, PortfolioCoin } from '../../services/portfolio-coin.service';
import { UserService } from '../../services/user.service';
import { ChartComponent } from 'ng-apexcharts';

type InvestmentTab = 'crypto' | 'gold' | 'vn' | 'portfolio';
type SortField = 'rank' | 'price' | 'change24h' | 'marketCap' | 'volume';
type SortDir = 'asc' | 'desc';

interface GoldHolding {
  symbol: string;
  name: string;
  netQty: number;
  avgBuyPriceVnd: number;
  totalBuyCostVnd: number;
  currentSellPriceVnd: number | null;
  currentValueVnd: number | null;
  pnlVnd: number | null;
  pnlPct: number | null;
}

interface StockHolding {
  ticker: string;
  name: string;
  netQty: number;
  avgBuyPriceVnd: number;
  totalBuyCostVnd: number;
  currentPriceVnd: number | null;
  currentValueVnd: number | null;
  pnlVnd: number | null;
  pnlPct: number | null;
}

interface CoinHolding {
  coinId: string;
  symbol: string;
  name: string;
  image: string;
  netQty: number;
  avgBuyPriceVnd: number;
  totalBuyCostVnd: number;
  currentPriceUsd: number | null;
  currentPriceVnd: number | null;
  currentValueVnd: number | null;
  pnlVnd: number | null;
  pnlPct: number | null;
  portfolioPct: number;
  change1h: number;
  change24h: number;
  change7d: number;
  volume24h: number;
  marketCap: number;
  sparkline: number[];
  lastTxType: 'BUY' | 'SELL';
  lastBuyDate: string;
  portfolioCoin: PortfolioCoin;
}

@Component({
  selector: 'app-investment',
  templateUrl: './investment.component.html',
  styleUrls: ['./investment.component.css']
})
export class InvestmentComponent implements OnInit, OnDestroy {

  @ViewChild('donutChart') donutChartRef?: ChartComponent;
  @ViewChild('perfChart') perfChartRef?: ChartComponent;

  activeTab: InvestmentTab = 'crypto';
  portfolioSubTab: 'crypto' | 'gold' | 'vn_stock' = 'crypto';

  // Crypto
  coins: CryptoAsset[] = [];
  filteredCoins: CryptoAsset[] = [];
  loading = false;
  error: string | null = null;
  globalData: MarketGlobalData | null = null;
  lastUpdated: Date | null = null;
  searchQuery = '';
  sortField: SortField = 'rank';
  sortDir: SortDir = 'asc';
  isRefreshing = false;

  // Gold
  gold: GoldData | null = null;
  goldLoading = false;
  goldError: string | null = null;

  // VN Stocks
  vnStocks: VNStockData[] = [];
  vnLoading = false;
  vnError: string | null = null;

  // Portfolio — raw lots (for aggregation)
  portfolioLots: InvestmentLot[] = [];
  portfolioLoading = false;
  liveUsdVndRate = 25400;
  private userId = '';

  // Portfolio — watchlist coins
  portfolioCoins: PortfolioCoin[] = [];

  // Add coin search
  addCoinQuery = '';
  showAddCoinDropdown = false;

  // Coin Detail View
  selectedCoin: PortfolioCoin | null = null;
  coinDetailLots: InvestmentLot[] = [];
  coinDetailPage = 0;
  readonly coinDetailPageSize = 10;
  coinDetailTotalElements = 0;
  coinDetailTotalPages = 0;
  coinDetailLoading = false;

  // Three-dot menu state
  openMenuCoinId: string | null = null;
  dropdownPos: { top: number; right: number } | null = null;

  // Gold Portfolio
  goldDetailSymbol: string | null = null;
  goldDetailLots: InvestmentLot[] = [];
  goldDetailPage = 0;
  readonly goldDetailPageSize = 10;
  goldDetailTotalElements = 0;
  goldDetailTotalPages = 0;
  goldDetailLoading = false;
  openMenuGoldSymbol: string | null = null;
  goldDropdownPos: { top: number; right: number } | null = null;

  // Stock Portfolio
  stockDetailTicker: string | null = null;
  stockDetailLots: InvestmentLot[] = [];
  stockDetailPage = 0;
  readonly stockDetailPageSize = 10;
  stockDetailTotalElements = 0;
  stockDetailTotalPages = 0;
  stockDetailLoading = false;
  openMenuStockTicker: string | null = null;
  stockDropdownPos: { top: number; right: number } | null = null;

  // Gold/Stock symbol search
  goldSymbolSearch = '';
  stockSymbolSearch = '';

  // Currency toggle
  displayCurrency: 'USD' | 'VND' = 'USD';

  // Transaction Modal
  showTxModal = false;
  txTab: 'BUY' | 'SELL' = 'BUY';
  txEditingLot: InvestmentLot | null = null;
  txLockedCoin: PortfolioCoin | null = null;  // when opened from coin row
  showTxFeesNotes = false;
  txForm: {
    assetType: AssetType;
    coinId: string;
    assetSymbol: string;
    assetName: string;
    totalSpent: number | null;
    quantity: number | null;
    pricePerUnit: number | null;
    transactionDate: Date;
    fees: number | null;
    note: string;
  } = {
    assetType: 'CRYPTO',
    coinId: '',
    assetSymbol: '',
    assetName: '',
    totalSpent: null,
    quantity: null,
    pricePerUnit: null,
    transactionDate: new Date(),
    fees: null,
    note: ''
  };
  txSaving = false;

  // Charts
  donutOptions: {
    series?: number[]; chart?: any; labels?: string[]; colors?: string[];
    legend?: any; dataLabels?: any; plotOptions?: any; theme?: any; tooltip?: any;
  } = {};
  performanceOptions: {
    series?: any[]; chart?: any; dataLabels?: any; stroke?: any; fill?: any; colors?: string[];
    xaxis?: any; yaxis?: any; grid?: any; theme?: any; tooltip?: any;
  } = {};

  private subs = new Subscription();
  currentLang = '';

  constructor(
    public marketService: MarketDataService,
    public language: LanguageService,
    private investmentLotService: InvestmentLotService,
    private portfolioCoinService: PortfolioCoinService,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    this.subs.add(this.language.currentLanguage$.subscribe(lang => {
      this.currentLang = lang;
    }));

    this.userService.getUserInfo().subscribe({
      next: (user: any) => {
        this.userId = user?.id || user?.userId || '';
        if (this.userId) {
          // Load lots eagerly so Gold/Stock tabs also have data
          this.investmentLotService.getByUser(this.userId).subscribe({
            next: lots => { this.portfolioLots = lots; }
          });
          if (this.activeTab === 'portfolio') {
            this.loadPortfolio();
          }
        }
      }
    });

    this.subs.add(this.marketService.crypto$.subscribe(coins => {
      this.coins = coins;
      this.applyFilters();
      if (this.activeTab === 'portfolio' && this.portfolioCoins.length > 0) {
        this.buildDonutChart();
        this.buildPerformanceChart();
      }
    }));
    this.subs.add(this.marketService.loading$.subscribe(v => this.loading = v));
    this.subs.add(this.marketService.error$.subscribe(e => this.error = e));
    this.subs.add(this.marketService.globalData$.subscribe(d => this.globalData = d));
    this.subs.add(this.marketService.lastUpdated$.subscribe(d => this.lastUpdated = d));
    this.subs.add(this.marketService.gold$.subscribe(g => this.gold = g));
    this.subs.add(this.marketService.goldLoading$.subscribe(v => this.goldLoading = v));
    this.subs.add(this.marketService.goldError$.subscribe(e => this.goldError = e));
    this.subs.add(this.marketService.vnStocks$.subscribe(s => this.vnStocks = s));
    this.subs.add(this.marketService.vnLoading$.subscribe(v => this.vnLoading = v));
    this.subs.add(this.marketService.vnError$.subscribe(e => this.vnError = e));
    this.subs.add(this.marketService.usdVndRate$.subscribe(r => {
      this.liveUsdVndRate = r;
    }));

    this.marketService.startAutoRefresh();
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    this.marketService.stopAutoRefresh();
  }

  setTab(tab: InvestmentTab): void {
    this.activeTab = tab;
    if (tab === 'gold' && !this.gold && !this.goldLoading) {
      this.marketService.fetchGoldData();
    }
    if (tab === 'vn' && this.vnStocks.length === 0 && !this.vnLoading) {
      this.marketService.fetchVNStocks();
    }
    if (tab === 'portfolio') {
      if (this.portfolioCoins.length === 0 && !this.portfolioLoading) {
        this.loadPortfolio();
      } else if (this.portfolioCoins.length > 0) {
        setTimeout(() => { this.buildDonutChart(); this.buildPerformanceChart(); }, 50);
      }
    }
  }

  setPortfolioSubTab(tab: 'crypto' | 'gold' | 'vn_stock'): void {
    this.portfolioSubTab = tab;
    // Load market data if needed for price lookups
    if (tab === 'gold' && !this.gold && !this.goldLoading) {
      this.marketService.fetchGoldData();
    }
    if (tab === 'vn_stock' && this.vnStocks.length === 0 && !this.vnLoading) {
      this.marketService.fetchVNStocks();
    }
    // Reset detail views when switching sub-tabs
    this.closeGoldDetail();
    this.closeStockDetail();
  }

  // ── Portfolio ────────────────────────────────────────────────
  loadPortfolio(): void {
    if (!this.userId) return;
    this.portfolioLoading = true;

    // Load watchlist coins
    this.portfolioCoinService.getByUser(this.userId).subscribe({
      next: coins => {
        this.portfolioCoins = coins;
        this.portfolioLoading = false;
        setTimeout(() => { this.buildDonutChart(); this.buildPerformanceChart(); }, 100);
      },
      error: () => { this.portfolioLoading = false; }
    });

    // Load all lots for chart aggregation
    this.investmentLotService.getByUser(this.userId).subscribe({
      next: lots => { this.portfolioLots = lots; }
    });
  }

  // ── Add / Remove coins from watchlist ──────────────────────
  get addCoinSuggestions(): CryptoAsset[] {
    if (!this.addCoinQuery.trim()) return [];
    const q = this.addCoinQuery.toLowerCase();
    const alreadyAdded = new Set(this.portfolioCoins.map(pc => pc.coinId));
    return this.coins
      .filter(c => !alreadyAdded.has(c.id) &&
        (c.name.toLowerCase().includes(q) || c.symbol.toLowerCase().includes(q)))
      .slice(0, 8);
  }

  addCoinToPortfolio(coin: CryptoAsset): void {
    if (!this.userId) return;
    const pc: PortfolioCoin = {
      userId: this.userId,
      coinId: coin.id,
      symbol: coin.symbol.toUpperCase(),
      name: coin.name
    };
    this.portfolioCoinService.addCoin(pc).subscribe({
      next: saved => {
        this.portfolioCoins = [...this.portfolioCoins, saved];
        this.addCoinQuery = '';
        this.showAddCoinDropdown = false;
        setTimeout(() => { this.buildDonutChart(); this.buildPerformanceChart(); }, 50);
      }
    });
  }

  removeCoinFromPortfolio(pc: PortfolioCoin): void {
    if (!this.userId || !pc.coinId) return;
    this.portfolioCoinService.removeCoin(this.userId, pc.coinId).subscribe({
      next: () => {
        this.portfolioCoins = this.portfolioCoins.filter(c => c.coinId !== pc.coinId);
        // Also delete all lots for this coin (lots store coinId as symbol)
        this.investmentLotService.deleteBySymbol(this.userId, pc.coinId).subscribe({
          next: () => {
            this.portfolioLots = this.portfolioLots.filter(l => l.symbol !== pc.coinId);
            setTimeout(() => { this.buildDonutChart(); this.buildPerformanceChart(); }, 50);
          }
        });
        this.openMenuCoinId = null;
      }
    });
  }

  toggleMenu(coinId: string, event: Event): void {
    event.stopPropagation();
    if (this.openMenuCoinId === coinId) {
      this.openMenuCoinId = null;
      this.dropdownPos = null;
    } else {
      const btn = event.currentTarget as HTMLElement;
      const rect = btn.getBoundingClientRect();
      this.dropdownPos = {
        top: rect.bottom + 4,
        right: window.innerWidth - rect.right
      };
      this.openMenuCoinId = coinId;
    }
  }

  closeAllMenus(): void {
    this.openMenuCoinId = null;
    this.dropdownPos = null;
    this.openMenuGoldSymbol = null;
    this.goldDropdownPos = null;
    this.openMenuStockTicker = null;
    this.stockDropdownPos = null;
  }

  // ── Coin Detail ─────────────────────────────────────────────
  openCoinDetail(pc: PortfolioCoin): void {
    this.selectedCoin = pc;
    this.coinDetailPage = 0;
    this.openMenuCoinId = null;
    this.loadCoinDetail();
  }

  closeCoinDetail(): void {
    this.selectedCoin = null;
    this.coinDetailLots = [];
  }

  loadCoinDetail(): void {
    if (!this.selectedCoin || !this.userId) return;
    this.coinDetailLoading = true;
    // lots store symbol = coinId (e.g. "bitcoin"), not the ticker symbol ("BTC")
    this.investmentLotService.getPaged(
      this.userId, this.selectedCoin.coinId, '', '',
      this.coinDetailPage, this.coinDetailPageSize
    ).subscribe({
      next: res => {
        this.coinDetailLots = res.content;
        this.coinDetailTotalElements = res.totalElements;
        this.coinDetailTotalPages = res.totalPages;
        this.coinDetailLoading = false;
      },
      error: () => { this.coinDetailLoading = false; }
    });
  }

  coinDetailGoPage(page: number): void {
    this.coinDetailPage = Math.max(0, Math.min(page, this.coinDetailTotalPages - 1));
    this.loadCoinDetail();
  }

  // ── Coin detail stat getters ────────────────────────────────
  get selectedCoinHolding(): CoinHolding | null {
    if (!this.selectedCoin) return null;
    return this.cryptoHoldings.find(h => h.coinId === this.selectedCoin!.coinId) || null;
  }

  getLotPnl(lot: InvestmentLot): number | null {
    const coin = this.coins.find(c => c.id === lot.symbol);
    if (!coin) return null;
    const currentVnd = coin.current_price * this.liveUsdVndRate;
    if (lot.transactionType === 'SELL') {
      return (lot.buyPriceVnd - (this.selectedCoinHolding?.avgBuyPriceVnd || lot.buyPriceVnd)) * lot.quantity;
    }
    return (currentVnd - lot.buyPriceVnd) * lot.quantity;
  }

  // ── CoinHolding aggregation (iterates portfolioCoins) ──────
  get cryptoHoldings(): CoinHolding[] {
    const rawHoldings: Omit<CoinHolding, 'portfolioPct'>[] = [];

    for (const pc of this.portfolioCoins) {
      const coin = this.coins.find(c => c.id === pc.coinId);
      const lots = this.portfolioLots.filter(l => l.symbol === pc.coinId && l.assetType === 'CRYPTO');

      let totalBuyQty = 0;
      let totalBuyCostVnd = 0;
      let totalSellQty = 0;
      let lastTxType: 'BUY' | 'SELL' = 'BUY';
      let lastBuyDate = '';

      for (const lot of lots) {
        if (lot.transactionType === 'SELL') {
          totalSellQty += lot.quantity;
          lastTxType = 'SELL';
        } else {
          totalBuyQty += lot.quantity;
          totalBuyCostVnd += lot.buyPriceVnd * lot.quantity;
          lastTxType = 'BUY';
          if (!lastBuyDate || lot.buyDate > lastBuyDate) lastBuyDate = lot.buyDate;
        }
      }

      const netQty = totalBuyQty - totalSellQty;
      const avgBuyPriceVnd = totalBuyQty > 0 ? totalBuyCostVnd / totalBuyQty : 0;
      const currentPriceUsd = coin ? coin.current_price : null;
      const currentPriceVnd = currentPriceUsd !== null ? currentPriceUsd * this.liveUsdVndRate : null;
      const currentValueVnd = currentPriceVnd !== null && netQty > 0 ? currentPriceVnd * netQty : null;
      const pnlVnd = currentPriceVnd !== null && netQty > 0 && avgBuyPriceVnd > 0
        ? (currentPriceVnd - avgBuyPriceVnd) * netQty : null;
      const pnlPct = avgBuyPriceVnd > 0 && netQty > 0 && pnlVnd !== null
        ? (pnlVnd / (avgBuyPriceVnd * netQty)) * 100 : null;

      rawHoldings.push({
        coinId: pc.coinId,
        symbol: coin ? coin.symbol.toUpperCase() : pc.symbol,
        name: coin ? coin.name : pc.name,
        image: coin ? coin.image : '',
        netQty,
        avgBuyPriceVnd,
        totalBuyCostVnd,
        currentPriceUsd,
        currentPriceVnd,
        currentValueVnd,
        pnlVnd,
        pnlPct,
        change1h: coin ? (coin.price_change_percentage_1h_in_currency || 0) : 0,
        change24h: coin ? (coin.price_change_percentage_24h || 0) : 0,
        change7d: coin ? (coin.price_change_percentage_7d_in_currency || 0) : 0,
        volume24h: coin ? coin.total_volume : 0,
        marketCap: coin ? coin.market_cap : 0,
        sparkline: coin?.sparkline_in_7d?.price || [],
        lastTxType,
        lastBuyDate,
        portfolioCoin: pc
      });
    }

    const totalVnd = rawHoldings.reduce((s, h) => s + (h.currentValueVnd ?? 0), 0);
    return rawHoldings.map(h => ({
      ...h,
      portfolioPct: totalVnd > 0 && h.currentValueVnd !== null
        ? (h.currentValueVnd / totalVnd) * 100 : 0
    } as CoinHolding));
  }

  // ── Portfolio stat getters ──────────────────────────────────
  get portfolioTotalValueVnd(): number {
    return this.cryptoHoldings.reduce((s, h) => s + (h.currentValueVnd ?? 0), 0);
  }

  get portfolio24hChangeVnd(): number {
    return this.cryptoHoldings.reduce((s, h) => {
      if (h.currentPriceVnd === null || h.netQty <= 0) return s;
      const priceYesterdayVnd = h.currentPriceVnd / (1 + h.change24h / 100);
      return s + (h.currentPriceVnd - priceYesterdayVnd) * h.netQty;
    }, 0);
  }

  get portfolio24hChangePct(): number {
    const total = this.portfolioTotalValueVnd;
    if (total === 0) return 0;
    const yesterday = total - this.portfolio24hChangeVnd;
    if (yesterday === 0) return 0;
    return (this.portfolio24hChangeVnd / yesterday) * 100;
  }

  get portfolioTotalPnlVnd(): number {
    return this.cryptoHoldings.reduce((s, h) => s + (h.pnlVnd ?? 0), 0);
  }

  get portfolioTotalCostVnd(): number {
    return this.cryptoHoldings.reduce((s, h) => s + h.totalBuyCostVnd, 0);
  }

  get portfolioTotalPnlPct(): number {
    const cost = this.portfolioTotalCostVnd;
    if (cost === 0) return 0;
    return (this.portfolioTotalPnlVnd / cost) * 100;
  }

  get topPerformer(): CoinHolding | null {
    const holdings = this.cryptoHoldings.filter(h => h.pnlPct !== null);
    if (holdings.length === 0) return null;
    return holdings.reduce((best, h) => (h.pnlPct! > best.pnlPct! ? h : best));
  }

  // ── Currency helpers ─────────────────────────────────────────
  fmtCurrency(vnd: number): string {
    if (this.displayCurrency === 'USD') {
      return '$' + this.marketService.formatPrice(vnd / this.liveUsdVndRate);
    }
    return this.marketService.formatVND(vnd) + ' ₫';
  }

  fmtPrice(vnd: number): string {
    if (this.displayCurrency === 'USD') {
      return '$' + this.marketService.formatPrice(vnd / this.liveUsdVndRate);
    }
    return (vnd).toLocaleString('vi-VN') + ' ₫';
  }

  toggleCurrency(): void {
    this.displayCurrency = this.displayCurrency === 'USD' ? 'VND' : 'USD';
    setTimeout(() => { this.buildDonutChart(); this.buildPerformanceChart(); }, 50);
  }

  // ── Modal ───────────────────────────────────────────────────
  openTxModal(portfolioCoin?: PortfolioCoin, tab: 'BUY' | 'SELL' = 'BUY'): void {
    this.txTab = tab;
    this.txEditingLot = null;
    this.txLockedCoin = portfolioCoin || null;
    this.showTxFeesNotes = false;
    const coinId = portfolioCoin ? portfolioCoin.coinId : '';
    this.txForm = {
      assetType: 'CRYPTO',
      coinId,
      assetSymbol: '',
      assetName: '',
      totalSpent: null,
      quantity: null,
      pricePerUnit: null,
      transactionDate: new Date(),
      fees: null,
      note: ''
    };
    if (coinId) {
      const coin = this.coins.find(c => c.id === coinId);
      if (coin) {
        this.txForm.pricePerUnit = this.displayCurrency === 'USD'
          ? coin.current_price
          : coin.current_price * this.liveUsdVndRate;
      }
    }
    this.openMenuCoinId = null;
    this.showTxModal = true;
  }

  closeTxModal(): void {
    this.showTxModal = false;
    this.txEditingLot = null;
    this.txLockedCoin = null;
  }

  onTxCoinChange(coinId: string): void {
    if (!coinId) return;
    const coin = this.coins.find(c => c.id === coinId);
    if (coin) {
      this.txForm.pricePerUnit = this.displayCurrency === 'USD'
        ? coin.current_price
        : coin.current_price * this.liveUsdVndRate;
      if (this.txForm.quantity) {
        this.txForm.totalSpent = this.txForm.pricePerUnit * this.txForm.quantity;
      }
    }
  }

  onTxTotalChange(val: number | null): void {
    this.txForm.totalSpent = val;
    if (val && this.txForm.quantity) {
      this.txForm.pricePerUnit = val / this.txForm.quantity;
    } else if (val && this.txForm.pricePerUnit) {
      this.txForm.quantity = val / this.txForm.pricePerUnit;
    }
  }

  onTxQtyChange(val: number | null): void {
    this.txForm.quantity = val;
    if (val && this.txForm.pricePerUnit) {
      this.txForm.totalSpent = val * this.txForm.pricePerUnit;
    } else if (val && this.txForm.totalSpent) {
      this.txForm.pricePerUnit = this.txForm.totalSpent / val;
    }
  }

  onTxPriceChange(val: number | null): void {
    this.txForm.pricePerUnit = val;
    if (val && this.txForm.quantity) {
      this.txForm.totalSpent = this.txForm.quantity * val;
    }
  }

  useMarketPrice(): void {
    const coin = this.coins.find(c => c.id === this.txForm.coinId);
    if (!coin) return;
    const price = this.displayCurrency === 'USD'
      ? coin.current_price
      : coin.current_price * this.liveUsdVndRate;
    this.onTxPriceChange(price);
  }

  useMaxQty(): void {
    const holding = this.cryptoHoldings.find(h => h.coinId === this.txForm.coinId);
    if (holding) {
      this.onTxQtyChange(holding.netQty);
    }
  }

  getSelectedCoinSymbol(): string {
    if (!this.txForm.coinId) return '';
    const opt = this.cryptoSymbolOptions.find(o => o.id === this.txForm.coinId);
    if (!opt) return '';
    const match = opt.name.match(/\(([^)]+)\)/);
    return match ? match[1] : '';
  }

  saveTx(): void {
    const qty = this.txForm.quantity;

    // Determine symbol based on asset type
    const symbol = this.txForm.assetType === 'CRYPTO' ? this.txForm.coinId : this.txForm.assetSymbol;
    if (!symbol || !qty || qty <= 0) return;

    let price = this.txForm.pricePerUnit;
    if (!price && this.txForm.totalSpent && qty) {
      price = this.txForm.totalSpent / qty;
    }
    if (!price || price <= 0) return;

    this.txSaving = true;

    // For CRYPTO, price might be in USD; for GOLD/VN_STOCK always VND
    const pricePerUnitVnd = (this.txForm.assetType === 'CRYPTO' && this.displayCurrency === 'USD')
      ? price * this.liveUsdVndRate
      : price;

    const feesVnd = this.txForm.fees
      ? ((this.txForm.assetType === 'CRYPTO' && this.displayCurrency === 'USD')
          ? this.txForm.fees * this.liveUsdVndRate
          : this.txForm.fees)
      : undefined;

    let name = '';
    if (this.txForm.assetType === 'CRYPTO') {
      const coin = this.coins.find(c => c.id === symbol);
      name = coin ? coin.name : symbol;
    } else {
      name = this.txForm.assetName || symbol;
    }

    const buyDateStr = this.txForm.transactionDate
      ? this.txForm.transactionDate.toISOString().slice(0, 10)
      : new Date().toISOString().slice(0, 10);

    const payload: InvestmentLot = {
      userId: this.userId,
      assetType: this.txForm.assetType,
      symbol,
      name,
      buyDate: buyDateStr,
      quantity: qty,
      buyPriceVnd: pricePerUnitVnd,
      transactionType: this.txTab,
      fees: feesVnd,
      note: this.txForm.note || undefined
    };

    this.investmentLotService.create(payload).subscribe({
      next: created => {
        this.portfolioLots.unshift(created);
        if (this.txForm.assetType === 'CRYPTO') {
          if (this.selectedCoin && this.selectedCoin.coinId === symbol) {
            this.loadCoinDetail();
          }
          setTimeout(() => { this.buildDonutChart(); this.buildPerformanceChart(); }, 50);
        } else if (this.txForm.assetType === 'GOLD') {
          if (this.goldDetailSymbol === symbol) this.loadGoldDetail();
        } else if (this.txForm.assetType === 'VN_STOCK') {
          if (this.stockDetailTicker === symbol) this.loadStockDetail();
        }
        this.closeTxModal();
        this.txSaving = false;
      },
      error: () => { this.txSaving = false; }
    });
  }

  deleteLot(id: string): void {
    this.investmentLotService.delete(id).subscribe({
      next: () => {
        const deleted = this.portfolioLots.find(l => l.id === id);
        this.portfolioLots = this.portfolioLots.filter(l => l.id !== id);
        this.coinDetailLots = this.coinDetailLots.filter(l => l.id !== id);
        this.goldDetailLots = this.goldDetailLots.filter(l => l.id !== id);
        this.stockDetailLots = this.stockDetailLots.filter(l => l.id !== id);
        this.coinDetailTotalElements = Math.max(0, this.coinDetailTotalElements - 1);
        if (deleted?.assetType === 'GOLD') this.goldDetailTotalElements = Math.max(0, this.goldDetailTotalElements - 1);
        if (deleted?.assetType === 'VN_STOCK') this.stockDetailTotalElements = Math.max(0, this.stockDetailTotalElements - 1);
        setTimeout(() => { this.buildDonutChart(); this.buildPerformanceChart(); }, 50);
      }
    });
  }

  // ── Charts ──────────────────────────────────────────────────
  buildDonutChart(): void {
    const holdings = this.cryptoHoldings.filter(h => h.netQty > 0 && h.currentValueVnd !== null);
    if (holdings.length === 0) { this.donutOptions = {}; return; }

    const series = holdings.map(h => parseFloat((h.portfolioPct).toFixed(2)));
    const labels = holdings.map(h => h.name);
    const colors = ['#4ECDC4', '#FF6B6B', '#FFD93D', '#6BCB77', '#845EC2',
                    '#F9A825', '#00B4D8', '#E76F51', '#2A9D8F', '#264653'];

    this.donutOptions = {
      series,
      chart: { type: 'donut', height: 260, background: 'transparent' },
      labels,
      colors: colors.slice(0, holdings.length),
      legend: { show: true, position: 'bottom', fontSize: '12px',
        labels: { colors: '#8a9aa9' } },
      dataLabels: { enabled: holdings.length <= 5 },
      plotOptions: { pie: { donut: { size: '65%' } } },
      theme: { mode: 'dark' },
      tooltip: {
        y: { formatter: (val: number) => val.toFixed(1) + '%' }
      }
    };
  }

  buildPerformanceChart(): void {
    const holdings = this.cryptoHoldings.filter(h => h.sparkline.length > 0 && h.netQty > 0);
    if (holdings.length === 0) { this.performanceOptions = {}; return; }

    const len = Math.min(...holdings.map(h => h.sparkline.length));
    const portfolioValues: number[] = [];
    for (let i = 0; i < len; i++) {
      const totalUsd = holdings.reduce((s, h) => s + h.sparkline[i] * h.netQty, 0);
      const totalVnd = totalUsd * this.liveUsdVndRate;
      portfolioValues.push(this.displayCurrency === 'USD' ? totalVnd / this.liveUsdVndRate : totalVnd);
    }

    const isPositive = portfolioValues[portfolioValues.length - 1] >= portfolioValues[0];
    const color = isPositive ? '#4ECDC4' : '#FF6B6B';

    this.performanceOptions = {
      series: [{ name: this.displayCurrency === 'USD' ? 'Portfolio (USD)' : 'Portfolio (VNĐ)',
        data: portfolioValues }],
      chart: { type: 'area', height: 200, background: 'transparent',
        toolbar: { show: false }, animations: { enabled: false } },
      dataLabels: { enabled: false },
      stroke: { curve: 'smooth', width: 2, colors: [color] },
      fill: {
        type: 'gradient',
        gradient: { shadeIntensity: 1, opacityFrom: 0.35, opacityTo: 0.02, stops: [0, 100] }
      },
      colors: [color],
      xaxis: { labels: { show: false }, axisBorder: { show: false }, axisTicks: { show: false },
        tooltip: { enabled: false } },
      yaxis: {
        tickAmount: 4,
        labels: {
          style: { colors: '#8a9aa9', fontSize: '11px' },
          formatter: (val: number) => this.displayCurrency === 'USD'
            ? '$' + this.marketService.formatPrice(val)
            : this.marketService.formatVND(val)
        }
      },
      grid: { borderColor: 'rgba(255,255,255,0.05)', strokeDashArray: 3,
        padding: { left: 10, right: 10 } },
      theme: { mode: 'dark' },
      tooltip: {
        theme: 'dark',
        x: { show: false },
        y: {
          formatter: (val: number) => this.displayCurrency === 'USD'
            ? '$' + this.marketService.formatPrice(val)
            : this.marketService.formatVND(val) + ' ₫'
        }
      }
    };
  }

  // ── Symbol options ───────────────────────────────────────────
  get cryptoSymbolOptions(): { id: string; name: string; image: string }[] {
    return this.coins.map(c => ({
      id: c.id,
      name: `${c.name} (${c.symbol.toUpperCase()})`,
      image: c.image
    }));
  }

  trackByOptId(_: number, opt: { id: string }): string { return opt.id; }

  // ── Crypto filters ──────────────────────────────────────────
  onSearch(query: string): void {
    this.searchQuery = query;
    this.applyFilters();
  }

  applyFilters(): void {
    let result = [...this.coins];
    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase();
      result = result.filter(c =>
        c.name.toLowerCase().includes(q) || c.symbol.toLowerCase().includes(q)
      );
    }
    this.sortCoins(result);
  }

  private sortCoins(coins: CryptoAsset[]): void {
    coins.sort((a, b) => {
      let av: number, bv: number;
      switch (this.sortField) {
        case 'price':      av = a.current_price; bv = b.current_price; break;
        case 'change24h':  av = a.price_change_percentage_24h; bv = b.price_change_percentage_24h; break;
        case 'marketCap':  av = a.market_cap; bv = b.market_cap; break;
        case 'volume':     av = a.total_volume; bv = b.total_volume; break;
        default:           av = a.market_cap_rank; bv = b.market_cap_rank; break;
      }
      return this.sortDir === 'asc' ? av - bv : bv - av;
    });
    this.filteredCoins = coins;
  }

  setSort(field: SortField): void {
    if (this.sortField === field) {
      this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortField = field;
      this.sortDir = field === 'rank' ? 'asc' : 'desc';
    }
    this.applyFilters();
  }

  onRefresh(): void {
    this.isRefreshing = true;
    this.marketService.manualRefresh();
    if (this.activeTab === 'gold')      this.marketService.fetchGoldData();
    if (this.activeTab === 'vn')        this.marketService.fetchVNStocks();
    if (this.activeTab === 'portfolio') this.loadPortfolio();
    setTimeout(() => this.isRefreshing = false, 1500);
  }

  // ── Crypto helpers ──────────────────────────────────────────
  getTopGainers(count = 4): CryptoAsset[] {
    return [...this.coins]
      .filter(c => c.price_change_percentage_24h != null)
      .sort((a, b) => b.price_change_percentage_24h - a.price_change_percentage_24h)
      .slice(0, count);
  }

  getTopLosers(count = 4): CryptoAsset[] {
    return [...this.coins]
      .filter(c => c.price_change_percentage_24h != null)
      .sort((a, b) => a.price_change_percentage_24h - b.price_change_percentage_24h)
      .slice(0, count);
  }

  // ── VN Stock helpers ────────────────────────────────────────
  getVNTopGainers(count = 4): VNStockData[] {
    return [...this.vnStocks].sort((a, b) => b.pctChange - a.pctChange).slice(0, count);
  }

  getVNTopLosers(count = 4): VNStockData[] {
    return [...this.vnStocks].sort((a, b) => a.pctChange - b.pctChange).slice(0, count);
  }

  getVNAvgChange(): number {
    if (!this.vnStocks.length) return 0;
    return this.vnStocks.reduce((s, v) => s + v.pctChange, 0) / this.vnStocks.length;
  }

  // ── Old portfolio helpers ────────────────────────────────────
  getCurrentPriceVnd(lot: InvestmentLot): number | null {
    if (lot.assetType === 'CRYPTO') {
      const coin = this.coins.find(c => c.id === lot.symbol);
      return coin ? Math.round(coin.current_price * this.liveUsdVndRate) : null;
    }
    if (lot.assetType === 'GOLD') {
      const g = this.gold?.vnGoldPrices?.find(g => g.code === lot.symbol);
      return g ? g.sell : null;
    }
    if (lot.assetType === 'VN_STOCK') {
      const s = this.vnStocks.find(s => s.ticker === lot.symbol);
      return s ? s.price : null;
    }
    return null;
  }

  // ── Formatters ──────────────────────────────────────────────
  formatPrice(price: number): string {
    return this.marketService.formatPrice(price);
  }

  formatLarge(value: number): string {
    return this.marketService.formatLargeNumber(value);
  }

  formatVND(value: number): string {
    return this.marketService.formatVND(value);
  }

  abs(v: number): number {
    return Math.abs(v);
  }

  getSparklinePoints(prices: number[]): string {
    if (!prices || prices.length === 0) return '';
    const w = 100, h = 40;
    const min = Math.min(...prices);
    const max = Math.max(...prices);
    const range = max - min || 1;
    const step = w / (prices.length - 1);
    return prices.map((p, i) => {
      const x = i * step;
      const y = h - ((p - min) / range) * h;
      return `${x},${y}`;
    }).join(' ');
  }

  getSparklineColor(coin: CryptoAsset): string {
    return coin.price_change_percentage_24h >= 0 ? '#00C17C' : '#FF6B6B';
  }

  getCoinImage(symbolOrId: string): string {
    const coin = this.coins.find(c => c.id === symbolOrId || c.symbol.toLowerCase() === symbolOrId.toLowerCase());
    return coin?.image || '';
  }

  formatTime(date: Date | null): string {
    if (!date) return '';
    return date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }

  getHoldingNetQty(coinId: string): number {
    const h = this.cryptoHoldings.find(h => h.coinId === coinId);
    return h ? h.netQty : 0;
  }

  trackById(_: number, coin: CryptoAsset): string { return coin.id; }
  trackBySymbol(_: number, item: VNStockData): string { return item.ticker; }
  trackByCoinId(_: number, h: CoinHolding): string { return h.coinId; }
  trackByLotId(_: number, lot: InvestmentLot): string { return lot.id || lot.symbol; }
  trackByPcId(_: number, pc: PortfolioCoin): string { return pc.id || pc.coinId; }
  trackByGoldSymbol(_: number, h: GoldHolding): string { return h.symbol; }
  trackByStockTicker(_: number, h: StockHolding): string { return h.ticker; }

  // ── Gold Holdings aggregation ────────────────────────────────
  get goldHoldings(): GoldHolding[] {
    const goldLots = this.portfolioLots.filter(l => l.assetType === 'GOLD');
    const map = new Map<string, InvestmentLot[]>();
    for (const lot of goldLots) {
      if (!map.has(lot.symbol)) map.set(lot.symbol, []);
      map.get(lot.symbol)!.push(lot);
    }
    const result: GoldHolding[] = [];
    map.forEach((lots, symbol) => {
      let totalBuyQty = 0, totalBuyCostVnd = 0, totalSellQty = 0;
      const name = lots[0].name;
      for (const lot of lots) {
        if (lot.transactionType === 'SELL') totalSellQty += lot.quantity;
        else { totalBuyQty += lot.quantity; totalBuyCostVnd += lot.buyPriceVnd * lot.quantity; }
      }
      const netQty = totalBuyQty - totalSellQty;
      const avgBuyPriceVnd = totalBuyQty > 0 ? totalBuyCostVnd / totalBuyQty : 0;
      const goldProvider = this.gold?.vnGoldPrices?.find(g => g.code === symbol);
      const currentSellPriceVnd = goldProvider ? goldProvider.sell : null;
      const currentValueVnd = currentSellPriceVnd !== null && netQty > 0 ? currentSellPriceVnd * netQty : null;
      const pnlVnd = currentSellPriceVnd !== null && netQty > 0 && avgBuyPriceVnd > 0
        ? (currentSellPriceVnd - avgBuyPriceVnd) * netQty : null;
      const pnlPct = pnlVnd !== null && avgBuyPriceVnd > 0 && netQty > 0
        ? (pnlVnd / (avgBuyPriceVnd * netQty)) * 100 : null;
      result.push({ symbol, name, netQty, avgBuyPriceVnd, totalBuyCostVnd, currentSellPriceVnd, currentValueVnd, pnlVnd, pnlPct });
    });
    return result;
  }

  // ── Stock Holdings aggregation ───────────────────────────────
  get stockHoldings(): StockHolding[] {
    const stockLots = this.portfolioLots.filter(l => l.assetType === 'VN_STOCK');
    const map = new Map<string, InvestmentLot[]>();
    for (const lot of stockLots) {
      if (!map.has(lot.symbol)) map.set(lot.symbol, []);
      map.get(lot.symbol)!.push(lot);
    }
    const result: StockHolding[] = [];
    map.forEach((lots, ticker) => {
      let totalBuyQty = 0, totalBuyCostVnd = 0, totalSellQty = 0;
      const name = lots[0].name;
      for (const lot of lots) {
        if (lot.transactionType === 'SELL') totalSellQty += lot.quantity;
        else { totalBuyQty += lot.quantity; totalBuyCostVnd += lot.buyPriceVnd * lot.quantity; }
      }
      const netQty = totalBuyQty - totalSellQty;
      const avgBuyPriceVnd = totalBuyQty > 0 ? totalBuyCostVnd / totalBuyQty : 0;
      const stockData = this.vnStocks.find(s => s.ticker === ticker);
      const currentPriceVnd = stockData ? stockData.price : null;
      const currentValueVnd = currentPriceVnd !== null && netQty > 0 ? currentPriceVnd * netQty : null;
      const pnlVnd = currentPriceVnd !== null && netQty > 0 && avgBuyPriceVnd > 0
        ? (currentPriceVnd - avgBuyPriceVnd) * netQty : null;
      const pnlPct = pnlVnd !== null && avgBuyPriceVnd > 0 && netQty > 0
        ? (pnlVnd / (avgBuyPriceVnd * netQty)) * 100 : null;
      result.push({ ticker, name, netQty, avgBuyPriceVnd, totalBuyCostVnd, currentPriceVnd, currentValueVnd, pnlVnd, pnlPct });
    });
    return result;
  }

  // ── Gold detail helpers ──────────────────────────────────────
  get goldDetailHolding(): GoldHolding | null {
    if (!this.goldDetailSymbol) return null;
    return this.goldHoldings.find(h => h.symbol === this.goldDetailSymbol) || null;
  }

  openGoldDetail(symbol: string): void {
    this.goldDetailSymbol = symbol;
    this.goldDetailPage = 0;
    this.openMenuGoldSymbol = null;
    this.loadGoldDetail();
  }

  closeGoldDetail(): void { this.goldDetailSymbol = null; this.goldDetailLots = []; }

  loadGoldDetail(): void {
    if (!this.goldDetailSymbol || !this.userId) return;
    this.goldDetailLoading = true;
    this.investmentLotService.getPaged(this.userId, this.goldDetailSymbol, '', '', this.goldDetailPage, this.goldDetailPageSize).subscribe({
      next: res => { this.goldDetailLots = res.content; this.goldDetailTotalElements = res.totalElements; this.goldDetailTotalPages = res.totalPages; this.goldDetailLoading = false; },
      error: () => { this.goldDetailLoading = false; }
    });
  }

  goldDetailGoPage(page: number): void {
    this.goldDetailPage = Math.max(0, Math.min(page, this.goldDetailTotalPages - 1));
    this.loadGoldDetail();
  }

  removeGoldHolding(symbol: string): void {
    this.investmentLotService.deleteBySymbol(this.userId, symbol).subscribe({
      next: () => {
        this.portfolioLots = this.portfolioLots.filter(l => !(l.symbol === symbol && l.assetType === 'GOLD'));
        if (this.goldDetailSymbol === symbol) this.closeGoldDetail();
        this.openMenuGoldSymbol = null;
      }
    });
  }

  toggleGoldMenu(symbol: string, event: Event): void {
    event.stopPropagation();
    if (this.openMenuGoldSymbol === symbol) {
      this.openMenuGoldSymbol = null;
      this.goldDropdownPos = null;
    } else {
      const btn = event.currentTarget as HTMLElement;
      const rect = btn.getBoundingClientRect();
      this.goldDropdownPos = { top: rect.bottom + 4, right: window.innerWidth - rect.right };
      this.openMenuGoldSymbol = symbol;
    }
  }

  openGoldTxModal(holding?: GoldHolding, tab: 'BUY' | 'SELL' = 'BUY'): void {
    this.txTab = tab;
    this.txEditingLot = null;
    this.txLockedCoin = null;
    this.showTxFeesNotes = false;
    this.txForm = {
      assetType: 'GOLD',
      coinId: '',
      assetSymbol: holding?.symbol || '',
      assetName: holding?.name || '',
      totalSpent: null,
      quantity: null,
      pricePerUnit: holding?.currentSellPriceVnd || null,
      transactionDate: new Date(),
      fees: null,
      note: ''
    };
    if (tab === 'SELL' && holding) {
      this.txForm.quantity = holding.netQty;
    }
    this.openMenuGoldSymbol = null;
    this.showTxModal = true;
  }

  onGoldSymbolChange(code: string): void {
    const provider = this.gold?.vnGoldPrices?.find(g => g.code === code);
    if (provider) {
      this.txForm.assetName = provider.name;
      this.txForm.pricePerUnit = provider.sell;
      if (this.txForm.quantity) this.txForm.totalSpent = provider.sell * this.txForm.quantity;
    }
  }

  get goldProviderOptions(): { code: string; name: string }[] {
    if (!this.gold?.vnGoldPrices) return [];
    return this.gold.vnGoldPrices.map(g => ({ code: g.code, name: g.name }));
  }

  getGoldLotPnl(lot: InvestmentLot): number | null {
    const holding = this.goldHoldings.find(h => h.symbol === lot.symbol);
    if (!holding || holding.currentSellPriceVnd === null) return null;
    if (lot.transactionType === 'SELL') return (lot.buyPriceVnd - (holding.avgBuyPriceVnd || lot.buyPriceVnd)) * lot.quantity;
    return (holding.currentSellPriceVnd - lot.buyPriceVnd) * lot.quantity;
  }

  // ── Stock detail helpers ─────────────────────────────────────
  get stockDetailHolding(): StockHolding | null {
    if (!this.stockDetailTicker) return null;
    return this.stockHoldings.find(h => h.ticker === this.stockDetailTicker) || null;
  }

  openStockDetail(ticker: string): void {
    this.stockDetailTicker = ticker;
    this.stockDetailPage = 0;
    this.openMenuStockTicker = null;
    this.loadStockDetail();
  }

  closeStockDetail(): void { this.stockDetailTicker = null; this.stockDetailLots = []; }

  loadStockDetail(): void {
    if (!this.stockDetailTicker || !this.userId) return;
    this.stockDetailLoading = true;
    this.investmentLotService.getPaged(this.userId, this.stockDetailTicker, '', '', this.stockDetailPage, this.stockDetailPageSize).subscribe({
      next: res => { this.stockDetailLots = res.content; this.stockDetailTotalElements = res.totalElements; this.stockDetailTotalPages = res.totalPages; this.stockDetailLoading = false; },
      error: () => { this.stockDetailLoading = false; }
    });
  }

  stockDetailGoPage(page: number): void {
    this.stockDetailPage = Math.max(0, Math.min(page, this.stockDetailTotalPages - 1));
    this.loadStockDetail();
  }

  removeStockHolding(ticker: string): void {
    this.investmentLotService.deleteBySymbol(this.userId, ticker).subscribe({
      next: () => {
        this.portfolioLots = this.portfolioLots.filter(l => !(l.symbol === ticker && l.assetType === 'VN_STOCK'));
        if (this.stockDetailTicker === ticker) this.closeStockDetail();
        this.openMenuStockTicker = null;
      }
    });
  }

  toggleStockMenu(ticker: string, event: Event): void {
    event.stopPropagation();
    if (this.openMenuStockTicker === ticker) {
      this.openMenuStockTicker = null;
      this.stockDropdownPos = null;
    } else {
      const btn = event.currentTarget as HTMLElement;
      const rect = btn.getBoundingClientRect();
      this.stockDropdownPos = { top: rect.bottom + 4, right: window.innerWidth - rect.right };
      this.openMenuStockTicker = ticker;
    }
  }

  openStockTxModal(holding?: StockHolding, tab: 'BUY' | 'SELL' = 'BUY'): void {
    this.txTab = tab;
    this.txEditingLot = null;
    this.txLockedCoin = null;
    this.showTxFeesNotes = false;
    this.txForm = {
      assetType: 'VN_STOCK',
      coinId: '',
      assetSymbol: holding?.ticker || '',
      assetName: holding?.name || '',
      totalSpent: null,
      quantity: null,
      pricePerUnit: holding?.currentPriceVnd || null,
      transactionDate: new Date(),
      fees: null,
      note: ''
    };
    if (tab === 'SELL' && holding) {
      this.txForm.quantity = holding.netQty;
    }
    this.openMenuStockTicker = null;
    this.showTxModal = true;
  }

  onStockSymbolChange(ticker: string): void {
    const stock = this.vnStocks.find(s => s.ticker === ticker);
    if (stock) {
      this.txForm.assetName = stock.companyName;
      this.txForm.pricePerUnit = stock.price;
      if (this.txForm.quantity) this.txForm.totalSpent = stock.price * this.txForm.quantity;
    }
  }

  get vnStockOptions(): { ticker: string; name: string }[] {
    return this.vnStocks.map(s => ({ ticker: s.ticker, name: s.companyName }));
  }

  getStockLotPnl(lot: InvestmentLot): number | null {
    const holding = this.stockHoldings.find(h => h.ticker === lot.symbol);
    if (!holding || holding.currentPriceVnd === null) return null;
    if (lot.transactionType === 'SELL') return (lot.buyPriceVnd - (holding.avgBuyPriceVnd || lot.buyPriceVnd)) * lot.quantity;
    return (holding.currentPriceVnd - lot.buyPriceVnd) * lot.quantity;
  }
}
