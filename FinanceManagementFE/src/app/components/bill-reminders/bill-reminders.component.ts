import { Component, OnInit } from '@angular/core';
import { BillReminder, BillReminderService } from '../../services/bill-reminder.service';
import { UserService } from '../../services/user.service';
import { ToastService } from '../../services/toast.service';
import { LanguageService } from '../../services/language.service';
import { LayoutService } from '../../services/layout.service';
import { User } from '../../model/user.model';

const BILL_ICONS = [
  '💡', '💧', '🔥', '📶', '🏠', '🚗', '📱', '💻',
  '🎬', '🎵', '🏋️', '📚', '🏥', '✈️', '🛒', '💳',
  '🌐', '📺', '🎮', '☕', '🐕', '👶', '🎓', '💰'
];
const BILL_COLORS = [
  '#ff6b6b', '#4ecdc4', '#45b7d1', '#96ceb4',
  '#ffeaa7', '#dda0dd', '#98d8c8', '#f7dc6f',
  '#bb8fce', '#85c1e9', '#f0a500', '#2ecc71'
];

@Component({
  selector: 'app-bill-reminders',
  templateUrl: './bill-reminders.component.html',
  styleUrls: ['./bill-reminders.component.css']
})
export class BillRemindersComponent implements OnInit {
  bills: BillReminder[] = [];
  userId = '';
  loading = false;
  saving = false;
  deletingId: string | null = null;
  togglingId: string | null = null;

  showModal = false;
  editingBill: BillReminder | null = null;

  selectedIcon = '💡';
  selectedColor = '#4ecdc4';
  readonly billIcons = BILL_ICONS;
  readonly billColors = BILL_COLORS;

  form: BillReminder = this.emptyForm();

  cycles = [
    { value: 'MONTHLY', label: 'Hàng tháng' },
    { value: 'QUARTERLY', label: 'Hàng quý' },
    { value: 'YEARLY', label: 'Hàng năm' }
  ];

  constructor(
    private service: BillReminderService,
    private userService: UserService,
    private toast: ToastService,
    public language: LanguageService,
    public layout: LayoutService
  ) {}

  ngOnInit(): void {
    this.userService.getUserInfo().subscribe({
      next: (user: User) => {
        this.userId = String((user as any)?.id || (user as any)?.userId || '');
        if (this.userId) this.loadBills();
      }
    });
  }

  loadBills(): void {
    this.loading = true;
    this.service.getByUser(this.userId).subscribe({
      next: (data) => { this.bills = data; this.loading = false; },
      error: () => { this.toast.showError('Không thể tải danh sách hóa đơn'); this.loading = false; }
    });
  }

  openCreateModal(): void {
    this.editingBill = null;
    this.form = this.emptyForm();
    this.selectedIcon = '💡';
    this.selectedColor = '#4ecdc4';
    this.showModal = true;
  }

  openEditModal(bill: BillReminder): void {
    this.editingBill = bill;
    this.form = { ...bill };
    this.selectedIcon = bill.icon || '💡';
    this.selectedColor = bill.color || '#4ecdc4';
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.editingBill = null;
  }

  saveBill(): void {
    if (!this.form.name || !this.form.amount || !this.form.dayOfMonth) {
      this.toast.showError('Vui lòng điền đầy đủ thông tin');
      return;
    }
    this.saving = true;
    const payload: BillReminder = {
      ...this.form,
      userId: this.userId,
      icon: this.selectedIcon,
      color: this.selectedColor
    };
    const op = this.editingBill
      ? this.service.update(this.editingBill.id!, payload)
      : this.service.create(payload);

    op.subscribe({
      next: () => {
        this.toast.showSuccess(this.editingBill ? 'Đã cập nhật hóa đơn' : 'Đã tạo hóa đơn');
        this.closeModal();
        this.loadBills();
        this.saving = false;
      },
      error: () => { this.toast.showError('Có lỗi xảy ra'); this.saving = false; }
    });
  }

  deleteBill(bill: BillReminder): void {
    if (!confirm(`Xóa hóa đơn "${bill.name}"?`)) return;
    this.deletingId = bill.id!;
    this.service.delete(bill.id!).subscribe({
      next: () => {
        this.bills = this.bills.filter(b => b.id !== bill.id);
        this.deletingId = null;
        this.toast.showSuccess('Đã xóa hóa đơn');
      },
      error: () => { this.toast.showError('Không thể xóa'); this.deletingId = null; }
    });
  }

  togglePaid(bill: BillReminder): void {
    this.togglingId = bill.id!;
    const op = this.isPaid(bill)
      ? this.service.unmarkAsPaid(bill.id!)
      : this.service.markAsPaid(bill.id!);
    op.subscribe({
      next: (updated) => {
        const idx = this.bills.findIndex(b => b.id === bill.id);
        if (idx >= 0) this.bills[idx] = updated;
        this.togglingId = null;
      },
      error: () => { this.toast.showError('Có lỗi xảy ra'); this.togglingId = null; }
    });
  }

  isPaid(bill: BillReminder): boolean {
    const period = this.currentPeriod();
    return (bill.payments || []).some(p => p.period === period);
  }

  daysUntilDue(bill: BillReminder): number {
    const today = new Date();
    const day = bill.dayOfMonth || 1;
    let due = new Date(today.getFullYear(), today.getMonth(), day);
    if (due < today) due = new Date(today.getFullYear(), today.getMonth() + 1, day);
    return Math.ceil((due.getTime() - today.getTime()) / 86400000);
  }

  dueDateLabel(bill: BillReminder): string {
    const days = this.daysUntilDue(bill);
    if (days === 0) return 'Hôm nay';
    if (days === 1) return 'Ngày mai';
    if (days <= (bill.remindDaysBefore || 3)) return `Còn ${days} ngày`;
    return `Ngày ${bill.dayOfMonth}`;
  }

  isUrgent(bill: BillReminder): boolean {
    return !this.isPaid(bill) && this.daysUntilDue(bill) <= (bill.remindDaysBefore || 3);
  }

  formatVnd(amount: number): string {
    return new Intl.NumberFormat('vi-VN').format(amount || 0) + 'đ';
  }

  onAmountInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const raw = input.value.replace(/,/g, '').replace(/[^0-9]/g, '');
    this.form.amount = raw === '' ? 0 : Number(raw);
    input.value = this.formatNumberWithDots(raw);
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

  get totalMonthly(): number {
    return this.bills.filter(b => b.isActive && b.cycle === 'MONTHLY')
      .reduce((s, b) => s + (b.amount || 0), 0);
  }

  get unpaidCount(): number {
    return this.bills.filter(b => b.isActive && !this.isPaid(b)).length;
  }

  get urgentCount(): number {
    return this.bills.filter(b => b.isActive && this.isUrgent(b)).length;
  }

  private currentPeriod(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  }

  private emptyForm(): BillReminder {
    return { name: '', amount: 0, cycle: 'MONTHLY', dayOfMonth: 1, remindDaysBefore: 3, isActive: true };
  }
}
