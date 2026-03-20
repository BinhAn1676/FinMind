import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type AssetType = 'CRYPTO' | 'GOLD' | 'VN_STOCK';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface InvestmentLot {
  id?: string;
  userId?: string;
  assetType: AssetType;
  symbol: string;
  name: string;
  buyDate: string;       // ISO date string (YYYY-MM-DD)
  quantity: number;
  buyPriceVnd: number;   // always VND/unit
  transactionType?: 'BUY' | 'SELL';  // default 'BUY'
  fees?: number;          // optional fees in VND
  note?: string;
  createdAt?: string;
  updatedAt?: string;
}

@Injectable({ providedIn: 'root' })
export class InvestmentLotService {
  private readonly api = `${environment.rooturl}/finances/api/v1/investment-lots`;

  constructor(private http: HttpClient) {}

  private headers(): HttpHeaders {
    const token = window.sessionStorage.getItem('Authorization') || '';
    return new HttpHeaders({ Authorization: token });
  }

  /** Load ALL lots for charts/stats aggregation */
  getByUser(userId: string): Observable<InvestmentLot[]> {
    const params = new HttpParams().set('userId', userId);
    return this.http.get<InvestmentLot[]>(this.api, { headers: this.headers(), params });
  }

  /** Paginated + filtered transaction history */
  getPaged(userId: string, symbol: string, dateFrom: string, dateTo: string, page: number, size: number): Observable<PageResponse<InvestmentLot>> {
    let params = new HttpParams().set('userId', userId).set('page', page).set('size', size);
    if (symbol)   params = params.set('symbol', symbol);
    if (dateFrom) params = params.set('dateFrom', dateFrom);
    if (dateTo)   params = params.set('dateTo', dateTo);
    return this.http.get<PageResponse<InvestmentLot>>(`${this.api}/paged`, { headers: this.headers(), params });
  }

  create(lot: InvestmentLot): Observable<InvestmentLot> {
    return this.http.post<InvestmentLot>(this.api, lot, { headers: this.headers() });
  }

  update(id: string, lot: InvestmentLot): Observable<InvestmentLot> {
    return this.http.put<InvestmentLot>(`${this.api}/${id}`, lot, { headers: this.headers() });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/${id}`, { headers: this.headers() });
  }

  deleteBySymbol(userId: string, symbol: string): Observable<void> {
    const params = new HttpParams().set('userId', userId).set('symbol', symbol);
    return this.http.delete<void>(`${this.api}/by-symbol`, { headers: this.headers(), params });
  }
}
