import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { ChatService } from '../../services/chat.service';
import { ChatRoom, ChatBoxState } from '../../model/chat.model';

@Component({
  selector: 'app-chat-container',
  templateUrl: './chat-container.component.html',
  styleUrls: ['./chat-container.component.css']
})
export class ChatContainerComponent implements OnInit, OnDestroy {
  activeChatBoxes: ChatBoxState[] = [];
  private subscriptions = new Subscription();

  constructor(private chatService: ChatService) {}

  ngOnInit() {
    // Subscribe to active chat boxes
    this.subscriptions.add(
      this.chatService.activeChatBoxes$.subscribe(boxes => {
        this.activeChatBoxes = boxes;
      })
    );
  }

  openChat(room: ChatRoom) {
    this.chatService.openChatBox(room);
  }

  closeChat(roomId: string) {
    this.chatService.closeChatBox(roomId);
  }

  minimizeChat(roomId: string) {
    this.chatService.minimizeChatBox(roomId);
  }

  maximizeChat(roomId: string) {
    this.chatService.maximizeChatBox(roomId);
  }

  toggleExpandChat(roomId: string) {
    this.chatService.toggleExpandChatBox(roomId);
  }

  trackByRoomId(index: number, box: ChatBoxState): string {
    return box.roomId;
  }

  // Calculate right offset for open boxes (in pixels)
  // Takes into account expanded boxes which are wider
  getOpenBoxRightOffset(box: ChatBoxState): number {
    if (box.isMinimized) return 0;
    
    const openBoxes = this.activeChatBoxes.filter(b => !b.isMinimized);
    const boxIndex = openBoxes.findIndex(b => b.roomId === box.roomId);
    
    // Calculate total width of all boxes to the right of this box
    let offset = 20; // Initial gap from right edge
    for (let i = 0; i < boxIndex; i++) {
      const otherBox = openBoxes[i];
      // Expanded box: 500px, normal box: 320px, plus 20px gap
      offset += (otherBox.isExpanded ? 500 : 320) + 20;
    }
    
    return offset;
  }

  // Get index for minimized boxes - vertical stacking
  getMinimizedIndex(box: ChatBoxState): number {
    if (!box.isMinimized) return 0;
    
    const minimizedBoxes = this.activeChatBoxes.filter(b => b.isMinimized);
    return minimizedBoxes.findIndex(b => b.roomId === box.roomId);
  }

  // Get total width of all open boxes (for positioning minimized boxes)
  getOpenBoxesTotalWidth(): number {
    const openBoxes = this.activeChatBoxes.filter(b => !b.isMinimized);
    if (openBoxes.length === 0) return 0;
    
    let totalWidth = 0;
    for (const box of openBoxes) {
      totalWidth += (box.isExpanded ? 500 : 320) + 20;
    }
    return totalWidth;
  }

  ngOnDestroy() {
    this.subscriptions.unsubscribe();
  }
}
