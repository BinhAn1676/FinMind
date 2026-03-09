import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl } from '@angular/forms';
import { PlanningBudget, PlanningBudgetService, PlanType, RepeatCycle } from '../../services/planning-budget.service';
import { Category, CategoryService } from '../../services/category.service';
import { LanguageService } from '../../services/language.service';
import { ToastService } from '../../services/toast.service';
import { LayoutService } from '../../services/layout.service';
import { UserService } from '../../services/user.service';
import { AccountService } from '../../services/account.service';
import { User } from '../../model/user.model';

@Component({
  selector: 'app-planning',
  templateUrl: './planning.component.html',
  styleUrls: ['./planning.component.css']
})
export class PlanningComponent implements OnInit {
  budgets: PlanningBudget[] = [];
  categories: Category[] = [];
  form!: FormGroup;
  userId = '';
  bankAccountIds: string[] = [];
  loadingBudgets = false;
  loadingCategories = false;
  loadingAccounts = false;
  saving = false;
  deletingId: string | null = null;
  showCreateModal = false;
  editingPlan: PlanningBudget | null = null;
  totals = { totalBudget: 0, totalSpent: 0, totalRemaining: 0 };
  newCategory = '';
  showNewCategoryInput = false;

  constructor(
    private planningService: PlanningBudgetService,
    private categoryService: CategoryService,
    private accountService: AccountService,
    private fb: FormBuilder,
    public language: LanguageService,
    private toast: ToastService,
    public layout: LayoutService,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.prefillDefaultDates();

    this.userService.getUserInfo().subscribe({
      next: (user: User) => {
        const userIdValue = (user as any)?.id || (user as any)?.userId;
        this.userId = userIdValue ? String(userIdValue) : '';
        console.log('Planning component - User ID:', this.userId);
        if (this.userId) {
          this.fetchBudgets();
          this.fetchCategories();
          this.loadBankAccounts();
        } else {
          console.error('User ID not found in user info:', user);
          this.toast.showError(this.language.translate('planning.toast.userIdError') || 'Không thể lấy thông tin người dùng');
        }
      },
      error: (err) => {
        console.error('Error loading user info:', err);
        this.toast.showError(this.language.translate('planning.toast.userIdError') || 'Không thể tải thông tin người dùng');
      }
    });
  }

  private loadBankAccounts(): void {
    if (!this.userId) return;
    
    this.loadingAccounts = true;
    this.accountService.filter(this.userId, '', 0, 100).subscribe({
      next: (response: any) => {
        const accounts = response.content || response || [];
        this.bankAccountIds = accounts
          .map((acc: any) => acc.bankAccountId || acc.id?.toString())
          .filter((id: string | undefined): id is string => !!id);
        
        this.loadingAccounts = false;
        console.log('Loaded bank account IDs:', this.bankAccountIds);
        
        // Recalculate spent amounts after accounts are loaded
        if (this.bankAccountIds.length > 0) {
          this.recalculatePlansSpentAmounts();
        }
      },
      error: (err) => {
        console.error('Error loading bank accounts:', err);
        this.bankAccountIds = [];
        this.loadingAccounts = false;
      }
    });
  }

  private recalculatePlansSpentAmounts(): void {
    if (!this.userId || this.bankAccountIds.length === 0) {
      return;
    }

    // Use current month as default date range
    const now = new Date();
    const startDate = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0];
    const endDate = new Date(now.getFullYear(), now.getMonth() + 1, 0).toISOString().split('T')[0];

    this.planningService.recalculateAllSpentAmountsWithDateRange(
      this.userId,
      this.bankAccountIds,
      startDate,
      endDate
    ).subscribe({
      next: () => {
        console.log('Recalculated spent amounts for all plans');
        // Reload budgets to get updated spent amounts
        this.fetchBudgets();
      },
      error: (err) => {
        console.error('Error recalculating spent amounts:', err);
        // Fallback to normal recalculate
        this.planningService.recalculateAllSpentAmounts(this.userId, this.bankAccountIds).subscribe({
          next: () => this.fetchBudgets(),
          error: () => console.error('Fallback recalculate also failed')
        });
      }
    });
  }

  private initForm(): void {
    this.form = this.fb.group({
      category: ['', Validators.required],
      budgetAmount: [null, [Validators.required, Validators.min(1)]],
      planType: ['SHORT_TERM', Validators.required],
      startDate: [''],
      endDate: [''],
      repeatCycle: [''],
      dayOfMonth: [null],
      applyForWholeMonth: [false], // For MONTHLY plans
      applyForWholeYear: [false] // For YEARLY plans
    });

    // Update validators when planType changes
    this.form.get('planType')?.valueChanges.subscribe(planType => {
      this.updateFormValidators(planType);
    });

    // Update validators when repeatCycle or applyForWholeMonth changes
    this.form.get('repeatCycle')?.valueChanges.subscribe(() => {
      if (this.form.get('planType')?.value === 'RECURRING') {
        this.updateFormValidators('RECURRING');
      }
    });

    this.form.get('applyForWholeMonth')?.valueChanges.subscribe((value) => {
      if (this.form.get('planType')?.value === 'RECURRING') {
        // Clear dayOfMonth when checkbox is checked
        if (value) {
          this.form.get('dayOfMonth')?.setValue(null);
        }
        this.updateFormValidators('RECURRING');
      }
    });

    this.form.get('applyForWholeYear')?.valueChanges.subscribe((value) => {
      if (this.form.get('planType')?.value === 'RECURRING') {
        // Clear dayOfMonth when checkbox is checked
        if (value) {
          this.form.get('dayOfMonth')?.setValue(null);
        }
        this.updateFormValidators('RECURRING');
      }
    });

    // Initialize validators
    this.updateFormValidators('SHORT_TERM');
  }

  private updateFormValidators(planType: PlanType): void {
    const startDateControl = this.form.get('startDate');
    const endDateControl = this.form.get('endDate');
    const repeatCycleControl = this.form.get('repeatCycle');
    const dayOfMonthControl = this.form.get('dayOfMonth');
    const applyForWholeMonthControl = this.form.get('applyForWholeMonth');

    // Clear all validators first
    startDateControl?.clearValidators();
    endDateControl?.clearValidators();
    repeatCycleControl?.clearValidators();
    dayOfMonthControl?.clearValidators();

    switch (planType) {
      case 'SHORT_TERM':
        startDateControl?.setValidators([Validators.required]);
        endDateControl?.setValidators([Validators.required]);
        break;
      case 'LONG_TERM':
        startDateControl?.setValidators([Validators.required]);
        // endDate is optional for LONG_TERM
        break;
      case 'RECURRING':
        repeatCycleControl?.setValidators([Validators.required]);
        // dayOfMonth is required only if not applying for whole month/year
        const repeatCycle = this.form.get('repeatCycle')?.value;
        const applyForWholeMonth = this.form.get('applyForWholeMonth')?.value;
        const applyForWholeYear = this.form.get('applyForWholeYear')?.value;
        if (repeatCycle === 'MONTHLY' && !applyForWholeMonth) {
          dayOfMonthControl?.setValidators([Validators.required, Validators.min(1), Validators.max(31)]);
        } else if (repeatCycle === 'YEARLY' && !applyForWholeYear) {
          dayOfMonthControl?.setValidators([Validators.required, Validators.min(1), Validators.max(31)]);
        } else if (repeatCycle === 'QUARTERLY') {
          // For QUARTERLY, dayOfMonth is always required
          dayOfMonthControl?.setValidators([Validators.required, Validators.min(1), Validators.max(31)]);
        }
        // If MONTHLY and applyForWholeMonth = true, or YEARLY and applyForWholeYear = true, dayOfMonth is optional (will be null)
        break;
    }

    startDateControl?.updateValueAndValidity();
    endDateControl?.updateValueAndValidity();
    repeatCycleControl?.updateValueAndValidity();
    dayOfMonthControl?.updateValueAndValidity();
  }

  get planType(): PlanType {
    return this.form.get('planType')?.value || 'SHORT_TERM';
  }

  get planTypes(): PlanType[] {
    return ['SHORT_TERM', 'LONG_TERM', 'RECURRING'];
  }

  get repeatCycles(): RepeatCycle[] {
    return ['MONTHLY', 'QUARTERLY', 'YEARLY'];
  }

  private fetchBudgets(): void {
    if (!this.userId) {
      console.error('Cannot fetch budgets: userId is empty');
      this.budgets = [];
      return;
    }
    this.loadingBudgets = true;
    console.log('Fetching budgets for userId:', this.userId);
    this.planningService.list(this.userId).subscribe({
      next: (res) => {
        console.log('Budgets fetched:', res);
        if (Array.isArray(res)) {
          this.budgets = res.map(item => ({
            ...item,
            spentAmount: item.spentAmount ?? 0
          }));
        } else {
          console.warn('Response is not an array:', res);
          this.budgets = [];
        }
        this.loadingBudgets = false;
        this.computeTotals();
      },
      error: (err) => {
        console.error('Error fetching budgets:', err);
        this.budgets = [];
        this.loadingBudgets = false;
        this.toast.showError(this.language.translate('planning.toast.loadError'));
      }
    });
  }

  private fetchCategories(): void {
    this.loadingCategories = true;
    this.categoryService.getAllCategories(this.userId).subscribe({
      next: (res) => {
        this.categories = res || [];
        this.loadingCategories = false;
      },
      error: () => {
        this.loadingCategories = false;
        this.toast.showError(this.language.translate('planning.toast.categoryLoadError'));
      }
    });
  }

  openCreateModal(): void {
    this.editingPlan = null;
    this.form.get('planType')?.setValue('SHORT_TERM');
    this.prefillDefaultDates();
    this.form.get('category')?.setValue('');
    this.form.get('budgetAmount')?.setValue(null);
    this.form.get('repeatCycle')?.setValue('');
    this.form.get('dayOfMonth')?.setValue(null);
    this.form.get('applyForWholeMonth')?.setValue(false);
    this.form.get('applyForWholeYear')?.setValue(false);
    this.form.markAsPristine();
    this.form.markAsUntouched();
    this.newCategory = '';
    this.showNewCategoryInput = false;
    // Load categories if not already loaded
    if (this.categories.length === 0) {
      this.fetchCategories();
    }
    this.showCreateModal = true;
  }

  openEditModal(plan: PlanningBudget): void {
    this.editingPlan = plan;
    this.form.get('planType')?.setValue(plan.planType || 'SHORT_TERM');
    this.form.get('category')?.setValue(plan.category || '');
    this.form.get('budgetAmount')?.setValue(plan.budgetAmount || null);
    this.form.get('startDate')?.setValue(this.stringToDate(plan.startDate));
    this.form.get('endDate')?.setValue(this.stringToDate(plan.endDate));
    this.form.get('repeatCycle')?.setValue(plan.repeatCycle || '');
    
    // Handle dayOfMonth and applyForWholeMonth/Year
    if (plan.planType === 'RECURRING') {
      if (plan.repeatCycle === 'MONTHLY' && !plan.dayOfMonth) {
        this.form.get('applyForWholeMonth')?.setValue(true);
        this.form.get('dayOfMonth')?.setValue(null);
      } else if (plan.repeatCycle === 'YEARLY' && !plan.dayOfMonth) {
        this.form.get('applyForWholeYear')?.setValue(true);
        this.form.get('dayOfMonth')?.setValue(null);
      } else {
        this.form.get('dayOfMonth')?.setValue(plan.dayOfMonth || null);
        this.form.get('applyForWholeMonth')?.setValue(false);
        this.form.get('applyForWholeYear')?.setValue(false);
      }
    } else {
      this.form.get('dayOfMonth')?.setValue(null);
      this.form.get('applyForWholeMonth')?.setValue(false);
      this.form.get('applyForWholeYear')?.setValue(false);
    }
    
    this.updateFormValidators(plan.planType || 'SHORT_TERM');
    this.form.markAsPristine();
    this.form.markAsUntouched();
    this.newCategory = '';
    this.showNewCategoryInput = false;
    // Load categories if not already loaded
    if (this.categories.length === 0) {
      this.fetchCategories();
    }
    this.showCreateModal = true;
  }

  closeCreateModal(): void {
    this.showCreateModal = false;
    this.editingPlan = null;
    this.form.reset();
    this.prefillDefaultDates();
    this.form.markAsPristine();
    this.form.markAsUntouched();
    this.newCategory = '';
    this.showNewCategoryInput = false;
  }

  toggleNewCategoryInput(): void {
    this.showNewCategoryInput = !this.showNewCategoryInput;
    if (this.showNewCategoryInput) {
      this.form.get('category')?.setValue('');
    }
  }

  getCategoryNames(): string[] {
    return this.categories.map(c => c.name);
  }

  private prefillDefaultDates(): void {
    if (!this.form) {
      return;
    }
    const today = new Date();
    const first = new Date(today.getFullYear(), today.getMonth(), 1);
    const last = new Date(today.getFullYear(), today.getMonth() + 1, 0);

    this.form.patchValue({
      startDate: first,
      endDate: last
    }, { emitEvent: false });
  }

  createPlan(): void {
    if (this.editingPlan) {
      this.updatePlan();
      return;
    }

    // Pre-fill category field with new category name so validation passes
    if (this.showNewCategoryInput && this.newCategory.trim()) {
      this.form.get('category')?.setValue(this.newCategory.trim(), { emitEvent: false });
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.toast.showError(this.language.translate('planning.toast.fillForm'));
      return;
    }

    const v = this.form.value;
    const categoryToUse = this.showNewCategoryInput && this.newCategory.trim()
      ? this.newCategory.trim()
      : v.category;

    if (!categoryToUse || categoryToUse.trim() === '') {
      this.toast.showError(this.language.translate('planning.toast.fillForm'));
      return;
    }

    // If adding new category, save it first
    if (this.showNewCategoryInput && this.newCategory.trim()) {
      this.categoryService.addCategory(this.userId, this.newCategory.trim()).subscribe({
        next: (newCategory) => {
          this.categories.push(newCategory);
          // Then create plan
          this.createPlanAfterCategorySave(categoryToUse, v);
        },
        error: (err) => {
          console.error('Error adding category:', err);
          // Still try to create plan with the category name
          this.createPlanAfterCategorySave(categoryToUse, v);
        }
      });
    } else {
      this.createPlanAfterCategorySave(categoryToUse, v);
    }
  }

  updatePlan(): void {
    if (!this.editingPlan || !this.editingPlan.id) {
      return;
    }

    // Pre-fill category field with new category name so validation passes
    if (this.showNewCategoryInput && this.newCategory.trim()) {
      this.form.get('category')?.setValue(this.newCategory.trim(), { emitEvent: false });
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.toast.showError(this.language.translate('planning.toast.fillForm'));
      return;
    }

    const v = this.form.value;
    const categoryToUse = this.showNewCategoryInput && this.newCategory.trim()
      ? this.newCategory.trim()
      : v.category;

    if (!categoryToUse || categoryToUse.trim() === '') {
      this.toast.showError(this.language.translate('planning.toast.fillForm'));
      return;
    }

    // If adding new category, save it first
    if (this.showNewCategoryInput && this.newCategory.trim()) {
      this.categoryService.addCategory(this.userId, this.newCategory.trim()).subscribe({
        next: (newCategory) => {
          this.categories.push(newCategory);
          // Then update plan
          this.updatePlanAfterCategorySave(categoryToUse, v);
        },
        error: (err) => {
          console.error('Error adding category:', err);
          // Still try to update plan with the category name
          this.updatePlanAfterCategorySave(categoryToUse, v);
        }
      });
    } else {
      this.updatePlanAfterCategorySave(categoryToUse, v);
    }
  }

  private updatePlanAfterCategorySave(category: string, formValues: any): void {
    if (!this.editingPlan || !this.editingPlan.id) {
      return;
    }

    const planType = formValues.planType as PlanType;
    const payload: Partial<PlanningBudget> = {
      category: category,
      budgetAmount: Number(formValues.budgetAmount),
      planType: planType
    };

    // Add fields based on plan type
    if (planType === 'SHORT_TERM' || planType === 'LONG_TERM') {
      payload.startDate = this.dateToString(formValues.startDate);
      if (planType === 'SHORT_TERM' || formValues.endDate) {
        payload.endDate = this.dateToString(formValues.endDate);
      }
      if (planType === 'LONG_TERM' && formValues.repeatCycle) {
        payload.repeatCycle = formValues.repeatCycle as RepeatCycle;
      }
    } else if (planType === 'RECURRING') {
      payload.repeatCycle = formValues.repeatCycle as RepeatCycle;
      // For MONTHLY with applyForWholeMonth, or YEARLY with applyForWholeYear, dayOfMonth should be null
      if ((formValues.repeatCycle === 'MONTHLY' && formValues.applyForWholeMonth) ||
          (formValues.repeatCycle === 'YEARLY' && formValues.applyForWholeYear)) {
        payload.dayOfMonth = undefined; // Will be null in backend
      } else {
        payload.dayOfMonth = formValues.dayOfMonth ? Number(formValues.dayOfMonth) : undefined;
      }
      // Optional startDate for RECURRING
      if (formValues.startDate) {
        payload.startDate = this.dateToString(formValues.startDate);
      }
    }

    this.saving = true;
    this.planningService.update(this.editingPlan.id, payload).subscribe({
      next: () => {
        this.toast.showSuccess(this.language.translate('planning.toast.updateSuccess'));
        this.saving = false;
        this.closeCreateModal();
        this.fetchBudgets();
      },
      error: () => {
        this.saving = false;
        this.toast.showError(this.language.translate('planning.toast.updateError'));
      }
    });
  }

  private createPlanAfterCategorySave(category: string, formValues: any): void {
    const planType = formValues.planType as PlanType;
    const payload: PlanningBudget = {
      userId: this.userId,
      category: category,
      budgetAmount: Number(formValues.budgetAmount),
      planType: planType
    };

    // Add fields based on plan type
    if (planType === 'SHORT_TERM' || planType === 'LONG_TERM') {
      payload.startDate = this.dateToString(formValues.startDate);
      if (planType === 'SHORT_TERM' || formValues.endDate) {
        payload.endDate = this.dateToString(formValues.endDate);
      }
      if (planType === 'LONG_TERM' && formValues.repeatCycle) {
        payload.repeatCycle = formValues.repeatCycle as RepeatCycle;
      }
    } else if (planType === 'RECURRING') {
      payload.repeatCycle = formValues.repeatCycle as RepeatCycle;
      // For MONTHLY with applyForWholeMonth, or YEARLY with applyForWholeYear, dayOfMonth should be null
      if ((formValues.repeatCycle === 'MONTHLY' && formValues.applyForWholeMonth) ||
          (formValues.repeatCycle === 'YEARLY' && formValues.applyForWholeYear)) {
        payload.dayOfMonth = undefined; // Will be null in backend
      } else {
        payload.dayOfMonth = formValues.dayOfMonth ? Number(formValues.dayOfMonth) : undefined;
      }
      // Optional startDate for RECURRING
      if (formValues.startDate) {
        payload.startDate = this.dateToString(formValues.startDate);
      }
    }

    this.saving = true;
    this.planningService.create(payload).subscribe({
      next: () => {
        this.toast.showSuccess(this.language.translate('planning.toast.createSuccess'));
        this.saving = false;
        this.closeCreateModal();
        this.fetchBudgets();
      },
      error: () => {
        this.saving = false;
        this.toast.showError(this.language.translate('planning.toast.createError'));
      }
    });
  }

  deleteCategory(categoryName: string): void {
    if (!this.userId || !categoryName) return;

    // Check if it's a default category (không xác định)
    const category = this.categories.find(c => c.name === categoryName);
    if (category && category.isDefault) {
      this.toast.showError(this.language.translate('planning.toast.cannotDeleteDefault') || 'Không thể xóa loại mặc định');
      return;
    }

    this.categoryService.deleteCategory(this.userId, categoryName).subscribe({
      next: () => {
        // Remove from local list
        this.categories = this.categories.filter(c => c.name !== categoryName);
        // If this was the selected category, reset it
        if (this.form.get('category')?.value === categoryName) {
          this.form.get('category')?.setValue('');
        }
        this.toast.showSuccess(this.language.translate('planning.toast.deleteCategorySuccess') || 'Đã xóa loại thành công');
      },
      error: (err) => {
        console.error('Error deleting category:', err);
        const errorMsg = err.error?.error || (this.language.translate('planning.toast.deleteCategoryError') || 'Không thể xóa loại này');
        this.toast.showError(errorMsg);
      }
    });
  }

  delete(plan: PlanningBudget): void {
    if (!plan.id) {
      return;
    }
    this.deletingId = plan.id;
    this.planningService.delete(plan.id).subscribe({
      next: () => {
        this.toast.showSuccess(this.language.translate('planning.toast.deleteSuccess'));
        this.deletingId = null;
        this.fetchBudgets();
      },
      error: () => {
        this.deletingId = null;
        this.toast.showError(this.language.translate('planning.toast.deleteError'));
      }
    });
  }

  private computeTotals(): void {
    const totalBudget = this.budgets.reduce((sum, plan) => sum + (plan.budgetAmount || 0), 0);
    const totalSpent = this.budgets.reduce((sum, plan) => sum + (plan.spentAmount || 0), 0);
    this.totals = {
      totalBudget,
      totalSpent,
      totalRemaining: totalBudget - totalSpent
    };
  }

  isOverspent(plan: PlanningBudget): boolean {
    return (plan.spentAmount || 0) > (plan.budgetAmount || 0);
  }

  progressPercent(plan: PlanningBudget): number {
    const budget = plan.budgetAmount || 0;
    const spent = plan.spentAmount || 0;
    if (budget <= 0) {
      return spent > 0 ? 100 : 0;
    }
    const percent = (spent / budget) * 100;
    return Math.min(Math.max(percent, 0), 100);
  }

  remainingAmount(plan: PlanningBudget): number {
    return (plan.budgetAmount || 0) - (plan.spentAmount || 0);
  }

  trackByPlan(index: number, plan: PlanningBudget): string {
    return plan.id || `${plan.category}-${index}`;
  }

  iconFor(category: string | undefined): string {
    if (!category) return 'fa-folder-open';
    
    // Normalize Vietnamese characters and convert to lowercase
    const c = category.toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '') // Remove diacritics
      .trim();
    
    // Check for food/eating related
    if (c.includes('an') || c.includes('uong') || c.includes('an uong') || 
        c.includes('food') || c.includes('eat') || c.includes('dining') ||
        c.includes('restaurant') || c.includes('cafe') || c.includes('meal') ||
        c.includes('com') || c.includes('thuc an') || c.includes('do an')) {
      return 'fa-cutlery'; // Changed to cutlery icon (utensils) for better visibility
    }
    
    // Check for transportation/travel related
    if (c.includes('giao thong') || c.includes('giao thong') || 
        c.includes('di chuyen') || c.includes('travel') || 
        c.includes('move') || c.includes('transport') || 
        c.includes('xe') || c.includes('car') || c.includes('bus') ||
        c.includes('taxi') || c.includes('uber') || c.includes('grab')) {
      return 'fa-car';
    }
    
    // Check for shopping
    if (c.includes('mua') || c.includes('shop') || c.includes('buy') ||
        c.includes('shopping') || c.includes('market')) {
      return 'fa-shopping-bag';
    }
    
    // Check for home/housing
    if (c.includes('nha') || c.includes('rent') || c.includes('home') ||
        c.includes('house') || c.includes('apartment')) {
      return 'fa-home';
    }
    
    // Check for entertainment
    if (c.includes('giai tri') || c.includes('entertain') ||
        c.includes('movie') || c.includes('cinema') || c.includes('game')) {
      return 'fa-film';
    }
    
    // Check for education
    if (c.includes('hoc') || c.includes('education') || c.includes('study') ||
        c.includes('school') || c.includes('university')) {
      return 'fa-book';
    }
    
    // Check for health
    if (c.includes('suc khoe') || c.includes('health') ||
        c.includes('medical') || c.includes('hospital') || c.includes('doctor')) {
      return 'fa-heartbeat';
    }
    
    // Default icon
    return 'fa-folder-open';
  }

  formatRange(plan: PlanningBudget): string {
    if (plan.planType === 'RECURRING') {
      if (plan.repeatCycle) {
        const cycle = this.language.translate(`planning.modal.${plan.repeatCycle.toLowerCase()}`);
        // For MONTHLY without dayOfMonth, show "Cả tháng" (Whole month)
        if (plan.repeatCycle === 'MONTHLY' && !plan.dayOfMonth) {
          return `${cycle} (${this.language.translate('planning.modal.wholeMonth')})`;
        }
        // For YEARLY without dayOfMonth, show "Cả năm" (Whole year)
        if (plan.repeatCycle === 'YEARLY' && !plan.dayOfMonth) {
          return `${cycle} (${this.language.translate('planning.modal.wholeYear')})`;
        }
        const day = plan.dayOfMonth ? ` (${this.language.translate('planning.modal.dayOfMonth')}: ${plan.dayOfMonth})` : '';
        return `${cycle}${day}`;
      }
      return this.language.translate('planning.modal.planTypeRecurring');
    } else if (plan.planType === 'LONG_TERM') {
      const start = this.formatDisplayDate(plan.startDate);
      const end = this.formatDisplayDate(plan.endDate);
      if (plan.repeatCycle) {
        const cycle = this.language.translate(`planning.modal.${plan.repeatCycle.toLowerCase()}`);
        return start ? `${start} - ${cycle}` : cycle;
      }
      return start ? (end ? `${start} - ${end}` : `${start} - ...`) : '';
    } else {
      // SHORT_TERM
      const start = this.formatDisplayDate(plan.startDate);
      const end = this.formatDisplayDate(plan.endDate);
      if (!start && !end) {
        return '';
      }
      return `${start} - ${end}`;
    }
  }

  private formatDisplayDate(dateStr?: string): string {
    if (!dateStr) return '';
    const parsed = new Date(dateStr);
    if (Number.isNaN(parsed.getTime())) {
      return dateStr;
    }
    const lang = this.language.getCurrentLanguage() === 'en' ? 'en-US' : 'vi-VN';
    return parsed.toLocaleDateString(lang);
  }

  private formatDateInput(date: Date): string {
    return date.toISOString().split('T')[0];
  }

  private dateToString(date: any): string {
    if (!date) return '';
    if (date instanceof Date) {
      return date.toISOString().split('T')[0];
    }
    return String(date);
  }

  private stringToDate(dateStr?: string): Date | null {
    if (!dateStr) return null;
    const parsed = new Date(dateStr);
    return isNaN(parsed.getTime()) ? null : parsed;
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

  onBudgetInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const raw = input.value.replace(/,/g, '').replace(/[^0-9]/g, '');
    const num = raw === '' ? null : Number(raw);
    this.form.get('budgetAmount')?.setValue(num, { emitEvent: false });
    this.form.get('budgetAmount')?.markAsTouched();
    input.value = this.formatNumberWithDots(raw);
  }
}


