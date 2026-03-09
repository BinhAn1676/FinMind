import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Borrower {
  fullName?: string;
  cccd?: string;
  phoneNumber?: string;
  address?: string;
  additionalInfo?: string;
}

export interface LoanPayment {
  id?: string;
  paymentDate?: string;
  amount?: number;
  principalAmount?: number;
  interestAmount?: number;
  notes?: string;
  createdAt?: string;
}

export interface Loan {
  id?: string;
  userId?: string;
  loanType?: 'CHO_VAY' | 'VAY';
  status?: 'PAID' | 'OUTDATE' | 'ON_GOING';
  borrower?: Borrower; // Keep for backward compatibility
  borrowers?: Borrower[]; // New: support multiple borrowers
  principalAmount?: number;
  interestRate?: number;
  interestAmount?: number;
  startDate?: string;
  termDays?: number;
  endDate?: string;
  dailyPaymentAmount?: number;
  payments?: LoanPayment[];
  totalPaid?: number;
  cumulativePrincipalCollected?: number;
  cumulativeInterestCollected?: number;
  cumulativeTotalCollected?: number;
  outstandingDebt?: number;
  amountDue?: number;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface LoanSummary {
  totalPrincipal: number;
  totalInterest: number;
  totalCollected: number;
  totalAfterInterest: number;
  loanCount: number;
}

export interface LoanFilterParams {
  userId?: string;
  loanType?: 'CHO_VAY' | 'VAY' | '';
  searchTerm?: string;
  startDate?: string;
  endDate?: string;
  statuses?: string[];
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class LoanService {
  private apiPrefix = '/finances/api/v1';

  constructor(private http: HttpClient) { }

  private getHeaders(): HttpHeaders {
    const token = window.sessionStorage.getItem('Authorization');
    return new HttpHeaders({
      'Authorization': token || '',
      'Content-Type': 'application/json'
    });
  }

  getLoanById(id: string): Observable<Loan> {
    return this.http.get<Loan>(`${environment.rooturl}${this.apiPrefix}/loans/${id}`, {
      headers: this.getHeaders()
    });
  }

  createLoan(loan: Loan): Observable<Loan> {
    return this.http.post<Loan>(`${environment.rooturl}${this.apiPrefix}/loans`, loan, {
      headers: this.getHeaders()
    });
  }

  updateLoan(id: string, loan: Loan): Observable<Loan> {
    return this.http.put<Loan>(`${environment.rooturl}${this.apiPrefix}/loans/${id}`, loan, {
      headers: this.getHeaders()
    });
  }

  deleteLoan(id: string): Observable<void> {
    return this.http.delete<void>(`${environment.rooturl}${this.apiPrefix}/loans/${id}`, {
      headers: this.getHeaders()
    });
  }

  filterLoans(params: LoanFilterParams): Observable<any> {
    let httpParams = new HttpParams();
    
    if (params.userId) httpParams = httpParams.set('userId', params.userId);
    if (params.loanType) httpParams = httpParams.set('loanType', params.loanType);
    if (params.searchTerm) httpParams = httpParams.set('searchTerm', params.searchTerm);
    if (params.startDate) httpParams = httpParams.set('startDate', params.startDate);
    if (params.endDate) httpParams = httpParams.set('endDate', params.endDate);
    if (params.statuses && params.statuses.length > 0) {
      httpParams = httpParams.set('statuses', params.statuses.join(','));
    }
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page.toString());
    if (params.size !== undefined) httpParams = httpParams.set('size', params.size.toString());

    return this.http.get(`${environment.rooturl}${this.apiPrefix}/loans`, {
      headers: this.getHeaders(),
      params: httpParams
    });
  }

  getSummary(userId: string, loanType?: 'CHO_VAY' | 'VAY'): Observable<LoanSummary> {
    let params = new HttpParams().set('userId', userId);
    if (loanType) params = params.set('loanType', loanType);
    
    return this.http.get<LoanSummary>(
      `${environment.rooturl}${this.apiPrefix}/loans/summary`,
      { headers: this.getHeaders(), params }
    );
  }

  addPayment(loanId: string, payment: LoanPayment): Observable<Loan> {
    return this.http.post<Loan>(
      `${environment.rooturl}${this.apiPrefix}/loans/${loanId}/payments`,
      payment,
      { headers: this.getHeaders() }
    );
  }

  updatePayment(loanId: string, paymentId: string, payment: LoanPayment): Observable<Loan> {
    return this.http.put<Loan>(
      `${environment.rooturl}${this.apiPrefix}/loans/${loanId}/payments/${paymentId}`,
      payment,
      { headers: this.getHeaders() }
    );
  }

  deletePayment(loanId: string, paymentId: string): Observable<Loan> {
    return this.http.delete<Loan>(
      `${environment.rooturl}${this.apiPrefix}/loans/${loanId}/payments/${paymentId}`,
      { headers: this.getHeaders() }
    );
  }
}

