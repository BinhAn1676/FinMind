import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { SepayOAuth2Service } from '../../services/sepay-oauth2.service';
import { ToastService } from '../../services/toast.service';
import { LanguageService } from '../../services/language.service';

/**
 * Handles the OAuth2 callback from SePay.
 * SePay redirects here with ?code=AUTH_CODE&state=STATE after user grants permission.
 */
@Component({
  selector: 'app-sepay-callback',
  template: `
    <div class="callback-container">
      <div class="callback-card">
        <div *ngIf="loading" class="loading-state">
          <div class="spinner"></div>
          <h3>{{ language.translate('sepayCallbackProcessing') }}</h3>
          <p>{{ language.translate('sepayCallbackWait') }}</p>
        </div>
        <div *ngIf="!loading && success" class="success-state">
          <div class="success-icon">✅</div>
          <h3>{{ language.translate('sepayCallbackSuccess') }}</h3>
          <p>{{ language.translate('sepayCallbackSuccessDesc') }}</p>
          <button class="btn btn-primary" (click)="goToProfile()">
            {{ language.translate('sepayCallbackGoToProfile') }}
          </button>
        </div>
        <div *ngIf="!loading && !success" class="error-state">
          <div class="error-icon">❌</div>
          <h3>{{ language.translate('sepayCallbackError') }}</h3>
          <p>{{ errorMessage }}</p>
          <button class="btn btn-primary" (click)="goToProfile()">
            {{ language.translate('sepayCallbackRetry') }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .callback-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100vh;
      background: #f5f7fa;
    }
    .callback-card {
      background: white;
      border-radius: 16px;
      padding: 48px;
      text-align: center;
      box-shadow: 0 4px 20px rgba(0,0,0,0.1);
      max-width: 480px;
      width: 90%;
    }
    .spinner {
      width: 48px;
      height: 48px;
      border: 4px solid #e0e0e0;
      border-top: 4px solid #4f46e5;
      border-radius: 50%;
      animation: spin 1s linear infinite;
      margin: 0 auto 24px;
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
    }
    .success-icon, .error-icon {
      font-size: 48px;
      margin-bottom: 16px;
    }
    h3 { margin-bottom: 8px; color: #1a1a2e; }
    p { color: #666; margin-bottom: 24px; }
    .btn-primary {
      background: #4f46e5;
      color: white;
      border: none;
      padding: 12px 32px;
      border-radius: 8px;
      cursor: pointer;
      font-size: 14px;
      font-weight: 600;
    }
    .btn-primary:hover { background: #4338ca; }
  `]
})
export class SepayCallbackComponent implements OnInit {
  loading = true;
  success = false;
  errorMessage = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private sepayOAuth2Service: SepayOAuth2Service,
    private toastService: ToastService,
    public language: LanguageService
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      const code = params['code'];
      const state = params['state'];
      const error = params['error'];

      if (error) {
        this.loading = false;
        this.success = false;
        this.errorMessage = params['error_description'] || 'Authorization was denied';
        return;
      }

      if (!code) {
        this.loading = false;
        this.success = false;
        this.errorMessage = 'No authorization code received';
        return;
      }

      // Extract userId from state (format: userId:uuid)
      let userId = '';
      if (state && state.includes(':')) {
        userId = state.split(':')[0];
      }

      if (!userId) {
        // Try to get from session storage
        userId = window.sessionStorage.getItem('sepay_oauth2_userId') || '';
      }

      if (!userId) {
        this.loading = false;
        this.success = false;
        this.errorMessage = 'User ID not found. Please try again from your profile.';
        return;
      }

      // Exchange code for tokens
      this.sepayOAuth2Service.exchangeCode(code, userId, state).subscribe({
        next: (status) => {
          this.loading = false;
          if (status.connected) {
            this.success = true;
            this.toastService.showSuccess(this.language.translate('sepayConnectSuccess'));
          } else {
            this.success = false;
            this.errorMessage = 'Failed to connect. Please try again.';
          }
          // Clean up
          window.sessionStorage.removeItem('sepay_oauth2_userId');
        },
        error: (err) => {
          this.loading = false;
          this.success = false;
          this.errorMessage = err.error?.message || 'An error occurred during connection.';
          console.error('SePay OAuth2 callback error:', err);
        }
      });
    });
  }

  goToProfile(): void {
    this.router.navigate(['/profile']);
  }
}
