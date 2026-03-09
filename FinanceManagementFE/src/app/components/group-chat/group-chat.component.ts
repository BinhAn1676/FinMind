import { Component, Input, OnInit, OnDestroy, ViewChild, ElementRef, AfterViewChecked, OnChanges, SimpleChanges, HostListener } from '@angular/core';
import { Subscription } from 'rxjs';
import { ChatService } from '../../services/chat.service';
import { FileService } from '../../services/file.service';
import { LanguageService } from '../../services/language.service';
import { GroupService } from '../../services/group.service';
import { ChatRoom, ChatMessage, SendMessageRequest, TypingIndicator } from '../../model/chat.model';

@Component({
  selector: 'app-group-chat',
  templateUrl: './group-chat.component.html',
  styleUrls: ['./group-chat.component.css']
})
export class GroupChatComponent implements OnInit, OnDestroy, AfterViewChecked, OnChanges {
  @Input() groupId!: number;
  @Input() groupName: string = '';
  @Input() groupAvatarUrl: string | null = null;
  
  @ViewChild('messagesContainer') private messagesContainer!: ElementRef;
  @ViewChild('messageInput') private messageInput!: ElementRef;
  @ViewChild('fileInput') private fileInput!: ElementRef;

  room: ChatRoom | null = null;
  messages: ChatMessage[] = [];
  newMessage = '';
  loading = true;
  sendingMessage = false;
  uploadingFile = false;
  
  // User info
  userId: number | null = null;
  userName: string = '';
  userAvatar: string = '';
  
  // Pagination
  currentPage = 0;
  hasMoreMessages = false;
  loadingMore = false;
  
  // Typing indicator
  typingUsers: Map<number, string> = new Map();
  private typingTimeout: any;
  
  // Reply
  replyingTo: ChatMessage | null = null;
  
  // Files
  selectedFiles: File[] = [];
  filePreviews: { file: File; previewUrl: string | null }[] = [];
  
  // Sticker
  showStickerPicker = false;
  selectedStickerCategory = 0;
  stickerCategories = [
    { name: 'Smileys', icon: '😀', stickers: ['😀', '😁', '😂', '🤣', '😃', '😄', '😅', '😆', '😉', '😊', '😋', '😎', '😍', '🥰', '😘', '😗', '😙', '😚', '🙂', '🤗', '🤩', '🤔', '🤨', '😐', '😑', '😶', '🙄', '😏', '😣', '😥'] },
    { name: 'Gestures', icon: '👍', stickers: ['👍', '👎', '👌', '✌️', '🤞', '🤟', '🤘', '🤙', '👈', '👉', '👆', '👇', '☝️', '👋', '🤚', '🖐️', '✋', '🖖', '👏', '🙌', '👐', '🤲', '🤝', '🙏', '✍️', '💪', '🦾', '🦵', '🦶', '👂'] },
    { name: 'Hearts', icon: '❤️', stickers: ['❤️', '🧡', '💛', '💚', '💙', '💜', '🖤', '🤍', '🤎', '💔', '❣️', '💕', '💞', '💓', '💗', '💖', '💘', '💝', '💟', '♥️', '💌', '💋', '👄', '👅', '🫀', '🫁', '🧠', '👀', '👁️', '👤'] },
    { name: 'Animals', icon: '🐱', stickers: ['🐱', '🐶', '🐭', '🐹', '🐰', '🦊', '🐻', '🐼', '🐨', '🐯', '🦁', '🐮', '🐷', '🐸', '🐵', '🐔', '🐧', '🐦', '🐤', '🦆', '🦅', '🦉', '🦇', '🐺', '🐗', '🐴', '🦄', '🐝', '🐛', '🦋'] },
    { name: 'Food', icon: '🍕', stickers: ['🍕', '🍔', '🍟', '🌭', '🍿', '🧂', '🥓', '🥚', '🍳', '🧇', '🥞', '🧈', '🍞', '🥐', '🥖', '🥨', '🧀', '🥗', '🥙', '🥪', '🌮', '🌯', '🫔', '🥫', '🍝', '🍜', '🍲', '🍛', '🍣', '🍱'] }
  ];
  
  // Scroll
  private shouldScrollToBottom = true;
  showScrollToBottom = false;
  highlightedMessageId: string | null = null;
  private highlightTimer: any;
  
  // Caches
  private avatarCache = new Map<string, string>();
  private failedFiles = new Set<string>();
  // Map userId -> avatarLiveUrl for group members
  private memberAvatarMap = new Map<number, string>();
  
  // Drag & Drop
  isDragOver = false;
  private dragCounter = 0;

  // Lightbox
  lightboxUrl: string | null = null;

  // Sidebar
  showSidebar = false;
  showSearch = false;
  searchKeyword = '';
  searchResults: ChatMessage[] = [];
  expandedSections: Set<string> = new Set();
  mediaMessages: ChatMessage[] = [];
  fileMessages: ChatMessage[] = [];
  linkMessages: ChatMessage[] = [];
  activeMediaTab: 'media' | 'file' | 'link' = 'media';
  sidebarMembers: any[] = [];

  private subscriptions = new Subscription();

  constructor(
    private chatService: ChatService,
    private fileService: FileService,
    private languageService: LanguageService,
    private groupService: GroupService
  ) {}

  ngOnInit() {
    this.loadUserInfo();
    this.loadOrCreateChatRoom();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['groupId'] && !changes['groupId'].firstChange) {
      this.cleanupRoom();
      this.loadOrCreateChatRoom();
    }
  }

  ngAfterViewChecked() {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
  }

  private loadUserInfo() {
    const userDetails = window.sessionStorage.getItem('userdetails');
    if (userDetails) {
      try {
        const user = JSON.parse(userDetails);
        this.userId = user.id;
        this.userName = user.firstName && user.lastName 
          ? `${user.firstName} ${user.lastName}` 
          : user.email?.split('@')[0] || 'User';
        this.userAvatar = user.avatarFileId || '';
      } catch (error) {
        console.error('Error parsing user details:', error);
      }
    }
  }

  private loadOrCreateChatRoom() {
    if (!this.groupId) return;
    
    this.loading = true;
    this.messages = [];
    
    this.chatService.syncChatRoomWithGroup(
      this.groupId,
      this.groupName,
      undefined,
      this.userId ? [this.userId] : []
    ).subscribe({
      next: (room) => {
        this.room = room;
        this.setupSubscriptions();
        this.chatService.subscribeToRoom(room.id);
        
        // Load group members and their avatars after sync
        this.loadGroupMembersAndAvatars();
        
        this.loadMessages();
        
        if (this.userId) {
          this.chatService.sendReadReceipt(room.id);
          this.chatService.markMessagesAsRead(room.id, this.userId).subscribe();
          this.chatService.clearUnreadCount(room.id);
        }
      },
      error: (err) => {
        console.error('Failed to load/create chat room:', err);
        this.loading = false;
      }
    });
  }

  private setupSubscriptions() {
    // Subscribe to new messages - SAME AS CHAT-BOX
    this.subscriptions.add(
      this.chatService.newMessage$.subscribe(message => {
        if (this.room && message.chatRoomId === this.room.id) {
          // Check if message already exists
          if (!this.messages.find(m => m.id === message.id)) {
            // Apply avatar from member map if available
            const messageWithAvatar = this.applyMemberAvatarToMessage(message);
            this.messages.push(messageWithAvatar);
            this.shouldScrollToBottom = true;
            
            // Mark as read if window is focused
            if (document.hasFocus() && this.userId) {
              this.chatService.sendReadReceipt(this.room.id);
            }
          }
        }
      })
    );

    // Subscribe to typing indicators - SAME AS CHAT-BOX
    this.subscriptions.add(
      this.chatService.typingIndicator$.subscribe((typing: TypingIndicator) => {
        // IMPORTANT: Only process typing indicators for THIS room
        if (typing.roomId && this.room && typing.roomId !== this.room.id) {
          return; // Ignore typing indicators from other rooms
        }
        
        if (typing.userId !== this.userId) {
          if (typing.isTyping) {
            this.typingUsers.set(typing.userId, typing.userName);
          } else {
            this.typingUsers.delete(typing.userId);
          }
        }
      })
    );
  }

  loadMessages() {
    if (!this.room) return;
    
    this.loading = true;
    this.currentPage = 0;
    
    this.chatService.getMessages(this.room.id, 0, 50).subscribe({
      next: (response) => {
        // Apply member avatars to loaded messages
        this.messages = response.content.reverse().map(msg => this.applyMemberAvatarToMessage(msg));
        this.hasMoreMessages = response.content.length === 50;
        this.loading = false;
        this.shouldScrollToBottom = true;
      },
      error: (err) => {
        console.error('Failed to load messages:', err);
        this.loading = false;
      }
    });
  }

  loadMoreMessages() {
    if (!this.room || this.loadingMore || !this.hasMoreMessages) return;
    
    this.loadingMore = true;
    this.shouldScrollToBottom = false;
    this.currentPage++;
    
    this.chatService.getMessages(this.room.id, this.currentPage, 50).subscribe({
      next: (response) => {
        // Apply member avatars to loaded messages
        const olderMessages = response.content.reverse().map(msg => this.applyMemberAvatarToMessage(msg));
        this.messages = [...olderMessages, ...this.messages];
        this.hasMoreMessages = response.content.length === 50;
        this.loadingMore = false;
      },
      error: (err) => {
        console.error('Failed to load more messages:', err);
        this.loadingMore = false;
        this.currentPage--;
      }
    });
  }

  onScroll(event: Event) {
    const element = event.target as HTMLElement;
    if (element.scrollTop < 100 && this.hasMoreMessages && !this.loadingMore) {
      this.loadMoreMessages();
    }
    // Show scroll-to-bottom button when scrolled up more than 300px from bottom
    const distanceFromBottom = element.scrollHeight - element.scrollTop - element.clientHeight;
    this.showScrollToBottom = distanceFromBottom > 300;
  }

  sendMessage() {
    if ((!this.newMessage.trim() && this.selectedFiles.length === 0) || !this.room || !this.userId || this.sendingMessage) return;
    
    if (this.selectedFiles.length > 0) {
      this.uploadAndSendFiles();
      return;
    }
    
    const content = this.newMessage.trim();
    if (!content) return;
    
    this.sendingMessage = true;
    
    const request: SendMessageRequest = {
      chatRoomId: this.room.id,
      senderId: this.userId,
      senderName: this.userName,
      senderAvatar: this.userAvatar,
      content: content,
      messageType: 'TEXT',
      replyToMessageId: this.replyingTo?.id,
      replyToContent: this.replyingTo?.content
    };
    
    this.chatService.sendMessageViaWebSocket(this.room.id, request);
    
    this.newMessage = '';
    this.replyingTo = null;
    this.sendingMessage = false;
    this.shouldScrollToBottom = true;
    this.resetTextareaHeight();
    
    // Stop typing indicator
    if (this.typingTimeout) {
      clearTimeout(this.typingTimeout);
    }
    this.chatService.sendTypingIndicator(this.room.id, this.userName, false);
  }

  onKeyDown(event: KeyboardEvent) {
    if (event.key === 'Enter') {
      if (event.altKey) {
        event.preventDefault();
        const textarea = event.target as HTMLTextAreaElement;
        const start = textarea.selectionStart;
        const end = textarea.selectionEnd;
        this.newMessage = this.newMessage.substring(0, start) + '\n' + this.newMessage.substring(end);
        setTimeout(() => {
          textarea.selectionStart = textarea.selectionEnd = start + 1;
          this.adjustTextareaHeight();
        });
      } else {
        event.preventDefault();
        this.sendMessage();
      }
    }
  }

  onMessageInput(event: Event) {
    this.adjustTextareaHeight();
    
    if (this.room && this.userId) {
      this.chatService.sendTypingIndicator(this.room.id, this.userName, true);
      
      clearTimeout(this.typingTimeout);
      this.typingTimeout = setTimeout(() => {
        if (this.room) {
          this.chatService.sendTypingIndicator(this.room.id, this.userName, false);
        }
      }, 2000);
    }
  }

  private adjustTextareaHeight() {
    if (this.messageInput) {
      const textarea = this.messageInput.nativeElement;
      textarea.style.height = 'auto';
      textarea.style.height = Math.min(textarea.scrollHeight, 150) + 'px';
    }
  }

  private resetTextareaHeight() {
    if (this.messageInput) {
      this.messageInput.nativeElement.style.height = 'auto';
    }
  }

  // File handling
  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      Array.from(input.files).forEach(file => this.addFileToQueue(file));
    }
    input.value = '';
  }

  private addFileToQueue(file: File) {
    if (this.selectedFiles.some(f => f.name === file.name && f.size === file.size)) {
      return;
    }
    
    this.selectedFiles.push(file);
    
    if (file.type.startsWith('image/')) {
      const reader = new FileReader();
      reader.onload = (e) => {
        this.filePreviews.push({ file, previewUrl: e.target?.result as string });
      };
      reader.readAsDataURL(file);
    } else {
      this.filePreviews.push({ file, previewUrl: null });
    }
  }

  clearSelectedFile(index?: number) {
    if (index !== undefined) {
      this.selectedFiles.splice(index, 1);
      this.filePreviews.splice(index, 1);
    } else {
      this.selectedFiles = [];
      this.filePreviews = [];
    }
  }

  uploadAndSendFiles() {
    if (!this.room || !this.userId || this.selectedFiles.length === 0) return;
    
    this.uploadingFile = true;
    const filesToUpload = [...this.selectedFiles];
    const caption = this.newMessage.trim();
    let uploadedCount = 0;
    
    filesToUpload.forEach((file, index) => {
      const isImage = file.type.startsWith('image/');
      
      this.fileService.uploadChatFile(file, this.userId!.toString()).subscribe({
        next: (response) => {
          const request: SendMessageRequest = {
            chatRoomId: this.room!.id,
            senderId: this.userId!,
            senderName: this.userName,
            senderAvatar: this.userAvatar,
            content: (index === 0 && caption) ? caption : (isImage ? '📷 Hình ảnh' : `📎 ${file.name}`),
            messageType: isImage ? 'IMAGE' : 'FILE',
            fileId: response.id,
            fileName: response.originalFileName,
            fileType: response.fileType,
            fileSize: response.fileSize,
            replyToMessageId: index === 0 ? this.replyingTo?.id : undefined,
            replyToContent: index === 0 ? this.replyingTo?.content : undefined
          };
          
          this.chatService.sendMessageViaWebSocket(this.room!.id, request);
          uploadedCount++;
          
          if (uploadedCount === filesToUpload.length) {
            this.clearSelectedFile();
            this.newMessage = '';
            this.replyingTo = null;
            this.uploadingFile = false;
            this.shouldScrollToBottom = true;
          }
        },
        error: (err) => {
          console.error('Failed to upload file:', err);
          uploadedCount++;
          if (uploadedCount === filesToUpload.length) {
            this.uploadingFile = false;
          }
        }
      });
    });
  }

  // Drag and drop
  onDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    if (!this.isDragOver) {
      this.dragCounter++;
      this.isDragOver = true;
    }
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragCounter--;
    if (this.dragCounter === 0) {
      this.isDragOver = false;
    }
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
    this.dragCounter = 0;
    
    const files = event.dataTransfer?.files;
    if (files) {
      Array.from(files).forEach(file => this.addFileToQueue(file));
    }
  }

  // Sticker
  toggleStickerPicker() {
    this.showStickerPicker = !this.showStickerPicker;
  }

  closeStickerPicker() {
    this.showStickerPicker = false;
  }

  selectStickerCategory(index: number) {
    this.selectedStickerCategory = index;
  }

  sendSticker(sticker: string) {
    if (!this.room || !this.userId) return;
    
    const request: SendMessageRequest = {
      chatRoomId: this.room.id,
      senderId: this.userId,
      senderName: this.userName,
      senderAvatar: this.userAvatar,
      content: sticker,
      messageType: 'STICKER'
    };
    
    this.chatService.sendMessageViaWebSocket(this.room.id, request);
    this.closeStickerPicker();
    this.shouldScrollToBottom = true;
  }

  // Reply
  replyTo(message: ChatMessage) {
    this.replyingTo = message;
    this.messageInput?.nativeElement?.focus();
  }

  cancelReply() {
    this.replyingTo = null;
  }

  // Helper methods - SAME AS CHAT-BOX
  isOwnMessage(message: ChatMessage): boolean {
    return message.senderId === this.userId;
  }

  trackByMessageId(index: number, message: ChatMessage): string {
    return message.id || index.toString();
  }

  getSenderAvatar(message: ChatMessage): string {
    // First check if we have avatar from member map
    if (message.senderId && this.memberAvatarMap.has(message.senderId)) {
      return this.memberAvatarMap.get(message.senderId)!;
    }

    if (message.senderAvatarLiveUrl) {
      return message.senderAvatarLiveUrl;
    }

    if (message.senderAvatar && message.senderAvatar.startsWith('http')) {
      return message.senderAvatar;
    }

    // Static asset path (e.g. /assets/avatar/finbot.png) — return directly, don't call file API
    if (message.senderAvatar && message.senderAvatar.startsWith('/')) {
      return message.senderAvatar;
    }

    if (message.senderAvatar && this.avatarCache.has(message.senderAvatar)) {
      const cachedUrl = this.avatarCache.get(message.senderAvatar);
      if (cachedUrl) return cachedUrl;
    }

    if (message.senderAvatar && !message.senderAvatar.startsWith('http') && !this.failedFiles.has(message.senderAvatar)) {
      this.loadSenderAvatar(message.senderAvatar);
    }

    const name = message.senderName || 'User';
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=4ECDC4&color=fff&size=40&bold=true`;
  }

  private loadSenderAvatar(fileId: string) {
    // Skip static asset paths — they are not MinIO file IDs
    if (fileId.startsWith('/') || fileId.startsWith('http')) return;
    if (this.avatarCache.has(fileId) || this.failedFiles.has(fileId)) return;

    this.avatarCache.set(fileId, '');

    this.fileService.getLiveUrl(fileId).subscribe({
      next: (response) => {
        this.avatarCache.set(fileId, response.liveUrl);

        // FIX: Deep clone affected messages to prevent shared reference mutation
        this.messages = this.messages.map(msg => {
          if (msg.senderAvatar === fileId && !msg.senderAvatarLiveUrl) {
            // Create new object with avatar live URL
            return { ...msg, senderAvatarLiveUrl: response.liveUrl };
          }
          return msg;
        });
      },
      error: () => {
        this.failedFiles.add(fileId);
        this.avatarCache.delete(fileId);
      }
    });
  }

  // Track which messages are currently loading
  private loadingFileIds = new Set<string>();

  getFileUrl(message: ChatMessage): string | null {
    // Already have URL on the message
    if (message.fileLiveUrl) return message.fileLiveUrl;
    if (!message.fileId) return null;
    
    // Check if already failed
    if (this.failedFiles.has(message.fileId)) return null;
    
    // Start loading if not already
    if (!this.loadingFileIds.has(message.id || message.fileId)) {
      this.loadFileUrlForMessage(message);
    }
    
    return null;
  }

  private loadFileUrlForMessage(message: ChatMessage) {
    if (!message.fileId || !message.id) return;

    const loadingKey = message.id || message.fileId;
    if (this.loadingFileIds.has(loadingKey) || this.failedFiles.has(message.fileId)) return;

    this.loadingFileIds.add(loadingKey);

    this.fileService.getLiveUrl(message.fileId).subscribe({
      next: (response) => {
        // FIX: Deep clone the message to prevent shared reference mutation
        const messageIndex = this.messages.findIndex(m => m.id === message.id);
        if (messageIndex >= 0) {
          // Create a new message object (deep clone) with the live URL
          this.messages[messageIndex] = {
            ...this.messages[messageIndex],
            fileLiveUrl: response.liveUrl
          };
          // Trigger change detection with new array reference
          this.messages = [...this.messages];
        }
        this.loadingFileIds.delete(loadingKey);
      },
      error: () => {
        this.failedFiles.add(message.fileId!);
        this.loadingFileIds.delete(loadingKey);
      }
    });
  }

  downloadFile(message: ChatMessage) {
    if (message.fileId) {
      this.fileService.getLiveUrl(message.fileId).subscribe({
        next: (response) => window.open(response.liveUrl, '_blank'),
        error: (err) => console.error('Failed to get file URL:', err)
      });
    }
  }

  getFileIcon(fileType: string): string {
    if (fileType?.includes('pdf')) return '📄';
    if (fileType?.includes('word') || fileType?.includes('doc')) return '📝';
    if (fileType?.includes('excel') || fileType?.includes('sheet') || fileType?.includes('xls')) return '📊';
    if (fileType?.includes('zip') || fileType?.includes('rar') || fileType?.includes('7z')) return '📦';
    if (fileType?.includes('image')) return '🖼️';
    if (fileType?.includes('video')) return '🎬';
    if (fileType?.includes('audio')) return '🎵';
    return '📎';
  }

  formatFileSize(bytes?: number): string {
    if (!bytes) return '';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  formatTime(dateString?: string): string {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
  }

  formatDate(dateString?: string): string {
    if (!dateString) return '';
    const date = new Date(dateString);
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    
    if (date.toDateString() === today.toDateString()) {
      return 'Hôm nay';
    } else if (date.toDateString() === yesterday.toDateString()) {
      return 'Hôm qua';
    } else {
      return date.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
    }
  }

  shouldShowDateSeparator(index: number): boolean {
    if (index === 0) return true;
    const currentDate = new Date(this.messages[index].createdAt || '').toDateString();
    const prevDate = new Date(this.messages[index - 1].createdAt || '').toDateString();
    return currentDate !== prevDate;
  }

  getTypingText(): string {
    const users = Array.from(this.typingUsers.values());
    if (users.length === 0) return '';
    if (users.length === 1) return `${users[0]} đang nhập...`;
    if (users.length === 2) return `${users[0]} và ${users[1]} đang nhập...`;
    return `${users.length} người đang nhập...`;
  }

  private scrollToBottom(): void {
    if (this.messagesContainer) {
      const element = this.messagesContainer.nativeElement;
      element.scrollTop = element.scrollHeight;
    }
  }

  scrollToBottomManual(): void {
    this.shouldScrollToBottom = true;
    this.scrollToBottom();
    this.showScrollToBottom = false;
  }

  scrollToMessage(messageId: string): void {
    // Clear any existing highlight
    if (this.highlightTimer) clearTimeout(this.highlightTimer);
    this.highlightedMessageId = null;

    const tryScroll = () => {
      const el = document.getElementById('msg-' + messageId);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        this.highlightedMessageId = messageId;
        this.highlightTimer = setTimeout(() => {
          this.highlightedMessageId = null;
        }, 2500);
      }
    };

    // Message might not be in current loaded list — check first
    const exists = this.messages.find(m => m.id === messageId);
    if (exists) {
      setTimeout(tryScroll, 50);
    } else {
      // Not loaded yet: just highlight in search results but don't break
      tryScroll();
    }
  }

  getTranslation(key: string): string {
    return this.languageService.translate(key);
  }

  /**
   * Load group members and their avatars after sync
   * This creates a map of userId -> avatarLiveUrl for quick lookup
   */
  private loadGroupMembersAndAvatars() {
    if (!this.groupId) return;
    
    this.groupService.getMembers(this.groupId, { page: 0, size: 100 }).subscribe({
      next: (pageResponse) => {
        const members = pageResponse.content || [];
        
        // Collect all avatar file IDs
        const avatarIds = Array.from(
          new Set(
            members
              .map(m => m.avatar)
              .filter((id): id is string => !!id)
          )
        );
        
        if (avatarIds.length === 0) {
          console.log('No avatars found for group members');
          return;
        }
        
        // Get live URLs for all avatars
        this.fileService.getLiveUrls(avatarIds).subscribe({
          next: (liveUrlMap) => {
            // Create userId -> avatarLiveUrl map
            members.forEach(member => {
              if (member.avatar && liveUrlMap[member.avatar]) {
                this.memberAvatarMap.set(member.userId, liveUrlMap[member.avatar]);
              }
            });
            
            console.log(`Loaded ${this.memberAvatarMap.size} member avatars for group ${this.groupId}`);
            
            // Update existing messages with avatars
            this.messages = this.messages.map(msg => this.applyMemberAvatarToMessage(msg));
          },
          error: (err) => {
            console.error('Failed to load member avatar URLs:', err);
          }
        });
      },
      error: (err) => {
        console.error('Failed to load group members:', err);
      }
    });
  }

  /**
   * Apply member avatar to a message if available in the map
   */
  private applyMemberAvatarToMessage(message: ChatMessage): ChatMessage {
    if (message.senderId && this.memberAvatarMap.has(message.senderId)) {
      return {
        ...message,
        senderAvatarLiveUrl: this.memberAvatarMap.get(message.senderId)!
      };
    }
    return message;
  }

  // Sidebar methods
  toggleSidebar(): void {
    this.showSidebar = !this.showSidebar;
    if (this.showSidebar) {
      this.loadSidebarMembers();
    }
  }

  toggleSearch(): void {
    this.showSearch = !this.showSearch;
    if (!this.showSearch) {
      this.searchKeyword = '';
      this.searchResults = [];
    }
  }

  onSearch(): void {
    if (!this.room || !this.searchKeyword.trim()) {
      this.searchResults = [];
      return;
    }
    this.chatService.searchMessages(this.room.id, this.searchKeyword.trim()).subscribe({
      next: (res) => { this.searchResults = res.content; },
      error: (err) => console.error('Search failed:', err)
    });
  }

  toggleSection(section: string): void {
    if (this.expandedSections.has(section)) {
      this.expandedSections.delete(section);
    } else {
      this.expandedSections.add(section);
      if (section === 'media') {
        this.loadMediaContent();
      }
    }
  }

  isSectionExpanded(section: string): boolean {
    return this.expandedSections.has(section);
  }

  setMediaTab(tab: 'media' | 'file' | 'link'): void {
    this.activeMediaTab = tab;
    this.loadMediaContent();
  }

  private loadMediaContent(): void {
    if (!this.room) return;
    if (this.activeMediaTab === 'media' && this.mediaMessages.length === 0) {
      this.chatService.getMediaMessages(this.room.id).subscribe({
        next: (res) => {
          this.mediaMessages = res.content;
          this.loadLiveUrlsForMessages(this.mediaMessages, 'media');
        },
        error: (err) => console.error('Failed to load media:', err)
      });
    } else if (this.activeMediaTab === 'file' && this.fileMessages.length === 0) {
      this.chatService.getFileMessages(this.room.id).subscribe({
        next: (res) => {
          this.fileMessages = res.content;
          this.loadLiveUrlsForMessages(this.fileMessages, 'file');
        },
        error: (err) => console.error('Failed to load files:', err)
      });
    } else if (this.activeMediaTab === 'link' && this.linkMessages.length === 0) {
      this.chatService.getLinkMessages(this.room.id).subscribe({
        next: (res) => { this.linkMessages = res.content; },
        error: (err) => console.error('Failed to load links:', err)
      });
    }
  }

  private loadLiveUrlsForMessages(messages: ChatMessage[], type: 'media' | 'file'): void {
    const fileIds = messages
      .filter(m => m.fileId && !m.fileLiveUrl)
      .map(m => m.fileId!);

    if (fileIds.length === 0) return;

    this.fileService.getLiveUrls(fileIds).subscribe({
      next: (urlMap) => {
        const target = type === 'media' ? this.mediaMessages : this.fileMessages;
        const updated = target.map(msg =>
          msg.fileId && urlMap[msg.fileId]
            ? { ...msg, fileLiveUrl: urlMap[msg.fileId] }
            : msg
        );
        if (type === 'media') {
          this.mediaMessages = updated;
        } else {
          this.fileMessages = updated;
        }
      },
      error: (err) => console.error('Failed to load live URLs for sidebar:', err)
    });
  }

  private loadSidebarMembers(): void {
    if (!this.groupId) return;
    this.groupService.getMembers(this.groupId, { page: 0, size: 100 }).subscribe({
      next: (res) => { this.sidebarMembers = res.content || []; },
      error: (err) => console.error('Failed to load sidebar members:', err)
    });
  }

  getMemberAvatar(member: any): string {
    if (member.userId && this.memberAvatarMap.has(member.userId)) {
      return this.memberAvatarMap.get(member.userId)!;
    }
    const name = member.fullName || member.email || 'User';
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=4ECDC4&color=fff&size=40&bold=true`;
  }

  extractLinks(content: string): string[] {
    const urlRegex = /https?:\/\/[^\s]+/g;
    return content.match(urlRegex) || [content];
  }

  openImageFullscreen(msg: ChatMessage): void {
    if (msg.fileLiveUrl) {
      this.lightboxUrl = msg.fileLiveUrl;
    }
  }

  closeLightbox(): void {
    this.lightboxUrl = null;
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.lightboxUrl) this.closeLightbox();
  }

  getGroupAvatarUrl(): string {
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(this.groupName || 'G')}&background=4ECDC4&color=fff&size=40&bold=true`;
  }

  private cleanupRoom() {
    if (this.room) {
      this.chatService.unsubscribeFromRoom(this.room.id);
    }
    this.subscriptions.unsubscribe();
    this.subscriptions = new Subscription();
    this.room = null;
    this.messages = [];
    this.typingUsers.clear();
    this.memberAvatarMap.clear();
    this.searchResults = [];
    this.mediaMessages = [];
    this.fileMessages = [];
    this.linkMessages = [];
    this.expandedSections.clear();
  }

  ngOnDestroy() {
    this.cleanupRoom();
    clearTimeout(this.typingTimeout);
    if (this.highlightTimer) clearTimeout(this.highlightTimer);
  }
}
