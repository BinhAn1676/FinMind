import { Injectable } from '@angular/core';
import { Subject, Observable } from 'rxjs';

export interface Toast {
  id: number;
  message: string;
  type: 'success' | 'error';
  duration?: number;
}

@Injectable({
  providedIn: 'root'
})
export class ToastService {
  private toastSubject = new Subject<Toast>();
  private toastIdCounter = 0;

  constructor() { }

  /**
   * Get toast observable for component subscription
   */
  getToast(): Observable<Toast> {
    return this.toastSubject.asObservable();
  }

  /**
   * Show success toast message (green)
   * @param message Message to display
   * @param duration Duration in milliseconds (default: 3000)
   */
  showSuccess(message: string, duration: number = 3000): void {
    if (!message || message.trim() === '') {
      console.warn('ToastService.showSuccess: Empty message provided');
      return;
    }
    console.log('ToastService.showSuccess called with:', message);
    const toast: Toast = {
      id: this.toastIdCounter++,
      message: message,
      type: 'success',
      duration: duration
    };
    this.toastSubject.next(toast);
  }

  /**
   * Show error toast message (red)
   * @param message Message to display
   * @param duration Duration in milliseconds (default: 4000)
   */
  showError(message: string, duration: number = 4000): void {
    if (!message || message.trim() === '') {
      console.warn('ToastService.showError: Empty message provided');
      return;
    }
    console.log('ToastService.showError called with:', message);
    const toast: Toast = {
      id: this.toastIdCounter++,
      message: message,
      type: 'error',
      duration: duration
    };
    this.toastSubject.next(toast);
  }
}

