import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { TransactionService, Transaction, TransactionFilterParams } from '../../services/transaction.service';
import { UserService } from '../../services/user.service';
import { LanguageService } from '../../services/language.service';
import { LayoutService } from '../../services/layout.service';
import { ToastService } from '../../services/toast.service';
import { User } from '../../model/user.model';

export interface CalendarDay {
  date: Date;
  dayNumber: number;
  isCurrentMonth: boolean;
  isToday: boolean;
  totalIncome: number;
  totalExpense: number;
  transactions: Transaction[];
}

@Component({
  selector: 'app-financial-calendar',
  templateUrl: './financial-calendar.component.html',
  styleUrls: ['./financial-calendar.component.css']
})
export class FinancialCalendarComponent implements OnInit, OnDestroy {
  userId = '';
  loading = false;

  currentYear: number = new Date().getFullYear();
  currentMonth: number = new Date().getMonth(); // 0-indexed

  calendarDays: CalendarDay[] = [];
  weekDays = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];

  selectedDay: CalendarDay | null = null;
  showDetailPanel = false;

  monthTransactions: Transaction[] = [];

  private langSub!: Subscription;

  constructor(
    private transactionService: TransactionService,
    private userService: UserService,
    public language: LanguageService,
    public layout: LayoutService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.userService.getUserInfo().subscribe({
      next: (user: User) => {
        this.userId = String((user as any)?.id || (user as any)?.userId || '');
        if (this.userId) {
          this.loadMonthTransactions();
        }
      },
      error: () => {
        this.toast.showError('Không thể tải thông tin người dùng');
      }
    });

    this.langSub = this.language.currentLanguage$.subscribe(lang => {
      this.weekDays = lang === 'en'
        ? ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
        : ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
    });
  }

  ngOnDestroy(): void {
    this.langSub?.unsubscribe();
  }

  get monthLabel(): string {
    const d = new Date(this.currentYear, this.currentMonth, 1);
    return d.toLocaleDateString('vi-VN', { month: 'long', year: 'numeric' });
  }

  prevMonth(): void {
    if (this.currentMonth === 0) { this.currentMonth = 11; this.currentYear--; }
    else { this.currentMonth--; }
    this.loadMonthTransactions();
  }

  nextMonth(): void {
    if (this.currentMonth === 11) { this.currentMonth = 0; this.currentYear++; }
    else { this.currentMonth++; }
    this.loadMonthTransactions();
  }

  loadMonthTransactions(): void {
    if (!this.userId) return;
    const startDate = new Date(this.currentYear, this.currentMonth, 1);
    const endDate = new Date(this.currentYear, this.currentMonth + 1, 0);

    const params: TransactionFilterParams = {
      userId: this.userId,
      startDate: this.toDateStr(startDate),
      endDate: this.toDateStr(endDate),
      page: 0,
      size: 1000
    };

    this.loading = true;
    this.transactionService.filterTransactions(params).subscribe({
      next: (res: any) => {
        this.monthTransactions = res.content || res || [];
        this.buildCalendar();
        this.loading = false;
      },
      error: () => {
        this.toast.showError('Không thể tải giao dịch');
        this.monthTransactions = [];
        this.buildCalendar();
        this.loading = false;
      }
    });
  }

  private buildCalendar(): void {
    const today = new Date();
    const firstDay = new Date(this.currentYear, this.currentMonth, 1);
    const lastDay = new Date(this.currentYear, this.currentMonth + 1, 0);
    const days: CalendarDay[] = [];

    // Padding: days from previous month
    for (let i = firstDay.getDay() - 1; i >= 0; i--) {
      const d = new Date(firstDay);
      d.setDate(d.getDate() - i - 1);
      days.push(this.createDay(d, false, today));
    }

    // Current month days
    for (let n = 1; n <= lastDay.getDate(); n++) {
      days.push(this.createDay(new Date(this.currentYear, this.currentMonth, n), true, today));
    }

    // Trailing days to fill 6 rows (42 cells)
    for (let i = 1; days.length < 42; i++) {
      days.push(this.createDay(new Date(this.currentYear, this.currentMonth + 1, i), false, today));
    }

    this.calendarDays = days;
  }

  private createDay(date: Date, isCurrentMonth: boolean, today: Date): CalendarDay {
    const isToday = date.toDateString() === today.toDateString();
    const dateStr = this.toDateStr(date);
    const txs = this.monthTransactions.filter(t =>
      (t.transactionDate || '').substring(0, 10) === dateStr
    );
    return {
      date,
      dayNumber: date.getDate(),
      isCurrentMonth,
      isToday,
      totalIncome: txs.reduce((s, t) => s + (t.amountIn || 0), 0),
      totalExpense: txs.reduce((s, t) => s + (t.amountOut || 0), 0),
      transactions: txs
    };
  }

  selectDay(day: CalendarDay): void {
    if (!day.isCurrentMonth) return;
    this.selectedDay = day;
    this.showDetailPanel = true;
  }

  closePanel(): void {
    this.showDetailPanel = false;
    this.selectedDay = null;
  }

  toDateStr(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

  formatVnd(amount: number): string {
    if (!amount) return '';
    return new Intl.NumberFormat('vi-VN').format(amount) + 'đ';
  }

  formatDayLabel(date: Date): string {
    return date.toLocaleDateString('vi-VN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  }

  get monthSummary() {
    const income = this.monthTransactions.reduce((s, t) => s + (t.amountIn || 0), 0);
    const expense = this.monthTransactions.reduce((s, t) => s + (t.amountOut || 0), 0);
    return { income, expense, net: income - expense };
  }
}
