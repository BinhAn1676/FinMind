import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Category {
  id?: string;
  name: string;
  userId?: string;
  createdAt?: string;
  isDefault?: boolean;
}

@Injectable({ providedIn: 'root' })
export class CategoryService {
  private apiPrefix = '/finances/api/v1';

  constructor(private http: HttpClient) {}

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    let headers = new HttpHeaders();
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    return headers;
  }

  getAllCategories(userId: string): Observable<Category[]> {
    const params = new HttpParams().set('userId', userId);
    return this.http.get<Category[]>(`${environment.rooturl}${this.apiPrefix}/categories`, {
      headers: this.getHeaders(),
      params
    });
  }

  addCategory(userId: string, name: string): Observable<Category> {
    const params = new HttpParams()
      .set('userId', userId)
      .set('name', name);
    return this.http.post<Category>(`${environment.rooturl}${this.apiPrefix}/categories`, {}, {
      headers: this.getHeaders(),
      params
    });
  }

  deleteCategory(userId: string, name: string): Observable<any> {
    const params = new HttpParams()
      .set('userId', userId)
      .set('name', name);
    return this.http.delete(`${environment.rooturl}${this.apiPrefix}/categories`, {
      headers: this.getHeaders(),
      params
    });
  }

  initializeDefaultCategories(userId: string): Observable<any> {
    const params = new HttpParams().set('userId', userId);
    return this.http.post(`${environment.rooturl}${this.apiPrefix}/categories/initialize`, {}, {
      headers: this.getHeaders(),
      params
    });
  }
}
