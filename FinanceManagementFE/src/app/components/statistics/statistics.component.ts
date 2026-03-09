import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { Subscription } from 'rxjs';
import * as Highcharts from 'highcharts';
import * as AccessibilityMod from 'highcharts/modules/accessibility';
function initHcModule(mod: any): void {
  const fn = mod?.default ?? mod;
  if (typeof fn === 'function') fn(Highcharts);
}
initHcModule(AccessibilityMod);
import { LanguageService } from '../../services/language.service';
import { TransactionService, CashflowPoint, TransactionDashboardSummary, Metric, CategoryBreakdown } from '../../services/transaction.service';
import { UserService } from '../../services/user.service';
import { ToastService } from '../../services/toast.service';
import {
  ChartComponent,
  ApexNonAxisChartSeries,
  ApexResponsive,
  ApexChart,
  ApexTheme,
  ApexLegend,
  ApexDataLabels,
  ApexTooltip
} from 'ng-apexcharts';

export type PieChartOptions = {
  series: ApexNonAxisChartSeries;
  chart: ApexChart;
  labels: string[];
  colors: string[];
  responsive: ApexResponsive[];
  theme: ApexTheme;
  legend: ApexLegend;
  dataLabels: ApexDataLabels;
  tooltip?: ApexTooltip;
};

interface SummaryCard {
  title: string;
  value: string;
  subLabel: string;
  trend: string;
  trendDirection: 'up' | 'down' | 'flat';
}

interface SpendItem {
  label: string;
  amount: string;
  percentage?: number;
  icon?: string;
  color?: string;
  date?: string;
  category?: string;
}

@Component({
  selector: 'app-statistics',
  templateUrl: './statistics.component.html',
  styleUrls: ['./statistics.component.css']
})
export class StatisticsComponent implements OnInit, OnDestroy {
  // Time filter
  timeFilters = ['Tháng này', 'Tháng trước', 'Quý này', 'Quý trước', 'Tùy chọn'];
  selectedTimeFilter = 'Tháng này';
  customStartDate = '';
  customEndDate = '';

  timeRanges = ['Tháng này', 'Tháng trước', '3 tháng', '6 tháng'];
  accounts = ['Tất cả tài khoản', 'Ví', 'Ngân hàng', 'Thẻ tín dụng'];
  categories = ['Tất cả danh mục', 'Ăn uống', 'Nhà cửa', 'Di chuyển', 'Khác'];
  groups = ['Cá nhân', 'Gia đình', 'Nhóm bạn'];

  summaries: SummaryCard[] = [
    { title: 'Tổng thu nhập', value: '—', subLabel: 'so với kỳ trước', trend: '—', trendDirection: 'flat' },
    { title: 'Tổng chi tiêu', value: '—', subLabel: 'so với kỳ trước', trend: '—', trendDirection: 'flat' },
    { title: 'Số dư ròng (kỳ này)', value: '—', subLabel: 'Tỷ lệ tiết kiệm', trend: '—', trendDirection: 'flat' },
    { title: 'Chi tiêu TB / ngày', value: '—', subLabel: 'so với kỳ trước', trend: '—', trendDirection: 'flat' }
  ];

  categoryBreakdown: SpendItem[] = [];
  breakdownTotal = 0;

  bigSpends: SpendItem[] = [];
  loadingBigSpends = false;

  spendingChanges: any[] = [];
  loadingVariance = false;
  varianceMode: 'expense' | 'income' = 'expense';
  
  // Heatmap settings
  showHeatmapSettings = false;
  heatmapSettings = {
    mode: 'historical', // 'budget' | 'historical' | 'manual'
    manualLimit: 500000 // default 500k VND
  };
  loadingHeatmap = false;
  selectedHeatmapMonth: number = new Date().getMonth() + 1; // 1-12
  selectedHeatmapYear: number = new Date().getFullYear();
  availableYears: number[] = [];
  calculatedDailyLimit: number = 0; // Store calculated daily limit from API

  // Expense / Income breakdown
  breakdownMode: 'expense' | 'income' = 'expense';
  // Calendar data for expense density
  weekDays = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
  calendarDays: Array<{ day: number | null; level: number }> = [];

  // Category breakdown interaction
  activeCategoryIndex: number | null = null;
  fallbackCategoryColors = ['#ff7043', '#4fc3f7', '#ba68c8', '#81c784', '#f6c344', '#5dd6c9', '#ef6c9c', '#9aa5b1'];
  incomeBreakdown: SpendItem[] = [];

  // Cashflow chart
  userId = '';
  cashflow: CashflowPoint[] = [];
  months: { month: number; label: string; income: number; expense: number; balance: number }[] = [];
  yMin = 0;
  yMax = 0;
  yStep = 5_000_000;
  loadingCashflow = false;
  loadingSummary = false;
  loadingBreakdown = false;
  
  // ApexCharts Pie Chart
  @ViewChild('categoryChart') categoryChart?: ChartComponent;
  pieChartOptions: Partial<PieChartOptions> = {};
  
  private languageSub?: Subscription;

  constructor(
    private language: LanguageService,
    private transactionService: TransactionService,
    private userService: UserService,
    private toastService: ToastService
  ) {
    this.generateCalendar();
    // Generate available years (last 5 years to current + 1)
    const currentYear = new Date().getFullYear();
    for (let i = currentYear - 4; i <= currentYear + 1; i++) {
      this.availableYears.push(i);
    }
  }

  translate(key: string): string {
    return this.language.translate(key);
  }

  ngOnInit(): void {
    this.updateTranslatedTexts();
    // Load heatmap settings from localStorage
    const savedSettings = localStorage.getItem('heatmapSettings');
    if (savedSettings) {
      this.heatmapSettings = JSON.parse(savedSettings);
    }
    // Ensure selectedTimeFilter has a default value
    if (!this.selectedTimeFilter || this.selectedTimeFilter.trim() === '') {
      this.selectedTimeFilter = this.translate('statistics.timeFilters.thisMonth');
    }
    this.userService.getUserInfo().subscribe((u: any) => {
      this.userId = u?.id || u?.userId || '';
      this.loadSummary();
      this.loadBreakdown(this.breakdownMode);
      this.loadCashflow();
      this.loadBigSpends();
      this.loadVariance(this.varianceMode);
      this.loadHeatmap();
    });
    this.languageSub = this.language.currentLanguage$.subscribe(() => {
      this.updateTranslatedTexts();
      this.renderCashflowChart();
    });
  }

  private updateTranslatedTexts(): void {
    this.timeFilters = [
      this.translate('statistics.timeFilters.thisMonth'),
      this.translate('statistics.timeFilters.lastMonth'),
      this.translate('statistics.timeFilters.thisQuarter'),
      this.translate('statistics.timeFilters.lastQuarter'),
      this.translate('statistics.timeFilters.custom')
    ];
    
    // Get the translated value for "this month" (default)
    const thisMonthTranslated = this.translate('statistics.timeFilters.thisMonth');
    
    // Update selectedTimeFilter if it matches old values
    const oldValues = ['Tháng này', 'This month', 'Tháng trước', 'Last month', 'Quý này', 'This quarter', 'Quý trước', 'Last quarter', 'Tùy chọn', 'Custom'];
    const newValues = [
      this.translate('statistics.timeFilters.thisMonth'),
      this.translate('statistics.timeFilters.lastMonth'),
      this.translate('statistics.timeFilters.thisQuarter'),
      this.translate('statistics.timeFilters.lastQuarter'),
      this.translate('statistics.timeFilters.custom')
    ];
    const index = oldValues.findIndex(v => v === this.selectedTimeFilter);
    if (index >= 0 && index < newValues.length) {
      this.selectedTimeFilter = newValues[Math.floor(index / 2)];
    } else if (!this.selectedTimeFilter || this.selectedTimeFilter.trim() === '' || !this.timeFilters.includes(this.selectedTimeFilter)) {
      // If selectedTimeFilter is empty or not in the current filters, set to default "this month"
      this.selectedTimeFilter = thisMonthTranslated;
    }
    
    this.weekDays = [
      this.translate('statistics.weekDays.sun'),
      this.translate('statistics.weekDays.mon'),
      this.translate('statistics.weekDays.tue'),
      this.translate('statistics.weekDays.wed'),
      this.translate('statistics.weekDays.thu'),
      this.translate('statistics.weekDays.fri'),
      this.translate('statistics.weekDays.sat')
    ];
    
    // Only update titles and subLabels, preserve values and trends
    if (this.summaries && this.summaries.length >= 4) {
      this.summaries[0].title = this.translate('statistics.summary.totalIncome');
      this.summaries[0].subLabel = this.translate('statistics.summary.comparedToPrevious');
      this.summaries[1].title = this.translate('statistics.summary.totalExpense');
      this.summaries[1].subLabel = this.translate('statistics.summary.comparedToPrevious');
      this.summaries[2].title = this.translate('statistics.summary.netBalance');
      this.summaries[2].subLabel = this.translate('statistics.summary.savingRate');
      this.summaries[3].title = this.translate('statistics.summary.averageDailyExpense');
      this.summaries[3].subLabel = this.translate('statistics.summary.comparedToPrevious');
    } else {
      // Initialize only if summaries is empty
      this.summaries = [
        { title: this.translate('statistics.summary.totalIncome'), value: '—', subLabel: this.translate('statistics.summary.comparedToPrevious'), trend: '—', trendDirection: 'flat' },
        { title: this.translate('statistics.summary.totalExpense'), value: '—', subLabel: this.translate('statistics.summary.comparedToPrevious'), trend: '—', trendDirection: 'flat' },
        { title: this.translate('statistics.summary.netBalance'), value: '—', subLabel: this.translate('statistics.summary.savingRate'), trend: '—', trendDirection: 'flat' },
        { title: this.translate('statistics.summary.averageDailyExpense'), value: '—', subLabel: this.translate('statistics.summary.comparedToPrevious'), trend: '—', trendDirection: 'flat' }
      ];
    }
  }

  ngOnDestroy(): void {
    this.languageSub?.unsubscribe();
  }

  generateCalendar() {
    const now = new Date();
    const year = now.getFullYear();
    const month = now.getMonth();
    
    // Get first day of month and number of days
    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const daysInMonth = lastDay.getDate();
    
    // Get day of week for first day (0 = Sunday, 6 = Saturday)
    // Calendar UI starts with Sunday (CN), so use getDay() directly
    const firstDayOfWeek = firstDay.getDay();
    
    this.calendarDays = [];
    
    // Add empty cells for days before the first day of the month
    for (let i = 0; i < firstDayOfWeek; i++) {
      this.calendarDays.push({ day: null, level: 0 });
    }
    
    // Add all days of the month with mock expense levels
    for (let day = 1; day <= daysInMonth; day++) {
      // Mock data: random levels for demonstration
      // In real app, this would come from actual expense data
      const random = Math.random();
      let level = 0;
      if (random > 0.7) level = 3; // High expense (red)
      else if (random > 0.4) level = 2; // Medium expense (orange)
      else if (random > 0.2) level = 1; // Low expense (green)
      
      this.calendarDays.push({ day, level });
    }
    
    // Fill remaining cells to complete the last week (if needed)
    const totalCells = this.calendarDays.length;
    const remaining = totalCells % 7;
    if (remaining !== 0) {
      for (let i = 0; i < (7 - remaining); i++) {
        this.calendarDays.push({ day: null, level: 0 });
      }
    }
  }

  loadHeatmap() {
    if (!this.userId) return;
    
    // Use selected month/year
    const year = this.selectedHeatmapYear;
    const month = this.selectedHeatmapMonth;
    
    // Determine daily limit based on mode
    const dailyLimit = this.heatmapSettings.mode === 'manual' ? this.heatmapSettings.manualLimit : undefined;
    
    this.loadingHeatmap = true;
    this.transactionService.getExpenseHeatmap(this.userId, year, month, dailyLimit).subscribe({
      next: (heatmap) => {
        // Store calculated daily limit from API
        this.calculatedDailyLimit = heatmap.dailyLimit || 0;
        
        // Regenerate calendar for selected month/year
        this.generateCalendarForDate(year, month);
        
        // Calculate first day offset for calendar positioning
        // Calendar UI starts with Sunday (CN), so use getDay() directly (0 = Sunday)
        const firstDayOfWeek = new Date(year, month - 1, 1).getDay();
        
        // Update calendar days with real data using date field
        heatmap.days.forEach((dayData) => {
          // Extract day number from date string (yyyy-MM-dd)
          const dayOfMonth = parseInt(dayData.date.split('-')[2], 10);
          
          // Calculate calendar index: offset for empty cells + (day - 1)
          const calendarIndex = firstDayOfWeek + (dayOfMonth - 1);
          
          if (calendarIndex >= 0 && calendarIndex < this.calendarDays.length && 
              this.calendarDays[calendarIndex].day === dayOfMonth) {
            this.calendarDays[calendarIndex].level = dayData.level;
          }
        });
      },
      error: (err) => {
        console.error('Error loading heatmap:', err);
      },
      complete: () => this.loadingHeatmap = false
    });
  }
  
  generateCalendarForDate(year: number, month: number) {
    // Get first day of month and number of days
    const firstDay = new Date(year, month - 1, 1);
    const lastDay = new Date(year, month, 0);
    const daysInMonth = lastDay.getDate();
    
    // Get day of week for first day (0 = Sunday, 6 = Saturday)
    // Calendar UI starts with Sunday (CN), so use getDay() directly
    const firstDayOfWeek = firstDay.getDay();
    
    this.calendarDays = [];
    
    // Add empty cells for days before the first day of the month
    for (let i = 0; i < firstDayOfWeek; i++) {
      this.calendarDays.push({ day: null, level: 0 });
    }
    
    // Add all days of the month - will be populated by loadHeatmap()
    for (let day = 1; day <= daysInMonth; day++) {
      this.calendarDays.push({ day, level: 0 });
    }
    
    // Fill remaining cells to complete the last week (if needed)
    const totalCells = this.calendarDays.length;
    const remaining = totalCells % 7;
    if (remaining !== 0) {
      for (let i = 0; i < (7 - remaining); i++) {
        this.calendarDays.push({ day: null, level: 0 });
      }
    }
  }
  
  onHeatmapDateChange() {
    this.loadHeatmap();
  }
  
  formatCurrencyVND(amount: number): string {
    return `${Math.round(amount).toLocaleString('vi-VN')} đ`;
  }
  
  openHeatmapSettings() {
    this.showHeatmapSettings = true;
    // Load calculated limit when opening settings if in historical mode
    if (this.heatmapSettings.mode === 'historical') {
      this.loadCalculatedDailyLimit();
    }
  }
  
  closeHeatmapSettings() {
    this.showHeatmapSettings = false;
  }

  /**
   * Called when heatmap mode changes (historical vs manual)
   * Fetches the calculated daily limit from API when switching to historical mode
   */
  onHeatmapModeChange() {
    if (this.heatmapSettings.mode === 'historical') {
      this.loadCalculatedDailyLimit();
    }
  }

  /**
   * Load the calculated daily limit from API (historical mode)
   */
  private loadCalculatedDailyLimit() {
    if (!this.userId) return;
    
    const year = this.selectedHeatmapYear;
    const month = this.selectedHeatmapMonth;
    
    // Call API without dailyLimit to get calculated historical limit
    this.transactionService.getExpenseHeatmap(this.userId, year, month).subscribe({
      next: (heatmap) => {
        this.calculatedDailyLimit = heatmap.dailyLimit || 0;
      },
      error: (err) => {
        console.error('Error loading calculated daily limit:', err);
      }
    });
  }
  
  saveHeatmapSettings() {
    try {
      // Validate manual limit if mode is manual
      if (this.heatmapSettings.mode === 'manual') {
        if (!this.heatmapSettings.manualLimit || this.heatmapSettings.manualLimit <= 0) {
          this.toastService.showError(this.translate('statistics.heatmapSettings.invalidLimit'));
          return;
        }
      }
      
      localStorage.setItem('heatmapSettings', JSON.stringify(this.heatmapSettings));
      this.showHeatmapSettings = false;
      this.toastService.showSuccess(this.translate('statistics.heatmapSettings.saveSuccess'));
      this.loadHeatmap(); // Reload with new settings
    } catch (error) {
      console.error('Error saving heatmap settings:', error);
      this.toastService.showError(this.translate('statistics.heatmapSettings.saveError'));
    }
  }

  getMonthName(): string {
    const months = ['Tháng 1', 'Tháng 2', 'Tháng 3', 'Tháng 4', 'Tháng 5', 'Tháng 6',
                    'Tháng 7', 'Tháng 8', 'Tháng 9', 'Tháng 10', 'Tháng 11', 'Tháng 12'];
    return months[new Date().getMonth()];
  }

  // ===== Category donut interactions =====
  getCategoryTotal(): number {
    return this.breakdownTotal;
  }

  getCategoryTotalFormatted(): string {
    if (this.loadingBreakdown) {
      return '—';
    }
    if (this.breakdownTotal === 0 && (!this.categoryBreakdown || this.categoryBreakdown.length === 0) && 
        (!this.incomeBreakdown || this.incomeBreakdown.length === 0)) {
      return '—';
    }
    return this.formatCurrency(this.breakdownTotal);
  }

  getCurrentBreakdown(): SpendItem[] {
    return this.breakdownMode === 'expense' ? this.categoryBreakdown : this.incomeBreakdown;
  }

  private normalizeAmount(amount: string | number | undefined | null): number {
    if (typeof amount === 'number') {
      return Number.isFinite(amount) ? amount : 0;
    }
    if (!amount) return 0;
    // Remove all non-digit except minus
    const cleaned = amount.toString().replace(/[^\d-]/g, '');
    const val = parseFloat(cleaned);
    return Number.isFinite(val) ? val : 0;
  }

  getCategoryColor(index: number): string {
    const list = this.getCurrentBreakdown();
    return list[index]?.color || this.fallbackCategoryColors[index % this.fallbackCategoryColors.length];
  }

  getCategoryPercentage(amount: string | number, index?: number): number {
    if (index !== undefined) {
      const item = this.getCurrentBreakdown()[index];
      if (item?.percentage !== undefined) {
        return item.percentage;
      }
    }
    const total = this.getCategoryTotal();
    const val = this.normalizeAmount(amount);
    if (total === 0) return 0;
    return (val / total) * 100;
  }

  getCategorySegments(): Array<{ color: string; dashArray: string; dashOffset: string; percentage: number }> {
    const total = this.getCategoryTotal();
    if (total === 0) return [];
    let cumulative = 0;
    const gap = 0.5;
    return this.getCurrentBreakdown().map((c, idx) => {
      const pct = this.getCategoryPercentage(c.amount);
      const adjusted = Math.max(pct - gap, 0);
      const seg = {
        color: this.getCategoryColor(idx),
        dashArray: `${adjusted} ${100 - adjusted}`,
        dashOffset: `${-cumulative}`,
        percentage: pct
      };
      cumulative += pct;
      return seg;
    });
  }

  setActiveCategory(index: number | null): void {
    this.activeCategoryIndex = index;
  }

  clearActiveCategory(): void {
    this.activeCategoryIndex = null;
  }

  getActiveCategoryLabel(): string {
    if (this.activeCategoryIndex === null) return '';
    return this.getCurrentBreakdown()[this.activeCategoryIndex]?.label || '';
  }

  getActiveCategoryPercent(): number {
    if (this.activeCategoryIndex === null) return 0;
    return this.getCategoryPercentage(this.getCurrentBreakdown()[this.activeCategoryIndex]?.amount, this.activeCategoryIndex);
  }

  applyTimeFilter(): void {
    // Apply filter for summary, breakdown, cashflow, biggest expenses, and variance
    this.loadSummary();
    this.loadBreakdown(this.breakdownMode);
    this.loadCashflow();
    this.loadBigSpends();
    this.loadVariance(this.varianceMode);
  }

  // ===== Cashflow chart =====
  private loadCashflow() {
    if (!this.userId) return;
    const { startDate, endDate } = this.resolveDateRange();
    this.loadingCashflow = true;
    this.transactionService.getCashflow(this.userId, startDate, endDate).subscribe({
      next: (data) => {
        this.cashflow = data || [];
        this.prepareChart();
        setTimeout(() => this.renderCashflowChart());
        this.loadingCashflow = false;
      },
      error: () => (this.loadingCashflow = false)
    });
  }

  private loadSummary() {
    if (!this.userId) return;
    const { startDate, endDate } = this.resolveDateRange();
    this.loadingSummary = true;
    this.transactionService.getDashboardSummary(this.userId, startDate, endDate).subscribe({
      next: (res) => this.summaries = this.mapDashboardSummary(res),
      error: () => { /* giữ nguyên state cũ nếu lỗi */ },
      complete: () => this.loadingSummary = false
    });
  }

  private loadBreakdown(mode: 'expense' | 'income') {
    if (!this.userId) return;
    const { startDate, endDate } = this.resolveDateRange();
    this.loadingBreakdown = true;
    this.transactionService.getCategoryBreakdown(this.userId, mode, startDate, endDate).subscribe({
      next: (res) => {
        this.breakdownTotal = res.totalAmount || 0;
        const mapped: SpendItem[] = (res.items || []).map((item, idx) => ({
          label: item.category,
          amount: this.formatCurrency(item.amount),
          percentage: item.percentage,
          color: this.fallbackCategoryColors[idx % this.fallbackCategoryColors.length]
        }));
        if (mode === 'expense') {
          this.categoryBreakdown = mapped;
        } else {
          this.incomeBreakdown = mapped;
        }
        this.updatePieChart();
      },
      error: () => { /* giữ nguyên state cũ nếu lỗi */ },
      complete: () => this.loadingBreakdown = false
    });
  }

  private loadBigSpends() {
    if (!this.userId) return;
    const { startDate, endDate } = this.resolveDateRange();
    this.loadingBigSpends = true;
    this.transactionService.getTopBiggestTransactions(this.userId, startDate, endDate, 6).subscribe({
      next: (transactions) => {
        this.bigSpends = transactions.map(tx => ({
          label: tx.transactionContent || this.translate('statistics.biggestExpenses.noDescription'),
          amount: this.formatCurrency(-(tx.amountOut || 0)),
          icon: this.getCategoryIcon(tx.category),
          date: this.formatDate(tx.transactionDate),
          category: tx.category || this.translate('statistics.biggestExpenses.uncategorized')
        }));
      },
      error: (err) => {
        console.error('Error loading biggest transactions:', err);
        this.bigSpends = [];
      },
      complete: () => this.loadingBigSpends = false
    });
  }

  private getCategoryIcon(category?: string): string {
    const iconMap: { [key: string]: string } = {
      'Mua sắm': '🛍️',
      'Ăn uống': '🍔',
      'Di chuyển': '🚗',
      'Giải trí': '🎮',
      'Y tế': '💊',
      'Giáo dục': '📚',
      'Nhà cửa': '🏠',
      'Điện nước': '💡',
      'Internet': '📶',
      'Điện thoại': '📱',
      'Quần áo': '👕',
      'Làm đẹp': '💄',
      'Du lịch': '✈️',
      'Đầu tư': '💰',
      'Tiết kiệm': '🏦',
      'Khác': '📦'
    };
    return iconMap[category || ''] || '💳';
  }

  private formatDate(date?: any): string {
    if (!date) return '';
    const d = new Date(date);
    const day = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const year = d.getFullYear();
    return `${day}/${month}/${year}`;
  }

  private loadVariance(mode: 'expense' | 'income') {
    if (!this.userId) return;
    
    // Get current period dates
    const { startDate: currentStart, endDate: currentEnd } = this.resolveDateRange();
    
    // Calculate previous period dates (same duration)
    const current = new Date(currentStart);
    const end = new Date(currentEnd);
    const durationMs = end.getTime() - current.getTime();
    const previous = new Date(current.getTime() - durationMs);
    
    const pad = (n: number) => n.toString().padStart(2, '0');
    const toStr = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    
    const previousStart = toStr(previous);
    const previousEnd = toStr(new Date(previous.getTime() + durationMs - 24 * 60 * 60 * 1000));
    
    this.loadingVariance = true;
    this.transactionService.getCategoryVariance(
      this.userId, mode, currentStart, currentEnd, previousStart, previousEnd
    ).subscribe({
      next: (res) => {
        this.spendingChanges = res.items.slice(0, 4).map((item) => ({
          label: item.category,
          actual: this.formatCurrencyShort(item.currentAmount),
          delta: this.formatCurrencyShort(Math.abs(item.delta)),
          deltaPct: `${item.delta >= 0 ? '+' : '-'}${Math.abs(item.deltaPercentage).toFixed(0)}%`,
          trend: item.trend,
          color: this.getCategoryColor(0),
          icon: this.getCategoryIcon(item.category)
        }));
      },
      error: (err) => {
        console.error('Error loading variance:', err);
        this.spendingChanges = [];
      },
      complete: () => this.loadingVariance = false
    });
  }

  private formatCurrencyShort(amount: number): string {
    const absAmount = Math.abs(amount);
    if (absAmount >= 1000000000) {
      return `${(absAmount / 1000000000).toFixed(1)}tỷ`;
    } else if (absAmount >= 1000000) {
      return `${(absAmount / 1000000).toFixed(1)}tr`;
    } else if (absAmount >= 1000) {
      return `${(absAmount / 1000).toFixed(0)}k`;
    } else {
      return `${absAmount.toFixed(0)}đ`;
    }
  }

  onToggleVariance(mode: 'expense' | 'income') {
    if (this.varianceMode === mode) return;
    this.varianceMode = mode;
    this.loadVariance(mode);
  }

  private updatePieChart() {
    const list = this.getCurrentBreakdown();
    if (!list || list.length === 0) {
      this.pieChartOptions = {};
      return;
    }

    const series = list.map(item => this.normalizeAmount(item.amount));
    const labels = list.map(item => item.label);
    const colors = list.map((_, idx) => this.getCategoryColor(idx));

    this.pieChartOptions = {
      series: series,
      chart: {
        type: 'pie',
        width: 220,
        height: 220,
        background: 'transparent'
      },
      labels: labels,
      colors: colors,
      theme: {
        mode: 'dark',
        palette: 'palette1'
      },
      legend: {
        show: false
      },
      dataLabels: {
        enabled: false
      },
      responsive: [
        {
          breakpoint: 600,
          options: {
            chart: {
              width: 220,
              height: 220
            }
          }
        }
      ],
      tooltip: {
        y: {
          formatter: (val: number) => {
            return this.formatCurrency(val);
          }
        }
      }
    };
  }

  onToggleBreakdown(mode: 'expense' | 'income') {
    if (this.breakdownMode === mode) return;
    this.breakdownMode = mode;
    const list = mode === 'expense' ? this.categoryBreakdown : this.incomeBreakdown;
    if (!list || list.length === 0) {
      this.loadBreakdown(mode);
    } else {
      // Recalculate total from current list
      this.breakdownTotal = list.reduce((sum, item) => {
        return sum + this.normalizeAmount(item.amount);
      }, 0);
      this.updatePieChart();
    }
  }

  private resolveDateRange(): { startDate: string; endDate: string } {
    const today = new Date();
    const pad = (n: number) => n.toString().padStart(2, '0');
    const toStr = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

    // Get translated values for comparison
    const customFilter = this.translate('statistics.timeFilters.custom');
    const lastMonthFilter = this.translate('statistics.timeFilters.lastMonth');
    const thisQuarterFilter = this.translate('statistics.timeFilters.thisQuarter');
    const thisMonthFilter = this.translate('statistics.timeFilters.thisMonth');
    const lastQuarterFilter = this.translate('statistics.timeFilters.lastQuarter');

    // Check for custom date range
    if (this.selectedTimeFilter === customFilter || this.selectedTimeFilter === 'Tùy chọn' || this.selectedTimeFilter === 'Custom') {
      if (this.customStartDate && this.customEndDate) {
        return { startDate: this.customStartDate, endDate: this.customEndDate };
      }
    }

    // Check for last month
    if (this.selectedTimeFilter === lastMonthFilter || this.selectedTimeFilter === 'Tháng trước' || this.selectedTimeFilter === 'Last month') {
      const firstDayPrev = new Date(today.getFullYear(), today.getMonth() - 1, 1);
      const lastDayPrev = new Date(today.getFullYear(), today.getMonth(), 0);
      return { startDate: toStr(firstDayPrev), endDate: toStr(lastDayPrev) };
    }

    // Check for last quarter
    if (this.selectedTimeFilter === lastQuarterFilter || this.selectedTimeFilter === 'Quý trước' || this.selectedTimeFilter === 'Last quarter') {
      const currentMonth = today.getMonth(); // 0-based
      const currentQuarter = Math.floor(currentMonth / 3);
      const lastQuarter = currentQuarter === 0 ? 3 : currentQuarter - 1; // If Q1, go to Q4 of previous year
      const lastQuarterStartMonth = lastQuarter * 3;
      const lastQuarterYear = currentQuarter === 0 ? today.getFullYear() - 1 : today.getFullYear();
      const firstDayLastQuarter = new Date(lastQuarterYear, lastQuarterStartMonth, 1);
      const lastDayLastQuarter = new Date(lastQuarterYear, lastQuarterStartMonth + 3, 0);
      return { startDate: toStr(firstDayLastQuarter), endDate: toStr(lastDayLastQuarter) };
    }

    // Check for this quarter
    if (this.selectedTimeFilter === thisQuarterFilter || this.selectedTimeFilter === 'Quý này' || this.selectedTimeFilter === 'This quarter') {
      const currentMonth = today.getMonth(); // 0-based
      const quarterStartMonth = currentMonth - (currentMonth % 3);
      const firstDayQuarter = new Date(today.getFullYear(), quarterStartMonth, 1);
      const lastDayQuarter = new Date(today.getFullYear(), quarterStartMonth + 3, 0);
      return { startDate: toStr(firstDayQuarter), endDate: toStr(lastDayQuarter) };
    }

    // Default: This month
    const firstDay = new Date(today.getFullYear(), today.getMonth(), 1);
    const lastDay = new Date(today.getFullYear(), today.getMonth() + 1, 0);
    return { startDate: toStr(firstDay), endDate: toStr(lastDay) };
  }

  private formatCurrency(v: number): string {
    return `${Math.round(v || 0).toLocaleString('vi-VN')} đ`;
  }

  private mapDashboardSummary(res: TransactionDashboardSummary): SummaryCard[] {
    if (!res) return this.summaries;
    const formatPct = (v: number | null) => v === null ? '—' : `${v >= 0 ? '+' : ''}${v.toFixed(1)}%`;

    const mapMetric = (title: string, metric: Metric, sub: string, trendSource?: Metric): SummaryCard => ({
      title,
      value: this.formatCurrency(metric?.current ?? 0),
      subLabel: sub,
      trend: formatPct((trendSource ?? metric)?.changePct ?? 0),
      trendDirection: metric?.direction ?? 'flat'
    });

    return [
      mapMetric(this.translate('statistics.summary.totalIncome'), res.totalIncome, this.translate('statistics.summary.comparedToPrevious')),
      mapMetric(this.translate('statistics.summary.totalExpense'), res.totalExpense, this.translate('statistics.summary.comparedToPrevious')),
      mapMetric(this.translate('statistics.summary.netBalance'), res.netBalance, this.translate('statistics.summary.savingRate'), res.savingRate),
      mapMetric(this.translate('statistics.summary.averageDailyExpense'), res.averageDailyExpense, this.translate('statistics.summary.comparedToPrevious'))
    ];
  }

  private prepareChart() {
    // Determine the year to display based on the selected period
    const { startDate } = this.resolveDateRange();
    const start = new Date(startDate);
    const year = start.getFullYear();
    
    // Group cashflow data by year-month for lookup
    const byYearMonth = new Map<string, CashflowPoint>();
    this.cashflow.forEach(p => {
      const key = `${p.year}-${p.month}`;
      byYearMonth.set(key, p);
    });

    // Build months array - show 12 months of the determined year
    this.months = Array.from({ length: 12 }, (_, i) => {
      const m = i + 1;
      const key = `${year}-${m}`;
      const p = byYearMonth.get(key);
      return {
        month: m,
        label: this.monthLabel(m, year),
        income: p?.totalIncome || 0,
        expense: p?.totalExpense || 0,
        balance: p?.balance || 0
      };
    });

    const maxVal = Math.max(...this.months.map(x => Math.max(x.income, x.expense, x.balance, 0)));
    const minVal = Math.min(0, ...this.months.map(x => Math.min(x.balance, 0)));
    const step = this.yStep;
    const roundUp = (n: number) => Math.ceil(n / step) * step;
    const roundDown = (n: number) => Math.floor(n / step) * step;
    this.yMax = roundUp(maxVal || step);
    this.yMin = roundDown(minVal);
  }

  private renderCashflowChart() {
    const H = Highcharts as any;
    const categories = this.months.map(m => m.label);
    const expenseNegative = this.months.map(m => -Math.abs(m.expense || 0));
    const income = this.months.map(m => m.income || 0);
    const balance = this.months.map(m => m.balance || 0);

    H.chart('cashflow-statistics', {
      chart: { type: 'column', backgroundColor: 'transparent', style: { fontFamily: 'Be Vietnam Pro, sans-serif' } },
      title: { text: undefined },
      xAxis: { categories, lineColor: '#334155', labels: { style: { color: '#94a3b8' } } },
      yAxis: {
        title: { text: undefined },
        gridLineColor: 'rgba(255,255,255,0.18)',
        gridLineWidth: 1,
        minorGridLineColor: 'rgba(255,255,255,0.12)',
        minorTickInterval: 'auto',
        plotLines: [{ value: 0, color: '#a3b1c6', width: 2, zIndex: 5 }],
        labels: { style: { color: '#94a3b8' }, formatter: function(this: any){ return H.numberFormat(this.value, 0, '.', ',') + '₫'; } }
      },
      legend: { itemStyle: { color: '#cbd5e1' } },
      credits: { enabled: false },
      exporting: { enabled: false },
      tooltip: { shared: false, backgroundColor: 'rgba(15,23,42,0.95)', borderColor: '#93c5fd', style: { color: '#e2e8f0' },
        pointFormatter: function(this: any){ return '<span style=\"color:'+ this.color +'\">●</span> ' + this.series.name + ': <b>' + H.numberFormat(this.y, 0, '.', ',') + '₫</b>'; }
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

  private monthLabel(month: number, year: number): string {
    const map = ['','T1','T2','T3','T4','T5','T6','T7','T8','T9','T10','T11','T12'];
    if (this.language.getCurrentLanguage() === 'en') {
      const em = ['','Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
      return `${em[month]} ${year}`;
    }
    return `${map[month]} ${year}`;
  }
}


