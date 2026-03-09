import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface CronValidationResponse {
  valid: boolean;
  message: string;
  description: string;
}

export interface CronPreset {
  name: string;
  expression: string;
  description: string;
}

export interface NextExecutionsResponse {
  nextExecutions: string[]; // ISO-8601 formatted timestamps
}

@Injectable({
  providedIn: 'root'
})
export class CronService {
  private baseUrl = `${environment.rooturl}/finances/api/v1/cron`;

  constructor(private http: HttpClient) {}

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    return new HttpHeaders({
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    });
  }

  /**
   * Validate a cron expression
   * @param expression The cron expression to validate
   * @returns Validation response with valid flag, message, and description
   */
  validate(expression: string): Observable<CronValidationResponse> {
    const params = new HttpParams().set('expression', expression);
    return this.http.get<CronValidationResponse>(`${this.baseUrl}/validate`, {
      headers: this.getHeaders(),
      params: params
    });
  }

  /**
   * Get human-readable description of a cron expression
   * @param expression The cron expression
   * @returns Validation response with description
   */
  describe(expression: string): Observable<CronValidationResponse> {
    const params = new HttpParams().set('expression', expression);
    return this.http.get<CronValidationResponse>(`${this.baseUrl}/describe`, {
      headers: this.getHeaders(),
      params: params
    });
  }

  /**
   * Get list of common cron presets for financial reports
   * @returns List of preset cron expressions
   */
  getPresets(): Observable<CronPreset[]> {
    return this.http.get<CronPreset[]>(`${this.baseUrl}/presets`, {
      headers: this.getHeaders()
    });
  }

  /**
   * Calculate next N execution times for a cron expression
   * @param expression The cron expression
   * @param count Number of next executions to calculate (default: 5)
   * @returns List of next execution times in ISO-8601 format
   */
  getNextExecutions(expression: string, count: number = 5): Observable<NextExecutionsResponse> {
    const params = new HttpParams()
      .set('expression', expression)
      .set('count', count.toString());
    return this.http.get<NextExecutionsResponse>(`${this.baseUrl}/next-executions`, {
      headers: this.getHeaders(),
      params: params
    });
  }
}
