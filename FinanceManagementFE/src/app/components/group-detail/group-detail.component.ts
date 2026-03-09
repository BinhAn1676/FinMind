import { Component, Input, OnInit, OnDestroy, OnChanges, SimpleChanges, Output, EventEmitter, ViewChild, HostListener } from '@angular/core';
import { GroupService, GroupDetail, GroupAccount, GroupActivity, GroupMember, GroupInvite, PageResponse } from '../../services/group.service';
import { LanguageService } from '../../services/language.service';
import { FileService } from '../../services/file.service';
import { ToastService } from '../../services/toast.service';
import { UserService } from '../../services/user.service';
import { TransactionService, Transaction, CategoryBreakdown, CategoryItem } from '../../services/transaction.service';
import { PlanningBudgetService, PlanningBudget } from '../../services/planning-budget.service';
import { GroupPlanningService, GroupPlanning } from '../../services/group-planning.service';
import { AccountService } from '../../services/account.service';
import { CategoryService } from '../../services/category.service';
import { User } from '../../model/user.model';
import { Subscription, forkJoin } from 'rxjs';
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

type TabType = 'overview' | 'accounts' | 'transactions' | 'plan' | 'members' | 'analysis' | 'activity' | 'chat';

export type PieChartOptions = {
  series: ApexNonAxisChartSeries;
  chart: ApexChart;
  labels: string[];
  colors: string[];
  responsive: ApexResponsive[];
  theme: ApexTheme;
  legend: ApexLegend;
  dataLabels: ApexDataLabels;
  tooltip: ApexTooltip;
};

@Component({
  selector: 'app-group-detail',
  templateUrl: './group-detail.component.html',
  styleUrls: ['./group-detail.component.css']
})
export class GroupDetailComponent implements OnInit, OnDestroy, OnChanges {
  @Input() groupId!: number;
  @Input() initialTab: string | null = null;
  @Output() deleted = new EventEmitter<number>();

  group: GroupDetail | null = null;
  loading = false;
  avatarUrl: string | null = null;
  activeTab: TabType = 'overview';
  currentUser: User | null = null;
  isAdmin = false;

  // Time filter for overview data
  overviewStartDate: string = '';
  overviewEndDate: string = '';
  overviewPreset: string = 'thisMonth';
  showOverviewPresetMenu = false;
  showUpdateModal = false;
  showAvatarView = false;
  showInviteModal = false;
  showDeleteModal = false;
  deleteInProgress = false;
  showLeaveModal = false;
  leaveInProgress = false;
  showEditMemberRoleModal = false;
  showDeleteMemberModal = false;
  selectedMember: GroupMember | null = null;
  selectedMemberRole: string = '';
  updatingMemberRole = false;
  removingMember = false;
  accountSearchTerm = '';
  availableAccounts: any[] = [];
  availableAccountsLoading = false;
  recentTransactions: Transaction[] = [];
  groupPlans: GroupPlanning[] = [];
  groupPlansOverview: GroupPlanning[] = []; // 2 kế hoạch gần nhất cho overview
  groupPlansFull: GroupPlanning[] = []; // Tất cả kế hoạch cho tab
  loadingGroupPlans = false;
  groupPlanningSummary: any = null;
  groupBankAccounts: GroupAccount[] = [];

  // Transactions tab
  groupTransactions: Transaction[] = [];
  loadingGroupTransactions = false;
  syncingGroupTransactions = false;
  groupTransactionSummary = {
    totalIncome: 0,
    totalExpense: 0,
    netAmount: 0,
    transactionCount: 0,
    averageAmount: 0
  };
  loadingGroupTransactionSummary = false;
  transactionSearchTerm = '';
  transactionType: 'income' | 'expense' | '' = '';
  transactionStartDate: string = '';
  transactionEndDate: string = '';
  selectedTransactionAccountId: string = '';
  transactionAmountRange: string = 'all';
  transactionCurrentPage = 0;
  transactionPageSize = 10;
  transactionTotalElements = 0;
  transactionTotalPages = 0;
  transactionPageSizeOptions = [10, 20, 50, 100];
  showTransactionDetail = false;
  transactionDetail: Transaction | null = null;
  loadingTransactionDetail = false;
  showCategoryModal = false;
  selectedTransactionForCategory: Transaction | null = null;
  selectedCategory = '';
  newCategory = '';
  showNewCategoryInput = false;
  categories: any[] = [];
  loadingCategories = false;
  groupSummary = {
    totalBalance: 0,
    totalTransactions: 0,
    totalIncome: 0,
    totalExpense: 0,
    monthBalance: 0
  };
  recentActivities: GroupActivity[] = [];
  // Activity tab state
  activities: GroupActivity[] = [];
  loadingActivities = false;
  activitySearchTerm = '';
  activityType = '';
  activityStartDate = '';
  activityEndDate = '';
  activityPage = 0;
  activityPageSize = 10;
  activityTotalElements = 0;
  activityTotalPages = 0;
  activityPageSizeOptions = [10, 20, 50, 100];
  activityGroups: { dateLabel: string; items: GroupActivity[] }[] = [];
  categoryStats: Array<{ category: string; amount: number }> = [];
  monthlyTrend: {
    currentMonth: { total: number; income: number; expense: number };
    lastMonth: { total: number; income: number; expense: number };
    changePercentage: number;
  } | null = null;
  activeCategoryIndex: number | null = null;
  activeExpenseCategoryIndex: number | null = null;
  activeIncomeCategoryIndex: number | null = null;
  categoryColors = ['#60a5fa', '#c084fc', '#f97316', '#34d399', '#f87171'];

  // ApexCharts for category breakdown
  @ViewChild('expenseChart') expenseChart?: ChartComponent;
  @ViewChild('incomeChart') incomeChart?: ChartComponent;
  expenseChartOptions: Partial<PieChartOptions> = {};
  incomeChartOptions: Partial<PieChartOptions> = {};
  expenseBreakdown: CategoryItem[] = [];
  incomeBreakdown: CategoryItem[] = [];
  expenseTotal = 0;
  incomeTotal = 0;
  loadingExpenseBreakdown = false;
  loadingIncomeBreakdown = false;
  Math = Math; // Expose Math to template
  tabs = [
    { id: 'overview' as TabType, label: 'groups.detail.tabs.overview' },
    { id: 'accounts' as TabType, label: 'groups.detail.tabs.accounts' },
    { id: 'transactions' as TabType, label: 'groups.detail.tabs.transactions' },
    { id: 'plan' as TabType, label: 'groups.detail.tabs.plan' },
    { id: 'members' as TabType, label: 'groups.detail.tabs.members' },
    { id: 'analysis' as TabType, label: 'groups.detail.tabs.analysis' },
    { id: 'activity' as TabType, label: 'groups.detail.tabs.activity' },
    { id: 'chat' as TabType, label: 'groups.detail.tabs.chat' }
  ];
  private subscriptions = new Subscription();

  // Members tab state
  members: GroupMember[] = [];
  loadingMembers = false;
  membersPage = 0;
  membersPageSize = 10;
  membersTotalElements = 0;
  membersTotalPages = 0;
  memberPageSizeOptions = [10, 20, 50, 100];

  // Invitations state
  pendingInvites: GroupInvite[] = [];
  loadingInvites = false;
  invitePage = 0;
  invitePageSize = 10;
  inviteTotalElements = 0;
  inviteTotalPages = 0;

  // ===== ANALYSIS TAB STATE =====
  @ViewChild('analysisBreakdownChart') analysisBreakdownChart?: ChartComponent;
  
  // Time filter for analysis
  analysisTimeFilter = 'thisMonth';
  analysisTimeFilters = [
    { value: 'thisMonth', label: 'Tháng này' },
    { value: 'lastMonth', label: 'Tháng trước' },
    { value: 'thisQuarter', label: 'Quý này' },
    { value: 'lastQuarter', label: 'Quý trước' },
    { value: 'custom', label: 'Tùy chọn' }
  ];
  analysisCustomStartDate = '';
  analysisCustomEndDate = '';
  
  // Summary cards
  analysisSummaries: Array<{
    title: string;
    value: string;
    subLabel: string;
    trend: string;
    trendDirection: 'up' | 'down' | 'flat';
  }> = [];
  loadingAnalysisSummary = false;
  
  // Cashflow chart
  analysisCashflow: Array<{ year: number; month: number; totalIncome: number; totalExpense: number; balance: number }> = [];
  loadingAnalysisCashflow = false;
  
  // Category breakdown
  analysisBreakdownMode: 'expense' | 'income' = 'expense';
  analysisExpenseBreakdown: CategoryItem[] = [];
  analysisIncomeBreakdown: CategoryItem[] = [];
  analysisExpenseTotal = 0;
  analysisIncomeTotal = 0;
  loadingAnalysisBreakdown = false;
  analysisChartOptions: Partial<PieChartOptions> = {};
  analysisActiveCategoryIndex: number | null = null;
  analysisBreakdownColors = ['#ff7043', '#4fc3f7', '#ba68c8', '#81c784', '#f6c344', '#5dd6c9', '#ef6c9c', '#9aa5b1'];
  
  // Heatmap
  analysisHeatmapMonth: number = new Date().getMonth() + 1;
  analysisHeatmapYear: number = new Date().getFullYear();
  analysisAvailableYears: number[] = [];
  analysisWeekDays = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
  analysisCalendarDays: Array<{ day: number | null; level: number }> = [];
  loadingAnalysisHeatmap = false;
  
  // Biggest expenses
  analysisBigSpends: Array<{
    label: string;
    amount: string;
    icon: string;
    date: string;
    category: string;
  }> = [];
  loadingAnalysisBigSpends = false;
  
  // Spending variance
  analysisVarianceMode: 'expense' | 'income' = 'expense';
  analysisSpendingChanges: Array<{
    label: string;
    actual: string;
    delta: string;
    deltaPct: string;
    trend: 'up' | 'down' | 'flat';
    icon: string;
  }> = [];
  loadingAnalysisVariance = false;

  constructor(
    private groupService: GroupService,
    private languageService: LanguageService,
    private fileService: FileService,
    private toastService: ToastService,
    private userService: UserService,
    private transactionService: TransactionService,
    private planningBudgetService: PlanningBudgetService,
    private groupPlanningService: GroupPlanningService,
    private accountService: AccountService,
    private categoryService: CategoryService
  ) {}

  // Getter for overview preset label
  get overviewPresetLabel(): string {
    const map: any = {
      today: this.translate('overview.today') || 'Hôm nay',
      yesterday: this.translate('overview.yesterday') || 'Hôm qua',
      last7: this.translate('overview.last7') || '7 ngày qua',
      last30: this.translate('overview.last30') || '30 ngày qua',
      thisMonth: this.translate('overview.thisMonth') || 'Tháng này',
      lastMonth: this.translate('overview.lastMonth') || 'Tháng trước',
      thisQuarter: this.translate('overview.thisQuarter') || 'Quý này',
      lastQuarter: this.translate('overview.lastQuarter') || 'Quý trước',
      thisYear: this.translate('overview.thisYear') || 'Năm nay',
      lastYear: this.translate('overview.lastYear') || 'Năm trước'
    };
    return map[this.overviewPreset] || map['thisMonth'];
  }

  // Get label for current selected period (used in Monthly Trend)
  get selectedPeriodLabel(): string {
    return this.overviewPresetLabel;
  }

  // Get label for previous period (used in Monthly Trend comparison)
  get previousPeriodLabel(): string {
    const previousMap: any = {
      today: this.translate('overview.yesterday') || 'Hôm qua',
      yesterday: this.translate('groups.detail.overview.dayBefore') || 'Hôm kia',
      last7: this.translate('groups.detail.overview.previous7Days') || '7 ngày trước đó',
      last30: this.translate('groups.detail.overview.previous30Days') || '30 ngày trước đó',
      thisMonth: this.translate('overview.lastMonth') || 'Tháng trước',
      lastMonth: this.translate('groups.detail.overview.monthBefore') || 'Tháng trước nữa',
      thisQuarter: this.translate('overview.lastQuarter') || 'Quý trước',
      lastQuarter: this.translate('groups.detail.overview.quarterBefore') || 'Quý trước nữa',
      thisYear: this.translate('overview.lastYear') || 'Năm trước',
      lastYear: this.translate('groups.detail.overview.yearBefore') || 'Năm trước nữa'
    };
    return previousMap[this.overviewPreset] || previousMap['thisMonth'];
  }

  // Get the trend comparison subtitle based on preset
  get trendSubtitle(): string {
    return `${this.selectedPeriodLabel} vs ${this.previousPeriodLabel}`;
  }

  // Apply preset for overview time filter
  applyOverviewPreset(p: string): void {
    this.overviewPreset = p;
    const today = new Date();
    const pad = (n: number) => (n < 10 ? '0' + n : '' + n);
    const toStr = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

    if (p === 'today') {
      this.overviewStartDate = toStr(today);
      this.overviewEndDate = toStr(today);
    } else if (p === 'thisMonth') {
      const first = new Date(today.getFullYear(), today.getMonth(), 1);
      const last = new Date(today.getFullYear(), today.getMonth() + 1, 0);
      this.overviewStartDate = toStr(first);
      this.overviewEndDate = toStr(last);
    } else if (p === 'yesterday') {
      const y = new Date(today);
      y.setDate(today.getDate() - 1);
      this.overviewStartDate = toStr(y);
      this.overviewEndDate = toStr(y);
    } else if (p === 'last7') {
      const start = new Date(today);
      start.setDate(today.getDate() - 6);
      this.overviewStartDate = toStr(start);
      this.overviewEndDate = toStr(today);
    } else if (p === 'last30') {
      const start = new Date(today);
      start.setDate(today.getDate() - 29);
      this.overviewStartDate = toStr(start);
      this.overviewEndDate = toStr(today);
    } else if (p === 'lastMonth') {
      const first = new Date(today.getFullYear(), today.getMonth() - 1, 1);
      const last = new Date(today.getFullYear(), today.getMonth(), 0);
      this.overviewStartDate = toStr(first);
      this.overviewEndDate = toStr(last);
    } else if (p === 'thisQuarter' || p === 'lastQuarter') {
      const q = Math.floor(today.getMonth() / 3) + 1;
      const tq = p === 'thisQuarter' ? q : q - 1;
      const year = p === 'thisQuarter' ? today.getFullYear() : (tq === 0 ? today.getFullYear() - 1 : today.getFullYear());
      const quarter = tq === 0 ? 4 : tq;
      const startMonth = (quarter - 1) * 3;
      const first = new Date(year, startMonth, 1);
      const last = new Date(year, startMonth + 3, 0);
      this.overviewStartDate = toStr(first);
      this.overviewEndDate = toStr(last);
    } else if (p === 'thisYear' || p === 'lastYear') {
      const year = p === 'thisYear' ? today.getFullYear() : today.getFullYear() - 1;
      const first = new Date(year, 0, 1);
      const last = new Date(year, 12, 0);
      this.overviewStartDate = toStr(first);
      this.overviewEndDate = toStr(last);
    }
  }

  // Called when overview date filter changes
  onOverviewDateChange(): void {
    this.refreshOverviewData();
  }

  // Close preset menu when clicking outside
  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    this.showOverviewPresetMenu = false;
  }

  // Refresh overview data with current date filter
  refreshOverviewData(): void {
    if (!this.group || !this.group.members || this.group.members.length === 0) {
      return;
    }
    const memberUserIds = this.group.members.map(m => m.userId.toString());
    this.loadRecentTransactions(memberUserIds);
    this.loadGroupPlansWithDateFilter(memberUserIds);
  }

  ngOnInit(): void {
    // Initialize default date filter to this month
    this.applyOverviewPreset('thisMonth');
    // Initialize analysis tab
    this.initAnalysisTab();
    this.loadCurrentUser();
    
    // Set initial tab if provided
    if (this.initialTab && this.tabs.some(t => t.id === this.initialTab)) {
      this.activeTab = this.initialTab as TabType;
    }
    
    if (this.groupId) {
      this.loadGroupDetail();
    }
  }

  loadCurrentUser(): void {
    this.userService.getUserInfo().subscribe({
      next: (user) => {
        this.currentUser = user;
        // Reload overview data if group is already loaded
        if (this.group) {
          this.loadOverviewData();
        }
      },
      error: (error) => {
        console.error('Error loading current user:', error);
      }
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    // Handle initialTab change
    if (changes['initialTab'] && this.initialTab && this.tabs.some(t => t.id === this.initialTab)) {
      this.activeTab = this.initialTab as TabType;
    }
    
    if (changes['groupId'] && !changes['groupId'].firstChange && this.groupId) {
      // Khi đổi group: reset tab về Tổng quan hoặc initialTab nếu có
      this.activeTab = (this.initialTab as TabType) || 'overview';

      // Reset accounts & overview-related caches
      this.availableAccounts = [];
      this.groupBankAccounts = [];

      // Reset transactions tab state
      this.groupTransactions = [];
      this.transactionSearchTerm = '';
      this.transactionType = '';
      this.transactionStartDate = '';
      this.transactionEndDate = '';
      this.selectedTransactionAccountId = '';
      this.transactionAmountRange = 'all';
      this.transactionCurrentPage = 0;
      this.transactionTotalElements = 0;
      this.transactionTotalPages = 0;

      // Reset activity tab state
      this.activities = [];
      this.activityGroups = [];
      this.activitySearchTerm = '';
      this.activityType = '';
      this.activityStartDate = '';
      this.activityEndDate = '';
      this.activityPage = 0;
      this.activityTotalElements = 0;
      this.activityTotalPages = 0;

      this.loadGroupDetail();
    }
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  loadGroupDetail(): void {
    this.loading = true;
    this.groupService.getById(this.groupId).subscribe({
      next: (group) => {
        this.group = group;
        this.checkAdminRole();
        this.loadAvatar();
        this.loadOverviewData();
        // Khi tab hiện tại là members thì load luôn danh sách
        if (this.activeTab === 'members') {
          this.loadMembers();
          this.loadPendingInvites();
        }
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading group detail:', error);
        this.toastService.showError(this.translate('groups.detail.loadError'));
        this.loading = false;
      }
    });
  }

  loadOverviewData(): void {
    if (!this.group || !this.group.members || this.group.members.length === 0) {
      // Reset data if no members
      this.recentTransactions = [];
      this.groupSummary = {
        totalBalance: 0,
        totalTransactions: 0,
        totalIncome: 0,
        totalExpense: 0,
        monthBalance: 0
      };
      this.monthlyTrend = null;
      this.categoryStats = [];
      this.recentActivities = [];
      return;
    }

    // Get all user IDs from group members
    const memberUserIds = this.group.members.map(m => m.userId.toString());

    // Load bank accounts linked to group first (this will trigger loadRecentTransactions)
    this.loadGroupAccounts();

    // Load plans for all members
    this.loadGroupPlans(memberUserIds);

    // Load recent activities
    this.loadRecentActivities();

    // Load categories for transactions tab
    this.loadGroupCategories();
  }

  loadRecentTransactions(userIds: string[]): void {
    // Load transactions from all accounts linked to the group
    if (this.groupBankAccounts.length === 0) {
      this.recentTransactions = [];
      // Reset summary and trend when no accounts
      this.groupSummary = {
        totalBalance: 0,
        totalTransactions: 0,
        totalIncome: 0,
        totalExpense: 0,
        monthBalance: 0
      };
      this.monthlyTrend = null;
      this.categoryStats = [];
      this.expenseBreakdown = [];
      this.incomeBreakdown = [];
      this.expenseTotal = 0;
      this.incomeTotal = 0;
      this.updateExpenseChart();
      this.updateIncomeChart();
      return;
    }

    // Use selected date range from overview filter (default to this month if not set)
    const startDate = this.overviewStartDate;
    const endDate = this.overviewEndDate;

    // Calculate previous period for trend comparison (same duration before startDate)
    const start = new Date(startDate);
    const end = new Date(endDate);
    const durationMs = end.getTime() - start.getTime();
    const previousEnd = new Date(start.getTime() - 1); // 1ms before start
    const previousStart = new Date(previousEnd.getTime() - durationMs);
    const pad = (n: number) => (n < 10 ? '0' + n : '' + n);
    const toStr = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    const previousStartDate = toStr(previousStart);
    const previousEndDate = toStr(previousEnd);

    // Get all bank account IDs (use bankAccountId if available, otherwise fallback to accountId)
    const bankAccountIds = this.groupBankAccounts
      .map(account => account.bankAccountId || account.accountId.toString())
      .filter(id => id != null && id !== '');

    // Load transactions for selected period for all group accounts in one call
    this.transactionService.filterTransactions({
      bankAccountIds: bankAccountIds,
      startDate: startDate,
      endDate: endDate,
      page: 0,
      size: 100 // Get enough to find top 7
    }).subscribe({
      next: (response: any) => {
        const allTransactions = response.content || response || [];
        // Sort by date descending
        allTransactions.sort((a: Transaction, b: Transaction) => {
          const dateA = new Date(a.transactionDate || 0).getTime();
          const dateB = new Date(b.transactionDate || 0).getTime();
          return dateB - dateA;
        });

        // Take only 7 most recent for display
        this.recentTransactions = allTransactions.slice(0, 7);

        // Calculate summary
        this.calculateGroupSummary(allTransactions);

        // Load category breakdown from API
        this.loadCategoryBreakdown(bankAccountIds, startDate, endDate);

        // Load previous period transactions for trend comparison
        this.transactionService.filterTransactions({
          bankAccountIds: bankAccountIds,
          startDate: previousStartDate,
          endDate: previousEndDate,
          page: 0,
          size: 1000
        }).subscribe({
          next: (lastMonthResponse: any) => {
            const lastMonthTransactions = lastMonthResponse.content || lastMonthResponse || [];
            this.calculateMonthlyTrend(allTransactions, lastMonthTransactions);
          },
          error: (error) => {
            console.error('Error loading last month transactions:', error);
            this.monthlyTrend = null;
          }
        });
      },
      error: (error) => {
        console.error('Error loading recent transactions:', error);
        this.recentTransactions = [];
      }
    });
  }

  calculateGroupSummary(transactions: Transaction[]): void {
    let totalIncome = 0;
    let totalExpense = 0;

    transactions.forEach((tx: Transaction) => {
      if (tx.amountIn && tx.amountIn > 0) {
        totalIncome += tx.amountIn;
      }
      if (tx.amountOut && tx.amountOut > 0) {
        totalExpense += tx.amountOut;
      }
    });

    this.groupSummary = {
      totalBalance: this.calculateTotalBalance(),
      totalTransactions: transactions.length,
      totalIncome: totalIncome,
      totalExpense: totalExpense,
      monthBalance: totalIncome - totalExpense
    };
  }

  calculateTotalBalance(): number {
    // Sum all bank account balances
    if (!this.groupBankAccounts || this.groupBankAccounts.length === 0) {
      return 0;
    }
    return this.groupBankAccounts.reduce((sum, account) => {
      const balance = account.accumulated || 0;
      return sum + (typeof balance === 'string' ? parseFloat(balance) || 0 : balance);
    }, 0);
  }

  loadGroupPlans(userIds: string[]): void {
    // Load group planning (not individual plans)
    if (!this.groupId) {
      this.groupPlans = [];
      this.groupPlansOverview = [];
      return;
    }

    this.loadingGroupPlans = true;
    this.groupPlanningService.list(this.groupId).subscribe({
      next: (plans) => {
        this.groupPlansFull = plans || [];
        // Sort by created date (most recent first) and take 2 for overview
        const sorted = [...this.groupPlansFull].sort((a, b) => {
          // If no date info, keep original order
          return 0;
        });
        this.groupPlansOverview = sorted.slice(0, 2);
        this.groupPlans = this.groupPlansOverview; // For overview display
        this.loadingGroupPlans = false;

        // Auto-recalculate spent amounts if we have bank accounts
        if (this.groupBankAccounts.length > 0) {
          const bankAccountIds = this.groupBankAccounts
            .map(account => account.bankAccountId || account.accountId.toString())
            .filter(id => id);

          if (bankAccountIds.length > 0) {
            // Recalculate in background (don't wait for it)
            this.groupPlanningService.recalculateAllSpentAmounts(this.groupId, bankAccountIds).subscribe({
              next: () => {
                // Reload plans after recalculating to get updated spent amounts
                this.groupPlanningService.list(this.groupId).subscribe({
                  next: (updatedPlans) => {
                    this.groupPlansFull = updatedPlans || [];
                    const sorted = [...this.groupPlansFull].sort((a, b) => 0);
                    this.groupPlansOverview = sorted.slice(0, 2);
                    this.groupPlans = this.groupPlansOverview;
                    this.loadGroupPlanningSummary();
                  },
                  error: (err) => {
                    console.error('Error reloading plans after recalculate:', err);
                  }
                });
              },
              error: (err) => {
                console.error('Error recalculating group planning spent amounts:', err);
                // Still load summary even if recalculate fails
                this.loadGroupPlanningSummary();
              }
            });
          } else {
            this.loadGroupPlanningSummary();
          }
        } else {
          // Load summary
          this.loadGroupPlanningSummary();
        }
      },
      error: (error) => {
        console.error('Error loading group plans:', error);
        this.groupPlans = [];
        this.groupPlansOverview = [];
        this.groupPlansFull = [];
        this.loadingGroupPlans = false;
      }
    });
  }

  loadGroupPlanningSummary(): void {
    if (!this.groupId) return;
    this.groupPlanningService.getSummary(this.groupId).subscribe({
      next: (summary) => {
        this.groupPlanningSummary = summary;
      },
      error: (error) => {
        console.error('Error loading group planning summary:', error);
      }
    });
  }

  // Load group plans with date filter consideration
  loadGroupPlansWithDateFilter(userIds: string[]): void {
    if (!this.groupId) {
      this.groupPlans = [];
      this.groupPlansOverview = [];
      return;
    }

    this.loadingGroupPlans = true;
    this.groupPlanningService.list(this.groupId).subscribe({
      next: (plans) => {
        // Filter plans that have startDate before or within the selected period
        const filteredPlans = (plans || []).filter(plan => {
          if (!plan.startDate) return true; // Include plans without start date
          const planStart = new Date(plan.startDate);
          const filterEnd = new Date(this.overviewEndDate);
          return planStart <= filterEnd; // Plan started before or during the selected period
        });

        this.groupPlansFull = filteredPlans;
        const sorted = [...filteredPlans].sort((a, b) => 0);
        this.groupPlansOverview = sorted.slice(0, 2);
        this.groupPlans = this.groupPlansOverview;
        this.loadingGroupPlans = false;

        // Recalculate spent amounts with date filter if we have bank accounts
        if (this.groupBankAccounts.length > 0) {
          const bankAccountIds = this.groupBankAccounts
            .map(account => account.bankAccountId || account.accountId.toString())
            .filter(id => id);

          if (bankAccountIds.length > 0) {
            // Recalculate with date filter
            this.groupPlanningService.recalculateAllSpentAmountsWithDateRange(
              this.groupId,
              bankAccountIds,
              this.overviewStartDate,
              this.overviewEndDate
            ).subscribe({
              next: () => {
                // Reload plans after recalculating to get updated spent amounts
                this.groupPlanningService.list(this.groupId).subscribe({
                  next: (updatedPlans) => {
                    const filtered = (updatedPlans || []).filter(plan => {
                      if (!plan.startDate) return true;
                      const planStart = new Date(plan.startDate);
                      const filterEnd = new Date(this.overviewEndDate);
                      return planStart <= filterEnd;
                    });
                    this.groupPlansFull = filtered;
                    const sorted = [...filtered].sort((a, b) => 0);
                    this.groupPlansOverview = sorted.slice(0, 2);
                    this.groupPlans = this.groupPlansOverview;
                    this.loadGroupPlanningSummary();
                  },
                  error: (err) => {
                    console.error('Error reloading plans after recalculate:', err);
                  }
                });
              },
              error: (err) => {
                console.error('Error recalculating group planning spent amounts with date range:', err);
                // Fallback to normal recalculate
                this.groupPlanningService.recalculateAllSpentAmounts(this.groupId, bankAccountIds).subscribe({
                  next: () => this.loadGroupPlanningSummary(),
                  error: () => this.loadGroupPlanningSummary()
                });
              }
            });
          } else {
            this.loadGroupPlanningSummary();
          }
        } else {
          this.loadGroupPlanningSummary();
        }
      },
      error: (error) => {
        console.error('Error loading group plans with date filter:', error);
        this.groupPlans = [];
        this.groupPlansOverview = [];
        this.groupPlansFull = [];
        this.loadingGroupPlans = false;
      }
    });
  }

  loadGroupAccounts(search = ''): void {
    if (!this.groupId) {
      return;
    }
    this.groupService.getGroupAccounts(this.groupId, search, 0, 100).subscribe({
      next: (response) => {
        this.groupBankAccounts = response.content || [];
        this.groupSummary.totalBalance = this.calculateTotalBalance();
        // Reload available accounts when group changes to show all accounts again
        this.loadAvailableAccounts();
        // Reload recent transactions when accounts change
        if (this.group && this.group.members && this.group.members.length > 0) {
          const memberUserIds = this.group.members.map(m => m.userId.toString());
          // Always reload to ensure data is correct (even if accounts list is empty)
          this.loadRecentTransactions(memberUserIds);
          // Recalculate plans spent amounts now that we have bank accounts
          this.recalculatePlansSpentAmounts();
        } else {
          // If no members, reset data
          this.recentTransactions = [];
          this.groupSummary = {
            totalBalance: 0,
            totalTransactions: 0,
            totalIncome: 0,
            totalExpense: 0,
            monthBalance: 0
          };
          this.monthlyTrend = null;
          this.categoryStats = [];
        }
        // Reload transactions tab if active
        if (this.activeTab === 'transactions') {
          this.loadGroupTransactions();
        }
      },
      error: (error) => {
        console.error('Error loading group bank accounts:', error);
        this.groupBankAccounts = [];
      }
    });
  }

  /**
   * Recalculate spent amounts for all plans after bank accounts are loaded
   * This ensures RECURRING plans use correct cycle-based calculation
   */
  private recalculatePlansSpentAmounts(): void {
    if (!this.groupId || this.groupBankAccounts.length === 0) {
      return;
    }

    const bankAccountIds = this.groupBankAccounts
      .map(account => account.bankAccountId || account.accountId?.toString())
      .filter((id): id is string => !!id);

    if (bankAccountIds.length === 0) {
      return;
    }

    // Recalculate with date filter for proper cycle-based calculation
    this.groupPlanningService.recalculateAllSpentAmountsWithDateRange(
      this.groupId,
      bankAccountIds,
      this.overviewStartDate,
      this.overviewEndDate
    ).subscribe({
      next: () => {
        // Reload plans after recalculating to get updated spent amounts
        this.groupPlanningService.list(this.groupId).subscribe({
          next: (updatedPlans) => {
            const filtered = (updatedPlans || []).filter(plan => {
              if (!plan.startDate) return true;
              const planStart = new Date(plan.startDate);
              const filterEnd = new Date(this.overviewEndDate);
              return planStart <= filterEnd;
            });
            this.groupPlansFull = filtered;
            const sorted = [...filtered].sort((a, b) => 0);
            this.groupPlansOverview = sorted.slice(0, 2);
            this.groupPlans = this.groupPlansOverview;
            this.loadGroupPlanningSummary();
          },
          error: (err) => {
            console.error('Error reloading plans after recalculate:', err);
          }
        });
      },
      error: (err) => {
        console.error('Error recalculating group planning spent amounts:', err);
        // Fallback to normal recalculate
        this.groupPlanningService.recalculateAllSpentAmounts(this.groupId, bankAccountIds).subscribe({
          next: () => this.loadGroupPlanningSummary(),
          error: () => this.loadGroupPlanningSummary()
        });
      }
    });
  }

  private loadAvailableAccounts(): void {
    if (!this.currentUser || this.availableAccountsLoading) {
      return;
    }
    const userId = this.currentUser.id.toString();
    this.availableAccountsLoading = true;
    this.accountService.filter(userId, '', 0, 100).subscribe({
      next: (response: any) => {
        const accounts = response.content || response || [];
        this.availableAccounts = accounts.filter((acc: any) => !this.isAccountLinked(acc.id));
        this.availableAccountsLoading = false;
      },
      error: (error) => {
        console.error('Error loading available accounts:', error);
        this.availableAccounts = [];
        this.availableAccountsLoading = false;
      }
    });
  }

  private syncAvailableAccounts(): void {
    if (!this.availableAccounts || !this.availableAccounts.length) {
      return;
    }
    this.availableAccounts = this.availableAccounts.filter(acc => !this.isAccountLinked(acc.id));
  }

  loadRecentActivities(): void {
    if (!this.group) {
      this.recentActivities = [];
      return;
    }
    this.groupService
      .getActivities(this.group.id, { page: 0, size: 4 })
      .subscribe({
        next: (page) => {
          this.recentActivities = page.content || [];
        },
        error: (error) => {
          console.error('Error loading recent activities:', error);
          this.recentActivities = [];
        }
      });
  }

  checkAdminRole(): void {
    if (!this.group || !this.currentUser) {
      this.isAdmin = false;
      return;
    }
    const currentMember = this.group.members?.find(m => m.userId === this.currentUser!.id);
    this.isAdmin = currentMember?.role === 'ADMIN';
  }

  canManageMembers(): boolean {
    if (!this.group || !this.currentUser) {
      return false;
    }
    // Owner can always manage members
    if (this.group.ownerUserId === this.currentUser.id) {
      return true;
    }
    // Admin can manage members
    const currentMember = this.group.members?.find(m => m.userId === this.currentUser!.id);
    return currentMember?.role === 'ADMIN';
  }

  private loadAvatar(): void {
    if (this.group?.avatarFileId) {
      this.fileService.getLiveUrl(this.group.avatarFileId).subscribe({
        next: (response) => {
          this.avatarUrl = response.liveUrl;
        },
        error: (error) => {
          console.error('Error loading avatar:', error);
        }
      });
    }
  }

  selectTab(tab: TabType): void {
    this.activeTab = tab;
    if (tab === 'accounts') {
      this.ensureAccountsDataLoaded();
    }
    if (tab === 'transactions' && this.groupTransactions.length === 0) {
      this.loadGroupTransactions();
    }
    if (tab === 'activity' && this.activities.length === 0) {
      this.loadActivities();
    }
    if (tab === 'members') {
      if (this.members.length === 0) {
        this.loadMembers();
      }
      if (this.pendingInvites.length === 0) {
        this.loadPendingInvites();
      }
    }
    if (tab === 'plan') {
      if (this.groupPlansFull.length === 0 && this.groupId) {
        this.loadGroupPlans([]);
      }
    }
    if (tab === 'analysis') {
      // Load analysis data if not loaded yet
      if (this.analysisSummaries.length === 0 && this.groupBankAccounts.length > 0) {
        this.loadAnalysisData();
      } else if (this.analysisCashflow.length > 0 || this.getAnalysisCurrentBreakdown().length > 0) {
        // If data already exists, just re-render the charts (DOM was recreated by *ngIf)
        setTimeout(() => {
          this.renderAnalysisCashflowChart();
          this.updateAnalysisPieChart();
        });
      }
    }
  }

  // ===== Group Activities (tab) =====

  loadActivities(): void {
    if (!this.group) {
      this.activities = [];
      this.activityGroups = [];
      return;
    }
    this.loadingActivities = true;
    this.groupService
      .getActivities(this.group.id, {
        query: this.activitySearchTerm || undefined,
        type: this.activityType || undefined,
        from: this.activityStartDate || undefined,
        to: this.activityEndDate || undefined,
        page: this.activityPage,
        size: this.activityPageSize
      })
      .subscribe({
        next: (page) => {
          this.activities = page.content || [];
          this.activityTotalElements = page.totalElements;
          this.activityTotalPages = page.totalPages;
          this.buildActivityGroups();
          this.loadingActivities = false;
        },
        error: (error) => {
          console.error('Error loading activities:', error);
          this.activities = [];
          this.activityTotalElements = 0;
          this.activityTotalPages = 0;
          this.activityGroups = [];
          this.loadingActivities = false;
        }
      });
  }

  onActivitySearch(): void {
    this.activityPage = 0;
    this.loadActivities();
  }

  onActivityFilterChange(): void {
    this.activityPage = 0;
    this.loadActivities();
  }

  clearActivityFilters(): void {
    this.activitySearchTerm = '';
    this.activityType = '';
    this.activityStartDate = '';
    this.activityEndDate = '';
    this.activityPage = 0;
    this.loadActivities();
  }

  onActivityPageChange(page: number): void {
    if (page < 0 || page >= this.activityTotalPages) {
      return;
    }
    this.activityPage = page;
    this.loadActivities();
  }

  onActivityPageSizeChange(size: number): void {
    this.activityPageSize = size;
    this.activityPage = 0;
    this.loadActivities();
  }

  getActivityTypeLabel(type: string): string {
    if (!type) {
      return this.translate('groups.detail.activity.type.all');
    }
    const key = `groups.detail.activity.type.${type}`;
    const translated = this.translate(key);
    return translated !== key ? translated : type;
  }

  getActivityIcon(type: string): string {
    switch (type) {
      case 'GROUP_CREATED':
        return 'fa-users';
      case 'INVITE_SENT':
        return 'fa-paper-plane';
      case 'INVITE_ACCEPTED':
        return 'fa-user-plus';
      case 'INVITE_REJECTED':
        return 'fa-user-times';
      case 'ACCOUNT_LINKED':
        return 'fa-link';
      case 'ACCOUNT_UNLINKED':
        return 'fa-unlink';
      case 'PLANNING_CREATED':
        return 'fa-file-alt';
      case 'PLANNING_UPDATED':
        return 'fa-edit';
      case 'PLANNING_DELETED':
        return 'fa-trash-alt';
      case 'ADMIN_LEFT':
        return 'fa-user-shield';
      case 'MEMBER_LEFT':
        return 'fa-sign-out';
      default:
        return 'fa-list';
    }
  }

  formatActivityDate(date?: string): string {
    return date ? this.formatDate(date) : '';
  }

  formatActivityTime(dateString?: string): string {
    if (!dateString) {
      return '';
    }
    const date = new Date(dateString);
    if (isNaN(date.getTime())) {
      return '';
    }
    const hours = date.getHours().toString().padStart(2, '0');
    const minutes = date.getMinutes().toString().padStart(2, '0');
    return `${hours}:${minutes}`;
  }

  private parseActivityMetadata(activity: GroupActivity): any {
    if (!activity || !activity.metadata) {
      return {};
    }
    try {
      return JSON.parse(activity.metadata);
    } catch {
      return {};
    }
  }

  getActivityTitle(activity: GroupActivity): string {
    if (!activity) {
      return '';
    }
    const meta = this.parseActivityMetadata(activity);
    const actor = activity.actorName || '';

    switch (activity.type) {
      case 'GROUP_CREATED':
        return actor
          ? `${actor} - ${this.getActivityTypeLabel(activity.type)}`
          : this.getActivityTypeLabel(activity.type);
      case 'INVITE_SENT': {
        const invitee = meta.inviteeName || '';
        if (actor && invitee) {
          return `${actor} ${this.translate('groups.detail.activity.text.inviteSentTo')} ${invitee}`;
        }
        return actor
          ? `${actor} - ${this.getActivityTypeLabel(activity.type)}`
          : this.getActivityTypeLabel(activity.type);
      }
      case 'INVITE_ACCEPTED': {
        if (actor) {
          return `${actor} ${this.translate('groups.detail.activity.text.inviteAccepted')}`;
        }
        return this.getActivityTypeLabel(activity.type);
      }
      case 'INVITE_REJECTED': {
        if (actor) {
          return `${actor} ${this.translate('groups.detail.activity.text.inviteRejected')}`;
        }
        return this.getActivityTypeLabel(activity.type);
      }
      case 'ACCOUNT_LINKED': {
        const account = meta.accountNumber || '';
        const bank = meta.bankName || '';
        if (actor && account) {
          const target = bank ? `${account} - ${bank}` : account;
          return `${actor} ${this.translate('groups.detail.activity.text.accountLinked')} ${target}`;
        }
        return this.getActivityTypeLabel(activity.type);
      }
      case 'ACCOUNT_UNLINKED': {
        const account = meta.accountNumber || '';
        const bank = meta.bankName || '';
        if (actor && account) {
          const target = bank ? `${account} - ${bank}` : account;
          return `${actor} ${this.translate('groups.detail.activity.text.accountUnlinked')} ${target}`;
        }
        return this.getActivityTypeLabel(activity.type);
      }
      case 'PLANNING_CREATED': {
        const category = meta.category || '';
        const budgetAmount = meta.budgetAmount || 0;
        if (actor && category) {
          const budgetText = budgetAmount > 0 ? ` với ngân sách ${budgetAmount.toLocaleString('vi-VN')}₫` : '';
          return `${actor} ${this.translate('groups.detail.activity.text.planningCreated')} '${category}'${budgetText}`;
        }
        return this.getActivityTypeLabel(activity.type);
      }
      case 'PLANNING_UPDATED': {
        const category = meta.category || '';
        if (actor && category) {
          return `${actor} ${this.translate('groups.detail.activity.text.planningUpdated')} '${category}'`;
        }
        return this.getActivityTypeLabel(activity.type);
      }
      case 'PLANNING_DELETED': {
        const category = meta.category || '';
        if (actor && category) {
          return `${actor} ${this.translate('groups.detail.activity.text.planningDeleted')} '${category}'`;
        }
        return this.getActivityTypeLabel(activity.type);
      }
      case 'MEMBER_LEFT':
      case 'ADMIN_LEFT': {
        if (actor) {
          return `${actor} ${this.translate('groups.detail.activity.text.memberLeft')}`;
        }
        return this.getActivityTypeLabel(activity.type);
      }
      case 'MEMBER_ROLE_CHANGED': {
        const targetName = meta.targetUserName || '';
        const oldRole = meta.oldRole || '';
        const newRole = meta.newRole || '';
        if (actor && targetName) {
          const oldRoleLabel = this.getMemberRoleLabel(oldRole);
          const newRoleLabel = this.getMemberRoleLabel(newRole);
          return `${actor} ${this.translate('groups.detail.activity.text.roleChanged')} ${targetName} ${this.translate('groups.detail.activity.text.fromRole')} ${oldRoleLabel} ${this.translate('groups.detail.activity.text.toRole')} ${newRoleLabel}`;
        }
        return this.getActivityTypeLabel(activity.type);
      }
      case 'MEMBER_REMOVED': {
        const targetName = meta.targetUserName || '';
        if (actor && targetName) {
          return `${actor} ${this.translate('groups.detail.activity.text.removedMember')} ${targetName} ${this.translate('groups.detail.activity.text.fromGroup')}`;
        }
        return this.getActivityTypeLabel(activity.type);
      }
      default:
        return this.getActivityTypeLabel(activity.type);
    }
  }

  getActivityMessage(activity: GroupActivity): string {
    if (!activity) {
      return '';
    }
    const meta = this.parseActivityMetadata(activity);

    switch (activity.type) {
      case 'INVITE_SENT': {
        const invitee = meta.inviteeName || '';
        if (invitee) {
          return `${this.translate('groups.detail.activity.text.inviteSentTo')} ${invitee}`;
        }
        return this.translate('groups.detail.activity.type.INVITE_SENT');
      }
      case 'INVITE_ACCEPTED':
        return this.translate('groups.detail.activity.text.inviteAccepted');
      case 'INVITE_REJECTED':
        return this.translate('groups.detail.activity.text.inviteRejected');
      case 'ACCOUNT_LINKED': {
        const account = meta.accountNumber || '';
        const bank = meta.bankName || '';
        if (account) {
          const target = bank ? `${account} - ${bank}` : account;
          return `${this.translate('groups.detail.activity.text.accountLinked')} ${target}`;
        }
        return this.translate('groups.detail.activity.type.ACCOUNT_LINKED');
      }
      case 'ACCOUNT_UNLINKED': {
        const account = meta.accountNumber || '';
        const bank = meta.bankName || '';
        if (account) {
          const target = bank ? `${account} - ${bank}` : account;
          return `${this.translate('groups.detail.activity.text.accountUnlinked')} ${target}`;
        }
        return this.translate('groups.detail.activity.type.ACCOUNT_UNLINKED');
      }
      case 'PLANNING_CREATED': {
        const category = meta.category || '';
        const budgetAmount = meta.budgetAmount || 0;
        if (category) {
          const budgetText = budgetAmount > 0 ? ` với ngân sách ${budgetAmount.toLocaleString('vi-VN')}₫` : '';
          return `${this.translate('groups.detail.activity.text.planningCreated')} '${category}'${budgetText}`;
        }
        return this.translate('groups.detail.activity.type.PLANNING_CREATED');
      }
      case 'PLANNING_UPDATED': {
        const category = meta.category || '';
        if (category) {
          return `${this.translate('groups.detail.activity.text.planningUpdated')} '${category}'`;
        }
        return this.translate('groups.detail.activity.type.PLANNING_UPDATED');
      }
      case 'PLANNING_DELETED': {
        const category = meta.category || '';
        if (category) {
          return `${this.translate('groups.detail.activity.text.planningDeleted')} '${category}'`;
        }
        return this.translate('groups.detail.activity.type.PLANNING_DELETED');
      }
      case 'MEMBER_ROLE_CHANGED': {
        const targetName = meta.targetUserName || '';
        const oldRole = meta.oldRole || '';
        const newRole = meta.newRole || '';
        if (targetName && oldRole && newRole) {
          const oldRoleLabel = this.getMemberRoleLabel(oldRole);
          const newRoleLabel = this.getMemberRoleLabel(newRole);
          return `${this.translate('groups.detail.activity.text.roleChanged')} ${targetName} ${this.translate('groups.detail.activity.text.fromRole')} ${oldRoleLabel} ${this.translate('groups.detail.activity.text.toRole')} ${newRoleLabel}`;
        }
        return this.translate('groups.detail.activity.type.MEMBER_ROLE_CHANGED');
      }
      case 'MEMBER_REMOVED': {
        const targetName = meta.targetUserName || '';
        if (targetName) {
          return `${this.translate('groups.detail.activity.text.removedMember')} ${targetName} ${this.translate('groups.detail.activity.text.fromGroup')}`;
        }
        return this.translate('groups.detail.activity.type.MEMBER_REMOVED');
      }
      case 'GROUP_CREATED':
      case 'MEMBER_LEFT':
      case 'ADMIN_LEFT':
      default:
        return this.translate('groups.detail.activity.defaultMessage');
    }
  }

  getActivityDotClass(type: string): string {
    switch (type) {
      case 'INVITE_ACCEPTED':
      case 'ACCOUNT_LINKED':
      case 'PLANNING_CREATED':
        return 'dot-success';
      case 'ACCOUNT_UNLINKED':
      case 'ADMIN_LEFT':
      case 'PLANNING_DELETED':
        return 'dot-danger';
      case 'INVITE_SENT':
      case 'INVITE_REJECTED':
      case 'MEMBER_LEFT':
        return 'dot-warning';
      case 'MEMBER_ROLE_CHANGED':
      case 'PLANNING_UPDATED':
        return 'dot-info';
      case 'MEMBER_REMOVED':
        return 'dot-danger';
      default:
        return 'dot-neutral';
    }
  }

  private buildActivityGroups(): void {
    if (!this.activities || this.activities.length === 0) {
      this.activityGroups = [];
      return;
    }

    const groupsMap: { [key: string]: GroupActivity[] } = {};

    for (const activity of this.activities) {
      const date = activity.createdAt ? new Date(activity.createdAt) : null;
      const key =
        date && !isNaN(date.getTime())
          ? `${date.getFullYear()}-${(date.getMonth() + 1).toString().padStart(2, '0')}-${date
              .getDate()
              .toString()
              .padStart(2, '0')}`
          : 'unknown';
      if (!groupsMap[key]) {
        groupsMap[key] = [];
      }
      groupsMap[key].push(activity);
    }

    const keys = Object.keys(groupsMap).sort((a, b) => (a < b ? 1 : -1)); // desc

    this.activityGroups = keys.map((key) => {
      const dateLabel = key === 'unknown' ? '' : this.getActivityDateLabel(key);
      const items = groupsMap[key]
        .slice()
        .sort((a, b) => {
          const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0;
          const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0;
          return tb - ta;
        });
      return { dateLabel, items };
    });
  }

  private getActivityDateLabel(ymd: string): string {
    const [year, month, day] = ymd.split('-').map((v) => Number(v));
    const date = new Date(year, month - 1, day);
    if (isNaN(date.getTime())) {
      return ymd;
    }

    const today = new Date();
    const todayKey = `${today.getFullYear()}-${(today.getMonth() + 1)
      .toString()
      .padStart(2, '0')}-${today.getDate().toString().padStart(2, '0')}`;
    const yesterday = new Date();
    yesterday.setDate(today.getDate() - 1);
    const yesterdayKey = `${yesterday.getFullYear()}-${(yesterday.getMonth() + 1)
      .toString()
      .padStart(2, '0')}-${yesterday.getDate().toString().padStart(2, '0')}`;

    const shortDate = `${day.toString().padStart(2, '0')}/${month.toString().padStart(2, '0')}/${year}`;

    if (ymd === todayKey) {
      return `${this.translate('groups.detail.activity.today')}, ${shortDate}`;
    }
    if (ymd === yesterdayKey) {
      return `${this.translate('groups.detail.activity.yesterday')}, ${shortDate}`;
    }
    return shortDate;
  }

  getGroupInitials(): string {
    if (!this.group) return '';
    return this.group.name.substring(0, 2).toUpperCase();
  }

  translate(key: string): string {
    return this.languageService.translate(key);
  }

  onInviteMembers(): void {
    this.showInviteModal = true;
  }

  handleInviteModalClosed(): void {
    this.showInviteModal = false;
  }

  handleMemberInvited(): void {
    // Reload group detail để cập nhật tổng số thành viên
    if (this.groupId) {
      this.loadGroupDetail();
    }
    // Đồng bộ lại danh sách members + invitations trong tab
    this.membersPage = 0;
    this.invitePage = 0;
    this.loadMembers();
    this.loadPendingInvites();
  }

  navigateToAccountsTab(): void {
    this.selectTab('accounts');
  }

  navigateToPlanTab(): void {
    this.selectTab('plan');
  }

  navigateToActivityTab(): void {
    this.selectTab('activity');
    if (this.activities.length === 0) {
      this.loadActivities();
    }
  }

  onLeaveGroup(): void {
    if (!this.group) {
      return;
    }
    this.showLeaveModal = true;
  }

  closeLeaveModal(): void {
    if (this.leaveInProgress) {
      return;
    }
    this.showLeaveModal = false;
  }

  confirmLeaveGroup(): void {
    if (!this.group || this.leaveInProgress) {
      return;
    }
    this.leaveInProgress = true;

    this.groupService.leaveGroup(this.group.id).subscribe({
      next: () => {
        const successMsg = this.translate('groups.detail.leave.success');
        this.toastService.showSuccess(
          successMsg !== 'groups.detail.leave.success' ? successMsg : 'Bạn đã rời nhóm thành công.'
        );
        if (this.group) {
          this.deleted.emit(this.group.id);
        }
        this.leaveInProgress = false;
        this.showLeaveModal = false;
      },
      error: (err) => {
        console.error('Error leaving group:', err);
        const msg = err.error?.error || '';
        const lastAdminKey = 'groups.detail.leave.lastAdminError';
        if (msg && msg.includes('at least one admin')) {
          const lastAdminMsg = this.translate(lastAdminKey);
          this.toastService.showError(
            lastAdminMsg !== lastAdminKey ? lastAdminMsg : 'Nhóm phải còn ít nhất một quản trị viên.'
          );
        } else {
          const errorMsg = this.translate('groups.detail.leave.error');
          this.toastService.showError(
            errorMsg !== 'groups.detail.leave.error' ? errorMsg : 'Lỗi khi rời nhóm. Vui lòng thử lại.'
          );
        }
        this.leaveInProgress = false;
        this.showLeaveModal = false;
      }
    });
  }

  private ensureAccountsDataLoaded(): void {
    if (!this.availableAccounts.length && !this.availableAccountsLoading) {
      this.loadAvailableAccounts();
    } else {
      this.syncAvailableAccounts();
    }
  }

  onDeleteGroup(): void {
    if (!this.group || !this.isAdmin) {
      return;
    }
    this.showDeleteModal = true;
  }

  onUpdateGroup(): void {
    this.showUpdateModal = true;
  }

  handleGroupUpdated(updatedGroup: GroupDetail): void {
    this.group = updatedGroup;
    this.loadAvatar();
    this.showUpdateModal = false;
  }

  closeDeleteModal(): void {
    if (this.deleteInProgress) {
      return;
    }
    this.showDeleteModal = false;
  }

  confirmDelete(): void {
    if (!this.group || this.deleteInProgress) {
      return;
    }
    const groupId = this.group.id;
    this.deleteInProgress = true;
    this.groupService.delete(groupId).subscribe({
      next: () => {
        this.toastService.showSuccess(this.translate('groups.detail.deleteSuccess'));
        this.deleteInProgress = false;
        this.showDeleteModal = false;
        this.group = null;
        this.deleted.emit(groupId);
      },
      error: (error) => {
        console.error('Error deleting group:', error);
        this.toastService.showError(this.translate('groups.detail.deleteError'));
        this.deleteInProgress = false;
      }
    });
  }

  handleUpdateModalClosed(): void {
    this.showUpdateModal = false;
  }

  filteredAvailableAccounts(): any[] {
    const term = this.accountSearchTerm.trim().toLowerCase();
    let list = this.availableAccounts || [];
    if (term) {
      list = list.filter((acc: any) => {
        const label = (acc.label || '').toLowerCase();
        const accNumber = (acc.accountNumber || '').toLowerCase();
        const bank = (acc.bankBrandName || '').toLowerCase();
        return label.includes(term) || accNumber.includes(term) || bank.includes(term);
      });
    }
    return list;
  }

  linkAccountToGroup(account: any): void {
    if (!this.group || !account || this.isAccountLinked(account.id)) {
      return;
    }
    this.groupService.linkGroupAccount(this.group.id, Number(account.id)).subscribe({
      next: (linkedAccount) => {
        this.groupBankAccounts = [...this.groupBankAccounts, linkedAccount];
        this.groupSummary.totalBalance = this.calculateTotalBalance();
        this.availableAccounts = this.availableAccounts.filter(acc => acc.id !== account.id);
        this.toastService.showSuccess(this.translate('groups.detail.accounts.linkSuccess'));

        // Reload overview data (summary, recent transactions, activities, etc.)
        this.loadOverviewData();

        // If we're on the transactions tab, reload transactions to show newly linked account's transactions
        if (this.activeTab === 'transactions') {
          this.loadGroupTransactions();
        }
      },
      error: (error) => {
        console.error('Error linking account to group:', error);
        this.toastService.showError(this.translate('groups.detail.accounts.linkError'));
      }
    });
  }

  unlinkAccountFromGroup(accountId: number): void {
    if (!this.group) {
      return;
    }
    this.groupService.unlinkGroupAccount(this.group.id, accountId).subscribe({
      next: () => {
        this.groupBankAccounts = this.groupBankAccounts.filter(acc => acc.accountId !== accountId);
        this.groupSummary.totalBalance = this.calculateTotalBalance();
        this.toastService.showSuccess(this.translate('groups.detail.accounts.unlinkSuccess'));
        this.loadAvailableAccounts();

        // Reload overview data to reflect removed account
        this.loadOverviewData();
      },
      error: (error) => {
        console.error('Error unlinking account:', error);
        this.toastService.showError(this.translate('groups.detail.accounts.unlinkError'));
      }
    });
  }

  isAccountLinked(accountId: string | number): boolean {
    const idNum = Number(accountId);
    return (this.groupBankAccounts || []).some(acc => Number(acc.accountId) === idNum);
  }

  onAccountSearchChange(term: string): void {
    this.accountSearchTerm = term;
  }

  getMemberInitials(member: any): string {
    if (member.fullName) {
      const parts = member.fullName.trim().split(/\s+/);
      if (parts.length >= 2) {
        return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
      }
      return member.fullName.substring(0, 2).toUpperCase();
    }
    return member.userId.toString().substring(0, 2);
  }

  onAvatarClick(): void {
    if (this.avatarUrl) {
      this.showAvatarView = true;
    }
  }

  closeAvatarView(): void {
    this.showAvatarView = false;
  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND'
    }).format(amount);
  }

  formatDate(dateString?: string): string {
    if (!dateString) return '';
    try {
      const date = new Date(dateString);
      return new Intl.DateTimeFormat('vi-VN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
      }).format(date);
    } catch (e) {
      return dateString || '';
    }
  }

  formatTransactionAmount(transaction: Transaction): string {
    if (transaction.amountIn && transaction.amountIn > 0) {
      return '+' + this.formatCurrency(transaction.amountIn);
    } else if (transaction.amountOut && transaction.amountOut > 0) {
      return '-' + this.formatCurrency(transaction.amountOut);
    }
    return this.formatCurrency(0);
  }

  getProgressPercentage(plan: PlanningBudget | GroupPlanning): number {
    if (!plan.budgetAmount || plan.budgetAmount === 0) return 0;
    const spent = plan.spentAmount || 0;
    const percentage = (spent / plan.budgetAmount) * 100;
    return Math.min(percentage, 100);
  }

  isOverspent(plan: GroupPlanning): boolean {
    return (plan.spentAmount || 0) > (plan.budgetAmount || 0);
  }

  remainingAmount(plan: GroupPlanning): number {
    return (plan.budgetAmount || 0) - (plan.spentAmount || 0);
  }

  getPlanTypeLabel(planType: string): string {
    if (!planType) return '';
    const typeKey = planType.toLowerCase();
    if (typeKey.includes('short')) {
      return this.translate('planning.modal.planTypeShortTerm').split('(')[0].trim();
    } else if (typeKey.includes('long')) {
      return this.translate('planning.modal.planTypeLongTerm').split('(')[0].trim();
    } else if (typeKey.includes('recurring')) {
      return this.translate('planning.modal.planTypeRecurring').split('(')[0].trim();
    }
    return planType;
  }

  asNumber(value: string | number | null | undefined): number {
    if (typeof value === 'number') {
      return value;
    }
    if (typeof value === 'string') {
      const parsed = parseFloat(value);
      return Number.isNaN(parsed) ? 0 : parsed;
    }
    return 0;
  }

  calculateCategoryStats(transactions: Transaction[]): void {
    const categoryMap = new Map<string, number>();

    transactions.forEach((tx: Transaction) => {
      if (tx.amountOut && tx.amountOut > 0) {
        const category = tx.category || 'uncategorized';
        const current = categoryMap.get(category) || 0;
        categoryMap.set(category, current + tx.amountOut);
      }
    });

    // Convert to array and sort by amount descending
    this.categoryStats = Array.from(categoryMap.entries())
      .map(([category, amount]) => ({ category, amount }))
      .sort((a, b) => b.amount - a.amount)
      .slice(0, 5); // Top 5 categories
  }

  loadCategoryBreakdown(bankAccountIds: string[], startDate: string, endDate: string): void {
    if (!bankAccountIds || bankAccountIds.length === 0) {
      this.expenseBreakdown = [];
      this.incomeBreakdown = [];
      this.expenseTotal = 0;
      this.incomeTotal = 0;
      this.updateExpenseChart();
      this.updateIncomeChart();
      return;
    }

    // Load expense breakdown
    this.loadingExpenseBreakdown = true;
    this.transactionService.getCategoryBreakdownByBankAccountIds(bankAccountIds, 'expense', startDate, endDate).subscribe({
      next: (res: CategoryBreakdown) => {
        this.expenseBreakdown = res.items || [];
        this.expenseTotal = res.totalAmount || 0;
        this.updateExpenseChart();
      },
      error: () => {
        this.expenseBreakdown = [];
        this.expenseTotal = 0;
        this.updateExpenseChart();
      },
      complete: () => this.loadingExpenseBreakdown = false
    });

    // Load income breakdown
    this.loadingIncomeBreakdown = true;
    this.transactionService.getCategoryBreakdownByBankAccountIds(bankAccountIds, 'income', startDate, endDate).subscribe({
      next: (res: CategoryBreakdown) => {
        this.incomeBreakdown = res.items || [];
        this.incomeTotal = res.totalAmount || 0;
        this.updateIncomeChart();
      },
      error: () => {
        this.incomeBreakdown = [];
        this.incomeTotal = 0;
        this.updateIncomeChart();
      },
      complete: () => this.loadingIncomeBreakdown = false
    });
  }

  private updateExpenseChart(): void {
    if (!this.expenseBreakdown || this.expenseBreakdown.length === 0) {
      this.expenseChartOptions = {};
      return;
    }

    const series = this.expenseBreakdown.map(item => item.amount);
    const labels = this.expenseBreakdown.map(item => item.category);
    const colors = this.expenseBreakdown.map((_, idx) => this.categoryColors[idx % this.categoryColors.length]);

    this.expenseChartOptions = {
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
              width: 180,
              height: 180
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

  private updateIncomeChart(): void {
    if (!this.incomeBreakdown || this.incomeBreakdown.length === 0) {
      this.incomeChartOptions = {};
      return;
    }

    const series = this.incomeBreakdown.map(item => item.amount);
    const labels = this.incomeBreakdown.map(item => item.category);
    const colors = this.incomeBreakdown.map((_, idx) => this.categoryColors[idx % this.categoryColors.length]);

    this.incomeChartOptions = {
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
              width: 180,
              height: 180
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

  getExpenseTotalFormatted(): string {
    if (this.loadingExpenseBreakdown) return '—';
    if (this.expenseTotal === 0 && (!this.expenseBreakdown || this.expenseBreakdown.length === 0)) return '—';
    return this.formatCurrency(this.expenseTotal);
  }

  getIncomeTotalFormatted(): string {
    if (this.loadingIncomeBreakdown) return '—';
    if (this.incomeTotal === 0 && (!this.incomeBreakdown || this.incomeBreakdown.length === 0)) return '—';
    return this.formatCurrency(this.incomeTotal);
  }

  getExpensePercentage(amount: number): number {
    if (this.expenseTotal === 0) return 0;
    return (amount / this.expenseTotal) * 100;
  }

  getIncomePercentage(amount: number): number {
    if (this.incomeTotal === 0) return 0;
    return (amount / this.incomeTotal) * 100;
  }

  setActiveExpenseCategory(index: number | null): void {
    this.activeExpenseCategoryIndex = index;
  }

  clearActiveExpenseCategory(): void {
    this.activeExpenseCategoryIndex = null;
  }

  setActiveIncomeCategory(index: number | null): void {
    this.activeIncomeCategoryIndex = index;
  }

  clearActiveIncomeCategory(): void {
    this.activeIncomeCategoryIndex = null;
  }

  getActiveExpenseCategoryLabel(): string {
    if (this.activeExpenseCategoryIndex === null) return '';
    return this.expenseBreakdown[this.activeExpenseCategoryIndex]?.category || this.translate('groups.detail.overview.uncategorized');
  }

  getActiveExpenseCategoryPercent(): number {
    if (this.activeExpenseCategoryIndex === null) return 0;
    const item = this.expenseBreakdown[this.activeExpenseCategoryIndex];
    if (!item) return 0;
    return this.getExpensePercentage(item.amount);
  }

  getActiveExpenseCategoryAmount(): string {
    if (this.activeExpenseCategoryIndex === null) return '';
    const item = this.expenseBreakdown[this.activeExpenseCategoryIndex];
    if (!item) return '';
    return this.formatCurrency(item.amount);
  }

  getActiveIncomeCategoryLabel(): string {
    if (this.activeIncomeCategoryIndex === null) return '';
    return this.incomeBreakdown[this.activeIncomeCategoryIndex]?.category || this.translate('groups.detail.overview.uncategorized');
  }

  getActiveIncomeCategoryPercent(): number {
    if (this.activeIncomeCategoryIndex === null) return 0;
    const item = this.incomeBreakdown[this.activeIncomeCategoryIndex];
    if (!item) return 0;
    return this.getIncomePercentage(item.amount);
  }

  getActiveIncomeCategoryAmount(): string {
    if (this.activeIncomeCategoryIndex === null) return '';
    const item = this.incomeBreakdown[this.activeIncomeCategoryIndex];
    if (!item) return '';
    return this.formatCurrency(item.amount);
  }

  getCategoryPercentage(amount: number): number {
    const total = this.getCategoryTotal();
    if (total === 0) return 0;
    return (amount / total) * 100;
  }

  getCategoryTotal(): number {
    if (!this.categoryStats || this.categoryStats.length === 0) return 0;
    return this.categoryStats.reduce((sum, stat) => sum + (stat.amount || 0), 0);
  }

  getCategoryColor(index: number): string {
    return this.categoryColors[index % this.categoryColors.length];
  }

  getCategorySegments(): Array<{
    color: string;
    dashArray: string;
    dashOffset: string;
    percentage: number;
  }> {
    const total = this.getCategoryTotal();
    if (total === 0) {
      return [];
    }

    let cumulative = 0;
    const gap = 0.5;
    return this.categoryStats.map((stat, index) => {
      const percentage = (stat.amount / total) * 100;
      const adjusted = Math.max(percentage - gap, 0);
      const segment = {
        color: this.getCategoryColor(index),
        dashArray: `${adjusted} ${100 - adjusted}`,
        dashOffset: `${-cumulative}`,
        percentage
      };
      cumulative += percentage;
      return segment;
    });
  }

  setActiveCategory(index: number | null): void {
    this.activeCategoryIndex = index;
  }

  clearActiveCategory(): void {
    this.activeCategoryIndex = null;
  }

  getCategoryLabel(index: number): string {
    const stat = this.categoryStats[index];
    if (!stat) {
      return '';
    }
    return stat.category || this.translate('groups.detail.overview.uncategorized');
  }

  calculateMonthlyTrend(currentMonth: Transaction[], lastMonth: Transaction[]): void {
    const calculateMonthData = (transactions: Transaction[]) => {
      let income = 0;
      let expense = 0;

      transactions.forEach((tx: Transaction) => {
        if (tx.amountIn && tx.amountIn > 0) {
          income += tx.amountIn;
        }
        if (tx.amountOut && tx.amountOut > 0) {
          expense += tx.amountOut;
        }
      });

      return {
        income,
        expense,
        total: income - expense
      };
    };

    const currentData = calculateMonthData(currentMonth);
    const lastData = calculateMonthData(lastMonth);

    let changePercentage = 0;
    if (lastData.total !== 0) {
      changePercentage = ((currentData.total - lastData.total) / Math.abs(lastData.total)) * 100;
    } else if (currentData.total !== 0) {
      changePercentage = currentData.total > 0 ? 100 : -100;
    } else if (lastData.total === 0 && currentData.total === 0) {
      // Both months have no balance change
      changePercentage = 0;
    }

    this.monthlyTrend = {
      currentMonth: currentData,
      lastMonth: lastData,
      changePercentage
    };
  }

  // Transactions tab methods
  loadGroupTransactions(): void {
    if (this.groupBankAccounts.length === 0) {
      this.groupTransactions = [];
      this.transactionTotalElements = 0;
      return;
    }

    this.loadingGroupTransactions = true;

    // Parse amount range
    let minAmount: number | undefined;
    let maxAmount: number | undefined;
    if (this.transactionAmountRange && this.transactionAmountRange !== 'all') {
      const range = this.parseTransactionAmountRange(this.transactionAmountRange);
      minAmount = range.min;
      maxAmount = range.max;
    }

    // Get bank account IDs to filter (use bankAccountId if available, otherwise fallback to accountId)
    let bankAccountIds = this.groupBankAccounts
      .filter(account => !this.selectedTransactionAccountId || account.accountId.toString() === this.selectedTransactionAccountId)
      .map(account => {
        // Prefer bankAccountId, but if not available, use accountId as string
        const id = account.bankAccountId || account.accountId?.toString();
        return id;
      })
      .filter(id => id != null && id !== '');

    console.log('Loading group transactions with bankAccountIds:', bankAccountIds);
    console.log('Group bank accounts:', this.groupBankAccounts.map(acc => ({
      accountId: acc.accountId,
      bankAccountId: acc.bankAccountId,
      accountNumber: acc.accountNumber
    })));

    if (bankAccountIds.length === 0) {
      console.warn('No bank account IDs found for group transactions');
      this.groupTransactions = [];
      this.transactionTotalElements = 0;
      this.loadingGroupTransactions = false;
      return;
    }

    // Load transactions for all group accounts in one call
    this.transactionService.filterTransactions({
      bankAccountIds: bankAccountIds,
      textSearch: this.transactionSearchTerm || undefined,
      transactionType: (this.transactionType && this.transactionType.trim() !== '') ? this.transactionType : undefined,
      startDate: this.transactionStartDate || undefined,
      endDate: this.transactionEndDate || undefined,
      minAmount: minAmount,
      maxAmount: maxAmount,
      page: this.transactionCurrentPage,
      size: this.transactionPageSize
    }).subscribe({
      next: (response: any) => {
        this.groupTransactions = response.content || [];
        this.transactionTotalElements = response.totalElements || 0;
        this.transactionTotalPages = response.totalPages || 0;
        this.loadingGroupTransactions = false;
        // Calculate summary from all transactions (not just current page)
        this.loadGroupTransactionSummary();
      },
      error: (error) => {
        console.error('Error loading group transactions:', error);
        this.groupTransactions = [];
        this.transactionTotalElements = 0;
        this.loadingGroupTransactions = false;
        this.toastService.showError(this.translate('groups.detail.transactions.loadError'));
      }
    });
  }

  loadGroupTransactionSummary(): void {
    if (this.groupBankAccounts.length === 0) {
      this.groupTransactionSummary = {
        totalIncome: 0,
        totalExpense: 0,
        netAmount: 0,
        transactionCount: 0,
        averageAmount: 0
      };
      return;
    }

    this.loadingGroupTransactionSummary = true;

    // Parse amount range
    let minAmount: number | undefined;
    let maxAmount: number | undefined;
    if (this.transactionAmountRange && this.transactionAmountRange !== 'all') {
      const range = this.parseTransactionAmountRange(this.transactionAmountRange);
      minAmount = range.min;
      maxAmount = range.max;
    }

    // Get bank account IDs to filter
    let bankAccountIds = this.groupBankAccounts
      .filter(account => !this.selectedTransactionAccountId || account.accountId.toString() === this.selectedTransactionAccountId)
      .map(account => account.bankAccountId || account.accountId.toString())
      .filter(id => id != null && id !== '');

    if (bankAccountIds.length === 0) {
      this.groupTransactionSummary = {
        totalIncome: 0,
        totalExpense: 0,
        netAmount: 0,
        transactionCount: 0,
        averageAmount: 0
      };
      this.loadingGroupTransactionSummary = false;
      return;
    }

    // Call API to get summary (with filters if any)
    this.transactionService.getSummaryByBankAccountIds(
      bankAccountIds,
      this.transactionStartDate || undefined,
      this.transactionEndDate || undefined,
      this.transactionSearchTerm || undefined,
      (this.transactionType && this.transactionType.trim() !== '') ? this.transactionType : undefined,
      minAmount,
      maxAmount
    ).subscribe({
      next: (summary) => {
        this.groupTransactionSummary = summary;
        this.loadingGroupTransactionSummary = false;
      },
      error: (error) => {
        console.error('Error loading group transaction summary:', error);
        this.groupTransactionSummary = {
          totalIncome: 0,
          totalExpense: 0,
          netAmount: 0,
          transactionCount: 0,
          averageAmount: 0
        };
        this.loadingGroupTransactionSummary = false;
      }
    });
  }

  onTransactionSearch(): void {
    this.transactionCurrentPage = 0;
    this.loadGroupTransactions();
    // Summary will be recalculated in loadGroupTransactions
  }

  onTransactionFilterChange(): void {
    this.transactionCurrentPage = 0;
    this.loadGroupTransactions();
    // Summary will be recalculated in loadGroupTransactions
  }

  clearTransactionFilters(): void {
    this.transactionSearchTerm = '';
    this.transactionType = '';
    this.transactionStartDate = '';
    this.transactionEndDate = '';
    this.selectedTransactionAccountId = '';
    this.transactionAmountRange = 'all';
    this.transactionCurrentPage = 0;
    this.loadGroupTransactions();
    // Summary will be recalculated in loadGroupTransactions
  }

  parseTransactionAmountRange(range: string): { min: number | undefined, max: number | undefined } {
    if (!range || range === 'all') {
      return { min: undefined, max: undefined };
    }

    if (range.startsWith('below-')) {
      const max = parseFloat(range.replace('below-', ''));
      return { min: undefined, max: max };
    }

    if (range.startsWith('above-')) {
      const min = parseFloat(range.replace('above-', ''));
      return { min: min, max: undefined };
    }

    if (range.includes('-')) {
      const parts = range.split('-');
      const min = parseFloat(parts[0]);
      const max = parseFloat(parts[1]);
      return { min: isNaN(min) ? undefined : min, max: isNaN(max) ? undefined : max };
    }

    return { min: undefined, max: undefined };
  }

  getTransactionAmountRangeOptions() {
    return [
      { value: 'all', label: this.translate('transactions.amountRange.all') },
      { value: '1-250000', label: this.translate('transactions.amountRange.range1') },
      { value: 'below-500000', label: this.translate('transactions.amountRange.range2') },
      { value: '500000-1000000', label: this.translate('transactions.amountRange.range3') },
      { value: '1000000-2500000', label: this.translate('transactions.amountRange.range4') },
      { value: '2500000-5000000', label: this.translate('transactions.amountRange.range5') },
      { value: '5000000-7000000', label: this.translate('transactions.amountRange.range6') },
      { value: '7000000-10000000', label: this.translate('transactions.amountRange.range7') },
      { value: '10000000-20000000', label: this.translate('transactions.amountRange.range8') },
      { value: '20000000-50000000', label: this.translate('transactions.amountRange.range9') },
      { value: '50000000-100000000', label: this.translate('transactions.amountRange.range10') },
      { value: 'above-100000000', label: this.translate('transactions.amountRange.range11') }
    ];
  }

  onTransactionPageChange(page: number): void {
    this.transactionCurrentPage = page;
    this.loadGroupTransactions();
  }

  onTransactionPageSizeChange(size: number): void {
    this.transactionPageSize = size;
    this.transactionCurrentPage = 0;
    this.loadGroupTransactions();
  }

  viewGroupTransactionDetail(transaction: Transaction): void {
    if (!transaction) {
      console.error('Transaction is null');
      return;
    }

    this.showTransactionDetail = true;
    this.loadingTransactionDetail = true;
    this.transactionDetail = null;

    if (transaction.id) {
      this.transactionService.getTransactionById(transaction.id).subscribe({
        next: (detail) => {
          this.transactionDetail = detail;
          this.loadingTransactionDetail = false;
        },
        error: (err) => {
          console.error('Error loading transaction detail:', err);
          // Fallback to use the transaction data we already have
          this.transactionDetail = transaction;
          this.loadingTransactionDetail = false;
        }
      });
    } else {
      // If no ID, just use the transaction data we have
      this.transactionDetail = transaction;
      this.loadingTransactionDetail = false;
    }
  }

  closeTransactionDetailDialog(): void {
    this.showTransactionDetail = false;
    this.transactionDetail = null;
  }

  getGroupTransactionAmount(transaction: Transaction): number {
    if (transaction.amountIn && transaction.amountIn > 0) {
      return transaction.amountIn;
    }
    if (transaction.amountOut && transaction.amountOut > 0) {
      return -transaction.amountOut;
    }
    return 0;
  }

  getGroupTransactionType(transaction: Transaction): 'income' | 'expense' {
    if (transaction.amountIn && transaction.amountIn > 0) {
      return 'income';
    }
    return 'expense';
  }

  exportGroupTransactionsToExcel(): void {
    if (this.groupBankAccounts.length === 0) {
      this.toastService.showError(this.translate('groups.detail.transactions.noAccounts'));
      return;
    }

    // Parse amount range
    let minAmount: number | undefined;
    let maxAmount: number | undefined;
    if (this.transactionAmountRange && this.transactionAmountRange !== 'all') {
      const range = this.parseTransactionAmountRange(this.transactionAmountRange);
      minAmount = range.min;
      maxAmount = range.max;
    }

    // Get bank account IDs to filter (use bankAccountId if available, otherwise fallback to accountId)
    const bankAccountIds = this.groupBankAccounts
      .filter(account => !this.selectedTransactionAccountId || account.accountId.toString() === this.selectedTransactionAccountId)
      .map(account => account.bankAccountId || account.accountId.toString())
      .filter(id => id != null && id !== '');

    if (bankAccountIds.length === 0) {
      this.toastService.showError(this.translate('groups.detail.transactions.noTransactions'));
      return;
    }

    // Use backend export API with bankAccountIds
    this.transactionService.exportToExcel({
      bankAccountIds: bankAccountIds,
      textSearch: this.transactionSearchTerm || undefined,
      transactionType: (this.transactionType && this.transactionType.trim() !== '') ? this.transactionType : undefined,
      startDate: this.transactionStartDate || undefined,
      endDate: this.transactionEndDate || undefined,
      minAmount: minAmount,
      maxAmount: maxAmount
    }).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `group_transactions_${this.group?.name || 'group'}_${new Date().getTime()}.xlsx`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
        this.toastService.showSuccess(this.translate('groups.detail.transactions.exportSuccess'));
      },
      error: (error) => {
        console.error('Error exporting transactions:', error);
        this.toastService.showError(this.translate('groups.detail.transactions.exportError'));
      }
    });
  }

  downloadTransactionsAsExcel(transactions: Transaction[]): void {
    // Simple CSV export (can be enhanced to use a library like xlsx)
    const headers = ['Ngày', 'Nội dung', 'Số tài khoản', 'Ngân hàng', 'Số tiền thu', 'Số tiền chi', 'Loại giao dịch', 'Phân loại', 'Mã tham chiếu'];
    const rows = transactions.map(tx => [
      tx.transactionDate || '',
      tx.transactionContent || '',
      tx.accountNumber || '',
      tx.bankBrandName || '',
      tx.amountIn || 0,
      tx.amountOut || 0,
      tx.transactionType || '',
      tx.category || 'không xác định',
      tx.referenceNumber || ''
    ]);

    const csvContent = [
      headers.join(','),
      ...rows.map(row => row.map(cell => `"${cell}"`).join(','))
    ].join('\n');

    const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `group_transactions_${this.group?.name || 'group'}_${new Date().getTime()}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    this.toastService.showSuccess(this.translate('groups.detail.transactions.exportSuccess'));
  }

  loadGroupCategories(): void {
    if (!this.currentUser) return;
    const userId = this.currentUser.id.toString();
    this.loadingCategories = true;
    this.categoryService.getAllCategories(userId).subscribe({
      next: (categories) => {
        this.categories = categories;
        this.loadingCategories = false;
      },
      error: (err) => {
        console.error('Error loading categories:', err);
        this.loadingCategories = false;
      }
    });
  }

  openGroupCategoryModal(transaction: Transaction): void {
    if (!transaction) return;
    this.selectedTransactionForCategory = transaction;
    this.selectedCategory = transaction.category || 'không xác định';
    this.newCategory = '';
    this.showNewCategoryInput = false;

    // Load categories if not already loaded
    if (this.categories.length === 0) {
      this.loadGroupCategories();
      // Wait a bit for categories to load, then show modal
      setTimeout(() => {
        this.showCategoryModal = true;
      }, 100);
    } else {
      this.showCategoryModal = true;
    }
  }

  toggleNewCategoryInput(): void {
    this.showNewCategoryInput = !this.showNewCategoryInput;
    if (this.showNewCategoryInput) {
      this.selectedCategory = '';
      this.newCategory = '';
    }
  }

  closeGroupCategoryModal(): void {
    this.showCategoryModal = false;
    this.selectedTransactionForCategory = null;
    this.selectedCategory = '';
    this.newCategory = '';
    this.showNewCategoryInput = false;
  }

  updateGroupTransactionCategory(): void {
    if (!this.selectedTransactionForCategory || !this.selectedTransactionForCategory.id) {
      return;
    }

    const categoryToUpdate = this.showNewCategoryInput && this.newCategory.trim()
      ? this.newCategory.trim()
      : this.selectedCategory;

    if (!categoryToUpdate || categoryToUpdate.trim() === '') {
      return;
    }

    // If adding new category, save it first
    if (this.showNewCategoryInput && this.newCategory.trim()) {
      if (!this.currentUser) return;
      const userId = this.currentUser.id.toString();
      this.categoryService.addCategory(userId, this.newCategory.trim()).subscribe({
        next: (newCategory) => {
          this.categories.push(newCategory);
          // Then update transaction category
          this.updateGroupTransactionCategoryAfterSave(categoryToUpdate);
        },
        error: (err) => {
          console.error('Error adding category:', err);
          // Check if category already exists
          if (err.error && err.error.error && err.error.error.includes('already exists')) {
            this.toastService.showError(this.translate('groups.detail.transactions.categoryExists'));
          } else {
            this.toastService.showError(this.translate('groups.detail.transactions.addCategoryError'));
          }
          // Still try to update transaction with the category name
          this.updateGroupTransactionCategoryAfterSave(categoryToUpdate);
        }
      });
    } else {
      this.updateGroupTransactionCategoryAfterSave(categoryToUpdate);
    }
  }

  private updateGroupTransactionCategoryAfterSave(categoryToUpdate: string): void {
    if (!this.selectedTransactionForCategory || !this.selectedTransactionForCategory.id) {
      return;
    }

    this.transactionService.updateCategory(this.selectedTransactionForCategory.id, categoryToUpdate).subscribe({
      next: (updated) => {
        // Update the transaction in the list
        const index = this.groupTransactions.findIndex(t => t.id === updated.id);
        if (index !== -1) {
          this.groupTransactions[index] = updated;
        }

        // Update transaction detail if it's the same
        if (this.transactionDetail && this.transactionDetail.id === updated.id) {
          this.transactionDetail = updated;
        }

        this.closeGroupCategoryModal();
        this.toastService.showSuccess(this.translate('groups.detail.transactions.updateCategorySuccess'));

        // Sau khi cập nhật loại giao dịch, recalculate group planning spent amounts
        if (this.group && this.groupBankAccounts.length > 0) {
          const bankAccountIds = this.groupBankAccounts
            .map(account => account.bankAccountId || account.accountId.toString())
            .filter(id => id);

          if (bankAccountIds.length > 0) {
            this.groupPlanningService.recalculateAllSpentAmounts(this.group.id, bankAccountIds).subscribe({
              next: () => {
                // Reload group plans and summary after recalculating
                // Get member user IDs for loadGroupPlans
                const memberUserIds = this.members.map(m => m.userId.toString());
                if (memberUserIds.length > 0) {
                  this.loadGroupPlans(memberUserIds);
                }
                this.loadGroupPlanningSummary();
              },
              error: (err) => {
                console.error('Error recalculating group planning spent amounts:', err);
                // Still reload data even if recalculate fails
                const memberUserIds = this.members.map(m => m.userId.toString());
                if (memberUserIds.length > 0) {
                  this.loadGroupPlans(memberUserIds);
                }
                this.loadGroupPlanningSummary();
              }
            });
          }
        }

        // Sau khi cập nhật loại giao dịch, reload lại dữ liệu tổng quan
        // để box kế hoạch và thống kê theo danh mục dùng dữ liệu mới nhất
        if (this.group) {
          this.loadOverviewData();
        }
      },
      error: (err) => {
        console.error('Error updating category:', err);
        this.toastService.showError(this.translate('groups.detail.transactions.updateCategoryError'));
      }
    });
  }

  deleteGroupCategory(categoryName: string): void {
    if (!this.currentUser || !categoryName) return;

    // Check if it's a default category (không xác định)
    const category = this.categories.find(c => c.name === categoryName);
    if (category && category.isDefault) {
      this.toastService.showError(this.translate('groups.detail.transactions.cannotDeleteDefault'));
      return;
    }

    const userId = this.currentUser.id.toString();
    this.categoryService.deleteCategory(userId, categoryName).subscribe({
      next: () => {
        // Remove from local list
        this.categories = this.categories.filter(c => c.name !== categoryName);
        // If this was the selected category, reset it
        if (this.selectedCategory === categoryName) {
          this.selectedCategory = 'không xác định';
        }
        this.toastService.showSuccess(this.translate('groups.detail.transactions.deleteCategory'));
      },
      error: (err) => {
        console.error('Error deleting category:', err);
        const errorMsg = err.error?.error || this.translate('groups.detail.transactions.deleteCategory');
        this.toastService.showError(errorMsg);
      }
    });
  }

  getGroupCategoryNames(): string[] {
    return this.categories.map(c => c.name);
  }

  getGroupCategoryDisplay(category: string | undefined): string {
    return category || 'không xác định';
  }

  syncGroupTransactions(): void {
    if (!this.currentUser || this.syncingGroupTransactions) return;

    const userId = this.currentUser.id.toString();
    this.syncingGroupTransactions = true;

    this.transactionService.syncTransactions(userId).subscribe({
      next: (res: any) => {
        this.syncingGroupTransactions = false;
        if (res && res.success === false && res.message === 'No bank token available') {
          this.toastService.showError('Chưa kết nối SePay, không thể đồng bộ giao dịch!');
          return;
        }
        // Reload transactions and recent transactions after sync
        if (this.activeTab === 'transactions') {
          this.loadGroupTransactions(); // This will also reload summary
        }
        // Reload recent transactions in overview
        if (this.group && this.group.members && this.group.members.length > 0) {
          const userIds = this.group.members.map(m => m.userId.toString());
          this.loadRecentTransactions(userIds);
        }
        const syncSuccessMsg = this.translate('transactions.syncSuccess');
        this.toastService.showSuccess(syncSuccessMsg !== 'transactions.syncSuccess' ? syncSuccessMsg : 'Đồng bộ giao dịch thành công!');
      },
      error: (err) => {
        console.error('Error syncing transactions:', err);
        this.syncingGroupTransactions = false;
        const syncErrorMsg = this.translate('transactions.syncError');
        this.toastService.showError(syncErrorMsg !== 'transactions.syncError' ? syncErrorMsg : 'Lỗi khi đồng bộ giao dịch. Vui lòng thử lại.');
      }
    });
  }

  navigateToTransactionsTab(): void {
    this.selectTab('transactions');
    if (this.groupTransactions.length === 0) {
      this.loadGroupTransactions();
    }
  }

  // ===== Members & Invitations tab =====

  private loadMembers(): void {
    if (!this.group) {
      this.members = [];
      this.membersTotalElements = 0;
      this.membersTotalPages = 0;
      return;
    }
    this.loadingMembers = true;
    this.groupService
      .getMembers(this.group.id, {
        page: this.membersPage,
        size: this.membersPageSize
      })
      .subscribe({
        next: (page: PageResponse<GroupMember>) => {
          const members = page.content || [];
          this.membersTotalElements = page.totalElements || 0;
          this.membersTotalPages = page.totalPages || 0;

          // Bulk load avatar live URLs giống màn Groups
          const avatarIds = Array.from(
            new Set(
              members
                .map(m => m.avatar)
                .filter((id): id is string => !!id)
            )
          );
          if (!avatarIds.length) {
            this.members = members;
            this.loadingMembers = false;
            return;
          }
          this.fileService.getLiveUrls(avatarIds).subscribe({
            next: (map) => {
              const liveMap = map || {};
              this.members = members.map(m => ({
                ...m,
                avatar: m.avatar && liveMap[m.avatar] ? liveMap[m.avatar] : m.avatar
              }));
              this.loadingMembers = false;
            },
            error: (e) => {
              console.error('Error loading member avatar urls:', e);
              this.members = members;
              this.loadingMembers = false;
            }
          });
        },
        error: (e) => {
          console.error('Error loading group members:', e);
          this.members = [];
          this.membersTotalElements = 0;
          this.membersTotalPages = 0;
          this.loadingMembers = false;
          const msgKey = 'groups.detail.membersTab.loadError';
          const msg = this.translate(msgKey);
          this.toastService.showError(msg !== msgKey ? msg : 'Không thể tải danh sách thành viên. Vui lòng thử lại.');
        }
      });
  }

  onMembersPageChange(page: number): void {
    if (page < 0 || (this.membersTotalPages && page >= this.membersTotalPages)) {
      return;
    }
    this.membersPage = page;
    this.loadMembers();
  }

  onMembersPageSizeChange(size: number): void {
    this.membersPageSize = size;
    this.membersPage = 0;
    this.loadMembers();
  }

  private loadPendingInvites(): void {
    if (!this.group) {
      this.pendingInvites = [];
      this.inviteTotalElements = 0;
      this.inviteTotalPages = 0;
      return;
    }
    this.loadingInvites = true;
    this.groupService
      .getInvites(this.group.id, {
        status: 'PENDING',
        page: this.invitePage,
        size: this.invitePageSize
      })
      .subscribe({
        next: (page: PageResponse<GroupInvite>) => {
          this.pendingInvites = page.content || [];
          this.inviteTotalElements = page.totalElements || 0;
          this.inviteTotalPages = page.totalPages || 0;
          this.loadingInvites = false;
        },
        error: (error) => {
          console.error('Error loading group invites:', error);
          this.pendingInvites = [];
          this.inviteTotalElements = 0;
          this.inviteTotalPages = 0;
          this.loadingInvites = false;
          const msgKey = 'groups.detail.membersTab.inviteLoadError';
          const msg = this.translate(msgKey);
          this.toastService.showError(msg !== msgKey ? msg : 'Không thể tải danh sách lời mời. Vui lòng thử lại.');
        }
      });
  }

  onInvitesPageChange(page: number): void {
    if (page < 0 || (this.inviteTotalPages && page >= this.inviteTotalPages)) {
      return;
    }
    this.invitePage = page;
    this.loadPendingInvites();
  }

  onInvitesPageSizeChange(size: number): void {
    this.invitePageSize = size;
    this.invitePage = 0;
    this.loadPendingInvites();
  }

  cancelInvite(invite: GroupInvite): void {
    if (!this.group || !invite) {
      return;
    }
    this.groupService.cancelInvite(this.group.id, invite.id).subscribe({
      next: () => {
        const key = 'groups.detail.membersTab.cancelSuccess';
        const msg = this.translate(key);
        this.toastService.showSuccess(msg !== key ? msg : 'Đã hủy lời mời thành công.');
        this.loadPendingInvites();
      },
      error: (e) => {
        console.error('Error cancelling invite:', e);
        const key = 'groups.detail.membersTab.cancelError';
        const msg = this.translate(key);
        this.toastService.showError(msg !== key ? msg : 'Không thể hủy lời mời. Vui lòng thử lại.');
      }
    });
  }

  getMemberRoleLabel(role: string): string {
    if (!role) {
      return '';
    }
    const key = `groups.detail.membersTab.role.${role}`;
    const translated = this.translate(key);
    return translated !== key ? translated : role;
  }

  isCurrentUser(member: GroupMember): boolean {
    return !!this.currentUser && member.userId === this.currentUser.id;
  }

  onChangeMemberRole(member: GroupMember): void {
    if (!this.group) {
      return;
    }
    this.selectedMember = member;
    this.selectedMemberRole = member.role || 'MEMBER';
    this.showEditMemberRoleModal = true;
  }

  onRemoveMember(member: GroupMember): void {
    if (!this.group) {
      return;
    }
    this.selectedMember = member;
    this.showDeleteMemberModal = true;
  }

  closeEditMemberRoleModal(): void {
    this.showEditMemberRoleModal = false;
    this.selectedMember = null;
    this.selectedMemberRole = '';
  }

  saveMemberRole(): void {
    if (!this.group || !this.selectedMember || this.updatingMemberRole) {
      return;
    }

    if (this.selectedMemberRole === this.selectedMember.role) {
      this.closeEditMemberRoleModal();
      return;
    }

    this.updatingMemberRole = true;
    this.groupService.updateMemberRole(
      this.group.id,
      this.selectedMember.userId,
      this.selectedMemberRole
    ).subscribe({
      next: (updatedMember) => {
        // Update the member in the list
        const index = this.members.findIndex(m => m.userId === updatedMember.userId);
        if (index !== -1) {
          this.members[index] = updatedMember;
        }
        this.toastService.showSuccess(this.translate('groups.detail.membersTab.updateRoleSuccess'));
        this.closeEditMemberRoleModal();
        this.updatingMemberRole = false;
      },
      error: (error) => {
        console.error('Error updating member role:', error);
        const errorMsg = error.error?.message || this.translate('groups.detail.membersTab.updateRoleError');
        this.toastService.showError(errorMsg);
        this.updatingMemberRole = false;
      }
    });
  }

  confirmRemoveMember(): void {
    if (!this.group || !this.selectedMember || this.removingMember) {
      return;
    }

    this.removingMember = true;
    this.groupService.removeMember(
      this.group.id,
      this.selectedMember.userId
    ).subscribe({
      next: () => {
        // Remove member from list
        this.members = this.members.filter(m => m.userId !== this.selectedMember!.userId);
        this.membersTotalElements = Math.max(0, this.membersTotalElements - 1);
        this.toastService.showSuccess(this.translate('groups.detail.membersTab.removeSuccess'));
        this.closeDeleteMemberModal();
        this.removingMember = false;
      },
      error: (error) => {
        console.error('Error removing member:', error);
        const errorMsg = error.error?.message || this.translate('groups.detail.membersTab.removeError');
        this.toastService.showError(errorMsg);
        this.removingMember = false;
      }
    });
  }

  closeDeleteMemberModal(): void {
    this.showDeleteMemberModal = false;
    this.selectedMember = null;
  }

  // ===== Group Planning Methods =====

  showCreatePlanningModal = false;
  showEditPlanningModal = false;
  editingPlanning: GroupPlanning | null = null;
  savingPlanning = false;
  deletingPlanningId: string | null = null;
  showDeletePlanningModal = false;
  planningToDelete: GroupPlanning | null = null;
  planningForm: any = {
    applyForWholeMonth: false,
    applyForWholeYear: false
  };
  loadingPlanningCategories = false;
  showNewPlanningCategoryInput = false;
  newPlanningCategory = '';
  planningCategories: any[] = [];

  openCreatePlanningModal(): void {
    this.editingPlanning = null;
    this.planningForm = {
      category: '',
      budgetAmount: null,
      planType: 'SHORT_TERM',
      startDate: '',
      endDate: '',
      repeatCycle: '',
      dayOfMonth: null,
      description: '',
      applyForWholeMonth: false,
      applyForWholeYear: false
    };
    this.prefillPlanningDates();
    this.showNewPlanningCategoryInput = false;
    this.newPlanningCategory = '';
    // Load categories if not already loaded
    if (this.planningCategories.length === 0) {
      this.loadPlanningCategories();
    }
    this.showCreatePlanningModal = true;
  }

  openEditPlanningModal(plan: GroupPlanning): void {
    this.editingPlanning = plan;
    // Determine applyForWholeMonth/Year based on repeatCycle and dayOfMonth
    const applyForWholeMonth = plan.repeatCycle === 'MONTHLY' && !plan.dayOfMonth;
    const applyForWholeYear = plan.repeatCycle === 'YEARLY' && !plan.dayOfMonth;

    this.planningForm = {
      category: plan.category || '',
      budgetAmount: plan.budgetAmount || null,
      planType: plan.planType || 'SHORT_TERM',
      startDate: plan.startDate || '',
      endDate: plan.endDate || '',
      repeatCycle: plan.repeatCycle || '',
      dayOfMonth: plan.dayOfMonth || null,
      description: plan.description || '',
      applyForWholeMonth: applyForWholeMonth,
      applyForWholeYear: applyForWholeYear
    };
    this.showNewPlanningCategoryInput = false;
    this.newPlanningCategory = '';
    // Load categories if not already loaded
    if (this.planningCategories.length === 0) {
      this.loadPlanningCategories();
    }
    this.showEditPlanningModal = true;
  }

  closePlanningModal(): void {
    this.showCreatePlanningModal = false;
    this.showEditPlanningModal = false;
    this.editingPlanning = null;
    this.planningForm = {
      applyForWholeMonth: false,
      applyForWholeYear: false
    };
    this.showNewPlanningCategoryInput = false;
    this.newPlanningCategory = '';
  }

  loadPlanningCategories(): void {
    if (!this.currentUser) return;
    const userId = this.currentUser.id.toString();
    this.loadingPlanningCategories = true;
    this.categoryService.getAllCategories(userId).subscribe({
      next: (categories) => {
        this.planningCategories = categories || [];
        this.loadingPlanningCategories = false;
      },
      error: (err) => {
        console.error('Error loading planning categories:', err);
        this.loadingPlanningCategories = false;
        this.toastService.showError(this.translate('planning.toast.categoryLoadError') || 'Không thể tải danh sách phân loại');
      }
    });
  }

  getGroupPlanningCategoryNames(): string[] {
    return this.planningCategories.map(c => c.name);
  }

  toggleNewPlanningCategoryInput(): void {
    this.showNewPlanningCategoryInput = !this.showNewPlanningCategoryInput;
    if (this.showNewPlanningCategoryInput) {
      this.planningForm.category = '';
    }
  }

  deleteGroupPlanningCategory(categoryName: string): void {
    if (!this.currentUser || !categoryName) return;

    // Check if it's a default category
    const category = this.planningCategories.find(c => c.name === categoryName);
    if (category && category.isDefault) {
      this.toastService.showError(this.translate('planning.toast.cannotDeleteDefault') || 'Không thể xóa loại mặc định');
      return;
    }

    const userId = this.currentUser.id.toString();
    this.categoryService.deleteCategory(userId, categoryName).subscribe({
      next: () => {
        // Remove from local list
        this.planningCategories = this.planningCategories.filter(c => c.name !== categoryName);
        // If this was the selected category, reset it
        if (this.planningForm.category === categoryName) {
          this.planningForm.category = '';
        }
        this.toastService.showSuccess(this.translate('planning.toast.deleteCategorySuccess') || 'Đã xóa loại thành công');
      },
      error: (err) => {
        console.error('Error deleting category:', err);
        const errorMsg = err.error?.error || (this.translate('planning.toast.deleteCategoryError') || 'Không thể xóa loại này');
        this.toastService.showError(errorMsg);
      }
    });
  }

  prefillPlanningDates(): void {
    const today = new Date();
    const first = new Date(today.getFullYear(), today.getMonth(), 1);
    const last = new Date(today.getFullYear(), today.getMonth() + 1, 0);
    this.planningForm.startDate = this.formatDateInput(first);
    this.planningForm.endDate = this.formatDateInput(last);
  }

  formatNumberWithDots(value: number | string | null | undefined): string {
    if (value === null || value === undefined || value === '') return '';
    let numStr = String(value).replace(/,/g, '').replace(/\./g, '').replace(/\s/g, '');
    numStr = numStr.replace(/^0+/, '') || '0';
    if (numStr === '0') return '';
    let formatted = '';
    for (let i = numStr.length - 1; i >= 0; i--) {
      const position = numStr.length - 1 - i;
      if (position > 0 && position % 3 === 0) formatted = ',' + formatted;
      formatted = numStr[i] + formatted;
    }
    return formatted;
  }

  onGroupBudgetInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const raw = input.value.replace(/,/g, '').replace(/[^0-9]/g, '');
    this.planningForm.budgetAmount = raw === '' ? null : Number(raw);
    input.value = this.formatNumberWithDots(raw);
  }

  formatDateInput(date: Date): string {
    return date.toISOString().split('T')[0];
  }

  createGroupPlanning(): void {
    if (!this.groupId || !this.currentUser) return;

    const categoryToUse = this.showNewPlanningCategoryInput && this.newPlanningCategory.trim()
      ? this.newPlanningCategory.trim()
      : this.planningForm.category;

    if (!categoryToUse || categoryToUse.trim() === '') {
      this.toastService.showError(this.translate('planning.toast.fillForm') || 'Vui lòng điền đầy đủ thông tin bắt buộc');
      return;
    }

    // If adding new category, save it first
    if (this.showNewPlanningCategoryInput && this.newPlanningCategory.trim()) {
      const userId = this.currentUser.id.toString();
      this.categoryService.addCategory(userId, this.newPlanningCategory.trim()).subscribe({
        next: (newCategory) => {
          this.planningCategories.push(newCategory);
          // Then create plan
          this.createGroupPlanningAfterCategorySave(categoryToUse);
        },
        error: (err) => {
          console.error('Error adding category:', err);
          // Still try to create plan with the category name
          this.createGroupPlanningAfterCategorySave(categoryToUse);
        }
      });
    } else {
      this.createGroupPlanningAfterCategorySave(categoryToUse);
    }
  }

  private createGroupPlanningAfterCategorySave(category: string): void {
    if (!this.groupId || !this.currentUser) return;

    // Handle dayOfMonth: if applyForWholeMonth/Year is checked, set dayOfMonth to null
    let dayOfMonth: number | undefined = undefined;
    if (this.planningForm.repeatCycle === 'MONTHLY' && this.planningForm.applyForWholeMonth) {
      dayOfMonth = undefined; // Will be null in backend
    } else if (this.planningForm.repeatCycle === 'YEARLY' && this.planningForm.applyForWholeYear) {
      dayOfMonth = undefined; // Will be null in backend
    } else {
      dayOfMonth = this.planningForm.dayOfMonth ? Number(this.planningForm.dayOfMonth) : undefined;
    }

    const payload: GroupPlanning = {
      groupId: this.groupId,
      category: category,
      budgetAmount: Number(this.planningForm.budgetAmount),
      planType: this.planningForm.planType,
      startDate: this.planningForm.startDate || undefined,
      endDate: this.planningForm.endDate || undefined,
      repeatCycle: this.planningForm.repeatCycle || undefined,
      dayOfMonth: dayOfMonth,
      createdBy: this.currentUser.id.toString(),
      description: this.planningForm.description || undefined
    };

    this.savingPlanning = true;
    this.groupPlanningService.create(payload).subscribe({
      next: () => {
        this.toastService.showSuccess(this.translate('groups.detail.planning.createSuccess') || 'Tạo kế hoạch thành công');
        this.savingPlanning = false;
        this.closePlanningModal();
        this.loadGroupPlans([]);
      },
      error: (error) => {
        console.error('Error creating group planning:', error);
        this.savingPlanning = false;
        this.toastService.showError(this.translate('groups.detail.planning.createError') || 'Lỗi khi tạo kế hoạch');
      }
    });
  }

  updateGroupPlanning(): void {
    if (!this.editingPlanning || !this.editingPlanning.id) return;

    const categoryToUse = this.showNewPlanningCategoryInput && this.newPlanningCategory.trim()
      ? this.newPlanningCategory.trim()
      : this.planningForm.category;

    if (!categoryToUse || categoryToUse.trim() === '') {
      this.toastService.showError(this.translate('planning.toast.fillForm') || 'Vui lòng điền đầy đủ thông tin bắt buộc');
      return;
    }

    // If adding new category, save it first
    if (this.showNewPlanningCategoryInput && this.newPlanningCategory.trim()) {
      const userId = this.currentUser!.id.toString();
      this.categoryService.addCategory(userId, this.newPlanningCategory.trim()).subscribe({
        next: (newCategory) => {
          this.planningCategories.push(newCategory);
          // Then update plan
          this.updateGroupPlanningAfterCategorySave(categoryToUse);
        },
        error: (err) => {
          console.error('Error adding category:', err);
          // Still try to update plan with the category name
          this.updateGroupPlanningAfterCategorySave(categoryToUse);
        }
      });
    } else {
      this.updateGroupPlanningAfterCategorySave(categoryToUse);
    }
  }

  private updateGroupPlanningAfterCategorySave(category: string): void {
    if (!this.editingPlanning || !this.editingPlanning.id) return;

    // Handle dayOfMonth: if applyForWholeMonth/Year is checked, set dayOfMonth to null
    let dayOfMonth: number | undefined = undefined;
    if (this.planningForm.repeatCycle === 'MONTHLY' && this.planningForm.applyForWholeMonth) {
      dayOfMonth = undefined; // Will be null in backend
    } else if (this.planningForm.repeatCycle === 'YEARLY' && this.planningForm.applyForWholeYear) {
      dayOfMonth = undefined; // Will be null in backend
    } else {
      dayOfMonth = this.planningForm.dayOfMonth ? Number(this.planningForm.dayOfMonth) : undefined;
    }

    const payload: Partial<GroupPlanning> = {
      category: category,
      budgetAmount: Number(this.planningForm.budgetAmount),
      planType: this.planningForm.planType,
      startDate: this.planningForm.startDate || undefined,
      endDate: this.planningForm.endDate || undefined,
      repeatCycle: this.planningForm.repeatCycle || undefined,
      dayOfMonth: dayOfMonth,
      description: this.planningForm.description || undefined
    };

    this.savingPlanning = true;
    this.groupPlanningService.update(this.editingPlanning.id, payload).subscribe({
      next: () => {
        this.toastService.showSuccess(this.translate('groups.detail.planning.updateSuccess') || 'Cập nhật kế hoạch thành công');
        this.savingPlanning = false;
        this.closePlanningModal();
        this.loadGroupPlans([]);
      },
      error: (error) => {
        console.error('Error updating group planning:', error);
        this.savingPlanning = false;
        this.toastService.showError(this.translate('groups.detail.planning.updateError') || 'Lỗi khi cập nhật kế hoạch');
      }
    });
  }

  deleteGroupPlanning(plan: GroupPlanning): void {
    if (!plan.id) return;
    this.planningToDelete = plan;
    this.showDeletePlanningModal = true;
  }

  closeDeletePlanningModal(): void {
    if (this.deletingPlanningId) {
      return; // Prevent closing while deleting
    }
    this.showDeletePlanningModal = false;
    this.planningToDelete = null;
  }

  confirmDeletePlanning(): void {
    if (!this.planningToDelete || !this.planningToDelete.id || this.deletingPlanningId) {
      return;
    }
    this.deletingPlanningId = this.planningToDelete.id;
    this.groupPlanningService.delete(this.planningToDelete.id).subscribe({
      next: () => {
        this.toastService.showSuccess(this.translate('groups.detail.planning.deleteSuccess') || 'Xóa kế hoạch thành công');
        this.deletingPlanningId = null;
        this.showDeletePlanningModal = false;
        this.planningToDelete = null;
        this.loadGroupPlans([]);
      },
      error: (error) => {
        console.error('Error deleting group planning:', error);
        this.deletingPlanningId = null;
        this.toastService.showError(this.translate('groups.detail.planning.deleteError') || 'Lỗi khi xóa kế hoạch');
      }
    });
  }

  isPlanningFormValid(): boolean {
    // Check required fields
    if (!this.planningForm.category && !this.newPlanningCategory.trim()) {
      return false;
    }
    if (!this.planningForm.budgetAmount || this.planningForm.budgetAmount <= 0) {
      return false;
    }

    // Check plan type specific validations
    if (this.planningForm.planType === 'SHORT_TERM') {
      if (!this.planningForm.startDate || !this.planningForm.endDate) {
        return false;
      }
    } else if (this.planningForm.planType === 'LONG_TERM') {
      if (!this.planningForm.startDate) {
        return false;
      }
    } else if (this.planningForm.planType === 'RECURRING') {
      // For RECURRING, repeatCycle is required
      if (!this.planningForm.repeatCycle) {
        return false;
      }

      // Check dayOfMonth requirements based on repeatCycle
      if (this.planningForm.repeatCycle === 'QUARTERLY') {
        // QUARTERLY always requires dayOfMonth
        if (!this.planningForm.dayOfMonth) {
          return false;
        }
      } else if (this.planningForm.repeatCycle === 'MONTHLY') {
        // MONTHLY requires dayOfMonth unless applyForWholeMonth is checked
        if (!this.planningForm.applyForWholeMonth && !this.planningForm.dayOfMonth) {
          return false;
        }
      } else if (this.planningForm.repeatCycle === 'YEARLY') {
        // YEARLY requires dayOfMonth unless applyForWholeYear is checked
        if (!this.planningForm.applyForWholeYear && !this.planningForm.dayOfMonth) {
          return false;
        }
      }
    }

    return true;
  }

  formatPlanningRange(plan: GroupPlanning): string {
    if (plan.planType === 'RECURRING') {
      if (plan.repeatCycle) {
        const cycleMap: any = {
          'MONTHLY': 'Hàng tháng',
          'QUARTERLY': 'Hàng quý',
          'YEARLY': 'Hàng năm'
        };
        return cycleMap[plan.repeatCycle] || plan.repeatCycle;
      }
      return 'Định kỳ';
    } else if (plan.planType === 'LONG_TERM') {
      const start = plan.startDate ? this.formatDisplayDate(plan.startDate) : '';
      const end = plan.endDate ? this.formatDisplayDate(plan.endDate) : '';
      return start ? (end ? `${start} - ${end}` : `${start} - ...`) : '';
    } else {
      const start = plan.startDate ? this.formatDisplayDate(plan.startDate) : '';
      const end = plan.endDate ? this.formatDisplayDate(plan.endDate) : '';
      return start && end ? `${start} - ${end}` : '';
    }
  }

  formatDisplayDate(dateStr?: string): string {
    if (!dateStr) return '';
    const parsed = new Date(dateStr);
    if (Number.isNaN(parsed.getTime())) {
      return dateStr;
    }
    const lang = this.languageService.getCurrentLanguage() === 'en' ? 'en-US' : 'vi-VN';
    return parsed.toLocaleDateString(lang);
  }

  trackByPlanning(index: number, plan: GroupPlanning): string {
    return plan.id || `${plan.category}-${index}`;
  }

  iconForPlanning(category: string | undefined): string {
    if (!category) return 'fa-folder-open';
    const c = category.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim();
    if (c.includes('an') || c.includes('uong') || c.includes('food') || c.includes('eat')) {
      return 'fa-cutlery';
    }
    if (c.includes('giao thong') || c.includes('travel') || c.includes('xe') || c.includes('car')) {
      return 'fa-car';
    }
    if (c.includes('mua') || c.includes('shop') || c.includes('buy')) {
      return 'fa-shopping-bag';
    }
    if (c.includes('nha') || c.includes('rent') || c.includes('home')) {
      return 'fa-home';
    }
    if (c.includes('giai tri') || c.includes('entertain') || c.includes('movie')) {
      return 'fa-film';
    }
    if (c.includes('hoc') || c.includes('education') || c.includes('study')) {
      return 'fa-book';
    }
    if (c.includes('suc khoe') || c.includes('health') || c.includes('medical')) {
      return 'fa-heartbeat';
    }
    return 'fa-folder-open';
  }

  // ===== ANALYSIS TAB METHODS =====

  private initAnalysisTab(): void {
    // Initialize available years
    const currentYear = new Date().getFullYear();
    for (let i = currentYear - 4; i <= currentYear + 1; i++) {
      this.analysisAvailableYears.push(i);
    }
    
    // Update time filter labels based on language
    this.updateAnalysisTimeFilterLabels();
    
    // Generate initial calendar
    this.generateAnalysisCalendar();
  }

  private updateAnalysisTimeFilterLabels(): void {
    this.analysisTimeFilters = [
      { value: 'thisMonth', label: this.translate('statistics.timeFilters.thisMonth') || 'Tháng này' },
      { value: 'lastMonth', label: this.translate('statistics.timeFilters.lastMonth') || 'Tháng trước' },
      { value: 'thisQuarter', label: this.translate('statistics.timeFilters.thisQuarter') || 'Quý này' },
      { value: 'lastQuarter', label: this.translate('statistics.timeFilters.lastQuarter') || 'Quý trước' },
      { value: 'custom', label: this.translate('statistics.timeFilters.custom') || 'Tùy chọn' }
    ];
    
    this.analysisWeekDays = [
      this.translate('statistics.weekDays.sun') || 'CN',
      this.translate('statistics.weekDays.mon') || 'T2',
      this.translate('statistics.weekDays.tue') || 'T3',
      this.translate('statistics.weekDays.wed') || 'T4',
      this.translate('statistics.weekDays.thu') || 'T5',
      this.translate('statistics.weekDays.fri') || 'T6',
      this.translate('statistics.weekDays.sat') || 'T7'
    ];
  }

  private loadAnalysisData(): void {
    if (this.groupBankAccounts.length === 0) {
      return;
    }
    
    const bankAccountIds = this.groupBankAccounts
      .map(a => a.bankAccountId || a.accountId?.toString())
      .filter((id): id is string => !!id);
    
    if (bankAccountIds.length === 0) {
      return;
    }
    
    this.loadAnalysisSummary(bankAccountIds);
    this.loadAnalysisCashflow(bankAccountIds);
    this.loadAnalysisBreakdown(bankAccountIds, this.analysisBreakdownMode);
    this.loadAnalysisBigSpends(bankAccountIds);
    this.loadAnalysisVariance(bankAccountIds, this.analysisVarianceMode);
    this.loadAnalysisHeatmap(bankAccountIds);
  }

  private getAnalysisDateRange(): { startDate: string; endDate: string } {
    const today = new Date();
    const pad = (n: number) => n.toString().padStart(2, '0');
    const toStr = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

    if (this.analysisTimeFilter === 'custom') {
      if (this.analysisCustomStartDate && this.analysisCustomEndDate) {
        return { startDate: this.analysisCustomStartDate, endDate: this.analysisCustomEndDate };
      }
    }

    if (this.analysisTimeFilter === 'lastMonth') {
      const firstDayPrev = new Date(today.getFullYear(), today.getMonth() - 1, 1);
      const lastDayPrev = new Date(today.getFullYear(), today.getMonth(), 0);
      return { startDate: toStr(firstDayPrev), endDate: toStr(lastDayPrev) };
    }

    if (this.analysisTimeFilter === 'lastQuarter') {
      const currentMonth = today.getMonth();
      const currentQuarter = Math.floor(currentMonth / 3);
      const lastQuarter = currentQuarter === 0 ? 3 : currentQuarter - 1;
      const lastQuarterStartMonth = lastQuarter * 3;
      const lastQuarterYear = currentQuarter === 0 ? today.getFullYear() - 1 : today.getFullYear();
      const firstDayLastQuarter = new Date(lastQuarterYear, lastQuarterStartMonth, 1);
      const lastDayLastQuarter = new Date(lastQuarterYear, lastQuarterStartMonth + 3, 0);
      return { startDate: toStr(firstDayLastQuarter), endDate: toStr(lastDayLastQuarter) };
    }

    if (this.analysisTimeFilter === 'thisQuarter') {
      const currentMonth = today.getMonth();
      const quarterStartMonth = currentMonth - (currentMonth % 3);
      const firstDayQuarter = new Date(today.getFullYear(), quarterStartMonth, 1);
      const lastDayQuarter = new Date(today.getFullYear(), quarterStartMonth + 3, 0);
      return { startDate: toStr(firstDayQuarter), endDate: toStr(lastDayQuarter) };
    }

    // Default: thisMonth
    const firstDay = new Date(today.getFullYear(), today.getMonth(), 1);
    const lastDay = new Date(today.getFullYear(), today.getMonth() + 1, 0);
    return { startDate: toStr(firstDay), endDate: toStr(lastDay) };
  }

  applyAnalysisTimeFilter(): void {
    if (this.groupBankAccounts.length === 0) return;
    this.loadAnalysisData();
  }

  private loadAnalysisSummary(bankAccountIds: string[]): void {
    const { startDate, endDate } = this.getAnalysisDateRange();
    this.loadingAnalysisSummary = true;
    
    this.transactionService.getGroupDashboardSummary(bankAccountIds, startDate, endDate).subscribe({
      next: (res) => {
        this.analysisSummaries = this.mapAnalysisSummary(res);
      },
      error: (err) => {
        console.error('Error loading analysis summary:', err);
      },
      complete: () => this.loadingAnalysisSummary = false
    });
  }

  private mapAnalysisSummary(res: any): typeof this.analysisSummaries {
    if (!res) return [];
    
    const formatCurrency = (v: number) => `${Math.round(v || 0).toLocaleString('vi-VN')} đ`;
    const formatPct = (v: number | null) => v === null ? '—' : `${v >= 0 ? '+' : ''}${v.toFixed(1)}%`;

    return [
      {
        title: this.translate('statistics.summary.totalIncome') || 'Tổng thu nhập',
        value: formatCurrency(res.totalIncome?.current ?? 0),
        subLabel: this.translate('statistics.summary.comparedToPrevious') || 'so với kỳ trước',
        trend: formatPct(res.totalIncome?.changePct ?? 0),
        trendDirection: res.totalIncome?.direction ?? 'flat'
      },
      {
        title: this.translate('statistics.summary.totalExpense') || 'Tổng chi tiêu',
        value: formatCurrency(res.totalExpense?.current ?? 0),
        subLabel: this.translate('statistics.summary.comparedToPrevious') || 'so với kỳ trước',
        trend: formatPct(res.totalExpense?.changePct ?? 0),
        trendDirection: res.totalExpense?.direction ?? 'flat'
      },
      {
        title: this.translate('statistics.summary.netBalance') || 'Số dư ròng',
        value: formatCurrency(res.netBalance?.current ?? 0),
        subLabel: this.translate('statistics.summary.savingRate') || 'Tỷ lệ tiết kiệm',
        trend: formatPct(res.savingRate?.changePct ?? 0),
        trendDirection: res.netBalance?.direction ?? 'flat'
      },
      {
        title: this.translate('statistics.summary.averageDailyExpense') || 'Chi tiêu TB/ngày',
        value: formatCurrency(res.averageDailyExpense?.current ?? 0),
        subLabel: this.translate('statistics.summary.comparedToPrevious') || 'so với kỳ trước',
        trend: formatPct(res.averageDailyExpense?.changePct ?? 0),
        trendDirection: res.averageDailyExpense?.direction ?? 'flat'
      }
    ];
  }

  private loadAnalysisCashflow(bankAccountIds: string[]): void {
    const { startDate, endDate } = this.getAnalysisDateRange();
    this.loadingAnalysisCashflow = true;
    
    this.transactionService.getGroupCashflow(bankAccountIds, startDate, endDate).subscribe({
      next: (data) => {
        this.analysisCashflow = data || [];
        setTimeout(() => this.renderAnalysisCashflowChart());
      },
      error: (err) => {
        console.error('Error loading analysis cashflow:', err);
      },
      complete: () => this.loadingAnalysisCashflow = false
    });
  }

  private renderAnalysisCashflowChart(): void {
    const H: any = (window as any)['Highcharts'];
    if (!H) return;
    
    const { startDate } = this.getAnalysisDateRange();
    const start = new Date(startDate);
    const year = start.getFullYear();
    
    // Group cashflow data by year-month
    const byYearMonth = new Map<string, any>();
    this.analysisCashflow.forEach(p => {
      const key = `${p.year}-${p.month}`;
      byYearMonth.set(key, p);
    });
    
    // Build months array
    const months = Array.from({ length: 12 }, (_, i) => {
      const m = i + 1;
      const key = `${year}-${m}`;
      const p = byYearMonth.get(key);
      return {
        month: m,
        label: this.getMonthLabel(m, year),
        income: p?.totalIncome || 0,
        expense: p?.totalExpense || 0,
        balance: p?.balance || 0
      };
    });

    const categories = months.map(m => m.label);
    const expenseNegative = months.map(m => -Math.abs(m.expense || 0));
    const income = months.map(m => m.income || 0);
    const balance = months.map(m => m.balance || 0);

    H.chart('group-cashflow-chart', {
      chart: { type: 'column', backgroundColor: 'transparent', style: { fontFamily: 'Be Vietnam Pro, sans-serif' } },
      title: { text: undefined },
      xAxis: { categories, lineColor: '#334155', labels: { style: { color: '#94a3b8' } } },
      yAxis: {
        title: { text: undefined },
        gridLineColor: 'rgba(255,255,255,0.18)',
        gridLineWidth: 1,
        plotLines: [{ value: 0, color: '#a3b1c6', width: 2, zIndex: 5 }],
        labels: { style: { color: '#94a3b8' }, formatter: function(this: any) { return H.numberFormat(this.value, 0, '.', ',') + '₫'; } }
      },
      legend: { itemStyle: { color: '#cbd5e1' } },
      credits: { enabled: false },
      exporting: { enabled: false },
      tooltip: {
        shared: false,
        backgroundColor: 'rgba(15,23,42,0.95)',
        borderColor: '#93c5fd',
        style: { color: '#e2e8f0' },
        pointFormatter: function(this: any) { return '<span style="color:' + this.color + '">●</span> ' + this.series.name + ': <b>' + H.numberFormat(this.y, 0, '.', ',') + '₫</b>'; }
      },
      plotOptions: {
        column: { borderRadius: 6, grouping: true, borderWidth: 0 }
      },
      series: [
        { name: this.translate('overview.income') || 'Thu', data: income, color: '#4E89FF' },
        { name: this.translate('overview.expense') || 'Chi', data: expenseNegative, color: '#FFC043' },
        { name: this.translate('overview.balance') || 'Cân bằng', data: balance, color: '#22c55e' }
      ]
    });
  }

  private getMonthLabel(month: number, year: number): string {
    const map = ['', 'T1', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'T8', 'T9', 'T10', 'T11', 'T12'];
    if (this.languageService.getCurrentLanguage() === 'en') {
      const em = ['', 'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
      return `${em[month]} ${year}`;
    }
    return `${map[month]} ${year}`;
  }

  private loadAnalysisBreakdown(bankAccountIds: string[], mode: 'expense' | 'income'): void {
    const { startDate, endDate } = this.getAnalysisDateRange();
    this.loadingAnalysisBreakdown = true;
    
    this.transactionService.getGroupCategoryBreakdown(bankAccountIds, mode, startDate, endDate).subscribe({
      next: (res) => {
        if (mode === 'expense') {
          this.analysisExpenseBreakdown = res.items || [];
          this.analysisExpenseTotal = res.totalAmount || 0;
        } else {
          this.analysisIncomeBreakdown = res.items || [];
          this.analysisIncomeTotal = res.totalAmount || 0;
        }
        this.updateAnalysisPieChart();
      },
      error: (err) => {
        console.error('Error loading analysis breakdown:', err);
      },
      complete: () => this.loadingAnalysisBreakdown = false
    });
  }

  private updateAnalysisPieChart(): void {
    const list = this.getAnalysisCurrentBreakdown();
    if (!list || list.length === 0) {
      this.analysisChartOptions = {};
      return;
    }

    const series = list.map(item => item.amount || 0);
    const labels = list.map(item => item.category || 'Chưa phân loại');
    const colors = list.map((_, idx) => this.analysisBreakdownColors[idx % this.analysisBreakdownColors.length]);

    this.analysisChartOptions = {
      series: series,
      chart: {
        type: 'pie',
        width: 220,
        height: 220,
        background: 'transparent'
      },
      labels: labels,
      colors: colors,
      theme: { mode: 'dark', palette: 'palette1' },
      legend: { show: false },
      dataLabels: { enabled: false },
      responsive: [
        { breakpoint: 600, options: { chart: { width: 220, height: 220 } } }
      ],
      tooltip: {
        y: {
          formatter: (val: number) => `${Math.round(val).toLocaleString('vi-VN')} đ`
        }
      }
    };
  }

  getAnalysisCurrentBreakdown(): CategoryItem[] {
    return this.analysisBreakdownMode === 'expense' ? this.analysisExpenseBreakdown : this.analysisIncomeBreakdown;
  }

  getAnalysisCategoryTotal(): number {
    return this.analysisBreakdownMode === 'expense' ? this.analysisExpenseTotal : this.analysisIncomeTotal;
  }

  getAnalysisCategoryTotalFormatted(): string {
    const total = this.getAnalysisCategoryTotal();
    if (this.loadingAnalysisBreakdown) return '—';
    if (total === 0 && this.getAnalysisCurrentBreakdown().length === 0) return '—';
    return `${Math.round(total).toLocaleString('vi-VN')} đ`;
  }

  setAnalysisActiveCategory(index: number): void {
    this.analysisActiveCategoryIndex = index;
  }

  clearAnalysisActiveCategory(): void {
    this.analysisActiveCategoryIndex = null;
  }

  getAnalysisActiveCategoryLabel(): string {
    if (this.analysisActiveCategoryIndex === null) return '';
    return this.getAnalysisCurrentBreakdown()[this.analysisActiveCategoryIndex]?.category || '';
  }

  getAnalysisActiveCategoryPercent(): number {
    if (this.analysisActiveCategoryIndex === null) return 0;
    return this.getAnalysisCurrentBreakdown()[this.analysisActiveCategoryIndex]?.percentage || 0;
  }

  onToggleAnalysisBreakdown(mode: 'expense' | 'income'): void {
    if (this.analysisBreakdownMode === mode) return;
    this.analysisBreakdownMode = mode;
    
    const list = mode === 'expense' ? this.analysisExpenseBreakdown : this.analysisIncomeBreakdown;
    if (!list || list.length === 0) {
      const bankAccountIds = this.groupBankAccounts
        .map(a => a.bankAccountId || a.accountId?.toString())
        .filter((id): id is string => !!id);
      if (bankAccountIds.length > 0) {
        this.loadAnalysisBreakdown(bankAccountIds, mode);
      }
    } else {
      this.updateAnalysisPieChart();
    }
  }

  private loadAnalysisBigSpends(bankAccountIds: string[]): void {
    const { startDate, endDate } = this.getAnalysisDateRange();
    this.loadingAnalysisBigSpends = true;
    
    this.transactionService.getGroupTopBiggestTransactions(bankAccountIds, startDate, endDate, 6).subscribe({
      next: (transactions) => {
        this.analysisBigSpends = transactions.map(tx => ({
          label: tx.transactionContent || this.translate('statistics.biggestExpenses.noDescription') || 'Không có mô tả',
          amount: `${Math.round(-(tx.amountOut || 0)).toLocaleString('vi-VN')} đ`,
          icon: this.getCategoryIcon(tx.category),
          date: this.formatDate(tx.transactionDate),
          category: tx.category || this.translate('statistics.biggestExpenses.uncategorized') || 'Chưa phân loại'
        }));
      },
      error: (err) => {
        console.error('Error loading biggest spends:', err);
        this.analysisBigSpends = [];
      },
      complete: () => this.loadingAnalysisBigSpends = false
    });
  }

  private getCategoryIcon(category?: string): string {
    const iconMap: { [key: string]: string } = {
      'Mua sắm': '🛍️', 'Ăn uống': '🍔', 'Di chuyển': '🚗', 'Giải trí': '🎮',
      'Y tế': '💊', 'Giáo dục': '📚', 'Nhà cửa': '🏠', 'Điện nước': '💡',
      'Internet': '📶', 'Điện thoại': '📱', 'Quần áo': '👕', 'Làm đẹp': '💄',
      'Du lịch': '✈️', 'Đầu tư': '💰', 'Tiết kiệm': '🏦', 'Khác': '📦'
    };
    return iconMap[category || ''] || '💳';
  }

  private loadAnalysisVariance(bankAccountIds: string[], mode: 'expense' | 'income'): void {
    const { startDate: currentStart, endDate: currentEnd } = this.getAnalysisDateRange();
    
    // Calculate previous period dates
    const current = new Date(currentStart);
    const end = new Date(currentEnd);
    const durationMs = end.getTime() - current.getTime();
    const previous = new Date(current.getTime() - durationMs);
    
    const pad = (n: number) => n.toString().padStart(2, '0');
    const toStr = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    
    const previousStart = toStr(previous);
    const previousEnd = toStr(new Date(previous.getTime() + durationMs - 24 * 60 * 60 * 1000));
    
    this.loadingAnalysisVariance = true;
    this.transactionService.getGroupCategoryVariance(
      bankAccountIds, mode, currentStart, currentEnd, previousStart, previousEnd
    ).subscribe({
      next: (res) => {
        this.analysisSpendingChanges = (res.items || []).slice(0, 4).map(item => ({
          label: item.category,
          actual: this.formatCurrencyShort(item.currentAmount),
          delta: this.formatCurrencyShort(Math.abs(item.delta)),
          deltaPct: `${item.delta >= 0 ? '+' : '-'}${Math.abs(item.deltaPercentage).toFixed(0)}%`,
          trend: item.trend as 'up' | 'down' | 'flat',
          icon: this.getCategoryIcon(item.category)
        }));
      },
      error: (err) => {
        console.error('Error loading variance:', err);
        this.analysisSpendingChanges = [];
      },
      complete: () => this.loadingAnalysisVariance = false
    });
  }

  private formatCurrencyShort(amount: number): string {
    const absAmount = Math.abs(amount);
    if (absAmount >= 1000000000) return `${(absAmount / 1000000000).toFixed(1)}tỷ`;
    if (absAmount >= 1000000) return `${(absAmount / 1000000).toFixed(1)}tr`;
    if (absAmount >= 1000) return `${(absAmount / 1000).toFixed(0)}k`;
    return `${absAmount.toFixed(0)}đ`;
  }

  onToggleAnalysisVariance(mode: 'expense' | 'income'): void {
    if (this.analysisVarianceMode === mode) return;
    this.analysisVarianceMode = mode;
    
    const bankAccountIds = this.groupBankAccounts
      .map(a => a.bankAccountId || a.accountId?.toString())
      .filter((id): id is string => !!id);
    if (bankAccountIds.length > 0) {
      this.loadAnalysisVariance(bankAccountIds, mode);
    }
  }

  private loadAnalysisHeatmap(bankAccountIds: string[]): void {
    const year = this.analysisHeatmapYear;
    const month = this.analysisHeatmapMonth;
    
    this.loadingAnalysisHeatmap = true;
    this.generateAnalysisCalendarForDate(year, month);
    
    this.transactionService.getGroupExpenseHeatmap(bankAccountIds, year, month).subscribe({
      next: (heatmap) => {
        // Calculate first day offset for calendar positioning
        const firstDayOfWeek = new Date(year, month - 1, 1).getDay();
        
        // Update calendar days with real data
        (heatmap.days || []).forEach(dayData => {
          const dayOfMonth = parseInt(dayData.date.split('-')[2], 10);
          const calendarIndex = firstDayOfWeek + (dayOfMonth - 1);
          
          if (calendarIndex >= 0 && calendarIndex < this.analysisCalendarDays.length &&
              this.analysisCalendarDays[calendarIndex].day === dayOfMonth) {
            this.analysisCalendarDays[calendarIndex].level = dayData.level;
          }
        });
      },
      error: (err) => console.error('Error loading heatmap:', err),
      complete: () => this.loadingAnalysisHeatmap = false
    });
  }

  private generateAnalysisCalendar(): void {
    this.generateAnalysisCalendarForDate(this.analysisHeatmapYear, this.analysisHeatmapMonth);
  }

  private generateAnalysisCalendarForDate(year: number, month: number): void {
    const firstDay = new Date(year, month - 1, 1);
    const lastDay = new Date(year, month, 0);
    const daysInMonth = lastDay.getDate();
    const firstDayOfWeek = firstDay.getDay();
    
    this.analysisCalendarDays = [];
    
    // Add empty cells for days before the first day of the month
    for (let i = 0; i < firstDayOfWeek; i++) {
      this.analysisCalendarDays.push({ day: null, level: 0 });
    }
    
    // Add all days of the month
    for (let day = 1; day <= daysInMonth; day++) {
      this.analysisCalendarDays.push({ day, level: 0 });
    }
    
    // Fill remaining cells to complete the last week
    const totalCells = this.analysisCalendarDays.length;
    const remaining = totalCells % 7;
    if (remaining !== 0) {
      for (let i = 0; i < (7 - remaining); i++) {
        this.analysisCalendarDays.push({ day: null, level: 0 });
      }
    }
  }

  onAnalysisHeatmapDateChange(): void {
    const bankAccountIds = this.groupBankAccounts
      .map(a => a.bankAccountId || a.accountId?.toString())
      .filter((id): id is string => !!id);
    if (bankAccountIds.length > 0) {
      this.loadAnalysisHeatmap(bankAccountIds);
    }
  }
}

