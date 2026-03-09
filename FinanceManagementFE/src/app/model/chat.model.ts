// Chat Room Model
export interface ChatRoom {
  id: string;
  groupId: number;
  name: string;
  avatarFileId?: string;
  avatarLiveUrl?: string;
  members?: ChatMember[];
  lastMessageSenderId?: number;
  lastMessageSenderName?: string;
  lastMessageContent?: string;
  lastMessageTime?: string;
  lastMessageType?: string;
  unreadCount?: number;
  isActive?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

// Chat Member Model
export interface ChatMember {
  userId: number;
  fullName?: string;
  email?: string;
  avatar?: string;
  avatarLiveUrl?: string;
  role?: string; // OWNER, ADMIN, MEMBER
  isOnline?: boolean;
  lastSeen?: string;
}

// Chat Message Model
export interface ChatMessage {
  id: string;
  chatRoomId: string;
  senderId: number;
  senderName?: string;
  senderAvatar?: string;
  senderAvatarLiveUrl?: string;
  content?: string;
  messageType: 'TEXT' | 'IMAGE' | 'FILE' | 'SYSTEM' | 'STICKER';
  // File/Image info
  fileId?: string;
  fileName?: string;
  fileType?: string;
  fileSize?: number;
  fileLiveUrl?: string;
  // Reply info
  replyToMessageId?: string;
  replyToContent?: string;
  mentionsAI?: boolean;
  readBy?: number[];
  isDeleted?: boolean;
  isEdited?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

// Request DTOs
export interface SendMessageRequest {
  chatRoomId: string;
  senderId: number;
  senderName?: string;
  senderAvatar?: string;
  content?: string;
  messageType?: 'TEXT' | 'IMAGE' | 'FILE' | 'STICKER';
  fileId?: string;
  fileName?: string;
  fileType?: string;
  fileSize?: number;
  replyToMessageId?: string;
  replyToContent?: string;
  mentionsAI?: boolean;
}

export interface CreateChatRoomRequest {
  groupId: number;
  name?: string;
  avatarFileId?: string;
  memberIds?: number[];
}

// Page Response
export interface ChatPageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// Typing Indicator
export interface TypingIndicator {
  userId: number;
  userName: string;
  isTyping: boolean;
  roomId?: string; // Optional: which room this typing indicator belongs to
}

// Read Receipt
export interface ReadReceipt {
  userId: number;
  roomId: string;
}

// Online Status
export interface OnlineStatus {
  userId: number;
  isOnline: boolean;
}

// Chat Box State (for minimized chat boxes)
export interface ChatBoxState {
  roomId: string;
  room: ChatRoom;
  isMinimized: boolean;
  isExpanded: boolean;
  unreadCount: number;
  position: number;
}

// AI Suggestion
export interface AISuggestion {
  id: string;
  icon: string;
  label: string;
  action: string;
}
