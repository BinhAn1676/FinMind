import { Component, OnDestroy, OnInit } from '@angular/core';
import { LanguageService } from '../../services/language.service';
import { LayoutService } from '../../services/layout.service';
import { TransactionService, CashflowPoint, TransactionSummary } from '../../services/transaction.service';
import { AccountService } from '../../services/account.service';
import { UserService } from '../../services/user.service';
import { Subscription, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { ThemeService } from '../../services/theme.service';
import * as Highcharts from 'highcharts';
import * as ExportingMod from 'highcharts/modules/exporting';
import * as ExportDataMod from 'highcharts/modules/export-data';
import * as OfflineExportingMod from 'highcharts/modules/offline-exporting';
import * as AccessibilityMod from 'highcharts/modules/accessibility';
function initHcModule(mod: any): void {
  const fn = mod?.default ?? mod;
  if (typeof fn === 'function') fn(Highcharts);
}
initHcModule(ExportingMod);
initHcModule(ExportDataMod);
initHcModule(OfflineExportingMod);
initHcModule(AccessibilityMod);

@Component({
  selector: 'app-overview',
  templateUrl: './overview.component.html',
  styleUrls: ['./overview.component.css']
})
export class OverviewComponent implements OnInit, OnDestroy {
  userId: string = '';

  // Date range (string for API, Date for p-calendar)
  startDate: string = '';
  endDate: string = '';
  startDateCal: Date | null = null;
  endDateCal: Date | null = null;
  preset: string = 'today';
  showPresetMenu = false;
  get presetLabel(): string {
    const map: any = {
      today: this.language.translate('overview.today'),
      yesterday: this.language.translate('overview.yesterday'),
      last7: this.language.translate('overview.last7'),
      last30: this.language.translate('overview.last30'),
      thisMonth: this.language.translate('overview.thisMonth'),
      lastMonth: this.language.translate('overview.lastMonth'),
      thisQuarter: this.language.translate('overview.thisQuarter'),
      lastQuarter: this.language.translate('overview.lastQuarter'),
      thisYear: this.language.translate('overview.thisYear'),
      lastYear: this.language.translate('overview.lastYear')
    };
    return map[this.preset] || map['today'];
  }

  // Summary cards
  txnSummary: TransactionSummary = {
    totalIncome: 0,
    totalExpense: 0,
    netAmount: 0,
    transactionCount: 0,
    averageAmount: 0
  };
  accountSummary: { totalBalance: number; accountCount: number } = { totalBalance: 0, accountCount: 0 };
  loadingSummary = false;

  // Recent transactions
  recent: any[] = [];
  loadingRecent = false;

  // Cashflow
  cashflow: CashflowPoint[] = [];
  loadingCashflow = false;
  // Normalized 12-month data for chart
  months: { month: number; label: string; income: number; expense: number; balance: number }[] = [];
  // Axis scaling
  yMin = 0; // rounded min (can be negative)
  yMax = 0; // rounded max
  yStep = 5_000_000; // 5M step as requested
  chartWidth = 960; // dynamic width
  chartHeight = 300;
  innerWidth = 0; // drawable area width
  innerHeight = 220; // will be recalculated
  leftPadding = 60;
  bottomPadding = 30;
  topPadding = 20;

  // Tooltip
  ttVisible = false;
  ttText = '';
  ttX = 0;
  ttY = 0;
  private hcChart: any;

  // Subscriptions
  languageSub!: Subscription;
  private destroy$ = new Subject<void>();

  constructor(
    public language: LanguageService,
    public layout: LayoutService,
    private transactionService: TransactionService,
    private accountService: AccountService,
    private userService: UserService,
    private themeService: ThemeService
  ) {}

  ngOnInit(): void {
    this.userService.getUserInfo().subscribe(u => {
      this.userId = (u as any)?.id || (u as any)?.userId || '';
      this.applyPreset('thisMonth');
      this.refreshAll();
    });
    this.languageSub = this.language.currentLanguage$.subscribe(() => {});
    this.themeService.theme$.pipe(takeUntil(this.destroy$)).subscribe(() => {
      setTimeout(() => this.renderHighcharts(), 50);
    });
  }

  ngOnDestroy(): void {
    this.languageSub?.unsubscribe();
    this.destroy$.next();
    this.destroy$.complete();
  }

  refreshAll() {
    if (!this.userId) return;
    this.loadSummaries();
    this.loadCashflow();
    this.loadRecent();
  }

  // Date presets similar to the screenshot
  applyPreset(p: string) {
    this.preset = p;
    const today = new Date();
    const pad = (n: number) => (n < 10 ? '0' + n : '' + n);
    const toStr = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

    if (p === 'today') {
      this.startDate = toStr(today);
      this.endDate = toStr(today);
    } else if (p === 'thisMonth') {
      const first = new Date(today.getFullYear(), today.getMonth(), 1);
      const last = new Date(today.getFullYear(), today.getMonth() + 1, 0);
      this.startDate = toStr(first);
      this.endDate = toStr(last);
    } else if (p === 'yesterday') {
      const y = new Date(today);
      y.setDate(today.getDate() - 1);
      this.startDate = toStr(y);
      this.endDate = toStr(y);
    } else if (p === 'last7') {
      const start = new Date(today);
      start.setDate(today.getDate() - 6);
      this.startDate = toStr(start);
      this.endDate = toStr(today);
    } else if (p === 'last30') {
      const start = new Date(today);
      start.setDate(today.getDate() - 29);
      this.startDate = toStr(start);
      this.endDate = toStr(today);
    } else if (p === 'lastMonth') {
      const first = new Date(today.getFullYear(), today.getMonth() - 1, 1);
      const last = new Date(today.getFullYear(), today.getMonth(), 0);
      this.startDate = toStr(first);
      this.endDate = toStr(last);
    } else if (p === 'thisQuarter' || p === 'lastQuarter') {
      const q = Math.floor(today.getMonth() / 3) + 1;
      const tq = p === 'thisQuarter' ? q : q - 1;
      const year = p === 'thisQuarter' ? today.getFullYear() : (tq === 0 ? today.getFullYear() - 1 : today.getFullYear());
      const quarter = tq === 0 ? 4 : tq;
      const startMonth = (quarter - 1) * 3;
      const first = new Date(year, startMonth, 1);
      const last = new Date(year, startMonth + 3, 0);
      this.startDate = toStr(first);
      this.endDate = toStr(last);
    } else if (p === 'thisYear' || p === 'lastYear') {
      const year = p === 'thisYear' ? today.getFullYear() : today.getFullYear() - 1;
      const first = new Date(year, 0, 1);
      const last = new Date(year, 12, 0);
      this.startDate = toStr(first);
      this.endDate = toStr(last);
    }
    // Sync calendar pickers
    this.startDateCal = this.startDate ? new Date(this.startDate) : null;
    this.endDateCal = this.endDate ? new Date(this.endDate) : null;
  }

  onDateChange() {
    this.refreshAll();
  }

  // Called when p-calendar selects/clears a date
  onCalendarChange() {
    const toStr = (d: Date | null) => {
      if (!d) return '';
      const pad = (n: number) => (n < 10 ? '0' + n : '' + n);
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    };
    this.startDate = toStr(this.startDateCal);
    this.endDate = toStr(this.endDateCal);
    this.preset = '';
    this.refreshAll();
  }

  private loadSummaries() {
    this.loadingSummary = true;
    this.transactionService.getSummary(this.userId, this.startDate, this.endDate).subscribe({
      next: (s) => {
        this.txnSummary = s;
        this.loadingSummary = false;
      },
      error: () => (this.loadingSummary = false)
    });

    this.accountService.summary(this.userId).subscribe({
      next: (s) => (this.accountSummary = s || { totalBalance: 0, accountCount: 0 }),
      error: () => {}
    });
  }

  private loadCashflow() {
    this.loadingCashflow = true;
    this.transactionService.getCashflow(this.userId, this.startDate, this.endDate).subscribe({
      next: (data) => {
        this.cashflow = data || [];
        this.prepareChart();
        this.loadingCashflow = false;
        setTimeout(() => this.renderHighcharts(), 50);
      },
      error: () => (this.loadingCashflow = false)
    });
  }

  private loadRecent() {
    this.loadingRecent = true;
    this.transactionService.filterTransactions({
      userId: this.userId,
      startDate: this.startDate,
      endDate: this.endDate,
      page: 0,
      size: 5
    }).subscribe({
      next: (res: any) => {
        this.recent = res?.content || [];
        this.loadingRecent = false;
      },
      error: () => (this.loadingRecent = false)
    });
  }

  // Helpers
  formatCurrency(n: number | undefined | null): string {
    const val = typeof n === 'number' ? n : 0;
    return val.toLocaleString(this.language.getCurrentLanguage() === 'en' ? 'en-US' : 'vi-VN') + '₫';
  }

  monthLabel(p: CashflowPoint): string {
    const m = p.month;
    const year = p.year;
    const map = ['','T1','T2','T3','T4','T5','T6','T7','T8','T9','T10','T11','T12'];
    if (this.language.getCurrentLanguage() === 'en') {
      const em = ['','Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
      return `${em[m]} ${year}`;
    }
    return `${map[m]} ${year}`;
  }

  // Expose Math for Angular template expressions
  get Math() {
    return Math;
  }

  // Compute top spending categories from recent transactions
  get categoryInsights(): { category: string; amount: number; pct: number }[] {
    const catMap = new Map<string, number>();
    for (const t of this.recent) {
      if ((t.amountOut || 0) > 0) {
        const cat = t.category || 'Khác';
        catMap.set(cat, (catMap.get(cat) || 0) + (t.amountOut || 0));
      }
    }
    const sorted = Array.from(catMap.entries())
      .sort((a, b) => b[1] - a[1])
      .slice(0, 5);
    const total = sorted.reduce((sum, [, val]) => sum + val, 0) || 1;
    return sorted.map(([category, amount]) => ({
      category,
      amount,
      pct: Math.round((amount / total) * 100)
    }));
  }

  downloadChartAsImage() {
    const node = document.getElementById('cashflow-chart');
    if (!node) return;
    const svg = node.querySelector('svg');
    if (!svg) return;
    const serializer = new XMLSerializer();
    const src = serializer.serializeToString(svg);
    const img = new Image();
    const svgBlob = new Blob([src], { type: 'image/svg+xml;charset=utf-8' });
    const url = URL.createObjectURL(svgBlob);
    img.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = svg.clientWidth || 800;
      canvas.height = svg.clientHeight || 300;
      const ctx = canvas.getContext('2d');
      if (!ctx) return;
      ctx.fillStyle = '#0A192F';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(img, 0, 0);
      canvas.toBlob((blob) => {
        if (!blob) return;
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = 'cashflow.png';
        a.click();
      });
      URL.revokeObjectURL(url);
    };
    img.src = url;
  }

  exportExcel() {
    const year = this.startDate ? new Date(this.startDate).getFullYear() : new Date().getFullYear();
    this.transactionService.exportCashflowExcel(this.userId, year, this.startDate, this.endDate).subscribe((blob: Blob) => {
      const a = document.createElement('a');
      const url = URL.createObjectURL(blob);
      a.href = url;
      a.download = `cashflow-${year}.xlsx`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  // === Chart helpers ===
  private prepareChart() {
    // Determine target year
    const yearFromStart = this.startDate ? new Date(this.startDate).getFullYear() : new Date().getFullYear();
    const year = (this.cashflow && this.cashflow.length > 0) ? (this.cashflow[0].year || yearFromStart) : yearFromStart;

    // Build 12 months with zeros
    const byMonth = new Map<number, CashflowPoint>();
    this.cashflow.forEach(p => { if (p.year === year) byMonth.set(p.month, p); });
    this.months = Array.from({ length: 12 }, (_, i) => {
      const m = i + 1;
      const p = byMonth.get(m);
      return {
        month: m,
        label: this.monthLabel({ year, month: m, totalIncome: 0, totalExpense: 0, balance: 0 } as CashflowPoint),
        income: p?.totalIncome || 0,
        expense: p?.totalExpense || 0,
        balance: p?.balance || 0
      };
    });

    // Compute y scale
    const maxVal = Math.max(
      ...this.months.map(x => Math.max(x.income, x.expense, x.balance, 0))
    );
    const minVal = Math.min(0, ...this.months.map(x => Math.min(x.balance, 0)));
    const step = this.yStep;
    const roundUp = (n: number) => Math.ceil(n / step) * step;
    const roundDown = (n: number) => Math.floor(n / step) * step;
    this.yMax = roundUp(maxVal || step);
    this.yMin = roundDown(minVal);

    // Layout math
    const barGroupWidth = 70; // group per month
    this.innerWidth = 12 * barGroupWidth;
    this.innerHeight = this.chartHeight - this.topPadding - this.bottomPadding;
    this.chartWidth = this.leftPadding + this.innerWidth + 40; // right padding
  }

  yToSvg(v: number): number {
    const range = this.yMax - this.yMin || 1;
    const y = this.topPadding + ((this.yMax - v) / range) * this.innerHeight;
    return y;
  }

  barHeight(value: number): number {
    const yBase = this.yToSvg(0);
    const yVal = this.yToSvg(value);
    return Math.max(0, Math.abs(yBase - yVal));
  }

  barY(value: number): number {
    const base = this.yToSvg(0);
    const yVal = this.yToSvg(value);
    return Math.min(base, yVal);
  }

  yTicks(): number[] {
    const ticks: number[] = [];
    for (let v = this.yMin; v <= this.yMax; v += this.yStep) ticks.push(v);
    if (ticks[ticks.length - 1] !== this.yMax) ticks.push(this.yMax);
    return ticks;
  }

  showTip(evt: MouseEvent, label: string, value: number) {
    this.ttText = `${label}: ${this.formatCurrency(value)}`;
    this.ttVisible = true;
    this.moveTip(evt);
  }
  showTipWithMonth(evt: MouseEvent, monthLabel: string, seriesLabel: string, value: number) {
    this.ttText = `${monthLabel} — ${seriesLabel}: ${this.formatCurrency(value)}`;
    this.ttVisible = true;
    this.moveTip(evt);
  }
  moveTip(evt: MouseEvent) { this.ttX = evt.clientX + 12; this.ttY = evt.clientY + 12; }
  hideTip() { this.ttVisible = false; }

  private renderHighcharts() {
    const H = Highcharts as any;

    const categories = this.months.map(m => m.label);
    const expenseNegative = this.months.map(m => -Math.abs(m.expense || 0));
    const income = this.months.map(m => m.income || 0);
    const balance = this.months.map(m => m.balance || 0);
    const isDark = this.themeService.isDark();
    const chartText = isDark ? '#94a3b8' : '#64748B';
    const chartGrid = isDark ? 'rgba(255,255,255,0.18)' : '#E2E8F0';
    const tooltipBg = isDark ? 'rgba(15,23,42,0.95)' : 'rgba(255,255,255,0.97)';
    const tooltipColor = isDark ? '#e2e8f0' : '#1E293B';

    const self = this;
    this.hcChart = H.chart('cashflow-hc', {
      chart: { type: 'column', backgroundColor: 'transparent', style: { fontFamily: 'Be Vietnam Pro, sans-serif' } },
      title: { text: undefined },
      xAxis: { categories, lineColor: chartGrid, labels: { style: { color: chartText } } },
      yAxis: {
        title: { text: undefined },
        gridLineColor: chartGrid,
        gridLineWidth: 1,
        minorGridLineColor: chartGrid,
        minorTickInterval: 'auto',
        plotLines: [{ value: 0, color: '#a3b1c6', width: 2, zIndex: 5 }],
        labels: { style: { color: chartText }, formatter: function(this: any){ return H.numberFormat(this.value, 0, '.', ',') + '₫'; } }
      },
      legend: { itemStyle: { color: chartText } },
      credits: { enabled: false },
      exporting: {
        enabled: true,
        buttons: {
          contextButton: {
            symbol: 'menu',
            menuItems: [
              {
                text: self.language.translate('overview.exportImage'),
                onclick: function(this: any) {
                  if (this.exportChartLocal) this.exportChartLocal(); else this.exportChart();
                }
              },
              {
                text: self.language.translate('transactions.exportExcel'),
                onclick: function() { self.exportExcel(); }
              }
            ]
          }
        },
        // Override options for exported image (solid background, clearer grid/labels)
        chartOptions: {
          chart: { backgroundColor: '#0A192F' },
          xAxis: { labels: { style: { color: '#cbd5e1' }, rotation: 0 } },
          yAxis: { gridLineColor: '#334155', labels: { style: { color: '#cbd5e1' } } },
          legend: { backgroundColor: 'transparent', itemStyle: { color: '#e2e8f0' } }
        },
        sourceWidth: 1200,
        sourceHeight: 420,
        scale: 1
      },
      tooltip: { shared: false, backgroundColor: tooltipBg, borderColor: '#93c5fd', style: { color: tooltipColor },
        pointFormatter: function(this: any){ return '<span style="color:'+ this.color +'">●</span> ' + this.series.name + ': <b>' + H.numberFormat(this.y, 0, '.', ',') + '₫</b>'; }
      },
      plotOptions: {
        column: {
          borderRadius: 6,
          grouping: true,
          borderWidth: 0
        }
      },
      series: [
        { name: this.language.translate('overview.income'), data: income, color: '#4E89FF' },
        { name: this.language.translate('overview.expense'), data: expenseNegative, color: '#FFC043' },
        { name: this.language.translate('overview.balance'), data: balance, color: '#22c55e' }
      ]
    });
  }

  formatDate(date: any): string {
    if (!date) return '';
    const d = new Date(date);
    const day = d.getDate().toString().padStart(2, '0');
    const month = (d.getMonth() + 1).toString().padStart(2, '0');
    const year = d.getFullYear();
    return `${day}/${month}/${year}`;
  }
}
