import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type PlanType = 'SHORT_TERM' | 'LONG_TERM' | 'RECURRING';
export type RepeatCycle = 'MONTHLY' | 'QUARTERLY' | 'YEARLY';

export interface GroupPlanning {
  id?: string;
  groupId: number;
  category: string;
  budgetAmount: number;
  spentAmount?: number;
  planType: PlanType;
  startDate?: string; // yyyy-MM-dd
  endDate?: string;   // yyyy-MM-dd
  repeatCycle?: RepeatCycle;
  dayOfMonth?: number; // 1-31
  createdBy?: string;
  description?: string;
  icon?: string;
  color?: string;
}

export interface GroupPlanningSummary {
  totalPlannings: number;
  totalBudgetAmount: number;
  totalSpentAmount: number;
  totalRemainingAmount: number;
  overallProgressPercentage: number;
}

@Injectable({ providedIn: 'root' })
export class GroupPlanningService {
  private apiPrefix = '/finances/api/v1';

  constructor(private http: HttpClient) {}

  private getHeaders(): HttpHeaders {
    const token = window.sessionStorage.getItem('Authorization');
    return new HttpHeaders({
      'Authorization': token || '',
      'Content-Type': 'application/json'
    });
  }

  list(groupId: number): Observable<GroupPlanning[]> {
    const params = new HttpParams().set('groupId', groupId.toString());
    return this.http.get<GroupPlanning[]>(`${environment.rooturl}${this.apiPrefix}/group-plannings`, {
      headers: this.getHeaders(),
      params
    });
  }

  getById(id: string): Observable<GroupPlanning> {
    return this.http.get<GroupPlanning>(`${environment.rooturl}${this.apiPrefix}/group-plannings/${id}`, {
      headers: this.getHeaders()
    });
  }

  create(body: GroupPlanning): Observable<GroupPlanning> {
    return this.http.post<GroupPlanning>(`${environment.rooturl}${this.apiPrefix}/group-plannings`, body, {
      headers: this.getHeaders()
    });
  }

  update(id: string, body: Partial<GroupPlanning>): Observable<GroupPlanning> {
    return this.http.put<GroupPlanning>(`${environment.rooturl}${this.apiPrefix}/group-plannings/${id}`, body, {
      headers: this.getHeaders()
    });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${environment.rooturl}${this.apiPrefix}/group-plannings/${id}`, {
      headers: this.getHeaders()
    });
  }

  getSummary(groupId: number): Observable<GroupPlanningSummary> {
    return this.http.get<GroupPlanningSummary>(
      `${environment.rooturl}${this.apiPrefix}/group-plannings/group/${groupId}/summary`,
      { headers: this.getHeaders() }
    );
  }

  recalculateSpentAmount(planningId: string, bankAccountIds: string[]): Observable<void> {
    return this.http.post<void>(
      `${environment.rooturl}${this.apiPrefix}/group-plannings/${planningId}/recalculate`,
      { bankAccountIds },
      { headers: this.getHeaders() }
    );
  }

  recalculateAllSpentAmounts(groupId: number, bankAccountIds: string[]): Observable<void> {
    return this.http.post<void>(
      `${environment.rooturl}${this.apiPrefix}/group-plannings/group/${groupId}/recalculate-all`,
      { bankAccountIds },
      { headers: this.getHeaders() }
    );
  }

  recalculateAllSpentAmountsWithDateRange(
    groupId: number, 
    bankAccountIds: string[],
    startDate?: string,
    endDate?: string
  ): Observable<void> {
    return this.http.post<void>(
      `${environment.rooturl}${this.apiPrefix}/group-plannings/group/${groupId}/recalculate-all`,
      { bankAccountIds, startDate, endDate },
      { headers: this.getHeaders() }
    );
  }
}

