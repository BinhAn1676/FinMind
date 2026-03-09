import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpHeaders } from '@angular/common/http';
import { Observable, from, switchMap } from 'rxjs';
import { environment } from '../../environments/environment';
import { KeycloakService } from 'keycloak-angular';

// Response interfaces matching backend DTOs
export interface HealthScore {
  score: number;
  grade: string;
  status: string;
  message: string;
  strengths: string[];
  concerns: string[];
  aiNarrative?: string;     // AI-generated narrative assessment (from HybridEngine)
  aiEnhanced?: boolean;     // Whether AI reasoning was applied
}

export interface SpendingCategory {
  name: string;
  actualPercent: number;
  idealPercent: number;
  amount: number;
  idealAmount: number;
  status: string;
}

export interface SpendingStructure {
  categories: SpendingCategory[];
  totalIncome: number;
  totalExpense: number;
  overallStatus: string;
  recommendation: string;
}

export interface AnomalyAlert {
  transactionId: string;
  description: string;
  amount: number;
  date: string;
  severity: string;
  reason: string;
  recommendation: string;
}

export interface SpendingPattern {
  type: string;
  pattern: string;
  description: string;
  recommendations: string[];
}

export interface ForecastPoint {
  period: string;
  balance: number;
  expense: number;
  income: number;
}

export interface CashFlowWarning {
  severity: string;
  message: string;
  projectedDeficit: number;
  recommendation: string;
}

export interface BudgetForecast {
  forecastPeriod: string;
  projectedBalance: number;
  projectedExpense: number;
  trend: string;
  confidence: number;
  timeline: ForecastPoint[];
  warning: CashFlowWarning;
}

export interface DisciplineRecommendation {
  category: string;
  currentAmount: number;
  targetAmount: number;
  savingsPotential: number;
  priority: number;
}

export interface CompleteDashboard {
  healthScore: HealthScore;
  spendingStructure: SpendingStructure;
  anomalies: AnomalyAlert[];
  patterns: SpendingPattern[];
  budgetForecast: BudgetForecast;
  recommendations: DisciplineRecommendation[];
  generatedAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class AIAnalyticsService {
  // Use Gateway with /ai prefix for service routing
  private readonly API_URL = `${environment.rooturl}/ai/api/analytics`;

  constructor(
    private http: HttpClient,
    private keycloakService: KeycloakService
  ) {}

  /**
   * Get HTTP headers with Bearer token
   */
  private async getAuthHeaders(): Promise<HttpHeaders> {
    const token = await this.keycloakService.getToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });
  }

  /**
   * Get financial health score
   * @param userId User ID
   * @param year Optional year (defaults to current year)
   * @param month Optional month (defaults to current month)
   */
  getHealthScore(userId: string, year?: number, month?: number): Observable<HealthScore> {
    return from(this.getAuthHeaders()).pipe(
      switchMap(headers => {
        let params = new HttpParams().set('userId', userId);
        if (year) params = params.set('year', year.toString());
        if (month) params = params.set('month', month.toString());
        return this.http.get<HealthScore>(`${this.API_URL}/health-score`, { params, headers });
      })
    );
  }

  /**
   * Get spending structure for chart
   * @param userId User ID
   * @param year Optional year (defaults to current year)
   * @param month Optional month (defaults to current month)
   */
  getSpendingStructure(userId: string, year?: number, month?: number): Observable<SpendingStructure> {
    return from(this.getAuthHeaders()).pipe(
      switchMap(headers => {
        let params = new HttpParams().set('userId', userId);
        if (year) params = params.set('year', year.toString());
        if (month) params = params.set('month', month.toString());
        return this.http.get<SpendingStructure>(`${this.API_URL}/spending-structure`, { params, headers });
      })
    );
  }

  /**
   * Get anomaly alerts
   * @param userId User ID
   * @param year Optional year (defaults to current year)
   * @param month Optional month (defaults to current month)
   */
  getAnomalies(userId: string, year?: number, month?: number): Observable<AnomalyAlert[]> {
    return from(this.getAuthHeaders()).pipe(
      switchMap(headers => {
        let params = new HttpParams().set('userId', userId);
        if (year) params = params.set('year', year.toString());
        if (month) params = params.set('month', month.toString());
        return this.http.get<AnomalyAlert[]>(`${this.API_URL}/anomalies`, { params, headers });
      })
    );
  }

  /**
   * Get spending patterns
   */
  getSpendingPatterns(userId: string): Observable<SpendingPattern[]> {
    return from(this.getAuthHeaders()).pipe(
      switchMap(headers => {
        const params = new HttpParams().set('userId', userId);
        return this.http.get<SpendingPattern[]>(`${this.API_URL}/patterns`, { params, headers });
      })
    );
  }

  /**
   * Get budget forecast with timeline
   */
  getBudgetForecast(userId: string): Observable<BudgetForecast> {
    return from(this.getAuthHeaders()).pipe(
      switchMap(headers => {
        const params = new HttpParams().set('userId', userId);
        return this.http.get<BudgetForecast>(`${this.API_URL}/forecast`, { params, headers });
      })
    );
  }

  /**
   * Get discipline recommendations
   */
  getDisciplineRecommendations(userId: string): Observable<DisciplineRecommendation[]> {
    return from(this.getAuthHeaders()).pipe(
      switchMap(headers => {
        const params = new HttpParams().set('userId', userId);
        return this.http.get<DisciplineRecommendation[]>(`${this.API_URL}/recommendations`, { params, headers });
      })
    );
  }

  /**
   * Get complete dashboard (all data in one call)
   */
  getCompleteDashboard(userId: string): Observable<CompleteDashboard> {
    return from(this.getAuthHeaders()).pipe(
      switchMap(headers => {
        const params = new HttpParams().set('userId', userId);
        return this.http.get<CompleteDashboard>(`${this.API_URL}/dashboard`, { params, headers });
      })
    );
  }

  /**
   * Export financial report as Excel or PDF file (blob download).
   * Sends the already-loaded dashboard data — no recalculation on backend.
   * @param dashboard  Dashboard data already displayed on screen
   * @param format     'excel' → .xlsx  |  'pdf' → .pdf
   * @param month      Month number (1-12)
   * @param year       Full year (e.g. 2026)
   */
  exportReport(dashboard: CompleteDashboard, format: 'excel' | 'pdf', month: number, year: number): Observable<Blob> {
    return from(this.getAuthHeaders()).pipe(
      switchMap(headers => {
        const params = new HttpParams()
          .set('format', format)
          .set('month', month.toString())
          .set('year', year.toString());
        return this.http.post(`${this.API_URL}/report/export`, dashboard, {
          params,
          headers,
          responseType: 'blob'
        });
      })
    );
  }
}
