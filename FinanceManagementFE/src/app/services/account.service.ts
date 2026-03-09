import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private apiPrefix = '/finances/api/v1';

  constructor(private http: HttpClient) { }

  private getHeaders(): HttpHeaders {
    const token = window.sessionStorage.getItem('Authorization');
    return new HttpHeaders({
      'Authorization': token || '',
      'Content-Type': 'application/json'
    });
  }

  filter(userId: string, textSearch: string, page = 0, size = 10): Observable<any> {
    let params = new HttpParams()
      .set('userId', userId)
      .set('textSearch', textSearch || '')
      .set('page', page + '')
      .set('size', size + '');
    return this.http.get(`${environment.rooturl}${this.apiPrefix}/accounts`, {
      headers: this.getHeaders(),
      params
    });
  }

  createBankAccount(userId: string, accountNumber: string, accountHolderName: string,
                   bankShortName: string, bankFullName: string, bankCode: string,
                   label: string, accumulated: string): Observable<any> {
    const body = {
      accountNumber,
      accountHolderName,
      bankShortName,
      bankFullName,
      bankCode,
      label,
      accumulated
    };
    return this.http.post(`${environment.rooturl}${this.apiPrefix}/accounts?userId=${userId}`, body, {
      headers: this.getHeaders()
    });
  }

  updateAccount(id: string, label: string, accumulated: string): Observable<any> {
    const body = { label, accumulated };
    return this.http.post(`${environment.rooturl}${this.apiPrefix}/accounts/${id}`, body, {
      headers: this.getHeaders(),
    });
  }

  getAccountDetail(id: string): Observable<any> {
    return this.http.get(`${environment.rooturl}${this.apiPrefix}/accounts/${id}`, {
      headers: this.getHeaders()
    });
  }

  deleteAccount(id: string): Observable<any> {
    return this.http.delete(`${environment.rooturl}${this.apiPrefix}/accounts/${id}`, {
      headers: this.getHeaders(),
    });
  }

  summary(userId: string): Observable<{ totalBalance: number; accountCount: number; }> {
    const params = new HttpParams().set('userId', userId);
    return this.http.get<{ totalBalance: number; accountCount: number; }>(
      `${environment.rooturl}${this.apiPrefix}/accounts/summary`,
      { headers: this.getHeaders(), params }
    );
  }

  manualSyncSepay(userId: string): Observable<any> {
    return this.http.post(`${environment.rooturl}${this.apiPrefix}/sepay/sync/accounts?userId=${userId}`, {}, {
      headers: this.getHeaders()
    });
  }

  getAccountDistribution(userId: string): Observable<any[]> {
    const params = new HttpParams().set('userId', userId);
    return this.http.get<any[]>(
      `${environment.rooturl}${this.apiPrefix}/accounts/distribution`,
      { headers: this.getHeaders(), params }
    );
  }

  // External Account Methods
  filterExternalAccounts(userId: string, textSearch: string, page = 0, size = 10): Observable<any> {
    let params = new HttpParams()
      .set('userId', userId)
      .set('textSearch', textSearch || '')
      .set('page', page + '')
      .set('size', size + '');
    return this.http.get(`${environment.rooturl}${this.apiPrefix}/external-accounts`, {
      headers: this.getHeaders(),
      params
    });
  }

  createExternalAccount(userId: string, label: string, type: string, accumulated: string, description?: string): Observable<any> {
    const body = { label, type, accumulated, description: description || '' };
    return this.http.post(`${environment.rooturl}${this.apiPrefix}/external-accounts?userId=${userId}`, body, {
      headers: this.getHeaders(),
    });
  }

  updateExternalAccount(id: string, label: string, type: string, accumulated: string, description?: string): Observable<any> {
    const body = { label, type, accumulated, description: description || '' };
    return this.http.post(`${environment.rooturl}${this.apiPrefix}/external-accounts/${id}`, body, {
      headers: this.getHeaders(),
    });
  }

  getExternalAccountDetail(id: string): Observable<any> {
    return this.http.get(`${environment.rooturl}${this.apiPrefix}/external-accounts/${id}`, {
      headers: this.getHeaders()
    });
  }

  deleteExternalAccount(id: string): Observable<any> {
    return this.http.delete(`${environment.rooturl}${this.apiPrefix}/external-accounts/${id}`, {
      headers: this.getHeaders(),
    });
  }

  summaryExternalAccounts(userId: string): Observable<{ totalBalance: number; accountCount: number; }> {
    const params = new HttpParams().set('userId', userId);
    return this.http.get<{ totalBalance: number; accountCount: number; }>(
      `${environment.rooturl}${this.apiPrefix}/external-accounts/summary`,
      { headers: this.getHeaders(), params }
    );
  }

  getExternalAccountDistribution(userId: string): Observable<any[]> {
    const params = new HttpParams().set('userId', userId);
    return this.http.get<any[]>(
      `${environment.rooturl}${this.apiPrefix}/external-accounts/distribution`,
      { headers: this.getHeaders(), params }
    );
  }
}
