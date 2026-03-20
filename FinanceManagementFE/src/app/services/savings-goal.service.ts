import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface SavingsContribution {
  id?: string;
  amount: number;
  note?: string;
  createdAt?: string;
}

export interface SavingsGoal {
  id?: string;
  userId?: string;
  groupId?: number;
  name: string;
  description?: string;
  targetAmount: number;
  currentAmount?: number;
  targetDate?: string;
  status?: 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
  icon?: string;
  color?: string;
  contributions?: SavingsContribution[];
  createdAt?: string;
  updatedAt?: string;
  autoSaveAmount?: number;
  autoSaveCycle?: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  autoSaveEnabled?: boolean;
  lastAutoSaveAt?: string;
}

@Injectable({ providedIn: 'root' })
export class SavingsGoalService {
  private readonly api = `${environment.rooturl}/finances/api/v1/savings-goals`;

  constructor(private http: HttpClient) {}

  private headers(): HttpHeaders {
    const token = window.sessionStorage.getItem('Authorization') || '';
    return new HttpHeaders({ Authorization: token });
  }

  getByUser(userId: string): Observable<SavingsGoal[]> {
    const params = new HttpParams().set('userId', userId);
    return this.http.get<SavingsGoal[]>(this.api, { headers: this.headers(), params });
  }

  getByGroup(groupId: number): Observable<SavingsGoal[]> {
    return this.http.get<SavingsGoal[]>(`${this.api}/group/${groupId}`, { headers: this.headers() });
  }

  create(goal: SavingsGoal): Observable<SavingsGoal> {
    return this.http.post<SavingsGoal>(this.api, goal, { headers: this.headers() });
  }

  update(id: string, goal: SavingsGoal): Observable<SavingsGoal> {
    return this.http.put<SavingsGoal>(`${this.api}/${id}`, goal, { headers: this.headers() });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/${id}`, { headers: this.headers() });
  }

  addContribution(id: string, amount: number, note: string): Observable<SavingsGoal> {
    return this.http.post<SavingsGoal>(
      `${this.api}/${id}/contributions`,
      { amount, note },
      { headers: this.headers() }
    );
  }

  removeContribution(goalId: string, contributionId: string): Observable<SavingsGoal> {
    return this.http.delete<SavingsGoal>(
      `${this.api}/${goalId}/contributions/${contributionId}`,
      { headers: this.headers() }
    );
  }
}
