import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { BehaviorSubject, Subscription, interval, forkJoin, Observable } from 'rxjs';
import { startWith, catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { SKIP_LOADING } from '../interceptors/loading.interceptor';

const SILENT = { context: new HttpContext().set(SKIP_LOADING, true) };

export interface CryptoAsset {
  id: string;
  symbol: string;
  name: string;
  image: string;
  current_price: number;
  market_cap: number;
  market_cap_rank: number;
  fully_diluted_valuation: number;
  total_volume: number;
  high_24h: number;
  low_24h: number;
  price_change_24h: number;
  price_change_percentage_24h: number;
  price_change_percentage_1h_in_currency: number;
  price_change_percentage_7d_in_currency: number;
  circulating_supply: number;
  total_supply: number;
  ath: number;
  ath_change_percentage: number;
  sparkline_in_7d: { price: number[] };
}

export interface MarketGlobalData {
  total_market_cap_usd: number;
  total_volume_usd: number;
  btc_dominance: number;
  market_cap_change_percentage_24h: number;
}

export interface StockQuote {
  symbol: string;
  shortName: string;
  regularMarketPrice: number;
  regularMarketChange: number;
  regularMarketChangePercent: number;
  regularMarketDayHigh: number;
  regularMarketDayLow: number;
  regularMarketPreviousClose: number;
  regularMarketVolume: number;
  marketCap: number;
  fiftyTwoWeekHigh: number;
  fiftyTwoWeekLow: number;
  currency: string;
  exchange: string;
  logoUrl: string;
  sector: string;
}

export interface VNGoldPrice {
  code: string;
  name: string;
  buy: number;    // VND per lượng
  sell: number;   // VND per lượng
  changeBuy: number;
}

export interface GoldData {
  xauPrice: number;         // USD per troy oz (international spot)
  xagPrice: number;
  xauChangePct: number;
  xagChangePct: number;
  xauChange: number;
  xauClose: number;
  pricePerGramUsd: number;
  pricePerChiVnd: number;
  pricePerLuongVnd: number;
  usdVndRate: number;
  vnGoldPrices: VNGoldPrice[]; // All VN gold types from vang.today
  source: string;
  updatedAt: Date;
}

export interface VNStockData {
  ticker: string;
  companyName: string;
  price: number;       // VND (raw, e.g. 76200 = 76,200 VND)
  change: number;
  pctChange: number;
  volume: number;
  high: number;
  low: number;
  open: number;
  exchange: string;
}

@Injectable({
  providedIn: 'root'
})
export class MarketDataService implements OnDestroy {

  private readonly COINGECKO_BASE    = 'https://api.coingecko.com/api/v3';
  // vang.today: free, no auth, updates every 5 min (via proxy to bypass CORS)
  private readonly VANG_TODAY        = '/proxy/vang/api/prices';
  private readonly VPS_PROXY         = '/proxy/vps/getliststockdata';
  // Finnhub.io — free tier, 60 req/min, CORS-native
  // Get your free API key at: https://finnhub.io/register
  private readonly FINNHUB_BASE      = 'https://finnhub.io/api/v1';
  private readonly FINNHUB_TOKEN     = ''; // ← paste your free token here
  private readonly REFRESH_INTERVAL_MS = 60000;

  // Crypto
  private cryptoSubject    = new BehaviorSubject<CryptoAsset[]>([]);
  private loadingSubject   = new BehaviorSubject<boolean>(false);
  private errorSubject     = new BehaviorSubject<string | null>(null);
  private globalDataSubject= new BehaviorSubject<MarketGlobalData | null>(null);
  private lastUpdatedSubject = new BehaviorSubject<Date | null>(null);

  // Stocks
  private stocksSubject    = new BehaviorSubject<StockQuote[]>([]);
  private stocksLoadingSubject = new BehaviorSubject<boolean>(false);
  private stocksErrorSubject   = new BehaviorSubject<string | null>(null);

  // Gold
  private goldSubject      = new BehaviorSubject<GoldData | null>(null);
  private goldLoadingSubject = new BehaviorSubject<boolean>(false);
  private goldErrorSubject   = new BehaviorSubject<string | null>(null);

  // VN Stocks
  private vnStocksSubject  = new BehaviorSubject<VNStockData[]>([]);
  private vnLoadingSubject = new BehaviorSubject<boolean>(false);
  private vnErrorSubject   = new BehaviorSubject<string | null>(null);

  public crypto$       = this.cryptoSubject.asObservable();
  public loading$      = this.loadingSubject.asObservable();
  public error$        = this.errorSubject.asObservable();
  public globalData$   = this.globalDataSubject.asObservable();
  public lastUpdated$  = this.lastUpdatedSubject.asObservable();
  public stocks$       = this.stocksSubject.asObservable();
  public stocksLoading$= this.stocksLoadingSubject.asObservable();
  public stocksError$  = this.stocksErrorSubject.asObservable();
  public gold$         = this.goldSubject.asObservable();
  public goldLoading$  = this.goldLoadingSubject.asObservable();
  public goldError$    = this.goldErrorSubject.asObservable();
  public vnStocks$     = this.vnStocksSubject.asObservable();
  public vnLoading$    = this.vnLoadingSubject.asObservable();
  public vnError$      = this.vnErrorSubject.asObservable();

  private refreshSubscription: Subscription | null = null;
  private activeSubscribers = 0;

  // Approximate USD/VND exchange rate (update periodically)
  private readonly USD_VND_RATE = 25400;
  private readonly TROY_OZ_GRAMS = 31.1035;
  private readonly CHI_GRAMS = 3.75;
  private readonly LUONG_GRAMS = 37.5;

  // Watch list
  readonly STOCK_SYMBOLS = 'AAPL,GOOGL,MSFT,TSLA,AMZN,META,NVDA,NFLX,JPM,V,BABA,AMD,DIS,BRK-B,ORCL';
  readonly VN_TICKERS    = 'VNM,VIC,HPG,FPT,MBB,VCB,CTG,ACB,BID,TCB,STB,MSN,VRE,HDB,VHM,GVR,PDR,KDH,VHC,DGW';

  private readonly VN_COMPANIES: Record<string, { name: string; exchange: string }> = {
    VNM: { name: 'Vinamilk',      exchange: 'HoSE' },
    VIC: { name: 'Vingroup',      exchange: 'HoSE' },
    HPG: { name: 'Hòa Phát',      exchange: 'HoSE' },
    FPT: { name: 'FPT Corp',      exchange: 'HoSE' },
    MBB: { name: 'MB Bank',       exchange: 'HoSE' },
    VCB: { name: 'Vietcombank',   exchange: 'HoSE' },
    CTG: { name: 'VietinBank',    exchange: 'HoSE' },
    ACB: { name: 'ACBank',        exchange: 'HoSE' },
    BID: { name: 'BIDV',          exchange: 'HoSE' },
    TCB: { name: 'Techcombank',   exchange: 'HoSE' },
    STB: { name: 'Sacombank',     exchange: 'HoSE' },
    MSN: { name: 'Masan Group',   exchange: 'HoSE' },
    VRE: { name: 'Vincom Retail', exchange: 'HoSE' },
    HDB: { name: 'HDBank',        exchange: 'HoSE' },
    VHM: { name: 'Vinhomes',      exchange: 'HoSE' },
    GVR: { name: 'VRG',           exchange: 'HoSE' },
    PDR: { name: 'Phát Đạt',      exchange: 'HoSE' },
    KDH: { name: 'Khang Điền',    exchange: 'HoSE' },
    VHC: { name: 'Vĩnh Hoàn',     exchange: 'HoSE' },
    DGW: { name: 'Digiworld',     exchange: 'HoSE' },
  };

  private readonly STOCK_NAMES: Record<string, string> = {
    AAPL: 'Apple Inc.',     GOOGL: 'Alphabet Inc.',   MSFT: 'Microsoft Corp.',
    TSLA: 'Tesla Inc.',     AMZN: 'Amazon.com Inc.',  META: 'Meta Platforms',
    NVDA: 'NVIDIA Corp.',   NFLX: 'Netflix Inc.',     JPM:  'JPMorgan Chase',
    V:    'Visa Inc.',      BABA: 'Alibaba Group',    AMD:  'Advanced Micro Devices',
    DIS:  'Walt Disney Co.','BRK-B': 'Berkshire Hathaway', ORCL: 'Oracle Corp.',
  };

  private readonly STOCK_LOGOS: Record<string, string> = {
    AAPL:  'https://logo.clearbit.com/apple.com',
    GOOGL: 'https://logo.clearbit.com/google.com',
    MSFT:  'https://logo.clearbit.com/microsoft.com',
    TSLA:  'https://logo.clearbit.com/tesla.com',
    AMZN:  'https://logo.clearbit.com/amazon.com',
    META:  'https://logo.clearbit.com/meta.com',
    NVDA:  'https://logo.clearbit.com/nvidia.com',
    NFLX:  'https://logo.clearbit.com/netflix.com',
    JPM:   'https://logo.clearbit.com/jpmorganchase.com',
    V:     'https://logo.clearbit.com/visa.com',
    BABA:  'https://logo.clearbit.com/alibaba.com',
    AMD:   'https://logo.clearbit.com/amd.com',
    DIS:   'https://logo.clearbit.com/disney.com',
    'BRK-B': 'https://logo.clearbit.com/berkshirehathaway.com',
    ORCL:  'https://logo.clearbit.com/oracle.com',
  };

  private readonly STOCK_SECTORS: Record<string, string> = {
    AAPL: 'Tech',  GOOGL: 'Tech',  MSFT: 'Tech',  TSLA: 'Auto/EV',
    AMZN: 'E-commerce', META: 'Social', NVDA: 'Chip', NFLX: 'Media',
    JPM: 'Finance', V: 'Finance', BABA: 'E-commerce', AMD: 'Chip',
    DIS: 'Media', 'BRK-B': 'Conglomerate', ORCL: 'Tech',
  };

  constructor(private http: HttpClient) {}

  startAutoRefresh(): void {
    this.activeSubscribers++;
    if (this.refreshSubscription) return;
    this.refreshSubscription = interval(this.REFRESH_INTERVAL_MS).pipe(
      startWith(0)
    ).subscribe(() => {
      this.fetchCryptoMarkets();
      this.fetchGlobalData();
    });
  }

  stopAutoRefresh(): void {
    this.activeSubscribers = Math.max(0, this.activeSubscribers - 1);
    if (this.activeSubscribers === 0 && this.refreshSubscription) {
      this.refreshSubscription.unsubscribe();
      this.refreshSubscription = null;
    }
  }

  manualRefresh(): void {
    this.fetchCryptoMarkets();
    this.fetchGlobalData();
  }

  // ─────────────────────────────────────────
  //  CRYPTO
  // ─────────────────────────────────────────
  private fetchCryptoMarkets(): void {
    this.loadingSubject.next(true);
    this.errorSubject.next(null);
    const params = [
      'vs_currency=usd', 'order=market_cap_desc', 'per_page=20',
      'page=1', 'sparkline=true', 'price_change_percentage=1h%2C7d'
    ].join('&');
    this.http.get<CryptoAsset[]>(`${this.COINGECKO_BASE}/coins/markets?${params}`, SILENT)
      .pipe(catchError(() => {
        this.errorSubject.next('Không thể tải dữ liệu thị trường. Vui lòng thử lại.');
        this.loadingSubject.next(false);
        return of([]);
      }))
      .subscribe(data => {
        if (data.length > 0) {
          this.cryptoSubject.next(data);
          this.lastUpdatedSubject.next(new Date());
        }
        this.loadingSubject.next(false);
      });
  }

  private fetchGlobalData(): void {
    this.http.get<any>(`${this.COINGECKO_BASE}/global`, SILENT)
      .pipe(catchError(() => of(null)))
      .subscribe(resp => {
        if (resp?.data) {
          const d = resp.data;
          this.globalDataSubject.next({
            total_market_cap_usd: d.total_market_cap?.usd ?? 0,
            total_volume_usd: d.total_volume?.usd ?? 0,
            btc_dominance: d.market_cap_percentage?.btc ?? 0,
            market_cap_change_percentage_24h: d.market_cap_change_percentage_24h_usd ?? 0
          });
        }
      });
  }

  // ─────────────────────────────────────────
  //  GOLD — vang.today (free, no auth, CORS-native, updates every 5 min)
  //  type_codes: XAUUSD (world spot, USD/oz), SJL1L10 (SJC bar, VND/lượng), SJ9999 (SJC ring)
  // ─────────────────────────────────────────
  fetchGoldData(): void {
    this.goldLoadingSubject.next(true);
    this.goldErrorSubject.next(null);

    this.http.get<any>(this.VANG_TODAY, SILENT).pipe(
      catchError(() => {
        this.goldErrorSubject.next('Không thể tải giá vàng. Vui lòng thử lại.');
        this.goldLoadingSubject.next(false);
        return of(null);
      })
    ).subscribe(resp => {
      // Response: { success, prices: { XAUUSD: {...}, SJL1L10: {...}, SJ9999: {...}, ... } }
      if (!resp?.success || !resp?.prices) {
        this.goldErrorSubject.next('Không thể tải giá vàng. Vui lòng thử lại.');
        this.goldLoadingSubject.next(false);
        return;
      }
      const prices = resp.prices;

      // International spot
      const xauItem      = prices['XAUUSD'];
      const xauPrice     = xauItem?.buy       ?? 0;
      const xauChange    = xauItem?.change_buy ?? 0;
      const xauClose     = xauPrice - xauChange;
      const xauChangePct = xauClose > 0 ? (xauChange / xauClose) * 100 : 0;

      // All VN gold prices (VND, sell > 0), skip XAUUSD
      const vnGoldPrices: VNGoldPrice[] = Object.entries(prices)
        .filter(([code, item]: [string, any]) => code !== 'XAUUSD' && item.currency === 'VND' && (item.sell ?? 0) > 0)
        .map(([code, item]: [string, any]) => ({
          code,
          name: item.name ?? code,
          buy:       item.buy       ?? 0,
          sell:      item.sell      ?? 0,
          changeBuy: item.change_buy ?? 0,
        }));

      const usdVndRate      = this.USD_VND_RATE;
      const pricePerGramUsd = xauPrice > 0 ? xauPrice / this.TROY_OZ_GRAMS : 0;
      const pricePerGramVnd = pricePerGramUsd * usdVndRate;

      if (xauPrice > 0 || vnGoldPrices.length > 0) {
        this.goldSubject.next({
          xauPrice, xagPrice: 0,
          xauChangePct, xagChangePct: 0,
          xauChange, xauClose,
          pricePerGramUsd,
          pricePerChiVnd:   Math.round(pricePerGramVnd * this.CHI_GRAMS),
          pricePerLuongVnd: Math.round(pricePerGramVnd * this.LUONG_GRAMS),
          usdVndRate,
          vnGoldPrices,
          source: 'vang.today',
          updatedAt: new Date()
        });
      } else {
        this.goldErrorSubject.next('Không thể tải giá vàng. Vui lòng thử lại.');
      }
      this.goldLoadingSubject.next(false);
    });
  }

  // ─────────────────────────────────────────
  //  VN STOCKS — VPS broker API (via proxy)
  //  Prices in thousands VND (e.g. 76.2 → 76,200 VND)
  // ─────────────────────────────────────────
  fetchVNStocks(): void {
    this.vnLoadingSubject.next(true);
    this.vnErrorSubject.next(null);

    const vpsUrl = `${this.VPS_PROXY}/${this.VN_TICKERS}`;

    this.http.get<any>(vpsUrl, SILENT).pipe(
      catchError(() => {
        this.vnErrorSubject.next('Không thể tải dữ liệu cổ phiếu VN. Vui lòng thử lại.');
        this.vnLoadingSubject.next(false);
        return of(null);
      })
    ).subscribe(resp => {
      if (!resp) return;
      // Support both TCBS (t, cp, ch, chP, mv, hp, lp, lo)
      //          and VPS  (sym, lastPrice, changePc, totalVolume, h, g, o)
      const raw: any[] = Array.isArray(resp) ? resp : (resp?.data ?? []);
      const toVnd = (v: number) => (v > 0 && v < 2000) ? v * 1000 : v;
      const stocks: VNStockData[] = raw.map((item: any) => {
        const ticker = (item.t || item.sym || item.ticker || '').toUpperCase();
        const info   = this.VN_COMPANIES[ticker];
        const rawPrice  = item.cp ?? item.lastPrice ?? item.price ?? 0;
        const rawRef    = item.r ?? 0; // reference price (yesterday's close, VPS field)
        // Change: TCBS has item.ch; VPS has none → derive from lastPrice - referencePrice
        const rawChange = item.ch ?? item.priceChange ?? item.change
                          ?? (rawPrice > 0 && rawRef > 0 ? rawPrice - rawRef : 0);
        return {
          ticker,
          companyName: info?.name ?? ticker,
          price:     toVnd(rawPrice),
          change:    toVnd(rawChange),
          pctChange:       item.chP ?? +(item.changePc ?? item.pctChange ?? 0),
          // VPS: total session volume is in field 'lot'; TCBS uses 'mv'
          volume:          item.mv  ?? item.lot  ?? item.totalVolume ?? item.vol ?? 0,
          high:      toVnd(item.hp  ?? +(item.highPrice ?? item.h ?? 0)),
          low:       toVnd(item.lp  ?? +(item.lowPrice  ?? item.g ?? 0)),
          open:      toVnd(item.lo  ?? +(item.openPrice ?? item.o ?? 0)),
          exchange:        item.mc  ?? info?.exchange ?? 'HoSE',
        };
      }).filter(s => s.ticker && s.price > 0);
      this.vnStocksSubject.next(stocks);
      this.vnLoadingSubject.next(false);
    });
  }

  // ─────────────────────────────────────────
  //  FOREIGN STOCKS — Finnhub.io (free tier, 60 req/min, CORS-native)
  //  Requires FINNHUB_TOKEN — get free key at https://finnhub.io/register
  // ─────────────────────────────────────────
  fetchStocks(): void {
    this.stocksLoadingSubject.next(true);
    this.stocksErrorSubject.next(null);

    if (!this.FINNHUB_TOKEN) {
      this.stocksErrorSubject.next('CORS_ERROR');
      this.stocksLoadingSubject.next(false);
      return;
    }

    const symbols = this.STOCK_SYMBOLS.split(',');
    const requests: Record<string, Observable<any>> = {};
    symbols.forEach(sym => {
      requests[sym] = this.http.get<any>(
        `${this.FINNHUB_BASE}/quote?symbol=${sym}&token=${this.FINNHUB_TOKEN}`, SILENT
      ).pipe(catchError(() => of(null)));
    });

    forkJoin(requests).subscribe(results => {
      const quotes: StockQuote[] = symbols
        .filter(sym => (results[sym]?.c ?? 0) > 0)
        .map(sym => {
          const q = results[sym];
          return {
            symbol:                     sym,
            shortName:                  this.STOCK_NAMES[sym] ?? sym,
            regularMarketPrice:         q.c  ?? 0,
            regularMarketChange:        q.d  ?? 0,
            regularMarketChangePercent: q.dp ?? 0,
            regularMarketDayHigh:       q.h  ?? 0,
            regularMarketDayLow:        q.l  ?? 0,
            regularMarketPreviousClose: q.pc ?? 0,
            regularMarketVolume:        0,
            marketCap:                  0,
            fiftyTwoWeekHigh:           0,
            fiftyTwoWeekLow:            0,
            currency: 'USD',
            exchange: '',
            logoUrl:  this.STOCK_LOGOS[sym]   ?? '',
            sector:   this.STOCK_SECTORS[sym] ?? '',
          };
        });

      if (quotes.length > 0) {
        this.stocksSubject.next(quotes);
      } else {
        this.stocksErrorSubject.next('CORS_ERROR');
      }
      this.stocksLoadingSubject.next(false);
    });
  }

  // ─────────────────────────────────────────
  //  FORMATTERS
  // ─────────────────────────────────────────
  getTopCoins(count = 5): CryptoAsset[] {
    return this.cryptoSubject.getValue().slice(0, count);
  }

  formatLargeNumber(value: number): string {
    if (value >= 1e12) return (value / 1e12).toFixed(2) + 'T';
    if (value >= 1e9)  return (value / 1e9).toFixed(2) + 'B';
    if (value >= 1e6)  return (value / 1e6).toFixed(2) + 'M';
    if (value >= 1e3)  return (value / 1e3).toFixed(1) + 'K';
    return value.toFixed(2);
  }

  formatPrice(price: number): string {
    if (price >= 10000) return price.toLocaleString('en-US', { maximumFractionDigits: 0 });
    if (price >= 1)     return price.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    if (price >= 0.01)  return price.toFixed(4);
    return price.toFixed(6);
  }

  formatVND(value: number): string {
    if (value >= 1e9)  return (value / 1e9).toFixed(2) + ' tỷ';
    if (value >= 1e6)  return (value / 1e6).toFixed(1) + ' triệu';
    if (value >= 1e3)  return (value / 1e3).toFixed(0) + ' nghìn';
    return value.toFixed(0);
  }

  ngOnDestroy(): void {
    if (this.refreshSubscription) this.refreshSubscription.unsubscribe();
  }
}
