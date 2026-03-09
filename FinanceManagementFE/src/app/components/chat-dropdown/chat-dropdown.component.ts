import { Component, OnInit, OnDestroy, HostListener, Output, EventEmitter } from '@angular/core';
import { Subscription, forkJoin, of } from 'rxjs';
import { catchError, finalize, switchMap } from 'rxjs/operators';
import { ChatService } from '../../services/chat.service';
import { ChatRoom } from '../../model/chat.model';
import { LanguageService } from '../../services/language.service';
import { FileService } from '../../services/file.service';
import { GroupService, GroupSummary } from '../../services/group.service';

@Component({
  selector: 'app-chat-dropdown',
  templateUrl: './chat-dropdown.component.html',
  styleUrls: ['./chat-dropdown.component.css']
})
export class ChatDropdownComponent implements OnInit, OnDestroy {
  @Output() openChatBox = new EventEmitter<ChatRoom>();
  
  showDropdown = false;
  chatRooms: ChatRoom[] = [];
  loading = false;
  refreshing = false;
  lastRefreshError: string | null = null;
  searchQuery = '';
  activeTab: 'all' | 'unread' | 'groups' = 'all';
  totalUnreadCount = 0;
  
  private subscriptions = new Subscription();
  private userId: number | null = null;
  private avatarCache = new Map<string, string>();
  private refreshTimeoutHandle: any;

  constructor(
    private chatService: ChatService,
    private languageService: LanguageService,
    private fileService: FileService,
    private groupService: GroupService
  ) {}

  async ngOnInit() {
    // Get user ID from session storage
    const userDetails = window.sessionStorage.getItem('userdetails');
    if (userDetails) {
      try {
        const user = JSON.parse(userDetails);
        this.userId = user.id;
        if (this.userId) {
          await this.initializeChat();
        }
      } catch (error) {
        console.error('Error parsing user details:', error);
      }
    }

    // Subscribe to chat rooms updates
    this.subscriptions.add(
      this.chatService.chatRooms$.subscribe(rooms => {
        this.chatRooms = rooms;
        this.loadAvatars();
        this.loading = false;
        // Calculate total unread from rooms
        this.calculateTotalUnreadFromRooms(rooms);
      })
    );

    // Subscribe to unread counts for real-time updates
    this.subscriptions.add(
      this.chatService.unreadCounts$.subscribe(counts => {
        this.calculateTotalUnread(counts);
      })
    );
  }

  async initializeChat() {
    if (!this.userId) return;
    
    try {
      // Initialize WebSocket first
      await this.chatService.initializeWebSocket(this.userId);
      
      // Don't load chat rooms immediately on init - wait for user to open dropdown
      // This prevents race conditions when page loads but dropdown isn't visible yet
      console.log('Chat WebSocket initialized, rooms will load when dropdown opens');
    } catch (error) {
      console.error('Failed to initialize chat:', error);
    }
  }

  /**
   * Refresh rooms list. This is safe to call repeatedly; it won't run concurrently.
   * Flow: ensure WS initialized -> sync rooms with groups -> load user's rooms
   */
  refreshChatRooms() {
    if (!this.userId) {
      console.warn('Cannot refresh chat rooms: userId is null');
      return;
    }

    // Prevent overlapping refreshes (common when user clicks dropdown quickly)
    if (this.refreshing) {
      console.log('Already refreshing chat rooms, skipping');
      return;
    }

    console.log('Starting chat rooms refresh...');
    this.refreshing = true;
    this.loading = true;
    this.lastRefreshError = null;
    
    // Load groups and sync chat rooms with member information
    this.groupService.search('', 0, 100).pipe(
      switchMap(response => {
        const groups = response.content;
        console.log(`Found ${groups.length} groups to sync`);
        
        if (groups.length === 0) {
          console.log('No groups found, skipping sync but will load existing chat rooms');
          return of([]);
        }
        
        // Create sync observables for each group
        // Include current user in memberIds
        const syncObservables = groups.map(group => 
          this.chatService.syncChatRoomWithGroup(
            group.id,
            group.name,
            group.avatarFileId,
            [this.userId!] // Add current user as member
          ).pipe(
            catchError(err => {
              console.warn(`Failed to sync chat room for group ${group.id}:`, err);
              return of(null);
            })
          )
        );
        
        // Wait for all syncs to complete
        return forkJoin(syncObservables);
      }),
      finalize(() => {
        // Always reset refreshing flag regardless of success or error
        this.refreshing = false;

        // Load user's chat rooms after all syncs are done
        this.chatService.loadUserChatRooms(this.userId!);

        // Fallback: sometimes the rooms stream hasn't updated yet (or emits empty first).
        // Re-trigger a load shortly after to ensure dropdown isn't stuck empty.
        if (this.refreshTimeoutHandle) {
          clearTimeout(this.refreshTimeoutHandle);
        }
        this.refreshTimeoutHandle = setTimeout(() => {
          if (this.showDropdown && (this.chatRooms?.length || 0) === 0 && this.userId) {
            this.chatService.loadUserChatRooms(this.userId);
          }
        }, 1200);
      })
    ).subscribe({
      next: (results) => {
        const successCount = results.filter(r => r !== null).length;
        console.log(`Synced ${successCount} chat rooms`);
      },
      error: (err) => {
        console.error('Failed to load groups:', err);
        this.lastRefreshError = 'Không thể tải danh sách nhóm/phòng chat. Vui lòng thử lại.';
        this.loading = false;
      }
    });
  }

  private loadAvatars() {
    const fileIds = this.chatRooms
      .filter(room =>
        room.avatarFileId &&
        !this.avatarCache.has(room.avatarFileId) &&
        !room.avatarFileId.startsWith('/assets/') &&  // Skip static assets
        !room.id?.startsWith('bot_')  // Skip bot rooms
      )
      .map(room => room.avatarFileId!);

    if (fileIds.length === 0) return;

    this.fileService.getLiveUrls(fileIds).subscribe({
      next: (urls) => {
        Object.entries(urls).forEach(([id, url]) => {
          this.avatarCache.set(id, url);
        });
      },
      error: (err) => {
        console.error('Failed to load avatars:', err);
      }
    });
  }

  private calculateTotalUnread(counts: Map<string, number>) {
    let total = 0;
    counts.forEach(count => {
      total += count;
    });
    this.totalUnreadCount = total;
  }

  private calculateTotalUnreadFromRooms(rooms: ChatRoom[]) {
    let total = 0;
    rooms.forEach(room => {
      total += room.unreadCount || 0;
    });
    this.totalUnreadCount = total;
  }

  private resolveUserId(): void {
    if (this.userId) return;
    try {
      const raw = window.sessionStorage.getItem('userdetails');
      if (raw) {
        const user = JSON.parse(raw);
        if (user?.id) this.userId = user.id;
      }
    } catch {}
  }

  async toggleDropdown() {
    this.showDropdown = !this.showDropdown;
    if (this.showDropdown) {
      this.resolveUserId();

      if (!this.userId) {
        console.warn('Chat dropdown: userId still not available after login');
        return;
      }

      console.log('Chat dropdown opened, ensuring WebSocket and loading rooms...');
      try {
        await this.chatService.initializeWebSocket(this.userId);
      } catch (e) {
        console.warn('Chat WS init failed (dropdown open):', e);
      }

      if (!this.refreshing) {
        this.refreshChatRooms();
      } else {
        console.log('Already refreshing chat rooms, skipping duplicate refresh');
      }
    }
  }

  async refreshNow() {
    this.resolveUserId();
    if (!this.userId) return;
    try {
      await this.chatService.initializeWebSocket(this.userId);
    } catch (e) {
      console.warn('Chat WS init failed (manual refresh):', e);
    }
    this.refreshChatRooms();
  }

  closeDropdown() {
    this.showDropdown = false;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event) {
    const target = event.target as HTMLElement;
    if (!target.closest('.chat-dropdown-container')) {
      this.closeDropdown();
    }
  }

  selectTab(tab: 'all' | 'unread' | 'groups') {
    this.activeTab = tab;
  }

  get filteredRooms(): ChatRoom[] {
    let rooms = this.chatRooms;
    
    // Filter by search query
    if (this.searchQuery.trim()) {
      const query = this.searchQuery.toLowerCase();
      rooms = rooms.filter(room => 
        room.name?.toLowerCase().includes(query)
      );
    }
    
    // Filter by tab
    if (this.activeTab === 'unread') {
      rooms = rooms.filter(room => (room.unreadCount || 0) > 0);
    }
    
    return rooms;
  }

  getRoomAvatar(room: ChatRoom): string {
    // Check if this is bot room (ID starts with 'bot_') - use static asset directly
    if (room.id && room.id.startsWith('bot_')) {
      return '/assets/avatar/finbot.png';
    }

    // Check if avatar is a static asset path (starts with /assets/)
    if (room.avatarFileId && room.avatarFileId.startsWith('/assets/')) {
      return room.avatarFileId;
    }

    // Check cache first
    if (room.avatarFileId && this.avatarCache.has(room.avatarFileId)) {
      return this.avatarCache.get(room.avatarFileId)!;
    }

    // Use live URL if available
    if (room.avatarLiveUrl) {
      return room.avatarLiveUrl;
    }

    // Fallback to initials avatar
    const name = room.name || 'Group';
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=4ECDC4&color=fff&size=48&bold=true`;
  }

  onRoomClick(room: ChatRoom) {
    // Set avatarLiveUrl from cache before emitting
    if (room.avatarFileId && this.avatarCache.has(room.avatarFileId)) {
      room.avatarLiveUrl = this.avatarCache.get(room.avatarFileId);
    }
    this.chatService.openChatBox(room);
    this.closeDropdown();
  }

  formatTime(dateString?: string): string {
    if (!dateString) return '';
    
    const date = new Date(dateString);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return 'Vừa xong';
    if (minutes < 60) return `${minutes} phút`;
    if (hours < 24) return `${hours} giờ`;
    if (days < 7) return `${days} ngày`;

    return date.toLocaleDateString('vi-VN');
  }

  getTranslation(key: string): string {
    return this.languageService.translate(key);
  }

  ngOnDestroy() {
    this.subscriptions.unsubscribe();
    if (this.refreshTimeoutHandle) {
      clearTimeout(this.refreshTimeoutHandle);
    }
  }
}
