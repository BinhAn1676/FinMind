import { Injectable } from '@angular/core';
import { BehaviorSubject, Subject } from 'rxjs';
import { environment } from '../../environments/environment';
import { KeycloakService } from 'keycloak-angular';
import * as SockJS from 'sockjs-client';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';

export interface Notification {
  id: string;
  userId: number;
  type: string;
  title: string;
  message: string;
  read: boolean;
  source: string;
  metadata?: any;
  createdAt?: string;
  updatedAt?: string;
}

export interface NotificationPage {
  content: Notification[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private baseUrl = environment.notificationServiceUrl || environment.rooturl;
  private stompClient: Client | null = null;
  private connected = false;
  private userId: number | null = null;
  private notificationSubscription?: StompSubscription;
  private countSubscription?: StompSubscription;
  private latestSubscription?: StompSubscription;
  private pendingLatestRequest: { page: number; size: number } | null = null;

  private notificationsSubject = new BehaviorSubject<Notification[]>([]);
  public notifications$ = this.notificationsSubject.asObservable();

  private unreadCountSubject = new BehaviorSubject<number>(0);
  public unreadCount$ = this.unreadCountSubject.asObservable();

  private newNotificationSubject = new Subject<Notification>();
  public newNotification$ = this.newNotificationSubject.asObservable();

  private latestPageSubject = new BehaviorSubject<NotificationPage | null>(null);
  public latestPage$ = this.latestPageSubject.asObservable();

  constructor(
    private keycloak: KeycloakService
  ) {}

  async initializeWebSocket(userId: number): Promise<void> {
    if (this.connected && this.userId === userId && this.stompClient?.connected) {
      return;
    }

    this.userId = userId;
    await this.disconnect();

    try {
      const token = await this.keycloak.getToken();
      const endpointUrl = `${this.baseUrl}/ws`;

      this.stompClient = new Client({
        webSocketFactory: () => new SockJS(endpointUrl),
        connectHeaders: { Authorization: `Bearer ${token}` },
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        debug: (msg: string) => console.debug('[Notification WS]', msg)
      });

      this.stompClient.onConnect = () => {
        this.connected = true;
        console.log('✅ WebSocket connected for userId:', userId);
        this.subscribeToUserChannels(userId);
        this.requestUnreadCount(userId);
        if (this.pendingLatestRequest) {
          const { page, size } = this.pendingLatestRequest;
          this.pendingLatestRequest = null;
          this.requestLatestNotifications(page, size);
        }
      };

      this.stompClient.onStompError = (frame) => {
        console.error('WebSocket STOMP error:', frame.headers['message'], frame.body);
      };

      this.stompClient.onWebSocketClose = () => {
        this.connected = false;
        console.warn('WebSocket disconnected');
      };

      this.stompClient.activate();
    } catch (error) {
      console.error('Failed to initialize WebSocket:', error);
    }
  }

  private subscribeToUserChannels(userId: number): void {
    if (!this.stompClient || !this.stompClient.connected) {
      return;
    }

    this.notificationSubscription?.unsubscribe();
    this.countSubscription?.unsubscribe();
    this.latestSubscription?.unsubscribe();

    console.log('[Notification WS] Subscribing to:', `/user/${userId}/queue/notifications`);
    this.notificationSubscription = this.stompClient.subscribe(
      `/user/${userId}/queue/notifications`,
      (message: IMessage) => {
        try {
          const notification: Notification = JSON.parse(message.body);
          console.log('[Notification WS] ✅ PUSH NOTIFICATION RECEIVED:', notification);
          this.handleNewNotification(notification);
        } catch (error) {
          console.error('Failed to parse notification message', error);
        }
      }
    );

    console.log('[Notification WS] Subscribing to count:', `/user/${userId}/queue/notifications-count`);
    this.countSubscription = this.stompClient.subscribe(
      `/user/${userId}/queue/notifications-count`,
      (message: IMessage) => {
        const count = parseInt(message.body, 10);
        console.log('[Notification WS] ✅ COUNT UPDATE RECEIVED:', count, 'raw:', message.body);
        if (!Number.isNaN(count)) {
          this.unreadCountSubject.next(count);
        }
      }
    );

    this.latestSubscription = this.stompClient.subscribe(
      `/user/${userId}/queue/notifications-all`,
      (message: IMessage) => {
        try {
          const page: NotificationPage = JSON.parse(message.body);
          console.debug('[Notification WS] notifications page received', page);
          this.latestPageSubject.next(page);
          this.notificationsSubject.next(page.content || []);
        } catch (error) {
          console.error('Failed to parse latest notifications message', error);
        }
      }
    );
  }

  private requestUnreadCount(userId: number): void {
    if (!this.stompClient?.connected) {
      return;
    }
    this.stompClient.publish({
      destination: '/app/notifications/count',
      body: JSON.stringify({ userId })
    });
  }

  private handleNewNotification(notification: Notification): void {
    const currentNotifications = this.notificationsSubject.value;
    this.notificationsSubject.next([notification, ...currentNotifications]);
    this.newNotificationSubject.next(notification);
    if (notification.userId) {
      this.requestUnreadCount(notification.userId);
    }
  }

  async disconnect(): Promise<void> {
    this.notificationSubscription?.unsubscribe();
    this.countSubscription?.unsubscribe();
    this.latestSubscription?.unsubscribe();
    this.notificationSubscription = undefined;
    this.countSubscription = undefined;
    this.latestSubscription = undefined;

    if (this.stompClient) {
      await this.stompClient.deactivate();
      this.stompClient = null;
    }
    this.connected = false;
  }

  requestLatestNotifications(page: number = 0, size: number = 10): boolean {
    if (!this.userId) {
      return false;
    }
    if (!this.stompClient?.connected) {
      this.pendingLatestRequest = { page, size };
      return false;
    }
    this.stompClient.publish({
      destination: '/app/notifications/all',
      body: JSON.stringify({ userId: this.userId, page, size })
    });
    return true;
  }

  markNotificationAsRead(notificationId: string): void {
    if (!this.userId || !this.stompClient?.connected) {
      return;
    }
    this.stompClient.publish({
      destination: '/app/notifications/mark-read',
      body: JSON.stringify({ userId: this.userId, notificationId })
    });
  }

  markAllNotificationsAsRead(): void {
    if (!this.userId || !this.stompClient?.connected) {
      return;
    }
    this.stompClient.publish({
      destination: '/app/notifications/mark-all-read',
      body: JSON.stringify({ userId: this.userId })
    });
  }
}

