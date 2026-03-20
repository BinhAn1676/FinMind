import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface PortfolioCoin {
  id?: string;
  userId?: string;
  coinId: string;
  symbol: string;
  name: string;
  addedAt?: string;
}

@Injectable({ providedIn: 'root' })
export class PortfolioCoinService {
  private readonly api = `${environment.rooturl}/finances/api/v1/portfolio-coins`;

  constructor(private http: HttpClient) {}

  private headers(): HttpHeaders {
    const token = window.sessionStorage.getItem('Authorization') || '';
    return new HttpHeaders({ Authorization: token });
  }

  getByUser(userId: string): Observable<PortfolioCoin[]> {
    const params = new HttpParams().set('userId', userId);
    return this.http.get<PortfolioCoin[]>(this.api, { headers: this.headers(), params });
  }

  addCoin(coin: PortfolioCoin): Observable<PortfolioCoin> {
    return this.http.post<PortfolioCoin>(this.api, coin, { headers: this.headers() });
  }

  removeCoin(userId: string, coinId: string): Observable<void> {
    const params = new HttpParams().set('userId', userId).set('coinId', coinId);
    return this.http.delete<void>(this.api, { headers: this.headers(), params });
  }
}
