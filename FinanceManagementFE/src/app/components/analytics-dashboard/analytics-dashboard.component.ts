import { Component, OnInit, OnDestroy } from '@angular/core';
import { AIAnalyticsService, CompleteDashboard, HealthScore, BudgetForecast, SpendingCategory, AnomalyAlert } from '../../services/ai-analytics.service';
import { LanguageService } from '../../services/language.service';
import { Subject, takeUntil } from 'rxjs';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-analytics-dashboard',
  templateUrl: './analytics-dashboard.component.html',
  styleUrls: ['./analytics-dashboard.component.css']
})
export class AnalyticsDashboardComponent implements OnInit, OnDestroy {
  dashboard: CompleteDashboard | null = null;
  loading = true;
  error: string | null = null;
  userId = '1'; // TODO: Get from auth service

  // Month selection for filtered sections
  selectedMonth: string = '';  // format: 'YYYY-MM'
  selectedYear: number = 0;
  selectedMonthNumber: number = 0;
  currentMonth: string = '';  // Max value for month picker

  // Export state
  exportingExcel = false;
  exportingPdf = false;

  private destroy$ = new Subject<void>();

  constructor(
    private analyticsService: AIAnalyticsService,
    public language: LanguageService,
    public themeService: ThemeService
  ) {}

  ngOnInit(): void {
    // Initialize to current month
    const now = new Date();
    this.selectedYear = now.getFullYear();
    this.selectedMonthNumber = now.getMonth() + 1;
    this.selectedMonth = `${this.selectedYear}-${String(this.selectedMonthNumber).padStart(2, '0')}`;
    this.currentMonth = this.selectedMonth;

    this.loadDashboard();

    this.themeService.theme$.pipe(takeUntil(this.destroy$)).subscribe(() => {
      // Analytics dashboard uses CSS vars and Angular templates - no explicit chart redraw needed
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Load complete dashboard (initial load)
   * This loads all sections including forecast, patterns, recommendations
   */
  loadDashboard(): void {
    this.loading = true;
    this.error = null;

    this.analyticsService.getCompleteDashboard(this.userId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data) => {
          this.dashboard = data;
          this.loading = false;
        },
        error: (err) => {
          console.error('Error loading analytics dashboard:', err);
          this.error = 'Không thể tải dữ liệu phân tích. Vui lòng thử lại sau.';
          this.loading = false;
        }
      });
  }

  /**
   * Handle month selection change
   * Reloads only the filtered sections: health score, spending structure, anomalies
   */
  onMonthChange(): void {
    // Parse selected month
    const [year, month] = this.selectedMonth.split('-').map(Number);
    this.selectedYear = year;
    this.selectedMonthNumber = month;

    console.log(`Month changed to: ${year}-${month}`);

    // Reload filtered sections
    if (this.dashboard) {
      this.loadFilteredSections();
    }
  }

  /**
   * Load only the month-filtered sections
   */
  private loadFilteredSections(): void {
    // Load health score with month filter
    this.analyticsService.getHealthScore(this.userId, this.selectedYear, this.selectedMonthNumber)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data) => {
          if (this.dashboard) {
            this.dashboard.healthScore = data;
          }
        },
        error: (err) => console.error('Error loading health score:', err)
      });

    // Load spending structure with month filter
    this.analyticsService.getSpendingStructure(this.userId, this.selectedYear, this.selectedMonthNumber)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data) => {
          if (this.dashboard) {
            this.dashboard.spendingStructure = data;
          }
        },
        error: (err) => console.error('Error loading spending structure:', err)
      });

    // Load anomalies with month filter
    this.analyticsService.getAnomalies(this.userId, this.selectedYear, this.selectedMonthNumber)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data) => {
          if (this.dashboard) {
            this.dashboard.anomalies = data;
          }
        },
        error: (err) => console.error('Error loading anomalies:', err)
      });
  }

  getHealthScoreColor(score: number): string {
    if (score >= 90) return '#4ECDC4';
    if (score >= 80) return '#4ade80';
    if (score >= 70) return '#f59e0b';
    if (score >= 60) return '#f97316';
    return '#FF6B6B';
  }

  getHealthScoreGradient(score: number): string {
    const color = this.getHealthScoreColor(score);
    return `conic-gradient(${color} ${score * 3.6}deg, rgba(255, 255, 255, 0.05) ${score * 3.6}deg)`;
  }

  getCategoryColor(index: number): string {
    const colors = ['#4ECDC4', '#FF6B6B', '#ba68c8', '#81c784', '#ffa726', '#42a5f5'];
    return colors[index % colors.length];
  }

  getSeverityColor(severity: string): string {
    switch (severity.toUpperCase()) {
      case 'HIGH':
      case 'CRITICAL':
        return '#FF6B6B';
      case 'MEDIUM':
      case 'WARNING':
        return '#f97316';
      case 'LOW':
      case 'INFO':
        return '#4ECDC4';
      default:
        return '#9fb2d4';
    }
  }

  getTrendIcon(trend: string): string {
    switch (trend.toUpperCase()) {
      case 'INCREASING':
      case 'UP':
        return '↗';
      case 'DECREASING':
      case 'DOWN':
        return '↘';
      default:
        return '→';
    }
  }

  getTrendColor(trend: string): string {
    switch (trend.toUpperCase()) {
      case 'INCREASING':
      case 'UP':
        return '#FF6B6B';
      case 'DECREASING':
      case 'DOWN':
        return '#4ECDC4';
      default:
        return '#9fb2d4';
    }
  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND',
      minimumFractionDigits: 0
    }).format(amount);
  }

  formatPercent(value: number): string {
    return `${value.toFixed(1)}%`;
  }

  refreshDashboard(): void {
    this.loadDashboard();
  }

  exportReport(format: 'excel' | 'pdf'): void {
    if (!this.dashboard) return;

    if (format === 'excel') {
      this.exportingExcel = true;
    } else {
      this.exportingPdf = true;
    }

    // Send already-loaded dashboard data — backend only formats, no recalculation
    this.analyticsService.exportReport(this.dashboard, format, this.selectedMonthNumber, this.selectedYear)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (blob: Blob) => {
          const ext = format === 'excel' ? 'xlsx' : 'pdf';
          const month = String(this.selectedMonthNumber).padStart(2, '0');
          const filename = `bao-cao-tai-chinh-${month}-${this.selectedYear}.${ext}`;

          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = filename;
          document.body.appendChild(anchor);
          anchor.click();
          document.body.removeChild(anchor);
          URL.revokeObjectURL(url);
        },
        error: (err) => {
          console.error(`Export ${format} failed:`, err);
          this.exportingExcel = false;
          this.exportingPdf = false;
        },
        complete: () => {
          this.exportingExcel = false;
          this.exportingPdf = false;
        }
      });
  }

  calculateDashOffset(index: number): number {
    if (!this.dashboard) return 0;

    let offset = 0;
    for (let i = 0; i < index; i++) {
      offset += this.dashboard.spendingStructure.categories[i].actualPercent * 4.4;
    }
    return offset;
  }

  getMaxBalance(): number {
    if (!this.dashboard || !this.dashboard.budgetForecast.timeline.length) {
      return 1;
    }

    return Math.max(...this.dashboard.budgetForecast.timeline.map(t => t.balance));
  }
}
