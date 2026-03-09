import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable, Subject, from, switchMap } from 'rxjs';
import { environment } from '../../environments/environment';
import { KeycloakService } from 'keycloak-angular';
import * as SockJS from 'sockjs-client';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import {
  ChatRoom,
  ChatMessage,
  SendMessageRequest,
  CreateChatRoomRequest,
  ChatPageResponse,
  TypingIndicator,
  ReadReceipt,
  OnlineStatus,
  ChatBoxState
} from '../model/chat.model';

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  // Direct connection to ChatService for WebSocket only
  private readonly chatServiceUrl = environment.chatServiceUrl || 'http://localhost:8086';
  // Gateway URL for REST API calls (goes through gateway with /chat prefix)
  private readonly gatewayUrl = environment.rooturl;
  private readonly chatApiPrefix = '/chat/api/v1/chat';
  private stompClient: Client | null = null;
  private connected = false;
  private userId: number | null = null;
  private roomSubscriptions: Map<string, StompSubscription[]> = new Map();

  // BehaviorSubjects for reactive state management
  private chatRoomsSubject = new BehaviorSubject<ChatRoom[]>([]);
  public chatRooms$ = this.chatRoomsSubject.asObservable();

  private currentMessagesSubject = new BehaviorSubject<ChatMessage[]>([]);
  public currentMessages$ = this.currentMessagesSubject.asObservable();

  private newMessageSubject = new Subject<ChatMessage>();
  public newMessage$ = this.newMessageSubject.asObservable();

  private typingIndicatorSubject = new Subject<TypingIndicator>();
  public typingIndicator$ = this.typingIndicatorSubject.asObservable();

  private readReceiptSubject = new Subject<ReadReceipt>();
  public readReceipt$ = this.readReceiptSubject.asObservable();

  private onlineStatusSubject = new BehaviorSubject<Map<number, boolean>>(new Map());
  public onlineStatus$ = this.onlineStatusSubject.asObservable();

  // Active chat boxes (minimized at bottom right)
  private activeChatBoxesSubject = new BehaviorSubject<ChatBoxState[]>([]);
  public activeChatBoxes$ = this.activeChatBoxesSubject.asObservable();

  // Unread counts per room
  private unreadCountsSubject = new BehaviorSubject<Map<string, number>>(new Map());
  public unreadCounts$ = this.unreadCountsSubject.asObservable();

  constructor(
    private http: HttpClient,
    private keycloak: KeycloakService
  ) {}

  // =============== WebSocket Connection ===============

  async initializeWebSocket(userId: number): Promise<void> {
    if (this.connected && this.userId === userId && this.stompClient?.connected) {
      return;
    }

    this.userId = userId;
    await this.disconnect();

    try {
      const token = await this.keycloak.getToken();
      // Connect directly to ChatService (like NotificationService)
      const endpointUrl = `${this.chatServiceUrl}/ws`;

      this.stompClient = new Client({
        webSocketFactory: () => new SockJS(endpointUrl),
        connectHeaders: { Authorization: `Bearer ${token}` },
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        debug: (msg: string) => console.debug('[Chat WS]', msg)
      });

      this.stompClient.onConnect = () => {
        this.connected = true;
        console.log('Chat WebSocket connected');
        this.subscribeToOnlineStatus();
        this.subscribeToUserNotifications(); // Subscribe to user-specific notifications
        this.broadcastOnlineStatus(true);
      };

      this.stompClient.onStompError = (frame) => {
        console.error('Chat WebSocket STOMP error:', frame.headers['message'], frame.body);
      };

      this.stompClient.onWebSocketClose = () => {
        this.connected = false;
        console.warn('Chat WebSocket disconnected');
      };

      this.stompClient.activate();
    } catch (error) {
      console.error('Failed to initialize Chat WebSocket:', error);
    }
  }

  private subscribeToOnlineStatus(): void {
    if (!this.stompClient?.connected) return;

    this.stompClient.subscribe('/topic/chat/online', (message: IMessage) => {
      try {
        const status: OnlineStatus = JSON.parse(message.body);
        const currentMap = this.onlineStatusSubject.value;
        currentMap.set(status.userId, status.isOnline);
        this.onlineStatusSubject.next(new Map(currentMap));
      } catch (error) {
        console.error('Failed to parse online status', error);
      }
    });
  }

  // Subscribe to user-specific notifications for new messages in any room
  private subscribeToUserNotifications(): void {
    if (!this.stompClient?.connected || !this.userId) return;

    // Subscribe to user-specific topic for new message notifications
    // Using topic-based approach (simpler than Spring's user destinations)
    this.stompClient.subscribe(`/topic/chat/user/${this.userId}/notifications`, (message: IMessage) => {
      try {
        const notification = JSON.parse(message.body);
        console.log('[Chat WS] User notification received:', notification);
        
        // Handle new message notification
        if (notification.type === 'NEW_MESSAGE') {
          this.handleUserNotification(notification);
        }
      } catch (error) {
        console.error('Failed to parse user notification', error);
      }
    });

    console.log(`Subscribed to user notifications for user ${this.userId}`);
  }

  private handleUserNotification(notification: any): void {
    const { roomId, message, senderId } = notification;
    
    // Don't count own messages
    if (senderId === this.userId) return;
    
    // Check if the chat box for this room is open and not minimized
    const activeChatBoxes = this.activeChatBoxesSubject.value;
    const activeBox = activeChatBoxes.find(b => b.roomId === roomId);
    const isRoomActive = activeBox && !activeBox.isMinimized;
    
    // Only increment unread count if the room is not actively being viewed
    if (!isRoomActive) {
      const unreadCounts = this.unreadCountsSubject.value;
      const currentCount = unreadCounts.get(roomId) || 0;
      const newCount = currentCount + 1;
      unreadCounts.set(roomId, newCount);
      this.unreadCountsSubject.next(new Map(unreadCounts));
      
      // Also update the room's unreadCount in the rooms list
      const rooms = this.chatRoomsSubject.value;
      const room = rooms.find(r => r.id === roomId);
      if (room) {
        room.unreadCount = newCount;
        // Update last message info
        if (message) {
          room.lastMessageContent = message.content || this.getMessagePreview(message);
          room.lastMessageSenderId = message.senderId;
          room.lastMessageTime = message.createdAt;
          room.lastMessageType = message.messageType;
        }
        this.chatRoomsSubject.next([...rooms]);
      }
    }
    
    // Emit new message event for any listeners
    if (message) {
      this.newMessageSubject.next(message);
    }
  }

  subscribeToRoom(roomId: string): void {
    if (!this.stompClient?.connected) {
      console.warn('WebSocket not connected, cannot subscribe to room');
      return;
    }

    // Unsubscribe from existing subscriptions for this room
    this.unsubscribeFromRoom(roomId);

    const subscriptions: StompSubscription[] = [];

    // Subscribe to new messages
    const messageSub = this.stompClient.subscribe(`/topic/chat/${roomId}`, (message: IMessage) => {
      try {
        const chatMessage: ChatMessage = JSON.parse(message.body);
        console.debug('[Chat WS] New message in room', roomId, chatMessage);
        this.handleNewMessage(chatMessage);
      } catch (error) {
        console.error('Failed to parse chat message', error);
      }
    });
    subscriptions.push(messageSub);

    // Subscribe to typing indicators
    const typingSub = this.stompClient.subscribe(`/topic/chat/${roomId}/typing`, (message: IMessage) => {
      try {
        const typing: any = JSON.parse(message.body);
        // Backend sends 'chatRoomId', map it to 'roomId' for frontend
        if (typing.chatRoomId) {
          typing.roomId = typing.chatRoomId;
        } else {
          // Fallback: use the roomId from subscription if not provided
          typing.roomId = roomId;
        }
        this.typingIndicatorSubject.next(typing);
      } catch (error) {
        console.error('Failed to parse typing indicator', error);
      }
    });
    subscriptions.push(typingSub);

    // Subscribe to read receipts
    const readSub = this.stompClient.subscribe(`/topic/chat/${roomId}/read`, (message: IMessage) => {
      try {
        const receipt: ReadReceipt = JSON.parse(message.body);
        this.readReceiptSubject.next(receipt);
      } catch (error) {
        console.error('Failed to parse read receipt', error);
      }
    });
    subscriptions.push(readSub);

    // Subscribe to message edits
    const editSub = this.stompClient.subscribe(`/topic/chat/${roomId}/edit`, (message: IMessage) => {
      try {
        const editedMessage: ChatMessage = JSON.parse(message.body);
        this.handleMessageEdit(editedMessage);
      } catch (error) {
        console.error('Failed to parse edited message', error);
      }
    });
    subscriptions.push(editSub);

    // Subscribe to message deletions
    const deleteSub = this.stompClient.subscribe(`/topic/chat/${roomId}/delete`, (message: IMessage) => {
      try {
        const data = JSON.parse(message.body);
        this.handleMessageDelete(data.messageId);
      } catch (error) {
        console.error('Failed to parse delete notification', error);
      }
    });
    subscriptions.push(deleteSub);

    this.roomSubscriptions.set(roomId, subscriptions);
    console.log(`Subscribed to room ${roomId}`);
  }

  unsubscribeFromRoom(roomId: string): void {
    const subs = this.roomSubscriptions.get(roomId);
    if (subs) {
      subs.forEach(sub => sub.unsubscribe());
      this.roomSubscriptions.delete(roomId);
      console.log(`Unsubscribed from room ${roomId}`);
    }
  }

  private handleNewMessage(message: ChatMessage): void {
    // Update current messages if we're viewing this room
    const currentMessages = this.currentMessagesSubject.value;
    if (currentMessages.length > 0 && currentMessages[0]?.chatRoomId === message.chatRoomId) {
      // Add to beginning (messages are sorted desc)
      this.currentMessagesSubject.next([message, ...currentMessages]);
    }

    // Update unread counts if not from current user
    if (message.senderId !== this.userId) {
      // Check if the chat box for this room is open and not minimized
      const activeChatBoxes = this.activeChatBoxesSubject.value;
      const activeBox = activeChatBoxes.find(b => b.roomId === message.chatRoomId);
      const isRoomActive = activeBox && !activeBox.isMinimized;
      
      // Only increment unread count if the room is not actively being viewed
      if (!isRoomActive) {
        const unreadCounts = this.unreadCountsSubject.value;
        const currentCount = unreadCounts.get(message.chatRoomId) || 0;
        const newCount = currentCount + 1;
        unreadCounts.set(message.chatRoomId, newCount);
        this.unreadCountsSubject.next(new Map(unreadCounts));
        
        // Also update the room's unreadCount in the rooms list
        const rooms = this.chatRoomsSubject.value;
        const room = rooms.find(r => r.id === message.chatRoomId);
        if (room) {
          room.unreadCount = newCount;
          this.chatRoomsSubject.next([...rooms]);
        }
      }
    }

    // Emit new message event
    this.newMessageSubject.next(message);

    // Update chat room list with latest message
    this.updateChatRoomLastMessage(message);
  }

  private handleMessageEdit(message: ChatMessage): void {
    const currentMessages = this.currentMessagesSubject.value;
    const index = currentMessages.findIndex(m => m.id === message.id);
    if (index >= 0) {
      currentMessages[index] = message;
      this.currentMessagesSubject.next([...currentMessages]);
    }
  }

  private handleMessageDelete(messageId: string): void {
    const currentMessages = this.currentMessagesSubject.value;
    const index = currentMessages.findIndex(m => m.id === messageId);
    if (index >= 0) {
      currentMessages[index].isDeleted = true;
      this.currentMessagesSubject.next([...currentMessages]);
    }
  }

  private updateChatRoomLastMessage(message: ChatMessage): void {
    const rooms = this.chatRoomsSubject.value;
    const roomIndex = rooms.findIndex(r => r.id === message.chatRoomId);
    if (roomIndex >= 0) {
      rooms[roomIndex].lastMessageContent = message.content || this.getMessagePreview(message);
      rooms[roomIndex].lastMessageSenderId = message.senderId;
      rooms[roomIndex].lastMessageTime = message.createdAt;
      rooms[roomIndex].lastMessageType = message.messageType;
      
      // Move room to top
      const room = rooms.splice(roomIndex, 1)[0];
      rooms.unshift(room);
      
      this.chatRoomsSubject.next([...rooms]);
    }
  }

  private getMessagePreview(message: ChatMessage): string {
    if (message.messageType === 'IMAGE') {
      return '📷 Hình ảnh';
    } else if (message.messageType === 'FILE') {
      return '📎 ' + (message.fileName || 'Tệp đính kèm');
    }
    return message.content || '';
  }

  // =============== WebSocket Actions ===============

  sendMessageViaWebSocket(roomId: string, request: SendMessageRequest): void {
    if (!this.stompClient?.connected) {
      console.warn('WebSocket not connected, sending via HTTP');
      this.sendMessage(request).subscribe();
      return;
    }

    this.stompClient.publish({
      destination: `/app/chat/${roomId}/send`,
      body: JSON.stringify(request)
    });
  }

  sendTypingIndicator(roomId: string, userName: string, isTyping: boolean): void {
    if (!this.stompClient?.connected || !this.userId) return;

    this.stompClient.publish({
      destination: `/app/chat/${roomId}/typing`,
      body: JSON.stringify({
        userId: this.userId,
        userName: userName,
        isTyping: isTyping
      })
    });
  }

  sendReadReceipt(roomId: string): void {
    if (!this.stompClient?.connected || !this.userId) return;

    this.stompClient.publish({
      destination: `/app/chat/${roomId}/read`,
      body: JSON.stringify({
        userId: this.userId
      })
    });

    // Clear unread count for this room
    const unreadCounts = this.unreadCountsSubject.value;
    unreadCounts.set(roomId, 0);
    this.unreadCountsSubject.next(new Map(unreadCounts));
  }

  broadcastOnlineStatus(isOnline: boolean): void {
    if (!this.stompClient?.connected || !this.userId) return;

    this.stompClient.publish({
      destination: '/app/chat/online',
      body: JSON.stringify({
        userId: this.userId,
        isOnline: isOnline
      })
    });
  }

  async disconnect(): Promise<void> {
    // Broadcast offline status before disconnecting
    if (this.connected && this.userId) {
      this.broadcastOnlineStatus(false);
    }

    // Unsubscribe from all rooms
    this.roomSubscriptions.forEach((subs, roomId) => {
      subs.forEach(sub => sub.unsubscribe());
    });
    this.roomSubscriptions.clear();

    if (this.stompClient) {
      await this.stompClient.deactivate();
      this.stompClient = null;
    }
    this.connected = false;
  }

  // =============== HTTP API Methods ===============

  // Helper to get auth headers
  private getAuthHeaders(): Observable<HttpHeaders> {
    return from(this.keycloak.getToken()).pipe(
      switchMap(token => {
        const headers = new HttpHeaders({
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        });
        return [headers];
      })
    );
  }

  // Chat Rooms
  createChatRoom(request: CreateChatRoomRequest): Observable<ChatRoom> {
    return this.getAuthHeaders().pipe(
      switchMap(headers => this.http.post<ChatRoom>(
        `${this.gatewayUrl}${this.chatApiPrefix}/rooms`, 
        request, 
        { headers }
      ))
    );
  }

  getChatRoomById(roomId: string): Observable<ChatRoom> {
    return this.getAuthHeaders().pipe(
      switchMap(headers => this.http.get<ChatRoom>(
        `${this.gatewayUrl}${this.chatApiPrefix}/rooms/${roomId}`,
        { headers }
      ))
    );
  }

  getChatRoomByGroupId(groupId: number): Observable<ChatRoom> {
    return this.getAuthHeaders().pipe(
      switchMap(headers => this.http.get<ChatRoom>(
        `${this.gatewayUrl}${this.chatApiPrefix}/rooms/group/${groupId}`,
        { headers }
      ))
    );
  }

  getUserChatRooms(userId: number): Observable<ChatRoom[]> {
    return this.getAuthHeaders().pipe(
      switchMap(headers => this.http.get<ChatRoom[]>(
        `${this.gatewayUrl}${this.chatApiPrefix}/rooms/user/${userId}`,
        { headers }
      ))
    );
  }

  getUserChatRoomsPaged(userId: number, page = 0, size = 20): Observable<ChatPageResponse<ChatRoom>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.getAuthHeaders().pipe(
      switchMap(headers => this.http.get<ChatPageResponse<ChatRoom>>(
        `${this.gatewayUrl}${this.chatApiPrefix}/rooms/user/${userId}/paged`,
        { headers, params }
      ))
    );
  }

  syncChatRoomWithGroup(groupId: number, groupName: string, avatarFileId?: string, memberIds?: number[]): Observable<ChatRoom> {
    return this.getAuthHeaders().pipe(
      switchMap(headers => this.http.post<ChatRoom>(
        `${this.gatewayUrl}${this.chatApiPrefix}/rooms/sync`, 
        { groupId, groupName, avatarFileId, memberIds },
        { headers }
      ))
    );
  }

  // Messages
  sendMessage(request: SendMessageRequest): Observable<ChatMessage> {
    return this.getAuthHeaders().pipe(
      switchMap(headers => this.http.post<ChatMessage>(
        `${this.gatewayUrl}${this.chatApiPrefix}/messages`, 
        request,
        { headers }
      ))
    );
  }

  getMessages(roomId: string, page = 0, size = 50): Observable<ChatPageResponse<ChatMessage>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.getAuthHeaders().pipe(
      switchMap(headers => this.http.get<ChatPageResponse<ChatMessage>>(
        `${this.gatewayUrl}${this.chatApiPrefix}/rooms/${roomId}/messages`,
        { headers, params }
      ))
    );
  }

  getRecentMessages(roomId: string, limit = 50): Observable<ChatMessage[]> {
    const params = new HttpParams().set('limit', limit.toString());
    return this.getAuthHeaders().pipe(
      switchMap(headers => this.http.get<ChatMessage[]>(
        `${this.gatewayUrl}${this.chatApiPrefix}/rooms/${roomId}/messages/recent`,
        { headers, params }
      ))
    );
  }

  updateMessage(messageId: string, content: string): Observable<ChatMessage> {
    return this.getAuthHeaders().pipe(
      switchMap(headers => this.http.put<ChatMessage>(
        `${this.gatewayUrl}${this.chatApiPrefix}/messages/${messageId}`,
        { content },
        { headers }
      ))
    );
  }

  deleteMessage(messageId: string): Observable<void> {
    return this.getAuthHeaders().pipe(
      switchMap(headers => this.http.delete<void>(
        `${this.gatewayUrl}${this.chatApiPrefix}/messages/${messageId}`,
        { headers }
      ))
    );
  }

  markMessagesAsRead(roomId: string, userId: number): Observable<void> {
    const params = new HttpParams().set('userId', userId.toString());
    return this.getAuthHeaders().pipe(
      switchMap(headers => this.http.post<void>(
        `${this.gatewayUrl}${this.chatApiPrefix}/rooms/${roomId}/read`,
        {},
        { headers, params }
      ))
    );
  }

  getUnreadCount(roomId: string, userId: number): Observable<{ unreadCount: number }> {
    const params = new HttpParams().set('userId', userId.toString());
    return this.getAuthHeaders().pipe(
      switchMap(headers => this.http.get<{ unreadCount: number }>(
        `${this.gatewayUrl}${this.chatApiPrefix}/rooms/${roomId}/unread`,
        { headers, params }
      ))
    );
  }

  searchMessages(roomId: string, keyword: string, page = 0, size = 20): Observable<ChatPageResponse<ChatMessage>> {
    const params = new HttpParams()
      .set('keyword', keyword)
      .set('page', page.toString())
      .set('size', size.toString());
    return this.getAuthHeaders().pipe(
      switchMap(headers => this.http.get<ChatPageResponse<ChatMessage>>(
        `${this.gatewayUrl}${this.chatApiPrefix}/rooms/${roomId}/messages/search`,
        { headers, params }
      ))
    );
  }

  getMediaMessages(roomId: string, page = 0, size = 20): Observable<ChatPageResponse<ChatMessage>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.getAuthHeaders().pipe(
      switchMap(headers => this.http.get<ChatPageResponse<ChatMessage>>(
        `${this.gatewayUrl}${this.chatApiPrefix}/rooms/${roomId}/media`,
        { headers, params }
      ))
    );
  }

  getFileMessages(roomId: string, page = 0, size = 20): Observable<ChatPageResponse<ChatMessage>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.getAuthHeaders().pipe(
      switchMap(headers => this.http.get<ChatPageResponse<ChatMessage>>(
        `${this.gatewayUrl}${this.chatApiPrefix}/rooms/${roomId}/files`,
        { headers, params }
      ))
    );
  }

  getLinkMessages(roomId: string, page = 0, size = 20): Observable<ChatPageResponse<ChatMessage>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.getAuthHeaders().pipe(
      switchMap(headers => this.http.get<ChatPageResponse<ChatMessage>>(
        `${this.gatewayUrl}${this.chatApiPrefix}/rooms/${roomId}/links`,
        { headers, params }
      ))
    );
  }

  // =============== Chat Box Management ===============

  openChatBox(room: ChatRoom): void {
    const currentBoxes = this.activeChatBoxesSubject.value;
    const existingIndex = currentBoxes.findIndex(b => b.roomId === room.id);
    
    if (existingIndex >= 0) {
      // Already open, just maximize it
      currentBoxes[existingIndex].isMinimized = false;
      this.activeChatBoxesSubject.next([...currentBoxes]);
    } else {
      // Add new chat box (max 3 boxes)
      if (currentBoxes.length >= 3) {
        currentBoxes.shift(); // Remove oldest
      }
      
      const newBox: ChatBoxState = {
        roomId: room.id,
        room: room,
        isMinimized: false,
        isExpanded: false,
        unreadCount: 0,
        position: currentBoxes.length
      };
      
      currentBoxes.push(newBox);
      this.activeChatBoxesSubject.next([...currentBoxes]);
      
      // Subscribe to this room
      this.subscribeToRoom(room.id);
    }
    
    // Clear unread count for this room when opened
    this.clearUnreadCount(room.id);
  }

  clearUnreadCount(roomId: string): void {
    const unreadCounts = this.unreadCountsSubject.value;
    if (unreadCounts.has(roomId)) {
      unreadCounts.set(roomId, 0);
      this.unreadCountsSubject.next(new Map(unreadCounts));
    }
    
    // Also update the room in the rooms list
    const rooms = this.chatRoomsSubject.value;
    const room = rooms.find(r => r.id === roomId);
    if (room) {
      room.unreadCount = 0;
      this.chatRoomsSubject.next([...rooms]);
    }
  }

  closeChatBox(roomId: string): void {
    const currentBoxes = this.activeChatBoxesSubject.value;
    const filteredBoxes = currentBoxes.filter(b => b.roomId !== roomId);
    
    // Update positions
    filteredBoxes.forEach((box, index) => {
      box.position = index;
    });
    
    this.activeChatBoxesSubject.next(filteredBoxes);
    this.unsubscribeFromRoom(roomId);
  }

  minimizeChatBox(roomId: string): void {
    const currentBoxes = this.activeChatBoxesSubject.value;
    const box = currentBoxes.find(b => b.roomId === roomId);
    if (box) {
      box.isMinimized = true;
      this.activeChatBoxesSubject.next([...currentBoxes]);
    }
  }

  maximizeChatBox(roomId: string): void {
    const currentBoxes = this.activeChatBoxesSubject.value;
    const box = currentBoxes.find(b => b.roomId === roomId);
    if (box) {
      box.isMinimized = false;
      this.activeChatBoxesSubject.next([...currentBoxes]);
      
      // Clear unread count when maximizing
      this.clearUnreadCount(roomId);
    }
  }

  toggleExpandChatBox(roomId: string): void {
    const currentBoxes = this.activeChatBoxesSubject.value;
    const box = currentBoxes.find(b => b.roomId === roomId);
    if (box) {
      box.isExpanded = !box.isExpanded;
      this.activeChatBoxesSubject.next([...currentBoxes]);
    }
  }

  // =============== State Management Helpers ===============

  loadUserChatRooms(userId: number): void {
    this.getUserChatRooms(userId).subscribe({
      next: (rooms) => {
        // Get active chat boxes (rooms that user is currently viewing)
        const activeChatBoxes = this.activeChatBoxesSubject.value;
        const activeRoomIds = new Set(
          activeChatBoxes
            .filter(box => !box.isMinimized)
            .map(box => box.roomId)
        );
        
        // Update rooms but preserve unread count = 0 for active rooms
        const updatedRooms = rooms.map(room => {
          if (activeRoomIds.has(room.id)) {
            // User is viewing this room, force unread count to 0
            return { ...room, unreadCount: 0 };
          }
          return room;
        });
        
        this.chatRoomsSubject.next(updatedRooms);
        
        // Sync unread counts from rooms to unreadCountsSubject
        // But preserve 0 for active rooms
        const unreadCounts = new Map<string, number>();
        updatedRooms.forEach(room => {
          if (room.id) {
            unreadCounts.set(room.id, room.unreadCount || 0);
          }
        });
        this.unreadCountsSubject.next(unreadCounts);
      },
      error: (err) => {
        console.error('Failed to load chat rooms', err);
      }
    });
  }

  loadMessages(roomId: string, page = 0, size = 50): void {
    this.getMessages(roomId, page, size).subscribe({
      next: (response) => {
        if (page === 0) {
          this.currentMessagesSubject.next(response.content);
        } else {
          const current = this.currentMessagesSubject.value;
          this.currentMessagesSubject.next([...current, ...response.content]);
        }
      },
      error: (err) => {
        console.error('Failed to load messages', err);
      }
    });
  }

  clearCurrentMessages(): void {
    this.currentMessagesSubject.next([]);
  }

  getCurrentUserId(): number | null {
    return this.userId;
  }

  isConnected(): boolean {
    return this.connected;
  }
}
