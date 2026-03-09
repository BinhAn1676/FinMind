import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { MarketDataService, CryptoAsset, MarketGlobalData, GoldData, VNStockData } from '../../services/market-data.service';
import { LanguageService } from '../../services/language.service';

type InvestmentTab = 'crypto' | 'gold' | 'vn';
type SortField = 'rank' | 'price' | 'change24h' | 'marketCap' | 'volume';
type SortDir = 'asc' | 'desc';

@Component({
  selector: 'app-investment',
  templateUrl: './investment.component.html',
  styleUrls: ['./investment.component.css']
})
export class InvestmentComponent implements OnInit, OnDestroy {

  activeTab: InvestmentTab = 'crypto';

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

  private subs = new Subscription();

  constructor(
    public marketService: MarketDataService,
    public language: LanguageService
  ) {}

  currentLang = '';

  ngOnInit(): void {
    this.subs.add(this.language.currentLanguage$.subscribe(lang => {
      this.currentLang = lang;
    }));

    // Crypto
    this.subs.add(this.marketService.crypto$.subscribe(coins => {
      this.coins = coins;
      this.applyFilters();
    }));
    this.subs.add(this.marketService.loading$.subscribe(v => this.loading = v));
    this.subs.add(this.marketService.error$.subscribe(e => this.error = e));
    this.subs.add(this.marketService.globalData$.subscribe(d => this.globalData = d));
    this.subs.add(this.marketService.lastUpdated$.subscribe(d => this.lastUpdated = d));

    // Gold
    this.subs.add(this.marketService.gold$.subscribe(g => this.gold = g));
    this.subs.add(this.marketService.goldLoading$.subscribe(v => this.goldLoading = v));
    this.subs.add(this.marketService.goldError$.subscribe(e => this.goldError = e));

    // VN Stocks
    this.subs.add(this.marketService.vnStocks$.subscribe(s => this.vnStocks = s));
    this.subs.add(this.marketService.vnLoading$.subscribe(v => this.vnLoading = v));
    this.subs.add(this.marketService.vnError$.subscribe(e => this.vnError = e));

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
  }

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
    if (this.activeTab === 'gold')   this.marketService.fetchGoldData();
    if (this.activeTab === 'vn')     this.marketService.fetchVNStocks();
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
    return [...this.vnStocks]
      .sort((a, b) => b.pctChange - a.pctChange)
      .slice(0, count);
  }

  getVNTopLosers(count = 4): VNStockData[] {
    return [...this.vnStocks]
      .sort((a, b) => a.pctChange - b.pctChange)
      .slice(0, count);
  }

  getVNAvgChange(): number {
    if (!this.vnStocks.length) return 0;
    return this.vnStocks.reduce((s, v) => s + v.pctChange, 0) / this.vnStocks.length;
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

  formatVNDPrice(price: number): string {
    // VN stock prices come as raw VND (e.g. 76200)
    return price.toLocaleString('vi-VN') + ' ₫';
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

  trackById(_: number, coin: CryptoAsset): string {
    return coin.id;
  }

  trackBySymbol(_: number, item: VNStockData): string {
    return item.ticker;
  }

  formatTime(date: Date | null): string {
    if (!date) return '';
    return date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }
}
