import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { environment } from '../../environments/environment';

export interface FileUploadResponse {
  id: string;
  userId: string;
  purpose: string;
  fileName: string;
  originalFileName: string;
  fileType: string;
  fileSize: number;
  fileUrl: string;
  liveUrl: string;
  description: string;
  createdAt: string;
  updatedAt: string;
}

export interface LiveUrlResponse {
  liveUrl: string;
}

@Injectable({
  providedIn: 'root'
})
export class FileService {

  constructor(private http: HttpClient) { }

  uploadAvatar(file: File, userId: string): Observable<FileUploadResponse> {
    const authToken = window.sessionStorage.getItem('Authorization');
    
    const formData = new FormData();
    formData.append('file', file);
    formData.append('userId', userId);
    formData.append('purpose', 'avatar');
    
    const headers = new HttpHeaders({
      'Authorization': authToken || ''
      // Don't set Content-Type for FormData, let browser set it
    });
    
    return this.http.post<FileUploadResponse>(
      `${environment.rooturl}/files/api/v1/files/upload`, 
      formData, 
      { headers }
    );
  }

  uploadGroupAvatar(file: File, ownerId: string): Observable<FileUploadResponse> {
    const authToken = window.sessionStorage.getItem('Authorization');

    const formData = new FormData();
    formData.append('file', file);
    formData.append('userId', ownerId);
    formData.append('purpose', 'GROUP_AVATAR');

    const headers = new HttpHeaders({
      'Authorization': authToken || ''
    });

    return this.http.post<FileUploadResponse>(
      `${environment.rooturl}/files/api/v1/files/upload`,
      formData,
      { headers }
    );
  }

  getLiveUrl(fileId: string, expiryTimeInSeconds: number = 3600): Observable<LiveUrlResponse> {
    // Static asset paths (e.g. /assets/avatar/finbot.png) or full URLs — return as-is, no API call
    if (!fileId || fileId.startsWith('/') || fileId.startsWith('http')) {
      return of({ liveUrl: fileId });
    }

    const authToken = window.sessionStorage.getItem('Authorization');

    const headers = new HttpHeaders({
      'Authorization': authToken || '',
      'Content-Type': 'application/json'
    });

    return this.http.get<LiveUrlResponse>(
      `${environment.rooturl}/files/api/v1/files/${fileId}/live-url?expiry=${expiryTimeInSeconds}`,
      { headers }
    );
  }

  getLiveUrlByUserIdAndPurpose(userId: string, purpose: string, expiryTimeInSeconds: number = 3600): Observable<LiveUrlResponse> {
    const authToken = window.sessionStorage.getItem('Authorization');
    
    const headers = new HttpHeaders({
      'Authorization': authToken || '',
      'Content-Type': 'application/json'
    });
    
    return this.http.get<LiveUrlResponse>(
      `${environment.rooturl}/files/api/v1/files/user/${userId}/purpose/${purpose}/live-url?expiry=${expiryTimeInSeconds}`, 
      { headers }
    );
  }

  getLiveUrls(ids: string[], expiryTimeInSeconds: number = 3600): Observable<Record<string, string>> {
    if (!ids || !ids.length) {
      return of({});
    }

    // Separate static paths (return as-is) from real file IDs (need API call)
    const staticMap: Record<string, string> = {};
    const realIds = ids.filter(id => {
      if (!id || id.startsWith('/') || id.startsWith('http')) {
        staticMap[id] = id;
        return false;
      }
      return true;
    });

    if (realIds.length === 0) {
      return of(staticMap);
    }

    const authToken = window.sessionStorage.getItem('Authorization');
    const headers = new HttpHeaders({
      'Authorization': authToken || '',
      'Content-Type': 'application/json'
    });
    return this.http.post<Record<string, string>>(
      `${environment.rooturl}/files/api/v1/files/live-url/bulk`,
      { ids: realIds, expiry: expiryTimeInSeconds },
      { headers }
    );
  }

  uploadChatFile(file: File, userId: string): Observable<FileUploadResponse> {
    const authToken = window.sessionStorage.getItem('Authorization');
    
    const formData = new FormData();
    formData.append('file', file);
    formData.append('userId', userId);
    formData.append('purpose', 'CHAT_ATTACHMENT');
    
    const headers = new HttpHeaders({
      'Authorization': authToken || ''
    });
    
    return this.http.post<FileUploadResponse>(
      `${environment.rooturl}/files/api/v1/files/upload`, 
      formData, 
      { headers }
    );
  }

  downloadFile(fileId: string): Observable<Blob> {
    const authToken = window.sessionStorage.getItem('Authorization');
    
    const headers = new HttpHeaders({
      'Authorization': authToken || ''
    });
    
    return this.http.get(
      `${environment.rooturl}/files/api/v1/files/${fileId}/download`,
      { headers, responseType: 'blob' }
    );
  }
}
