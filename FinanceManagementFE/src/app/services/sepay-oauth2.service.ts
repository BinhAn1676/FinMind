import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface SepayConnectionStatus {
  oauth2Enabled: boolean;
  connected: boolean;
  scopes?: string;
  connectedAt?: string;
  tokenValid?: boolean;
  authorizeUrl?: string;
}

export interface SyncResult {
  success: boolean;
  message: string;
  processedCount: number;
}

@Injectable({
  providedIn: 'root'
})
export class SepayOAuth2Service {
  private apiPrefix = '/finances/api/v1/sepay/oauth2';

  constructor(private http: HttpClient) {}

  private getHeaders(): HttpHeaders {
    const token = window.sessionStorage.getItem('Authorization');
    return new HttpHeaders({
      'Authorization': token || '',
      'Content-Type': 'application/json'
    });
  }

  /**
   * Get the current SePay connection status for a user.
   */
  getConnectionStatus(userId: string): Observable<SepayConnectionStatus> {
    const params = new HttpParams().set('userId', userId);
    return this.http.get<SepayConnectionStatus>(
      `${environment.rooturl}${this.apiPrefix}/status`,
      { headers: this.getHeaders(), params }
    );
  }

  /**
   * Get the authorization URL to redirect user to SePay OAuth2 consent page.
   */
  getAuthorizeUrl(userId: string): Observable<{ authorizeUrl: string }> {
    const params = new HttpParams().set('userId', userId);
    return this.http.get<{ authorizeUrl: string }>(
      `${environment.rooturl}${this.apiPrefix}/authorize-url`,
      { headers: this.getHeaders(), params }
    );
  }

  /**
   * Exchange authorization code for tokens after SePay redirects back.
   */
  exchangeCode(code: string, userId: string, state?: string): Observable<SepayConnectionStatus> {
    let params = new HttpParams()
      .set('code', code)
      .set('userId', userId);
    if (state) {
      params = params.set('state', state);
    }
    return this.http.post<SepayConnectionStatus>(
      `${environment.rooturl}${this.apiPrefix}/callback`,
      {},
      { headers: this.getHeaders(), params }
    );
  }

  /**
   * Disconnect SePay OAuth2 connection.
   */
  disconnect(userId: string): Observable<{ message: string }> {
    const params = new HttpParams().set('userId', userId);
    return this.http.post<{ message: string }>(
      `${environment.rooturl}${this.apiPrefix}/disconnect`,
      {},
      { headers: this.getHeaders(), params }
    );
  }

  /**
   * Manually sync accounts using OAuth2 tokens.
   */
  syncAccountsOAuth2(userId: string): Observable<SyncResult> {
    const params = new HttpParams().set('userId', userId);
    return this.http.post<SyncResult>(
      `${environment.rooturl}${this.apiPrefix}/sync/accounts`,
      {},
      { headers: this.getHeaders(), params }
    );
  }

  /**
   * Manually sync transactions using OAuth2 tokens.
   */
  syncTransactionsOAuth2(userId: string): Observable<SyncResult> {
    const params = new HttpParams().set('userId', userId);
    return this.http.post<SyncResult>(
      `${environment.rooturl}${this.apiPrefix}/sync/transactions`,
      {},
      { headers: this.getHeaders(), params }
    );
  }

  /**
   * Setup/recreate webhooks for a user.
   */
  setupWebhooks(userId: string): Observable<{ message: string }> {
    const params = new HttpParams().set('userId', userId);
    return this.http.post<{ message: string }>(
      `${environment.rooturl}${this.apiPrefix}/webhooks/setup`,
      {},
      { headers: this.getHeaders(), params }
    );
  }
}
