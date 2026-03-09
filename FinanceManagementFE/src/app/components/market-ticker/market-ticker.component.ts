import { Component, OnInit, OnDestroy, HostListener, ElementRef } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { MarketDataService, CryptoAsset, GoldData, VNStockData } from '../../services/market-data.service';

type TickerSection = 'crypto' | 'gold' | 'vn';

@Component({
  selector: 'app-market-ticker',
  templateUrl: './market-ticker.component.html',
  styleUrls: ['./market-ticker.component.css']
})
export class MarketTickerComponent implements OnInit, OnDestroy {

  isOpen = false;
  activeSection: TickerSection = 'crypto';

  topCoins: CryptoAsset[] = [];
  loading = false;

  gold: GoldData | null = null;
  goldLoading = false;

  vnStocks: VNStockData[] = [];
  vnLoading = false;

  private subs = new Subscription();

  constructor(
    private marketService: MarketDataService,
    private router: Router,
    private elRef: ElementRef
  ) {}

  ngOnInit(): void {
    this.subs.add(this.marketService.crypto$.subscribe(coins => {
      this.topCoins = coins.slice(0, 6);
    }));
    this.subs.add(this.marketService.loading$.subscribe(v => this.loading = v));
    this.subs.add(this.marketService.gold$.subscribe(g => this.gold = g));
    this.subs.add(this.marketService.goldLoading$.subscribe(v => this.goldLoading = v));
    this.subs.add(this.marketService.vnStocks$.subscribe(s => this.vnStocks = s.slice(0, 6)));
    this.subs.add(this.marketService.vnLoading$.subscribe(v => this.vnLoading = v));
    this.marketService.startAutoRefresh();
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    this.marketService.stopAutoRefresh();
  }

  toggleDropdown(): void {
    this.isOpen = !this.isOpen;
  }

  setSection(section: TickerSection): void {
    this.activeSection = section;
    if (section === 'gold' && !this.gold && !this.goldLoading) {
      this.marketService.fetchGoldData();
    }
    if (section === 'vn' && this.vnStocks.length === 0 && !this.vnLoading) {
      this.marketService.fetchVNStocks();
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.isOpen && !this.elRef.nativeElement.contains(event.target)) {
      this.isOpen = false;
    }
  }

  goToInvestment(): void {
    this.isOpen = false;
    this.router.navigate(['/investment']);
  }

  formatPrice(price: number): string {
    return this.marketService.formatPrice(price);
  }

  formatVND(v: number): string {
    if (v >= 1_000_000) return (v / 1_000_000).toFixed(1) + ' tr';
    if (v >= 1_000) return (v / 1_000).toFixed(0) + 'k';
    return v.toLocaleString('vi-VN');
  }

  abs(v: number): number {
    return Math.abs(v);
  }

  getBtcPrice(): string {
    const btc = this.topCoins.find(c => c.id === 'bitcoin');
    if (!btc) return '---';
    return '$' + this.marketService.formatPrice(btc.current_price);
  }

  getBtcChange(): number {
    const btc = this.topCoins.find(c => c.id === 'bitcoin');
    return btc?.price_change_percentage_24h ?? 0;
  }
}
