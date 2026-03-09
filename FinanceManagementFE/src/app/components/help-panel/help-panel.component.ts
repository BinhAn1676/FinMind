import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { HelpContent, HelpService } from '../../services/help.service';
import { ChatService } from '../../services/chat.service';
import { ChatBoxState } from '../../model/chat.model';

@Component({
  selector: 'app-help-panel',
  templateUrl: './help-panel.component.html',
  styleUrls: ['./help-panel.component.css']
})
export class HelpPanelComponent implements OnInit, OnDestroy {
  isOpen = false;
  helpContent: HelpContent | null = null;
  hasContent = false;
  fabBottom = '28px';

  private subs: Subscription[] = [];

  constructor(public helpService: HelpService, private chatService: ChatService) {}

  ngOnInit(): void {
    this.subs.push(
      this.helpService.isOpen$.subscribe(open => (this.isOpen = open)),
      this.helpService.currentHelp$.subscribe(content => {
        this.helpContent = content;
        this.hasContent = !!content;
      }),
      this.chatService.activeChatBoxes$.subscribe(boxes => {
        this.fabBottom = this.computeFabBottom(boxes);
      })
    );
  }

  private computeFabBottom(boxes: ChatBoxState[]): string {
    if (!boxes.length) return '28px';

    const openBoxes = boxes.filter(b => !b.isMinimized);
    const minimizedBoxes = boxes.filter(b => b.isMinimized);

    let maxTop = 0;

    // Open boxes start at bottom:0; height 600px (expanded) or 450px (normal)
    if (openBoxes.some(b => b.isExpanded)) {
      maxTop = Math.max(maxTop, 600);
    } else if (openBoxes.length > 0) {
      maxTop = Math.max(maxTop, 450);
    }

    // Minimized boxes stack vertically: box[i].bottom = (i*56)+10, height ~48px
    // → top edge of box[i] = (i*56) + 58
    if (minimizedBoxes.length > 0) {
      const topOfHighest = (minimizedBoxes.length - 1) * 56 + 58;
      maxTop = Math.max(maxTop, topOfHighest);
    }

    return `${maxTop + 20}px`;
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
  }

  toggle(): void {
    this.helpService.toggle();
  }

  close(): void {
    this.helpService.close();
  }
}
