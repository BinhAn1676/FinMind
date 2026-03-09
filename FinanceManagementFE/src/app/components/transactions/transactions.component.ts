import { Component, OnInit, ChangeDetectorRef, OnDestroy } from '@angular/core';
import { LanguageService } from '../../services/language.service';
import { LayoutService } from '../../services/layout.service';
import { Subscription } from 'rxjs';
import { TransactionService, Transaction, TransactionSummary } from '../../services/transaction.service';
import { UserService } from '../../services/user.service';
import { User } from '../../model/user.model';
import { ToastService } from '../../services/toast.service';
import { CategoryService, Category } from '../../services/category.service';
import { AccountService } from '../../services/account.service';

@Component({
  selector: 'app-transactions',
  templateUrl: './transactions.component.html',
  styleUrls: ['./transactions.component.css']
})
export class TransactionsComponent implements OnInit, OnDestroy {
  transactions: Transaction[] = [];
  summary: TransactionSummary = {
    totalIncome: 0,
    totalExpense: 0,
    netAmount: 0,
    transactionCount: 0,
    averageAmount: 0
  };
  languageSub!: Subscription;
  userId!: string;
  loadingTransactions = false;
  loadingSummary = false;
  showDetail = false;
  transactionDetail: Transaction | null = null;
  loadingDetail = false;
  
  // Category management
  showCategoryModal = false;
  selectedTransactionForCategory: Transaction | null = null;
  selectedCategory = '';
  newCategory = '';
  showNewCategoryInput = false;
  categories: Category[] = [];
  loadingCategories = false;

  // Create transaction
  showCreateTransactionModal = false;
  newTransaction: any = {
    accountNumber: '',
    bankBrandName: '',
    transactionDate: '',
    amountIn: null,
    amountOut: null,
    amountInFormatted: '',
    amountOutFormatted: '',
    transactionContent: '',
    referenceNumber: '',
    bankAccountId: '',
    category: 'không xác định'
  };
  creatingTransaction = false;
  accounts: any[] = [];

  // Filters
  searchTerm = '';
  transactionType: 'income' | 'expense' | '' = '';
  startDate: Date | null = null;
  endDate: Date | null = null;
  selectedAccountId: string = '';
  amountRange: string = 'all'; // 'all' or range value
  syncingTransactions = false;

  // Pagination
  currentPage = 0;
  pageSize = 10;
  totalElements = 0;
  totalPages = 0;
  pageSizeOptions = [10, 20, 50, 100];

  constructor(
    public language: LanguageService,
    public layout: LayoutService,
    private cdr: ChangeDetectorRef,
    private transactionService: TransactionService,
    private userService: UserService,
    private toastService: ToastService,
    private categoryService: CategoryService,
    private accountService: AccountService
  ) { }

  ngOnInit(): void {
    this.userService.getUserInfo().subscribe({
      next: (user: User) => {
        this.userId = (user as any)?.id || (user as any)?.userId || '';
        if (this.userId) {
          this.loadCategories();
          this.loadAccounts();
          this.refreshAll();
        }
      }
    });
    this.languageSub = this.language.currentLanguage$.subscribe(() => {
      this.cdr.detectChanges();
    });
  }

  ngOnDestroy(): void {
    this.languageSub?.unsubscribe();
  }

  refreshAll() {
    this.filterTransactions();
    this.loadSummary();
  }

  filterTransactions() {
    if (!this.userId) return;
    this.loadingTransactions = true;

    // Parse amount range
    let minAmount: number | undefined;
    let maxAmount: number | undefined;
    if (this.amountRange && this.amountRange !== 'all') {
      const range = this.parseAmountRange(this.amountRange);
      minAmount = range.min;
      maxAmount = range.max;
    }

    const params = {
      userId: this.userId,
      accountId: this.selectedAccountId || undefined,
      textSearch: this.searchTerm || undefined,
      transactionType: (this.transactionType && this.transactionType.trim() !== '') ? this.transactionType : undefined,
      startDate: this.formatDateToString(this.startDate) || undefined,
      endDate: this.formatDateToString(this.endDate) || undefined,
      minAmount: minAmount,
      maxAmount: maxAmount,
      page: this.currentPage,
      size: this.pageSize
    };

    this.transactionService.filterTransactions(params).subscribe({
      next: (res) => {
        this.transactions = res?.content || [];
        this.totalElements = res?.totalElements || 0;
        this.totalPages = res?.totalPages || 0;
        this.loadingTransactions = false;
      },
      error: (err) => {
        console.error('Error loading transactions:', err);
        this.loadingTransactions = false;
        this.toastService.showError('Lỗi khi tải giao dịch. Vui lòng thử lại.');
      }
    });
  }

  loadSummary() {
    if (!this.userId) return;
    this.loadingSummary = true;

    this.transactionService.getSummary(
      this.userId,
      this.formatDateToString(this.startDate) || undefined,
      this.formatDateToString(this.endDate) || undefined
    ).subscribe({
      next: (s) => {
        this.summary = s;
        this.loadingSummary = false;
      },
      error: (err) => {
        console.error('Error loading summary:', err);
        this.loadingSummary = false;
      }
    });
  }

  onSearch() {
    this.currentPage = 0;
    this.filterTransactions();
  }

  onFilterChange() {
    this.currentPage = 0;
    this.filterTransactions();
    this.loadSummary();
  }

  clearFilters() {
    this.searchTerm = '';
    this.transactionType = '';
    this.startDate = null;
    this.endDate = null;
    this.selectedAccountId = '';
    this.amountRange = 'all';
    this.currentPage = 0;
    this.refreshAll();
  }

  parseAmountRange(range: string): { min: number | undefined, max: number | undefined } {
    if (!range || range === 'all') {
      return { min: undefined, max: undefined };
    }

    // Parse ranges like "1-250000", "below-500000", "500000-1000000", "above-100000000"
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

  getAmountRangeOptions() {
    return [
      { value: 'all', label: this.language.translate('transactions.amountRange.all') },
      { value: '1-250000', label: this.language.translate('transactions.amountRange.range1') },
      { value: 'below-500000', label: this.language.translate('transactions.amountRange.range2') },
      { value: '500000-1000000', label: this.language.translate('transactions.amountRange.range3') },
      { value: '1000000-2500000', label: this.language.translate('transactions.amountRange.range4') },
      { value: '2500000-5000000', label: this.language.translate('transactions.amountRange.range5') },
      { value: '5000000-7000000', label: this.language.translate('transactions.amountRange.range6') },
      { value: '7000000-10000000', label: this.language.translate('transactions.amountRange.range7') },
      { value: '10000000-20000000', label: this.language.translate('transactions.amountRange.range8') },
      { value: '20000000-50000000', label: this.language.translate('transactions.amountRange.range9') },
      { value: '50000000-100000000', label: this.language.translate('transactions.amountRange.range10') },
      { value: 'above-100000000', label: this.language.translate('transactions.amountRange.range11') }
    ];
  }

  manualSyncTransactions() {
    if (!this.userId) return;
    this.syncingTransactions = true;
    this.transactionService.syncTransactions(this.userId).subscribe({
      next: (res: any) => {
        this.syncingTransactions = false;
        if (res && res.success === false && res.message === 'No bank token available') {
          this.toastService.showError('Chưa kết nối SePay, không thể đồng bộ giao dịch!');
          return;
        }
        this.refreshAll();
        const syncSuccessMsg = this.language.translate('transactions.syncSuccess');
        this.toastService.showSuccess(syncSuccessMsg !== 'transactions.syncSuccess' ? syncSuccessMsg : 'Đồng bộ giao dịch thành công!');
      },
      error: () => {
        this.syncingTransactions = false;
        const syncErrorMsg = this.language.translate('transactions.syncError');
        this.toastService.showError(syncErrorMsg !== 'transactions.syncError' ? syncErrorMsg : 'Lỗi khi đồng bộ giao dịch. Vui lòng thử lại.');
      }
    });
  }

  onPageChange(page: number) {
    this.currentPage = page;
    this.filterTransactions();
  }

  onPageSizeChange(size: number) {
    this.pageSize = size;
    this.currentPage = 0;
    this.filterTransactions();
  }

  viewTransactionDetail(transaction: Transaction) {
    if (!transaction?.id) return;
    this.showDetail = true;
    this.loadingDetail = true;
    this.transactionService.getTransactionById(transaction.id).subscribe({
      next: (detail) => {
        this.transactionDetail = detail;
        this.loadingDetail = false;
      },
      error: () => {
        this.transactionDetail = transaction;
        this.loadingDetail = false;
      }
    });
  }

  closeDetailDialog() {
    this.showDetail = false;
    this.transactionDetail = null;
  }

  getTransactionAmount(transaction: Transaction): number {
    if (transaction.amountIn && transaction.amountIn > 0) {
      return transaction.amountIn;
    }
    if (transaction.amountOut && transaction.amountOut > 0) {
      return -transaction.amountOut;
    }
    return 0;
  }

  getTransactionType(transaction: Transaction): 'income' | 'expense' {
    if (transaction.amountIn && transaction.amountIn > 0) {
      return 'income';
    }
    return 'expense';
  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND'
    }).format(amount);
  }

  formatDate(dateString: string | undefined): string {
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
      return dateString;
    }
  }

  formatDateToString(date: Date | null): string {
    if (!date) return '';
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  get Math() {
    return Math;
  }

  loadCategories() {
    if (!this.userId) return;
    this.loadingCategories = true;
    this.categoryService.getAllCategories(this.userId).subscribe({
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

  openCategoryModal(transaction: Transaction) {
    this.selectedTransactionForCategory = transaction;
    this.selectedCategory = transaction.category || 'không xác định';
    this.newCategory = '';
    this.showNewCategoryInput = false;
    // Load categories if not already loaded
    if (this.categories.length === 0) {
      this.loadCategories();
    }
    this.showCategoryModal = true;
  }

  closeCategoryModal() {
    this.showCategoryModal = false;
    this.selectedTransactionForCategory = null;
    this.selectedCategory = '';
    this.newCategory = '';
    this.showNewCategoryInput = false;
  }

  toggleNewCategoryInput() {
    this.showNewCategoryInput = !this.showNewCategoryInput;
    if (this.showNewCategoryInput) {
      this.selectedCategory = '';
      this.newCategory = '';
    }
  }

  updateTransactionCategory() {
    if (!this.selectedTransactionForCategory || !this.selectedTransactionForCategory.id || !this.userId) {
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
      this.categoryService.addCategory(this.userId, this.newCategory.trim()).subscribe({
        next: (newCategory) => {
          this.categories.push(newCategory);
          // Then update transaction category
          this.updateTransactionCategoryAfterSave(categoryToUpdate);
        },
        error: (err) => {
          console.error('Error adding category:', err);
          // Still try to update transaction with the category name
          this.updateTransactionCategoryAfterSave(categoryToUpdate);
        }
      });
    } else {
      this.updateTransactionCategoryAfterSave(categoryToUpdate);
    }
  }

  private updateTransactionCategoryAfterSave(categoryToUpdate: string) {
    if (!this.selectedTransactionForCategory || !this.selectedTransactionForCategory.id) {
      return;
    }

    this.transactionService.updateCategory(this.selectedTransactionForCategory.id, categoryToUpdate).subscribe({
      next: (updated) => {
        // Update the transaction in the list
        const index = this.transactions.findIndex(t => t.id === updated.id);
        if (index !== -1) {
          this.transactions[index] = updated;
        }
        
        // Update transaction detail if it's the same
        if (this.transactionDetail && this.transactionDetail.id === updated.id) {
          this.transactionDetail = updated;
        }
        
        this.closeCategoryModal();
        const msg = this.language.translate('transactions.updateCategorySuccess');
        this.toastService.showSuccess(msg !== 'transactions.updateCategorySuccess' ? msg : 'Cập nhật phân loại giao dịch thành công');
      },
      error: (err) => {
        console.error('Error updating category:', err);
        const errMsg = this.language.translate('transactions.updateCategoryError');
        this.toastService.showError(errMsg !== 'transactions.updateCategoryError' ? errMsg : 'Cập nhật phân loại giao dịch thất bại');
      }
    });
  }

  deleteCategory(categoryName: string) {
    if (!this.userId || !categoryName) return;

    // Check if it's a default category (không xác định)
    const category = this.categories.find(c => c.name === categoryName);
    if (category && category.isDefault) {
      this.toastService.showError('Không thể xóa loại mặc định');
      return;
    }

    this.categoryService.deleteCategory(this.userId, categoryName).subscribe({
      next: () => {
        // Remove from local list
        this.categories = this.categories.filter(c => c.name !== categoryName);
        // If this was the selected category, reset it
        if (this.selectedCategory === categoryName) {
          this.selectedCategory = 'không xác định';
        }
        this.toastService.showSuccess('Đã xóa loại thành công');
      },
      error: (err) => {
        console.error('Error deleting category:', err);
        const errorMsg = err.error?.error || 'Không thể xóa loại này';
        this.toastService.showError(errorMsg);
      }
    });
  }

  getCategoryNames(): string[] {
    return this.categories.map(c => c.name);
  }

  getCategoryDisplay(category: string | undefined): string {
    return category || 'không xác định';
  }

  exportToExcel() {
    if (!this.userId) return;

    // Parse amount range
    let minAmount: number | undefined;
    let maxAmount: number | undefined;
    if (this.amountRange && this.amountRange !== 'all') {
      const range = this.parseAmountRange(this.amountRange);
      minAmount = range.min;
      maxAmount = range.max;
    }

    const params = {
      userId: this.userId,
      accountId: this.selectedAccountId || undefined,
      textSearch: this.searchTerm || undefined,
      transactionType: (this.transactionType && this.transactionType.trim() !== '') ? this.transactionType : undefined,
      startDate: this.formatDateToString(this.startDate) || undefined,
      endDate: this.formatDateToString(this.endDate) || undefined,
      minAmount: minAmount,
      maxAmount: maxAmount
    };

    this.transactionService.exportToExcel(params).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `transactions_${new Date().getTime()}.xlsx`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
        this.toastService.showSuccess('Xuất Excel thành công!');
      },
      error: (err) => {
        console.error('Error exporting Excel:', err);
        this.toastService.showError('Lỗi khi xuất Excel. Vui lòng thử lại.');
      }
    });
  }

  loadAccounts() {
    if (!this.userId) return;
    this.accountService.filter(this.userId, '', 0, 1000).subscribe({
      next: (res) => {
        this.accounts = res?.content || [];
      },
      error: (err) => {
        console.error('Error loading accounts:', err);
      }
    });
  }

  openCreateTransactionModal() {
    const now = new Date();
    // Format datetime-local input: YYYY-MM-DDTHH:mm
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const dateTimeStr = `${year}-${month}-${day}T${hours}:${minutes}`;
    
    // Generate random reference number
    const randomRef = 'REF' + Date.now().toString(36).toUpperCase() + Math.random().toString(36).substring(2, 8).toUpperCase();
    this.newTransaction = {
      accountNumber: '',
      bankBrandName: '',
      transactionDate: dateTimeStr,
      amountIn: null,
      amountOut: null,
      amountInFormatted: '',
      amountOutFormatted: '',
      transactionContent: '',
      referenceNumber: randomRef,
      bankAccountId: '',
      category: 'không xác định'
    };
    this.showCreateTransactionModal = true;
  }

  closeCreateTransactionModal() {
    this.showCreateTransactionModal = false;
  }

  createTransaction() {
    // Check required fields: userId, bankAccountId, transactionDate
    if (!this.userId) {
      const errorMsg = this.language.translate('transactions.validation.userRequired');
      this.toastService.showError(errorMsg !== 'transactions.validation.userRequired' ? errorMsg : 'Vui lòng đăng nhập để tạo giao dịch.');
      return;
    }
    // Check if bankAccountId is provided and not empty
    if (!this.newTransaction.bankAccountId || this.newTransaction.bankAccountId.trim() === '') {
      const errorMsg = this.language.translate('transactions.validation.accountRequired');
      this.toastService.showError(errorMsg !== 'transactions.validation.accountRequired' ? errorMsg : 'Vui lòng chọn tài khoản.');
      return;
    }
    if (!this.newTransaction.transactionDate) {
      const errorMsg = this.language.translate('transactions.validation.dateRequired');
      this.toastService.showError(errorMsg !== 'transactions.validation.dateRequired' ? errorMsg : 'Vui lòng chọn ngày giao dịch.');
      return;
    }
    
    // Parse amounts from formatted strings
    const amountIn = this.parseNumberFromFormatted(this.newTransaction.amountInFormatted || '0');
    const amountOut = this.parseNumberFromFormatted(this.newTransaction.amountOutFormatted || '0');
    
    // At least one amount (income or expense) must be provided and > 0
    if ((!amountIn || amountIn <= 0) && (!amountOut || amountOut <= 0)) {
      const errorMsg = this.language.translate('transactions.validation.amountRequired');
      this.toastService.showError(errorMsg !== 'transactions.validation.amountRequired' ? errorMsg : 'Vui lòng nhập số tiền thu hoặc chi (chỉ cần một trong hai).');
      return;
    }
    
    // Resolve the actual bankAccountId from the selected account
    // newTransaction.bankAccountId currently contains acc.id (from dropdown), we need to get the actual bankAccountId
    const account = this.accounts.find(a =>
      a.id.toString() === this.newTransaction.bankAccountId ||
      a.bankAccountId === this.newTransaction.bankAccountId
    );
    if (account) {
      // Ensure all account fields are properly set
      this.newTransaction.accountNumber = account.accountNumber || '';
      this.newTransaction.bankBrandName = account.bankShortName || account.bankFullName || '';
      // Use the actual bankAccountId from the account (e.g., "MANUAL_xyz"), not the display ID
      this.newTransaction.bankAccountId = account.bankAccountId || account.id.toString();
    }
    // Convert datetime-local format (YYYY-MM-DDTHH:mm) to ISO string for backend
    let transactionDateTime: string;
    if (this.newTransaction.transactionDate) {
      // If it's already in datetime-local format (YYYY-MM-DDTHH:mm), convert to ISO
      if (this.newTransaction.transactionDate.includes('T')) {
        const dateTime = new Date(this.newTransaction.transactionDate);
        transactionDateTime = dateTime.toISOString();
      } else {
        // If it's just date (YYYY-MM-DD), set time to 00:00:00
        transactionDateTime = new Date(this.newTransaction.transactionDate + 'T00:00:00').toISOString();
      }
    } else {
      transactionDateTime = new Date().toISOString();
    }
    
    this.creatingTransaction = true;
    this.transactionService.createTransaction(
      this.userId,
      this.newTransaction.accountNumber || '',
      this.newTransaction.bankBrandName || '',
      transactionDateTime,
      amountIn > 0 ? amountIn : null,
      amountOut > 0 ? amountOut : null,
      this.newTransaction.transactionContent || '',
      this.newTransaction.referenceNumber || '',
      this.newTransaction.bankAccountId || '',
      this.newTransaction.category || 'không xác định'
    ).subscribe({
      next: () => {
        this.creatingTransaction = false;
        this.showCreateTransactionModal = false;
        this.refreshAll();
        const successMsg = this.language.translate('transactions.createSuccess');
        this.toastService.showSuccess(successMsg !== 'transactions.createSuccess' ? successMsg : 'Tạo giao dịch thành công!');
      },
      error: (err) => {
        console.error('Error creating transaction:', err);
        this.creatingTransaction = false;
        const errorMsg = this.language.translate('transactions.createError');
        this.toastService.showError(errorMsg !== 'transactions.createError' ? errorMsg : 'Lỗi khi tạo giao dịch. Vui lòng thử lại.');
      }
    });
  }

  // Number formatting helpers - Format with dots every 3 digits from right to left
  formatNumberWithDots(value: number | string | null | undefined): string {
    if (value === null || value === undefined || value === '') return '';
    
    // Convert to string and remove all formatting
    let numStr = String(value).replace(/\./g, '').replace(/,/g, '').replace(/\s/g, '');
    
    // Remove leading zeros except if it's just "0"
    numStr = numStr.replace(/^0+/, '') || '0';
    if (numStr === '0') return '';
    
    // Format: add dot every 3 digits from right to left
    let formatted = '';
    for (let i = numStr.length - 1; i >= 0; i--) {
      const position = numStr.length - 1 - i;
      if (position > 0 && position % 3 === 0) {
        formatted = '.' + formatted;
      }
      formatted = numStr[i] + formatted;
    }
    
    return formatted;
  }

  parseNumberFromFormatted(value: string): number {
    if (!value) return 0;
    const cleaned = value.replace(/\./g, '').replace(/,/g, '');
    const num = parseFloat(cleaned);
    return isNaN(num) ? 0 : num;
  }

  onTransactionDateChange(value: string): void {
    // Ensure the datetime-local value is properly formatted
    if (value && !value.includes('T')) {
      // If only date is provided, add default time (00:00)
      this.newTransaction.transactionDate = value + 'T00:00';
    }
  }

  onTransactionAmountInput(event: Event, fieldName: 'amountInFormatted' | 'amountOutFormatted') {
    const input = event.target as HTMLInputElement;
    let value = input.value;
    
    // Remove all non-digit characters
    value = value.replace(/[^\d.]/g, '');
    
    // Remove dots temporarily to get the number
    const numStr = value.replace(/\./g, '');
    
    // Only proceed if there are digits
    if (numStr === '') {
      (this.newTransaction as any)[fieldName] = '';
      input.value = '';
      return;
    }
    
    const num = parseFloat(numStr);
    if (isNaN(num)) {
      (this.newTransaction as any)[fieldName] = '';
      input.value = '';
      return;
    }
    
    // Format with dots every 3 digits
    const formatted = this.formatNumberWithDots(num);
    
    // Update the model with the formatted value
    (this.newTransaction as any)[fieldName] = formatted;
    
    // Update input display
    input.value = formatted;
  }

  onAccountSelectForTransaction(accountId: string | number) {
    // Handle both string and number types for accountId
    const accountIdStr = accountId ? accountId.toString() : '';
    const account = this.accounts.find(a => a.id.toString() === accountIdStr || a.id === accountId);
    if (account && accountIdStr) {
      this.newTransaction.accountNumber = account.accountNumber || '';
      this.newTransaction.bankBrandName = account.bankShortName || account.bankFullName || '';
      // DON'T update bankAccountId here - it's already set by ngModel binding to acc.id.toString()
      // We'll resolve the actual bankAccountId (e.g., "MANUAL_xyz") when creating the transaction
    } else {
      this.newTransaction.accountNumber = '';
      this.newTransaction.bankBrandName = '';
      // Only clear bankAccountId if the selection is invalid (empty accountId)
      if (!accountIdStr) {
        this.newTransaction.bankAccountId = '';
      }
    }
  }
}


