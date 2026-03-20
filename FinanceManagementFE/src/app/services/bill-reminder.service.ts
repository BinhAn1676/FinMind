import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface BillPayment {
  id?: string;
  period: string;
  paidAt?: string;
  note?: string;
}

export interface BillReminder {
  id?: string;
  userId?: string;
  name: string;
  amount: number;
  cycle: 'MONTHLY' | 'QUARTERLY' | 'YEARLY';
  dayOfMonth: number;
  icon?: string;
  color?: string;
  notes?: string;
  isActive?: boolean;
  remindDaysBefore?: number;
  payments?: BillPayment[];
  createdAt?: string;
  updatedAt?: string;
}

@Injectable({ providedIn: 'root' })
export class BillReminderService {
  private readonly api = `${environment.rooturl}/finances/api/v1/bill-reminders`;

  constructor(private http: HttpClient) {}

  private headers(): HttpHeaders {
    const token = window.sessionStorage.getItem('Authorization') || '';
    return new HttpHeaders({ Authorization: token });
  }

  getByUser(userId: string): Observable<BillReminder[]> {
    const params = new HttpParams().set('userId', userId);
    return this.http.get<BillReminder[]>(this.api, { headers: this.headers(), params });
  }

  create(bill: BillReminder): Observable<BillReminder> {
    return this.http.post<BillReminder>(this.api, bill, { headers: this.headers() });
  }

  update(id: string, bill: BillReminder): Observable<BillReminder> {
    return this.http.put<BillReminder>(`${this.api}/${id}`, bill, { headers: this.headers() });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/${id}`, { headers: this.headers() });
  }

  markAsPaid(id: string, note?: string): Observable<BillReminder> {
    return this.http.post<BillReminder>(`${this.api}/${id}/pay`, { note: note || null }, { headers: this.headers() });
  }

  unmarkAsPaid(id: string): Observable<BillReminder> {
    return this.http.delete<BillReminder>(`${this.api}/${id}/pay`, { headers: this.headers() });
  }
}
