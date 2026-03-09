import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Transaction {
  id?: string;
  bankBrandName?: string;
  accountNumber?: string;
  transactionDate?: string;
  amountOut?: number;
  amountIn?: number;
  accumulated?: number;
  transactionContent?: string;
  referenceNumber?: string;
  code?: string;
  subAccount?: string;
  bankAccountId?: string;
  userId?: string;
  transactionType?: string;
  category?: string;
}

export interface TransactionSummary {
  totalIncome: number;
  totalExpense: number;
  netAmount: number;
  transactionCount: number;
  averageAmount: number;
}

export interface TransactionStats {
  accountId: string;
  accountLabel: string;
  totalIncome: number;
  totalExpense: number;
  transactionCount: number;
}

export interface CashflowPoint {
  year: number;
  month: number; // 1-12
  totalIncome: number;
  totalExpense: number;
  balance: number; // income - expense
}

export interface Metric {
  current: number;
  previous: number;
  change: number;
  changePct: number | null;
  direction: 'up' | 'down' | 'flat';
}

export interface TransactionDashboardSummary {
  totalIncome: Metric;
  totalExpense: Metric;
  netBalance: Metric;
  averageDailyExpense: Metric;
  savingRate: Metric;
}

export interface CategoryItem {
  category: string;
  amount: number;
  percentage: number;
}

export interface CategoryBreakdown {
  totalAmount: number;
  items: CategoryItem[];
}

export interface CategoryVarianceItem {
  category: string;
  currentAmount: number;
  previousAmount: number;
  delta: number;
  deltaPercentage: number;
  trend: 'up' | 'down' | 'flat';
}

export interface CategoryVariance {
  items: CategoryVarianceItem[];
}

export interface DailyExpenseData {
  date: string; // yyyy-MM-dd
  amount: number;
  level: number; // 0=null, 1=low, 2=medium, 3=high, 4=critical
}

export interface ExpenseHeatmap {
  days: DailyExpenseData[];
  dailyLimit: number;
  limitMode: string; // "budget", "historical", "manual"
}

export interface DailyTrendData {
  date: string; // yyyy-MM-dd
  income: number;
  expense: number;
}

export interface TransactionFilterParams {
  userId?: string;
  accountId?: string;
  bankAccountId?: string;
  bankAccountIds?: string[];
  startDate?: string;
  endDate?: string;
  textSearch?: string;
  transactionType?: 'income' | 'expense' | '';
  category?: string;
  minAmount?: number;
  maxAmount?: number;
  page?: number;
  size?: number;
  // Export options (Phase 2)
  sortOrder?: 'dateAsc' | 'dateDesc' | 'amountAsc' | 'amountDesc';
  includeSummarySheet?: boolean;
  // Export options (Phase 3)
  selectedColumns?: string[];
  exportFormat?: 'excel' | 'pdf' | 'csv';
}

@Injectable({ providedIn: 'root' })
export class TransactionService {
  private apiPrefix = '/finances/api/v1';

  constructor(private http: HttpClient) { }

  private getHeaders(): HttpHeaders {
    const token = window.sessionStorage.getItem('Authorization');
    return new HttpHeaders({
      'Authorization': token || '',
      'Content-Type': 'application/json'
    });
  }

  getTransactionById(id: string): Observable<Transaction> {
    return this.http.get<Transaction>(`${environment.rooturl}${this.apiPrefix}/transactions/${id}`, {
      headers: this.getHeaders()
    });
  }

  createTransaction(userId: string, accountNumber: string, bankBrandName: string,
                   transactionDate: string, amountIn: number | null, amountOut: number | null,
                   transactionContent: string, referenceNumber: string, bankAccountId: string,
                   category: string): Observable<Transaction> {
    const body = {
      accountNumber,
      bankBrandName,
      transactionDate,
      amountIn: amountIn || null,
      amountOut: amountOut || null,
      transactionContent,
      referenceNumber,
      bankAccountId,
      category: category || 'không xác định'
    };
    return this.http.post<Transaction>(`${environment.rooturl}${this.apiPrefix}/transactions?userId=${userId}`, body, {
      headers: this.getHeaders()
    });
  }

  filterTransactions(params: TransactionFilterParams): Observable<any> {
    let httpParams = new HttpParams();

    if (params.userId) httpParams = httpParams.set('userId', params.userId);
    if (params.accountId) httpParams = httpParams.set('accountId', params.accountId);
    if (params.bankAccountId) httpParams = httpParams.set('bankAccountId', params.bankAccountId);
    if (params.bankAccountIds && params.bankAccountIds.length > 0) {
      params.bankAccountIds.forEach(id => httpParams = httpParams.append('bankAccountIds', id));
    }
    if (params.startDate) httpParams = httpParams.set('startDate', params.startDate);
    if (params.endDate) httpParams = httpParams.set('endDate', params.endDate);
    if (params.textSearch) httpParams = httpParams.set('textSearch', params.textSearch);
    if (params.transactionType) httpParams = httpParams.set('transactionType', params.transactionType);
    if (params.category) httpParams = httpParams.set('category', params.category);
    if (params.minAmount !== undefined) httpParams = httpParams.set('minAmount', params.minAmount.toString());
    if (params.maxAmount !== undefined) httpParams = httpParams.set('maxAmount', params.maxAmount.toString());
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page.toString());
    if (params.size !== undefined) httpParams = httpParams.set('size', params.size.toString());

    return this.http.get(`${environment.rooturl}${this.apiPrefix}/transactions`, {
      headers: this.getHeaders(),
      params: httpParams
    });
  }

  getSummary(userId: string, startDate?: string, endDate?: string): Observable<TransactionSummary> {
    let params = new HttpParams().set('userId', userId);
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);

    return this.http.get<TransactionSummary>(
      `${environment.rooturl}${this.apiPrefix}/transactions/summary`,
      { headers: this.getHeaders(), params }
    );
  }

  getSummaryByBankAccountIds(
    bankAccountIds: string[],
    startDate?: string,
    endDate?: string,
    textSearch?: string,
    transactionType?: 'income' | 'expense' | '',
    minAmount?: number,
    maxAmount?: number
  ): Observable<TransactionSummary> {
    let params = new HttpParams();
    if (bankAccountIds && bankAccountIds.length > 0) {
      bankAccountIds.forEach(id => params = params.append('bankAccountIds', id));
    }
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    if (textSearch) params = params.set('textSearch', textSearch);
    if (transactionType) params = params.set('transactionType', transactionType);
    if (minAmount !== undefined) params = params.set('minAmount', minAmount.toString());
    if (maxAmount !== undefined) params = params.set('maxAmount', maxAmount.toString());

    return this.http.get<TransactionSummary>(
      `${environment.rooturl}${this.apiPrefix}/transactions/summary`,
      { headers: this.getHeaders(), params }
    );
  }

  getTransactionStats(userId: string, startDate?: string, endDate?: string): Observable<TransactionStats[]> {
    let params = new HttpParams().set('userId', userId);
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);

    return this.http.get<TransactionStats[]>(
      `${environment.rooturl}${this.apiPrefix}/transactions/stats`,
      { headers: this.getHeaders(), params }
    );
  }

  getCashflow(userId: string, startDate?: string, endDate?: string): Observable<CashflowPoint[]> {
    let params = new HttpParams().set('userId', userId);
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);

    return this.http.get<CashflowPoint[]>(
      `${environment.rooturl}${this.apiPrefix}/transactions/cashflow`,
      { headers: this.getHeaders(), params }
    );
  }

  getDashboardSummary(userId: string, startDate?: string, endDate?: string): Observable<TransactionDashboardSummary> {
    let params = new HttpParams().set('userId', userId);
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);

    // New endpoint served by StatisticsController
    return this.http.get<TransactionDashboardSummary>(
      `${environment.rooturl}${this.apiPrefix.replace('/transactions', '')}/statistics/dashboard/summary`,
      { headers: this.getHeaders(), params }
    );
  }

  getCategoryBreakdown(userId: string, type: 'expense' | 'income', startDate?: string, endDate?: string): Observable<CategoryBreakdown> {
    let params = new HttpParams().set('userId', userId).set('type', type);
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);

    return this.http.get<CategoryBreakdown>(
      `${environment.rooturl}${this.apiPrefix.replace('/transactions', '')}/statistics/breakdown`,
      { headers: this.getHeaders(), params }
    );
  }

  getCategoryBreakdownByBankAccountIds(bankAccountIds: string[], type: 'expense' | 'income', startDate?: string, endDate?: string): Observable<CategoryBreakdown> {
    let params = new HttpParams().set('type', type);
    bankAccountIds.forEach(id => params = params.append('bankAccountIds', id));
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);

    return this.http.get<CategoryBreakdown>(
      `${environment.rooturl}${this.apiPrefix.replace('/transactions', '')}/statistics/breakdown/by-accounts`,
      { headers: this.getHeaders(), params }
    );
  }

  getTopBiggestTransactions(userId: string, startDate?: string, endDate?: string, limit: number = 6): Observable<Transaction[]> {
    let params = new HttpParams().set('userId', userId).set('limit', limit.toString());
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);

    return this.http.get<Transaction[]>(
      `${environment.rooturl}${this.apiPrefix.replace('/transactions', '')}/statistics/top-biggest`,
      { headers: this.getHeaders(), params }
    );
  }

  getCategoryVariance(userId: string, type: 'expense' | 'income',
                     currentStartDate?: string, currentEndDate?: string,
                     previousStartDate?: string, previousEndDate?: string): Observable<CategoryVariance> {
    let params = new HttpParams().set('userId', userId).set('type', type);
    if (currentStartDate) params = params.set('currentStartDate', currentStartDate);
    if (currentEndDate) params = params.set('currentEndDate', currentEndDate);
    if (previousStartDate) params = params.set('previousStartDate', previousStartDate);
    if (previousEndDate) params = params.set('previousEndDate', previousEndDate);

    return this.http.get<CategoryVariance>(
      `${environment.rooturl}${this.apiPrefix.replace('/transactions', '')}/statistics/variance`,
      { headers: this.getHeaders(), params }
    );
  }

  getExpenseHeatmap(userId: string, year: number, month: number, dailyLimit?: number): Observable<ExpenseHeatmap> {
    let params = new HttpParams().set('userId', userId).set('year', year.toString()).set('month', month.toString());
    if (dailyLimit !== undefined && dailyLimit !== null) {
      params = params.set('dailyLimit', dailyLimit.toString());
    }

    return this.http.get<ExpenseHeatmap>(
      `${environment.rooturl}${this.apiPrefix.replace('/transactions', '')}/statistics/expense-heatmap`,
      { headers: this.getHeaders(), params }
    );
  }

  exportCashflowExcel(userId: string, year?: number, startDate?: string, endDate?: string): Observable<Blob> {
    let params = new HttpParams();
    if (userId) params = params.set('userId', userId);
    if (year !== undefined) params = params.set('year', String(year));
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);

    return this.http.get(`${environment.rooturl}${this.apiPrefix}/transactions/cashflow/export/excel`, {
      headers: this.getHeaders(),
      params,
      responseType: 'blob'
    });
  }

  syncTransactions(userId: string): Observable<any> {
    return this.http.post(`${environment.rooturl}${this.apiPrefix}/sepay/sync/transactions?userId=${userId}`, {}, {
      headers: this.getHeaders()
    });
  }

  updateCategory(transactionId: string, category: string): Observable<Transaction> {
    const params = new HttpParams().set('category', category);
    return this.http.put<Transaction>(
      `${environment.rooturl}${this.apiPrefix}/transactions/${transactionId}/category`,
      {},
      { headers: this.getHeaders(), params }
    );
  }

  exportToExcel(params: TransactionFilterParams): Observable<Blob> {
    let httpParams = this.buildExportParams(params);

    return this.http.get(`${environment.rooturl}${this.apiPrefix}/transactions/export/excel`, {
      headers: this.getHeaders(),
      params: httpParams,
      responseType: 'blob'
    });
  }

  exportToPdf(params: TransactionFilterParams): Observable<Blob> {
    let httpParams = this.buildExportParams(params);

    return this.http.get(`${environment.rooturl}${this.apiPrefix}/transactions/export/pdf`, {
      headers: this.getHeaders(),
      params: httpParams,
      responseType: 'blob'
    });
  }

  exportToCsv(params: TransactionFilterParams): Observable<Blob> {
    let httpParams = this.buildExportParams(params);

    return this.http.get(`${environment.rooturl}${this.apiPrefix}/transactions/export/csv`, {
      headers: this.getHeaders(),
      params: httpParams,
      responseType: 'blob'
    });
  }

  private buildExportParams(params: TransactionFilterParams): HttpParams {
    let httpParams = new HttpParams();

    if (params.userId) httpParams = httpParams.set('userId', params.userId);
    if (params.accountId) httpParams = httpParams.set('accountId', params.accountId);
    if (params.bankAccountId) httpParams = httpParams.set('bankAccountId', params.bankAccountId);
    if (params.bankAccountIds && params.bankAccountIds.length > 0) {
      params.bankAccountIds.forEach(id => httpParams = httpParams.append('bankAccountIds', id));
    }
    if (params.startDate) httpParams = httpParams.set('startDate', params.startDate);
    if (params.endDate) httpParams = httpParams.set('endDate', params.endDate);
    if (params.textSearch) httpParams = httpParams.set('textSearch', params.textSearch);
    if (params.transactionType) httpParams = httpParams.set('transactionType', params.transactionType);
    if (params.category) httpParams = httpParams.set('category', params.category);
    if (params.minAmount !== undefined) httpParams = httpParams.set('minAmount', params.minAmount.toString());
    if (params.maxAmount !== undefined) httpParams = httpParams.set('maxAmount', params.maxAmount.toString());
    // Export options (Phase 2)
    if (params.sortOrder) httpParams = httpParams.set('sortOrder', params.sortOrder);
    if (params.includeSummarySheet !== undefined) httpParams = httpParams.set('includeSummarySheet', params.includeSummarySheet.toString());
    // Export options (Phase 3)
    if (params.selectedColumns && params.selectedColumns.length > 0) {
      params.selectedColumns.forEach(col => httpParams = httpParams.append('selectedColumns', col));
    }

    return httpParams;
  }

  // ===== REPORT TEMPLATES METHODS (Phase 3) =====

  getReportTemplates(userId: string): Observable<any[]> {
    return this.http.get<any[]>(`${environment.rooturl}${this.apiPrefix}/report-templates?userId=${userId}`, {
      headers: this.getHeaders()
    });
  }

  saveReportTemplate(template: any): Observable<any> {
    return this.http.post<any>(`${environment.rooturl}${this.apiPrefix}/report-templates`, template, {
      headers: this.getHeaders()
    });
  }

  deleteReportTemplate(templateId: string, userId: string): Observable<void> {
    const params = new HttpParams().set('userId', userId);
    return this.http.delete<void>(`${environment.rooturl}${this.apiPrefix}/report-templates/${templateId}`, {
      headers: this.getHeaders(),
      params: params
    });
  }

  // ===== EMAIL REPORTS METHODS (Phase 3) =====

  sendReportByEmail(params: TransactionFilterParams, recipientEmail: string): Observable<void> {
    let httpParams = this.buildExportParams(params);
    httpParams = httpParams.set('recipientEmail', recipientEmail);

    return this.http.post<void>(`${environment.rooturl}${this.apiPrefix}/transactions/email-report`, null, {
      headers: this.getHeaders(),
      params: httpParams
    });
  }

  // ===== SCHEDULED REPORTS METHODS (Phase 3) =====

  getReportSchedules(userId: string): Observable<any[]> {
    return this.http.get<any[]>(`${environment.rooturl}${this.apiPrefix}/report-schedules?userId=${userId}`, {
      headers: this.getHeaders()
    });
  }

  saveReportSchedule(schedule: any): Observable<any> {
    return this.http.post<any>(`${environment.rooturl}${this.apiPrefix}/report-schedules`, schedule, {
      headers: this.getHeaders()
    });
  }

  updateReportSchedule(scheduleId: string, schedule: any): Observable<any> {
    return this.http.put<any>(`${environment.rooturl}${this.apiPrefix}/report-schedules/${scheduleId}`, schedule, {
      headers: this.getHeaders()
    });
  }

  deleteReportSchedule(scheduleId: string, userId: string): Observable<void> {
    return this.http.delete<void>(`${environment.rooturl}${this.apiPrefix}/report-schedules/${scheduleId}?userId=${userId}`, {
      headers: this.getHeaders()
    });
  }

  // ===== REPORT HISTORY METHODS (Phase 5) =====

  getReportHistory(userId: string, page: number = 0, size: number = 10): Observable<any> {
    return this.http.get<any>(`${environment.rooturl}${this.apiPrefix}/report-history?userId=${userId}&page=${page}&size=${size}`, {
      headers: this.getHeaders()
    });
  }

  getAllReportHistory(userId: string): Observable<any[]> {
    return this.http.get<any[]>(`${environment.rooturl}${this.apiPrefix}/report-history/all?userId=${userId}`, {
      headers: this.getHeaders()
    });
  }

  getReportHistoryBySchedule(scheduleId: string): Observable<any[]> {
    return this.http.get<any[]>(`${environment.rooturl}${this.apiPrefix}/report-history/schedule/${scheduleId}`, {
      headers: this.getHeaders()
    });
  }

  getReportHistoryByStatus(userId: string, status: string): Observable<any[]> {
    return this.http.get<any[]>(`${environment.rooturl}${this.apiPrefix}/report-history/status?userId=${userId}&status=${status}`, {
      headers: this.getHeaders()
    });
  }

  getReportHistoryByDateRange(userId: string, startDate: string, endDate: string): Observable<any[]> {
    return this.http.get<any[]>(`${environment.rooturl}${this.apiPrefix}/report-history/date-range?userId=${userId}&startDate=${startDate}&endDate=${endDate}`, {
      headers: this.getHeaders()
    });
  }

  deleteReportHistory(historyId: string, userId: string): Observable<void> {
    return this.http.delete<void>(`${environment.rooturl}${this.apiPrefix}/report-history/${historyId}?userId=${userId}`, {
      headers: this.getHeaders()
    });
  }

  /**
   * Bulk delete multiple report history records
   */
  bulkDeleteReportHistory(historyIds: string[], userId: string): Observable<any> {
    return this.http.delete<any>(`${environment.rooturl}${this.apiPrefix}/report-history/bulk?userId=${userId}`, {
      headers: this.getHeaders(),
      body: historyIds
    });
  }

  // ===== GROUP STATISTICS METHODS =====

  /**
   * Get dashboard summary for a group (4 summary boxes)
   */
  getGroupDashboardSummary(bankAccountIds: string[], startDate?: string, endDate?: string): Observable<TransactionDashboardSummary> {
    let params = new HttpParams();
    bankAccountIds.forEach(id => params = params.append('bankAccountIds', id));
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);

    return this.http.get<TransactionDashboardSummary>(
      `${environment.rooturl}${this.apiPrefix.replace('/transactions', '')}/statistics/group/dashboard/summary`,
      { headers: this.getHeaders(), params }
    );
  }

  /**
   * Get monthly cashflow for a group
   */
  getGroupCashflow(bankAccountIds: string[], startDate?: string, endDate?: string): Observable<CashflowPoint[]> {
    let params = new HttpParams();
    bankAccountIds.forEach(id => params = params.append('bankAccountIds', id));
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);

    return this.http.get<CashflowPoint[]>(
      `${environment.rooturl}${this.apiPrefix.replace('/transactions', '')}/statistics/group/cashflow`,
      { headers: this.getHeaders(), params }
    );
  }

  /**
   * Get category breakdown for a group (expense or income)
   */
  getGroupCategoryBreakdown(bankAccountIds: string[], type: 'expense' | 'income', startDate?: string, endDate?: string): Observable<CategoryBreakdown> {
    let params = new HttpParams().set('type', type);
    bankAccountIds.forEach(id => params = params.append('bankAccountIds', id));
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);

    return this.http.get<CategoryBreakdown>(
      `${environment.rooturl}${this.apiPrefix.replace('/transactions', '')}/statistics/group/breakdown`,
      { headers: this.getHeaders(), params }
    );
  }

  /**
   * Get top biggest expense transactions for a group
   */
  getGroupTopBiggestTransactions(bankAccountIds: string[], startDate?: string, endDate?: string, limit: number = 6): Observable<Transaction[]> {
    let params = new HttpParams().set('limit', limit.toString());
    bankAccountIds.forEach(id => params = params.append('bankAccountIds', id));
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);

    return this.http.get<Transaction[]>(
      `${environment.rooturl}${this.apiPrefix.replace('/transactions', '')}/statistics/group/top-biggest`,
      { headers: this.getHeaders(), params }
    );
  }

  /**
   * Get category variance for a group (compare current vs previous period)
   */
  getGroupCategoryVariance(
    bankAccountIds: string[],
    type: 'expense' | 'income',
    currentStartDate?: string,
    currentEndDate?: string,
    previousStartDate?: string,
    previousEndDate?: string
  ): Observable<CategoryVariance> {
    let params = new HttpParams().set('type', type);
    bankAccountIds.forEach(id => params = params.append('bankAccountIds', id));
    if (currentStartDate) params = params.set('currentStartDate', currentStartDate);
    if (currentEndDate) params = params.set('currentEndDate', currentEndDate);
    if (previousStartDate) params = params.set('previousStartDate', previousStartDate);
    if (previousEndDate) params = params.set('previousEndDate', previousEndDate);

    return this.http.get<CategoryVariance>(
      `${environment.rooturl}${this.apiPrefix.replace('/transactions', '')}/statistics/group/variance`,
      { headers: this.getHeaders(), params }
    );
  }

  /**
   * Get daily expense heatmap for a group
   */
  getGroupExpenseHeatmap(bankAccountIds: string[], year: number, month: number, dailyLimit?: number): Observable<ExpenseHeatmap> {
    let params = new HttpParams().set('year', year.toString()).set('month', month.toString());
    bankAccountIds.forEach(id => params = params.append('bankAccountIds', id));
    if (dailyLimit !== undefined && dailyLimit !== null) {
      params = params.set('dailyLimit', dailyLimit.toString());
    }

    return this.http.get<ExpenseHeatmap>(
      `${environment.rooturl}${this.apiPrefix.replace('/transactions', '')}/statistics/group/expense-heatmap`,
      { headers: this.getHeaders(), params }
    );
  }

  // ===== REPORTS CHARTS DATA METHODS (Phase 3) =====

  /**
   * Get category summary with filters for reports charts
   */
  getCategorySummary(params: TransactionFilterParams): Observable<CategoryBreakdown> {
    let httpParams = this.buildExportParams(params);

    return this.http.get<CategoryBreakdown>(
      `${environment.rooturl}${this.apiPrefix}/transactions/category-summary`,
      { headers: this.getHeaders(), params: httpParams }
    );
  }

  /**
   * Get daily trend data with filters for reports charts
   */
  getDailyTrend(params: TransactionFilterParams): Observable<DailyTrendData[]> {
    let httpParams = this.buildExportParams(params);

    return this.http.get<DailyTrendData[]>(
      `${environment.rooturl}${this.apiPrefix}/transactions/daily-trend`,
      { headers: this.getHeaders(), params: httpParams }
    );
  }
}

