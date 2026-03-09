import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface GroupSummary {
  id: number;
  name: string;
  description?: string;
  avatarFileId?: string;
  memberCount?: number;
}

export interface GroupDetail extends GroupSummary {
  ownerUserId: number;
  createdAt: string;
  updatedAt: string;
  members?: GroupMember[];
}

export interface GroupMember {
  userId: number;
  fullName?: string;
  email?: string;
  phone?: string;
  avatar?: string;
  role: string;
  joinedAt: string;
}

export interface GroupAccount {
  id: number;
  accountId: number;
  bankAccountId?: string;
  ownerUserId: number;
  label?: string;
  accountNumber?: string;
  bankBrandName?: string;
  accumulated?: string;
  currency?: string;
  linkedAt?: string;
}

export interface GroupCreatePayload {
  name: string;
  description?: string;
  avatarFileId?: string;
}

export interface GroupUpdatePayload {
  name?: string;
  description?: string;
  avatarFileId?: string;
}

export interface GroupInvite {
  id: number;
  groupId: number;
  inviterUserId: number;
  inviteeUserId: number;
  inviteeFullName?: string;
  inviteeEmail?: string;
  inviteePhone?: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface UserSearchResult {
  id: number;
  username: string;
  fullName?: string;
  email?: string;
  phone?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface GroupActivity {
  id: number;
  groupId: number;
  actorUserId: number;
  actorName?: string;
  type: string;
  message?: string;
  metadata?: string;
  createdAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class GroupService {
  private readonly prefix = '/users/api/v1';

  constructor(private http: HttpClient) {}

  search(query = '', page = 0, size = 20): Observable<PageResponse<GroupSummary>> {
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    if (query) {
      params = params.set('q', query);
    }
    return this.http.get<PageResponse<GroupSummary>>(
      `${environment.rooturl}${this.prefix}/groups`,
      { params }
    );
  }

  create(payload: GroupCreatePayload): Observable<GroupSummary> {
    return this.http.post<GroupSummary>(`${environment.rooturl}${this.prefix}/groups`, payload);
  }

  inviteMembers(groupId: number, userIds: number[]): Observable<GroupInvite[]> {
    return this.http.post<GroupInvite[]>(
      `${environment.rooturl}${this.prefix}/groups/${groupId}/invites`,
      { inviteeUserIds: userIds }
    );
  }

  respondToInvite(groupId: number, inviteId: number, accept: boolean): Observable<GroupInvite> {
    const action = accept ? 'accept' : 'reject';
    return this.http.post<GroupInvite>(
      `${environment.rooturl}${this.prefix}/groups/${groupId}/invites/${inviteId}/${action}`,
      {}
    );
  }

  getInvites(
    groupId: number,
    options: { status?: string; page?: number; size?: number } = {}
  ): Observable<PageResponse<GroupInvite>> {
    let params = new HttpParams();
    if (options.status) {
      params = params.set('status', options.status);
    }
    if (options.page !== undefined) {
      params = params.set('page', options.page.toString());
    }
    if (options.size !== undefined) {
      params = params.set('size', options.size.toString());
    }
    return this.http.get<PageResponse<GroupInvite>>(
      `${environment.rooturl}${this.prefix}/groups/${groupId}/invites`,
      { params }
    );
  }

  cancelInvite(groupId: number, inviteId: number): Observable<GroupInvite> {
    return this.http.post<GroupInvite>(
      `${environment.rooturl}${this.prefix}/groups/${groupId}/invites/${inviteId}/cancel`,
      {}
    );
  }

  getById(id: number): Observable<GroupDetail> {
    return this.http.get<GroupDetail>(`${environment.rooturl}${this.prefix}/groups/${id}`);
  }

  getMembers(
    groupId: number,
    options: { page?: number; size?: number } = {}
  ): Observable<PageResponse<GroupMember>> {
    let params = new HttpParams();
    if (options.page !== undefined) {
      params = params.set('page', options.page.toString());
    }
    if (options.size !== undefined) {
      params = params.set('size', options.size.toString());
    }
    return this.http.get<PageResponse<GroupMember>>(
      `${environment.rooturl}${this.prefix}/groups/${groupId}/members`,
      { params }
    );
  }

  updateMemberRole(groupId: number, userId: number, role: string): Observable<GroupMember> {
    return this.http.put<GroupMember>(
      `${environment.rooturl}${this.prefix}/groups/${groupId}/members/${userId}/role`,
      { role }
    );
  }

  removeMember(groupId: number, userId: number): Observable<void> {
    return this.http.delete<void>(
      `${environment.rooturl}${this.prefix}/groups/${groupId}/members/${userId}`
    );
  }

  update(id: number, payload: GroupUpdatePayload): Observable<GroupDetail> {
    return this.http.put<GroupDetail>(`${environment.rooturl}${this.prefix}/groups/${id}`, payload);
  }

  getGroupAccounts(groupId: number, search = '', page = 0, size = 20): Observable<PageResponse<GroupAccount>> {
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    if (search) {
      params = params.set('q', search);
    }
    return this.http.get<PageResponse<GroupAccount>>(
      `${environment.rooturl}${this.prefix}/groups/${groupId}/accounts`,
      { params }
    );
  }

  linkGroupAccount(groupId: number, accountId: number): Observable<GroupAccount> {
    return this.http.post<GroupAccount>(
      `${environment.rooturl}${this.prefix}/groups/${groupId}/accounts`,
      { accountId }
    );
  }

  unlinkGroupAccount(groupId: number, accountId: number): Observable<void> {
    return this.http.delete<void>(`${environment.rooturl}${this.prefix}/groups/${groupId}/accounts/${accountId}`);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.rooturl}${this.prefix}/groups/${id}`);
  }

  leaveGroup(id: number): Observable<void> {
    return this.http.post<void>(`${environment.rooturl}${this.prefix}/groups/${id}/leave`, {});
  }

  getActivities(
    groupId: number,
    options: {
      query?: string;
      type?: string;
      from?: string;
      to?: string;
      page?: number;
      size?: number;
    } = {}
  ): Observable<PageResponse<GroupActivity>> {
    let params = new HttpParams();
    if (options.query) {
      params = params.set('q', options.query);
    }
    if (options.type) {
      params = params.set('type', options.type);
    }
    if (options.from) {
      params = params.set('from', options.from);
    }
    if (options.to) {
      params = params.set('to', options.to);
    }
    if (options.page !== undefined) {
      params = params.set('page', options.page.toString());
    }
    if (options.size !== undefined) {
      params = params.set('size', options.size.toString());
    }

    return this.http.get<PageResponse<GroupActivity>>(
      `${environment.rooturl}${this.prefix}/groups/${groupId}/activities`,
      { params }
    );
  }

  searchUsers(text: string, page = 0, size = 10): Observable<PageResponse<UserSearchResult>> {
    const params = new HttpParams()
      .set('textSearch', text)
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<PageResponse<UserSearchResult>>(
      `${environment.rooturl}${this.prefix}/users/search/contact`,
      { params }
    );
  }
}

