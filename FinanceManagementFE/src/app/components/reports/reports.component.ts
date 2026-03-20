import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { LanguageService } from '../../services/language.service';
import { LayoutService } from '../../services/layout.service';
import { TransactionService, TransactionFilterParams } from '../../services/transaction.service';
import { AccountService } from '../../services/account.service';
import { CategoryService, Category } from '../../services/category.service';
import { UserService } from '../../services/user.service';
import { ToastService } from '../../services/toast.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { ThemeService } from '../../services/theme.service';
import {
  ChartComponent,
  ApexAxisChartSeries,
  ApexChart,
  ApexXAxis,
  ApexDataLabels,
  ApexTooltip,
  ApexStroke,
  ApexPlotOptions,
  ApexLegend,
  ApexNonAxisChartSeries,
  ApexResponsive
} from "ng-apexcharts";

@Component({
  selector: 'app-reports',
  templateUrl: './reports.component.html',
  styleUrls: ['./reports.component.css']
})
export class ReportsComponent implements OnInit, OnDestroy {
  userId!: string;

  // Report generation state
  isGenerating = false;

  // Filter options
  reportFilters: TransactionFilterParams = {
    userId: '',
    startDate: '',
    endDate: '',
    transactionType: undefined,
    bankAccountIds: [],
    textSearch: '',
    minAmount: undefined,
    maxAmount: undefined
  };

  // Additional filter not in TransactionFilterParams
  selectedCategory: string | undefined = undefined;

  // Preview Panel
  previewData: any = null;
  isLoadingPreview = false;
  showPreview = false;

  // Charts (Phase 3)
  categoryChartOptions: any;
  trendChartOptions: any;
  showCharts = false;

  // Templates (Phase 3)
  savedTemplates: any[] = [];
  showSaveTemplateDialog = false;
  newTemplateName = '';
  loadingTemplates = false;
  showDeleteConfirmDialog = false;
  templateToDelete: any = null;

  // Email Reports (Phase 3)
  showEmailDialog = false;
  recipientEmail = '';
  isSendingEmail = false;

  // Scheduled Reports (Phase 3)
  showScheduleDialog = false;
  scheduleEmail = '';
  showDeleteScheduleDialog = false;
  scheduleToDelete: any = null;
  scheduleFrequency: 'daily' | 'weekly' | 'monthly' = 'weekly';
  scheduleHour: number = 9; // Default to 9 AM
  // Phase 4: Cron support
  scheduleUseCron: boolean = false; // Toggle between legacy (simple) and cron (advanced)
  scheduleCronExpression: string = ''; // Cron expression from builder
  isCronValid: boolean = false; // Track cron validation state
  // Phase 5: Web Notifications
  scheduleWebNotificationEnabled: boolean = true; // Enable/disable web notifications (default: true)
  scheduleNotifyOnSuccess: boolean = true; // Send notification on success (default: true)
  scheduleNotifyOnFailure: boolean = true; // Send notification on failure (default: true)
  isSavingSchedule = false;
  savedSchedules: any[] = [];
  loadingSchedules = false;
  showScheduleDetailsDialog = false;
  selectedSchedule: any = null;

  // Report History (Phase 5)
  reportHistory: any[] = [];
  loadingHistory = false;
  showHistorySection = false;
  historyPage = 0;
  historySize = 5;
  historyTotalElements = 0;
  historyTotalPages = 0;
  showDeleteHistoryDialog = false;
  historyToDelete: any = null;
  selectedHistoryIds: Set<string> = new Set();
  selectAllHistory = false;
  showBulkDeleteDialog = false;

  // Export Customization (Phase 2 & 3)
  selectedColumns: string[] = ['date', 'type', 'amount', 'category', 'content', 'account'];
  availableColumns = [
    { value: 'date', label: 'Transaction Date' },
    { value: 'type', label: 'Type (In/Out)' },
    { value: 'amount', label: 'Amount' },
    { value: 'category', label: 'Category' },
    { value: 'content', label: 'Description' },
    { value: 'account', label: 'Bank Account' },
    { value: 'reference', label: 'Reference Number' }
  ];
  sortOrder: 'dateAsc' | 'dateDesc' | 'amountAsc' | 'amountDesc' = 'dateDesc';
  includeSummarySheet = true;
  exportFormat: 'excel' | 'pdf' | 'csv' = 'excel';

  // Available options
  categories: Category[] = [];
  bankAccounts: any[] = [];
  loadingCategories = false;
  loadingAccounts = false;

  // Date range presets
  dateRangePresets = [
    { value: 'last7days' },
    { value: 'last30days' },
    { value: 'thisMonth' },
    { value: 'lastMonth' },
    { value: 'thisYear' },
    { value: 'custom' }
  ];

  selectedDatePreset = 'last30days';
  showCustomDateRange = false;

  // Date objects for p-calendar binding
  startDateObj: Date | null = null;
  endDateObj: Date | null = null;

  private destroy$ = new Subject<void>();

  constructor(
    public language: LanguageService,
    public layout: LayoutService,
    private transactionService: TransactionService,
    private accountService: AccountService,
    private categoryService: CategoryService,
    private userService: UserService,
    private toastService: ToastService,
    private themeService: ThemeService
  ) {}

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  ngOnInit(): void {
    // Load user info
    this.userService.getUserInfo().subscribe({
      next: (user: any) => {
        if (user) {
          this.userId = user.id;
          this.reportFilters.userId = user.id;
          this.applyDatePreset('last30days'); // Set default
          this.loadCategories();
          this.loadBankAccounts();
          this.loadSavedTemplates();
          this.loadSchedules();
        }
      },
      error: (error: any) => {
        console.error('Error loading user:', error);
      }
    });

    this.themeService.theme$.pipe(takeUntil(this.destroy$)).subscribe(() => {
      // Re-initialize charts if preview data is available
      if (this.previewData?.categoryBreakdown) {
        this.initializeCategoryChart(this.previewData.categoryBreakdown);
      }
      if (this.previewData?.dailyTrend) {
        this.initializeTrendChart(this.previewData.dailyTrend);
      }
    });
  }

  loadSavedTemplates(): void {
    this.loadingTemplates = true;
    this.transactionService.getReportTemplates(this.userId).subscribe({
      next: (templates: any[]) => {
        this.savedTemplates = templates;
        this.loadingTemplates = false;
      },
      error: (error: any) => {
        console.error('Error loading templates:', error);
        this.loadingTemplates = false;
      }
    });
  }

  openSaveTemplateDialog(): void {
    this.newTemplateName = '';
    this.showSaveTemplateDialog = true;
  }

  onOverlayMouseDown(event: MouseEvent, dialogType: 'saveTemplate' | 'email' | 'schedule' | 'deleteConfirm' | 'deleteSchedule' | 'deleteHistory' | 'bulkDelete' | 'scheduleDetails'): void {
    // Only close if the click target is the overlay itself (not a child element)
    const target = event.target as HTMLElement;
    if (target.classList.contains('modal-overlay')) {
      event.preventDefault();
      event.stopPropagation();

      if (dialogType === 'saveTemplate') {
        this.cancelSaveTemplate();
      } else if (dialogType === 'email') {
        this.cancelEmailDialog();
      } else if (dialogType === 'schedule') {
        this.cancelScheduleDialog();
      } else if (dialogType === 'deleteConfirm') {
        this.cancelDeleteTemplate();
      } else if (dialogType === 'deleteSchedule') {
        this.cancelDeleteSchedule();
      } else if (dialogType === 'deleteHistory') {
        this.cancelDeleteHistory();
      } else if (dialogType === 'bulkDelete') {
        this.cancelBulkDelete();
      } else if (dialogType === 'scheduleDetails') {
        this.closeScheduleDetails();
      }
    }
  }

  cancelSaveTemplate(): void {
    this.showSaveTemplateDialog = false;
    this.newTemplateName = '';
  }

  saveTemplate(): void {
    if (!this.newTemplateName.trim()) {
      this.toastService.showError('Please enter a template name', 3000);
      return;
    }

    const template = {
      userId: this.userId,
      name: this.newTemplateName.trim(),
      filters: {
        ...this.reportFilters,
        category: this.selectedCategory,
        sortOrder: this.sortOrder,
        includeSummarySheet: this.includeSummarySheet,
        selectedColumns: this.selectedColumns,
        exportFormat: this.exportFormat
      }
    };

    this.transactionService.saveReportTemplate(template).subscribe({
      next: (savedTemplate: any) => {
        this.savedTemplates.push(savedTemplate);
        this.toastService.showSuccess('Template saved successfully!', 3000);
        this.showSaveTemplateDialog = false;
        this.newTemplateName = '';
      },
      error: (error: any) => {
        console.error('Error saving template:', error);
        this.toastService.showError('Failed to save template', 3000);
      }
    });
  }

  loadTemplate(template: any): void {
    const filters = template.filters;

    // Apply all saved filters
    this.reportFilters = {
      ...this.reportFilters,
      startDate: filters.startDate,
      endDate: filters.endDate,
      transactionType: filters.transactionType,
      bankAccountIds: filters.bankAccountIds || [],
      textSearch: filters.textSearch,
      minAmount: filters.minAmount,
      maxAmount: filters.maxAmount
    };

    this.selectedCategory = filters.category;
    this.sortOrder = filters.sortOrder || 'dateDesc';
    this.includeSummarySheet = filters.includeSummarySheet !== false;
    this.selectedColumns = filters.selectedColumns || ['date', 'type', 'amount', 'category', 'content', 'account'];
    this.exportFormat = filters.exportFormat || 'excel';

    this.toastService.showSuccess(`Template "${template.name}" loaded!`, 3000);
  }

  deleteTemplate(template: any): void {
    this.templateToDelete = template;
    this.showDeleteConfirmDialog = true;
  }

  cancelDeleteTemplate(): void {
    this.showDeleteConfirmDialog = false;
    this.templateToDelete = null;
  }

  confirmDeleteTemplate(): void {
    if (!this.templateToDelete) {
      return;
    }

    const templateId = this.templateToDelete.id;
    this.transactionService.deleteReportTemplate(templateId, this.userId).subscribe({
      next: () => {
        this.savedTemplates = this.savedTemplates.filter(t => t.id !== templateId);
        this.toastService.showSuccess('Template deleted successfully!', 3000);
        this.cancelDeleteTemplate();
      },
      error: (error: any) => {
        console.error('Error deleting template:', error);
        this.toastService.showError('Failed to delete template', 3000);
        this.cancelDeleteTemplate();
      }
    });
  }

  openEmailDialog(): void {
    // Validate filters before opening email dialog
    if (!this.reportFilters.startDate || !this.reportFilters.endDate) {
      this.toastService.showError('Please select a date range first', 3000);
      return;
    }

    if (this.selectedColumns.length === 0) {
      this.toastService.showError('Please select at least one column to export', 3000);
      return;
    }

    this.recipientEmail = '';
    this.showEmailDialog = true;
  }

  cancelEmailDialog(): void {
    this.showEmailDialog = false;
    this.recipientEmail = '';
  }

  sendReportByEmail(): void {
    // Validate email
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!this.recipientEmail.trim() || !emailPattern.test(this.recipientEmail)) {
      this.toastService.showError('Please enter a valid email address', 3000);
      return;
    }

    this.isSendingEmail = true;

    // Prepare filters
    const cleanedFilters: TransactionFilterParams = {
      ...this.reportFilters,
      bankAccountIds: (this.reportFilters.bankAccountIds || [])
        .filter(id => id !== null && id !== undefined && id !== '')
        .map(id => String(id).trim()),
      category: this.selectedCategory,
      sortOrder: this.sortOrder,
      includeSummarySheet: this.includeSummarySheet,
      selectedColumns: this.selectedColumns,
      exportFormat: this.exportFormat
    };

    this.transactionService.sendReportByEmail(cleanedFilters, this.recipientEmail).subscribe({
      next: () => {
        this.toastService.showSuccess(`Report sent to ${this.recipientEmail}!`, 4000);
        this.showEmailDialog = false;
        this.recipientEmail = '';
        this.isSendingEmail = false;
      },
      error: (error: any) => {
        console.error('Error sending email:', error);
        this.toastService.showError('Failed to send email. Please try again.', 4000);
        this.isSendingEmail = false;
      }
    });
  }

  loadCategories(): void {
    this.loadingCategories = true;
    this.categoryService.getAllCategories(this.userId).subscribe({
      next: (categories: Category[]) => {
        this.categories = categories;
        this.loadingCategories = false;
      },
      error: (error: any) => {
        console.error('Error loading categories:', error);
        this.loadingCategories = false;
      }
    });
  }

  loadBankAccounts(): void {
    this.loadingAccounts = true;
    this.accountService.filter(this.userId, '', 0, 1000).subscribe({
      next: (response: any) => {
        // Ensure bankAccountId is string for proper binding
        this.bankAccounts = (response.content || []).map((account: any) => ({
          ...account,
          bankAccountId: String(account.bankAccountId) // Use bankAccountId, not id
        }));
        this.loadingAccounts = false;
        console.log('✅ Loaded bank accounts:', this.bankAccounts);
      },
      error: (error: any) => {
        console.error('Error loading accounts:', error);
        this.loadingAccounts = false;
      }
    });
  }

  onDatePresetChange(): void {
    if (this.selectedDatePreset === 'custom') {
      this.showCustomDateRange = true;
    } else {
      this.showCustomDateRange = false;
      this.applyDatePreset(this.selectedDatePreset);
    }
  }

  applyDatePreset(preset: string): void {
    const today = new Date();
    let startDate = new Date();
    let endDate = new Date();

    switch (preset) {
      case 'last7days':
        startDate.setDate(today.getDate() - 7);
        break;
      case 'last30days':
        startDate.setDate(today.getDate() - 30);
        break;
      case 'thisMonth':
        startDate = new Date(today.getFullYear(), today.getMonth(), 1);
        break;
      case 'lastMonth':
        startDate = new Date(today.getFullYear(), today.getMonth() - 1, 1);
        endDate = new Date(today.getFullYear(), today.getMonth(), 0);
        break;
      case 'thisYear':
        startDate = new Date(today.getFullYear(), 0, 1);
        break;
      default:
        return;
    }

    // Update both Date objects and string formats
    this.startDateObj = startDate;
    this.endDateObj = endDate;
    this.reportFilters.startDate = this.formatDate(startDate);
    this.reportFilters.endDate = this.formatDate(endDate);
  }

  onDateChange(): void {
    // Convert Date objects to string format for backend
    if (this.startDateObj) {
      this.reportFilters.startDate = this.formatDate(this.startDateObj);
    } else {
      this.reportFilters.startDate = '';
    }

    if (this.endDateObj) {
      this.reportFilters.endDate = this.formatDate(this.endDateObj);
    } else {
      this.reportFilters.endDate = '';
    }
  }

  formatDate(date: Date): string {
    return date.toISOString().split('T')[0];
  }

  loadPreview(): void {
    // Validation
    if (!this.reportFilters.startDate || !this.reportFilters.endDate) {
      this.toastService.showError('Please select a date range first', 3000);
      return;
    }

    this.isLoadingPreview = true;
    this.showPreview = true;

    // Prepare filters for summary
    const summaryFilters = {
      bankAccountIds: (this.reportFilters.bankAccountIds || [])
        .filter(id => id !== null && id !== undefined && id !== ''),
      startDate: this.reportFilters.startDate,
      endDate: this.reportFilters.endDate,
      textSearch: this.reportFilters.textSearch,
      transactionType: this.reportFilters.transactionType,
      minAmount: this.reportFilters.minAmount,
      maxAmount: this.reportFilters.maxAmount
    };

    // Load summary data
    if (summaryFilters.bankAccountIds.length > 0) {
      // Group summary by bank accounts
      this.transactionService.getSummaryByBankAccountIds(
        summaryFilters.bankAccountIds,
        summaryFilters.startDate,
        summaryFilters.endDate,
        summaryFilters.textSearch,
        summaryFilters.transactionType as any,
        summaryFilters.minAmount,
        summaryFilters.maxAmount
      ).subscribe({
        next: (data: any) => {
          this.previewData = data;
          this.isLoadingPreview = false;
          this.loadChartData();
        },
        error: (error: any) => {
          console.error('Error loading preview:', error);
          this.toastService.showError('Failed to load preview', 3000);
          this.isLoadingPreview = false;
        }
      });
    } else if (this.userId) {
      // User summary
      this.transactionService.getSummary(
        this.userId,
        summaryFilters.startDate,
        summaryFilters.endDate
      ).subscribe({
        next: (data: any) => {
          this.previewData = data;
          this.isLoadingPreview = false;
          this.loadChartData();
        },
        error: (error: any) => {
          console.error('Error loading preview:', error);
          this.toastService.showError('Failed to load preview', 3000);
          this.isLoadingPreview = false;
        }
      });
    }
  }

  generateReport(): void {
    if (this.isGenerating) return;

    // Validation
    if (!this.reportFilters.startDate || !this.reportFilters.endDate) {
      this.toastService.showError('Please select a date range', 3000);
      return;
    }

    // Validate at least one column is selected
    if (this.selectedColumns.length === 0) {
      this.toastService.showError('Please select at least one column to export', 3000);
      return;
    }

    this.isGenerating = true;

    // Clean and prepare filters
    const cleanedFilters: TransactionFilterParams = {
      ...this.reportFilters,
      // Ensure bankAccountIds is properly filtered and converted to strings
      bankAccountIds: (this.reportFilters.bankAccountIds || [])
        .filter(id => id !== null && id !== undefined && id !== '')
        .map(id => String(id).trim()),
      // Add category if selected
      category: this.selectedCategory,
      // Add Phase 2 & 3 export options
      sortOrder: this.sortOrder,
      includeSummarySheet: this.includeSummarySheet,
      selectedColumns: this.selectedColumns,
      exportFormat: this.exportFormat
    };

    console.log('📊 Generating report with filters:', cleanedFilters);

    // Call appropriate export method based on format
    let exportObservable: any;
    switch (this.exportFormat) {
      case 'pdf':
        exportObservable = this.transactionService.exportToPdf(cleanedFilters);
        break;
      case 'csv':
        exportObservable = this.transactionService.exportToCsv(cleanedFilters);
        break;
      case 'excel':
      default:
        exportObservable = this.transactionService.exportToExcel(cleanedFilters);
        break;
    }

    exportObservable.subscribe({
      next: (blob: Blob) => {
        this.downloadFile(blob, this.generateFileName());
        this.isGenerating = false;
        this.toastService.showSuccess('Report generated successfully!', 3000);
      },
      error: (error: any) => {
        console.error('Error generating report:', error);
        this.toastService.showError('Failed to generate report', 4000);
        this.isGenerating = false;
      }
    });
  }

  private downloadFile(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  }

  private generateFileName(): string {
    const parts = ['transactions'];

    // Add date range
    const startDate = this.reportFilters.startDate?.replace(/-/g, '');
    const endDate = this.reportFilters.endDate?.replace(/-/g, '');
    parts.push(`${startDate}_${endDate}`);

    // Add transaction type if specified
    if (this.reportFilters.transactionType === 'income') {
      parts.push('income');
    } else if (this.reportFilters.transactionType === 'expense') {
      parts.push('expense');
    }

    // Add category if specified
    if (this.selectedCategory) {
      const categorySlug = this.selectedCategory.toLowerCase().replace(/\s+/g, '-');
      parts.push(`cat-${categorySlug}`);
    }

    // Add account info if single account selected
    if (this.reportFilters.bankAccountIds && this.reportFilters.bankAccountIds.length === 1) {
      parts.push('single-account');
    } else if (this.reportFilters.bankAccountIds && this.reportFilters.bankAccountIds.length > 1) {
      parts.push(`${this.reportFilters.bankAccountIds.length}-accounts`);
    }

    // Determine file extension based on export format
    let extension = 'xlsx';
    if (this.exportFormat === 'pdf') {
      extension = 'pdf';
    } else if (this.exportFormat === 'csv') {
      extension = 'csv';
    }

    return `${parts.join('_')}.${extension}`;
  }

  resetFilters(): void {
    this.selectedDatePreset = 'last30days';
    this.showCustomDateRange = false;
    this.applyDatePreset('last30days');
    this.selectedCategory = undefined;
    this.reportFilters.transactionType = undefined;
    this.reportFilters.bankAccountIds = [];
    this.reportFilters.textSearch = '';
    this.reportFilters.minAmount = undefined;
    this.reportFilters.maxAmount = undefined;
    this.showPreview = false;
    this.previewData = null;
    // Reset Date objects
    this.startDateObj = null;
    this.endDateObj = null;
  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND'
    }).format(amount);
  }

  toggleColumn(columnValue: string): void {
    const index = this.selectedColumns.indexOf(columnValue);
    if (index > -1) {
      // Remove column if already selected
      this.selectedColumns.splice(index, 1);
    } else {
      // Add column if not selected
      this.selectedColumns.push(columnValue);
    }
  }

  loadChartData(): void {
    // Call real API endpoints to get chart data
    if (this.previewData) {
      // Build filter params from current report filters
      const filterParams: TransactionFilterParams = {
        userId: this.reportFilters.userId,
        accountId: this.reportFilters.accountId,
        bankAccountId: this.reportFilters.bankAccountId,
        bankAccountIds: this.reportFilters.bankAccountIds,
        startDate: this.reportFilters.startDate,
        endDate: this.reportFilters.endDate,
        textSearch: this.reportFilters.textSearch,
        category: this.selectedCategory,
        transactionType: this.reportFilters.transactionType,
        minAmount: this.reportFilters.minAmount,
        maxAmount: this.reportFilters.maxAmount
      };

      // Load category summary chart
      this.transactionService.getCategorySummary(filterParams).subscribe({
        next: (categoryData) => {
          this.initializeCategoryChart(categoryData.items);
        },
        error: (error) => {
          console.error('Error loading category chart data:', error);
        }
      });

      // Load daily trend chart
      this.transactionService.getDailyTrend(filterParams).subscribe({
        next: (trendData) => {
          this.initializeTrendChart(trendData);
        },
        error: (error) => {
          console.error('Error loading trend chart data:', error);
        }
      });

      this.showCharts = true;
    }
  }

  initializeCategoryChart(categories: any[]): void {
    const categoryNames = categories.map(c => c.category || 'Uncategorized');
    const categoryAmounts = categories.map(c => Math.abs(c.amount || c.totalAmount || 0));
    const isDark = this.themeService.isDark();

    this.categoryChartOptions = {
      series: categoryAmounts,
      chart: {
        type: 'donut',
        height: 320,
        background: 'transparent',
        foreColor: isDark ? '#94a3b8' : '#64748B'
      },
      theme: { mode: isDark ? 'dark' : 'light' },
      labels: categoryNames,
      colors: ['#FF6B6B', '#4ECDC4', '#45B7D1', '#FFA07A', '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E2'],
      plotOptions: {
        pie: {
          donut: {
            size: '65%',
            labels: {
              show: true,
              total: {
                show: true,
                label: 'Total',
                formatter: () => {
                  return this.formatCurrency(categoryAmounts.reduce((a, b) => a + b, 0));
                }
              }
            }
          }
        }
      },
      legend: {
        position: 'bottom',
        labels: {
          colors: isDark ? '#e9edf5' : '#334155'
        }
      },
      dataLabels: {
        enabled: true,
        formatter: (val: number) => {
          return val.toFixed(1) + '%';
        },
        style: {
          colors: ['#ffffff']
        }
      },
      tooltip: {
        theme: isDark ? 'dark' : 'light',
        y: {
          formatter: (val: number) => {
            return this.formatCurrency(val);
          }
        }
      }
    };
  }

  initializeTrendChart(dailyData: any[]): void {
    const dates = dailyData.map(d => d.date);
    const incomeData = dailyData.map(d => d.income || 0);
    const expenseData = dailyData.map(d => Math.abs(d.expense || 0));
    const isDark = this.themeService.isDark();
    const chartText = isDark ? '#9fb2d4' : '#64748B';

    this.trendChartOptions = {
      series: [
        {
          name: 'Income',
          data: incomeData
        },
        {
          name: 'Expense',
          data: expenseData
        }
      ],
      chart: {
        type: 'area',
        height: 320,
        background: 'transparent',
        foreColor: chartText,
        toolbar: {
          show: false
        }
      },
      theme: { mode: isDark ? 'dark' : 'light' },
      colors: ['#4ECDC4', '#FF6B6B'],
      dataLabels: {
        enabled: false
      },
      stroke: {
        curve: 'smooth',
        width: 2
      },
      xaxis: {
        categories: dates,
        labels: {
          style: {
            colors: chartText
          }
        }
      },
      yaxis: {
        labels: {
          style: {
            colors: chartText
          },
          formatter: (val: number) => {
            return (val / 1000000).toFixed(1) + 'M';
          }
        }
      },
      legend: {
        position: 'top',
        labels: {
          colors: isDark ? '#e9edf5' : '#334155'
        }
      },
      tooltip: {
        theme: isDark ? 'dark' : 'light',
        y: {
          formatter: (val: number) => {
            return this.formatCurrency(val);
          }
        }
      },
      fill: {
        type: 'gradient',
        gradient: {
          shadeIntensity: 1,
          opacityFrom: 0.7,
          opacityTo: 0.2
        }
      }
    };
  }

  // Scheduled Reports Methods (Phase 3)

  loadSchedules(): void {
    this.loadingSchedules = true;
    this.transactionService.getReportSchedules(this.userId).subscribe({
      next: (schedules: any[]) => {
        this.savedSchedules = schedules;
        this.loadingSchedules = false;
      },
      error: (error: any) => {
        console.error('Error loading schedules:', error);
        this.loadingSchedules = false;
      }
    });
  }

  openScheduleDialog(): void {
    if (!this.reportFilters.startDate || !this.reportFilters.endDate) {
      this.toastService.showError('Please select a date range first', 3000);
      return;
    }

    if (this.selectedColumns.length === 0) {
      this.toastService.showError('Please select at least one column to export', 3000);
      return;
    }

    this.scheduleEmail = '';
    this.scheduleFrequency = 'weekly';
    this.scheduleHour = 9; // Reset to default 9 AM
    // Phase 4: Reset cron fields
    this.scheduleUseCron = false;
    this.scheduleCronExpression = '';
    this.isCronValid = false;
    // Phase 5: Reset notification fields
    this.scheduleWebNotificationEnabled = true;
    this.scheduleNotifyOnSuccess = true;
    this.scheduleNotifyOnFailure = true;
    this.showScheduleDialog = true;
  }

  cancelScheduleDialog(): void {
    this.showScheduleDialog = false;
    this.scheduleEmail = '';
    this.scheduleHour = 9; // Reset to default
    // Phase 4: Reset cron fields
    this.scheduleUseCron = false;
    this.scheduleCronExpression = '';
    this.isCronValid = false;
  }

  saveSchedule(): void {
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!this.scheduleEmail.trim() || !emailPattern.test(this.scheduleEmail)) {
      this.toastService.showError('Please enter a valid email address', 3000);
      return;
    }

    // Phase 4: Validate cron if using cron mode
    if (this.scheduleUseCron && !this.isCronValid) {
      this.toastService.showError('Please enter a valid cron expression', 3000);
      return;
    }

    this.isSavingSchedule = true;

    const schedule = {
      userId: this.userId,
      email: this.scheduleEmail.trim(),
      frequency: this.scheduleUseCron ? undefined : this.scheduleFrequency,
      hour: this.scheduleUseCron ? undefined : this.scheduleHour,
      cronExpression: this.scheduleUseCron ? this.scheduleCronExpression : undefined, // Phase 4
      // Phase 5: Web Notification fields
      webNotificationEnabled: this.scheduleWebNotificationEnabled,
      notifyOnSuccess: this.scheduleWebNotificationEnabled ? this.scheduleNotifyOnSuccess : undefined,
      notifyOnFailure: this.scheduleWebNotificationEnabled ? this.scheduleNotifyOnFailure : undefined,
      filters: {
        ...this.reportFilters,
        category: this.selectedCategory,
        sortOrder: this.sortOrder,
        includeSummarySheet: this.includeSummarySheet,
        selectedColumns: this.selectedColumns,
        exportFormat: this.exportFormat
      },
      active: true
    };

    this.transactionService.saveReportSchedule(schedule).subscribe({
      next: (savedSchedule: any) => {
        this.savedSchedules.push(savedSchedule);
        this.toastService.showSuccess(`Schedule created! Reports will be sent ${this.scheduleFrequency} at ${this.scheduleHour}:00 to ${this.scheduleEmail}`, 4000);
        this.showScheduleDialog = false;
        this.scheduleEmail = '';
        this.scheduleHour = 9;
        this.isSavingSchedule = false;
      },
      error: (error: any) => {
        console.error('Error saving schedule:', error);
        this.toastService.showError('Failed to create schedule', 3000);
        this.isSavingSchedule = false;
      }
    });
  }

  // Phase 4: Cron builder event handlers
  onCronChange(cronExpression: string): void {
    this.scheduleCronExpression = cronExpression;
  }

  onCronValidChange(isValid: boolean): void {
    this.isCronValid = isValid;
  }

  deleteSchedule(schedule: any): void {
    this.scheduleToDelete = schedule;
    this.showDeleteScheduleDialog = true;
  }

  cancelDeleteSchedule(): void {
    this.showDeleteScheduleDialog = false;
    this.scheduleToDelete = null;
  }

  confirmDeleteSchedule(): void {
    if (!this.scheduleToDelete) {
      return;
    }

    const scheduleId = this.scheduleToDelete.id;
    this.transactionService.deleteReportSchedule(scheduleId, this.userId).subscribe({
      next: () => {
        this.savedSchedules = this.savedSchedules.filter(s => s.id !== scheduleId);
        this.toastService.showSuccess('Schedule deleted successfully!', 3000);
        this.cancelDeleteSchedule();
      },
      error: (error: any) => {
        console.error('Error deleting schedule:', error);
        this.toastService.showError('Failed to delete schedule', 3000);
        this.cancelDeleteSchedule();
      }
    });
  }

  toggleScheduleStatus(schedule: any): void {
    const updatedSchedule = { ...schedule, active: !schedule.active };

    this.transactionService.updateReportSchedule(schedule.id, updatedSchedule).subscribe({
      next: () => {
        schedule.active = !schedule.active;
        const status = schedule.active ? 'activated' : 'paused';
        this.toastService.showSuccess(`Schedule ${status} successfully!`, 3000);
      },
      error: (error: any) => {
        console.error('Error updating schedule:', error);
        this.toastService.showError('Failed to update schedule', 3000);
      }
    });
  }

  // View schedule details
  viewScheduleDetails(schedule: any): void {
    this.selectedSchedule = schedule;
    this.showScheduleDetailsDialog = true;
  }

  closeScheduleDetails(): void {
    this.showScheduleDetailsDialog = false;
    this.selectedSchedule = null;
  }

  // Get schedule time display string
  getScheduleTimeDisplay(schedule: any): string {
    if (schedule.cronExpression) {
      return 'Custom schedule';
    }

    const hour = schedule.hour !== undefined ? schedule.hour : 9;
    return `at ${String(hour).padStart(2, '0')}:00`;
  }

  // Get schedule frequency label
  getScheduleFrequencyLabel(schedule: any): string {
    if (schedule.cronExpression) {
      return 'Custom (Cron)';
    }

    const freq = schedule.frequency || 'daily';
    return freq.charAt(0).toUpperCase() + freq.slice(1);
  }

  // Report History Methods (Phase 5)

  toggleHistorySection(): void {
    this.showHistorySection = !this.showHistorySection;
    if (this.showHistorySection && this.reportHistory.length === 0) {
      this.loadReportHistory();
    }
  }

  loadReportHistory(): void {
    this.loadingHistory = true;
    this.transactionService.getReportHistory(this.userId, this.historyPage, this.historySize).subscribe({
      next: (response: any) => {
        this.reportHistory = response.content || [];
        this.historyTotalElements = response.totalElements || 0;
        this.historyTotalPages = response.totalPages || 0;
        this.loadingHistory = false;
      },
      error: (error: any) => {
        console.error('Error loading report history:', error);
        this.toastService.showError('Failed to load report history', 3000);
        this.loadingHistory = false;
      }
    });
  }

  changeHistoryPage(page: number): void {
    if (page < 0 || page >= this.historyTotalPages) {
      return;
    }
    this.historyPage = page;
    // Clear selection when changing pages
    this.selectedHistoryIds.clear();
    this.selectAllHistory = false;
    this.loadReportHistory();
  }

  deleteReportHistory(history: any): void {
    this.historyToDelete = history;
    this.showDeleteHistoryDialog = true;
  }

  cancelDeleteHistory(): void {
    this.showDeleteHistoryDialog = false;
    this.historyToDelete = null;
  }

  confirmDeleteHistory(): void {
    if (!this.historyToDelete) {
      return;
    }

    const historyId = this.historyToDelete.id;
    this.transactionService.deleteReportHistory(historyId, this.userId).subscribe({
      next: () => {
        this.reportHistory = this.reportHistory.filter(h => h.id !== historyId);
        this.historyTotalElements--;
        this.toastService.showSuccess('Report history deleted successfully!', 3000);
        this.cancelDeleteHistory();

        // Reload if current page is now empty and not the first page
        if (this.reportHistory.length === 0 && this.historyPage > 0) {
          this.historyPage--;
          this.loadReportHistory();
        }
      },
      error: (error: any) => {
        console.error('Error deleting report history:', error);
        this.toastService.showError('Failed to delete report history', 3000);
        this.cancelDeleteHistory();
      }
    });
  }

  // Bulk selection methods
  toggleSelectAllHistory(): void {
    this.selectAllHistory = !this.selectAllHistory;
    if (this.selectAllHistory) {
      this.reportHistory.forEach(h => this.selectedHistoryIds.add(h.id));
    } else {
      this.selectedHistoryIds.clear();
    }
  }

  toggleHistorySelection(historyId: string): void {
    if (this.selectedHistoryIds.has(historyId)) {
      this.selectedHistoryIds.delete(historyId);
      this.selectAllHistory = false;
    } else {
      this.selectedHistoryIds.add(historyId);
      this.selectAllHistory = this.selectedHistoryIds.size === this.reportHistory.length;
    }
  }

  isHistorySelected(historyId: string): boolean {
    return this.selectedHistoryIds.has(historyId);
  }

  deleteSelectedHistory(): void {
    if (this.selectedHistoryIds.size === 0) {
      this.toastService.showError('Please select at least one record to delete', 3000);
      return;
    }

    this.showBulkDeleteDialog = true;
  }

  cancelBulkDelete(): void {
    this.showBulkDeleteDialog = false;
  }

  confirmBulkDelete(): void {
    const historyIds = Array.from(this.selectedHistoryIds);
    const selectedCount = historyIds.length;

    this.transactionService.bulkDeleteReportHistory(historyIds, this.userId).subscribe({
      next: (result) => {
        this.toastService.showSuccess(`Successfully deleted ${result.deletedCount} record(s)!`, 3000);

        if (result.failedCount > 0) {
          this.toastService.showError(`Failed to delete ${result.failedCount} record(s)`, 3000);
        }

        this.selectedHistoryIds.clear();
        this.selectAllHistory = false;
        this.showBulkDeleteDialog = false;
        this.loadReportHistory();
      },
      error: (error) => {
        console.error('Error deleting selected history:', error);
        this.toastService.showError('Failed to delete records', 3000);
        this.showBulkDeleteDialog = false;
        this.loadReportHistory(); // Reload to show actual state
      }
    });
  }

  formatDateTime(dateTime: string): string {
    if (!dateTime) return '';
    const date = new Date(dateTime);
    return date.toLocaleString('vi-VN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  getStatusBadgeClass(status: string): string {
    return status === 'success' ? 'status-success' : 'status-failed';
  }

  getStatusIcon(status: string): string {
    return status === 'success' ? 'fa-check-circle' : 'fa-exclamation-circle';
  }
}
