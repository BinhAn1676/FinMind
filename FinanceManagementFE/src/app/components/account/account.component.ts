import { Component, OnInit, ChangeDetectorRef, OnDestroy } from '@angular/core';
import { LanguageService } from '../../services/language.service';
import { LayoutService } from '../../services/layout.service';
import { Subscription } from 'rxjs';
import { AccountService } from '../../services/account.service';
import { UserService } from '../../services/user.service';
import { User } from '../../model/user.model';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-account',
  templateUrl: './account.component.html',
  styleUrls: ['./account.component.css']
})
export class AccountComponent implements OnInit, OnDestroy {
  // Tab management
  activeTab: 'bank' | 'external' = 'bank';
  
  summary: any = {};
  accounts: any[] = [];
  languageSub!: Subscription;
  userId!: string;
  searchTerm = '';
  syncingSepay = false;
  showEdit = false;
  showDetail = false;
  editAccount: any = {};
  accountDetail: any = null;
  savingEdit = false;
  loadingAccounts = false;
  loadingDetail = false;
  showDeleteConfirm = false;
  accountToDelete: any = null;
  deleting = false;
  showSyncInfoModal = false;
  
  // External Account specific
  showCreateModal = false;
  newExternalAccount: any = {
    label: '',
    type: '',
    accumulated: '0',
    description: ''
  };
  creating = false;

  // Bank Account specific
  showCreateBankModal = false;
  newBankAccount: any = {
    accountNumber: '',
    accountHolderName: '',
    bankShortName: '',
    bankFullName: '',
    bankCode: '',
    label: '',
    accumulated: '0'
  };
  creatingBank = false;

  // Chart Distribution
  distributionData: any[] = [];
  pieSegments: any[] = [];
  tooltipVisible = false;
  tooltipX = 0;
  tooltipY = 0;
  tooltipData: any = null;
  private chartColors = ['#FF6B6B', '#4ECDC4', '#FFC107', '#9C27B0', '#2196F3', '#4CAF50', '#FF9800', '#E91E63'];

  // Pagination
  currentPage = 0;
  pageSize = 10;
  totalElements = 0;
  totalPages = 0;
  pageSizeOptions = [5, 10, 20, 50];

  constructor(
    public language: LanguageService,
    public layout: LayoutService,
    private cdr: ChangeDetectorRef,
    private accountService: AccountService,
    private userService: UserService,
    private toastService: ToastService
  ) { }

  ngOnInit(): void {
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

  getBankInitials(acc: any): string {
    const source = (acc?.bankShortName || acc?.bankFullName || 'NA');
    const text = typeof source === 'string' ? source : Array.isArray(source) ? source.join('') : String(source);
    return text.substring(0, 2).toUpperCase();
  }

  switchTab(tab: 'bank' | 'external') {
    this.activeTab = tab;
    this.currentPage = 0;
    this.searchTerm = '';
    this.refreshAll();
  }

  refreshAll() {
    if (this.activeTab === 'bank') {
      this.filterAccounts();
      this.loadSummary();
      this.loadAccountDistribution();
    } else {
      this.filterExternalAccounts();
      this.loadExternalSummary();
      this.loadExternalAccountDistribution();
    }
  }

  loadSummary() {
    if (!this.userId) return;
    this.accountService.summary(this.userId).subscribe({
      next: (s) => this.summary = s,
      error: () => {}
    });
  }

  filterAccounts() {
    if (!this.userId) return;
    this.loadingAccounts = true;
    this.accountService.filter(this.userId, this.searchTerm, this.currentPage, this.pageSize).subscribe({
      next: (res) => {
        this.accounts = res?.content || [];
        this.totalElements = res?.totalElements || 0;
        this.totalPages = res?.totalPages || 0;
        this.loadingAccounts = false;
      },
      error: () => { this.loadingAccounts = false; }
    });
  }

  onPageChange(page: number) {
    this.currentPage = page;
    this.filterAccounts();
  }

  onPageSizeChange(size: number) {
    this.pageSize = size;
    this.currentPage = 0;
    this.filterAccounts();
  }

  get Math() {
    return Math;
  }

  loadAccountDistribution() {
    if (!this.userId) return;
    this.accountService.getAccountDistribution(this.userId).subscribe({
      next: (data) => {
        console.log('=== Distribution data received ===');
        console.log('Raw data:', JSON.stringify(data, null, 2));
        this.distributionData = data || [];
        console.log('Distribution data array:', this.distributionData);
        console.log('Distribution data length:', this.distributionData.length);
        
        // Debug each item
        this.distributionData.forEach((item, idx) => {
          console.log(`Item ${idx}:`, {
            accountId: item.accountId,
            label: item.label,
            bankName: item.bankName,
            balance: item.balance,
            balanceType: typeof item.balance,
            percentage: item.percentage,
            parsedBalance: this.parseBalance(item.balance)
          });
        });
        
        this.updatePieChart();
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading distribution:', err);
        this.distributionData = [];
        this.pieSegments = [];
      }
    });
  }

  updatePieChart() {
    if (!this.distributionData || this.distributionData.length === 0) {
      this.pieSegments = [];
      return;
    }

    const total = this.distributionData.reduce((sum, item) => {
      const balance = this.parseBalance(item.balance);
      return sum + balance;
    }, 0);

    console.log('Total balance:', total);
    console.log('Distribution data:', this.distributionData);

    if (total <= 0) {
      this.pieSegments = [];
      return;
    }

    const radius = 80;
    const centerX = 100;
    const centerY = 100;

    const segments: any[] = [];
    
    // Filter only accounts with balance > 0 for rendering
    const accountsWithBalance = this.distributionData.filter(item => {
      const balance = this.parseBalance(item.balance);
      return balance > 0;
    });

    // Calculate total from accounts with balance only
    const totalWithBalance = accountsWithBalance.reduce((sum, item) => {
      return sum + this.parseBalance(item.balance);
    }, 0);

    if (totalWithBalance <= 0 || accountsWithBalance.length === 0) {
      this.pieSegments = [];
      return;
    }

    console.log('Accounts with balance:', accountsWithBalance.length);
    console.log('Total with balance:', totalWithBalance);

    // Special case: if only one account, make it a full circle
    if (accountsWithBalance.length === 1) {
      const item = accountsWithBalance[0];
      const balance = this.parseBalance(item.balance);
      const percentage = 100;
      
      // For full circle, use a simple path that goes around completely
      // We'll use pathData with special flag for full circle
      // Path: Move to center, line to top, arc 360 degrees clockwise back to top
      const startAngle = -90;
      const endAngle = 270; // -90 + 360
      
      const startRad = (startAngle * Math.PI) / 180;
      const endRad = (endAngle * Math.PI) / 180;

      const x1 = centerX + radius * Math.cos(startRad);
      const y1 = centerY + radius * Math.sin(startRad);
      const x2 = centerX + radius * Math.cos(endRad);
      const y2 = centerY + radius * Math.sin(endRad);

      // For a complete 360-degree arc, we need to ensure the path closes properly
      // Use large arc flag = 1 and sweep flag = 1 (clockwise)
      // The key is that when start and end points are the same relative to the circle,
      // we need to specify it correctly
      const pathData = `M ${centerX} ${centerY} L ${x1.toFixed(2)} ${y1.toFixed(2)} A ${radius} ${radius} 0 1 1 ${x2.toFixed(2)} ${y2.toFixed(2)} Z`;

      console.log('Full circle path:', pathData);
      console.log('Full circle - balance:', balance, 'percentage:', percentage);
      console.log('Center:', centerX, centerY, 'Radius:', radius);
      console.log('Start point:', x1.toFixed(2), y1.toFixed(2));
      console.log('End point:', x2.toFixed(2), y2.toFixed(2));

      segments.push({
        accountId: item.accountId,
        label: item.label || item.bankName,
        color: this.getColorForAccount(item.accountId),
        percentage: percentage,
        balance: balance,
        pathData,
        startAngle,
        endAngle,
        opacity: 1,
        isFullCircle: true // Flag to identify full circle
      });
    } else {
      // Multiple accounts: calculate proportions
      let currentAngle = -90; // Start from top
      let totalAngleUsed = 0;
      
      accountsWithBalance.forEach((item, index) => {
        const balance = this.parseBalance(item.balance);
        const percentage = totalWithBalance > 0 ? (balance / totalWithBalance) : 0;
        
        // For the last segment, use remaining angle to ensure full 360 degrees
        let angle: number;
        if (index === accountsWithBalance.length - 1) {
          angle = 360 - totalAngleUsed; // Use remaining angle to complete circle
        } else {
          angle = percentage * 360;
          totalAngleUsed += angle;
        }

        console.log(`Account ${index}: balance=${balance}, percentage=${percentage}, angle=${angle}`);

        const startAngle = currentAngle;
        const endAngle = currentAngle + angle;

        // Convert angles to radians
        const startRad = (startAngle * Math.PI) / 180;
        const endRad = (endAngle * Math.PI) / 180;

        // Calculate points on circle
        const x1 = centerX + radius * Math.cos(startRad);
        const y1 = centerY + radius * Math.sin(startRad);
        const x2 = centerX + radius * Math.cos(endRad);
        const y2 = centerY + radius * Math.sin(endRad);

        // Large arc flag: 1 if angle > 180, 0 otherwise
        const largeArcFlag = angle > 180 ? 1 : 0;

        // Build SVG path for pie slice
        const pathData = `M ${centerX} ${centerY} L ${x1.toFixed(2)} ${y1.toFixed(2)} A ${radius} ${radius} 0 ${largeArcFlag} 1 ${x2.toFixed(2)} ${y2.toFixed(2)} Z`;

        segments.push({
          accountId: item.accountId,
          label: item.label || item.bankName,
          color: this.getColorForAccount(item.accountId),
          percentage: percentage * 100,
          balance: balance,
          pathData,
          startAngle,
          endAngle,
          opacity: 1
        });

        currentAngle = endAngle;
      });
    }

    this.pieSegments = segments;
    console.log('Pie segments generated:', this.pieSegments.length, 'Total angle should be 360');
  }

  parseBalance(balance: any): number {
    if (balance === null || balance === undefined || balance === '') return 0;
    const num = typeof balance === 'string' ? parseFloat(balance.replace(/[^\d.-]/g, '')) : Number(balance);
    return isNaN(num) ? 0 : num;
  }

  getSegmentTooltip(segment: any): string {
    if (!segment) return '';
    return `${segment.label}: ${segment.balance.toLocaleString('vi-VN')}₫ (${segment.percentage.toFixed(2)}%)`;
  }

  getColorForAccount(accountId: string): string {
    // Find index in distributionData (all accounts)
    const index = this.distributionData.findIndex(d => d.accountId === accountId);
    if (index === -1) return this.chartColors[0]; // Fallback
    return this.chartColors[index % this.chartColors.length];
  }

  getAccountLabel(item: any): string {
    if (this.activeTab === 'external') {
      return item.label || item.type || 'Tài khoản ngoài';
    }
    return item.label || (item.bankShortName || item.bankFullName || 'Tài khoản ngân hàng');
  }

  showTooltip(event: MouseEvent, segment: any) {
    this.tooltipVisible = true;
    this.tooltipData = segment;
    this.moveTooltip(event);
  }

  hideTooltip() {
    this.tooltipVisible = false;
    this.tooltipData = null;
  }

  moveTooltip(event: MouseEvent) {
    this.tooltipX = event.clientX + 10;
    this.tooltipY = event.clientY + 10;
  }

  manualSyncAccounts() {
    if (!this.userId) return;
    this.syncingSepay = true;
    this.accountService.manualSyncSepay(this.userId)
      .subscribe({
        next: (res: any) => {
          this.syncingSepay = false;
          if (res && res.success === false && res.message === 'No bank token available') {
            this.toastService.showError('Chưa kết nối SePay, không thể đồng bộ tài khoản!');
            return;
          }
          this.refreshAll();
          const syncSuccessMsg = this.language.translate('account.syncSuccess');
          this.toastService.showSuccess(syncSuccessMsg !== 'account.syncSuccess' ? syncSuccessMsg : 'Đồng bộ tài khoản thành công!');
        },
        error: () => {
          this.syncingSepay = false;
          const syncErrorMsg = this.language.translate('account.syncError');
          this.toastService.showError(syncErrorMsg !== 'account.syncError' ? syncErrorMsg : 'Lỗi khi đồng bộ tài khoản. Vui lòng thử lại.');
        }
      });
  }

  openEditDialog(acc: any) {
    if (this.activeTab === 'external') {
      this.openEditExternalAccount(acc);
    } else {
      this.editAccount = { ...acc };
      this.showEdit = true;
    }
  }

  closeEditDialog() { this.showEdit = false; }

  updateAccount() {
    if (this.activeTab === 'external') {
      this.updateExternalAccount();
    } else {
      if (!this.userId) return;
      this.savingEdit = true;
      this.accountService.updateAccount(this.editAccount.id, this.editAccount.label, this.editAccount.accumulated)
        .subscribe({
          next: () => { 
            this.savingEdit = false; 
            this.showEdit = false; 
            this.refreshAll();
            const updateSuccessMsg = this.language.translate('account.updateSuccess');
            this.toastService.showSuccess(updateSuccessMsg !== 'account.updateSuccess' ? updateSuccessMsg : 'Cập nhật tài khoản thành công!');
          },
          error: () => { 
            this.savingEdit = false; 
            const updateErrorMsg = this.language.translate('account.updateError');
            this.toastService.showError(updateErrorMsg !== 'account.updateError' ? updateErrorMsg : 'Lỗi khi cập nhật tài khoản. Vui lòng thử lại.'); 
          }
        });
    }
  }

  viewAccountDetail(acc: any) {
    if (this.activeTab === 'external') {
      this.viewExternalAccountDetail(acc);
    } else {
      if (!acc?.id) return;
      this.showDetail = true;
      this.loadingDetail = true;
      this.accountService.getAccountDetail(acc.id).subscribe({
        next: (detail) => {
          this.accountDetail = detail;
          this.loadingDetail = false;
        },
        error: () => {
          this.accountDetail = acc;
          this.loadingDetail = false;
        }
      });
    }
  }

  closeDetailDialog() {
    this.showDetail = false;
    this.accountDetail = null;
  }

  deleteAccount(acc: any) {
    if (this.activeTab === 'external') {
      this.deleteExternalAccount(acc);
    } else {
      this.accountToDelete = acc;
      this.showDeleteConfirm = true;
    }
  }

  confirmDelete() {
    if (this.activeTab === 'external') {
      this.confirmDeleteExternalAccount();
    } else {
      if (!this.accountToDelete) return;
      this.deleting = true;
      this.accountService.deleteAccount(this.accountToDelete.id).subscribe({
        next: () => {
          this.deleting = false;
          this.showDeleteConfirm = false;
          this.accountToDelete = null;
          this.refreshAll();
          const deleteSuccessMsg = this.language.translate('account.deleteSuccess');
          this.toastService.showSuccess(deleteSuccessMsg !== 'account.deleteSuccess' ? deleteSuccessMsg : 'Xóa tài khoản thành công!');
        },
        error: () => {
          this.deleting = false;
          const deleteErrorMsg = this.language.translate('account.deleteError');
          this.toastService.showError(deleteErrorMsg !== 'account.deleteError' ? deleteErrorMsg : 'Lỗi khi xóa tài khoản. Vui lòng thử lại.');
        }
      });
    }
  }

  cancelDelete() {
    this.showDeleteConfirm = false;
    this.accountToDelete = null;
  }

  openSyncInfoModal() {
    this.showSyncInfoModal = true;
  }

  closeSyncInfoModal() {
    this.showSyncInfoModal = false;
  }

  getSupportedBanks() {
    return [
      { name: 'VPBank', features: this.language.translate('account.syncInfo.features.incoming') },
      { name: 'TPBank', features: this.language.translate('account.syncInfo.features.incomingOutgoing') + ', ' + this.language.translate('account.syncInfo.features.balance') },
      { name: 'VietinBank', features: this.language.translate('account.syncInfo.features.incomingOutgoing') + ', ' + this.language.translate('account.syncInfo.features.balance') },
      { name: 'ACB', features: this.language.translate('account.syncInfo.features.incomingOutgoing') },
      { name: 'BIDV', features: this.language.translate('account.syncInfo.features.incoming') },
      { name: 'MBBank', features: this.language.translate('account.syncInfo.features.incomingOutgoing') },
      { name: 'OCB', features: this.language.translate('account.syncInfo.features.incoming') },
      { name: 'KienLongBank', features: this.language.translate('account.syncInfo.features.incoming') },
      { name: 'MSB', features: this.language.translate('account.syncInfo.features.incoming') },
      { name: 'Sacombank', features: this.language.translate('account.syncInfo.features.incomingOutgoing') }
    ];
  }

  // External Account Methods
  getExternalAccountInitials(acc: any): string {
    if (acc?.type) {
      const type = acc.type.toUpperCase();
      return type.length >= 2 ? type.substring(0, 2) : type.substring(0, 1) + 'X';
    }
    if (acc?.label) {
      const label = acc.label.toUpperCase();
      return label.length >= 2 ? label.substring(0, 2) : label.substring(0, 1) + 'X';
    }
    return 'EX';
  }

  filterExternalAccounts() {
    if (!this.userId) return;
    this.loadingAccounts = true;
    this.accountService.filterExternalAccounts(this.userId, this.searchTerm, this.currentPage, this.pageSize).subscribe({
      next: (res) => {
        this.accounts = res?.content || [];
        this.totalElements = res?.totalElements || 0;
        this.totalPages = res?.totalPages || 0;
        this.loadingAccounts = false;
      },
      error: () => { this.loadingAccounts = false; }
    });
  }

  loadExternalSummary() {
    if (!this.userId) return;
    this.accountService.summaryExternalAccounts(this.userId).subscribe({
      next: (s) => this.summary = s,
      error: () => {}
    });
  }

  loadExternalAccountDistribution() {
    if (!this.userId) return;
    this.accountService.getExternalAccountDistribution(this.userId).subscribe({
      next: (data) => {
        this.distributionData = data || [];
        this.updatePieChart();
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading external account distribution:', err);
        this.distributionData = [];
        this.pieSegments = [];
      }
    });
  }

  openCreateModal() {
    this.newExternalAccount = {
      label: '',
      type: '',
      accumulated: '0',
      description: ''
    };
    this.showCreateModal = true;
  }

  closeCreateModal() {
    this.showCreateModal = false;
  }

  createExternalAccount() {
    if (!this.userId || !this.newExternalAccount.label || !this.newExternalAccount.accumulated) {
      const errorMsg = this.language.translate('account.external.createError');
      this.toastService.showError(errorMsg !== 'account.external.createError' ? errorMsg : 'Vui lòng nhập đầy đủ thông tin.');
      return;
    }
    this.creating = true;
    this.accountService.createExternalAccount(
      this.userId,
      this.newExternalAccount.label,
      this.newExternalAccount.type,
      this.newExternalAccount.accumulated,
      this.newExternalAccount.description
    ).subscribe({
      next: () => {
        this.creating = false;
        this.showCreateModal = false;
        this.refreshAll();
        const successMsg = this.language.translate('account.external.createSuccess');
        this.toastService.showSuccess(successMsg !== 'account.external.createSuccess' ? successMsg : 'Tạo tài khoản ngoài thành công!');
      },
      error: () => {
        this.creating = false;
        const errorMsg = this.language.translate('account.external.createError');
        this.toastService.showError(errorMsg !== 'account.external.createError' ? errorMsg : 'Lỗi khi tạo tài khoản ngoài. Vui lòng thử lại.');
      }
    });
  }

  openEditExternalAccount(acc: any) {
    this.editAccount = { ...acc };
    this.showEdit = true;
  }

  updateExternalAccount() {
    if (!this.userId) return;
    this.savingEdit = true;
    this.accountService.updateExternalAccount(
      this.editAccount.id,
      this.editAccount.label,
      this.editAccount.type,
      this.editAccount.accumulated,
      this.editAccount.description
    ).subscribe({
      next: () => {
        this.savingEdit = false;
        this.showEdit = false;
        this.refreshAll();
        const updateSuccessMsg = this.language.translate('account.external.updateSuccess');
        this.toastService.showSuccess(updateSuccessMsg !== 'account.external.updateSuccess' ? updateSuccessMsg : 'Cập nhật tài khoản ngoài thành công!');
      },
      error: () => {
        this.savingEdit = false;
        const updateErrorMsg = this.language.translate('account.external.updateError');
        this.toastService.showError(updateErrorMsg !== 'account.external.updateError' ? updateErrorMsg : 'Lỗi khi cập nhật tài khoản ngoài. Vui lòng thử lại.');
      }
    });
  }

  viewExternalAccountDetail(acc: any) {
    if (!acc?.id) return;
    this.showDetail = true;
    this.loadingDetail = true;
    this.accountService.getExternalAccountDetail(acc.id).subscribe({
      next: (detail) => {
        this.accountDetail = detail;
        this.loadingDetail = false;
      },
      error: () => {
        this.accountDetail = acc;
        this.loadingDetail = false;
      }
    });
  }

  deleteExternalAccount(acc: any) {
    this.accountToDelete = acc;
    this.showDeleteConfirm = true;
  }

  confirmDeleteExternalAccount() {
    if (!this.accountToDelete) return;
    this.deleting = true;
    this.accountService.deleteExternalAccount(this.accountToDelete.id).subscribe({
      next: () => {
        this.deleting = false;
        this.showDeleteConfirm = false;
        this.accountToDelete = null;
        this.refreshAll();
        const deleteSuccessMsg = this.language.translate('account.external.deleteSuccess');
        this.toastService.showSuccess(deleteSuccessMsg !== 'account.external.deleteSuccess' ? deleteSuccessMsg : 'Xóa tài khoản ngoài thành công!');
      },
      error: () => {
        this.deleting = false;
        const deleteErrorMsg = this.language.translate('account.external.deleteError');
        this.toastService.showError(deleteErrorMsg !== 'account.external.deleteError' ? deleteErrorMsg : 'Lỗi khi xóa tài khoản ngoài. Vui lòng thử lại.');
      }
    });
  }

  openCreateBankModal() {
    this.newBankAccount = {
      accountNumber: '',
      accountHolderName: '',
      bankShortName: '',
      bankFullName: '',
      bankCode: '',
      label: '',
      accumulated: ''
    };
    this.showCreateBankModal = true;
  }

  closeCreateBankModal() {
    this.showCreateBankModal = false;
  }

  createBankAccount() {
    if (!this.userId || !this.newBankAccount.accountNumber || !this.newBankAccount.label) {
      const errorMsg = this.language.translate('account.bank.createError');
      this.toastService.showError(errorMsg !== 'account.bank.createError' ? errorMsg : 'Vui lòng nhập đầy đủ thông tin bắt buộc.');
      return;
    }
    this.creatingBank = true;
    // Parse accumulated from formatted string
    const accumulated = this.parseNumberFromFormatted(this.newBankAccount.accumulated || '0').toString();
    this.accountService.createBankAccount(
      this.userId,
      this.newBankAccount.accountNumber,
      this.newBankAccount.accountHolderName || '',
      this.newBankAccount.bankShortName || '',
      this.newBankAccount.bankFullName || '',
      this.newBankAccount.bankCode || '',
      this.newBankAccount.label,
      accumulated
    ).subscribe({
      next: () => {
        this.creatingBank = false;
        this.showCreateBankModal = false;
        this.refreshAll();
        const successMsg = this.language.translate('account.bank.createSuccess');
        this.toastService.showSuccess(successMsg !== 'account.bank.createSuccess' ? successMsg : 'Tạo tài khoản ngân hàng thành công!');
      },
      error: () => {
        this.creatingBank = false;
        const errorMsg = this.language.translate('account.bank.createError');
        this.toastService.showError(errorMsg !== 'account.bank.createError' ? errorMsg : 'Lỗi khi tạo tài khoản ngân hàng. Vui lòng thử lại.');
      }
    });
  }

  // Number formatting helpers - Format with dots every 3 digits from right to left
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

  parseNumberFromFormatted(value: string): number {
    if (!value) return 0;
    const cleaned = value.replace(/,/g, '').replace(/\./g, '');
    const num = parseFloat(cleaned);
    return isNaN(num) ? 0 : num;
  }

  onNumberInput(event: Event, fieldName: string) {
    const input = event.target as HTMLInputElement;
    const raw = input.value.replace(/,/g, '').replace(/[^0-9]/g, '');
    if (raw === '') {
      (this.newBankAccount as any)[fieldName] = '';
      input.value = '';
      return;
    }
    const formatted = this.formatNumberWithDots(raw);
    (this.newBankAccount as any)[fieldName] = formatted;
    input.value = formatted;
  }

  onEditAccumulatedInput(event: Event) {
    const input = event.target as HTMLInputElement;
    const raw = input.value.replace(/,/g, '').replace(/[^0-9]/g, '');
    this.editAccount.accumulated = raw === '' ? null : Number(raw);
    input.value = this.formatNumberWithDots(raw);
  }

  onExternalAccumulatedInput(event: Event) {
    const input = event.target as HTMLInputElement;
    const raw = input.value.replace(/,/g, '').replace(/[^0-9]/g, '');
    this.newExternalAccount.accumulated = raw === '' ? null : Number(raw);
    input.value = this.formatNumberWithDots(raw);
  }
}
