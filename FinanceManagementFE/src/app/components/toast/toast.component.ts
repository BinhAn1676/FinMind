import { Component, OnInit, OnDestroy, ChangeDetectorRef, ViewEncapsulation } from '@angular/core';
import { ToastService, Toast } from '../../services/toast.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-toast',
  templateUrl: './toast.component.html',
  styleUrls: ['./toast.component.css'],
  encapsulation: ViewEncapsulation.None
})
export class ToastComponent implements OnInit, OnDestroy {
  toasts: Toast[] = [];
  private toastSubscription?: Subscription;

  constructor(
    private toastService: ToastService,
    private cdr: ChangeDetectorRef
  ) { }

  ngOnInit(): void {
    console.log('ToastComponent initialized');
    this.toastSubscription = this.toastService.getToast().subscribe(
      (toast: Toast) => {
        console.log('Toast received:', toast);
        if (toast && toast.message) {
          this.toasts.push(toast);
          console.log('Current toasts array:', this.toasts);
          console.log('Toast count:', this.toasts.length);
          
          // Force change detection with setTimeout to ensure DOM is ready
          setTimeout(() => {
            this.cdr.detectChanges();
            console.log('Change detection triggered, checking DOM...');
            // Check if toast is in DOM
            const toastElements = document.querySelectorAll('.toast');
            console.log('Toast elements in DOM:', toastElements.length);
          }, 0);
          
          this.autoRemove(toast, toast.duration || 3000);
        } else {
          console.warn('Invalid toast received:', toast);
        }
      }
    );
  }

  ngOnDestroy(): void {
    if (this.toastSubscription) {
      this.toastSubscription.unsubscribe();
    }
  }

  /**
   * Automatically remove toast after duration
   */
  private autoRemove(toast: Toast, duration: number): void {
    setTimeout(() => {
      this.removeToast(toast.id);
    }, duration);
  }

  /**
   * Manually remove toast
   */
  removeToast(id: number): void {
    this.toasts = this.toasts.filter(t => t.id !== id);
    this.cdr.detectChanges(); // Force change detection
  }

  /**
   * Get CSS class for toast type
   */
  getToastClass(type: string): string {
    return type === 'success' ? 'toast-success' : 'toast-error';
  }

  /**
   * Get icon for toast type
   */
  getToastIcon(type: string): string {
    return type === 'success' ? '✓' : '✕';
  }

  /**
   * Track by function for *ngFor
   */
  trackByToastId(index: number, toast: Toast): number {
    return toast.id;
  }
}

