import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User } from "src/app/model/user.model";
import { AppConstants } from 'src/app/constants/app.constants';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class UserService {

  constructor(private http: HttpClient) {
    
  }

  getUserInfo(): Observable<User> {
    // Get the authorization token from session storage
    const authToken = window.sessionStorage.getItem('Authorization');
    console.log('Auth token:', authToken);
    
    const headers = new HttpHeaders({
      'Authorization': authToken || '',
      'Content-Type': 'application/json'
    });
    
    console.log('Making request to:', environment.rooturl + AppConstants.USER_INFO_API_URL);
    return this.http.get<User>(environment.rooturl + AppConstants.USER_INFO_API_URL, { headers });
  }

  getUserById(id: number): Observable<User> {
    const authToken = window.sessionStorage.getItem('Authorization');
    
    const headers = new HttpHeaders({
      'Authorization': authToken || '',
      'Content-Type': 'application/json'
    });
    
    return this.http.get<User>(`${environment.rooturl}/users/api/v1/users/${id}`, { headers });
  }

  getUserByUsername(username: string): Observable<User> {
    const authToken = window.sessionStorage.getItem('Authorization');
    
    const headers = new HttpHeaders({
      'Authorization': authToken || '',
      'Content-Type': 'application/json'
    });
    
    return this.http.get<User>(`${environment.rooturl}/users/api/v1/users/username/${username}`, { headers });
  }

  updateUser(user: User): Observable<User> {
    const authToken = window.sessionStorage.getItem('Authorization');
    
    const headers = new HttpHeaders({
      'Authorization': authToken || '',
      'Content-Type': 'application/json'
    });
    
    return this.http.post<User>(`${environment.rooturl}/users/api/v1/users/${user.id}`, user, { headers });
  }

  updateBankToken(userId: number, bankToken: string): Observable<any> {
    const authToken = window.sessionStorage.getItem('Authorization');
    
    const headers = new HttpHeaders({
      'Authorization': authToken || '',
      'Content-Type': 'application/json'
    });
    
    const requestBody = {
      bankToken: bankToken
    };
    
    return this.http.put<any>(`${environment.rooturl}/users/api/v1/users/${userId}/bank-token`, requestBody, { headers });
  }

}
