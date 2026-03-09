import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { CronService, CronPreset, CronValidationResponse, NextExecutionsResponse } from '../../../services/cron.service';
import { LanguageService } from '../../../services/language.service';

@Component({
  selector: 'app-cron-builder',
  templateUrl: './cron-builder.component.html',
  styleUrls: ['./cron-builder.component.css']
})
export class CronBuilderComponent implements OnInit {
  @Input() initialExpression: string = '';
  @Output() cronChange = new EventEmitter<string>();
  @Output() validChange = new EventEmitter<boolean>();

  // Mode selection
  mode: 'simple' | 'advanced' = 'simple';

  // Simple mode - Presets
  presets: CronPreset[] = [];
  selectedPreset: string = '';
  loadingPresets: boolean = false;

  // Simple mode - Custom builder
  customFrequency: 'daily' | 'weekly' | 'monthly' = 'daily';
  customHour: number = 9;
  customMinute: number = 0;
  selectedDaysOfWeek: string[] = []; // 'MON', 'TUE', etc.
  selectedDayOfMonth: number = 1; // 1-31

  daysOfWeek = [
    { value: 'MON', label: 'Monday' },
    { value: 'TUE', label: 'Tuesday' },
    { value: 'WED', label: 'Wednesday' },
    { value: 'THU', label: 'Thursday' },
    { value: 'FRI', label: 'Friday' },
    { value: 'SAT', label: 'Saturday' },
    { value: 'SUN', label: 'Sunday' }
  ];

  // Advanced mode
  cronExpression: string = '';

  // Validation & Preview
  isValid: boolean = false;
  validationMessage: string = '';
  description: string = '';
  nextExecutions: string[] = [];
  isLoadingPreview: boolean = false;

  constructor(
    private cronService: CronService,
    public language: LanguageService
  ) {}

  ngOnInit(): void {
    this.loadPresets();

    if (this.initialExpression && this.initialExpression.trim()) {
      this.cronExpression = this.initialExpression.trim();
      this.mode = 'advanced';
      this.validateAndPreview();
    }
  }

  loadPresets(): void {
    this.loadingPresets = true;
    this.cronService.getPresets().subscribe({
      next: (presets) => {
        this.presets = presets;
        this.loadingPresets = false;
      },
      error: (err) => {
        console.error('Error loading presets:', err);
        this.loadingPresets = false;
      }
    });
  }

  onModeChange(newMode: 'simple' | 'advanced'): void {
    this.mode = newMode;
  }

  onPresetSelect(): void {
    if (this.selectedPreset) {
      this.cronExpression = this.selectedPreset;
      this.validateAndPreview();
      this.emitChange();
    }
  }

  buildSimpleCron(): void {
    let cron = '';

    const minute = this.customMinute || 0;
    const hour = this.customHour || 9;

    if (this.customFrequency === 'daily') {
      // Every day at specified time
      cron = `0 ${minute} ${hour} * * ?`;
    } else if (this.customFrequency === 'weekly') {
      // Specific days of week at specified time
      const days = this.selectedDaysOfWeek.length > 0
        ? this.selectedDaysOfWeek.sort().join(',')
        : 'MON';
      cron = `0 ${minute} ${hour} ? * ${days}`;
    } else if (this.customFrequency === 'monthly') {
      // Specific day of month at specified time
      const dayOfMonth = this.selectedDayOfMonth || 1;
      cron = `0 ${minute} ${hour} ${dayOfMonth} * ?`;
    }

    this.cronExpression = cron;
    this.validateAndPreview();
    this.emitChange();
  }

  toggleDayOfWeek(day: string): void {
    const index = this.selectedDaysOfWeek.indexOf(day);
    if (index > -1) {
      this.selectedDaysOfWeek.splice(index, 1);
    } else {
      this.selectedDaysOfWeek.push(day);
    }
    this.buildSimpleCron();
  }

  isDaySelected(day: string): boolean {
    return this.selectedDaysOfWeek.includes(day);
  }

  validateAndPreview(): void {
    if (!this.cronExpression || this.cronExpression.trim() === '') {
      this.isValid = false;
      this.validationMessage = 'Cron expression is required';
      this.description = '';
      this.nextExecutions = [];
      this.validChange.emit(false);
      return;
    }

    this.isLoadingPreview = true;

    this.cronService.validate(this.cronExpression).subscribe({
      next: (response: CronValidationResponse) => {
        this.isValid = response.valid;
        this.validationMessage = response.message;
        this.description = response.description;
        this.validChange.emit(response.valid);

        if (response.valid) {
          this.loadNextExecutions();
        } else {
          this.isLoadingPreview = false;
          this.nextExecutions = [];
        }
      },
      error: (err) => {
        console.error('Validation error:', err);
        this.isValid = false;
        this.validationMessage = 'Validation error';
        this.isLoadingPreview = false;
        this.validChange.emit(false);
      }
    });
  }

  loadNextExecutions(): void {
    this.cronService.getNextExecutions(this.cronExpression, 5).subscribe({
      next: (response: NextExecutionsResponse) => {
        this.nextExecutions = response.nextExecutions.map(timestamp => {
          // Format ISO timestamp to readable format
          const date = new Date(timestamp);
          return date.toLocaleString('en-US', {
            weekday: 'short',
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
          });
        });
        this.isLoadingPreview = false;
      },
      error: (err) => {
        console.error('Error loading next executions:', err);
        this.nextExecutions = [];
        this.isLoadingPreview = false;
      }
    });
  }

  onAdvancedCronChange(): void {
    this.validateAndPreview();
    this.emitChange();
  }

  emitChange(): void {
    this.cronChange.emit(this.cronExpression);
  }
}
