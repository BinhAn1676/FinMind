import { Component, OnInit, OnDestroy, Input, Output, EventEmitter, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription, Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { ChatService } from '../../services/chat.service';
import { FileService, FileUploadResponse } from '../../services/file.service';
import { LanguageService } from '../../services/language.service';
import { GroupService } from '../../services/group.service';
import { ChatRoom, ChatMessage, SendMessageRequest, TypingIndicator, AISuggestion } from '../../model/chat.model';

@Component({
  selector: 'app-chat-box',
  templateUrl: './chat-box.component.html',
  styleUrls: ['./chat-box.component.css']
})
export class ChatBoxComponent implements OnInit, OnDestroy, AfterViewChecked {
  @Input() room!: ChatRoom;
  @Input() isMinimized = false;
  @Input() isExpanded = false;
  @Input() rightOffset = 20; // Calculated right offset in pixels
  @Input() minimizedIndex = 0; // Index for vertical stacking when minimized
  @Input() openBoxesTotalWidth = 0; // Total width of all open boxes
  
  @Output() close = new EventEmitter<void>();
  @Output() minimize = new EventEmitter<void>();
  @Output() maximize = new EventEmitter<void>();
  @Output() toggleExpand = new EventEmitter<void>();

  @ViewChild('messagesContainer') messagesContainer!: ElementRef;
  @ViewChild('messageInput') messageInput!: ElementRef;
  @ViewChild('fileInput') fileInput!: ElementRef;

  messages: ChatMessage[] = [];
  newMessage = '';
  loading = false;
  loadingMore = false;
  sendingMessage = false;
  uploadingFile = false;
  
  // Typing indicator
  typingUsers: Map<number, string> = new Map();
  aiTyping = false;
  aiTypingMessage = '';
  private typingSubject = new Subject<string>();
  private typingTimeout: any;
  
  // User info
  private userId: number | null = null;
  private userName: string = '';
  private userAvatar: string = '';
  
  // Pagination
  private currentPage = 0;
  private hasMoreMessages = true;
  
  // Reply
  replyingTo: ChatMessage | null = null;
  
  // AI Suggestions
  showAISuggestions = false;
  aiSuggestions: AISuggestion[] = [
    { id: '1', icon: '💡', label: 'Gợi ý tối ưu', action: 'optimize' },
    { id: '2', icon: '📊', label: 'Phân tích', action: 'analyze' },
    { id: '3', icon: '⚠️', label: 'Cảnh báo', action: 'alert' },
    { id: '4', icon: '🔮', label: 'Dự báo', action: 'predict' }
  ];
  
  // Sticker picker
  showStickerPicker = false;
  stickerCategories = [
    {
      name: 'Cảm xúc',
      icon: '😀',
      stickers: ['😀', '😃', '😄', '😁', '😆', '😅', '🤣', '😂', '🙂', '😊', '😇', '🥰', '😍', '🤩', '😘', '😗', '😚', '😋', '😛', '😜', '🤪', '😝', '🤑', '🤗', '🤭', '🤫', '🤔', '🤐', '🤨', '😐', '😑', '😶', '😏', '😒', '🙄', '😬', '🤥', '😌', '😔', '😪', '🤤', '😴', '😷', '🤒', '🤕', '🤢', '🤮', '🤧', '🥵', '🥶', '🥴', '😵', '🤯', '🤠', '🥳', '😎', '🤓', '🧐']
    },
    {
      name: 'Cử chỉ',
      icon: '👋',
      stickers: ['👋', '🤚', '🖐️', '✋', '🖖', '👌', '🤌', '🤏', '✌️', '🤞', '🤟', '🤘', '🤙', '👈', '👉', '👆', '🖕', '👇', '☝️', '👍', '👎', '✊', '👊', '🤛', '🤜', '👏', '🙌', '👐', '🤲', '🤝', '🙏', '✍️', '💪', '🦾', '🦿', '🦵', '🦶']
    },
    {
      name: 'Trái tim',
      icon: '❤️',
      stickers: ['❤️', '🧡', '💛', '💚', '💙', '💜', '🖤', '🤍', '🤎', '💔', '❣️', '💕', '💞', '💓', '💗', '💖', '💘', '💝', '💟', '♥️', '💌', '💋', '👄', '🫦']
    },
    {
      name: 'Động vật',
      icon: '🐱',
      stickers: ['🐶', '🐱', '🐭', '🐹', '🐰', '🦊', '🐻', '🐼', '🐨', '🐯', '🦁', '🐮', '🐷', '🐸', '🐵', '🙈', '🙉', '🙊', '🐔', '🐧', '🐦', '🐤', '🦆', '🦅', '🦉', '🦇', '🐺', '🐗', '🐴', '🦄', '🐝', '🐛', '🦋', '🐌', '🐞', '🐜', '🦟', '🦗', '🕷️', '🦂']
    },
    {
      name: 'Đồ ăn',
      icon: '🍔',
      stickers: ['🍎', '🍐', '🍊', '🍋', '🍌', '🍉', '🍇', '🍓', '🫐', '🍈', '🍒', '🍑', '🥭', '🍍', '🥥', '🥝', '🍅', '🍆', '🥑', '🥦', '🥬', '🌽', '🥕', '🫒', '🧄', '🧅', '🥔', '🍠', '🥐', '🥯', '🍞', '🥖', '🥨', '🧀', '🥚', '🍳', '🧈', '🥞', '🧇', '🥓', '🥩', '🍗', '🍖', '🦴', '🌭', '🍔', '🍟', '🍕', '🫓', '🥪', '🥙', '🧆', '🌮', '🌯', '🫔', '🥗', '🥘', '🫕', '🍝', '🍜', '🍲', '🍛', '🍣', '🍱', '🥟', '🦪', '🍤', '🍙', '🍚', '🍘', '🍥', '🥠', '🥮', '🍢', '🍡', '🍧', '🍨', '🍦', '🥧', '🧁', '🍰', '🎂', '🍮', '🍭', '🍬', '🍫', '🍿', '🍩', '🍪', '🌰', '🥜', '🍯']
    },
    {
      name: 'Hoạt động',
      icon: '⚽',
      stickers: ['⚽', '🏀', '🏈', '⚾', '🥎', '🎾', '🏐', '🏉', '🥏', '🎱', '🪀', '🏓', '🏸', '🏒', '🏑', '🥍', '🏏', '🪃', '🥅', '⛳', '🪁', '🏹', '🎣', '🤿', '🥊', '🥋', '🎽', '🛹', '🛼', '🛷', '⛸️', '🥌', '🎿', '⛷️', '🏂', '🪂', '🏋️', '🤼', '🤸', '🤺', '⛹️', '🤾', '🏌️', '🏇', '🧘', '🏄', '🏊', '🤽', '🚣', '🧗', '🚵', '🚴', '🏆', '🥇', '🥈', '🥉', '🏅', '🎖️', '🎗️', '🎪', '🎭', '🩰', '🎨', '🎬', '🎤', '🎧', '🎼', '🎹', '🥁', '🪘', '🎷', '🎺', '🪗', '🎸', '🪕', '🎻', '🎲', '♟️', '🎯', '🎳', '🎮', '🎰', '🧩']
    },
    {
      name: 'Biểu tượng',
      icon: '💯',
      stickers: ['💯', '💢', '💥', '💫', '💦', '💨', '🕳️', '💣', '💬', '👁️‍🗨️', '🗨️', '🗯️', '💭', '💤', '🔥', '✨', '🌟', '💫', '⭐', '🌈', '☀️', '🌤️', '⛅', '🌥️', '☁️', '🌦️', '🌧️', '⛈️', '🌩️', '🌨️', '❄️', '☃️', '⛄', '🌬️', '💨', '🌪️', '🌫️', '🌊', '💧', '💦', '☔', '⚡', '🎉', '🎊', '🎈', '🎁', '🏷️', '📌', '📍', '✅', '❌', '❓', '❗', '‼️', '⁉️', '💲', '💱', '©️', '®️', '™️']
    }
  ];
  selectedStickerCategory = 0;
  
  // File preview - support multiple files
  selectedFiles: File[] = [];
  filePreviews: { file: File; previewUrl: string | null }[] = [];
  
  // Drag & Drop
  isDragOver = false;
  private dragCounter = 0; // To handle nested drag events
  
  private subscriptions = new Subscription();
  private shouldScrollToBottom = true;

  constructor(
    private chatService: ChatService,
    private fileService: FileService,
    private languageService: LanguageService,
    private groupService: GroupService,
    private router: Router
  ) {}

  ngOnInit() {
    this.loadUserInfo();
    this.setupSubscriptions();
    this.loadMessages();
    this.setupTypingDebounce();
    this.loadRoomAvatar();
    
    // Load group members and their avatars if this is a group chat
    if (this.room.groupId) {
      this.loadGroupMembersAndAvatars();
    }
    
    // Subscribe to room
    this.chatService.subscribeToRoom(this.room.id);
    
    // Mark messages as read (both via WebSocket and HTTP API)
    if (this.userId) {
      this.chatService.sendReadReceipt(this.room.id);
      // Also mark on server side
      this.chatService.markMessagesAsRead(this.room.id, this.userId).subscribe({
        error: (err) => console.error('Failed to mark messages as read:', err)
      });
    }
  }

  ngAfterViewChecked() {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false; // Reset after scrolling
    }
  }

  private loadUserInfo() {
    const userDetails = window.sessionStorage.getItem('userdetails');
    if (userDetails) {
      try {
        const user = JSON.parse(userDetails);
        this.userId = user.id;
        this.userName = user.fullName || user.name || 'User';
        // FIX: Use avatarFileId (the file ID stored in DB) to be consistent with group-chat
        this.userAvatar = user.avatarFileId || user.avatar || '';
      } catch (error) {
        console.error('Error parsing user details:', error);
      }
    }
  }

  private loadRoomAvatar() {
    // Skip loading if bot room or static asset
    if (this.room.id?.startsWith('bot_') || this.room.avatarFileId?.startsWith('/assets/')) {
      this.room.avatarLiveUrl = this.room.avatarFileId;
      return;
    }

    // Load avatar if we have fileId but no liveUrl
    if (this.room.avatarFileId && !this.room.avatarLiveUrl) {
      this.fileService.getLiveUrl(this.room.avatarFileId).subscribe({
        next: (response) => {
          this.room.avatarLiveUrl = response.liveUrl;
          console.log('Room avatar loaded:', response.liveUrl);
        },
        error: (err) => {
          console.warn('Failed to load room avatar:', err);
        }
      });
    }
  }

  private setupSubscriptions() {
    // Subscribe to messages updates (for new messages via WebSocket)
    // Note: Initial load is handled separately in loadMessages()
    this.subscriptions.add(
      this.chatService.currentMessages$.subscribe(messages => {
        // Only update if messages are for this room
        if (messages.length > 0 && messages[0]?.chatRoomId === this.room.id) {
          this.messages = [...messages].reverse(); // Oldest first for display
          this.shouldScrollToBottom = true;
        }
      })
    );

    // Subscribe to new messages
    this.subscriptions.add(
      this.chatService.newMessage$.subscribe(message => {
        if (message.chatRoomId === this.room.id) {
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

    // Subscribe to typing indicators
    this.subscriptions.add(
      this.chatService.typingIndicator$.subscribe((typing: TypingIndicator) => {
        // IMPORTANT: Only process typing indicators for THIS room
        if (typing.roomId && typing.roomId !== this.room.id) {
          return; // Ignore typing indicators from other rooms
        }
        
        // Check if this is AI typing indicator
        if ((typing as any).type === 'AI_TYPING') {
          this.aiTyping = typing.isTyping;
          this.aiTypingMessage = (typing as any).message || 'FinBot đang suy nghĩ...';
          // Auto-scroll when AI starts typing to show typing indicator
          if (typing.isTyping) {
            this.shouldScrollToBottom = true;
          }
        } else if (typing.userId !== this.userId) {
          // Regular user typing
          if (typing.isTyping) {
            this.typingUsers.set(typing.userId, typing.userName);
          } else {
            this.typingUsers.delete(typing.userId);
          }
        }
      })
    );
  }

  private setupTypingDebounce() {
    this.subscriptions.add(
      this.typingSubject.pipe(
        debounceTime(300),
        distinctUntilChanged()
      ).subscribe(value => {
        if (value && this.userId) {
          this.chatService.sendTypingIndicator(this.room.id, this.userName, true);
          
          // Clear previous timeout
          if (this.typingTimeout) {
            clearTimeout(this.typingTimeout);
          }
          
          // Stop typing indicator after 3 seconds
          this.typingTimeout = setTimeout(() => {
            this.chatService.sendTypingIndicator(this.room.id, this.userName, false);
          }, 3000);
        }
      })
    );
  }

  loadMessages() {
    this.loading = true;
    this.currentPage = 0;
    
    // Call getMessages directly to properly handle loading state
    this.chatService.getMessages(this.room.id, 0, 50).subscribe({
      next: (response) => {
        // Apply member avatars to loaded messages
        this.messages = response.content.reverse().map(msg => this.applyMemberAvatarToMessage(msg));
        this.hasMoreMessages = response.content.length === 50;
        this.loading = false;
        this.shouldScrollToBottom = true;
        console.log(`Loaded ${response.content.length} messages for room ${this.room.id}`);
      },
      error: (err) => {
        console.error('Failed to load messages:', err);
        this.loading = false;
        this.messages = []; // Empty on error
      }
    });
  }

  loadMoreMessages() {
    if (this.loadingMore || !this.hasMoreMessages) return;
    
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
      }
    });
  }

  onScroll(event: Event) {
    const element = event.target as HTMLElement;
    if (element.scrollTop < 100 && !this.loadingMore) {
      this.loadMoreMessages();
    }
  }


  sendMessage() {
    if ((!this.newMessage.trim() && this.selectedFiles.length === 0) || !this.userId || this.sendingMessage) return;

    // If there are files, upload them first
    if (this.selectedFiles.length > 0) {
      this.uploadAndSendFiles();
      return;
    }

    this.sendingMessage = true;
    const mentionsAI = this.newMessage.includes('@AI') || this.newMessage.includes('@ai');

    const request: SendMessageRequest = {
      chatRoomId: this.room.id,
      senderId: this.userId,
      senderName: this.userName,
      senderAvatar: this.userAvatar,
      content: this.newMessage.trim(),
      messageType: 'TEXT',
      mentionsAI: mentionsAI,
      replyToMessageId: this.replyingTo?.id,
      replyToContent: this.replyingTo?.content
    };

    // Send via WebSocket for real-time
    this.chatService.sendMessageViaWebSocket(this.room.id, request);
    
    // Clear input
    this.newMessage = '';
    this.replyingTo = null;
    this.showAISuggestions = false;
    this.sendingMessage = false;
    this.shouldScrollToBottom = true;
    
    // Reset textarea height
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
        // Alt+Enter: Insert new line
        event.preventDefault();
        const textarea = event.target as HTMLTextAreaElement;
        const start = textarea.selectionStart;
        const end = textarea.selectionEnd;
        const value = this.newMessage;
        
        // Insert newline at cursor position
        this.newMessage = value.substring(0, start) + '\n' + value.substring(end);
        
        // Move cursor after the newline
        setTimeout(() => {
          textarea.selectionStart = textarea.selectionEnd = start + 1;
          this.adjustTextareaHeight(textarea);
        }, 0);
      } else if (!event.shiftKey) {
        // Enter (without Alt or Shift): Send message
        event.preventDefault();
        this.sendMessage();
      }
    }
  }

  private adjustTextareaHeight(textarea: HTMLTextAreaElement) {
    // Reset height to auto to get the correct scrollHeight
    textarea.style.height = 'auto';
    // Set new height based on content, max 100px
    const newHeight = Math.min(textarea.scrollHeight, 100);
    textarea.style.height = newHeight + 'px';
  }

  private resetTextareaHeight() {
    if (this.messageInput?.nativeElement) {
      this.messageInput.nativeElement.style.height = 'auto';
    }
  }

  onMessageInput(event: Event) {
    const textarea = event.target as HTMLTextAreaElement;
    this.adjustTextareaHeight(textarea);
    
    const value = textarea.value;
    this.typingSubject.next(value);
    
    // Check for @AI mention
    if (value.includes('@AI') || value.includes('@ai')) {
      this.showAISuggestions = true;
    } else {
      this.showAISuggestions = false;
    }
  }

  // File handling
  triggerFileInput() {
    this.fileInput.nativeElement.click();
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      // Add all selected files
      for (let i = 0; i < input.files.length; i++) {
        this.addFileToQueue(input.files[i]);
      }
    }
  }

  private addFileToQueue(file: File) {
    // Check if file already exists (by name and size)
    if (this.selectedFiles.some(f => f.name === file.name && f.size === file.size)) {
      return;
    }
    
    this.selectedFiles.push(file);
    
    // Create preview for images
    if (file.type.startsWith('image/')) {
      const reader = new FileReader();
      reader.onload = (e) => {
        this.filePreviews.push({
          file: file,
          previewUrl: e.target?.result as string
        });
      };
      reader.readAsDataURL(file);
    } else {
      this.filePreviews.push({
        file: file,
        previewUrl: null
      });
    }
  }

  clearSelectedFile(index?: number) {
    if (index !== undefined) {
      // Remove specific file
      const file = this.selectedFiles[index];
      this.selectedFiles.splice(index, 1);
      this.filePreviews = this.filePreviews.filter(p => p.file !== file);
    } else {
      // Clear all files
      this.selectedFiles = [];
      this.filePreviews = [];
    }
    if (this.fileInput) {
      this.fileInput.nativeElement.value = '';
    }
  }

  // Drag & Drop handlers
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
    if (files && files.length > 0) {
      // Add all dropped files to queue
      for (let i = 0; i < files.length; i++) {
        this.addFileToQueue(files[i]);
      }
      
      // Focus on input so user can add caption
      this.messageInput?.nativeElement?.focus();
    }
  }

  private uploadAndSendFiles() {
    if (this.selectedFiles.length === 0 || !this.userId) return;

    this.uploadingFile = true;
    const filesToUpload = [...this.selectedFiles];
    const caption = this.newMessage.trim();
    let uploadedCount = 0;
    const totalFiles = filesToUpload.length;

    // Upload each file sequentially
    filesToUpload.forEach((file, index) => {
      const isImage = file.type.startsWith('image/');
      
      this.fileService.uploadChatFile(file, this.userId!.toString()).subscribe({
        next: (response: FileUploadResponse) => {
          const request: SendMessageRequest = {
            chatRoomId: this.room.id,
            senderId: this.userId!,
            senderName: this.userName,
            senderAvatar: this.userAvatar,
            // Only add caption to the first file
            content: (index === 0 && caption) ? caption : (isImage ? '📷 Hình ảnh' : `📎 ${file.name}`),
            messageType: isImage ? 'IMAGE' : 'FILE',
            fileId: response.id,
            fileName: response.originalFileName,
            fileType: response.fileType,
            fileSize: response.fileSize,
            // Only add reply info to the first file
            replyToMessageId: index === 0 ? this.replyingTo?.id : undefined,
            replyToContent: index === 0 ? this.replyingTo?.content : undefined
          };

          this.chatService.sendMessageViaWebSocket(this.room.id, request);
          uploadedCount++;
          
          // All files uploaded
          if (uploadedCount === totalFiles) {
            this.clearSelectedFile();
            this.newMessage = '';
            this.replyingTo = null;
            this.uploadingFile = false;
            this.shouldScrollToBottom = true;
          }
        },
        error: (err) => {
          console.error('Failed to upload file:', file.name, err);
          uploadedCount++;
          
          // Continue even if some fail
          if (uploadedCount === totalFiles) {
            this.clearSelectedFile();
            this.uploadingFile = false;
          }
        }
      });
    });
  }

  // Reply handling
  replyTo(message: ChatMessage) {
    this.replyingTo = message;
    this.messageInput?.nativeElement?.focus();
  }

  cancelReply() {
    this.replyingTo = null;
  }

  // AI Suggestion handling
  selectAISuggestion(suggestion: AISuggestion) {
    this.newMessage = `@AI ${suggestion.label}: `;
    this.showAISuggestions = false;
    this.messageInput?.nativeElement?.focus();
  }

  // Sticker handling
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
    if (!this.userId) return;

    const request: SendMessageRequest = {
      chatRoomId: this.room.id,
      senderId: this.userId,
      senderName: this.userName,
      senderAvatar: this.userAvatar,
      content: sticker,
      messageType: 'STICKER',
      mentionsAI: false
    };

    this.chatService.sendMessageViaWebSocket(this.room.id, request);
    this.showStickerPicker = false;
    this.shouldScrollToBottom = true;
  }

  // Message formatting
  isMyMessage(message: ChatMessage): boolean {
    return message.senderId === this.userId;
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
    
    if (date.toDateString() === today.toDateString()) {
      return 'Hôm nay';
    }
    
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    if (date.toDateString() === yesterday.toDateString()) {
      return 'Hôm qua';
    }
    
    return date.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' });
  }

  shouldShowDateDivider(index: number): boolean {
    if (index === 0) return true;
    
    const currentDate = new Date(this.messages[index].createdAt || '');
    const prevDate = new Date(this.messages[index - 1].createdAt || '');
    
    return currentDate.toDateString() !== prevDate.toDateString();
  }

  getTypingText(): string {
    const users = Array.from(this.typingUsers.values());
    if (users.length === 0) return '';
    if (users.length === 1) return `${users[0]} đang nhập...`;
    if (users.length === 2) return `${users[0]} và ${users[1]} đang nhập...`;
    return `${users.length} người đang nhập...`;
  }

  getRoomAvatar(): string {
    // Check if this is bot room - use static asset
    if (this.room.id?.startsWith('bot_')) {
      return '/assets/avatar/finbot.png';
    }

    // Check if avatar is static asset path
    if (this.room.avatarFileId?.startsWith('/assets/')) {
      return this.room.avatarFileId;
    }

    if (this.room.avatarLiveUrl) {
      return this.room.avatarLiveUrl;
    }

    const name = this.room.name || 'Group';
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=4ECDC4&color=fff&size=40&bold=true`;
  }

  // Positioning methods
  getPositionRight(): number {
    if (this.isMinimized) {
      // Minimized boxes: positioned to the right of all open boxes
      return this.openBoxesTotalWidth + 20;
    } else {
      // Open boxes: use calculated offset from container
      return this.rightOffset;
    }
  }

  getPositionBottom(): number {
    if (this.isMinimized) {
      // Minimized boxes: stack vertically
      return (this.minimizedIndex * 56) + 10;
    } else {
      // Open boxes: at bottom
      return 0;
    }
  }

  getSenderAvatar(message: ChatMessage): string {
    // Check if this is FinBot (ID: 999999) - use static asset directly
    if (message.senderId === 999999) {
      return '/assets/avatar/finbot.png';
    }

    // Check if avatar is a static asset path (starts with /assets/)
    if (message.senderAvatar && message.senderAvatar.startsWith('/assets/')) {
      return message.senderAvatar;
    }

    // First check if we have avatar from member map
    if (message.senderId && this.memberAvatarMap.has(message.senderId)) {
      return this.memberAvatarMap.get(message.senderId)!;
    }

    // Check live URL first
    if (message.senderAvatarLiveUrl) {
      return message.senderAvatarLiveUrl;
    }

    // Check if senderAvatar is a URL (not a file ID)
    if (message.senderAvatar && message.senderAvatar.startsWith('http')) {
      return message.senderAvatar;
    }

    // Check avatar cache (only if it has a real URL, not empty placeholder)
    if (message.senderAvatar && this.avatarCache.has(message.senderAvatar)) {
      const cachedUrl = this.avatarCache.get(message.senderAvatar);
      if (cachedUrl) {
        return cachedUrl;
      }
    }

    // If it's a file ID and not failed, load it
    if (message.senderAvatar && !message.senderAvatar.startsWith('http') && !this.failedFiles.has(message.senderAvatar)) {
      this.loadSenderAvatar(message.senderAvatar);
    }

    // Fallback to initials avatar
    const name = message.senderName || 'User';
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=4ECDC4&color=fff&size=32&bold=true`;
  }

  private avatarCache = new Map<string, string>();
  private fileUrlCache = new Map<string, string>();
  private failedFiles = new Set<string>(); // Track failed file IDs to prevent retry spam
  // Map userId -> avatarLiveUrl for group members
  private memberAvatarMap = new Map<number, string>();
  
  private loadSenderAvatar(fileId: string) {
    // Skip if already loaded, loading, or failed
    if (this.avatarCache.has(fileId) || this.failedFiles.has(fileId)) return;

    // Mark as loading by setting empty string
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
      error: (err) => {
        console.warn('Failed to load sender avatar:', fileId, err);
        // Mark as failed to prevent infinite retry
        this.failedFiles.add(fileId);
        this.avatarCache.delete(fileId);
      }
    });
  }

  getFileUrl(message: ChatMessage): string | null {
    // Check if already have live URL
    if (message.fileLiveUrl) {
      return message.fileLiveUrl;
    }
    
    // No file ID, can't load
    if (!message.fileId) {
      return null;
    }
    
    // Check cache
    const cached = this.fileUrlCache.get(message.fileId);
    if (cached) {
      return cached;
    }
    
    // Check if failed before
    if (this.failedFiles.has(message.fileId)) {
      return null;
    }
    
    // Start loading
    this.loadFileUrl(message.fileId);
    return null;
  }

  private loadFileUrl(fileId: string) {
    // Skip if already loading or failed
    if (this.fileUrlCache.has(fileId) || this.failedFiles.has(fileId)) return;

    // Mark as loading
    this.fileUrlCache.set(fileId, '');

    this.fileService.getLiveUrl(fileId).subscribe({
      next: (response) => {
        this.fileUrlCache.set(fileId, response.liveUrl);

        // FIX: Deep clone affected messages to prevent shared reference mutation
        this.messages = this.messages.map(msg => {
          if (msg.fileId === fileId && !msg.fileLiveUrl) {
            // Create new object with live URL
            return { ...msg, fileLiveUrl: response.liveUrl };
          }
          return msg;
        });
      },
      error: (err) => {
        console.warn('Failed to load file URL:', fileId, err);
        this.failedFiles.add(fileId);
        this.fileUrlCache.delete(fileId);
      }
    });
  }

  getFileIcon(fileType?: string): string {
    if (!fileType) return '📎';
    if (fileType.startsWith('image/')) return '🖼️';
    if (fileType.includes('pdf')) return '📄';
    if (fileType.includes('word') || fileType.includes('document')) return '📝';
    if (fileType.includes('excel') || fileType.includes('spreadsheet')) return '📊';
    if (fileType.includes('zip') || fileType.includes('rar')) return '📦';
    return '📎';
  }

  formatFileSize(bytes?: number): string {
    if (!bytes) return '';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  downloadFile(message: ChatMessage) {
    if (message.fileId) {
      this.fileService.getLiveUrl(message.fileId).subscribe({
        next: (response) => {
          window.open(response.liveUrl, '_blank');
        },
        error: (err) => {
          console.error('Failed to get file URL:', err);
        }
      });
    }
  }

  private scrollToBottom() {
    try {
      if (this.messagesContainer) {
        const element = this.messagesContainer.nativeElement;
        element.scrollTop = element.scrollHeight;
      }
    } catch (err) {}
  }

  getTranslation(key: string): string {
    return this.languageService.translate(key);
  }

  /**
   * Load group members and their avatars if this is a group chat
   * This creates a map of userId -> avatarLiveUrl for quick lookup
   */
  private loadGroupMembersAndAvatars() {
    if (!this.room.groupId) return;
    
    this.groupService.getMembers(this.room.groupId, { page: 0, size: 100 }).subscribe({
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
            
            console.log(`Loaded ${this.memberAvatarMap.size} member avatars for group ${this.room.groupId}`);
            
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

  // Actions
  onClose() {
    this.close.emit();
  }

  onMinimize() {
    this.minimize.emit();
  }

  onMaximize() {
    this.maximize.emit();
  }

  onToggleExpand() {
    this.toggleExpand.emit();
  }

  // Navigate to full chat in group detail
  navigateToGroupChat() {
    if (this.room.groupId) {
      // Close the chat box first
      this.close.emit();
      // Navigate to groups page with chat tab selected
      this.router.navigate(['/groups'], { 
        queryParams: { 
          groupId: this.room.groupId,
          tab: 'chat'
        }
      });
    }
  }

  ngOnDestroy() {
    this.subscriptions.unsubscribe();
    if (this.typingTimeout) {
      clearTimeout(this.typingTimeout);
    }
    this.chatService.unsubscribeFromRoom(this.room.id);
  }
}
