import { Component, OnInit, ChangeDetectorRef, OnDestroy } from '@angular/core';
import { LanguageService } from '../../services/language.service';
import { LayoutService } from '../../services/layout.service';
import { Subscription } from 'rxjs';
import { LoanService, Loan, LoanSummary, LoanPayment, LoanFilterParams, Borrower } from '../../services/loan.service';
import { UserService } from '../../services/user.service';
import { User } from '../../model/user.model';
import { ToastService } from '../../services/toast.service';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

@Component({
  selector: 'app-loans',
  templateUrl: './loans.component.html',
  styleUrls: ['./loans.component.css']
})
export class LoansComponent implements OnInit, OnDestroy {
  // Tab management
  activeTab: 'CHO_VAY' | 'VAY' = 'CHO_VAY';
  
  // Data
  loans: Loan[] = [];
  summary: LoanSummary = {
    totalPrincipal: 0,
    totalInterest: 0,
    totalCollected: 0,
    totalAfterInterest: 0,
    loanCount: 0
  };
  
  languageSub!: Subscription;
  userId!: string;
  
  // Loading states
  loadingLoans = false;
  loadingSummary = false;
  loadingDetail = false;
  
  // Modals
  showDetail = false;
  showCreateModal = false;
  showEditModal = false;
  showPaymentModal = false;
  showDeleteConfirm = false;
  
  // Selected items
  selectedLoan: Loan | null = null;
  loanDetail: Loan | null = null;
  loanToDelete: Loan | null = null;
  editingPayment: LoanPayment | null = null;
  editingLoan: Loan | null = null;
  
  // Forms
  loanForm!: FormGroup;
  paymentForm!: FormGroup;
  
  // Borrowers management
  borrowers: Borrower[] = [];
  
  // Filters
  searchTerm = '';
  startDate: Date | null = null;
  endDate: Date | null = null;
  selectedStatuses: string[] = []; // Selected statuses for filtering
  reconciliationDate: Date | null = null; // Ngày đối soát, mặc định là hôm nay
  
  // Available statuses
  availableStatuses = [
    { value: 'PAID', label: 'loans.statusLabels.paid' },
    { value: 'OUTDATE', label: 'loans.statusLabels.outdate' },
    { value: 'ON_GOING', label: 'loans.statusLabels.onGoing' }
  ];
  
  // Pagination
  currentPage = 0;
  pageSize = 10;
  totalElements = 0;
  totalPages = 0;
  pageSizeOptions = [10, 20, 50, 100];
  
  // Actions
  saving = false;
  deleting = false;
  savingPayment = false;
  deletingPayment = false;

  constructor(
    public language: LanguageService,
    public layout: LayoutService,
    private cdr: ChangeDetectorRef,
    private loanService: LoanService,
    private userService: UserService,
    private toastService: ToastService,
    private fb: FormBuilder
  ) {
    this.initForms();
  }

  ngOnInit(): void {
    // Set reconciliation date to today by default
    this.reconciliationDate = new Date();

    this.userService.getUserInfo().subscribe({
      next: (user: User) => {
        this.userId = (user as any)?.id || (user as any)?.userId || '';
        if (this.userId) {
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

  initForms() {
    this.loanForm = this.fb.group({
      principalAmount: [0, [Validators.required, Validators.min(1)]],
      interestRate: [0, [Validators.required, Validators.min(0)]],
      startDate: ['', Validators.required],
      termDays: [0, [Validators.required, Validators.min(1)]],
      endDate: [''],
      notes: ['']
    });
    
    // Initialize with one empty borrower
    this.borrowers = [this.createEmptyBorrower()];
    
    // Initialize payment form
    this.initPaymentForm();
  }
  
  createEmptyBorrower(): Borrower {
    return {
      fullName: '',
      cccd: '',
      phoneNumber: '',
      address: '',
      additionalInfo: ''
    };
  }
  
  addBorrower() {
    this.borrowers.push(this.createEmptyBorrower());
  }
  
  removeBorrower(index: number) {
    if (this.borrowers.length > 1) {
      this.borrowers.splice(index, 1);
    }
  }

  initPaymentForm() {
    this.paymentForm = this.fb.group({
      paymentDate: ['', Validators.required],
      amount: [0, [Validators.required, Validators.min(1)]],
      principalAmount: [0],
      interestAmount: [0],
      notes: ['']
    });
  }

  switchTab(tab: 'CHO_VAY' | 'VAY') {
    this.activeTab = tab;
    this.currentPage = 0;
    this.clearFilters();
    this.refreshAll();
  }

  refreshAll() {
    this.filterLoans();
    this.loadSummary();
  }

  filterLoans() {
    if (!this.userId) return;
    this.loadingLoans = true;

    const params: LoanFilterParams = {
      userId: this.userId,
      loanType: this.activeTab,
      searchTerm: this.searchTerm || undefined,
      startDate: this.formatDateToString(this.startDate) || undefined,
      endDate: this.formatDateToString(this.endDate) || undefined,
      statuses: this.selectedStatuses.length > 0 ? this.selectedStatuses : undefined,
      page: this.currentPage,
      size: this.pageSize
    };

    this.loanService.filterLoans(params).subscribe({
      next: (res) => {
        this.loans = res?.content || [];
        this.totalElements = res?.totalElements || 0;
        this.totalPages = res?.totalPages || 0;
        this.loadingLoans = false;
      },
      error: (err) => {
        console.error('Error loading loans:', err);
        this.loadingLoans = false;
        this.toastService.showError('Lỗi khi tải danh sách vay. Vui lòng thử lại.');
      }
    });
  }

  formatDateToString(date: Date | null): string {
    if (!date) return '';
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  loadSummary() {
    if (!this.userId) return;
    this.loadingSummary = true;

    this.loanService.getSummary(this.userId, this.activeTab).subscribe({
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
    this.filterLoans();
  }

  onFilterChange() {
    this.currentPage = 0;
    this.filterLoans();
    this.loadSummary();
  }

  clearFilters() {
    this.searchTerm = '';
    this.startDate = null;
    this.endDate = null;
    this.selectedStatuses = [];
    // Reset reconciliation date to today
    this.reconciliationDate = new Date();
    this.currentPage = 0;
    this.refreshAll();
  }
  
  toggleStatus(status: string) {
    const index = this.selectedStatuses.indexOf(status);
    if (index > -1) {
      this.selectedStatuses.splice(index, 1);
    } else {
      this.selectedStatuses.push(status);
    }
    this.onFilterChange();
  }
  
  isStatusSelected(status: string): boolean {
    return this.selectedStatuses.includes(status);
  }
  
  getStatusLabel(status: string): string {
    const statusObj = this.availableStatuses.find(s => s.value === status);
    return statusObj ? this.language.translate(statusObj.label) : status;
  }

  onReconciliationDateChange() {
    // Trigger change detection to recalculate amount due
    this.cdr.detectChanges();
  }

  onPageChange(page: number) {
    this.currentPage = page;
    this.filterLoans();
  }

  onPageSizeChange(size: number) {
    this.pageSize = size;
    this.currentPage = 0;
    this.filterLoans();
  }

  formatCurrency(amount: number | undefined): string {
    if (amount === undefined || amount === null) return '0 ₫';
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
        day: '2-digit'
      }).format(date);
    } catch (e) {
      return dateString;
    }
  }

  get Math() {
    return Math;
  }

  // Loan CRUD operations
  openCreateModal() {
    this.editingLoan = null;
    this.loanForm.reset();
    this.loanForm.patchValue({
      principalAmount: 0,
      interestRate: 0,
      termDays: 0
    });
    this.borrowers = [this.createEmptyBorrower()];
    this.showCreateModal = true;
  }

  closeCreateModal() {
    this.showCreateModal = false;
    this.editingLoan = null;
    this.loanForm.reset();
    this.borrowers = [this.createEmptyBorrower()];
  }

  openEditModal(loan: Loan) {
    this.editingLoan = loan;
    const startDate = loan.startDate ? this.formatDateForInput(loan.startDate) : '';
    const termDays = loan.termDays || 0;
    const endDate = loan.endDate ? this.formatDateForInput(loan.endDate) : 
                    (startDate && termDays > 0 ? this.calculateEndDate(startDate, termDays) : '');
    
    // Load borrowers - support both old format (borrower) and new format (borrowers)
    if (loan.borrowers && loan.borrowers.length > 0) {
      this.borrowers = loan.borrowers.map(b => ({ ...b }));
    } else if (loan.borrower) {
      // Backward compatibility: convert single borrower to array
      this.borrowers = [{ ...loan.borrower }];
    } else {
      this.borrowers = [this.createEmptyBorrower()];
    }
    
    this.loanForm.patchValue({
      principalAmount: loan.principalAmount || 0,
      interestRate: loan.interestRate || 0,
      startDate: startDate,
      termDays: termDays,
      endDate: endDate,
      notes: loan.notes || ''
    });
    this.showEditModal = true;
  }

  closeEditModal() {
    this.showEditModal = false;
    this.editingLoan = null;
    this.loanForm.reset();
    this.borrowers = [this.createEmptyBorrower()];
  }

  formatDateForInput(dateString: string): string {
    if (!dateString) return '';
    try {
      const date = new Date(dateString);
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const day = String(date.getDate()).padStart(2, '0');
      return `${year}-${month}-${day}`;
    } catch (e) {
      return dateString;
    }
  }

  onDisbursementDateOrTermChange() {
    const startDate = this.loanForm.get('startDate')?.value;
    const termDays = this.loanForm.get('termDays')?.value;
    
    if (startDate && termDays && termDays > 0) {
      const endDate = this.calculateEndDate(startDate, termDays);
      this.loanForm.patchValue({ endDate: endDate }, { emitEvent: false });
    } else {
      this.loanForm.patchValue({ endDate: '' }, { emitEvent: false });
    }
  }

  saveLoan() {
    // Validate borrowers
    const invalidBorrowers = this.borrowers.filter(b => !b.fullName || !b.phoneNumber);
    if (invalidBorrowers.length > 0) {
      this.toastService.showError('Vui lòng nhập đầy đủ thông tin bắt buộc cho tất cả người vay.');
      return;
    }

    if (this.loanForm.invalid) {
      this.toastService.showError('Vui lòng nhập đầy đủ thông tin bắt buộc.');
      return;
    }

    if (!this.userId) return;

    const formValue = this.loanForm.value;
    const loan: Loan = {
      userId: this.userId,
      loanType: this.activeTab,
      borrowers: this.borrowers.filter(b => b.fullName || b.phoneNumber), // Only include non-empty borrowers
      principalAmount: formValue.principalAmount,
      interestRate: formValue.interestRate,
      startDate: formValue.startDate,
      termDays: formValue.termDays,
      endDate: formValue.endDate || this.calculateEndDate(formValue.startDate, formValue.termDays),
      notes: formValue.notes
    };

    this.saving = true;
    if (this.editingLoan?.id) {
      loan.id = this.editingLoan.id;
      this.loanService.updateLoan(this.editingLoan.id, loan).subscribe({
        next: () => {
          this.saving = false;
          this.closeEditModal();
          this.refreshAll();
          this.toastService.showSuccess('Cập nhật khoản vay thành công!');
        },
        error: () => {
          this.saving = false;
          this.toastService.showError('Lỗi khi cập nhật khoản vay. Vui lòng thử lại.');
        }
      });
    } else {
      this.loanService.createLoan(loan).subscribe({
        next: () => {
          this.saving = false;
          this.closeCreateModal();
          this.refreshAll();
          this.toastService.showSuccess('Tạo khoản vay thành công!');
        },
        error: () => {
          this.saving = false;
          this.toastService.showError('Lỗi khi tạo khoản vay. Vui lòng thử lại.');
        }
      });
    }
  }

  calculateEndDate(startDate: string, termDays: number): string {
    if (!startDate || !termDays) return '';
    try {
      const start = new Date(startDate);
      start.setDate(start.getDate() + termDays);
      return start.toISOString().split('T')[0];
    } catch (e) {
      return '';
    }
  }

  deleteLoan(loan: Loan) {
    this.loanToDelete = loan;
    this.showDeleteConfirm = true;
  }

  confirmDelete() {
    if (!this.loanToDelete?.id) return;
    this.deleting = true;
    this.loanService.deleteLoan(this.loanToDelete.id).subscribe({
      next: () => {
        this.deleting = false;
        this.showDeleteConfirm = false;
        this.loanToDelete = null;
        this.refreshAll();
        this.toastService.showSuccess('Xóa khoản vay thành công!');
      },
      error: () => {
        this.deleting = false;
        this.toastService.showError('Lỗi khi xóa khoản vay. Vui lòng thử lại.');
      }
    });
  }

  cancelDelete() {
    this.showDeleteConfirm = false;
    this.loanToDelete = null;
  }

  // Loan Detail
  viewLoanDetail(loan: Loan) {
    if (!loan?.id) return;
    this.selectedLoan = loan;
    this.showDetail = true;
    this.loadingDetail = true;
    this.loanService.getLoanById(loan.id).subscribe({
      next: (detail) => {
        this.loanDetail = detail;
        this.loadingDetail = false;
      },
      error: () => {
        this.loanDetail = loan;
        this.loadingDetail = false;
      }
    });
  }

  closeDetailDialog() {
    this.showDetail = false;
    this.selectedLoan = null;
    this.loanDetail = null;
  }

  // Payment Management
  openPaymentModal(payment?: LoanPayment) {
    this.editingPayment = payment || null;
    if (payment) {
      this.paymentForm.patchValue({
        paymentDate: payment.paymentDate ? this.formatDateForInput(payment.paymentDate) : '',
        amount: payment.amount || 0,
        notes: payment.notes || ''
      });
    } else {
      this.paymentForm.reset();
      this.paymentForm.patchValue({
        paymentDate: new Date().toISOString().split('T')[0],
        amount: 0
      });
    }
    this.showPaymentModal = true;
  }

  closePaymentModal() {
    this.showPaymentModal = false;
    this.editingPayment = null;
    this.paymentForm.reset();
  }

  savePayment() {
    if (this.paymentForm.invalid || !this.loanDetail?.id) {
      this.toastService.showError('Vui lòng nhập đầy đủ thông tin.');
      return;
    }

    const formValue = this.paymentForm.value;
    const payment: LoanPayment = {
      id: this.editingPayment?.id,
      paymentDate: formValue.paymentDate,
      amount: formValue.amount,
      principalAmount: 0, // Auto-set to 0, not user input
      interestAmount: 0, // Auto-set to 0, not user input
      notes: formValue.notes
    };

    this.savingPayment = true;
    if (this.editingPayment?.id) {
      this.loanService.updatePayment(this.loanDetail.id, this.editingPayment.id, payment).subscribe({
        next: (updatedLoan) => {
          this.savingPayment = false;
          this.loanDetail = updatedLoan;
          this.closePaymentModal();
          this.refreshAll();
          this.toastService.showSuccess('Cập nhật thanh toán thành công!');
        },
        error: () => {
          this.savingPayment = false;
          this.toastService.showError('Lỗi khi cập nhật thanh toán. Vui lòng thử lại.');
        }
      });
    } else {
      this.loanService.addPayment(this.loanDetail.id, payment).subscribe({
        next: (updatedLoan) => {
          this.savingPayment = false;
          this.loanDetail = updatedLoan;
          this.closePaymentModal();
          this.refreshAll();
          this.toastService.showSuccess('Thêm thanh toán thành công!');
        },
        error: () => {
          this.savingPayment = false;
          this.toastService.showError('Lỗi khi thêm thanh toán. Vui lòng thử lại.');
        }
      });
    }
  }

  deletePayment(payment: LoanPayment) {
    if (!this.loanDetail?.id || !payment.id) return;
    this.deletingPayment = true;
    this.loanService.deletePayment(this.loanDetail.id, payment.id).subscribe({
      next: (updatedLoan) => {
        this.deletingPayment = false;
        this.loanDetail = updatedLoan;
        this.refreshAll();
        this.toastService.showSuccess('Xóa thanh toán thành công!');
      },
      error: () => {
        this.deletingPayment = false;
        this.toastService.showError('Lỗi khi xóa thanh toán. Vui lòng thử lại.');
      }
    });
  }

  getAmountDue(loan: Loan): number {
    if (!loan.startDate || !loan.dailyPaymentAmount || !this.reconciliationDate) {
      return 0;
    }

    try {
      const startDate = new Date(loan.startDate);
      const reconciliationDate = this.reconciliationDate;
      
      // Calculate days from start date to reconciliation date
      const timeDiff = reconciliationDate.getTime() - startDate.getTime();
      const daysDiff = Math.ceil(timeDiff / (1000 * 60 * 60 * 24));
      
      // If reconciliation date is before start date, return 0
      if (daysDiff < 0) {
        return 0;
      }
      
      // Limit days to term days if reconciliation date is after end date
      const maxDays = loan.termDays || daysDiff;
      const daysToCalculate = Math.min(daysDiff, maxDays);
      
      // Calculate expected amount: daily payment * days
      const expectedAmount = loan.dailyPaymentAmount * daysToCalculate;
      
      // Get total paid
      const totalPaid = loan.totalPaid || 0;
      
      // Amount due = expected amount - total paid
      return expectedAmount - totalPaid;
    } catch (e) {
      console.error('Error calculating amount due:', e);
      return 0;
    }
  }

  getAmountDueClass(amount: number | undefined): string {
    if (amount === undefined || amount === null) return '';
    if (amount > 0) return 'positive'; // Customer needs to pay
    if (amount < 0) return 'negative'; // Overpaid
    return '';
  }

  getOutstandingDebtClass(debt: number | undefined): string {
    if (debt === undefined || debt === null) return '';
    if (debt > 0) return 'positive'; // Still has debt
    if (debt < 0) return 'negative'; // Overpaid/credit
    return '';
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

  onNumberInput(event: Event, formControlName: string, form: FormGroup) {
    const input = event.target as HTMLInputElement;
    let value = input.value;
    
    // Remove all non-digit characters except dots (for formatting)
    value = value.replace(/[^\d.]/g, '');
    
    // Remove dots temporarily to get the number
    const numStr = value.replace(/\./g, '');
    
    // Only proceed if there are digits
    if (numStr === '') {
      form.patchValue({ [formControlName]: 0 }, { emitEvent: false });
      input.value = '';
      return;
    }
    
    const num = parseFloat(numStr);
    if (isNaN(num)) {
      form.patchValue({ [formControlName]: 0 }, { emitEvent: false });
      input.value = '';
      return;
    }
    
    // Format with dots every 3 digits
    const formatted = this.formatNumberWithDots(num);
    
    // Update input display
    input.value = formatted;
    
    // Update form control with numeric value
    form.patchValue({ [formControlName]: num }, { emitEvent: false });
  }
}
