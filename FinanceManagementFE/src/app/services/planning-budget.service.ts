import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type PlanType = 'SHORT_TERM' | 'LONG_TERM' | 'RECURRING';
export type RepeatCycle = 'MONTHLY' | 'QUARTERLY' | 'YEARLY';

export interface PlanningBudget {
  id?: string;
  userId: string;
  category: string;
  budgetAmount: number;
  spentAmount?: number;
  planType: PlanType;
  startDate?: string; // yyyy-MM-dd - required for SHORT_TERM and LONG_TERM
  endDate?: string;   // yyyy-MM-dd - required for SHORT_TERM, optional for LONG_TERM
  repeatCycle?: RepeatCycle; // required for RECURRING, optional for LONG_TERM
  dayOfMonth?: number; // 1-31, required for RECURRING
  icon?: string;
  color?: string;
}

@Injectable({ providedIn: 'root' })
export class PlanningBudgetService {
  private apiPrefix = '/finances/api/v1';

  constructor(private http: HttpClient) {}

  private getHeaders(): HttpHeaders {
    const token = window.sessionStorage.getItem('Authorization');
    return new HttpHeaders({
      'Authorization': token || '',
      'Content-Type': 'application/json'
    });
  }

  list(userId: string): Observable<PlanningBudget[]> {
    const params = new HttpParams().set('userId', userId);
    return this.http.get<PlanningBudget[]>(`${environment.rooturl}${this.apiPrefix}/planning-budgets`, {
      headers: this.getHeaders(),
      params
    });
  }

  create(body: PlanningBudget): Observable<PlanningBudget> {
    return this.http.post<PlanningBudget>(`${environment.rooturl}${this.apiPrefix}/planning-budgets`, body, {
      headers: this.getHeaders()
    });
  }

  update(id: string, body: Partial<PlanningBudget>): Observable<PlanningBudget> {
    return this.http.put<PlanningBudget>(`${environment.rooturl}${this.apiPrefix}/planning-budgets/${id}`, body, {
      headers: this.getHeaders()
    });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${environment.rooturl}${this.apiPrefix}/planning-budgets/${id}`, {
      headers: this.getHeaders()
    });
  }

  recalculateSpentAmount(planningId: string, bankAccountIds: string[]): Observable<void> {
    return this.http.post<void>(
      `${environment.rooturl}${this.apiPrefix}/planning-budgets/${planningId}/recalculate`,
      { bankAccountIds },
      { headers: this.getHeaders() }
    );
  }

  recalculateAllSpentAmounts(userId: string, bankAccountIds: string[]): Observable<void> {
    const params = new HttpParams().set('userId', userId);
    return this.http.post<void>(
      `${environment.rooturl}${this.apiPrefix}/planning-budgets/recalculate-all`,
      { bankAccountIds },
      { headers: this.getHeaders(), params }
    );
  }

  recalculateAllSpentAmountsWithDateRange(
    userId: string,
    bankAccountIds: string[],
    startDate?: string,
    endDate?: string
  ): Observable<void> {
    let params = new HttpParams().set('userId', userId);
    if (startDate) {
      params = params.set('startDate', startDate);
    }
    if (endDate) {
      params = params.set('endDate', endDate);
    }
    return this.http.post<void>(
      `${environment.rooturl}${this.apiPrefix}/planning-budgets/recalculate-all-with-range`,
      { bankAccountIds },
      { headers: this.getHeaders(), params }
    );
  }
}


